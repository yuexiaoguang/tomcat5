package org.apache.catalina.authenticator;

import java.io.IOException;
import java.security.Principal;

import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.util.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;

/**
 * <b>Authenticator</b>和<b>Valve</b>的HTTP BASIC身份验证实现类, 
 * RFC 2617概述: "HTTP Authentication: 基本和摘要访问验证."
 */
public class BasicAuthenticator extends AuthenticatorBase {
    private static Log log = LogFactory.getLog(BasicAuthenticator.class);

    /**
     * 验证字节.
     */
    public static final byte[] AUTHENTICATE_BYTES = {
        (byte) 'W',
        (byte) 'W',
        (byte) 'W',
        (byte) '-',
        (byte) 'A',
        (byte) 'u',
        (byte) 't',
        (byte) 'h',
        (byte) 'e',
        (byte) 'n',
        (byte) 't',
        (byte) 'i',
        (byte) 'c',
        (byte) 'a',
        (byte) 't',
        (byte) 'e'
    };


   // ----------------------------------------------------- Instance Variables


    /**
     * 实现类描述信息
     */
    protected static final String info =
        "org.apache.catalina.authenticator.BasicAuthenticator/1.0";


    // ------------------------------------------------------------- Properties

    /**
     * 返回关于Valve实现类的描述信息
     */
    public String getInfo() {
        return (info);
    }

    // --------------------------------------------------------- Public Methods


    /**
     * 根据指定的登录配置，对作出此请求的用户进行身份验证. 
     * 如果满足指定的约束，返回<code>true</code>;
     * 如果已经创建了一个响应，返回<code>false</code>
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

        // 是否转备好验证其中一个?
        Principal principal = request.getUserPrincipal();
        String ssoId = (String) request.getNote(Constants.REQ_SSOID_NOTE);
        if (principal != null) {
            if (log.isDebugEnabled())
                log.debug("Already authenticated '" + principal.getName() + "'");
            // 将会话与任何现有SSO会话关联
            if (ssoId != null)
                associate(ssoId, request.getSessionInternal(true));
            return (true);
        }

        // 有一个登录会话，可以尝试验证?
        if (ssoId != null) {
            if (log.isDebugEnabled())
                log.debug("SSO Id " + ssoId + " set; attempting " +
                          "reauthentication");
            /* 尝试使用数据缓存重新验证通过 SSO. 
             * 如果失败, 不论原始SSO登录是DIGEST 或 SSL (无法重新验证，因为没有缓存的用户名和密码), 还是realm 拒绝用户的重新认证.
             * 在这两种情况下，我们都必须提示用户登录
             */
            if (reauthenticateFromSSO(ssoId, request))
                return true;
        }

        // 验证此请求中已经包含的所有凭据
        String username = null;
        String password = null;

        MessageBytes authorization = 
            request.getCoyoteRequest().getMimeHeaders()
            .getValue("authorization");
        
        if (authorization != null) {
            authorization.toBytes();
            ByteChunk authorizationBC = authorization.getByteChunk();
            if (authorizationBC.startsWithIgnoreCase("basic ", 0)) {
                authorizationBC.setOffset(authorizationBC.getOffset() + 6);
                // FIXME: Add trimming
                // authorizationBC.trim();
                
                CharChunk authorizationCC = authorization.getCharChunk();
                Base64.decode(authorizationBC, authorizationCC);
                
                // Get username and password
                int colon = authorizationCC.indexOf(':');
                if (colon < 0) {
                    username = authorizationCC.toString();
                } else {
                    char[] buf = authorizationCC.getBuffer();
                    username = new String(buf, 0, colon);
                    password = new String(buf, colon + 1, 
                            authorizationCC.getEnd() - colon - 1);
                }
                
                authorizationBC.setOffset(authorizationBC.getOffset() - 6);
            }

            principal = context.getRealm().authenticate(username, password);
            if (principal != null) {
                register(request, response, principal, Constants.BASIC_METHOD,
                         username, password);
                return (true);
            }
        }
        

        // 发送一个"unauthorized"响应和适当的疑问
        MessageBytes authenticate = 
            response.getCoyoteResponse().getMimeHeaders()
            .addValue(AUTHENTICATE_BYTES, 0, AUTHENTICATE_BYTES.length);
        CharChunk authenticateCC = authenticate.getCharChunk();
        authenticateCC.append("Basic realm=\"");
        if (config.getRealmName() == null) {
            authenticateCC.append(request.getServerName());
            authenticateCC.append(':');
            authenticateCC.append(Integer.toString(request.getServerPort()));
        } else {
            authenticateCC.append(config.getRealmName());
        }
        authenticateCC.append('\"');        
        authenticate.toChars();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        //response.flushBuffer();
        return (false);
    }
}
