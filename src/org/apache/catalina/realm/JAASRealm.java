package org.apache.catalina.realm;


import java.security.Principal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.security.auth.Subject;
import javax.security.auth.login.AccountExpiredException;
import javax.security.auth.login.CredentialExpiredException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.catalina.Container;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * <p><b>Realm</b>实现类，通过<em>Java Authentication and Authorization Service</em> (JAAS)验证用户.
 * JAAS 需要 JDK 1.4 (其中包括它作为标准平台的一部分)或 JDK 1.3 (用插件 <code>jaas.jar</code>文件).</p>
 *
 * <p><code>appName</code>属性配置的值通过对<code>javax.security.auth.login.LoginContext</code>构造方法, 
 * 指定<em>应用名称</em>用于选择<code>LoginModules</code>相关的集合.</p>
 *
 * <p>JAAS规范描述了成功登录的结果, 作为一个<code>javax.security.auth.Subject</code>实例, 可能包括零个或多个<code>java.security.Principal</code>
 * 对象，在<code>Subject.getPrincipals()</code>方法的返回值中.
 * 但是，它并没有指导如何区分描述单个用户的Principal(web应用程序中request.getUserPrincipal()返回的值)与描述该用户授权角色的Principal.
 * 保持尽可能独立于底层的JAAS执行的<code>LoginMethod</code>实现类, 以下策略由该Realm实现:</p>
 * <ul>
 * <li>JAAS <code>LoginModule</code>假设返回具有至少一个<code>Principal</code>实例的主题，该实例代表用户自己, 
 * 		以及代表此用户授权的安全角色的零个或多个独立的<code>Principals</code>.</li>
 * <li>如果<code>Principal</code>表示用户, 名称是servlet API方法
 * 		<code>HttpServletRequest.getRemoteUser()</code>返回的值.</li>
 * <li>如果<code>Principals</code>表示安全角色, 名称是授权的安全角色的名称.</li>
 * <li>这个Realm 将配置<code>java.security.Principal</code>实现类完全限定java类名的两个列表
 *      - 标识类表示一个用户, 标识类表示一个安全角色.</li>
 * <li>这个Realm 遍历<code>Subject.getPrincipals()</code>返回的<code>Principals</code>, 它将识别第一个
 *     <code>Principal</code> ，匹配"user classes"列表.</li>
 * <li>这个Realm 遍历<code>Subject.getPrincipals()</code>返回的<code>Principals</code>, 它将添加<code>Principal</code>，
 * 		匹配"role classes"列表 作为标识此用户的安全角色.</li>
 * <li>这是一个配置错误，JAAS登录方法返回一个已验证的<code>Subject</code> ,不包含匹配"user classes"列表的<code>Principal</code>.</li>
 *     
 * <li>默认情况下, 封闭容器的名称是用于获取JAAS LoginContext应用程序名称(在默认安装中的"Catalina"). Tomcat 必须能够找到在JAAS配置文件中有名称的应用.
 * 		这是一个假设的JAAS配置文件条目, 对于一个面向数据库的使用Tomcat JNDI的数据库资源管理的登录模块:
 *     <blockquote><pre>Catalina {
org.foobar.auth.DatabaseLoginModule REQUIRED
    JNDI_RESOURCE=jdbc/AuthDB
  USER_TABLE=users
  USER_ID_COLUMN=id
  USER_NAME_COLUMN=name
  USER_CREDENTIAL_COLUMN=password
  ROLE_TABLE=roles
  ROLE_NAME_COLUMN=name
  PRINCIPAL_FACTORY=org.foobar.auth.impl.SimplePrincipalFactory;
};</pre></blockquote></li>
 * <li>设置JAAS配置文件的位置, 设置<code>CATALINA_OPTS</code>与下面类似的环境变量:
<blockquote><code>CATALINA_OPTS="-Djava.security.auth.login.config=$CATALINA_HOME/conf/jaas.config"</code></blockquote>
 * </li>
 * <li>作为登录过程的一部分, JAASRealm 注册它自己的<code>CallbackHandler</code>, 调用(意料之中) <code>JAASCallbackHandler</code>.
 * 		该处理程序向用户提供的<code>LoginModule</code>提供HTTP请求的用户名和凭据</li>
 * <li>与其他<code>Realm</code>实现类一起, 支持加密密码, 如果<code>server.xml</code>中的<code>&lt;Realm&gt;</code>元素
 * 		包含一个<code>digest</code>属性; <code>JAASCallbackHandler</code>将之前加密的密码传递给<code>LoginModule</code></li>  
* </ul>
 */
public class JAASRealm extends RealmBase {
    private static Log log = LogFactory.getLog(JAASRealm.class);

    // ----------------------------------------------------- Instance Variables


    /**
     * 通过JAAS <code>LoginContext</code>的应用程序名称,
     * 使用它来选择相关<code>LoginModules</code>的集合.
     */
    protected String appName = null;


    /**
     * <code>Realm</code>实现类的描述信息.
     */
    protected static final String info =
        "org.apache.catalina.realm.JAASRealm/1.0";


    /**
     * 描述信息.
     */
    protected static final String name = "JAASRealm";


    /**
     * 角色类名称列表, 拆分便于处理.
     */
    protected List roleClasses = new ArrayList();


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 用户类名称的列表, 拆分便于处理.
     */
    protected List userClasses = new ArrayList();


    /**
     * 使用上下文ClassLoader还是默认的 ClassLoader.
     * True 表示上下文ClassLoader, 默认是True.
     */
     protected boolean useContextClassLoader = true;


    // ------------------------------------------------------------- Properties

    
    /**
     * 设置<code>appName</code>成员变量
     * @deprecated JAAS 应该使用<code>Engine</code>（域名）的名称和webpp/host覆盖
     */
    public void setAppName(String name) {
        appName = name;
    }
    
    /**
     * 获取<code>appName</code>成员变量
     */
    public String getAppName() {
        return appName;
    }

    /**
     * 设置是否使用上下文或默认的ClassLoader.
     * True表示使用上下文 ClassLoader.
     *
     * @param useContext True表示使用上下文 ClassLoader.
     */
    public void setUseContextClassLoader(boolean useContext) {
      useContextClassLoader = useContext;
      log.info("Setting useContextClassLoader = " + useContext);
    }

    /**
     * 使用上下文或默认的ClassLoader.
     * True表示使用上下文 ClassLoader.
     */
    public boolean isUseContextClassLoader() {
    	return useContextClassLoader;
    } 

    public void setContainer(Container container) {
        super.setContainer(container);

        if( appName==null  ) {
            String name=container.getName();
            name = makeLegalForJAAS(name);

            appName=name;

            log.info("Set JAAS app name " + appName);
        }
    }

    /**
     * 代表安全角色的<code>java.security.Principal</code>类逗号分隔的列表.
     */
    protected String roleClassNames = null;

    public String getRoleClassNames() {
        return (this.roleClassNames);
    }

     /**
      * 设置表示角色的逗号分隔类的列表. 列表中的类必须实现<code>java.security.Principal</code>.
      * 当这个方法被调用(例如, 通过<code>Digester</code>实例解析配置文件), 它将解析类名并存储结果字符串到</code>roleClasses</code>.
      */
     public void setRoleClassNames(String roleClassNames) {
         this.roleClassNames = roleClassNames;
        roleClasses.clear();
        String temp = this.roleClassNames;
        if (temp == null) {
            return;
        }
        while (true) {
            int comma = temp.indexOf(',');
            if (comma < 0) {
                break;
            }
            roleClasses.add(temp.substring(0, comma).trim());
            temp = temp.substring(comma + 1);
        }
        temp = temp.trim();
        if (temp.length() > 0) {
            roleClasses.add(temp);
        }
    }


    /**
     * 逗号分隔<code>javax.security.Principal</code>类列表,表示个人用户.
     */
    protected String userClassNames = null;

    public String getUserClassNames() {
        return (this.userClassNames);
    }

     /**
     * 设置表示用户的逗号分隔类的列表. 列表中的类必须实现<code>java.security.Principal</code>.
      * 当这个方法被调用(例如, 通过<code>Digester</code>实例解析配置文件), 它将解析类名并存储结果字符串到</code>userClasses</code>.
     */
    public void setUserClassNames(String userClassNames) {
        this.userClassNames = userClassNames;
        userClasses.clear();
        String temp = this.userClassNames;
        if (temp == null) {
            return;
        }
        while (true) {
            int comma = temp.indexOf(',');
            if (comma < 0) {
                break;
            }
            userClasses.add(temp.substring(0, comma).trim());
            temp = temp.substring(comma + 1);
        }
        temp = temp.trim();
        if (temp.length() > 0) {
            userClasses.add(temp);
        }
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 返回指定名称和凭据的Principal; 或者<code>null</code>.
     *
     * 如果JDBC连接有任何错误, 执行查询或返回null的任何操作 (不验证). 
     * 此事件也被记录, 连接将被关闭，以便随后的请求将自动重新打开它.
     *
     * @param username 要查找的Principal的用户名
     * @param credentials 用于验证此用户名的Password或其它凭据
     */
    public Principal authenticate(String username, String credentials) {

        // 建立一个LoginContext 用于认证
        try {
        LoginContext loginContext = null;
        if( appName==null ) appName="Tomcat";

        if( log.isDebugEnabled())
            log.debug(sm.getString("jaasRealm.beginLogin", username, appName));

        // What if the LoginModule is in the container class loader ?
        ClassLoader ocl = null;

        if (isUseContextClassLoader()) {
          ocl=Thread.currentThread().getContextClassLoader();
          Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        }

        try {
            loginContext = new LoginContext
                (appName, new JAASCallbackHandler(this, username,
                                                  credentials));
        } catch (Throwable e) {
            log.error(sm.getString("jaasRealm.unexpectedError"), e);
            return (null);
        } finally {
            if( isUseContextClassLoader()) {
              Thread.currentThread().setContextClassLoader(ocl);
            }
        }

        if( log.isDebugEnabled())
            log.debug("Login context created " + username);

        // 通过这个LoginContext进行登录
        Subject subject = null;
        try {
            loginContext.login();
            subject = loginContext.getSubject();
            if (subject == null) {
                if( log.isDebugEnabled())
                    log.debug(sm.getString("jaasRealm.failedLogin", username));
                return (null);
            }
        } catch (AccountExpiredException e) {
            if (log.isDebugEnabled())
                log.debug(sm.getString("jaasRealm.accountExpired", username));
            return (null);
        } catch (CredentialExpiredException e) {
            if (log.isDebugEnabled())
                log.debug(sm.getString("jaasRealm.credentialExpired", username));
            return (null);
        } catch (FailedLoginException e) {
            if (log.isDebugEnabled())
                log.debug(sm.getString("jaasRealm.failedLogin", username));
            return (null);
        } catch (LoginException e) {
            log.warn(sm.getString("jaasRealm.loginException", username), e);
            return (null);
        } catch (Throwable e) {
            log.error(sm.getString("jaasRealm.unexpectedError"), e);
            return (null);
        }

        if( log.isDebugEnabled())
            log.debug(sm.getString("jaasRealm.loginContextCreated", username));

        // 返回适当的Principal 为这个已认证的 Subject
        Principal principal = createPrincipal(username, subject);
        if (principal == null) {
            log.debug(sm.getString("jaasRealm.authenticateFailure", username));
            return (null);
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("jaasRealm.authenticateSuccess", username));
        }

        return (principal);
        } catch( Throwable t) {
            log.error( "error ", t);
            return null;
        }
    }

    // ------------------------------------------------------ Protected Methods

    /**
     * 返回这个Realm实现类的短名称.
     */
    protected String getName() {
        return (name);
    }


    /**
     * 返回指定用户名的密码.
     */
    protected String getPassword(String username) {
        return (null);
    }


    /**
     * 返回指定名称的Principal.
     */
    protected Principal getPrincipal(String username) {
        return (null);
    }


    /**
     * 识别并返回一个 <code>java.security.Principal</code>实例, 表示指定的<code>Subject</code>的经过身份验证的用户.
     * 通过扫描JAASLoginModule返回的Principal列表创建Principal.
     * 第一个<code>Principal</code>对象匹配作为"user class"提供的类名之一是用户 Principal.
     * 如果用户Principal不能创建, 返回<code>null</code>.
     * 
     * @param subject <code>Subject</code>表示已登录的用户
     */
    protected Principal createPrincipal(String username, Subject subject) {
        // 准备扫描这个Subject的 Principal
        String password = null; // 将不会被转发

        List roles = new ArrayList();
        Principal userPrincipal = null;

        // Scan the Principals for this Subject
        Iterator principals = subject.getPrincipals().iterator();
        while (principals.hasNext()) {
            Principal principal = (Principal) principals.next();

            String principalClass = principal.getClass().getName();

            if( log.isDebugEnabled() ) {
                log.debug(sm.getString("jaasRealm.checkPrincipal", principal, principalClass));
            }

            if (userPrincipal == null && userClasses.contains(principalClass)) {
                userPrincipal = principal;
                if( log.isDebugEnabled() ) {
                    log.debug(sm.getString("jaasRealm.userPrincipalSuccess", principal.getName()));
                }
            }
            
            if (roleClasses.contains(principalClass)) {
                roles.add(principal.getName());
                if( log.isDebugEnabled() ) {
                    log.debug(sm.getString("jaasRealm.rolePrincipalAdd", principal.getName()));
                }
            }
        }

        // 如果需要打印失败信息
        if (userPrincipal == null) {
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("jaasRealm.userPrincipalFailure"));
                log.debug(sm.getString("jaasRealm.rolePrincipalFailure"));
            }
        } else {
            if (roles.size() == 0) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("jaasRealm.rolePrincipalFailure"));
                }
            }
        }

        // 返回已验证用户的结果Principal
        return new GenericPrincipal(this, username, null, roles, userPrincipal);
    }

     /**
      * 确保给定的名称是合法的JAAS配置.
      * 添加Bugzilla 30869, protected方法能够轻松自定义重写，以防默认实现类不够用.
      *
      * @param src 要验证的名称
      * @return 一个字符串，是一个有效的JAAS realm 名称
      */
     protected String makeLegalForJAAS(final String src) {
         String result = src;
         
         // 每个JAAS的默认名称是"other"
         if(result == null) {
             result = "other";
         }

         // 如果出现斜杠
         if(result.startsWith("/")) {
             result = result.substring(1);
         }
         return result;
     }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * @exception LifecycleException 如果此组件检测到阻止其启动的致命错误
     */
    public void start() throws LifecycleException {
        // 执行正常的父类初始化
        super.start();
    }


    /**
     * @exception LifecycleException 如果此组件检测到需要报告的致命错误
     */
    public void stop() throws LifecycleException {
        // 执行正常的超类的终结
        super.stop();
    }
}
