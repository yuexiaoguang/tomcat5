package org.apache.catalina.realm;


import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

import javax.management.Attribute;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Realm;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.util.HexUtils;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.MD5Encoder;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.modeler.Registry;

/**
 * <b>Realm</b>实现类， 读取XML文件以配置有效用户、密码和角色.
 * 文件格式（和默认文件位置）与当前由Tomcat 3支持的文件格式相同.
 */
public abstract class RealmBase implements Lifecycle, Realm, MBeanRegistration {

    private static Log log = LogFactory.getLog(RealmBase.class);

    // ----------------------------------------------------- Instance Variables


    /**
     * 关联的Container.
     */
    protected Container container = null;


    /**
     * Container log
     */
    protected Log containerLog = null;


    /**
     * 用于以非明文格式存储密码的摘要算法.
     * 有效值是MessageDigest接受的算法名称, 或<code>null</code>.
     */
    protected String digest = null;

    /**
     * 摘要算法的编码字符集.
     */
    protected String digestEncoding = null;


    /**
     * 描述信息
     */
    protected static final String info =
        "org.apache.catalina.realm.RealmBase/1.0";


    /**
     * 生命周期事件支持
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * 解析用户凭据（密码）的MessageDigest对象.
     */
    protected MessageDigest md = null;


    /**
     * MD5对象.
     */
    protected static final MD5Encoder md5Encoder = new MD5Encoder();


    /**
     * MD5信息摘要提供者.
     */
    protected static MessageDigest md5Helper;


    /**
     * The string manager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 是否启动?
     */
    protected boolean started = false;


    /**
     * 属性修改支持
     */
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * 当客户端证书链出现时，是否应该验证?
     */
    protected boolean validate = true;


    // ------------------------------------------------------------- Properties


    /**
     * 返回关联的Container.
     */
    public Container getContainer() {
        return (container);
    }


    /**
     * 设置关联的Container.
     *
     * @param container The associated Container
     */
    public void setContainer(Container container) {
        Container oldContainer = this.container;
        this.container = container;
        this.containerLog = container.getLogger();
        support.firePropertyChange("container", oldContainer, this.container);
    }

    /**
     * 返回用于存储凭据的摘要算法.
     */
    public String getDigest() {
        return digest;
    }


    /**
     * 设置用于存储凭据的摘要算法
     *
     * @param digest The new digest algorithm
     */
    public void setDigest(String digest) {

        this.digest = digest;

    }

    /**
     * 返回摘要算法的编码字符集.
     *
     * @return 字符集(平台默认 null)
     */
    public String getDigestEncoding() {
        return digestEncoding;
    }

    /**
     * 设置摘要算法的编码字符集.
     *
     * @param 字符集(平台默认 null)
     */
    public void setDigestEncoding(String charset) {
        digestEncoding = charset;
    }

    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return info;
    }


    /**
     * 返回“验证证书链”标志.
     */
    public boolean getValidate() {
        return (this.validate);
    }


    /**
     * 设置“验证证书链”标志.
     *
     * @param validate The new validate certificate chains flag
     */
    public void setValidate(boolean validate) {
        this.validate = validate;
    }


    // --------------------------------------------------------- Public Methods


    
    /**
     * 添加属性修改监听器.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * 返回指定用户名和凭据关联的Principal; 或者<code>null</code>.
     *
     * @param username 要查找的Principal 用户名
     * @param credentials 要验证的用户名的Password或其它凭据
     */
    public Principal authenticate(String username, String credentials) {
        String serverCredentials = getPassword(username);

        boolean validated ;
        if ( serverCredentials == null ) {
            validated = false;
        } else if(hasMessageDigest()) {
            validated = serverCredentials.equalsIgnoreCase(digest(credentials));
        } else {
            validated = serverCredentials.equals(credentials);
        }
        if(! validated ) {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace(sm.getString("realmBase.authenticateFailure",
                                                username));
            }
            return null;
        }
        if (containerLog.isTraceEnabled()) {
            containerLog.trace(sm.getString("realmBase.authenticateSuccess",
                                            username));
        }
        return getPrincipal(username);
    }


    /**
     * 返回指定用户名和凭据关联的Principal; 或者<code>null</code>.
     *
     * @param username 要查找的Principal 用户名
     * @param credentials 要验证的用户名的Password或其它凭据
     */
    public Principal authenticate(String username, byte[] credentials) {
        return (authenticate(username, credentials.toString()));
    }


    /**
     * 返回指定用户名关联的Principal, 使用RFC 2617中描述的方法, 使用给定参数匹配计算的摘要;或者<code>null</code>.
     *
     * @param username Username of the Principal to look up
     * @param clientDigest 客户提交的摘要
     * @param nOnce 用于此请求的唯一（或可能是唯一的）令牌
     * @param realm Realm name
     * @param md5a2 第二个MD5用于计算摘要 : MD5(Method + ":" + uri)
     */
    public Principal authenticate(String username, String clientDigest,
                                  String nOnce, String nc, String cnonce,
                                  String qop, String realm,
                                  String md5a2) {

        String md5a1 = getDigest(username, realm);
        if (md5a1 == null)
            return null;
        String serverDigestValue = md5a1 + ":" + nOnce + ":" + nc + ":"
            + cnonce + ":" + qop + ":" + md5a2;

        byte[] valueBytes = null;
        if(getDigestEncoding() == null) {
            valueBytes = serverDigestValue.getBytes();
        } else {
            try {
                valueBytes = serverDigestValue.getBytes(getDigestEncoding());
            } catch (UnsupportedEncodingException uee) {
                log.error("Illegal digestEncoding: " + getDigestEncoding(), uee);
                throw new IllegalArgumentException(uee.getMessage());
            }
        }

        String serverDigest = null;
        // Bugzilla 32137
        synchronized(md5Helper) {
            serverDigest = md5Encoder.encode(md5Helper.digest(valueBytes));
        }

        if (log.isDebugEnabled()) {
            log.debug("Digest : " + clientDigest + " Username:" + username 
                    + " ClientSigest:" + clientDigest + " nOnce:" + nOnce 
                    + " nc:" + nc + " cnonce:" + cnonce + " qop:" + qop 
                    + " realm:" + realm + "md5a2:" + md5a2 
                    + " Server digest:" + serverDigest);
        }
        
        if (serverDigest.equals(clientDigest))
            return getPrincipal(username);
        else
            return null;
    }



    /**
     * 指定X509客户端证书链关联的Principal.  如果没有，返回<code>null</code>.
     *
     * @param certs 客户端证书数组, 数组中的第一个是客户端本身的证书.
     */
    public Principal authenticate(X509Certificate certs[]) {

        if ((certs == null) || (certs.length < 1))
            return (null);

        // 检查链中每个证书的有效性
        if (log.isDebugEnabled())
            log.debug("Authenticating client certificate chain");
        if (validate) {
            for (int i = 0; i < certs.length; i++) {
                if (log.isDebugEnabled())
                    log.debug(" Checking validity for '" +
                        certs[i].getSubjectDN().getName() + "'");
                try {
                    certs[i].checkValidity();
                } catch (Exception e) {
                    if (log.isDebugEnabled())
                        log.debug("  Validity exception", e);
                    return (null);
                }
            }
        }
        // 检查数据库中客户端Principal 是否存在
        return (getPrincipal(certs[0].getSubjectDN().getName()));
    }

    
    /**
     * 执行周期任务, 例如重新加载, etc. 该方法将在该容器的类加载上下文中被调用.
     * 异常将被捕获和记录.
     */
    public void backgroundProcess() {
    }


    /**
     * 返回配置用于保护请求URI的SecurityConstraint, 或<code>null</code>如果没有约束.
     *
     * @param request 处理的请求
     * @param context 请求映射的上下文
     */
    public SecurityConstraint [] findSecurityConstraints(Request request,
                                                         Context context) {

        ArrayList results = null;
        // 是否定义了安全约束?
        SecurityConstraint constraints[] = context.findConstraints();
        if ((constraints == null) || (constraints.length == 0)) {
            if (log.isDebugEnabled())
                log.debug("  No applicable constraints defined");
            return (null);
        }

        // 检查每个定义的安全约束
        String uri = request.getRequestPathMB().toString();
        
        String method = request.getMethod();
        int i;
        boolean found = false;
        for (i = 0; i < constraints.length; i++) {
            SecurityCollection [] collection = constraints[i].findCollections();
                     
            // If collection is null, 继续以避免 NPE
            // See Bugzilla 30624
            if ( collection == null) {
            	continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("  Checking constraint '" + constraints[i] +
                    "' against " + method + " " + uri + " --> " +
                    constraints[i].included(uri, method));
	    }

            for(int j=0; j < collection.length; j++){
                String [] patterns = collection[j].findPatterns();
 
                // If patterns is null, 继续以避免 NPE
                // See Bugzilla 30624
                if ( patterns == null) {
		    continue;
                }

                for(int k=0; k < patterns.length; k++) {
                    if(uri.equals(patterns[k])) {
                        found = true;
                        if(collection[j].findMethod(method)) {
                            if(results == null) {
                                results = new ArrayList();
                            }
                            results.add(constraints[i]);
                        }
                    }
                }
            }
        }

        if(found) {
            return resultsToArray(results);
        }

        int longest = -1;

        for (i = 0; i < constraints.length; i++) {
            SecurityCollection [] collection = constraints[i].findCollections();
            
            // If collection is null, 继续以避免 NPE
            // See Bugzilla 30624
            if ( collection == null) {
		continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("  Checking constraint '" + constraints[i] +
                    "' against " + method + " " + uri + " --> " +
                    constraints[i].included(uri, method));
	    }

            for(int j=0; j < collection.length; j++){
                String [] patterns = collection[j].findPatterns();

                // If patterns is null, 继续以避免 NPE
                // See Bugzilla 30624
                if ( patterns == null) {
		    continue;
                }

                boolean matched = false;
                int length = -1;
                for(int k=0; k < patterns.length; k++) {
                    String pattern = patterns[k];
                    if(pattern.startsWith("/") && pattern.endsWith("/*") && 
                       pattern.length() >= longest) {
                            
                        if(pattern.length() == 2) {
                            matched = true;
                            length = pattern.length();
                        } else if(pattern.regionMatches(0,uri,0,
                                                        pattern.length()-1) ||
                                  (pattern.length()-2 == uri.length() &&
                                   pattern.regionMatches(0,uri,0,
                                                        pattern.length()-2))) {
                            matched = true;
                            length = pattern.length();
                        }
                    }
                }
                if(matched) {
                    found = true;
                    if(length > longest) {
                        if(results != null) {
                            results.clear();
                        }
                        longest = length;
                    }
                    if(collection[j].findMethod(method)) {
                        if(results == null) {
                            results = new ArrayList();
                        }
                        results.add(constraints[i]);
                    }
                }
            }
        }

        if(found) {
            return  resultsToArray(results);
        }

        for (i = 0; i < constraints.length; i++) {
            SecurityCollection [] collection = constraints[i].findCollections();

            // If collection is null, 继续以避免 NPE
            // See Bugzilla 30624
            if ( collection == null) {
		continue;
            }
            
            if (log.isDebugEnabled()) {
                log.debug("  Checking constraint '" + constraints[i] +
                    "' against " + method + " " + uri + " --> " +
                    constraints[i].included(uri, method));
	    }

            boolean matched = false;
            int pos = -1;
            for(int j=0; j < collection.length; j++){
                String [] patterns = collection[j].findPatterns();

                // If patterns is null, 继续以避免 NPE
                // See Bugzilla 30624
                if ( patterns == null) {
		    continue;
                }

                for(int k=0; k < patterns.length && !matched; k++) {
                    String pattern = patterns[k];
                    if(pattern.startsWith("*.")){
                        int slash = uri.lastIndexOf("/");
                        int dot = uri.lastIndexOf(".");
                        if(slash >= 0 && dot > slash &&
                           dot != uri.length()-1 &&
                           uri.length()-dot == pattern.length()-1) {
                            if(pattern.regionMatches(1,uri,dot,uri.length()-dot)) {
                                matched = true;
                                pos = j;
                            }
                        }
                    }
                }
            }
            if(matched) {
                found = true;
                if(collection[pos].findMethod(method)) {
                    if(results == null) {
                        results = new ArrayList();
                    }
                    results.add(constraints[i]);
                }
            }
        }

        if(found) {
            return resultsToArray(results);
        }

        for (i = 0; i < constraints.length; i++) {
            SecurityCollection [] collection = constraints[i].findCollections();
            
            // If collection is null, 继续以避免 NPE
            // See Bugzilla 30624
            if ( collection == null) {
		continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("  Checking constraint '" + constraints[i] +
                    "' against " + method + " " + uri + " --> " +
                    constraints[i].included(uri, method));
	    }

            for(int j=0; j < collection.length; j++){
                String [] patterns = collection[j].findPatterns();

                // If patterns is null, 继续以避免 NPE
                // See Bugzilla 30624
                if ( patterns == null) {
		    continue;
                }

                boolean matched = false;
                for(int k=0; k < patterns.length && !matched; k++) {
                    String pattern = patterns[k];
                    if(pattern.equals("/")){
                        matched = true;
                    }
                }
                if(matched) {
                    if(results == null) {
                        results = new ArrayList();
                    }                    
                    results.add(constraints[i]);
                }
            }
        }

        if(results == null) {
            // 没有安全约束
            if (log.isDebugEnabled())
                log.debug("  No applicable constraint located");
        }
        return resultsToArray(results);
    }
 
    /**
     * 将ArrayList转换为 SecurityContraint [].
     */
    private SecurityConstraint [] resultsToArray(ArrayList results) {
        if(results == null) {
            return null;
        }
        SecurityConstraint [] array = new SecurityConstraint[results.size()];
        results.toArray(array);
        return array;
    }

    
    /**
     * 根据指定的授权约束执行访问控制.
     * 返回<code>true</code> 如果满足此约束，则处理将继续进行, 否则返回<code>false</code>.
     *
     * @param request Request we are processing
     * @param response Response we are creating
     * @param constraints 正在执行的安全约束
     * @param context 这个类的客户端所附的上下文.
     *
     * @exception IOException 如果发生输入/输出错误
     */
    public boolean hasResourcePermission(Request request,
                                         Response response,
                                         SecurityConstraint []constraints,
                                         Context context)
        throws IOException {

        if (constraints == null || constraints.length == 0)
            return (true);

        // 特别允许访问表单登录和表单错误页和 "j_security_check"
        LoginConfig config = context.getLoginConfig();
        if ((config != null) &&
            (Constants.FORM_METHOD.equals(config.getAuthMethod()))) {
            String requestURI = request.getRequestPathMB().toString();
            String loginPage = config.getLoginPage();
            if (loginPage.equals(requestURI)) {
                if (log.isDebugEnabled())
                    log.debug(" Allow access to login page " + loginPage);
                return (true);
            }
            String errorPage = config.getErrorPage();
            if (errorPage.equals(requestURI)) {
                if (log.isDebugEnabled())
                    log.debug(" Allow access to error page " + errorPage);
                return (true);
            }
            if (requestURI.endsWith(Constants.FORM_ACTION)) {
                if (log.isDebugEnabled())
                    log.debug(" Allow access to username/password submission");
                return (true);
            }
        }

        // Which user principal have we already authenticated?
        Principal principal = request.getUserPrincipal();
        for(int i=0; i < constraints.length; i++) {
            SecurityConstraint constraint = constraints[i];
            String roles[] = constraint.findAuthRoles();
            if (roles == null)
                roles = new String[0];

            if (constraint.getAllRoles())
                return (true);

            if (log.isDebugEnabled())
                log.debug("  Checking roles " + principal);

            if (roles.length == 0) {
                if(constraint.getAuthConstraint()) {
                    response.sendError
                        (HttpServletResponse.SC_FORBIDDEN,
                         sm.getString("realmBase.forbidden"));
                    if( log.isDebugEnabled() )
                        log.debug("No roles ");
                    return (false); // 没有列出的角色意味着根本无法访问
                } else {
                    if(log.isDebugEnabled())
                        log.debug("Passing all access");
                    return (true);
                }
            } else if (principal == null) {
                if (log.isDebugEnabled())
                    log.debug("  No user authenticated, cannot grant access");
                response.sendError
                    (HttpServletResponse.SC_FORBIDDEN,
                     sm.getString("realmBase.notAuthenticated"));
                return (false);
            }


            for (int j = 0; j < roles.length; j++) {
                if (hasRole(principal, roles[j]))
                    return (true);
                if( log.isDebugEnabled() )
                    log.debug( "No role found:  " + roles[j]);
            }
        }
        // 返回一个"Forbidden"信息， 拒绝访问此资源
        response.sendError(HttpServletResponse.SC_FORBIDDEN, sm.getString("realmBase.forbidden"));
        return (false);
    }
    
    
    /**
     * 返回<code>true</code>，如果指定的Principal拥有指定的安全角色, 在这个Realm上下文中; 
     * 否则返回<code>false</code>.
     * 这个方法可以被Realm实现类覆盖, 但默认情况下是足够的，<code>GenericPrincipal</code>用于表示认证的Principals.
     *
     * @param principal 要验证角色的Principal
     * @param role Security role to be checked
     */
    public boolean hasRole(Principal principal, String role) {

        // 是否在JAASRealm中重写 - 避免相当低效的转换
        if ((principal == null) || (role == null) ||
            !(principal instanceof GenericPrincipal))
            return (false);

        GenericPrincipal gp = (GenericPrincipal) principal;
        if (!(gp.getRealm() == this)) {
            if(log.isDebugEnabled())
                log.debug("Different realm " + this + " " + gp.getRealm());//    return (false);
        }
        boolean result = gp.hasRole(role);
        if (log.isDebugEnabled()) {
            String name = principal.getName();
            if (result)
                log.debug(sm.getString("realmBase.hasRoleSuccess", name, role));
            else
                log.debug(sm.getString("realmBase.hasRoleFailure", name, role));
        }
        return (result);

    }

    
    /**
     * 通过安全约束强制保护该请求URI所需的任何用户数据约束. 
     * 返回<code>true</code>如果该约束未被违反，则处理将继续进行, 或者<code>false</code>
     *
     * @param request Request we are processing
     * @param response Response we are creating
     * @param constraints 正在检查的安全约束
     *
     * @exception IOException 如果发生输入/输出错误
     */
    public boolean hasUserDataPermission(Request request,
                                         Response response,
                                         SecurityConstraint []constraints)
        throws IOException {

        // 是否有相关的用户数据约束?
        if (constraints == null || constraints.length == 0) {
            if (log.isDebugEnabled())
                log.debug("  No applicable security constraint defined");
            return (true);
        }
        for(int i=0; i < constraints.length; i++) {
            SecurityConstraint constraint = constraints[i];
            String userConstraint = constraint.getUserConstraint();
            if (userConstraint == null) {
                if (log.isDebugEnabled())
                    log.debug("  No applicable user data constraint defined");
                return (true);
            }
            if (userConstraint.equals(Constants.NONE_TRANSPORT)) {
                if (log.isDebugEnabled())
                    log.debug("  User data constraint has no restrictions");
                return (true);
            }

        }
        // 针对用户数据约束验证请求
        if (request.getRequest().isSecure()) {
            if (log.isDebugEnabled())
                log.debug("  User data constraint already satisfied");
            return (true);
        }
        // 初始化需要确定适当操作的变量
        int redirectPort = request.getConnector().getRedirectPort();

        // 正在重定向禁用?
        if (redirectPort <= 0) {
            if (log.isDebugEnabled())
                log.debug("  SSL redirect is disabled");
            response.sendError
                (HttpServletResponse.SC_FORBIDDEN,
                 request.getRequestURI());
            return (false);
        }

        // 重定向到相应的 SSL 端口
        StringBuffer file = new StringBuffer();
        String protocol = "https";
        String host = request.getServerName();
        // Protocol
        file.append(protocol).append("://");
        // Host with port
        file.append(host).append(":").append(redirectPort);
        // URI
        file.append(request.getRequestURI());
        String requestedSessionId = request.getRequestedSessionId();
        if ((requestedSessionId != null) &&
            request.isRequestedSessionIdFromURL()) {
            file.append(";jsessionid=");
            file.append(requestedSessionId);
        }
        String queryString = request.getQueryString();
        if (queryString != null) {
            file.append('?');
            file.append(queryString);
        }
        if (log.isDebugEnabled())
            log.debug("  Redirecting to " + file.toString());
        response.sendRedirect(file.toString());
        return (false);
    }
    
    
    /**
     * 移除属性修改监听器.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * 添加一个生命周期事件监听器.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }


    /**
     * 获取关联的生命周期监听器.
     * 如果没有, 返回零长度数组.
     */
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }


    /**
     * 移除一个生命周期事件监听器.
     *
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }

    /**
     * 这个方法应该第一个登录.
     * 它将发送一个START_EVENT类型的LifecycleEvent事件给所有监听器.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    public void start() throws LifecycleException {

        // Validate and update our current component state
        if (started) {
            if(log.isInfoEnabled())
                log.info(sm.getString("realmBase.alreadyStarted"));
            return;
        }
        if( !initialized ) {
            init();
        }
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        // Create a MessageDigest instance for credentials, if desired
        if (digest != null) {
            try {
                md = MessageDigest.getInstance(digest);
            } catch (NoSuchAlgorithmException e) {
                throw new LifecycleException
                    (sm.getString("realmBase.algorithm", digest), e);
            }
        }
    }


    /**
     * 这个方法应该最后一个调用.
     * 它将发送一个STOP_EVENT类型的LifecycleEvent事件给所有监听器.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public void stop()
        throws LifecycleException {

        // Validate and update our current component state
        if (!started) {
            if(log.isInfoEnabled())
                log.info(sm.getString("realmBase.notStarted"));
            return;
        }
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        // Clean up allocated resources
        md = null;
        
        destroy();
    }
    
    public void destroy() {
        // unregister this realm
        if ( oname!=null ) {   
            try {   
                Registry.getRegistry(null, null).unregisterComponent(oname); 
                if(log.isDebugEnabled())
                    log.debug( "unregistering realm " + oname );   
            } catch( Exception ex ) {   
                log.error( "Can't unregister realm " + oname, ex);   
            }      
        }
    }

    // ------------------------------------------------------ Protected Methods


    /**
     * 使用指定的算法解析密码并将结果转换为相应的十六进制字符串.
     * 如果异常，则返回明文凭据字符串.
     *
     * @param credentials 验证这个用户名的Password 或其它凭据
     */
    protected String digest(String credentials)  {

        // 如果没有指定MessageDigest 实例, return unchanged
        if (hasMessageDigest() == false)
            return (credentials);

        // 加密用户凭据并作为十六进制返回
        synchronized (this) {
            try {
                md.reset();
    
                byte[] bytes = null;
                if(getDigestEncoding() == null) {
                    bytes = credentials.getBytes();
                } else {
                    try {
                        bytes = credentials.getBytes(getDigestEncoding());
                    } catch (UnsupportedEncodingException uee) {
                        log.error("Illegal digestEncoding: " + getDigestEncoding(), uee);
                        throw new IllegalArgumentException(uee.getMessage());
                    }
                }
                md.update(bytes);

                return (HexUtils.convert(md.digest()));
            } catch (Exception e) {
                log.error(sm.getString("realmBase.digest"), e);
                return (credentials);
            }
        }
    }

    protected boolean hasMessageDigest() {
        return !(md == null);
    }

    /**
     * 返回给定用户名关联的摘要.
     */
    protected String getDigest(String username, String realmName) {
        if (md5Helper == null) {
            try {
                md5Helper = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                log.error("Couldn't get MD5 digest: ", e);
                throw new IllegalStateException(e.getMessage());
            }
        }

    	if (hasMessageDigest()) {
    		// Use pre-generated digest
    		return getPassword(username);
    	}
    	
        String digestValue = username + ":" + realmName + ":"
            + getPassword(username);

        byte[] valueBytes = null;
        if(getDigestEncoding() == null) {
            valueBytes = digestValue.getBytes();
        } else {
            try {
                valueBytes = digestValue.getBytes(getDigestEncoding());
            } catch (UnsupportedEncodingException uee) {
                log.error("Illegal digestEncoding: " + getDigestEncoding(), uee);
                throw new IllegalArgumentException(uee.getMessage());
            }
        }

        byte[] digest = null;
        // Bugzilla 32137
        synchronized(md5Helper) {
            digest = md5Helper.digest(valueBytes);
        }

        return md5Encoder.encode(digest);
    }


    /**
     * 返回Realm实现类的名称, 用于日志记录.
     */
    protected abstract String getName();


    /**
     * 返回指定用户名关联的密码.
     */
    protected abstract String getPassword(String username);


    /**
     * 返回指定用户名关联的Principal.
     */
    protected abstract Principal getPrincipal(String username);


    // --------------------------------------------------------- Static Methods


    /**
     * 摘要使用密码算法especificied并将结果转换为相应的字符串.
     * 如果异常，则返回明文凭据字符串
     *
     * @param credentials 验证这个用户名的Password或其它凭据
     * @param algorithm 用于加密的算法
     * @param encoding 要加密的字符串的字符编码
     */
    public final static String Digest(String credentials, String algorithm,
                                      String encoding) {

        try {
            // 用“摘要”加密获取新的消息摘要
            MessageDigest md =
                (MessageDigest) MessageDigest.getInstance(algorithm).clone();

            // 编码凭据
            // 应该使用 digestEncoding, 但是没有静态字段
            if (encoding == null) {
                md.update(credentials.getBytes());
            } else {
                md.update(credentials.getBytes(encoding));                
            }

            // 加密凭据并以十六进制返回
            return (HexUtils.convert(md.digest()));
        } catch(Exception ex) {
            log.error(ex);
            return credentials;
        }
    }


    /**
     * 摘要使用密码算法especificied并将结果转换为相应的字符串.
     * 如果异常，则返回明文凭据字符串
     */
    public static void main(String args[]) {

        String encoding = null;
        int firstCredentialArg = 2;
        
        if (args.length > 4 && args[2].equalsIgnoreCase("-e")) {
            encoding = args[3];
            firstCredentialArg = 4;
        }
        
        if(args.length > firstCredentialArg && args[0].equalsIgnoreCase("-a")) {
            for(int i=firstCredentialArg; i < args.length ; i++){
                System.out.print(args[i]+":");
                System.out.println(Digest(args[i], args[1], encoding));
            }
        } else {
            System.out.println
                ("Usage: RealmBase -a <algorithm> [-e <encoding>] <credentials>");
        }
    }


    // -------------------- JMX and Registration  --------------------
    protected String type;
    protected String domain;
    protected String host;
    protected String path;
    protected ObjectName oname;
    protected ObjectName controller;
    protected MBeanServer mserver;

    public ObjectName getController() {
        return controller;
    }

    public void setController(ObjectName controller) {
        this.controller = controller;
    }

    public ObjectName getObjectName() {
        return oname;
    }

    public String getDomain() {
        return domain;
    }

    public String getType() {
        return type;
    }

    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
        oname=name;
        mserver=server;
        domain=name.getDomain();

        type=name.getKeyProperty("type");
        host=name.getKeyProperty("host");
        path=name.getKeyProperty("path");

        return name;
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() throws Exception {
    }

    public void postDeregister() {
    }

    protected boolean initialized=false;
    
    public void init() {
        if( initialized && container != null ) return;
        
        initialized=true;
        if( container== null ) {
            ObjectName parent=null;
            // Register with the parent
            try {
                if( host == null ) {
                    // global
                    parent=new ObjectName(domain +":type=Engine");
                } else if( path==null ) {
                    parent=new ObjectName(domain +
                            ":type=Host,host=" + host);
                } else {
                    parent=new ObjectName(domain +":j2eeType=WebModule,name=//" +
                            host + path);
                }
                if( mserver.isRegistered(parent ))  {
                    if(log.isDebugEnabled())
                        log.debug("Register with " + parent);
                    mserver.setAttribute(parent, new Attribute("realm", this));
                }
            } catch (Exception e) {
                log.error("Parent not available yet: " + parent);  
            }
        }
        
        if( oname==null ) {
            // register
            try {
                ContainerBase cb=(ContainerBase)container;
                oname=new ObjectName(cb.getDomain()+":type=Realm" + cb.getContainerSuffix());
                Registry.getRegistry(null, null).registerComponent(this, oname, null );
                if(log.isDebugEnabled())
                    log.debug("Register Realm "+oname);
            } catch (Throwable e) {
                log.error( "Can't register " + oname, e);
            }
        }
    }
}
