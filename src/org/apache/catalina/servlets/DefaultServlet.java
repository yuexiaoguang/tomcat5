package org.apache.catalina.servlets;


import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.catalina.Globals;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.util.URLEncoder;
import org.apache.naming.resources.CacheEntry;
import org.apache.naming.resources.ProxyDirContext;
import org.apache.naming.resources.Resource;
import org.apache.naming.resources.ResourceAttributes;


/**
 * 大多数Web应用程序的默认资源服务servlet, 用于服务静态资源，如HTML页面和图像.
 */
public class DefaultServlet extends HttpServlet {


    // ----------------------------------------------------- Instance Variables


    /**
     * 调试等级
     */
    protected int debug = 0;


    /**
     * 服务资源时要使用的输入缓冲区大小.
     */
    protected int input = 2048;


    /**
     * 当没有欢迎文件时，我们是否应该生成目录列表？
     */
    protected boolean listings = true;


    /**
     * 只读标记. 默认是true.
     */
    protected boolean readOnly = true;


    /**
     * 服务资源时要使用的输出缓冲区大小
     */
    protected int output = 2048;


    /**
     * 包含安全字符集的数组.
     */
    protected static URLEncoder urlEncoder;


    /**
     * 允许每个目录定制目录列表.
     */
    protected String  localXsltFile = null;


    /**
     * 允许每个实例定制目录列表.
     */
    protected String  globalXsltFile = null;


    /**
     * 允许包含自述文件
     */
    protected String readmeFile = null;


    /**
     * 代理目录上下文.
     */
    protected ProxyDirContext resources = null;


    /**
     * 读取静态文件时使用的文件编码. 如果没有指定，则使用平台默认值.
     */
    protected String fileEncoding = null;
    
    
    /**
     * sendfile使用的最小大小, in bytes.
     */
    protected int sendfileSize = 48 * 1024;
    
    
    // ----------------------------------------------------- Static Initializer


    /**
     * GMT时区 - 所有http日期都是格林尼治时间
     */
    static {
        urlEncoder = new URLEncoder();
        urlEncoder.addSafeCharacter('-');
        urlEncoder.addSafeCharacter('_');
        urlEncoder.addSafeCharacter('.');
        urlEncoder.addSafeCharacter('*');
        urlEncoder.addSafeCharacter('/');
    }


    /**
     * MIME multipart separation string
     */
    protected static final String mimeSeparation = "CATALINA_MIME_BOUNDARY";


    /**
     * JNDI 资源名称.
     */
    protected static final String RESOURCES_JNDI_NAME = "java:/comp/Resources";


    /**
     * The string manager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 文件传输缓冲区的大小
     */
    private static final int BUFFER_SIZE = 4096;


    // --------------------------------------------------------- Public Methods


    /**
     * 结束这个servlet.
     */
    public void destroy() {
    }


    /**
     * 初始化这个servlet.
     */
    public void init() throws ServletException {

        // 从初始化参数设置属性
        String value = null;
        try {
            value = getServletConfig().getInitParameter("debug");
            debug = Integer.parseInt(value);
        } catch (Throwable t) {
            ;
        }
        try {
            value = getServletConfig().getInitParameter("input");
            input = Integer.parseInt(value);
        } catch (Throwable t) {
            ;
        }
        try {
            value = getServletConfig().getInitParameter("listings");
            listings = (new Boolean(value)).booleanValue();
        } catch (Throwable t) {
            ;
        }
        try {
            value = getServletConfig().getInitParameter("readonly");
            if (value != null)
                readOnly = (new Boolean(value)).booleanValue();
        } catch (Throwable t) {
            ;
        }
        try {
            value = getServletConfig().getInitParameter("output");
            output = Integer.parseInt(value);
        } catch (Throwable t) {
            ;
        }
        try {
            value = getServletConfig().getInitParameter("sendfileSize");
            sendfileSize = Integer.parseInt(value) * 1024;
        } catch (Throwable t) {
            ;
        }
        try {
            value = getServletConfig().getInitParameter("fileEncoding");
            fileEncoding = value;
        } catch (Throwable t) {
            ;
        }

        globalXsltFile = getServletConfig().getInitParameter("globalXsltFile");
        localXsltFile = getServletConfig().getInitParameter("localXsltFile");
        readmeFile = getServletConfig().getInitParameter("readmeFile");

        // 对指定缓冲区大小的检查
        if (input < 256)
            input = 256;
        if (output < 256)
            output = 256;

        if (debug > 0) {
            log("DefaultServlet.init:  input buffer size=" + input +
                ", output buffer size=" + output);
        }

        // Load the proxy dir context.
        try {
            resources = (ProxyDirContext) getServletContext()
                .getAttribute(Globals.RESOURCES_ATTR);
        } catch(ClassCastException e) {
            // Failed : Not the right type
        }
        if (resources == null) {
            try {
                resources =
                    (ProxyDirContext) new InitialContext()
                    .lookup(RESOURCES_JNDI_NAME);
            } catch (NamingException e) {
                // Failed
            } catch (ClassCastException e) {
                // Failed : Not the right type
            }
        }
        if (resources == null) {
            throw new UnavailableException("No resources");
        }
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 返回相对路径.
     *
     * @param request The servlet request we are processing
     */
    protected String getRelativePath(HttpServletRequest request) {

        // Are we being processed by a RequestDispatcher.include()?
        if (request.getAttribute(Globals.INCLUDE_REQUEST_URI_ATTR) != null) {
            String result = (String) request.getAttribute(
                                            Globals.INCLUDE_PATH_INFO_ATTR);
            if (result == null)
                result = (String) request.getAttribute(
                                            Globals.INCLUDE_SERVLET_PATH_ATTR);
            if ((result == null) || (result.equals("")))
                result = "/";
            return (result);
        }

        // No, 从请求中直接提取所需的路径
        String result = request.getPathInfo();
        if (result == null) {
            result = request.getServletPath();
        }
        if ((result == null) || (result.equals(""))) {
            result = "/";
        }
        return (result);
    }


    /**
     * 处理指定资源的GET请求.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
        throws IOException, ServletException {

        // Serve the requested resource, including the data content
        try {
            serveResource(request, response, true);
        } catch( IOException ex ) {
            // we probably have this check somewhere else too.
            if( ex.getMessage() != null
                && ex.getMessage().indexOf("Broken pipe") >= 0 ) {
                // ignore it.
            }
            throw ex;
        }
    }


    /**
     * 处理指定资源的HEAD请求.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void doHead(HttpServletRequest request,
                          HttpServletResponse response)
        throws IOException, ServletException {

        // 服务请求的资源，不包含数据内容
        serveResource(request, response, false);
    }


    /**
     * 处理指定资源的POST请求.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
        throws IOException, ServletException {
        doGet(request, response);
    }


    /**
     * 处理指定资源的Put请求.
     *
     * @param req The servlet request we are processing
     * @param resp The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String path = getRelativePath(req);

        boolean exists = true;
        try {
            resources.lookup(path);
        } catch (NamingException e) {
            exists = false;
        }

        boolean result = true;

        // Temp. content file used to support partial PUT
        File contentFile = null;

        // Input stream for temp. content file used to support partial PUT
        FileInputStream contentFileInStream = null;

        Range range = parseContentRange(req, resp);

        InputStream resourceInputStream = null;

        // 将范围中指定的数据追加到该资源的现有内容中 - create a temp. 在本地文件系统上执行此操作的文件
        // 假设现在只指定一个范围
        if (range != null) {
            contentFile = executePartialPut(req, range, path);
            resourceInputStream = new FileInputStream(contentFile);
        } else {
            resourceInputStream = req.getInputStream();
        }

        try {
            Resource newResource = new Resource(resourceInputStream);
            // FIXME: Add attributes
            if (exists) {
                resources.rebind(path, newResource);
            } else {
                resources.bind(path, newResource);
            }
        } catch(NamingException e) {
            result = false;
        }

        if (result) {
            if (exists) {
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.setStatus(HttpServletResponse.SC_CREATED);
            }
        } else {
            resp.sendError(HttpServletResponse.SC_CONFLICT);
        }
    }


    /**
     * Handle a partial PUT. 请求中指定的新内容添加到oldRevisionContent的现有内容. 
     * 此代码不支持对同一资源的同步部分更新.
     */
    protected File executePartialPut(HttpServletRequest req, Range range,
                                     String path)
        throws IOException {

        // 将范围中指定的数据追加到该资源的现有内容中 - create a temp. 在本地文件系统上执行此操作的文件
        // 假设现在只指定一个范围
        File tempDir = (File) getServletContext().getAttribute
            ("javax.servlet.context.tempdir");
        // Convert all '/' characters to '.' in resourcePath
        String convertedResourcePath = path.replace('/', '.');
        File contentFile = new File(tempDir, convertedResourcePath);
        if (contentFile.createNewFile()) {
            // Clean up contentFile when Tomcat is terminated
            contentFile.deleteOnExit();
        }

        RandomAccessFile randAccessContentFile =
            new RandomAccessFile(contentFile, "rw");

        Resource oldResource = null;
        try {
            Object obj = resources.lookup(path);
            if (obj instanceof Resource)
                oldResource = (Resource) obj;
        } catch (NamingException e) {
        }

        // Copy data in oldRevisionContent to contentFile
        if (oldResource != null) {
            BufferedInputStream bufOldRevStream =
                new BufferedInputStream(oldResource.streamContent(),
                                        BUFFER_SIZE);

            int numBytesRead;
            byte[] copyBuffer = new byte[BUFFER_SIZE];
            while ((numBytesRead = bufOldRevStream.read(copyBuffer)) != -1) {
                randAccessContentFile.write(copyBuffer, 0, numBytesRead);
            }

            bufOldRevStream.close();
        }

        randAccessContentFile.setLength(range.length);

        // Append data in request input stream to contentFile
        randAccessContentFile.seek(range.start);
        int numBytesRead;
        byte[] transferBuffer = new byte[BUFFER_SIZE];
        BufferedInputStream requestBufInStream =
            new BufferedInputStream(req.getInputStream(), BUFFER_SIZE);
        while ((numBytesRead = requestBufInStream.read(transferBuffer)) != -1) {
            randAccessContentFile.write(transferBuffer, 0, numBytesRead);
        }
        randAccessContentFile.close();
        requestBufInStream.close();

        return contentFile;
    }


    /**
     * 处理指定资源的Delete请求.
     *
     * @param req The servlet request we are processing
     * @param resp The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String path = getRelativePath(req);

        boolean exists = true;
        try {
            resources.lookup(path);
        } catch (NamingException e) {
            exists = false;
        }

        if (exists) {
            boolean result = true;
            try {
                resources.unbind(path);
            } catch (NamingException e) {
                result = false;
            }
            if (result) {
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }


    /**
     * 检查可选的IF标头中指定的条件是否满足.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceAttributes The resource information
     * @return boolean true: 如果资源满足所有指定条件,
     * false: 如果任何条件不满意, 在这种情况下请求处理停止
     */
    protected boolean checkIfHeaders(HttpServletRequest request,
                                     HttpServletResponse response,
                                     ResourceAttributes resourceAttributes)
        throws IOException {

        return checkIfMatch(request, response, resourceAttributes)
            && checkIfModifiedSince(request, response, resourceAttributes)
            && checkIfNoneMatch(request, response, resourceAttributes)
            && checkIfUnmodifiedSince(request, response, resourceAttributes);

    }


    /**
     * 得到与文件关联的ETag.
     *
     * @param resourceAttributes The resource information
     */
    protected String getETag(ResourceAttributes resourceAttributes) {
        String result = null;
        if ((result = resourceAttributes.getETag(true)) != null) {
            return result;
        } else if ((result = resourceAttributes.getETag()) != null) {
            return result;
        } else {
            return "W/\"" + resourceAttributes.getContentLength() + "-"
                + resourceAttributes.getLastModified() + "\"";
        }
    }


    /**
     * URL rewriter.
     *
     * @param path Path which has to be rewiten
     */
    protected String rewriteUrl(String path) {
        return urlEncoder.encode( path );
    }


    /**
     * 显示文件的大小.
     */
    protected void displaySize(StringBuffer buf, int filesize) {

        int leftside = filesize / 1024;
        int rightside = (filesize % 1024) / 103;  // makes 1 digit
        // To avoid 0.0 for non-zero file, we bump to 0.1
        if (leftside == 0 && rightside == 0 && filesize != 0)
            rightside = 1;
        buf.append(leftside).append(".").append(rightside);
        buf.append(" KB");

    }


    /**
     * 服务指定资源, 可选地包含数据内容.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param content Should the content be included?
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void serveResource(HttpServletRequest request,
                                 HttpServletResponse response,
                                 boolean content)
        throws IOException, ServletException {

        // 标识请求的资源路径
        String path = getRelativePath(request);
        if (debug > 0) {
            if (content)
                log("DefaultServlet.serveResource:  Serving resource '" +
                    path + "' headers and data");
            else
                log("DefaultServlet.serveResource:  Serving resource '" +
                    path + "' headers only");
        }

        CacheEntry cacheEntry = resources.lookupCache(path);

        if (!cacheEntry.exists) {
            // 检查是否包含在内，这样我们就可以在错误中返回适当的缺失资源名
            String requestUri = (String) request.getAttribute(
                                            Globals.INCLUDE_REQUEST_URI_ATTR);
            if (requestUri == null) {
                requestUri = request.getRequestURI();
            } else {
                // We're included, response.sendError()将被包括我们在内的资源所忽略.
                // 因此, 可以让包含资源的唯一方法是在响应中包含警告消息
                response.getWriter().write(
                    sm.getString("defaultServlet.missingResource",
                    requestUri));
            }

            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                               requestUri);
            return;
        }

        // 如果资源不是集合, 资源路径不是以"/"或"\"结束, 还没有找到
        if (cacheEntry.context == null) {
            if (path.endsWith("/") || (path.endsWith("\\"))) {
                // 检查是否包含在内，这样我们就可以在错误中返回适当的缺失资源名
                String requestUri = (String) request.getAttribute(
                                            Globals.INCLUDE_REQUEST_URI_ATTR);
                if (requestUri == null) {
                    requestUri = request.getRequestURI();
                }
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                   requestUri);
                return;
            }
        }

        // 检查可选的IF标头中指定的条件是否满足.
        if (cacheEntry.context == null) {

            // Checking If headers
            boolean included =
                (request.getAttribute(Globals.INCLUDE_CONTEXT_PATH_ATTR) != null);
            if (!included
                && !checkIfHeaders(request, response, cacheEntry.attributes)) {
                return;
            }

        }

        // Find content type.
        String contentType = cacheEntry.attributes.getMimeType();
        if (contentType == null) {
            contentType = getServletContext().getMimeType(cacheEntry.name);
            cacheEntry.attributes.setMimeType(contentType);
        }

        Vector ranges = null;
        long contentLength = -1L;

        if (cacheEntry.context != null) {

            // 跳过目录列表，如果被配置来阻止它们
            if (!listings) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                   request.getRequestURI());
                return;
            }
            contentType = "text/html;charset=UTF-8";

        } else {

            // 解析范围说明符
            ranges = parseRange(request, response, cacheEntry.attributes);

            // ETag header
            response.setHeader("ETag", getETag(cacheEntry.attributes));

            // Last-Modified header
            response.setHeader("Last-Modified",
                    cacheEntry.attributes.getLastModifiedHttp());

            // Get content length
            contentLength = cacheEntry.attributes.getContentLength();
            // 零长度文件的特殊情况, 当设置输出缓冲区大小时会导致（静默）ISE
            if (contentLength == 0L) {
                content = false;
            }

        }

        ServletOutputStream ostream = null;
        PrintWriter writer = null;

        if (content) {

            // 试图检索servlet输出流
            try {
                ostream = response.getOutputStream();
            } catch (IllegalStateException e) {
                // 如果它失败, 试图获取一个 Writer , 如果试图为文本文件服务
                if ( (contentType == null)
                     || (contentType.startsWith("text")) ) {
                    writer = response.getWriter();
                } else {
                    throw e;
                }
            }

        }

        if ( (cacheEntry.context != null) ||
             ( ((ranges == null) || (ranges.isEmpty()))
               && (request.getHeader("Range") == null) ) ) {

            // 设置适当的输出header
            if (contentType != null) {
                if (debug > 0)
                    log("DefaultServlet.serveFile:  contentType='" +
                        contentType + "'");
                response.setContentType(contentType);
            }
            if ((cacheEntry.resource != null) && (contentLength >= 0)) {
                if (debug > 0)
                    log("DefaultServlet.serveFile:  contentLength=" +
                        contentLength);
                if (contentLength < Integer.MAX_VALUE) {
                    response.setContentLength((int) contentLength);
                } else {
                    // Set the content-length as String to be able to use a long
                    response.setHeader("content-length", "" + contentLength);
                }
            }

            InputStream renderResult = null;
            if (cacheEntry.context != null) {

                if (content) {
                    // Serve the directory browser
                    renderResult =
                        render(request.getContextPath(), cacheEntry);
                }

            }

            // Copy the input stream to our output stream (if requested)
            if (content) {
                try {
                    response.setBufferSize(output);
                } catch (IllegalStateException e) {
                    // Silent catch
                }
                if (ostream != null) {
                    if (!checkSendfile(request, response, cacheEntry, contentLength, null))
                        copy(cacheEntry, renderResult, ostream);
                } else {
                    copy(cacheEntry, renderResult, writer);
                }
            }

        } else {

            if ((ranges == null) || (ranges.isEmpty()))
                return;

            // Partial content response.
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            if (ranges.size() == 1) {

                Range range = (Range) ranges.elementAt(0);
                response.addHeader("Content-Range", "bytes "
                                   + range.start
                                   + "-" + range.end + "/"
                                   + range.length);
                long length = range.end - range.start + 1;
                if (length < Integer.MAX_VALUE) {
                    response.setContentLength((int) length);
                } else {
                    // Set the content-length as String to be able to use a long
                    response.setHeader("content-length", "" + length);
                }

                if (contentType != null) {
                    if (debug > 0)
                        log("DefaultServlet.serveFile:  contentType='" +
                            contentType + "'");
                    response.setContentType(contentType);
                }

                if (content) {
                    try {
                        response.setBufferSize(output);
                    } catch (IllegalStateException e) {
                        // Silent catch
                    }
                    if (ostream != null) {
                        if (!checkSendfile(request, response, cacheEntry, range.end - range.start + 1, range))
                            copy(cacheEntry, ostream, range);
                    } else {
                        copy(cacheEntry, writer, range);
                    }
                }
            } else {
                response.setContentType("multipart/byteranges; boundary="
                                        + mimeSeparation);
                if (content) {
                    try {
                        response.setBufferSize(output);
                    } catch (IllegalStateException e) {
                        // Silent catch
                    }
                    if (ostream != null) {
                        copy(cacheEntry, ostream, ranges.elements(),
                             contentType);
                    } else {
                        copy(cacheEntry, writer, ranges.elements(),
                             contentType);
                    }
                }
            }
        }
    }


    /**
     * 解析content-range标头.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @return Range
     */
    protected Range parseContentRange(HttpServletRequest request,
                                      HttpServletResponse response)
        throws IOException {

        // Retrieving the content-range header (if any is specified
        String rangeHeader = request.getHeader("Content-Range");

        if (rangeHeader == null)
            return null;

        // bytes is the only range unit supported
        if (!rangeHeader.startsWith("bytes")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        rangeHeader = rangeHeader.substring(6).trim();

        int dashPos = rangeHeader.indexOf('-');
        int slashPos = rangeHeader.indexOf('/');

        if (dashPos == -1) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        if (slashPos == -1) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        Range range = new Range();

        try {
            range.start = Long.parseLong(rangeHeader.substring(0, dashPos));
            range.end =
                Long.parseLong(rangeHeader.substring(dashPos + 1, slashPos));
            range.length = Long.parseLong
                (rangeHeader.substring(slashPos + 1, rangeHeader.length()));
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        if (!range.validate()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
        return range;
    }


    /**
     * 解析range标头.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @return Vector of ranges
     */
    protected Vector parseRange(HttpServletRequest request,
                                HttpServletResponse response,
                                ResourceAttributes resourceAttributes)
        throws IOException {

        // Checking If-Range
        String headerValue = request.getHeader("If-Range");

        if (headerValue != null) {

            long headerValueTime = (-1L);
            try {
                headerValueTime = request.getDateHeader("If-Range");
            } catch (Exception e) {
                ;
            }

            String eTag = getETag(resourceAttributes);
            long lastModified = resourceAttributes.getLastModified();

            if (headerValueTime == (-1L)) {

                // If the ETag the client gave does not match the entity
                // etag, then the entire entity is returned.
                if (!eTag.equals(headerValue.trim()))
                    return null;

            } else {

                // 如果客户端获取的实体的时间戳比实体的最后一个修改日期早, 返回整个实体.
                if (lastModified > (headerValueTime + 1000))
                    return null;
            }
        }

        long fileLength = resourceAttributes.getContentLength();

        if (fileLength == 0)
            return null;

        // Retrieving the range header (if any is specified
        String rangeHeader = request.getHeader("Range");

        if (rangeHeader == null)
            return null;
        // bytes is the only range unit supported (and I don't see the point
        // of adding new ones).
        if (!rangeHeader.startsWith("bytes")) {
            response.addHeader("Content-Range", "bytes */" + fileLength);
            response.sendError
                (HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            return null;
        }

        rangeHeader = rangeHeader.substring(6);

        // Vector which will contain all the ranges which are successfully
        // parsed.
        Vector result = new Vector();
        StringTokenizer commaTokenizer = new StringTokenizer(rangeHeader, ",");

        // Parsing the range list
        while (commaTokenizer.hasMoreTokens()) {
            String rangeDefinition = commaTokenizer.nextToken().trim();

            Range currentRange = new Range();
            currentRange.length = fileLength;

            int dashPos = rangeDefinition.indexOf('-');

            if (dashPos == -1) {
                response.addHeader("Content-Range", "bytes */" + fileLength);
                response.sendError
                    (HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return null;
            }

            if (dashPos == 0) {

                try {
                    long offset = Long.parseLong(rangeDefinition);
                    currentRange.start = fileLength + offset;
                    currentRange.end = fileLength - 1;
                } catch (NumberFormatException e) {
                    response.addHeader("Content-Range",
                                       "bytes */" + fileLength);
                    response.sendError
                        (HttpServletResponse
                         .SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    return null;
                }

            } else {

                try {
                    currentRange.start = Long.parseLong
                        (rangeDefinition.substring(0, dashPos));
                    if (dashPos < rangeDefinition.length() - 1)
                        currentRange.end = Long.parseLong
                            (rangeDefinition.substring
                             (dashPos + 1, rangeDefinition.length()));
                    else
                        currentRange.end = fileLength - 1;
                } catch (NumberFormatException e) {
                    response.addHeader("Content-Range",
                                       "bytes */" + fileLength);
                    response.sendError
                        (HttpServletResponse
                         .SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    return null;
                }

            }

            if (!currentRange.validate()) {
                response.addHeader("Content-Range", "bytes */" + fileLength);
                response.sendError
                    (HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return null;
            }
            result.addElement(currentRange);
        }
        return result;
    }



    /**
     * 返回InputStream 到该目录内容的HTML表示形式.
     */
    protected InputStream render
        (String contextPath, CacheEntry cacheEntry) {
        InputStream xsltInputStream =
            findXsltInputStream(cacheEntry.context);

        if (xsltInputStream==null) {
            return renderHtml(contextPath, cacheEntry);
        } else {
            return renderXml(contextPath, cacheEntry, xsltInputStream);
        }

    }

    /**
     * 返回一个InputStream到这个目录的内容的XML表示.
     *
     * @param contextPath 内部路径是相对的上下文路径
     */
    protected InputStream renderXml(String contextPath,
                                    CacheEntry cacheEntry,
                                    InputStream xsltInputStream) {

        StringBuffer sb = new StringBuffer();

        sb.append("<?xml version=\"1.0\"?>");
        sb.append("<listing ");
        sb.append(" contextPath='");
        sb.append(contextPath);
        sb.append("'");
        sb.append(" directory='");
        sb.append(cacheEntry.name);
        sb.append("' ");
        sb.append(" hasParent='").append(!cacheEntry.name.equals("/"));
        sb.append("'>");

        sb.append("<entries>");

        try {

            // 在这个目录中呈现目录条目
            DirContext directory = cacheEntry.context;
            NamingEnumeration enumeration = resources.list(cacheEntry.name);
            while (enumeration.hasMoreElements()) {

                NameClassPair ncPair = (NameClassPair) enumeration.nextElement();
                String resourceName = ncPair.getName();
                String trimmed = resourceName/*.substring(trim)*/;
                if (trimmed.equalsIgnoreCase("WEB-INF") ||
                    trimmed.equalsIgnoreCase("META-INF") ||
                    trimmed.equalsIgnoreCase(localXsltFile))
                    continue;

                CacheEntry childCacheEntry =
                    resources.lookupCache(cacheEntry.name + resourceName);
                if (!childCacheEntry.exists) {
                    continue;
                }

                sb.append("<entry");
                sb.append(" type='")
                  .append((childCacheEntry.context != null)?"dir":"file")
                  .append("'");
                sb.append(" urlPath='")
                  .append(rewriteUrl(contextPath))
                  .append(rewriteUrl(cacheEntry.name + resourceName))
                  .append((childCacheEntry.context != null)?"/":"")
                  .append("'");
                if (childCacheEntry.resource != null) {
                    sb.append(" size='")
                      .append(renderSize(childCacheEntry.attributes.getContentLength()))
                      .append("'");
                }
                sb.append(" date='")
                  .append(childCacheEntry.attributes.getLastModifiedHttp())
                  .append("'");

                sb.append(">");
                sb.append(trimmed);
                if (childCacheEntry.context != null)
                    sb.append("/");
                sb.append("</entry>");

            }

        } catch (NamingException e) {
            // Something went wrong
            e.printStackTrace();
        }

        sb.append("</entries>");

        String readme = getReadme(cacheEntry.context);

        if (readme!=null) {
            sb.append("<readme><![CDATA[");
            sb.append(readme);
            sb.append("]]></readme>");
        }


        sb.append("</listing>");


        try {
            TransformerFactory tFactory = TransformerFactory.newInstance();
            Source xmlSource = new StreamSource(new StringReader(sb.toString()));
            Source xslSource = new StreamSource(xsltInputStream);
            Transformer transformer = tFactory.newTransformer(xslSource);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            OutputStreamWriter osWriter = new OutputStreamWriter(stream, "UTF8");
            StreamResult out = new StreamResult(osWriter);
            transformer.transform(xmlSource, out);
            osWriter.flush();
            return (new ByteArrayInputStream(stream.toByteArray()));
        } catch (Exception e) {
            log("directory transform failure: " + e.getMessage());
            return renderHtml(contextPath, cacheEntry);
        }
    }

    /**
     * 返回一个InputStream到这个目录的内容的HTML表示.
     *
     * @param contextPath 内部路径是相对的上下文路径
     */
    protected InputStream renderHtml
        (String contextPath, CacheEntry cacheEntry) {

        String name = cacheEntry.name;

        // Number of characters to trim from the beginnings of filenames
        int trim = name.length();
        if (!name.endsWith("/"))
            trim += 1;
        if (name.equals("/"))
            trim = 1;

        // Prepare a writer to a buffered area
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        OutputStreamWriter osWriter = null;
        try {
            osWriter = new OutputStreamWriter(stream, "UTF8");
        } catch (Exception e) {
            // Should never happen
            osWriter = new OutputStreamWriter(stream);
        }
        PrintWriter writer = new PrintWriter(osWriter);

        StringBuffer sb = new StringBuffer();

        // Render the page header
        sb.append("<html>\r\n");
        sb.append("<head>\r\n");
        sb.append("<title>");
        sb.append(sm.getString("directory.title", name));
        sb.append("</title>\r\n");
        sb.append("<STYLE><!--");
        sb.append(org.apache.catalina.util.TomcatCSS.TOMCAT_CSS);
        sb.append("--></STYLE> ");
        sb.append("</head>\r\n");
        sb.append("<body>");
        sb.append("<h1>");
        sb.append(sm.getString("directory.title", name));

        // Render the link to our parent (if required)
        String parentDirectory = name;
        if (parentDirectory.endsWith("/")) {
            parentDirectory =
                parentDirectory.substring(0, parentDirectory.length() - 1);
        }
        int slash = parentDirectory.lastIndexOf('/');
        if (slash >= 0) {
            String parent = name.substring(0, slash);
            sb.append(" - <a href=\"");
            sb.append(rewriteUrl(contextPath));
            if (parent.equals(""))
                parent = "/";
            sb.append(rewriteUrl(parent));
            if (!parent.endsWith("/"))
                sb.append("/");
            sb.append("\">");
            sb.append("<b>");
            sb.append(sm.getString("directory.parent", parent));
            sb.append("</b>");
            sb.append("</a>");
        }

        sb.append("</h1>");
        sb.append("<HR size=\"1\" noshade=\"noshade\">");

        sb.append("<table width=\"100%\" cellspacing=\"0\"" +
                     " cellpadding=\"5\" align=\"center\">\r\n");

        // Render the column headings
        sb.append("<tr>\r\n");
        sb.append("<td align=\"left\"><font size=\"+1\"><strong>");
        sb.append(sm.getString("directory.filename"));
        sb.append("</strong></font></td>\r\n");
        sb.append("<td align=\"center\"><font size=\"+1\"><strong>");
        sb.append(sm.getString("directory.size"));
        sb.append("</strong></font></td>\r\n");
        sb.append("<td align=\"right\"><font size=\"+1\"><strong>");
        sb.append(sm.getString("directory.lastModified"));
        sb.append("</strong></font></td>\r\n");
        sb.append("</tr>");

        try {
            // Render the directory entries within this directory
            DirContext directory = cacheEntry.context;
            NamingEnumeration enumeration = resources.list(cacheEntry.name);
            boolean shade = false;
            while (enumeration.hasMoreElements()) {

                NameClassPair ncPair = (NameClassPair) enumeration.nextElement();
                String resourceName = ncPair.getName();
                String trimmed = resourceName/*.substring(trim)*/;
                if (trimmed.equalsIgnoreCase("WEB-INF") ||
                    trimmed.equalsIgnoreCase("META-INF"))
                    continue;

                CacheEntry childCacheEntry =
                    resources.lookupCache(cacheEntry.name + resourceName);
                if (!childCacheEntry.exists) {
                    continue;
                }

                sb.append("<tr");
                if (shade)
                    sb.append(" bgcolor=\"#eeeeee\"");
                sb.append(">\r\n");
                shade = !shade;

                sb.append("<td align=\"left\">&nbsp;&nbsp;\r\n");
                sb.append("<a href=\"");
                sb.append(rewriteUrl(contextPath));
                resourceName = rewriteUrl(name + resourceName);
                sb.append(resourceName);
                if (childCacheEntry.context != null)
                    sb.append("/");
                sb.append("\"><tt>");
                sb.append(trimmed);
                if (childCacheEntry.context != null)
                    sb.append("/");
                sb.append("</tt></a></td>\r\n");

                sb.append("<td align=\"right\"><tt>");
                if (childCacheEntry.context != null)
                    sb.append("&nbsp;");
                else
                    sb.append(renderSize(childCacheEntry.attributes.getContentLength()));
                sb.append("</tt></td>\r\n");

                sb.append("<td align=\"right\"><tt>");
                sb.append(childCacheEntry.attributes.getLastModifiedHttp());
                sb.append("</tt></td>\r\n");

                sb.append("</tr>\r\n");
            }
        } catch (NamingException e) {
            // Something went wrong
            e.printStackTrace();
        }

        // Render the page footer
        sb.append("</table>\r\n");

        sb.append("<HR size=\"1\" noshade=\"noshade\">");

        String readme = getReadme(cacheEntry.context);
        if (readme!=null) {
            sb.append(readme);
            sb.append("<HR size=\"1\" noshade=\"noshade\">");
        }

        sb.append("<h3>").append(ServerInfo.getServerInfo()).append("</h3>");
        sb.append("</body>\r\n");
        sb.append("</html>\r\n");

        // 将输入流返回到底层字节
        writer.write(sb.toString());
        writer.flush();
        return (new ByteArrayInputStream(stream.toByteArray()));
    }


    /**
     * 呈现指定的文件大小 (in bytes).
     *
     * @param size File size (in bytes)
     */
    protected String renderSize(long size) {

        long leftSide = size / 1024;
        long rightSide = (size % 1024) / 103;   // Makes 1 digit
        if ((leftSide == 0) && (rightSide == 0) && (size > 0))
            rightSide = 1;

        return ("" + leftSide + "." + rightSide + " kb");

    }


    /**
     * 将自述文件作为字符串获取.
     */
    protected String getReadme(DirContext directory) {
        if (readmeFile!=null) {
            try {
                Object obj = directory.lookup(readmeFile);

                if (obj!=null && obj instanceof Resource) {
                    StringWriter buffer = new StringWriter();
                    InputStream is = ((Resource)obj).streamContent();
                    copyRange(new InputStreamReader(is),
                              new PrintWriter(buffer));

                    return buffer.toString();
                 }
             } catch(Throwable e) {
                 ; /* Should only be IOException or NamingException
                    * can be ignored
                    */
             }
        }
        return null;
    }


    /**
     * 返回的XSL模板输入流
     */
    protected InputStream findXsltInputStream(DirContext directory) {

        if (localXsltFile!=null) {
            try {
                Object obj = directory.lookup(localXsltFile);
                if (obj!=null && obj instanceof Resource) {
                    InputStream is = ((Resource)obj).streamContent();
                    if (is!=null)
                        return is;
                }
             } catch(Throwable e) {
                 ; /* Should only be IOException or NamingException
                    * can be ignored
                    */
             }
        }

        /* 一次打开和读取文件，减少了打开句柄的机会
         */
        if (globalXsltFile!=null) {
            FileInputStream fis = null;

            try {
                File f = new File(globalXsltFile);
                if (f.exists()){
                    fis =new FileInputStream(f);
                    byte b[] = new byte[(int)f.length()]; /* danger! */
                    fis.read(b);
                    return new ByteArrayInputStream(b);
                }
            } catch(Throwable e) {
                log("This shouldn't happen (?)...", e);
                return null;
            } finally {
                try {
                    if (fis!=null)
                        fis.close();
                } catch(Throwable e){
                    ;
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------- Private Methods

    /**
     * 检查是否定期更新可用.
     */
    private boolean checkSendfile(HttpServletRequest request,
                                  HttpServletResponse response,
                                  CacheEntry entry,
                                  long length, Range range) {
        if ((sendfileSize > 0)
            && (entry.resource != null)
            && ((length > sendfileSize) || (entry.resource.getContent() == null))
            && (entry.attributes.getCanonicalPath() != null)
            && (Boolean.TRUE == request.getAttribute("org.apache.tomcat.sendfile.support"))
            && (request.getClass().getName().equals("org.apache.catalina.connector.RequestFacade"))
            && (response.getClass().getName().equals("org.apache.catalina.connector.ResponseFacade"))) {
            request.setAttribute("org.apache.tomcat.sendfile.filename", entry.attributes.getCanonicalPath());
            if (range == null) {
                request.setAttribute("org.apache.tomcat.sendfile.start", new Long(0L));
                request.setAttribute("org.apache.tomcat.sendfile.end", new Long(length));
            } else {
                request.setAttribute("org.apache.tomcat.sendfile.start", new Long(range.start));
                request.setAttribute("org.apache.tomcat.sendfile.end", new Long(range.end + 1));
            }
            request.setAttribute("org.apache.tomcat.sendfile.token", this);
            return true;
        } else {
            return false;
        }
    }
    
    
    /**
     * 检查是否满足If-Match条件.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceInfo File object
     * @return boolean true: 如果资源满足指定条件;
     * false : 如果条件不满足，在这种情况下请求处理停止
     */
    private boolean checkIfMatch(HttpServletRequest request,
                                 HttpServletResponse response,
                                 ResourceAttributes resourceAttributes)
        throws IOException {

        String eTag = getETag(resourceAttributes);
        String headerValue = request.getHeader("If-Match");
        if (headerValue != null) {
            if (headerValue.indexOf('*') == -1) {

                StringTokenizer commaTokenizer = new StringTokenizer
                    (headerValue, ",");
                boolean conditionSatisfied = false;

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

                // If none of the given ETags match, 412 Precodition failed is
                // sent back
                if (!conditionSatisfied) {
                    response.sendError
                        (HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }

            }
        }
        return true;
    }


    /**
     * 检查是否满足if-modified-since条件.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceInfo File object
     * @return boolean true: 如果资源满足指定条件;
     * false : 如果条件不满足，在这种情况下请求处理停止
     */
    private boolean checkIfModifiedSince(HttpServletRequest request,
                                         HttpServletResponse response,
                                         ResourceAttributes resourceAttributes)
        throws IOException {
        try {
            long headerValue = request.getDateHeader("If-Modified-Since");
            long lastModified = resourceAttributes.getLastModified();
            if (headerValue != -1) {

                // If an If-None-Match header has been specified, if modified since
                // is ignored.
                if ((request.getHeader("If-None-Match") == null)
                    && (lastModified <= headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return false;
                }
            }
        } catch(IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;

    }


    /**
     * 检查是否满足if-none-match条件.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceInfo File object
     * @return boolean true: 如果资源满足指定条件;
     * false : 如果条件不满足，在这种情况下请求处理停止
     */
    private boolean checkIfNoneMatch(HttpServletRequest request,
                                     HttpServletResponse response,
                                     ResourceAttributes resourceAttributes)
        throws IOException {

        String eTag = getETag(resourceAttributes);
        String headerValue = request.getHeader("If-None-Match");
        if (headerValue != null) {

            boolean conditionSatisfied = false;

            if (!headerValue.equals("*")) {

                StringTokenizer commaTokenizer =
                    new StringTokenizer(headerValue, ",");

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

            } else {
                conditionSatisfied = true;
            }

            if (conditionSatisfied) {

                // For GET and HEAD, we should respond with
                // 304 Not Modified.
                // For every other method, 412 Precondition Failed is sent
                // back.
                if ( ("GET".equals(request.getMethod()))
                     || ("HEAD".equals(request.getMethod())) ) {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return false;
                } else {
                    response.sendError
                        (HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        }
        return true;
    }


    /**
     * 检查是否满足if-unmodified-since条件.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @param resourceInfo File object
     * @return boolean true: 如果资源满足指定条件;
     * false : 如果条件不满足，在这种情况下请求处理停止
     */
    private boolean checkIfUnmodifiedSince(HttpServletRequest request,
                                           HttpServletResponse response,
                                           ResourceAttributes resourceAttributes)
        throws IOException {
        try {
            long lastModified = resourceAttributes.getLastModified();
            long headerValue = request.getDateHeader("If-Unmodified-Since");
            if (headerValue != -1) {
                if ( lastModified > (headerValue + 1000)) {
                    // 自客户指定的日期起，实体没有被修改. 这不是一个错误案例
                    response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        } catch(IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;

    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流(即使面对一个异常).
     *
     * @param resourceInfo 资源信息
     * @param ostream 要写入的输出流
     *
     * @exception IOException if an input/output error occurs
     */
    private void copy(CacheEntry cacheEntry, InputStream is,
                      ServletOutputStream ostream)
        throws IOException {

        IOException exception = null;
        InputStream resourceInputStream = null;

        // Optimization: 如果二进制内容已经加载, 直接发送它
        if (cacheEntry.resource != null) {
            byte buffer[] = cacheEntry.resource.getContent();
            if (buffer != null) {
                ostream.write(buffer, 0, buffer.length);
                return;
            }
            resourceInputStream = cacheEntry.resource.streamContent();
        } else {
            resourceInputStream = is;
        }

        InputStream istream = new BufferedInputStream
            (resourceInputStream, input);

        // Copy the input stream to the output stream
        exception = copyRange(istream, ostream);

        // Clean up the input stream
        try {
            istream.close();
        } catch (Throwable t) {
            ;
        }

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;
    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流
     * (即使面对一个异常).
     *
     * @param resourceInfo The resource info
     * @param writer The writer to write to
     *
     * @exception IOException if an input/output error occurs
     */
    private void copy(CacheEntry cacheEntry, InputStream is, PrintWriter writer)
        throws IOException {

        IOException exception = null;

        InputStream resourceInputStream = null;
        if (cacheEntry.resource != null) {
            resourceInputStream = cacheEntry.resource.streamContent();
        } else {
            resourceInputStream = is;
        }

        Reader reader;
        if (fileEncoding == null) {
            reader = new InputStreamReader(resourceInputStream);
        } else {
            reader = new InputStreamReader(resourceInputStream,
                                           fileEncoding);
        }

        // Copy the input stream to the output stream
        exception = copyRange(reader, writer);

        // Clean up the reader
        try {
            reader.close();
        } catch (Throwable t) {
            ;
        }

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;

    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流
     * (即使面对一个异常).
     *
     * @param resourceInfo The ResourceInfo object
     * @param ostream 要写入的输出流
     * @param range 要检索的客户端的范围
     * @exception IOException if an input/output error occurs
     */
    private void copy(CacheEntry cacheEntry, ServletOutputStream ostream,
                      Range range)
        throws IOException {

        IOException exception = null;

        InputStream resourceInputStream = cacheEntry.resource.streamContent();
        InputStream istream =
            new BufferedInputStream(resourceInputStream, input);
        exception = copyRange(istream, ostream, range.start, range.end);

        // Clean up the input stream
        try {
            istream.close();
        } catch (Throwable t) {
            ;
        }

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;
    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流
     * (即使面对一个异常).
     *
     * @param resourceInfo The ResourceInfo object
     * @param writer The writer to write to
     * @param range Range the client wanted to retrieve
     * @exception IOException if an input/output error occurs
     */
    private void copy(CacheEntry cacheEntry, PrintWriter writer,
                      Range range)
        throws IOException {

        IOException exception = null;

        InputStream resourceInputStream = cacheEntry.resource.streamContent();

        Reader reader;
        if (fileEncoding == null) {
            reader = new InputStreamReader(resourceInputStream);
        } else {
            reader = new InputStreamReader(resourceInputStream,
                                           fileEncoding);
        }

        exception = copyRange(reader, writer, range.start, range.end);

        // Clean up the input stream
        try {
            reader.close();
        } catch (Throwable t) {
            ;
        }

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;
    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流
     * (即使面对一个异常).
     *
     * @param resourceInfo The ResourceInfo object
     * @param ostream The output stream to write to
     * @param ranges Enumeration of the ranges the client wanted to retrieve
     * @param contentType Content type of the resource
     * @exception IOException if an input/output error occurs
     */
    private void copy(CacheEntry cacheEntry, ServletOutputStream ostream,
                      Enumeration ranges, String contentType)
        throws IOException {

        IOException exception = null;

        while ( (exception == null) && (ranges.hasMoreElements()) ) {

            InputStream resourceInputStream = cacheEntry.resource.streamContent();
            InputStream istream =
                new BufferedInputStream(resourceInputStream, input);

            Range currentRange = (Range) ranges.nextElement();

            // Writing MIME header.
            ostream.println();
            ostream.println("--" + mimeSeparation);
            if (contentType != null)
                ostream.println("Content-Type: " + contentType);
            ostream.println("Content-Range: bytes " + currentRange.start
                           + "-" + currentRange.end + "/"
                           + currentRange.length);
            ostream.println();

            // Printing content
            exception = copyRange(istream, ostream, currentRange.start,
                                  currentRange.end);

            try {
                istream.close();
            } catch (Throwable t) {
                ;
            }

        }

        ostream.println();
        ostream.print("--" + mimeSeparation + "--");

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;

    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流
     * (即使面对一个异常).
     *
     * @param resourceInfo The ResourceInfo object
     * @param writer The writer to write to
     * @param ranges Enumeration of the ranges the client wanted to retrieve
     * @param contentType Content type of the resource
     * @exception IOException if an input/output error occurs
     */
    private void copy(CacheEntry cacheEntry, PrintWriter writer,
                      Enumeration ranges, String contentType)
        throws IOException {

        IOException exception = null;

        while ( (exception == null) && (ranges.hasMoreElements()) ) {

            InputStream resourceInputStream = cacheEntry.resource.streamContent();
            
            Reader reader;
            if (fileEncoding == null) {
                reader = new InputStreamReader(resourceInputStream);
            } else {
                reader = new InputStreamReader(resourceInputStream,
                                               fileEncoding);
            }

            Range currentRange = (Range) ranges.nextElement();

            // Writing MIME header.
            writer.println();
            writer.println("--" + mimeSeparation);
            if (contentType != null)
                writer.println("Content-Type: " + contentType);
            writer.println("Content-Range: bytes " + currentRange.start
                           + "-" + currentRange.end + "/"
                           + currentRange.length);
            writer.println();

            // Printing content
            exception = copyRange(reader, writer, currentRange.start,
                                  currentRange.end);

            try {
                reader.close();
            } catch (Throwable t) {
                ;
            }

        }

        writer.println();
        writer.print("--" + mimeSeparation + "--");

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;
    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流
     * (即使面对一个异常).
     *
     * @param istream The input stream to read from
     * @param ostream The output stream to write to
     * @return Exception which occurred during processing
     */
    private IOException copyRange(InputStream istream,
                                  ServletOutputStream ostream) {

        // Copy the input stream to the output stream
        IOException exception = null;
        byte buffer[] = new byte[input];
        int len = buffer.length;
        while (true) {
            try {
                len = istream.read(buffer);
                if (len == -1)
                    break;
                ostream.write(buffer, 0, len);
            } catch (IOException e) {
                exception = e;
                len = -1;
                break;
            }
        }
        return exception;
    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流
     * (即使面对一个异常).
     *
     * @param reader The reader to read from
     * @param writer The writer to write to
     * @return Exception which occurred during processing
     */
    private IOException copyRange(Reader reader, PrintWriter writer) {

        // Copy the input stream to the output stream
        IOException exception = null;
        char buffer[] = new char[input];
        int len = buffer.length;
        while (true) {
            try {
                len = reader.read(buffer);
                if (len == -1)
                    break;
                writer.write(buffer, 0, len);
            } catch (IOException e) {
                exception = e;
                len = -1;
                break;
            }
        }
        return exception;
    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流
     * (即使面对一个异常).
     *
     * @param istream The input stream to read from
     * @param ostream The output stream to write to
     * @param start Start of the range which will be copied
     * @param end End of the range which will be copied
     * @return Exception which occurred during processing
     */
    private IOException copyRange(InputStream istream,
                                  ServletOutputStream ostream,
                                  long start, long end) {

        if (debug > 10)
            log("Serving bytes:" + start + "-" + end);

        try {
            istream.skip(start);
        } catch (IOException e) {
            return e;
        }

        IOException exception = null;
        long bytesToRead = end - start + 1;

        byte buffer[] = new byte[input];
        int len = buffer.length;
        while ( (bytesToRead > 0) && (len >= buffer.length)) {
            try {
                len = istream.read(buffer);
                if (bytesToRead >= len) {
                    ostream.write(buffer, 0, len);
                    bytesToRead -= len;
                } else {
                    ostream.write(buffer, 0, (int) bytesToRead);
                    bytesToRead = 0;
                }
            } catch (IOException e) {
                exception = e;
                len = -1;
            }
            if (len < buffer.length)
                break;
        }
        return exception;
    }


    /**
     * 将指定输入流的内容复制到指定的输出流, 并确保在返回之前关闭两个流
     * (即使面对一个异常).
     *
     * @param reader The reader to read from
     * @param writer The writer to write to
     * @param start Start of the range which will be copied
     * @param end End of the range which will be copied
     * @return Exception which occurred during processing
     */
    private IOException copyRange(Reader reader, PrintWriter writer,
                                  long start, long end) {

        try {
            reader.skip(start);
        } catch (IOException e) {
            return e;
        }

        IOException exception = null;
        long bytesToRead = end - start + 1;

        char buffer[] = new char[input];
        int len = buffer.length;
        while ( (bytesToRead > 0) && (len >= buffer.length)) {
            try {
                len = reader.read(buffer);
                if (bytesToRead >= len) {
                    writer.write(buffer, 0, len);
                    bytesToRead -= len;
                } else {
                    writer.write(buffer, 0, (int) bytesToRead);
                    bytesToRead = 0;
                }
            } catch (IOException e) {
                exception = e;
                len = -1;
            }
            if (len < buffer.length)
                break;
        }
        return exception;
    }



    // ------------------------------------------------------ Range Inner Class


    private class Range {

        public long start;
        public long end;
        public long length;

        /**
         * Validate range.
         */
        public boolean validate() {
            if (end >= length)
                end = length - 1;
            return ( (start >= 0) && (end >= 0) && (start <= end)
                     && (length > 0) );
        }

        public void recycle() {
            start = 0;
            end = 0;
            length = 0;
        }
    }
}
