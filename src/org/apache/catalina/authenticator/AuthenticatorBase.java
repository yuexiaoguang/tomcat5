package org.apache.catalina.authenticator;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;

import org.apache.catalina.Authenticator;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Realm;
import org.apache.catalina.Session;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.util.DateTool;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <b>Valve</b>接口的基础实现类，强制执行Web应用程序部署描述符中的<code>&lt;security-constraint&gt;</code>元素. 
 * 此功能是作为Valve实现的,因此可忽略的环境中不需要这些功能.  每个支持的身份验证方法的单独实现可以根据需要对这个基类进行子类划分.
 * <p>
 * <b>使用约束</b>:  当使用这个类时, 附加的Context(或层次结构中的父级Container) 必须有一个关联的Realm，
 * 可用于验证用户以及已分配给它们的角色
 * <p>
 * <b>使用约束</b>: 这个Valve只用于处理HTTP请求.  其他类型的请求都将被直接通过.
 */
public abstract class AuthenticatorBase extends ValveBase implements Authenticator, Lifecycle {
    private static Log log = LogFactory.getLog(AuthenticatorBase.class);


    // ----------------------------------------------------- Instance Variables


    /**
     * 如果不能使用请求的，则使用默认的消息摘要算法
     */
    protected static final String DEFAULT_ALGORITHM = "MD5";


    /**
     * 生成会话标识符时要包含的随机字节数
     */
    protected static final int SESSION_ID_BYTES = 16;


    /**
     * 生成会话标识符时要使用的消息摘要算法. 
     * 它必须被<code>java.security.MessageDigest</code>类支持
     */
    protected String algorithm = DEFAULT_ALGORITHM;


    /**
     * 如果请求是HTTP会话的一部分，是否应该缓存经过身份验证的Principal？
     */
    protected boolean cache = true;


    /**
     * 关联的Context
     */
    protected Context context = null;


    /**
     * 返回用于创建session的ID的MessageDigest实现类
     */
    protected MessageDigest digest = null;


    /**
     * 一个字符串初始化参数，用于增加随机数生成器初始化的熵
     */
    protected String entropy = null;


    /**
     * 描述信息
     */
    protected static final String info =
        "org.apache.catalina.authenticator.AuthenticatorBase/1.0";

    /**
     * 确定是否禁用代理缓存的标志, 或把问题上升到应用的开发者.
     */
    protected boolean disableProxyCaching = true;

    /**
     * 确定是否禁用与IE不兼容的标头的代理缓存
     */
    protected boolean securePagesWithPragma = true;
    
    /**
     * 生命周期事件支持
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * 生成会话标识符时使用的随机数生成器
     */
    protected Random random = null;


    /**
     * 随机数生成器的Java类名称,当生成session的ID时使用
     */
    protected String randomClass = "java.security.SecureRandom";


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 请求处理链中的SingleSignOn 实现类
     */
    protected SingleSignOn sso = null;


    /**
     * 是否启动?
     */
    protected boolean started = false;


    /**
     * "Expires" 标头总是设置为 Date(1), 因此只生成一次
     */
    private static final String DATE_ONE =
        (new SimpleDateFormat(DateTool.HTTP_RESPONSE_DATE_HEADER,
                              Locale.US)).format(new Date(1));


    // ------------------------------------------------------------- Properties


    /**
     * 返回此管理器的消息摘要算法
     */
    public String getAlgorithm() {
        return (this.algorithm);
    }


    /**
     * 设置此管理器的消息摘要算法
     *
     * @param algorithm The new message digest algorithm
     */
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }


    /**
     * 返回是否缓存已验证的Principal
     */
    public boolean getCache() {
        return (this.cache);
    }


    /**
     * 设置是否缓存已验证的Principal
     *
     * @param cache The new cache flag
     */
    public void setCache(boolean cache) {
        this.cache = cache;
    }


    /**
     * 返回关联的Container
     */
    public Container getContainer() {
        return (this.context);
    }


    /**
     * 设置关联的Container
     *
     * @param container The container to which we are attached
     */
    public void setContainer(Container container) {
        if (!(container instanceof Context))
            throw new IllegalArgumentException
                (sm.getString("authenticator.notContext"));

        super.setContainer(container);
        this.context = (Context) container;
    }


    /**
     * 返回熵的增加值, 或者如果这个字符串还没有被设置，计算一个半有效的值
     */
    public String getEntropy() {
        // Calculate a semi-useful value if this has not been set
        if (this.entropy == null)
            setEntropy(this.toString());

        return (this.entropy);
    }


    /**
     * 设置熵的增加值
     *
     * @param entropy The new entropy increaser value
     */
    public void setEntropy(String entropy) {
        this.entropy = entropy;
    }


    /**
     * 返回描述信息
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 返回随机数生成器类名
     */
    public String getRandomClass() {
        return (this.randomClass);
    }


    /**
     * 设置随机数生成器类名
     *
     * @param randomClass 随机数生成器类名
     */
    public void setRandomClass(String randomClass) {
        this.randomClass = randomClass;
    }

    /**
     * 是否添加标头来禁用代理缓存.
     */
    public boolean getDisableProxyCaching() {
        return disableProxyCaching;
    }

    /**
     * 是否添加标头来禁用代理缓存.
     * 
     * @param nocache <code>true</code> 如果添加标头以禁用代理缓存, 否则<code>false</code>
     */
    public void setDisableProxyCaching(boolean nocache) {
        disableProxyCaching = nocache;
    }
    
    /**
     * 返回是否禁用与IE不兼容的标头的代理缓存的标志.
     */
    public boolean getSecurePagesWithPragma() {
        return securePagesWithPragma;
    }

    /**
     * 设置是否禁用与IE不兼容的标头的代理缓存的标志.
     * 
     * @param securePagesWithPragma <code>true</code>如果添加了与SSL下的IE Office文档不兼容的标头，但它解决了Mozilla中的缓存问题.
     */
    public void setSecurePagesWithPragma(boolean securePagesWithPragma) {
        this.securePagesWithPragma = securePagesWithPragma;
    }    

    // --------------------------------------------------------- Public Methods


    /**
     * 在关联上下文的Web应用程序部署描述符中强制执行安全限制
     *
     * @param request Request to be processed
     * @param response Response to be processed
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果处理的元素抛出此异常
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        if (log.isDebugEnabled())
            log.debug("Security checking request " +
                request.getMethod() + " " + request.getRequestURI());
        LoginConfig config = this.context.getLoginConfig();

        // 有一个缓存的经过身份验证的Principal来记录吗?
        if (cache) {
            Principal principal = request.getUserPrincipal();
            if (principal == null) {
                Session session = request.getSessionInternal(false);
                if (session != null) {
                    principal = session.getPrincipal();
                    if (principal != null) {
                        if (log.isDebugEnabled())
                            log.debug("We have cached auth type " +
                                session.getAuthType() +
                                " for principal " +
                                session.getPrincipal());
                        request.setAuthType(session.getAuthType());
                        request.setUserPrincipal(principal);
                    }
                }
            }
        }

        // 特殊处理基于表单的登录, 
        // 登录表单可能在安全区域外面(因此 "j_security_check" URI到它提交的地方)
        String contextPath = this.context.getPath();
        String requestURI = request.getDecodedRequestURI();
        if (requestURI.startsWith(contextPath) &&
            requestURI.endsWith(Constants.FORM_ACTION)) {
            if (!authenticate(request, response, config)) {
                if (log.isDebugEnabled())
                    log.debug(" Failed authenticate() test ??" + requestURI );
                return;
            }
        }

        Realm realm = this.context.getRealm();
        //这个请求URI受制于安全约束吗?
        SecurityConstraint [] constraints
            = realm.findSecurityConstraints(request, this.context);
       
        if ((constraints == null) /* &&
            (!Constants.FORM_METHOD.equals(config.getAuthMethod())) */ ) {
            if (log.isDebugEnabled())
                log.debug(" Not subject to any constraint");
            getNext().invoke(request, response);
            return;
        }

        // 请确保Web代理或浏览器不缓存受限资源，因为缓存可能有安全漏洞
        if (disableProxyCaching && 
            // FIXME: 对Mozilla禁用了对SSL的支持
            // (improper caching issue)
            //!request.isSecure() &&
            !"POST".equalsIgnoreCase(request.getMethod())) {
            if (securePagesWithPragma) {
                // FIXME: 这些都会导致在SSL下从IE下载Office文档的问题，而对于新的Mozilla客户端可能不需要这些.
                response.setHeader("Pragma", "No-cache");
                response.setHeader("Cache-Control", "no-cache");
            } else {
                response.setHeader("Cache-Control", "private");
            }
            response.setHeader("Expires", DATE_ONE);
        }

        int i;
        // 强制执行此安全约束的任何用户数据约束
        if (log.isDebugEnabled()) {
            log.debug(" Calling hasUserDataPermission()");
        }
        if (!realm.hasUserDataPermission(request, response,
                                         constraints)) {
            if (log.isDebugEnabled()) {
                log.debug(" Failed hasUserDataPermission() test");
            }
            /*
             * ASSERT: Authenticator 已经设置适当的HTTP状态码, 所以不必做任何特别的事情
             */
            return;
        }
       
        for(i=0; i < constraints.length; i++) {
            // Authenticate 基于指定的登录配置
            if (constraints[i].getAuthConstraint()) {
                if (log.isDebugEnabled()) {
                    log.debug(" Calling authenticate()");
                }
                if (!authenticate(request, response, config)) {
                    if (log.isDebugEnabled()) {
                        log.debug(" Failed authenticate() test");
                    }
                    /*
                     * ASSERT: Authenticator 已经设置适当的HTTP状态码, 所以不必做任何特别的事情
                     */
                    return;
                } else {
                    break;
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug(" Calling accessControl()");
        }
        if (!realm.hasResourcePermission(request, response,
                                         constraints,
                                         this.context)) {
            if (log.isDebugEnabled()) {
                log.debug(" Failed accessControl() test");
            }
            /*
             * ASSERT: AccessControl 方法已经设置适当的HTTP状态码, 所以不必做任何特别的事情
             */
            return;
        }
    
        // 满足所有指定约束
        if (log.isDebugEnabled()) {
            log.debug(" Successfully passed all security constraints");
        }
        getNext().invoke(request, response);
    }

    // ------------------------------------------------------ Protected Methods


    /**
     * 将指定的单点登录标识符与指定的会话关联.
     *
     * @param ssoId 单点登录标识符
     * @param session 被关联的会话
     */
    protected void associate(String ssoId, Session session) {
        if (sso == null)
            return;
        sso.associate(ssoId, session);
    }


    /**
     * 根据指定的登录配置对作出此请求的用户进行身份验证.
     * 返回<code>true</code> 如果满足指定的约束, 或者<code>false</code>.
     *
     * @param request Request we are processing
     * @param response Response we are creating
     * @param config   描述如何进行身份验证的登录配置
     *
     * @exception IOException if an input/output error occurs
     */
    protected abstract boolean authenticate(Request request,
                                            Response response,
                                            LoginConfig config)
        throws IOException;


    /**
     * 生成并返回标识SSO主体的cookie的新会话标识符
     */
    protected synchronized String generateSessionId() {

        // 生成包含会话标识符的字节数组
        byte bytes[] = new byte[SESSION_ID_BYTES];
        getRandom().nextBytes(bytes);
        bytes = getDigest().digest(bytes);

        // 将结果呈现为十六进制数字的字符串
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < bytes.length; i++) {
            byte b1 = (byte) ((bytes[i] & 0xf0) >> 4);
            byte b2 = (byte) (bytes[i] & 0x0f);
            if (b1 < 10)
                result.append((char) ('0' + b1));
            else
                result.append((char) ('A' + (b1 - 10)));
            if (b2 < 10)
                result.append((char) ('0' + b2));
            else
                result.append((char) ('A' + (b2 - 10)));
        }
        return (result.toString());

    }


    /**
     * 返回用于计算session的ID的MessageDigest对象. 
     * 如果还没有创建，那么在第一次调用这个方法时初始化一个
     */
    protected synchronized MessageDigest getDigest() {

        if (this.digest == null) {
            try {
                this.digest = MessageDigest.getInstance(algorithm);
            } catch (NoSuchAlgorithmException e) {
                try {
                    this.digest = MessageDigest.getInstance(DEFAULT_ALGORITHM);
                } catch (NoSuchAlgorithmException f) {
                    this.digest = null;
                }
            }
        }

        return (this.digest);
    }


    /**
     * 返回用于生成会话标识符的随机数生成器实例。如果没有当前定义的生成器，构建并生成一个新生成器
     */
    protected synchronized Random getRandom() {
        if (this.random == null) {
            try {
                Class clazz = Class.forName(randomClass);
                this.random = (Random) clazz.newInstance();
                long seed = System.currentTimeMillis();
                char entropy[] = getEntropy().toCharArray();
                for (int i = 0; i < entropy.length; i++) {
                    long update = ((byte) entropy[i]) << ((i % 8) * 8);
                    seed ^= update;
                }
                this.random.setSeed(seed);
            } catch (Exception e) {
                this.random = new java.util.Random();
            }
        }

        return (this.random);
    }


    /**
     * 尝试重新进行身份验证的 <code>Realm</code>, 使用<code>entry</code>参数中包含的凭据.
     *
     * @param ssoId 与调用者关联的SingleSignOn 会话标识符
     * @param request   需要进行身份验证的请求
     */
    protected boolean reauthenticateFromSSO(String ssoId, Request request) {

        if (sso == null || ssoId == null)
            return false;

        boolean reauthenticated = false;

        Container parent = getContainer();
        if (parent != null) {
            Realm realm = parent.getRealm();
            if (realm != null) {
                reauthenticated = sso.reauthenticate(ssoId, realm, request);
            }
        }

        if (reauthenticated) {
            associate(ssoId, request.getSessionInternal(true));

            if (log.isDebugEnabled()) {
                log.debug(" Reauthenticated cached principal '" +
                          request.getUserPrincipal().getName() +
                          "' with auth type '" +  request.getAuthType() + "'");
            }
        }

        return reauthenticated;
    }


    /**
     * 注册一个经过验证的Principal和身份验证类型, 在当前session中, 使用SingleSignOn valve. 设置要返回的cookie
     *
     * @param request 处理的servlet请求
     * @param response 生成的servlet响应
     * @param principal 已注册的身份验证主体
     * @param authType 要注册的身份验证类型
     * @param username 用于验证的用户名
     * @param password 用于验证的密码
     */
    protected void register(Request request, Response response,
                            Principal principal, String authType,
                            String username, String password) {

        if (log.isDebugEnabled())
            log.debug("Authenticated '" + principal.getName() + "' with type '"
                + authType + "'");

        // 在请求中缓存身份验证信息
        request.setAuthType(authType);
        request.setUserPrincipal(principal);

        Session session = request.getSessionInternal(false);
        // 缓存会话中的身份验证信息
        if (cache) {
            if (session != null) {
                session.setAuthType(authType);
                session.setPrincipal(principal);
                if (username != null)
                    session.setNote(Constants.SESS_USERNAME_NOTE, username);
                else
                    session.removeNote(Constants.SESS_USERNAME_NOTE);
                if (password != null)
                    session.setNote(Constants.SESS_PASSWORD_NOTE, password);
                else
                    session.removeNote(Constants.SESS_PASSWORD_NOTE);
            }
        }

        // 创建一个cookie 并返回给客户端
        if (sso == null)
            return;

        // 仅当SSO未为现有条目设置注释时，才创建新的SSO条目 (与随后对摘要和SSL验证上下文请求的关系一样)
        String ssoId = (String) request.getNote(Constants.REQ_SSOID_NOTE);
        if (ssoId == null) {
            // 创建一个cookie 并返回给客户端
            ssoId = generateSessionId();
            Cookie cookie = new Cookie(Constants.SINGLE_SIGN_ON_COOKIE, ssoId);
            cookie.setMaxAge(-1);
            cookie.setPath("/");
            response.addCookie(cookie);

            // 用SSO valve注册这个principal
            sso.register(ssoId, principal, authType, username, password);
            request.setNote(Constants.REQ_SSOID_NOTE, ssoId);

        } else {
            // 使用最新的身份验证数据更新SSO会话
            sso.update(ssoId, principal, authType, username, password);
        }

        // Fix for Bug 10040
        // 总是将一个会话和新的SSO 注册关联.
        // 当关联会话被销毁时，SSO条目仅从SSO注册表映射中删除;
        // 如果为此请求创建一个新的SSO条目并且用户再也没有访问上下文, 如果我们不关联会话，SSO条目将永远不会被清除
        if (session == null)
            session = request.getSessionInternal(true);
        sso.associate(ssoId, session);
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * 添加生命周期事件监听器
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }


    /**
     * 返回生命周期事件监听器. 如果没有，返回零长度数组
     */
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }


    /**
     * 移除生命周期事件监听器
     *
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }


    /**
     * 这个方法应该在<code>configure()</code>方法之后调用, 在其他公用方法调用之前调用.
     *
     * @exception LifecycleException 如果此组件检测到防止该组件被使用的致命错误
     */
    public void start() throws LifecycleException {

        // 验证并更新当前的组件状态
        if (started)
            throw new LifecycleException
                (sm.getString("authenticator.alreadyStarted"));
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        // 在请求处理路径中查找SingleSignOn 实现类
        Container parent = context.getParent();
        while ((sso == null) && (parent != null)) {
            if (!(parent instanceof Pipeline)) {
                parent = parent.getParent();
                continue;
            }
            Valve valves[] = ((Pipeline) parent).getValves();
            for (int i = 0; i < valves.length; i++) {
                if (valves[i] instanceof SingleSignOn) {
                    sso = (SingleSignOn) valves[i];
                    break;
                }
            }
            if (sso == null)
                parent = parent.getParent();
        }
        if (log.isDebugEnabled()) {
            if (sso != null)
                log.debug("Found SingleSignOn Valve at " + sso);
            else
                log.debug("No SingleSignOn Valve is present");
        }
    }


    /**
     * 这个方法应该被最后一个调用
     *
     * @exception LifecycleException 如果此组件检测到需要报告的致命错误
     */
    public void stop() throws LifecycleException {
        // 验证并更新当前的组件状态
        if (!started)
            throw new LifecycleException(sm.getString("authenticator.notStarted"));
        
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        sso = null;
    }
}
