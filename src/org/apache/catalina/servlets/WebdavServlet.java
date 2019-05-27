package org.apache.catalina.servlets;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Stack;
import java.util.TimeZone;
import java.util.Vector;

import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.catalina.util.DOMWriter;
import org.apache.catalina.util.MD5Encoder;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.XMLWriter;
import org.apache.naming.resources.CacheEntry;
import org.apache.naming.resources.Resource;
import org.apache.naming.resources.ResourceAttributes;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * 增加了2级支持WebDAV. 
 * DefaultServlet处理的所有的基础HTTP请求
 */
public class WebdavServlet extends DefaultServlet {


    // -------------------------------------------------------------- Constants


    private static final String METHOD_HEAD = "HEAD";
    private static final String METHOD_PROPFIND = "PROPFIND";
    private static final String METHOD_PROPPATCH = "PROPPATCH";
    private static final String METHOD_MKCOL = "MKCOL";
    private static final String METHOD_COPY = "COPY";
    private static final String METHOD_MOVE = "MOVE";
    private static final String METHOD_LOCK = "LOCK";
    private static final String METHOD_UNLOCK = "UNLOCK";


    /**
     * 默认深度是无限.
     */
    private static final int INFINITY = 3; // To limit tree browsing a bit


    /**
     * PROPFIND - 指定属性掩码
     */
    private static final int FIND_BY_PROPERTY = 0;


    /**
     * PROPFIND - 显示所有属性.
     */
    private static final int FIND_ALL_PROP = 1;


    /**
     * PROPFIND - 返回属性名称.
     */
    private static final int FIND_PROPERTY_NAMES = 2;


    /**
     * 创建一个新锁.
     */
    private static final int LOCK_CREATION = 0;


    /**
     * 刷新锁.
     */
    private static final int LOCK_REFRESH = 1;


    /**
     * 默认锁定超时值.
     */
    private static final int DEFAULT_TIMEOUT = 3600;


    /**
     * 最大锁定超时.
     */
    private static final int MAX_TIMEOUT = 604800;


    /**
     * 默认命名空间.
     */
    protected static final String DEFAULT_NAMESPACE = "DAV:";


    /**
     * 创建日期ISO表示的简单日期格式 (部分).
     */
    protected static final SimpleDateFormat creationDateFormat =
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");


     /**
     * MD5 message digest provider.
     */
    protected static MessageDigest md5Helper;


    /**
     * The MD5 helper object for this class.
     */
    protected static final MD5Encoder md5Encoder = new MD5Encoder();



    static {
        creationDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 锁资源库放在单个资源上.
     * <p>
     * Key : path <br>
     * Value : LockInfo
     */
    private Hashtable resourceLocks = new Hashtable();


    /**
     * 锁空资源库.
     * <p>
     * Key : 包含空锁资源的集合的路径<br>
     * Value : 作为集合成员的空锁资源的向量. vector的每个元素都是与空锁资源相关联的路径.
     */
    private Hashtable lockNullResources = new Hashtable();


    /**
     * 遗传锁的向量.
     * <p>
     * Key : path <br>
     * Value : LockInfo
     */
    private Vector collectionLocks = new Vector();


    /**
     * 用于生成合理安全锁定ID的机密信息.
     */
    private String secret = "catalina";


    // --------------------------------------------------------- Public Methods


    /**
     * 初始化这个servlet
     */
    public void init()
        throws ServletException {

        super.init();

        String value = null;
        try {
            value = getServletConfig().getInitParameter("secret");
            if (value != null)
                secret = value;
        } catch (Throwable t) {
            ;
        }

        // Load the MD5 helper used to calculate signatures.
        try {
            md5Helper = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new UnavailableException("No MD5");
        }
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 返回JAXP文档生成器实例.
     */
    protected DocumentBuilder getDocumentBuilder()
        throws ServletException {
        DocumentBuilder documentBuilder = null;
        DocumentBuilderFactory documentBuilderFactory = null;
        try {
            documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            documentBuilder = documentBuilderFactory.newDocumentBuilder();
        } catch(ParserConfigurationException e) {
            throw new ServletException
                (sm.getString("webdavservlet.jaxpfailed"));
        }
        return documentBuilder;
    }


    /**
     * 处理特殊WebDAV方法.
     */
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        String method = req.getMethod();

        if (debug > 0) {
            String path = getRelativePath(req);
            log("[" + method + "] " + path);
        }

        if (method.equals(METHOD_PROPFIND)) {
            doPropfind(req, resp);
        } else if (method.equals(METHOD_PROPPATCH)) {
            doProppatch(req, resp);
        } else if (method.equals(METHOD_MKCOL)) {
            doMkcol(req, resp);
        } else if (method.equals(METHOD_COPY)) {
            doCopy(req, resp);
        } else if (method.equals(METHOD_MOVE)) {
            doMove(req, resp);
        } else if (method.equals(METHOD_LOCK)) {
            doLock(req, resp);
        } else if (method.equals(METHOD_UNLOCK)) {
            doUnlock(req, resp);
        } else {
            // DefaultServlet processing
            super.service(req, resp);
        }
    }


    /**
     * 检查可选的IF标头中指定的条件是否满足.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceAttributes The resource information
     * @return boolean true : 如果资源满足所有指定条件,
     * false : 如果任何条件不满足，在这种情况下请求处理停止
     */
    protected boolean checkIfHeaders(HttpServletRequest request,
                                     HttpServletResponse response,
                                     ResourceAttributes resourceAttributes)
        throws IOException {

        if (!super.checkIfHeaders(request, response, resourceAttributes))
            return false;

        // TODO : Checking the WebDAV If header
        return true;

    }


    /**
     * OPTIONS Method.
     *
     * @param req The request
     * @param resp The response
     * @throws ServletException If an error occurs
     * @throws IOException If an IO error occurs
     */
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        resp.addHeader("DAV", "1,2");

        StringBuffer methodsAllowed = determineMethodsAllowed(resources,
                                                              req);

        resp.addHeader("Allow", methodsAllowed.toString());
        resp.addHeader("MS-Author-Via", "DAV");

    }


    /**
     * PROPFIND Method.
     */
    protected void doPropfind(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (!listings) {
            // Get allowed methods
            StringBuffer methodsAllowed = determineMethodsAllowed(resources,
                                                                  req);

            resp.addHeader("Allow", methodsAllowed.toString());
            resp.sendError(WebdavStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        String path = getRelativePath(req);
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);

        if ((path.toUpperCase().startsWith("/WEB-INF")) ||
            (path.toUpperCase().startsWith("/META-INF"))) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        // Properties which are to be displayed.
        Vector properties = null;
        // Propfind depth
        int depth = INFINITY;
        // Propfind type
        int type = FIND_ALL_PROP;

        String depthStr = req.getHeader("Depth");

        if (depthStr == null) {
            depth = INFINITY;
        } else {
            if (depthStr.equals("0")) {
                depth = 0;
            } else if (depthStr.equals("1")) {
                depth = 1;
            } else if (depthStr.equals("infinity")) {
                depth = INFINITY;
            }
        }

        Node propNode = null;

        DocumentBuilder documentBuilder = getDocumentBuilder();

        try {
            Document document = documentBuilder.parse
                (new InputSource(req.getInputStream()));

            // Get the root element of the document
            Element rootElement = document.getDocumentElement();
            NodeList childList = rootElement.getChildNodes();

            for (int i=0; i < childList.getLength(); i++) {
                Node currentNode = childList.item(i);
                switch (currentNode.getNodeType()) {
                case Node.TEXT_NODE:
                    break;
                case Node.ELEMENT_NODE:
                    if (currentNode.getNodeName().endsWith("prop")) {
                        type = FIND_BY_PROPERTY;
                        propNode = currentNode;
                    }
                    if (currentNode.getNodeName().endsWith("propname")) {
                        type = FIND_PROPERTY_NAMES;
                    }
                    if (currentNode.getNodeName().endsWith("allprop")) {
                        type = FIND_ALL_PROP;
                    }
                    break;
                }
            }
        } catch(Exception e) {
            // Most likely there was no content : we use the defaults.
            // TODO : Enhance that !
        }

        if (type == FIND_BY_PROPERTY) {
            properties = new Vector();
            NodeList childList = propNode.getChildNodes();

            for (int i=0; i < childList.getLength(); i++) {
                Node currentNode = childList.item(i);
                switch (currentNode.getNodeType()) {
                case Node.TEXT_NODE:
                    break;
                case Node.ELEMENT_NODE:
                    String nodeName = currentNode.getNodeName();
                    String propertyName = null;
                    if (nodeName.indexOf(':') != -1) {
                        propertyName = nodeName.substring
                            (nodeName.indexOf(':') + 1);
                    } else {
                        propertyName = nodeName;
                    }
                    // href is a live property which is handled differently
                    properties.addElement(propertyName);
                    break;
                }
            }

        }

        boolean exists = true;
        Object object = null;
        try {
            object = resources.lookup(path);
        } catch (NamingException e) {
            exists = false;
            int slash = path.lastIndexOf('/');
            if (slash != -1) {
                String parentPath = path.substring(0, slash);
                Vector currentLockNullResources =
                    (Vector) lockNullResources.get(parentPath);
                if (currentLockNullResources != null) {
                    Enumeration lockNullResourcesList =
                        currentLockNullResources.elements();
                    while (lockNullResourcesList.hasMoreElements()) {
                        String lockNullPath = (String)
                            lockNullResourcesList.nextElement();
                        if (lockNullPath.equals(path)) {
                            resp.setStatus(WebdavStatus.SC_MULTI_STATUS);
                            resp.setContentType("text/xml; charset=UTF-8");
                            // Create multistatus object
                            XMLWriter generatedXML =
                                new XMLWriter(resp.getWriter());
                            generatedXML.writeXMLHeader();
                            generatedXML.writeElement
                                (null, "multistatus"
                                 + generateNamespaceDeclarations(),
                                 XMLWriter.OPENING);
                            parseLockNullProperties
                                (req, generatedXML, lockNullPath, type,
                                 properties);
                            generatedXML.writeElement(null, "multistatus",
                                                      XMLWriter.CLOSING);
                            generatedXML.sendData();
                            return;
                        }
                    }
                }
            }
        }

        if (!exists) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, path);
            return;
        }

        resp.setStatus(WebdavStatus.SC_MULTI_STATUS);

        resp.setContentType("text/xml; charset=UTF-8");

        // Create multistatus object
        XMLWriter generatedXML = new XMLWriter(resp.getWriter());
        generatedXML.writeXMLHeader();

        generatedXML.writeElement(null, "multistatus"
                                  + generateNamespaceDeclarations(),
                                  XMLWriter.OPENING);

        if (depth == 0) {
            parseProperties(req, generatedXML, path, type,
                            properties);
        } else {
            // The stack always contains the object of the current level
            Stack stack = new Stack();
            stack.push(path);

            // Stack of the objects one level below
            Stack stackBelow = new Stack();

            while ((!stack.isEmpty()) && (depth >= 0)) {

                String currentPath = (String) stack.pop();
                parseProperties(req, generatedXML, currentPath,
                                type, properties);

                try {
                    object = resources.lookup(currentPath);
                } catch (NamingException e) {
                    continue;
                }

                if ((object instanceof DirContext) && (depth > 0)) {

                    try {
                        NamingEnumeration enumeration = resources.list(currentPath);
                        while (enumeration.hasMoreElements()) {
                            NameClassPair ncPair =
                                (NameClassPair) enumeration.nextElement();
                            String newPath = currentPath;
                            if (!(newPath.endsWith("/")))
                                newPath += "/";
                            newPath += ncPair.getName();
                            stackBelow.push(newPath);
                        }
                    } catch (NamingException e) {
                        resp.sendError
                            (HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                             path);
                        return;
                    }

                    // Displaying the lock-null resources present in that
                    // collection
                    String lockPath = currentPath;
                    if (lockPath.endsWith("/"))
                        lockPath =
                            lockPath.substring(0, lockPath.length() - 1);
                    Vector currentLockNullResources =
                        (Vector) lockNullResources.get(lockPath);
                    if (currentLockNullResources != null) {
                        Enumeration lockNullResourcesList =
                            currentLockNullResources.elements();
                        while (lockNullResourcesList.hasMoreElements()) {
                            String lockNullPath = (String)
                                lockNullResourcesList.nextElement();
                            parseLockNullProperties
                                (req, generatedXML, lockNullPath, type,
                                 properties);
                        }
                    }

                }

                if (stack.isEmpty()) {
                    depth--;
                    stack = stackBelow;
                    stackBelow = new Stack();
                }

                generatedXML.sendData();

            }
        }
        generatedXML.writeElement(null, "multistatus", XMLWriter.CLOSING);
        generatedXML.sendData();
    }


    /**
     * PROPPATCH Method.
     */
    protected void doProppatch(HttpServletRequest req,
                               HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }
        resp.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
    }


    /**
     * MKCOL Method.
     */
    protected void doMkcol(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        String path = getRelativePath(req);

        if ((path.toUpperCase().startsWith("/WEB-INF")) ||
            (path.toUpperCase().startsWith("/META-INF"))) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        boolean exists = true;
        Object object = null;
        try {
            object = resources.lookup(path);
        } catch (NamingException e) {
            exists = false;
        }

        // Can't create a collection if a resource already exists at the given
        // path
        if (exists) {
            // Get allowed methods
            StringBuffer methodsAllowed = determineMethodsAllowed(resources, req);

            resp.addHeader("Allow", methodsAllowed.toString());

            resp.sendError(WebdavStatus.SC_METHOD_NOT_ALLOWED);
            return;
        }

        if (req.getInputStream().available() > 0) {
            DocumentBuilder documentBuilder = getDocumentBuilder();
            try {
                Document document = documentBuilder.parse
                    (new InputSource(req.getInputStream()));
                // TODO : Process this request body
                resp.sendError(WebdavStatus.SC_NOT_IMPLEMENTED);
                return;

            } catch(SAXException saxe) {
                // Parse error - assume invalid content
                resp.sendError(WebdavStatus.SC_BAD_REQUEST);
                return;
            }
        }

        boolean result = true;
        try {
            resources.createSubcontext(path);
        } catch (NamingException e) {
            result = false;
        }

        if (!result) {
            resp.sendError(WebdavStatus.SC_CONFLICT,
                           WebdavStatus.getStatusText
                           (WebdavStatus.SC_CONFLICT));
        } else {
            resp.setStatus(WebdavStatus.SC_CREATED);
            // Removing any lock-null resource which would be present
            lockNullResources.remove(path);
        }

    }


    /**
     * DELETE Method.
     */
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        deleteResource(req, resp);

    }


    /**
     * 处理指定资源的POST请求.
     *
     * @param req The servlet request we are processing
     * @param resp The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        super.doPut(req, resp);

        String path = getRelativePath(req);

        // Removing any lock-null resource which would be present
        lockNullResources.remove(path);
    }

    /**
     * COPY Method.
     */
    protected void doCopy(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }
        copyResource(req, resp);
    }


    /**
     * MOVE Method.
     */
    protected void doMove(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        String path = getRelativePath(req);

        if (copyResource(req, resp)) {
            deleteResource(path, req, resp, false);
        }

    }


    /**
     * LOCK Method.
     */
    protected void doLock(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        LockInfo lock = new LockInfo();

        // Parsing lock request

        // Parsing depth header

        String depthStr = req.getHeader("Depth");

        if (depthStr == null) {
            lock.depth = INFINITY;
        } else {
            if (depthStr.equals("0")) {
                lock.depth = 0;
            } else {
                lock.depth = INFINITY;
            }
        }

        // Parsing timeout header

        int lockDuration = DEFAULT_TIMEOUT;
        String lockDurationStr = req.getHeader("Timeout");
        if (lockDurationStr == null) {
            lockDuration = DEFAULT_TIMEOUT;
        } else {
            int commaPos = lockDurationStr.indexOf(",");
            // If multiple timeouts, just use the first
            if (commaPos != -1) {
                lockDurationStr = lockDurationStr.substring(0,commaPos);
            }
            if (lockDurationStr.startsWith("Second-")) {
                lockDuration =
                    (new Integer(lockDurationStr.substring(7))).intValue();
            } else {
                if (lockDurationStr.equalsIgnoreCase("infinity")) {
                    lockDuration = MAX_TIMEOUT;
                } else {
                    try {
                        lockDuration =
                            (new Integer(lockDurationStr)).intValue();
                    } catch (NumberFormatException e) {
                        lockDuration = MAX_TIMEOUT;
                    }
                }
            }
            if (lockDuration == 0) {
                lockDuration = DEFAULT_TIMEOUT;
            }
            if (lockDuration > MAX_TIMEOUT) {
                lockDuration = MAX_TIMEOUT;
            }
        }
        lock.expiresAt = System.currentTimeMillis() + (lockDuration * 1000);

        int lockRequestType = LOCK_CREATION;

        Node lockInfoNode = null;

        DocumentBuilder documentBuilder = getDocumentBuilder();

        try {
            Document document = documentBuilder.parse(new InputSource
                (req.getInputStream()));

            // Get the root element of the document
            Element rootElement = document.getDocumentElement();
            lockInfoNode = rootElement;
        } catch(Exception e) {
            lockRequestType = LOCK_REFRESH;
        }

        if (lockInfoNode != null) {

            // Reading lock information

            NodeList childList = lockInfoNode.getChildNodes();
            StringWriter strWriter = null;
            DOMWriter domWriter = null;

            Node lockScopeNode = null;
            Node lockTypeNode = null;
            Node lockOwnerNode = null;

            for (int i=0; i < childList.getLength(); i++) {
                Node currentNode = childList.item(i);
                switch (currentNode.getNodeType()) {
                case Node.TEXT_NODE:
                    break;
                case Node.ELEMENT_NODE:
                    String nodeName = currentNode.getNodeName();
                    if (nodeName.endsWith("lockscope")) {
                        lockScopeNode = currentNode;
                    }
                    if (nodeName.endsWith("locktype")) {
                        lockTypeNode = currentNode;
                    }
                    if (nodeName.endsWith("owner")) {
                        lockOwnerNode = currentNode;
                    }
                    break;
                }
            }

            if (lockScopeNode != null) {

                childList = lockScopeNode.getChildNodes();
                for (int i=0; i < childList.getLength(); i++) {
                    Node currentNode = childList.item(i);
                    switch (currentNode.getNodeType()) {
                    case Node.TEXT_NODE:
                        break;
                    case Node.ELEMENT_NODE:
                        String tempScope = currentNode.getNodeName();
                        if (tempScope.indexOf(':') != -1) {
                            lock.scope = tempScope.substring
                                (tempScope.indexOf(':') + 1);
                        } else {
                            lock.scope = tempScope;
                        }
                        break;
                    }
                }

                if (lock.scope == null) {
                    // Bad request
                    resp.setStatus(WebdavStatus.SC_BAD_REQUEST);
                }

            } else {
                // Bad request
                resp.setStatus(WebdavStatus.SC_BAD_REQUEST);
            }

            if (lockTypeNode != null) {

                childList = lockTypeNode.getChildNodes();
                for (int i=0; i < childList.getLength(); i++) {
                    Node currentNode = childList.item(i);
                    switch (currentNode.getNodeType()) {
                    case Node.TEXT_NODE:
                        break;
                    case Node.ELEMENT_NODE:
                        String tempType = currentNode.getNodeName();
                        if (tempType.indexOf(':') != -1) {
                            lock.type =
                                tempType.substring(tempType.indexOf(':') + 1);
                        } else {
                            lock.type = tempType;
                        }
                        break;
                    }
                }

                if (lock.type == null) {
                    // Bad request
                    resp.setStatus(WebdavStatus.SC_BAD_REQUEST);
                }

            } else {
                // Bad request
                resp.setStatus(WebdavStatus.SC_BAD_REQUEST);
            }

            if (lockOwnerNode != null) {

                childList = lockOwnerNode.getChildNodes();
                for (int i=0; i < childList.getLength(); i++) {
                    Node currentNode = childList.item(i);
                    switch (currentNode.getNodeType()) {
                    case Node.TEXT_NODE:
                        lock.owner += currentNode.getNodeValue();
                        break;
                    case Node.ELEMENT_NODE:
                        strWriter = new StringWriter();
                        domWriter = new DOMWriter(strWriter, true);
                        domWriter.setQualifiedNames(false);
                        domWriter.print(currentNode);
                        lock.owner += strWriter.toString();
                        break;
                    }
                }

                if (lock.owner == null) {
                    // Bad request
                    resp.setStatus(WebdavStatus.SC_BAD_REQUEST);
                }

            } else {
                lock.owner = new String();
            }

        }

        String path = getRelativePath(req);

        lock.path = path;

        boolean exists = true;
        Object object = null;
        try {
            object = resources.lookup(path);
        } catch (NamingException e) {
            exists = false;
        }

        Enumeration locksList = null;

        if (lockRequestType == LOCK_CREATION) {

            // Generating lock id
            String lockTokenStr = req.getServletPath() + "-" + lock.type + "-"
                + lock.scope + "-" + req.getUserPrincipal() + "-"
                + lock.depth + "-" + lock.owner + "-" + lock.tokens + "-"
                + lock.expiresAt + "-" + System.currentTimeMillis() + "-"
                + secret;
            String lockToken =
                md5Encoder.encode(md5Helper.digest(lockTokenStr.getBytes()));

            if ( (exists) && (object instanceof DirContext) &&
                 (lock.depth == INFINITY) ) {

                // Locking a collection (and all its member resources)

                // Checking if a child resource of this collection is
                // already locked
                Vector lockPaths = new Vector();
                locksList = collectionLocks.elements();
                while (locksList.hasMoreElements()) {
                    LockInfo currentLock = (LockInfo) locksList.nextElement();
                    if (currentLock.hasExpired()) {
                        resourceLocks.remove(currentLock.path);
                        continue;
                    }
                    if ( (currentLock.path.startsWith(lock.path)) &&
                         ((currentLock.isExclusive()) ||
                          (lock.isExclusive())) ) {
                        // A child collection of this collection is locked
                        lockPaths.addElement(currentLock.path);
                    }
                }
                locksList = resourceLocks.elements();
                while (locksList.hasMoreElements()) {
                    LockInfo currentLock = (LockInfo) locksList.nextElement();
                    if (currentLock.hasExpired()) {
                        resourceLocks.remove(currentLock.path);
                        continue;
                    }
                    if ( (currentLock.path.startsWith(lock.path)) &&
                         ((currentLock.isExclusive()) ||
                          (lock.isExclusive())) ) {
                        // A child resource of this collection is locked
                        lockPaths.addElement(currentLock.path);
                    }
                }

                if (!lockPaths.isEmpty()) {

                    // One of the child paths was locked
                    // We generate a multistatus error report

                    Enumeration lockPathsList = lockPaths.elements();

                    resp.setStatus(WebdavStatus.SC_CONFLICT);

                    XMLWriter generatedXML = new XMLWriter();
                    generatedXML.writeXMLHeader();

                    generatedXML.writeElement
                        (null, "multistatus" + generateNamespaceDeclarations(),
                         XMLWriter.OPENING);

                    while (lockPathsList.hasMoreElements()) {
                        generatedXML.writeElement(null, "response",
                                                  XMLWriter.OPENING);
                        generatedXML.writeElement(null, "href",
                                                  XMLWriter.OPENING);
                        generatedXML
                            .writeText((String) lockPathsList.nextElement());
                        generatedXML.writeElement(null, "href",
                                                  XMLWriter.CLOSING);
                        generatedXML.writeElement(null, "status",
                                                  XMLWriter.OPENING);
                        generatedXML
                            .writeText("HTTP/1.1 " + WebdavStatus.SC_LOCKED
                                       + " " + WebdavStatus
                                       .getStatusText(WebdavStatus.SC_LOCKED));
                        generatedXML.writeElement(null, "status",
                                                  XMLWriter.CLOSING);

                        generatedXML.writeElement(null, "response",
                                                  XMLWriter.CLOSING);
                    }

                    generatedXML.writeElement(null, "multistatus",
                                          XMLWriter.CLOSING);

                    Writer writer = resp.getWriter();
                    writer.write(generatedXML.toString());
                    writer.close();

                    return;

                }

                boolean addLock = true;

                // Checking if there is already a shared lock on this path
                locksList = collectionLocks.elements();
                while (locksList.hasMoreElements()) {

                    LockInfo currentLock = (LockInfo) locksList.nextElement();
                    if (currentLock.path.equals(lock.path)) {

                        if (currentLock.isExclusive()) {
                            resp.sendError(WebdavStatus.SC_LOCKED);
                            return;
                        } else {
                            if (lock.isExclusive()) {
                                resp.sendError(WebdavStatus.SC_LOCKED);
                                return;
                            }
                        }

                        currentLock.tokens.addElement(lockToken);
                        lock = currentLock;
                        addLock = false;

                    }

                }

                if (addLock) {
                    lock.tokens.addElement(lockToken);
                    collectionLocks.addElement(lock);
                }

            } else {

                // Locking a single resource
                // Retrieving an already existing lock on that resource
                LockInfo presentLock = (LockInfo) resourceLocks.get(lock.path);
                if (presentLock != null) {

                    if ((presentLock.isExclusive()) || (lock.isExclusive())) {
                        // If either lock is exclusive, the lock can't be
                        // granted
                        resp.sendError(WebdavStatus.SC_PRECONDITION_FAILED);
                        return;
                    } else {
                        presentLock.tokens.addElement(lockToken);
                        lock = presentLock;
                    }

                } else {

                    lock.tokens.addElement(lockToken);
                    resourceLocks.put(lock.path, lock);

                    // Checking if a resource exists at this path
                    exists = true;
                    try {
                        object = resources.lookup(path);
                    } catch (NamingException e) {
                        exists = false;
                    }
                    if (!exists) {

                        // "Creating" a lock-null resource
                        int slash = lock.path.lastIndexOf('/');
                        String parentPath = lock.path.substring(0, slash);

                        Vector lockNulls =
                            (Vector) lockNullResources.get(parentPath);
                        if (lockNulls == null) {
                            lockNulls = new Vector();
                            lockNullResources.put(parentPath, lockNulls);
                        }

                        lockNulls.addElement(lock.path);

                    }
                    // Add the Lock-Token header as by RFC 2518 8.10.1
                    // - only do this for newly created locks
                    resp.addHeader("Lock-Token", "<opaquelocktoken:" + lockToken + ">");
                }
            }
        }

        if (lockRequestType == LOCK_REFRESH) {

            String ifHeader = req.getHeader("If");
            if (ifHeader == null)
                ifHeader = "";

            // Checking resource locks
            LockInfo toRenew = (LockInfo) resourceLocks.get(path);
            Enumeration tokenList = null;
            if (lock != null) {

                // At least one of the tokens of the locks must have been given
                tokenList = toRenew.tokens.elements();
                while (tokenList.hasMoreElements()) {
                    String token = (String) tokenList.nextElement();
                    if (ifHeader.indexOf(token) != -1) {
                        toRenew.expiresAt = lock.expiresAt;
                        lock = toRenew;
                    }
                }

            }

            // Checking inheritable collection locks
            Enumeration collectionLocksList = collectionLocks.elements();
            while (collectionLocksList.hasMoreElements()) {
                toRenew = (LockInfo) collectionLocksList.nextElement();
                if (path.equals(toRenew.path)) {

                    tokenList = toRenew.tokens.elements();
                    while (tokenList.hasMoreElements()) {
                        String token = (String) tokenList.nextElement();
                        if (ifHeader.indexOf(token) != -1) {
                            toRenew.expiresAt = lock.expiresAt;
                            lock = toRenew;
                        }
                    }
                }
            }
        }

        // Set the status, then generate the XML response containing
        // the lock information
        XMLWriter generatedXML = new XMLWriter();
        generatedXML.writeXMLHeader();
        generatedXML.writeElement(null, "prop"
                                  + generateNamespaceDeclarations(),
                                  XMLWriter.OPENING);

        generatedXML.writeElement(null, "lockdiscovery",
                                  XMLWriter.OPENING);

        lock.toXML(generatedXML);

        generatedXML.writeElement(null, "lockdiscovery",
                                  XMLWriter.CLOSING);

        generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);

        resp.setStatus(WebdavStatus.SC_OK);
        resp.setContentType("text/xml; charset=UTF-8");
        Writer writer = resp.getWriter();
        writer.write(generatedXML.toString());
        writer.close();
    }


    /**
     * UNLOCK Method.
     */
    protected void doUnlock(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return;
        }

        if (isLocked(req)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return;
        }

        String path = getRelativePath(req);

        String lockTokenHeader = req.getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";

        // Checking resource locks
        LockInfo lock = (LockInfo) resourceLocks.get(path);
        Enumeration tokenList = null;
        if (lock != null) {

            // At least one of the tokens of the locks must have been given
            tokenList = lock.tokens.elements();
            while (tokenList.hasMoreElements()) {
                String token = (String) tokenList.nextElement();
                if (lockTokenHeader.indexOf(token) != -1) {
                    lock.tokens.removeElement(token);
                }
            }

            if (lock.tokens.isEmpty()) {
                resourceLocks.remove(path);
                // Removing any lock-null resource which would be present
                lockNullResources.remove(path);
            }
        }

        // Checking inheritable collection locks
        Enumeration collectionLocksList = collectionLocks.elements();
        while (collectionLocksList.hasMoreElements()) {
            lock = (LockInfo) collectionLocksList.nextElement();
            if (path.equals(lock.path)) {

                tokenList = lock.tokens.elements();
                while (tokenList.hasMoreElements()) {
                    String token = (String) tokenList.nextElement();
                    if (lockTokenHeader.indexOf(token) != -1) {
                        lock.tokens.removeElement(token);
                        break;
                    }
                }

                if (lock.tokens.isEmpty()) {
                    collectionLocks.removeElement(lock);
                    // Removing any lock-null resource which would be present
                    lockNullResources.remove(path);
                }
            }
        }
        resp.setStatus(WebdavStatus.SC_NO_CONTENT);
    }

    /**
     * 返回上下文相对路径, 以 "/"开头, 表示指定路径的规范版本, 在".." 和 "."元素被解析之后.
     * 如果指定的路径试图超出当前上下文的边界(即太多 ".."路径元素), 返回<code>null</code>.
     *
     * @param path Path to be normalized
     */
    protected String normalize(String path) {

        if (path == null)
            return null;

        // 为标准化路径创建一个位置
        String normalized = path;

        if (normalized == null)
            return (null);

        if (normalized.equals("/."))
            return "/";

        // 规范的斜线
        if (normalized.indexOf('\\') >= 0)
            normalized = normalized.replace('\\', '/');
        if (!normalized.startsWith("/"))
            normalized = "/" + normalized;

        // Resolve occurrences of "//" in the normalized path
        while (true) {
            int index = normalized.indexOf("//");
            if (index < 0)
                break;
            normalized = normalized.substring(0, index) +
                normalized.substring(index + 1);
        }

        // Resolve occurrences of "/./" in the normalized path
        while (true) {
            int index = normalized.indexOf("/./");
            if (index < 0)
                break;
            normalized = normalized.substring(0, index) +
                normalized.substring(index + 2);
        }

        // Resolve occurrences of "/../" in the normalized path
        while (true) {
            int index = normalized.indexOf("/../");
            if (index < 0)
                break;
            if (index == 0)
                return (null);  // Trying to go outside our context
            int index2 = normalized.lastIndexOf('/', index - 1);
            normalized = normalized.substring(0, index2) +
                normalized.substring(index + 3);
        }
        // 返回已完成的标准化路径
        return (normalized);
    }


    // -------------------------------------------------------- Private Methods

    /**
     * 生成名称空间声明.
     */
    private String generateNamespaceDeclarations() {
        return " xmlns=\"" + DEFAULT_NAMESPACE + "\"";
    }


    /**
     * 检查当前资源是否已写入锁定状态. 该方法将查看"If"标头，以确保客户端给出了适当的锁定令牌.
     *
     * @param req Servlet request
     * @return boolean true if the resource is locked (在资源上存在的至少一个非共享锁中没有找到适当的锁定令牌).
     */
    private boolean isLocked(HttpServletRequest req) {

        String path = getRelativePath(req);

        String ifHeader = req.getHeader("If");
        if (ifHeader == null)
            ifHeader = "";

        String lockTokenHeader = req.getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";

        return isLocked(path, ifHeader + lockTokenHeader);
    }


    /**
     * 检查当前资源状态是否已经写锁定.
     *
     * @param path Path of the resource
     * @param ifHeader "If" HTTP header which was included in the request
     * @return boolean true if the resource is locked (在资源上存在的至少一个非共享锁中没有找到适当的锁定令牌).
     */
    private boolean isLocked(String path, String ifHeader) {

        // Checking resource locks
        LockInfo lock = (LockInfo) resourceLocks.get(path);
        Enumeration tokenList = null;
        if ((lock != null) && (lock.hasExpired())) {
            resourceLocks.remove(path);
        } else if (lock != null) {

            // 至少有一个锁的令牌必须被给定
            tokenList = lock.tokens.elements();
            boolean tokenMatch = false;
            while (tokenList.hasMoreElements()) {
                String token = (String) tokenList.nextElement();
                if (ifHeader.indexOf(token) != -1)
                    tokenMatch = true;
            }
            if (!tokenMatch)
                return true;

        }

        // Checking inheritable collection locks
        Enumeration collectionLocksList = collectionLocks.elements();
        while (collectionLocksList.hasMoreElements()) {
            lock = (LockInfo) collectionLocksList.nextElement();
            if (lock.hasExpired()) {
                collectionLocks.removeElement(lock);
            } else if (path.startsWith(lock.path)) {

                tokenList = lock.tokens.elements();
                boolean tokenMatch = false;
                while (tokenList.hasMoreElements()) {
                    String token = (String) tokenList.nextElement();
                    if (ifHeader.indexOf(token) != -1)
                        tokenMatch = true;
                }
                if (!tokenMatch)
                    return true;
            }
        }
        return false;
    }


    /**
     * 复制资源.
     *
     * @param req Servlet request
     * @param resp Servlet response
     * @return boolean true if the copy is successful
     */
    private boolean copyResource(HttpServletRequest req,
                                 HttpServletResponse resp)
        throws ServletException, IOException {

        // Parsing destination header
        String destinationPath = req.getHeader("Destination");

        if (destinationPath == null) {
            resp.sendError(WebdavStatus.SC_BAD_REQUEST);
            return false;
        }

        // Remove url encoding from destination
        destinationPath = RequestUtil.URLDecode(destinationPath, "UTF8");

        int protocolIndex = destinationPath.indexOf("://");
        if (protocolIndex >= 0) {
            // 如果目标URL包含协议, we can safely
            // trim everything upto the first "/" character after "://"
            int firstSeparator =
                destinationPath.indexOf("/", protocolIndex + 4);
            if (firstSeparator < 0) {
                destinationPath = "/";
            } else {
                destinationPath = destinationPath.substring(firstSeparator);
            }
        } else {
            String hostName = req.getServerName();
            if ((hostName != null) && (destinationPath.startsWith(hostName))) {
                destinationPath = destinationPath.substring(hostName.length());
            }

            int portIndex = destinationPath.indexOf(":");
            if (portIndex >= 0) {
                destinationPath = destinationPath.substring(portIndex);
            }

            if (destinationPath.startsWith(":")) {
                int firstSeparator = destinationPath.indexOf("/");
                if (firstSeparator < 0) {
                    destinationPath = "/";
                } else {
                    destinationPath =
                        destinationPath.substring(firstSeparator);
                }
            }
        }

        // Normalise destination path (remove '.' and '..')
        destinationPath = normalize(destinationPath);

        String contextPath = req.getContextPath();
        if ((contextPath != null) &&
            (destinationPath.startsWith(contextPath))) {
            destinationPath = destinationPath.substring(contextPath.length());
        }

        String pathInfo = req.getPathInfo();
        if (pathInfo != null) {
            String servletPath = req.getServletPath();
            if ((servletPath != null) &&
                (destinationPath.startsWith(servletPath))) {
                destinationPath = destinationPath
                    .substring(servletPath.length());
            }
        }

        if (debug > 0)
            log("Dest path :" + destinationPath);

        if ((destinationPath.toUpperCase().startsWith("/WEB-INF")) ||
            (destinationPath.toUpperCase().startsWith("/META-INF"))) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return false;
        }

        String path = getRelativePath(req);

        if ((path.toUpperCase().startsWith("/WEB-INF")) ||
            (path.toUpperCase().startsWith("/META-INF"))) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return false;
        }

        if (destinationPath.equals(path)) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return false;
        }

        // Parsing overwrite header
        boolean overwrite = true;
        String overwriteHeader = req.getHeader("Overwrite");

        if (overwriteHeader != null) {
            if (overwriteHeader.equalsIgnoreCase("T")) {
                overwrite = true;
            } else {
                overwrite = false;
            }
        }

        // Overwriting the destination
        boolean exists = true;
        try {
            resources.lookup(destinationPath);
        } catch (NamingException e) {
            exists = false;
        }

        if (overwrite) {

            // Delete destination resource, if it exists
            if (exists) {
                if (!deleteResource(destinationPath, req, resp, true)) {
                    return false;
                }
            } else {
                resp.setStatus(WebdavStatus.SC_CREATED);
            }
        } else {
            // If the destination exists, then it's a conflict
            if (exists) {
                resp.sendError(WebdavStatus.SC_PRECONDITION_FAILED);
                return false;
            }
        }

        // Copying source to destination
        Hashtable errorList = new Hashtable();

        boolean result = copyResource(resources, errorList,
                                      path, destinationPath);

        if ((!result) || (!errorList.isEmpty())) {
            sendReport(req, resp, errorList);
            return false;
        }

        // Removing any lock-null resource which would be present at
        // the destination path
        lockNullResources.remove(destinationPath);
        return true;
    }


    /**
     * 复制一个集合.
     *
     * @param resources 要使用的资源实施
     * @param errorList 复制过程中的错误信息列表Hashtable
     * @param source 要复制的资源的路径
     * @param dest Destination path
     */
    private boolean copyResource(DirContext resources, Hashtable errorList,
                                 String source, String dest) {

        if (debug > 1)
            log("Copy: " + source + " To: " + dest);

        Object object = null;
        try {
            object = resources.lookup(source);
        } catch (NamingException e) {
        }

        if (object instanceof DirContext) {

            try {
                resources.createSubcontext(dest);
            } catch (NamingException e) {
                errorList.put
                    (dest, new Integer(WebdavStatus.SC_CONFLICT));
                return false;
            }

            try {
                NamingEnumeration enumeration = resources.list(source);
                while (enumeration.hasMoreElements()) {
                    NameClassPair ncPair = (NameClassPair) enumeration.nextElement();
                    String childDest = dest;
                    if (!childDest.equals("/"))
                        childDest += "/";
                    childDest += ncPair.getName();
                    String childSrc = source;
                    if (!childSrc.equals("/"))
                        childSrc += "/";
                    childSrc += ncPair.getName();
                    copyResource(resources, errorList, childSrc, childDest);
                }
            } catch (NamingException e) {
                errorList.put
                    (dest, new Integer(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
                return false;
            }

        } else {

            if (object instanceof Resource) {
                try {
                    resources.bind(dest, object);
                } catch (NamingException e) {
                    errorList.put
                        (source,
                         new Integer(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
                    return false;
                }
            } else {
                errorList.put
                    (source,
                     new Integer(WebdavStatus.SC_INTERNAL_SERVER_ERROR));
                return false;
            }

        }
        return true;
    }


    /**
     * 删除资源.
     *
     * @param req Servlet request
     * @param resp Servlet response
     * @return boolean true if the copy is successful
     */
    private boolean deleteResource(HttpServletRequest req,
                                   HttpServletResponse resp)
        throws ServletException, IOException {

        String path = getRelativePath(req);
        return deleteResource(path, req, resp, true);
    }


    /**
     * 删除资源.
     *
     * @param path 要删除的资源的路径
     * @param req Servlet request
     * @param resp Servlet response
     * @param setStatus 应该在成功完成后设置响应状态吗
     */
    private boolean deleteResource(String path, HttpServletRequest req,
                                   HttpServletResponse resp, boolean setStatus)
        throws ServletException, IOException {

        if ((path.toUpperCase().startsWith("/WEB-INF")) ||
            (path.toUpperCase().startsWith("/META-INF"))) {
            resp.sendError(WebdavStatus.SC_FORBIDDEN);
            return false;
        }

        String ifHeader = req.getHeader("If");
        if (ifHeader == null)
            ifHeader = "";

        String lockTokenHeader = req.getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";

        if (isLocked(path, ifHeader + lockTokenHeader)) {
            resp.sendError(WebdavStatus.SC_LOCKED);
            return false;
        }

        boolean exists = true;
        Object object = null;
        try {
            object = resources.lookup(path);
        } catch (NamingException e) {
            exists = false;
        }

        if (!exists) {
            resp.sendError(WebdavStatus.SC_NOT_FOUND);
            return false;
        }

        boolean collection = (object instanceof DirContext);

        if (!collection) {
            try {
                resources.unbind(path);
            } catch (NamingException e) {
                resp.sendError(WebdavStatus.SC_INTERNAL_SERVER_ERROR);
                return false;
            }
        } else {

            Hashtable errorList = new Hashtable();

            deleteCollection(req, resources, path, errorList);
            try {
                resources.unbind(path);
            } catch (NamingException e) {
                errorList.put(path, new Integer
                    (WebdavStatus.SC_INTERNAL_SERVER_ERROR));
            }

            if (!errorList.isEmpty()) {

                sendReport(req, resp, errorList);
                return false;

            }

        }
        if (setStatus) {
            resp.setStatus(WebdavStatus.SC_NO_CONTENT);
        }
        return true;
    }


    /**
     * 删除集合.
     *
     * @param resources 与上下文相关联的资源实现
     * @param path 要删除的集合的路径
     * @param errorList 包含发生的错误的列表
     */
    private void deleteCollection(HttpServletRequest req,
                                  DirContext resources,
                                  String path, Hashtable errorList) {

        if (debug > 1)
            log("Delete:" + path);

        if ((path.toUpperCase().startsWith("/WEB-INF")) ||
            (path.toUpperCase().startsWith("/META-INF"))) {
            errorList.put(path, new Integer(WebdavStatus.SC_FORBIDDEN));
            return;
        }

        String ifHeader = req.getHeader("If");
        if (ifHeader == null)
            ifHeader = "";

        String lockTokenHeader = req.getHeader("Lock-Token");
        if (lockTokenHeader == null)
            lockTokenHeader = "";

        Enumeration enumeration = null;
        try {
            enumeration = resources.list(path);
        } catch (NamingException e) {
            errorList.put(path, new Integer
                (WebdavStatus.SC_INTERNAL_SERVER_ERROR));
            return;
        }

        while (enumeration.hasMoreElements()) {
            NameClassPair ncPair = (NameClassPair) enumeration.nextElement();
            String childName = path;
            if (!childName.equals("/"))
                childName += "/";
            childName += ncPair.getName();

            if (isLocked(childName, ifHeader + lockTokenHeader)) {

                errorList.put(childName, new Integer(WebdavStatus.SC_LOCKED));

            } else {

                try {
                    Object object = resources.lookup(childName);
                    if (object instanceof DirContext) {
                        deleteCollection(req, resources, childName, errorList);
                    }

                    try {
                        resources.unbind(childName);
                    } catch (NamingException e) {
                        if (!(object instanceof DirContext)) {
                            // If it's not a collection, then it's an unknown
                            // error
                            errorList.put
                                (childName, new Integer
                                    (WebdavStatus.SC_INTERNAL_SERVER_ERROR));
                        }
                    }
                } catch (NamingException e) {
                    errorList.put
                        (childName, new Integer
                            (WebdavStatus.SC_INTERNAL_SERVER_ERROR));
                }
            }

        }

    }


    /**
     * 发送multistatus元素包含一个完整的错误报告给客户.
     *
     * @param req Servlet request
     * @param resp Servlet response
     * @param errorList 要显示的错误列表
     */
    private void sendReport(HttpServletRequest req, HttpServletResponse resp,
                            Hashtable errorList)
        throws ServletException, IOException {

        resp.setStatus(WebdavStatus.SC_MULTI_STATUS);

        String absoluteUri = req.getRequestURI();
        String relativePath = getRelativePath(req);

        XMLWriter generatedXML = new XMLWriter();
        generatedXML.writeXMLHeader();

        generatedXML.writeElement(null, "multistatus"
                                  + generateNamespaceDeclarations(),
                                  XMLWriter.OPENING);

        Enumeration pathList = errorList.keys();
        while (pathList.hasMoreElements()) {

            String errorPath = (String) pathList.nextElement();
            int errorCode = ((Integer) errorList.get(errorPath)).intValue();

            generatedXML.writeElement(null, "response", XMLWriter.OPENING);

            generatedXML.writeElement(null, "href", XMLWriter.OPENING);
            String toAppend = errorPath.substring(relativePath.length());
            if (!toAppend.startsWith("/"))
                toAppend = "/" + toAppend;
            generatedXML.writeText(absoluteUri + toAppend);
            generatedXML.writeElement(null, "href", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "status", XMLWriter.OPENING);
            generatedXML
                .writeText("HTTP/1.1 " + errorCode + " "
                           + WebdavStatus.getStatusText(errorCode));
            generatedXML.writeElement(null, "status", XMLWriter.CLOSING);

            generatedXML.writeElement(null, "response", XMLWriter.CLOSING);

        }

        generatedXML.writeElement(null, "multistatus", XMLWriter.CLOSING);

        Writer writer = resp.getWriter();
        writer.write(generatedXML.toString());
        writer.close();

    }


    /**
     * Propfind辅助方法.
     *
     * @param req The servlet request
     * @param resources 与此上下文关联的资源对象
     * @param generatedXML XML response to the Propfind request
     * @param path 当前资源的路径
     * @param type Propfind type
     * @param propertiesVector 如果propfind类型是通过名称查找属性, 然后这个Vector包含这些属性
     */
    private void parseProperties(HttpServletRequest req,
                                 XMLWriter generatedXML,
                                 String path, int type,
                                 Vector propertiesVector) {

        // Exclude any resource in the /WEB-INF and /META-INF subdirectories
        // (the "toUpperCase()" avoids problems on Windows systems)
        if (path.toUpperCase().startsWith("/WEB-INF") ||
            path.toUpperCase().startsWith("/META-INF"))
            return;

        CacheEntry cacheEntry = resources.lookupCache(path);

        generatedXML.writeElement(null, "response", XMLWriter.OPENING);
        String status = new String("HTTP/1.1 " + WebdavStatus.SC_OK + " "
                                   + WebdavStatus.getStatusText
                                   (WebdavStatus.SC_OK));

        // Generating href element
        generatedXML.writeElement(null, "href", XMLWriter.OPENING);

        String href = req.getContextPath() + req.getServletPath();
        if ((href.endsWith("/")) && (path.startsWith("/")))
            href += path.substring(1);
        else
            href += path;
        if ((cacheEntry.context != null) && (!href.endsWith("/")))
            href += "/";

        generatedXML.writeText(rewriteUrl(href));

        generatedXML.writeElement(null, "href", XMLWriter.CLOSING);

        String resourceName = path;
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1)
            resourceName = resourceName.substring(lastSlash + 1);

        switch (type) {

        case FIND_ALL_PROP :

            generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
            generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

            generatedXML.writeProperty
                (null, "creationdate",
                 getISOCreationDate(cacheEntry.attributes.getCreation()));
            generatedXML.writeElement(null, "displayname", XMLWriter.OPENING);
            generatedXML.writeData(resourceName);
            generatedXML.writeElement(null, "displayname", XMLWriter.CLOSING);
            if (cacheEntry.resource != null) {
                generatedXML.writeProperty
                    (null, "getlastmodified", FastHttpDateFormat.formatDate
                           (cacheEntry.attributes.getLastModified(), null));
                generatedXML.writeProperty
                    (null, "getcontentlength",
                     String.valueOf(cacheEntry.attributes.getContentLength()));
                String contentType = getServletContext().getMimeType
                    (cacheEntry.name);
                if (contentType != null) {
                    generatedXML.writeProperty(null, "getcontenttype",
                                               contentType);
                }
                generatedXML.writeProperty(null, "getetag",
                                           getETag(cacheEntry.attributes));
                generatedXML.writeElement(null, "resourcetype",
                                          XMLWriter.NO_CONTENT);
            } else {
                generatedXML.writeElement(null, "resourcetype",
                                          XMLWriter.OPENING);
                generatedXML.writeElement(null, "collection",
                                          XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "resourcetype",
                                          XMLWriter.CLOSING);
            }

            generatedXML.writeProperty(null, "source", "");

            String supportedLocks = "<lockentry>"
                + "<lockscope><exclusive/></lockscope>"
                + "<locktype><write/></locktype>"
                + "</lockentry>" + "<lockentry>"
                + "<lockscope><shared/></lockscope>"
                + "<locktype><write/></locktype>"
                + "</lockentry>";
            generatedXML.writeElement(null, "supportedlock",
                                      XMLWriter.OPENING);
            generatedXML.writeText(supportedLocks);
            generatedXML.writeElement(null, "supportedlock",
                                      XMLWriter.CLOSING);

            generateLockDiscovery(path, generatedXML);

            generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "status", XMLWriter.OPENING);
            generatedXML.writeText(status);
            generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);

            break;

        case FIND_PROPERTY_NAMES :

            generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
            generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

            generatedXML.writeElement(null, "creationdate",
                                      XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "displayname",
                                      XMLWriter.NO_CONTENT);
            if (cacheEntry.resource != null) {
                generatedXML.writeElement(null, "getcontentlanguage",
                                          XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "getcontentlength",
                                          XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "getcontenttype",
                                          XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "getetag",
                                          XMLWriter.NO_CONTENT);
                generatedXML.writeElement(null, "getlastmodified",
                                          XMLWriter.NO_CONTENT);
            }
            generatedXML.writeElement(null, "resourcetype",
                                      XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "source", XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "lockdiscovery",
                                      XMLWriter.NO_CONTENT);

            generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "status", XMLWriter.OPENING);
            generatedXML.writeText(status);
            generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);

            break;

        case FIND_BY_PROPERTY :

            Vector propertiesNotFound = new Vector();

            // Parse the list of properties
            generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
            generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

            Enumeration properties = propertiesVector.elements();

            while (properties.hasMoreElements()) {

                String property = (String) properties.nextElement();

                if (property.equals("creationdate")) {
                    generatedXML.writeProperty
                        (null, "creationdate",
                         getISOCreationDate(cacheEntry.attributes.getCreation()));
                } else if (property.equals("displayname")) {
                    generatedXML.writeElement
                        (null, "displayname", XMLWriter.OPENING);
                    generatedXML.writeData(resourceName);
                    generatedXML.writeElement
                        (null, "displayname", XMLWriter.CLOSING);
                } else if (property.equals("getcontentlanguage")) {
                    if (cacheEntry.context != null) {
                        propertiesNotFound.addElement(property);
                    } else {
                        generatedXML.writeElement(null, "getcontentlanguage",
                                                  XMLWriter.NO_CONTENT);
                    }
                } else if (property.equals("getcontentlength")) {
                    if (cacheEntry.context != null) {
                        propertiesNotFound.addElement(property);
                    } else {
                        generatedXML.writeProperty
                            (null, "getcontentlength",
                             (String.valueOf(cacheEntry.attributes.getContentLength())));
                    }
                } else if (property.equals("getcontenttype")) {
                    if (cacheEntry.context != null) {
                        propertiesNotFound.addElement(property);
                    } else {
                        generatedXML.writeProperty
                            (null, "getcontenttype",
                             getServletContext().getMimeType
                             (cacheEntry.name));
                    }
                } else if (property.equals("getetag")) {
                    if (cacheEntry.context != null) {
                        propertiesNotFound.addElement(property);
                    } else {
                        generatedXML.writeProperty
                            (null, "getetag", getETag(cacheEntry.attributes));
                    }
                } else if (property.equals("getlastmodified")) {
                    if (cacheEntry.context != null) {
                        propertiesNotFound.addElement(property);
                    } else {
                        generatedXML.writeProperty
                            (null, "getlastmodified", FastHttpDateFormat.formatDate
                                    (cacheEntry.attributes.getLastModified(), null));
                    }
                } else if (property.equals("resourcetype")) {
                    if (cacheEntry.context != null) {
                        generatedXML.writeElement(null, "resourcetype",
                                                  XMLWriter.OPENING);
                        generatedXML.writeElement(null, "collection",
                                                  XMLWriter.NO_CONTENT);
                        generatedXML.writeElement(null, "resourcetype",
                                                  XMLWriter.CLOSING);
                    } else {
                        generatedXML.writeElement(null, "resourcetype",
                                                  XMLWriter.NO_CONTENT);
                    }
                } else if (property.equals("source")) {
                    generatedXML.writeProperty(null, "source", "");
                } else if (property.equals("supportedlock")) {
                    supportedLocks = "<lockentry>"
                        + "<lockscope><exclusive/></lockscope>"
                        + "<locktype><write/></locktype>"
                        + "</lockentry>" + "<lockentry>"
                        + "<lockscope><shared/></lockscope>"
                        + "<locktype><write/></locktype>"
                        + "</lockentry>";
                    generatedXML.writeElement(null, "supportedlock",
                                              XMLWriter.OPENING);
                    generatedXML.writeText(supportedLocks);
                    generatedXML.writeElement(null, "supportedlock",
                                              XMLWriter.CLOSING);
                } else if (property.equals("lockdiscovery")) {
                    if (!generateLockDiscovery(path, generatedXML))
                        propertiesNotFound.addElement(property);
                } else {
                    propertiesNotFound.addElement(property);
                }

            }

            generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "status", XMLWriter.OPENING);
            generatedXML.writeText(status);
            generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);

            Enumeration propertiesNotFoundList = propertiesNotFound.elements();

            if (propertiesNotFoundList.hasMoreElements()) {

                status = new String("HTTP/1.1 " + WebdavStatus.SC_NOT_FOUND
                                    + " " + WebdavStatus.getStatusText
                                    (WebdavStatus.SC_NOT_FOUND));

                generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
                generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

                while (propertiesNotFoundList.hasMoreElements()) {
                    generatedXML.writeElement
                        (null, (String) propertiesNotFoundList.nextElement(),
                         XMLWriter.NO_CONTENT);
                }

                generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "status", XMLWriter.OPENING);
                generatedXML.writeText(status);
                generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);
            }
            break;
        }
        generatedXML.writeElement(null, "response", XMLWriter.CLOSING);
    }


    /**
     * Propfind 辅助方法. 可以显示一个空锁资源的属性.
     *
     * @param resources 与此上下文关联的资源对象
     * @param generatedXML XML response to the Propfind request
     * @param path Path of the current resource
     * @param type Propfind type
     * @param propertiesVector 如果propfind类型是通过名称查找属性, 然后这个Vector包含这些属性
     */
    private void parseLockNullProperties(HttpServletRequest req,
                                         XMLWriter generatedXML,
                                         String path, int type,
                                         Vector propertiesVector) {

        // Exclude any resource in the /WEB-INF and /META-INF subdirectories
        // (the "toUpperCase()" avoids problems on Windows systems)
        if (path.toUpperCase().startsWith("/WEB-INF") ||
            path.toUpperCase().startsWith("/META-INF"))
            return;

        // Retrieving the lock associated with the lock-null resource
        LockInfo lock = (LockInfo) resourceLocks.get(path);

        if (lock == null)
            return;

        generatedXML.writeElement(null, "response", XMLWriter.OPENING);
        String status = new String("HTTP/1.1 " + WebdavStatus.SC_OK + " "
                                   + WebdavStatus.getStatusText
                                   (WebdavStatus.SC_OK));

        // Generating href element
        generatedXML.writeElement(null, "href", XMLWriter.OPENING);

        String absoluteUri = req.getRequestURI();
        String relativePath = getRelativePath(req);
        String toAppend = path.substring(relativePath.length());
        if (!toAppend.startsWith("/"))
            toAppend = "/" + toAppend;

        generatedXML.writeText(rewriteUrl(normalize(absoluteUri + toAppend)));

        generatedXML.writeElement(null, "href", XMLWriter.CLOSING);

        String resourceName = path;
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash != -1)
            resourceName = resourceName.substring(lastSlash + 1);

        switch (type) {

        case FIND_ALL_PROP :

            generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
            generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

            generatedXML.writeProperty
                (null, "creationdate",
                 getISOCreationDate(lock.creationDate.getTime()));
            generatedXML.writeElement
                (null, "displayname", XMLWriter.OPENING);
            generatedXML.writeData(resourceName);
            generatedXML.writeElement
                (null, "displayname", XMLWriter.CLOSING);
            generatedXML.writeProperty(null, "getlastmodified",
                                       FastHttpDateFormat.formatDate
                                       (lock.creationDate.getTime(), null));
            generatedXML.writeProperty
                (null, "getcontentlength", String.valueOf(0));
            generatedXML.writeProperty(null, "getcontenttype", "");
            generatedXML.writeProperty(null, "getetag", "");
            generatedXML.writeElement(null, "resourcetype",
                                      XMLWriter.OPENING);
            generatedXML.writeElement(null, "lock-null", XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "resourcetype",
                                      XMLWriter.CLOSING);

            generatedXML.writeProperty(null, "source", "");

            String supportedLocks = "<lockentry>"
                + "<lockscope><exclusive/></lockscope>"
                + "<locktype><write/></locktype>"
                + "</lockentry>" + "<lockentry>"
                + "<lockscope><shared/></lockscope>"
                + "<locktype><write/></locktype>"
                + "</lockentry>";
            generatedXML.writeElement(null, "supportedlock",
                                      XMLWriter.OPENING);
            generatedXML.writeText(supportedLocks);
            generatedXML.writeElement(null, "supportedlock",
                                      XMLWriter.CLOSING);

            generateLockDiscovery(path, generatedXML);

            generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "status", XMLWriter.OPENING);
            generatedXML.writeText(status);
            generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);

            break;

        case FIND_PROPERTY_NAMES :

            generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
            generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

            generatedXML.writeElement(null, "creationdate",
                                      XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "displayname",
                                      XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "getcontentlanguage",
                                      XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "getcontentlength",
                                      XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "getcontenttype",
                                      XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "getetag",
                                      XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "getlastmodified",
                                      XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "resourcetype",
                                      XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "source",
                                      XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "lockdiscovery",
                                      XMLWriter.NO_CONTENT);

            generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "status", XMLWriter.OPENING);
            generatedXML.writeText(status);
            generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);

            break;

        case FIND_BY_PROPERTY :

            Vector propertiesNotFound = new Vector();

            // Parse the list of properties
            generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
            generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

            Enumeration properties = propertiesVector.elements();

            while (properties.hasMoreElements()) {

                String property = (String) properties.nextElement();

                if (property.equals("creationdate")) {
                    generatedXML.writeProperty
                        (null, "creationdate",
                         getISOCreationDate(lock.creationDate.getTime()));
                } else if (property.equals("displayname")) {
                    generatedXML.writeElement
                        (null, "displayname", XMLWriter.OPENING);
                    generatedXML.writeData(resourceName);
                    generatedXML.writeElement
                        (null, "displayname", XMLWriter.CLOSING);
                } else if (property.equals("getcontentlanguage")) {
                    generatedXML.writeElement(null, "getcontentlanguage",
                                              XMLWriter.NO_CONTENT);
                } else if (property.equals("getcontentlength")) {
                    generatedXML.writeProperty
                        (null, "getcontentlength", (String.valueOf(0)));
                } else if (property.equals("getcontenttype")) {
                    generatedXML.writeProperty
                        (null, "getcontenttype", "");
                } else if (property.equals("getetag")) {
                    generatedXML.writeProperty(null, "getetag", "");
                } else if (property.equals("getlastmodified")) {
                    generatedXML.writeProperty
                        (null, "getlastmodified",
                          FastHttpDateFormat.formatDate
                         (lock.creationDate.getTime(), null));
                } else if (property.equals("resourcetype")) {
                    generatedXML.writeElement(null, "resourcetype",
                                              XMLWriter.OPENING);
                    generatedXML.writeElement(null, "lock-null",
                                              XMLWriter.NO_CONTENT);
                    generatedXML.writeElement(null, "resourcetype",
                                              XMLWriter.CLOSING);
                } else if (property.equals("source")) {
                    generatedXML.writeProperty(null, "source", "");
                } else if (property.equals("supportedlock")) {
                    supportedLocks = "<lockentry>"
                        + "<lockscope><exclusive/></lockscope>"
                        + "<locktype><write/></locktype>"
                        + "</lockentry>" + "<lockentry>"
                        + "<lockscope><shared/></lockscope>"
                        + "<locktype><write/></locktype>"
                        + "</lockentry>";
                    generatedXML.writeElement(null, "supportedlock",
                                              XMLWriter.OPENING);
                    generatedXML.writeText(supportedLocks);
                    generatedXML.writeElement(null, "supportedlock",
                                              XMLWriter.CLOSING);
                } else if (property.equals("lockdiscovery")) {
                    if (!generateLockDiscovery(path, generatedXML))
                        propertiesNotFound.addElement(property);
                } else {
                    propertiesNotFound.addElement(property);
                }

            }

            generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "status", XMLWriter.OPENING);
            generatedXML.writeText(status);
            generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
            generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);

            Enumeration propertiesNotFoundList = propertiesNotFound.elements();

            if (propertiesNotFoundList.hasMoreElements()) {

                status = new String("HTTP/1.1 " + WebdavStatus.SC_NOT_FOUND
                                    + " " + WebdavStatus.getStatusText
                                    (WebdavStatus.SC_NOT_FOUND));

                generatedXML.writeElement(null, "propstat", XMLWriter.OPENING);
                generatedXML.writeElement(null, "prop", XMLWriter.OPENING);

                while (propertiesNotFoundList.hasMoreElements()) {
                    generatedXML.writeElement
                        (null, (String) propertiesNotFoundList.nextElement(),
                         XMLWriter.NO_CONTENT);
                }

                generatedXML.writeElement(null, "prop", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "status", XMLWriter.OPENING);
                generatedXML.writeText(status);
                generatedXML.writeElement(null, "status", XMLWriter.CLOSING);
                generatedXML.writeElement(null, "propstat", XMLWriter.CLOSING);
            }
            break;
        }
        generatedXML.writeElement(null, "response", XMLWriter.CLOSING);
    }


    /**
     * 打印与路径相关联的锁发现信息
     *
     * @param path Path
     * @param generatedXML 将添加锁信息的XML数据
     * @return true 如果至少显示了一个锁
     */
    private boolean generateLockDiscovery(String path, XMLWriter generatedXML) {

        LockInfo resourceLock = (LockInfo) resourceLocks.get(path);
        Enumeration collectionLocksList = collectionLocks.elements();

        boolean wroteStart = false;

        if (resourceLock != null) {
            wroteStart = true;
            generatedXML.writeElement(null, "lockdiscovery", XMLWriter.OPENING);
            resourceLock.toXML(generatedXML);
        }

        while (collectionLocksList.hasMoreElements()) {
            LockInfo currentLock =
                (LockInfo) collectionLocksList.nextElement();
            if (path.startsWith(currentLock.path)) {
                if (!wroteStart) {
                    wroteStart = true;
                    generatedXML.writeElement(null, "lockdiscovery",
                                              XMLWriter.OPENING);
                }
                currentLock.toXML(generatedXML);
            }
        }

        if (wroteStart) {
            generatedXML.writeElement(null, "lockdiscovery", XMLWriter.CLOSING);
        } else {
            return false;
        }
        return true;
    }


    /**
     * 获取ISO格式的创建日期.
     */
    private String getISOCreationDate(long creationDate) {
        StringBuffer creationDateValue = new StringBuffer
            (creationDateFormat.format
             (new Date(creationDate)));
        /*
        int offset = Calendar.getInstance().getTimeZone().getRawOffset()
            / 3600000; // FIXME ?
        if (offset < 0) {
            creationDateValue.append("-");
            offset = -offset;
        } else if (offset > 0) {
            creationDateValue.append("+");
        }
        if (offset != 0) {
            if (offset < 10)
                creationDateValue.append("0");
            creationDateValue.append(offset + ":00");
        } else {
            creationDateValue.append("Z");
        }
        */
        return creationDateValue.toString();
    }

    /**
     * 确定资源通常允许的方法.
     */
    private StringBuffer determineMethodsAllowed(DirContext resources,
                                                 HttpServletRequest req) {

        StringBuffer methodsAllowed = new StringBuffer();
        boolean exists = true;
        Object object = null;
        try {
            String path = getRelativePath(req);

            object = resources.lookup(path);
        } catch (NamingException e) {
            exists = false;
        }

        if (!exists) {
            methodsAllowed.append("OPTIONS, MKCOL, PUT, LOCK");
            return methodsAllowed;
        }

        methodsAllowed.append("OPTIONS, GET, HEAD, POST, DELETE, TRACE");
        methodsAllowed.append(", PROPPATCH, COPY, MOVE, LOCK, UNLOCK");

        if (listings) {
            methodsAllowed.append(", PROPFIND");
        }

        if (!(object instanceof DirContext)) {
            methodsAllowed.append(", PUT");
        }
        return methodsAllowed;
    }

    // --------------------------------------------------  LockInfo Inner Class


    /**
     * 持有锁信息.
     */
    private class LockInfo {

        // -------------------------------------------------------- Constructor
        public LockInfo() {
        }

        // ------------------------------------------------- Instance Variables

        String path = "/";
        String type = "write";
        String scope = "exclusive";
        int depth = 0;
        String owner = "";
        Vector tokens = new Vector();
        long expiresAt = 0;
        Date creationDate = new Date();

        // ----------------------------------------------------- Public Methods

        public String toString() {
            String result =  "Type:" + type + "\n";
            result += "Scope:" + scope + "\n";
            result += "Depth:" + depth + "\n";
            result += "Owner:" + owner + "\n";
            result += "Expiration:"
                + FastHttpDateFormat.formatDate(expiresAt, null) + "\n";
            Enumeration tokensList = tokens.elements();
            while (tokensList.hasMoreElements()) {
                result += "Token:" + tokensList.nextElement() + "\n";
            }
            return result;
        }


        /**
         * 返回true， 如果锁过期了.
         */
        public boolean hasExpired() {
            return (System.currentTimeMillis() > expiresAt);
        }


        /**
         * 返回true，如果锁是独占的.
         */
        public boolean isExclusive() {
            return (scope.equals("exclusive"));
        }


        /**
         * 获取此锁令牌的XML表示形式. 此方法将向给定的XML写入器追加一个XML片段.
         */
        public void toXML(XMLWriter generatedXML) {
            generatedXML.writeElement(null, "activelock", XMLWriter.OPENING);
            generatedXML.writeElement(null, "locktype", XMLWriter.OPENING);
            generatedXML.writeElement(null, type, XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "locktype", XMLWriter.CLOSING);

            generatedXML.writeElement(null, "lockscope", XMLWriter.OPENING);
            generatedXML.writeElement(null, scope, XMLWriter.NO_CONTENT);
            generatedXML.writeElement(null, "lockscope", XMLWriter.CLOSING);

            generatedXML.writeElement(null, "depth", XMLWriter.OPENING);
            if (depth == INFINITY) {
                generatedXML.writeText("Infinity");
            } else {
                generatedXML.writeText("0");
            }
            generatedXML.writeElement(null, "depth", XMLWriter.CLOSING);

            generatedXML.writeElement(null, "owner", XMLWriter.OPENING);
            generatedXML.writeText(owner);
            generatedXML.writeElement(null, "owner", XMLWriter.CLOSING);

            generatedXML.writeElement(null, "timeout", XMLWriter.OPENING);
            long timeout = (expiresAt - System.currentTimeMillis()) / 1000;
            generatedXML.writeText("Second-" + timeout);
            generatedXML.writeElement(null, "timeout", XMLWriter.CLOSING);

            generatedXML.writeElement(null, "locktoken", XMLWriter.OPENING);
            Enumeration tokensList = tokens.elements();
            while (tokensList.hasMoreElements()) {
                generatedXML.writeElement(null, "href", XMLWriter.OPENING);
                generatedXML.writeText("opaquelocktoken:"
                                       + tokensList.nextElement());
                generatedXML.writeElement(null, "href", XMLWriter.CLOSING);
            }
            generatedXML.writeElement(null, "locktoken", XMLWriter.CLOSING);

            generatedXML.writeElement(null, "activelock", XMLWriter.CLOSING);
        }
    }


    // --------------------------------------------------- Property Inner Class

    private class Property {
        public String name;
        public String value;
        public String namespace;
        public String namespaceAbbrev;
        public int status = WebdavStatus.SC_OK;
    }
};


// --------------------------------------------------------  WebdavStatus Class


/**
 * 包装HttpServletResponse类成抽象的，指定协议使用的. 
 * 为了支持其他协议，我们只需要修改这个类和WebDavRetCode 类.
 */
class WebdavStatus {

    // ----------------------------------------------------- Instance Variables

    /**
     * 这个Hashtable 包含HTTP状态码和WebDAV的描述性文本映射.
     */
    private static Hashtable mapStatusCodes = new Hashtable();


    // ------------------------------------------------------ HTTP Status Codes


    /**
     * 状态码(200) 指示请求成功正常.
     */
    public static final int SC_OK = HttpServletResponse.SC_OK;


    /**
     * 状态码(201) 指示请求成功并在服务器上创建新资源.
     */
    public static final int SC_CREATED = HttpServletResponse.SC_CREATED;


    /**
     * 状态码(202) 指示请求接受处理，但未完成.
     */
    public static final int SC_ACCEPTED = HttpServletResponse.SC_ACCEPTED;


    /**
     * 状态码(204) 指示请求成功，但没有返回新信息.
     */
    public static final int SC_NO_CONTENT = HttpServletResponse.SC_NO_CONTENT;


    /**
     * 状态码(301) 指示资源已永久移动到新位置, 未来的引用应该使用一个新的URI和它们的请求.
     */
    public static final int SC_MOVED_PERMANENTLY = HttpServletResponse.SC_MOVED_PERMANENTLY;


    /**
     * 状态码(302) 指示资源已临时移到另一位置, 但是将来的引用仍然应该使用原始URI来访问资源.
     */
    public static final int SC_MOVED_TEMPORARILY = HttpServletResponse.SC_MOVED_TEMPORARILY;


    /**
     * 状态码(304) 指示条件GET 操作发现资源可用且未修改.
     */
    public static final int SC_NOT_MODIFIED = HttpServletResponse.SC_NOT_MODIFIED;


    /**
     * 状态码(400) 指示客户端发送的请求有语法错误.
     */
    public static final int SC_BAD_REQUEST = HttpServletResponse.SC_BAD_REQUEST;


    /**
     * 状态码(401) 指示请求需要HTTP身份验证.
     */
    public static final int SC_UNAUTHORIZED = HttpServletResponse.SC_UNAUTHORIZED;


    /**
     * 状态码(403) 指示服务器理解请求，但拒绝执行请求.
     */
    public static final int SC_FORBIDDEN = HttpServletResponse.SC_FORBIDDEN;


    /**
     * 状态码(404) 指示所请求的资源不可用.
     */
    public static final int SC_NOT_FOUND = HttpServletResponse.SC_NOT_FOUND;


    /**
     * 状态码(500) 指示HTTP服务内部的错误，从而阻止它执行请求.
     */
    public static final int SC_INTERNAL_SERVER_ERROR = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;


    /**
     * 状态码(501) 指示HTTP服务不支持实现请求所需的功能.
     */
    public static final int SC_NOT_IMPLEMENTED = HttpServletResponse.SC_NOT_IMPLEMENTED;


    /**
     * 状态码(502) 指示HTTP服务器在代理或网关时从服务器处接收到无效响应.
     */
    public static final int SC_BAD_GATEWAY = HttpServletResponse.SC_BAD_GATEWAY;


    /**
     * 状态码(503) 指示HTTP服务暂时超载, 无法处理请求.
     */
    public static final int SC_SERVICE_UNAVAILABLE = HttpServletResponse.SC_SERVICE_UNAVAILABLE;


    /**
     * 状态码(100) 指示客户端可以继续其请求. 
     * 此临时响应用于通知客户端已收到请求的初始部分，尚未被服务器拒绝.
     */
    public static final int SC_CONTINUE = 100;


    /**
     * 状态码(405) 指示资源不允许指定方法.
     */
    public static final int SC_METHOD_NOT_ALLOWED = 405;


    /**
     * 状态码(409) 指示由于资源的当前状态发生冲突，无法完成请求.
     */
    public static final int SC_CONFLICT = 409;


    /**
     * 状态码(412) 指示在服务器上测试的请求报头字段中的一个或多个给定的前提条件，该字段被评估为false.
     */
    public static final int SC_PRECONDITION_FAILED = 412;


    /**
     * 状态码(413) 指示服务器拒绝处理请求，因为请求实体大于服务器愿意或能够处理的请求.
     */
    public static final int SC_REQUEST_TOO_LONG = 413;


    /**
     * 状态码(415) 指示服务器拒绝服务请求，因为请求的实体是所请求的资源不支持所请求方法的格式.
     */
    public static final int SC_UNSUPPORTED_MEDIA_TYPE = 415;


    // -------------------------------------------- Extended WebDav status code


    /**
     * 状态码(207) 指示响应需要为多个独立操作提供状态.
     */
    public static final int SC_MULTI_STATUS = 207;
    // This one colides with HTTP 1.1
    // "207 Parital Update OK"


    /**
     * 状态码(418) 指示使用PATCH方法提交的实体不被资源理解.
     */
    public static final int SC_UNPROCESSABLE_ENTITY = 418;
    // This one colides with HTTP 1.1
    // "418 Reauthentication Required"


    /**
     * 状态码(419) 指示资源在执行该方法后没有足够的空间来记录资源的状态.
     */
    public static final int SC_INSUFFICIENT_SPACE_ON_RESOURCE = 419;
    // This one colides with HTTP 1.1
    // "419 Proxy Reauthentication Required"


    /**
     * 状态码(420) 指示该方法未在其范围内的特定资源上执行，因为该方法执行的某些部分失败导致整个方法被中止.
     */
    public static final int SC_METHOD_FAILURE = 420;


    /**
     * 状态码(423) 指示方法的目标资源被锁定, 而且请求中没有一个有效的锁信息头, 或者锁信息头标识另一个主体持有的锁.
     */
    public static final int SC_LOCKED = 423;


    // ------------------------------------------------------------ Initializer


    static {
        // HTTP 1.0 tatus Code
        addStatusCodeMap(SC_OK, "OK");
        addStatusCodeMap(SC_CREATED, "Created");
        addStatusCodeMap(SC_ACCEPTED, "Accepted");
        addStatusCodeMap(SC_NO_CONTENT, "No Content");
        addStatusCodeMap(SC_MOVED_PERMANENTLY, "Moved Permanently");
        addStatusCodeMap(SC_MOVED_TEMPORARILY, "Moved Temporarily");
        addStatusCodeMap(SC_NOT_MODIFIED, "Not Modified");
        addStatusCodeMap(SC_BAD_REQUEST, "Bad Request");
        addStatusCodeMap(SC_UNAUTHORIZED, "Unauthorized");
        addStatusCodeMap(SC_FORBIDDEN, "Forbidden");
        addStatusCodeMap(SC_NOT_FOUND, "Not Found");
        addStatusCodeMap(SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
        addStatusCodeMap(SC_NOT_IMPLEMENTED, "Not Implemented");
        addStatusCodeMap(SC_BAD_GATEWAY, "Bad Gateway");
        addStatusCodeMap(SC_SERVICE_UNAVAILABLE, "Service Unavailable");
        addStatusCodeMap(SC_CONTINUE, "Continue");
        addStatusCodeMap(SC_METHOD_NOT_ALLOWED, "Method Not Allowed");
        addStatusCodeMap(SC_CONFLICT, "Conflict");
        addStatusCodeMap(SC_PRECONDITION_FAILED, "Precondition Failed");
        addStatusCodeMap(SC_REQUEST_TOO_LONG, "Request Too Long");
        addStatusCodeMap(SC_UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type");
        // WebDav Status Codes
        addStatusCodeMap(SC_MULTI_STATUS, "Multi-Status");
        addStatusCodeMap(SC_UNPROCESSABLE_ENTITY, "Unprocessable Entity");
        addStatusCodeMap(SC_INSUFFICIENT_SPACE_ON_RESOURCE,
                         "Insufficient Space On Resource");
        addStatusCodeMap(SC_METHOD_FAILURE, "Method Failure");
        addStatusCodeMap(SC_LOCKED, "Locked");
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 返回HTTP状态文本的HTTP或WebDAV状态码，通过查找指定的静态映射.
     *
     * @param   nHttpStatusCode [IN] HTTP or WebDAV status code
     * @return  带有HTTP状态码的简短描述短语的字符串 (e.g., "OK").
     */
    public static String getStatusText(int nHttpStatusCode) {
        Integer intKey = new Integer(nHttpStatusCode);

        if (!mapStatusCodes.containsKey(intKey)) {
            return "";
        } else {
            return (String) mapStatusCodes.get(intKey);
        }
    }

    // -------------------------------------------------------- Private Methods

    /**
     * 添加新的状态码 -> 状态文本映射. 这是静态方法，因为映射是静态变量.
     *
     * @param   nKey    [IN] HTTP或WebDAV状态码
     * @param   strVal  [IN] HTTP状态文本
     */
    private static void addStatusCodeMap(int nKey, String strVal) {
        mapStatusCodes.put(new Integer(nKey), strVal);
    }
};

