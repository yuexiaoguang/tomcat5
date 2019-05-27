package org.apache.catalina.authenticator;

import java.io.IOException;
import java.security.Principal;
import java.security.cert.X509Certificate;

import javax.servlet.http.HttpServletResponse;

import org.apache.coyote.ActionCode;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.deploy.LoginConfig;

/**
 * <b>Authenticator</b>和<b>Valve</b>身份验证的实现，使用SSL证书识别客户端用户.
 */
public class SSLAuthenticator extends AuthenticatorBase {

    // ------------------------------------------------------------- Properties

    /**
     * 实现类的描述信息
     */
    protected static final String info =
        "org.apache.catalina.authenticator.SSLAuthenticator/1.0";


    /**
     * 返回实现类的描述信息
     */
    public String getInfo() {
        return (info);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 通过检查证书链的存在来验证用户(应该由一个<code>CertificatesValve</code>实例显示), 
     * 还可以请求信任管理器验证我们信任这个用户
     *
     * @param request Request we are processing
     * @param response Response we are creating
     * @param config 描述如何进行身份验证的登录配置
     *
     * @exception IOException if an input/output error occurs
     */
    public boolean authenticate(Request request,
                                Response response,
                                LoginConfig config)
        throws IOException {

        // Have we already authenticated someone?
        Principal principal = request.getUserPrincipal();
        //String ssoId = (String) request.getNote(Constants.REQ_SSOID_NOTE);
        if (principal != null) {
            if (containerLog.isDebugEnabled())
                containerLog.debug("Already authenticated '" + principal.getName() + "'");
            // 将会话和任何现有的SSO会话关联，为了协调会话在退出登录时失效
            String ssoId = (String) request.getNote(Constants.REQ_SSOID_NOTE);
            if (ssoId != null)
                associate(ssoId, request.getSessionInternal(true));
            return (true);
        }

        // NOTE: 不要试图使用任何现有的SSO会话重新验证,
        // 因为只有原始身份验证是BASIC 或 FORM才能生效, 这比这个应用指定的DIGEST 验证类型更不安全
        //
        // 下面让以前的注释 FORM 或 BASIC 认证对用户进行身份验证
        // TODO 使之成为可配置属性(in SingleSignOn??)
        /*
        // 有一个SSO 会话，可以尝试重新验证?
        if (ssoId != null) {
            if (log.isDebugEnabled())
                log.debug("SSO Id " + ssoId + " set; attempting " +
                          "reauthentication");
            // 尝试使用数据缓存重新验证通过 SSO. 
            // 如果失败, 不论原始SSO登录是DIGEST 或 SSL (无法重新验证，因为没有缓存的用户名和密码), 还是realm 拒绝用户的重新认证.
            // 在这两种情况下，我们都必须提示用户登录
            if (reauthenticateFromSSO(ssoId, request))
                return true;
        }
        */

        // 验证此请求中已经包含的所有凭据
        if (containerLog.isDebugEnabled())
            containerLog.debug(" Looking up certificates");

        X509Certificate certs[] = (X509Certificate[])
            request.getAttribute(Globals.CERTIFICATES_ATTR);
        if ((certs == null) || (certs.length < 1)) {
            request.getCoyoteRequest().action
                              (ActionCode.ACTION_REQ_SSL_CERTIFICATE, null);
            certs = (X509Certificate[])
                request.getAttribute(Globals.CERTIFICATES_ATTR);
        }
        if ((certs == null) || (certs.length < 1)) {
            if (containerLog.isDebugEnabled())
                containerLog.debug("  No certificates included with this request");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                               sm.getString("authenticator.certificates"));
            return (false);
        }

        // 验证指定的证书链
        principal = context.getRealm().authenticate(certs);
        if (principal == null) {
            if (containerLog.isDebugEnabled())
                containerLog.debug("  Realm.authenticate() returned false");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                               sm.getString("authenticator.unauthorized"));
            return (false);
        }

        // 缓存 principal (如果要求) 并记录此身份验证
        register(request, response, principal, Constants.CERT_METHOD,
                 null, null);
        return (true);

    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * 初始化数据库 ，将使用客户端验证和证书验证
     *
     * @exception LifecycleException 如果此组件检测到防止该组件被使用的致命错误
     */
    public void start() throws LifecycleException {
        super.start();
    }


    /**
     * 关闭数据库 ，将使用客户端验证和证书验证
     *
     * @exception LifecycleException 如果此组件检测到防止该组件被使用的致命错误
     */
    public void stop() throws LifecycleException {
        super.stop();
    }
}
