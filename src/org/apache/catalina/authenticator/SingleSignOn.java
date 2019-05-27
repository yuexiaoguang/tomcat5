package org.apache.catalina.authenticator;

import java.io.IOException;
import java.security.Principal;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Realm;
import org.apache.catalina.Session;
import org.apache.catalina.SessionEvent;
import org.apache.catalina.SessionListener;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.valves.ValveBase;


/**
 * <strong>Valve</strong> 支持“单点登录”用户体验,
 * 通过一个Web应用程序身份验证的用户的安全标识同样适用于同一个安全域中的其他应用.
 * 为了成功地使用，必须满足以下要求:
 * <ul>
 * <li>Valve 必须在表示虚拟主机的容器上进行配置(通常是<code>Host</code>实现类).</li>
 * <li><code>Realm</code>包含共享用户和角色信息，必须在同一个Container上配置(或更高一级), 在Web应用程序级别上未被重写.</li>
 * <li>Web应用程序本身必须使用一个标准的认证，在
 *     <code>org.apache.catalina.authenticator</code>包中找到的.</li>
 * </ul>
 */
public class SingleSignOn extends ValveBase implements Lifecycle, SessionListener {

    // ----------------------------------------------------- Instance Variables

    /**
     * SingleSignOnEntry实例的缓存，为已认证的Principal, 使用cookie值作为key.
     */
    protected HashMap cache = new HashMap();


    /**
     * 描述信息
     */
    protected static String info =
        "org.apache.catalina.authenticator.SingleSignOn";


    /**
     * 生命周期事件支持
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);

    /**
     * 这个valve 是否需要下游的 Authenticator 重新验证每个请求, 或者它本身可以绑定一个 UserPrincipal和AuthType对象到请求中.
     */
    private boolean requireReauthentication = false;

    /**
     * 单点登录标识符的缓存, 使用session进行key控制.
     */
    protected HashMap reverse = new HashMap();


    /**
     * The string manager for this package.
     */
    protected final static StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 启动标记.
     */
    protected boolean started = false;


    // ------------------------------------------------------------- Properties


    /**
     * 获取是否每个请求需要重新验证安全<code>Realm</code> (通过pipeline中下游的Authenticator);
     * 或者如果这个Valve 本身可以绑定安全信息到请求中，基于一个有效的SSO条目的存在, 不需要重新验证<code>Realm</code>.
     *
     * @return  <code>true</code>如果它需要Authenticator重新验证每个请求, 在调用
     *          <code>HttpServletRequest.setUserPrincipal()</code>和<code>HttpServletRequest.setAuthType()</code>之前;
     *          <code>false</code> 如果<code>Valve</code>本身让这些调用依赖请求相关的有效的SingleSignOn的存在
     */
    public boolean getRequireReauthentication() {
        return requireReauthentication;
    }


    /**
     * 设置是否每个请求需要重新验证安全<code>Realm</code> (通过pipeline中下游的Authenticator);
     * 或者如果这个Valve 本身可以绑定安全信息到请求中，基于一个有效的SSO条目的存在, 不需要重新验证<code>Realm</code>.
     * <p>
     * 如果这个属性是<code>false</code> (默认), 这个<code>Valve</code> 将绑定一个 UserPrincipal 和 AuthType到请求中,
     * 如果这个请求关联了一个有效的 SSO 条目.  它不会通知传入请求的安全<code>Realm</code>.
     * <p>
     * 这个属性应该设置为<code>true</code>, 如果整个服务器配置需要<code>Realm</code>重新验证每个请求.
     * 这种配置的一个例子是, <code>Realm</code>实现类为Web层和相关的EJB层提供了安全性, 并且需要在每个请求线程上设置安全凭据，以支持EJB访问.
     * <p>
     * 如果这个属性应该设置<code>true</code>, 这个Valve 将设置请求中的标志, 提醒下游的Authenticator 请求被关联到一个 SSO 会话. 
     * 然后Authenticator 将调用它的{@link AuthenticatorBase#reauthenticateFromSSO reauthenticateFromSSO}
     * 方法尝试重新验证请求到<code>Realm</code>, 使用这个Valve缓存的任何凭据.
     * <p>
     * 这个属性默认是<code>false</code>, 为了保持与以前版本的Tomcat的向后兼容性.
     *
     * @param required  <code>true</code>如果它需要Authenticator重新验证每个请求, 在调用
     *          <code>HttpServletRequest.setUserPrincipal()</code>和<code>HttpServletRequest.setAuthType()</code>之前;
     *          <code>false</code> 如果<code>Valve</code>本身让这些调用依赖请求相关的有效的SingleSignOn的存在
     */
    public void setRequireReauthentication(boolean required) {
        this.requireReauthentication = required;
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
     * 获取生命周期事件监听器. 如果没有，返回零长度数组
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
     *  这个方法应该在<code>configure()</code>方法之后,在其他方法调用之前调用
     *
     * @exception LifecycleException 如果此组件检测到阻止该组件被使用的致命错误
     */
    public void start() throws LifecycleException {
        // 验证并更新当前的组件状态
        if (started)
            throw new LifecycleException
                (sm.getString("authenticator.alreadyStarted"));
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;
    }


    /**
     * 这个方法应该被最后一个调用.
     *
     * @exception LifecycleException 如果此组件检测到需要报告的致命错误
     */
    public void stop() throws LifecycleException {
        // 验证并更新当前的组件状态
        if (!started)
            throw new LifecycleException(sm.getString("authenticator.notStarted"));
        
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;
    }


    // ------------------------------------------------ SessionListener Methods


    /**
     * 确认指定事件的发生
     *
     * @param event SessionEvent that has occurred
     */
    public void sessionEvent(SessionEvent event) {

        // We only care about session destroyed events
        if (!Session.SESSION_DESTROYED_EVENT.equals(event.getType())
                && (!Session.SESSION_PASSIVATED_EVENT.equals(event.getType())))
            return;

        // 查找单个会话ID
        Session session = event.getSession();
        if (containerLog.isDebugEnabled())
            containerLog.debug("Process session destroyed on " + session);

        String ssoId = null;
        synchronized (reverse) {
            ssoId = (String) reverse.get(session);
        }
        if (ssoId == null)
            return;

        // 由于超时，会话被销毁了吗?
        // 如果是这样的话, 将只从SSO中删除过期会话. 如果会话已注销, 将注销与SSO相关联的所有会话.
        if (((session.getMaxInactiveInterval() > 0)
            && (System.currentTimeMillis() - session.getLastAccessedTime() >=
                session.getMaxInactiveInterval() * 1000)) 
            || (Session.SESSION_PASSIVATED_EVENT.equals(event.getType()))) {
            removeSession(ssoId, session);
        } else {
            // The session was logged out.
            // 注销这个单一的会话ID，无效的session
            deregister(ssoId);
        }
    }


    // ---------------------------------------------------------- Valve Methods


    /**
     * 返回描述信息
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 执行单点登录支持处理
     *
     * @param request 正在处理的servlet请求
     * @param response 正在创建的servlet响应
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        request.removeNote(Constants.REQ_SSOID_NOTE);

        // 有效用户是否已被验证?
        if (containerLog.isDebugEnabled())
            containerLog.debug("Process request for '" + request.getRequestURI() + "'");
        if (request.getUserPrincipal() != null) {
            if (containerLog.isDebugEnabled())
                containerLog.debug(" Principal '" + request.getUserPrincipal().getName() +
                    "' has already been authenticated");
            getNext().invoke(request, response);
            return;
        }

        // 检查单点登录cookie
        if (containerLog.isDebugEnabled())
            containerLog.debug(" Checking for SSO cookie");
        Cookie cookie = null;
        Cookie cookies[] = request.getCookies();
        if (cookies == null)
            cookies = new Cookie[0];
        for (int i = 0; i < cookies.length; i++) {
            if (Constants.SINGLE_SIGN_ON_COOKIE.equals(cookies[i].getName())) {
                cookie = cookies[i];
                break;
            }
        }
        if (cookie == null) {
            if (containerLog.isDebugEnabled())
                containerLog.debug(" SSO cookie is not present");
            getNext().invoke(request, response);
            return;
        }

        // 查找与此cookie值关联的缓存主体
        if (containerLog.isDebugEnabled())
            containerLog.debug(" Checking for cached principal for " + cookie.getValue());
        SingleSignOnEntry entry = lookup(cookie.getValue());
        if (entry != null) {
            if (containerLog.isDebugEnabled())
                containerLog.debug(" Found cached principal '" +
                    entry.getPrincipal().getName() + "' with auth type '" +
                    entry.getAuthType() + "'");
            request.setNote(Constants.REQ_SSOID_NOTE, cookie.getValue());
            // 只有设置安全元素, 如果重新认证是不需要的
            if (!getRequireReauthentication()) {
                request.setAuthType(entry.getAuthType());
                request.setUserPrincipal(entry.getPrincipal());
            }
        } else {
            if (containerLog.isDebugEnabled())
                containerLog.debug(" No cached principal found, erasing SSO cookie");
            cookie.setMaxAge(0);
            response.addCookie(cookie);
        }

        // Invoke the next Valve in our pipeline
        getNext().invoke(request, response);
    }


    // --------------------------------------------------------- Public Methods


    public String toString() {
        StringBuffer sb = new StringBuffer("SingleSignOn[");
        if (container == null )
            sb.append("Container is null");
        else
            sb.append(container.getName());
        sb.append("]");
        return (sb.toString());
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 将指定的单点登录标识符与指定的会话关联
     *
     * @param ssoId 单点登录标识符
     * @param session 关联的会话
     */
    protected void associate(String ssoId, Session session) {
        if (containerLog.isDebugEnabled())
            containerLog.debug("Associate sso id " + ssoId + " with session " + session);

        SingleSignOnEntry sso = lookup(ssoId);
        if (sso != null)
            sso.addSession(this, session);
        synchronized (reverse) {
            reverse.put(session, ssoId);
        }
    }

    /**
     * 注销指定的会话.
     * 如果这是最后一个会话, 然后去掉单点登录标识符
     *
     * @param ssoId 单点登录标识符
     * @param session 注销的会话
     */
    protected void deregister(String ssoId, Session session) {

        synchronized (reverse) {
            reverse.remove(session);
        }

        SingleSignOnEntry sso = lookup(ssoId);
        if ( sso == null )
            return;

        sso.removeSession( session );

        // 如果是最后一个标识, 删除 ssoId
        Session sessions[] = sso.findSessions();
        if ( sessions == null || sessions.length == 0 ) {
            synchronized (cache) {
                sso = (SingleSignOnEntry) cache.remove(ssoId);
            }
        }

    }


    /**
     * 注销指定的单点登录标识符,同时使关联的会话无效
     *
     * @param ssoId 注销的单点登录标识符
     */
    protected void deregister(String ssoId) {

        if (containerLog.isDebugEnabled())
            containerLog.debug("Deregistering sso id '" + ssoId + "'");

        // 查找删除相应的SingleSignOnEntry
        SingleSignOnEntry sso = null;
        synchronized (cache) {
            sso = (SingleSignOnEntry) cache.remove(ssoId);
        }

        if (sso == null)
            return;

        // 终止任何关联的会话
        Session sessions[] = sso.findSessions();
        for (int i = 0; i < sessions.length; i++) {
            if (containerLog.isTraceEnabled())
                containerLog.trace(" Invalidating session " + sessions[i]);
            // 首先从反向缓存中删除，以避免递归
            synchronized (reverse) {
                reverse.remove(sessions[i]);
            }
            // Invalidate this session
            sessions[i].expire();
        }

     // NOTE: 客户端可能仍然拥有旧的单点登录cookie, 但它将在下一个请求中被删除，因为它不再在缓存中
    }


    /**
     * 重新验证给定的<code>Realm</code>, 使用单点登录会话标识符关联的凭据, 通过参数<code>ssoId</code>.
     * <p>
     * 如果重新验证成功, SSO会话关联的<code>Principal</code>和验证类型将被绑定到给定的<code>Request</code>对象, 通过调用
     * {@link Request#setAuthType Request.setAuthType()} 和 {@link Request#setUserPrincipal Request.setUserPrincipal()}
     * </p>
     *
     * @param ssoId     SingleSignOn会话标识符
     * @param realm     要验证的Realm实现类
     * @param request   需要验证的请求
     * 
     * @return  <code>true</code>如果重新验证成功,否则返回<code>false</code>
     */
    protected boolean reauthenticate(String ssoId, Realm realm,
                                     Request request) {

        if (ssoId == null || realm == null)
            return false;

        boolean reauthenticated = false;

        SingleSignOnEntry entry = lookup(ssoId);
        if (entry != null && entry.getCanReauthenticate()) {
            
            String username = entry.getUsername();
            if (username != null) {
                Principal reauthPrincipal =
                        realm.authenticate(username, entry.getPassword());                
                if (reauthPrincipal != null) {                    
                    reauthenticated = true;                    
                    // Bind the authorization credentials to the request
                    request.setAuthType(entry.getAuthType());
                    request.setUserPrincipal(reauthPrincipal);
                }
            }
        }

        return reauthenticated;
    }


    /**
     * 将指定的Principal注册，与单个登录标识符的指定值相关联
     *
     * @param ssoId 要注册的单点登录标识符
     * @param principal 已识别的关联用户主体
     * @param authType 用于验证此用户主体的身份验证类型
     * @param username 用于对该用户进行身份验证的用户名
     * @param password 用于对该用户进行身份验证的密码
     */
    protected void register(String ssoId, Principal principal, String authType,
                  String username, String password) {

        if (containerLog.isDebugEnabled())
            containerLog.debug("Registering sso id '" + ssoId + "' for user '" +
                principal.getName() + "' with auth type '" + authType + "'");

        synchronized (cache) {
            cache.put(ssoId, new SingleSignOnEntry(principal, authType,
                                                   username, password));
        }
    }


    /**
     * 更新根据<code>ssoId</code>找到的所有<code>SingleSignOnEntry</code>以及指定的验证数据.
     * <p>
     * 此方法的目的是允许在没有用户名/密码组合的情况下建立SSO条目(即使用 DIGEST 或 CLIENT-CERT认证)更新用户名和密码,
     * 如果一个可用的通过BASIC 或 FORM 认证. SSO 将可用于认证.
     * <p>
     * <b>NOTE:</b> 只更新 SSO 条目，如果调用<code>SingleSignOnEntry.getCanReauthenticate()</code>返回
     * <code>false</code>; 否则, 假设SSO 已经有足够的信息来认证, 而且不需要更新.
     *
     * @param ssoId     要更新的单点登录的标识符
     * @param principal 最后调用的<code>Realm.authenticate</code>返回的<code>Principal</code>.
     * @param authType  用于认证的类型(BASIC, CLIENT-CERT, DIGEST or FORM)
     * @param username  用于身份验证的用户名
     * @param password  用于身份验证的密码
     */
    protected void update(String ssoId, Principal principal, String authType,
                          String username, String password) {

        SingleSignOnEntry sso = lookup(ssoId);
        if (sso != null && !sso.getCanReauthenticate()) {
            if (containerLog.isDebugEnabled())
                containerLog.debug("Update sso id " + ssoId + " to auth type " + authType);

            synchronized(sso) {
                sso.updateCredentials(principal, authType, username, password);
            }
        }
    }


    /**
     * 查找并返回指定的缓存的SingleSignOn 条目; 否则返回<code>null</code>.
     *
     * @param ssoId 要查找的单点登录标识符
     */
    protected SingleSignOnEntry lookup(String ssoId) {
        synchronized (cache) {
            return ((SingleSignOnEntry) cache.get(ssoId));
        }
    }

    
    /**
     * 从一个SingleSignOn删除一个会话.
     * 当会话超时并不再活动时调用.
     *
     * @param ssoId 会话的单点登录标识符
     * @param session 要删除的会话
     */
    protected void removeSession(String ssoId, Session session) {

        if (containerLog.isDebugEnabled())
            containerLog.debug("Removing session " + session.toString() + " from sso id " + 
                ssoId );

        // 从SingleSignOn获取引用
        SingleSignOnEntry entry = lookup(ssoId);
        if (entry == null)
            return;

        // 从SingleSignOnEntry删除非活动会话
        entry.removeSession(session);

        // 从'reverse' Map删除非活动会话.
        synchronized(reverse) {
            reverse.remove(session);
        }

        // 如果SingleSignOnEntry中没有会话, 注销它.
        if (entry.findSessions().length == 0) {
            deregister(ssoId);
        }
    }
}
