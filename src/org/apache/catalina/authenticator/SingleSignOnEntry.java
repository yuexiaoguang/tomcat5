package org.apache.catalina.authenticator;

import java.security.Principal;

import org.apache.catalina.Session;
import org.apache.catalina.authenticator.Constants;

/**
 * 已验证用户的缓存中的条目.
 * 这是必要的，使其可获得<code>AuthenticatorBase</code>子类, 需要使用它进行重新验证, 当使用 SingleSignOn 的时候.
 */
public class SingleSignOnEntry {
    // ------------------------------------------------------  Instance Fields

    protected String authType = null;

    protected String password = null;

    protected Principal principal = null;

    protected Session sessions[] = new Session[0];

    protected String username = null;

    protected boolean canReauthenticate = false;

    // ---------------------------------------------------------  Constructors

    /**
     * @param principal 最后调用的<code>Realm.authenticate</code>返回的<code>Principal</code>.
     * @param authType  用于认证的类型(BASIC, CLIENT-CERT, DIGEST or FORM)
     * @param username  用于身份验证的用户名
     * @param password  用于身份验证的密码
     */
    public SingleSignOnEntry(Principal principal, String authType,
                             String username, String password) {
        super();
        updateCredentials(principal, authType, username, password);
    }

    public SingleSignOnEntry() {
    }

    // ------------------------------------------------------- Package Methods

    /**
     * 添加一个<code>Session</code>.
     *
     * @param sso       管理SSO会话的<code>SingleSignOn</code> valve
     * @param session   关联的<code>Session</code>
     */
    public synchronized void addSession(SingleSignOn sso, Session session) {
        for (int i = 0; i < sessions.length; i++) {
            if (session == sessions[i])
                return;
        }
        Session results[] = new Session[sessions.length + 1];
        System.arraycopy(sessions, 0, results, 0, sessions.length);
        results[sessions.length] = session;
        sessions = results;
        session.addSessionListener(sso);
    }

    /**
     * 删除<code>Session</code>
     *
     * @param session  the <code>Session</code> to remove.
     */
    public synchronized void removeSession(Session session) {
        Session[] nsessions = new Session[sessions.length - 1];
        for (int i = 0, j = 0; i < sessions.length; i++) {
            if (session == sessions[i])
                continue;
            nsessions[j++] = sessions[i];
        }
        sessions = nsessions;
    }

    /**
     * 返回所有关联的<code>Session</code>.
     */
    public synchronized Session[] findSessions() {
        return (this.sessions);
    }

    /**
     * 获取最初用于验证与SSO相关联的用户的验证类型的名称
     *
     * @return "BASIC", "CLIENT-CERT", "DIGEST", "FORM" or "NONE"
     */
    public String getAuthType() {
        return (this.authType);
    }

    /**
     * 与原认证相关的认证类型是否支持重新认证.
     *
     * @return  <code>true</code>如果<code>getAuthType</code> 返回"BASIC" or "FORM"; 否则<code>false</code>
     */
    public boolean getCanReauthenticate() {
        return (this.canReauthenticate);
    }

    /**
     * 获取密码.
     *
     * @return  如果原始身份验证类型不涉及密码则为<code>null</code>.
     */
    public String getPassword() {
        return (this.password);
    }

    /**
     * 获取已经通过SSO认证的<code>Principal</code>
     */
    public Principal getPrincipal() {
        return (this.principal);
    }

    /**
     * 获取用户提供的用户名，作为认证过程的一部分.
     */
    public String getUsername() {
        return (this.username);
    }


    /**
     * 更新SingleSignOnEntry 以反映与调用方关联的最新安全信息.
     *
     * @param principal 最后调用的<code>Realm.authenticate</code>返回的<code>Principal</code>
     * @param authType  用于认证的类型(BASIC, CLIENT-CERT, DIGEST or FORM)
     * @param username  用于身份验证的用户名
     * @param password  用于身份验证的密码
     */
    public void updateCredentials(Principal principal, String authType,
                                  String username, String password) {
        this.principal = principal;
        this.authType = authType;
        this.username = username;
        this.password = password;
        this.canReauthenticate =
            (Constants.BASIC_METHOD.equals(authType)
                || Constants.FORM_METHOD.equals(authType));
    }
}
