package org.apache.catalina.realm;


import java.io.File;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

import org.apache.catalina.Context;
import org.apache.catalina.Realm;
import org.apache.catalina.connector.Request;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.util.digester.Digester;


/**
 * <p>JAAS <strong>LoginModule</strong>接口的实现类, 主要用于测试<code>JAASRealm</code>.
 * 它使用与<code>org.apache.catalina.realm.MemoryRealm</code>支持的用户名/密码/角色信息的XML格式数据文件
 * (除了不支持已过时的密码).</p>
 *
 * <p>该类识别下列字符串值选项, 这是在配置文件中指定的 (并传递给构造函数，使用<code>options</code>参数:</p>
 * <ul>
 * <li><strong>debug</strong> - 设置为"true"，使用 System.out输出日志信息. 默认是<code>false</code>.</li>
 * <li><strong>pathname</strong> - 相对("catalina.base"系统属性指定的路径名)或绝对路径的XML文件，包含用户信息,
 *     使用{@link MemoryRealm}支持的格式.  默认值匹配MemoryRealm.</li>
 * </ul>
 *
 * <p><strong>实现注意</strong> - 这个类实现<code>Realm</code> 仅满足<code>GenericPrincipal</code>构造方法的调用要求. 
 * 它实际上并没有执行<code>Realm</code>实现类所需的功能.</p>
 */
public class JAASMemoryLoginModule extends MemoryRealm implements LoginModule, Realm {
    //需要继承 MemoryRealm to avoid class cast

    private static Log log = LogFactory.getLog(JAASMemoryLoginModule.class);

    // ----------------------------------------------------- Instance Variables


    /**
     * 负责响应请求的回调处理程序.
     */
    protected CallbackHandler callbackHandler = null;


    /**
     * <code>commit()</code>是否成功返回?
     */
    protected boolean committed = false;


    /**
     * <code>LoginModule</code>的配置信息.
     */
    protected Map options = null;


    /**
     * XML配置文件的绝对或相对路径.
     */
    protected String pathname = "conf/tomcat-users.xml";


    /**
     * 通过确认的<code>Principal</code>,或者<code>null</code>，如果验证失败.
     */
    protected Principal principal = null;


    /**
     * 从配置文件中加载的<code>Principal</code>集合.
     */
    protected HashMap principals = new HashMap();

    /**
     * The string manager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * 和其他配置的<code>LoginModule</code>实例共享的状态信息.
     */
    protected Map sharedState = null;


    /**
     * 正在进行身份验证的主题.
     */
    protected Subject subject = null;


    // --------------------------------------------------------- Public Methods

    public JAASMemoryLoginModule() {
        log.debug("MEMORY LOGIN MODULE");
    }

    /**
     * <code>Subject</code>身份验证的第2阶段,当第一阶段失败.
     * 如果<code>LoginContext</code>在整个认证链中的某处失败，将调用这个方法.
     *
     * @return <code>true</code>如果这个方法成功, 或者<code>false</code>如果这个<code>LoginModule</code>应该忽略
     *
     * @exception LoginException 如果中止失败
     */
    public boolean abort() throws LoginException {
        // 如果认证不成功，只返回false
        if (principal == null)
            return (false);

        // 如果整体身份验证失败，清除
        if (committed)
            logout();
        else {
            committed = false;
            principal = null;
        }
        log.debug("Abort");
        return (true);
    }


    /**
     * <code>Subject</code>验证的第二阶段，当第一阶段验证成功.
     * 如果<code>LoginContext</code>在整个认证链中成功，将调用这个方法.
     *
     * @return <code>true</code>如果这个方法成功, 或者<code>false</code>如果这个<code>LoginModule</code>应该忽略
     *
     * @exception LoginException 如果提交失败
     */
    public boolean commit() throws LoginException {
        log.debug("commit " + principal);

        // 如果认证不成功，只返回false
        if (principal == null)
            return (false);

        // Add our Principal to the Subject if needed
        if (!subject.getPrincipals().contains(principal))
            subject.getPrincipals().add(principal);

        committed = true;
        return (true);
    }

    
    /**
     * 返回配置为保护此请求的请求URI的 SecurityConstraint, 或<code>null</code>如果没有这样的约束.
     *
     * @param request 正在处理的请求
     * @param context 将请求映射到的上下文
     */
    public SecurityConstraint [] findSecurityConstraints(Request request,
                                                     Context context) {
        ArrayList results = null;
        // 是否有任何定义的安全约束?
        SecurityConstraint constraints[] = context.findConstraints();
        if ((constraints == null) || (constraints.length == 0)) {
            if (context.getLogger().isDebugEnabled())
                context.getLogger().debug("  No applicable constraints defined");
            return (null);
        }

        // 检查每个定义的安全约束
        String uri = request.getDecodedRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath.length() > 0)
            uri = uri.substring(contextPath.length());
        uri = RequestUtil.URLDecode(uri); // Before checking constraints
        String method = request.getMethod();
        for (int i = 0; i < constraints.length; i++) {
            if (context.getLogger().isDebugEnabled())
                context.getLogger().debug("  Checking constraint '" + constraints[i] +
                    "' against " + method + " " + uri + " --> " +
                    constraints[i].included(uri, method));
            if (constraints[i].included(uri, method)) {
                if(results == null) {
                    results = new ArrayList();
                }
                results.add(constraints[i]);
            }
        }

        // 没有发现适用的安全约束
        if (context.getLogger().isDebugEnabled())
            context.getLogger().debug("  No applicable constraint located");
        if(results == null)
            return null;
        SecurityConstraint [] array = new SecurityConstraint[results.size()];
        System.arraycopy(results.toArray(), 0, array, 0, array.length);
        return array;
    }
    
    
    /**
     * 使用指定的配置信息初始化这个<code>LoginModule</code>.
     *
     * @param subject 要验证的<code>Subject</code>
     * @param callbackHandler <code>CallbackHandler</code>，在必要时与最终用户通信
     * @param sharedState 和其他<code>LoginModule</code>实例共享的配置信息
     * @param options 指定的<code>LoginModule</code>实例的配置信息
     */
    public void initialize(Subject subject, CallbackHandler callbackHandler,
                           Map sharedState, Map options) {
        log.debug("Init");

        // 保存配置值
        this.subject = subject;
        this.callbackHandler = callbackHandler;
        this.sharedState = sharedState;
        this.options = options;

        // 执行特定实例的初始化
        if (options.get("pathname") != null)
            this.pathname = (String) options.get("pathname");

        // 加载定义的 Principals
        load();
    }


    /**
     * 验证<code>Subject</code>的第一阶段.
     *
     * @return <code>true</code>如果这个方法成功, 或者<code>false</code>如果这个<code>LoginModule</code>应该忽略
     *
     * @exception LoginException 如果身份验证失败
     */
    public boolean login() throws LoginException {

        // 设置 CallbackHandler请求
        if (callbackHandler == null)
            throw new LoginException("No CallbackHandler specified");
        Callback callbacks[] = new Callback[2];
        callbacks[0] = new NameCallback("Username: ");
        callbacks[1] = new PasswordCallback("Password: ", false);

        // 与用户交互以检索用户名和密码
        String username = null;
        String password = null;
        try {
            callbackHandler.handle(callbacks);
            username = ((NameCallback) callbacks[0]).getName();
            password =
                new String(((PasswordCallback) callbacks[1]).getPassword());
        } catch (IOException e) {
            throw new LoginException(e.toString());
        } catch (UnsupportedCallbackException e) {
            throw new LoginException(e.toString());
        }

        // 验证收到的用户名和密码
        principal = super.authenticate(username, password);

        log.debug("login " + username + " " + principal);

        // 根据成功或失败报告结果
        if (principal != null) {
            return (true);
        } else {
            throw new
                FailedLoginException("Username or password is incorrect");
        }
    }


    /**
     * 用户退出登录.
     *
     * @return 所有情况都返回<code>true</code>，因为<code>LoginModule</code>不应该被忽略
     *
     * @exception LoginException 如果注销失败
     */
    public boolean logout() throws LoginException {
        subject.getPrincipals().remove(principal);
        committed = false;
        principal = null;
        return (true);
    }

    // ------------------------------------------------------ Protected Methods

    /**
     * 加载配置文件的内容.
     */
    protected void load() {

        // 验证配置文件是否存在
        File file = new File(pathname);
        if (!file.isAbsolute())
            file = new File(System.getProperty("catalina.base"), pathname);
        if (!file.exists() || !file.canRead()) {
            log.warn("Cannot load configuration file " + file.getAbsolutePath());
            return;
        }

        // 加载配置文件的内容
        Digester digester = new Digester();
        digester.setValidating(false);
        digester.addRuleSet(new MemoryRuleSet());
        try {
            digester.push(this);
            digester.parse(file);
        } catch (Exception e) {
            log.warn("Error processing configuration file " +
                file.getAbsolutePath(), e);
            return;
        } finally {
            digester.reset();
        }
    }
}
