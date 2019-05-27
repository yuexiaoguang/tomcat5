package org.apache.catalina.authenticator;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.catalina.Realm;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.util.MD5Encoder;

/**
 * <b>Authenticator</b>和<b>Valve</b>的HTTP DIGEST认证的实现类(see RFC 2069).
 */
public class DigestAuthenticator extends AuthenticatorBase {
    private static Log log = LogFactory.getLog(DigestAuthenticator.class);


    // -------------------------------------------------------------- Constants

    protected static final MD5Encoder md5Encoder = new MD5Encoder();


    /**
     * 实现类的表述信息
     */
    protected static final String info =
        "org.apache.catalina.authenticator.DigestAuthenticator/1.0";


    // ----------------------------------------------------------- Constructors


    public DigestAuthenticator() {
        super();
        try {
            if (md5Helper == null)
                md5Helper = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new IllegalStateException();
        }
    }

    // ----------------------------------------------------- Instance Variables

    protected static MessageDigest md5Helper;

    protected String key = "Catalina";

    // ------------------------------------------------------------- Properties

    /**
     * 返回描述信息
     */
    public String getInfo() {
        return (info);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 根据指定的登录配置对作出此请求的用户进行身份验证. 
     * 如果满足指定的约束，返回<code>true</code> , 
     * 否则返回<code>false</code>.
     *
     * @param request Request we are processing
     * @param response Response we are creating
     * @param config  登录配置，描述如何进行身份验证
     *
     * @exception IOException if an input/output error occurs
     */
    public boolean authenticate(Request request,
                                Response response,
                                LoginConfig config)
        throws IOException {

        // 已经验证过某个了吗?
        Principal principal = request.getUserPrincipal();
        //String ssoId = (String) request.getNote(Constants.REQ_SSOID_NOTE);
        if (principal != null) {
            if (log.isDebugEnabled())
                log.debug("Already authenticated '" + principal.getName() + "'");
            // 关联会话和任何现有的 SSO 会话，为了协调会话注销失效
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
        String authorization = request.getHeader("authorization");
        if (authorization != null) {
            principal = findPrincipal(request, authorization, context.getRealm());
            if (principal != null) {
                String username = parseUsername(authorization);
                register(request, response, principal,
                         Constants.DIGEST_METHOD,
                         username, null);
                return (true);
            }
        }

        // 发送"unauthorized" 响应

        // 下一步, 生成一个一次性 token (这是一个唯一的token).
        String nOnce = generateNOnce(request);

        setAuthenticateHeader(request, response, config, nOnce);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        //      hres.flushBuffer();
        return (false);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 解析指定的授权凭据, 以及关联的Principal，这些凭据被指定的Realm验证是有效的. 
     * 如果没有Principal, 返回<code>null</code>.
     *
     * @param request HTTP servlet request
     * @param authorization 这个请求的授权凭据
     * @param realm Realm,用于验证Principals
     */
    protected static Principal findPrincipal(Request request,
                                             String authorization,
                                             Realm realm) {

        //System.out.println("Authorization token : " + authorization);
        // Validate the authorization credentials format
        if (authorization == null)
            return (null);
        if (!authorization.startsWith("Digest "))
            return (null);
        authorization = authorization.substring(7).trim();


        StringTokenizer commaTokenizer =
            new StringTokenizer(authorization, ",");

        String userName = null;
        String realmName = null;
        String nOnce = null;
        String nc = null;
        String cnonce = null;
        String qop = null;
        String uri = null;
        String response = null;
        String method = request.getMethod();

        while (commaTokenizer.hasMoreTokens()) {
            String currentToken = commaTokenizer.nextToken();
            int equalSign = currentToken.indexOf('=');
            if (equalSign < 0)
                return null;
            String currentTokenName =
                currentToken.substring(0, equalSign).trim();
            String currentTokenValue =
                currentToken.substring(equalSign + 1).trim();
            if ("username".equals(currentTokenName))
                userName = removeQuotes(currentTokenValue);
            if ("realm".equals(currentTokenName))
                realmName = removeQuotes(currentTokenValue, true);
            if ("nonce".equals(currentTokenName))
                nOnce = removeQuotes(currentTokenValue);
            if ("nc".equals(currentTokenName))
                nc = removeQuotes(currentTokenValue);
            if ("cnonce".equals(currentTokenName))
                cnonce = removeQuotes(currentTokenValue);
            if ("qop".equals(currentTokenName))
                qop = removeQuotes(currentTokenValue);
            if ("uri".equals(currentTokenName))
                uri = removeQuotes(currentTokenValue);
            if ("response".equals(currentTokenName))
                response = removeQuotes(currentTokenValue);
        }

        if ( (userName == null) || (realmName == null) || (nOnce == null)
             || (uri == null) || (response == null) )
            return null;

        // Second MD5 digest used to calculate the digest :
        // MD5(Method + ":" + uri)
        String a2 = method + ":" + uri;
        //System.out.println("A2:" + a2);

        byte[] buffer = null;
        synchronized (md5Helper) {
            buffer = md5Helper.digest(a2.getBytes());
        }
        String md5a2 = md5Encoder.encode(buffer);

        return (realm.authenticate(userName, response, nOnce, nc, cnonce, qop,
                                   realmName, md5a2));
    }


    /**
     * 从指定的授权字符串解析用户名. 
     * 没有返回<code>null</code>
     *
     * @param authorization Authorization string to be parsed
     */
    protected String parseUsername(String authorization) {

        //System.out.println("Authorization token : " + authorization);
        // Validate the authorization credentials format
        if (authorization == null)
            return (null);
        if (!authorization.startsWith("Digest "))
            return (null);
        authorization = authorization.substring(7).trim();

        StringTokenizer commaTokenizer =
            new StringTokenizer(authorization, ",");

        while (commaTokenizer.hasMoreTokens()) {
            String currentToken = commaTokenizer.nextToken();
            int equalSign = currentToken.indexOf('=');
            if (equalSign < 0)
                return null;
            String currentTokenName =
                currentToken.substring(0, equalSign).trim();
            String currentTokenValue =
                currentToken.substring(equalSign + 1).trim();
            if ("username".equals(currentTokenName))
                return (removeQuotes(currentTokenValue));
        }

        return (null);
    }


    /**
     * 删除字符串上的引号. RFC2617 状态引用对于除域以外的所有参数都是可选的
     */
    protected static String removeQuotes(String quotedString,
                                         boolean quotesRequired) {
        //支持引号和非引号
        if (quotedString.length() > 0 && quotedString.charAt(0) != '"' &&
                !quotesRequired) {
            return quotedString;
        } else if (quotedString.length() > 2) {
            return quotedString.substring(1, quotedString.length() - 1);
        } else {
            return new String();
        }
    }

    /**
     * 删除字符串上的引号
     */
    protected static String removeQuotes(String quotedString) {
        return removeQuotes(quotedString, false);
    }

    /**
     * 生成唯一令牌。令牌是根据以下模式生成的. 
     * NOnceToken = Base64 ( MD5 ( client-IP ":" time-stamp ":" private-key ) ).
     *
     * @param request HTTP Servlet request
     */
    protected String generateNOnce(Request request) {

        long currentTime = System.currentTimeMillis();

        String nOnceValue = request.getRemoteAddr() + ":" +
            currentTime + ":" + key;

        byte[] buffer = null;
        synchronized (md5Helper) {
            buffer = md5Helper.digest(nOnceValue.getBytes());
        }
        nOnceValue = md5Encoder.encode(buffer);

        return nOnceValue;
    }


    /**
     * 生成 WWW-Authenticate header.
     * <p>
     * header 必须遵循此模板 :
     * <pre>
     *      WWW-Authenticate    = "WWW-Authenticate" ":" "Digest"
     *                            digest-challenge
     *
     *      digest-challenge    = 1#( realm | [ domain ] | nOnce |
     *                  [ digest-opaque ] |[ stale ] | [ algorithm ] )
     *
     *      realm               = "realm" "=" realm-value
     *      realm-value         = quoted-string
     *      domain              = "domain" "=" <"> 1#URI <">
     *      nonce               = "nonce" "=" nonce-value
     *      nonce-value         = quoted-string
     *      opaque              = "opaque" "=" quoted-string
     *      stale               = "stale" "=" ( "true" | "false" )
     *      algorithm           = "algorithm" "=" ( "MD5" | token )
     * </pre>
     *
     * @param request HTTP Servlet request
     * @param response HTTP Servlet response
     * @param config  描述如何进行身份验证的登录配置
     * @param nOnce nonce token
     */
    protected void setAuthenticateHeader(Request request,
                                         Response response,
                                         LoginConfig config,
                                         String nOnce) {

        // 获取域名
        String realmName = config.getRealmName();
        if (realmName == null)
            realmName = request.getServerName() + ":"
                + request.getServerPort();

        byte[] buffer = null;
        synchronized (md5Helper) {
            buffer = md5Helper.digest(nOnce.getBytes());
        }

        String authenticateHeader = "Digest realm=\"" + realmName + "\", "
            +  "qop=\"auth\", nonce=\"" + nOnce + "\", " + "opaque=\""
            + md5Encoder.encode(buffer) + "\"";
        response.setHeader("WWW-Authenticate", authenticateHeader);
    }
}
