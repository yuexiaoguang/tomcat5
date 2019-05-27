package org.apache.catalina.authenticator;

import java.io.IOException;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.deploy.LoginConfig;

/**
 * <b>Authenticator</b>和<b>Valve</b>实现类， 只检查不涉及用户身份验证的安全约束
 */
public final class NonLoginAuthenticator extends AuthenticatorBase {

    // ----------------------------------------------------- Instance Variables

    /**
     * 实现类的描述信息
     */
    private static final String info =
        "org.apache.catalina.authenticator.NonLoginAuthenticator/1.0";


    // ------------------------------------------------------------- Properties


    /**
     * 返回Valve实现类的描述信息
     */
    public String getInfo() {
        return (info);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 根据指定的登录配置，对作出此请求的用户进行身份验证. 
     * 如果满足指定的约束，返回<code>true</code>;
     * 如果已经创建了一个响应，返回 or <code>false</code>
     *
     * @param request Request we are processing
     * @param response Response we are creating
     * @param config  描述如何进行身份验证的登录配置
     *
     * @exception IOException if an input/output error occurs
     */
    public boolean authenticate(Request request,
                                Response response,
                                LoginConfig config)
        throws IOException {

        /*  将请求的会话和一个 SSO关联允许协调会话失效, 当另一个会话登出时, 用户没有登录的web应用的会话应该失效吗?
        String ssoId = (String) request.getNote(Constants.REQ_SSOID_NOTE);
        if (ssoId != null)
            associate(ssoId, getSession(request, true));
        */
        
        if (containerLog.isDebugEnabled())
            containerLog.debug("User authentication is not required");
        return (true);
    }
}
