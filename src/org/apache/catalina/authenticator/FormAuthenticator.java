package org.apache.catalina.authenticator;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Locale;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Realm;
import org.apache.catalina.Session;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.coyote.ActionCode;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;

/**
 * <b>Authenticator</b>和<b>Valve</b>的FORM BASED验证实现类, 正如servlet API规范中描述的, Version 2.2.
 */
public class FormAuthenticator extends AuthenticatorBase {
    
    private static Log log = LogFactory.getLog(FormAuthenticator.class);

    // ----------------------------------------------------- Instance Variables


    /**
     * 实现类的描述信息
     */
    protected static final String info =
        "org.apache.catalina.authenticator.FormAuthenticator/1.0";

    /**
     * 用于从请求读取用户名和密码参数的字符编码.
     * 如果未设置, 将使用请求正文的编码.
     */
    protected String characterEncoding = null;


    // ------------------------------------------------------------- Properties


    /**
     * 返回Valve实现类的描述信息
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 返回用于读取用户名和密码的字符编码.
     */
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    
    /**
     * 设置用于读取用户名和密码的字符编码. 
     */
    public void setCharacterEncoding(String encoding) {
        characterEncoding = encoding;
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
     * @exception IOException 如果发生输入/输出错误
     */
    public boolean authenticate(Request request,
                                Response response,
                                LoginConfig config)
        throws IOException {

        // 稍后需要的对象的引用
        Session session = null;

        // 是否转备好验证其中一个?
        Principal principal = request.getUserPrincipal();
        String ssoId = (String) request.getNote(Constants.REQ_SSOID_NOTE);
        if (principal != null) {
            if (log.isDebugEnabled())
                log.debug("Already authenticated '" +
                    principal.getName() + "'");
            // Associate the session with any existing SSO session
            if (ssoId != null)
                associate(ssoId, request.getSessionInternal(true));
            return (true);
        }

        // 有一个登录会话，可以尝试验证?
        if (ssoId != null) {
            if (log.isDebugEnabled())
                log.debug("SSO Id " + ssoId + " set; attempting " + "reauthentication");
            
            /* 尝试使用数据缓存重新验证通过 SSO. 
             * 如果失败, 不论原始SSO登录是DIGEST 或 SSL (无法重新验证，因为没有缓存的用户名和密码), 还是realm 拒绝用户的重新认证.
             * 在这两种情况下，我们都必须提示用户登录
             */
            if (reauthenticateFromSSO(ssoId, request))
                return true;
        }

        // 是否已经验证过这个用户，但是已经禁用了缓存？
        if (!cache) {
            session = request.getSessionInternal(true);
            if (log.isDebugEnabled())
                log.debug("Checking for reauthenticate in session " + session);
            String username =
                (String) session.getNote(Constants.SESS_USERNAME_NOTE);
            String password =
                (String) session.getNote(Constants.SESS_PASSWORD_NOTE);
            if ((username != null) && (password != null)) {
                if (log.isDebugEnabled())
                    log.debug("Reauthenticating username '" + username + "'");
                principal =
                    context.getRealm().authenticate(username, password);
                if (principal != null) {
                    session.setNote(Constants.FORM_PRINCIPAL_NOTE, principal);
                    if (!matchRequest(request)) {
                        register(request, response, principal,
                                 Constants.FORM_METHOD,
                                 username, password);
                        return (true);
                    }
                }
                if (log.isDebugEnabled())
                    log.debug("Reauthentication failed, proceed normally");
            }
        }

        // 这是在成功验证身份之后原始请求URI的重新提交吗?  如果是这样的, 重定向 *original* 请求.
        if (matchRequest(request)) {
            session = request.getSessionInternal(true);
            if (log.isDebugEnabled())
                log.debug("Restore request from session '"
                          + session.getIdInternal() 
                          + "'");
            principal = (Principal)
                session.getNote(Constants.FORM_PRINCIPAL_NOTE);
            register(request, response, principal, Constants.FORM_METHOD,
                     (String) session.getNote(Constants.SESS_USERNAME_NOTE),
                     (String) session.getNote(Constants.SESS_PASSWORD_NOTE));
            // 如果我们正在缓存主体，我们不再需要会话中的用户名和密码，所以删除它们
            if (cache) {
                session.removeNote(Constants.SESS_USERNAME_NOTE);
                session.removeNote(Constants.SESS_PASSWORD_NOTE);
            }
            if (restoreRequest(request, session)) {
                if (log.isDebugEnabled())
                    log.debug("Proceed to restored request");
                return (true);
            } else {
                if (log.isDebugEnabled())
                    log.debug("Restore of original request failed");
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return (false);
            }
        }

        // 获取需要评估的对象的引用
        MessageBytes uriMB = MessageBytes.newInstance();
        CharChunk uriCC = uriMB.getCharChunk();
        uriCC.setLimit(-1);
        String contextPath = request.getContextPath();
        String requestURI = request.getDecodedRequestURI();
        response.setContext(request.getContext());

        // 这是登录页面的操作请求吗?
        boolean loginAction =
            requestURI.startsWith(contextPath) &&
            requestURI.endsWith(Constants.FORM_ACTION);

        // No -- 保存此请求并重定向到表单登录页面
        if (!loginAction) {
            session = request.getSessionInternal(true);
            if (log.isDebugEnabled())
                log.debug("Save request in session '" + session.getIdInternal() + "'");
            try {
                saveRequest(request, session);
            } catch (IOException ioe) {
                log.debug("Request body too big to save during authentication");
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        sm.getString("authenticator.requestBodyTooBig"));
                return (false);
            }
            forwardToLoginPage(request, response, config);
            return (false);
        }

        // Yes -- 验证指定的凭据并重定向到错误页，如果它们不正确
        Realm realm = context.getRealm();
        if (characterEncoding != null) {
            request.setCharacterEncoding(characterEncoding);
        }
        String username = request.getParameter(Constants.FORM_USERNAME);
        String password = request.getParameter(Constants.FORM_PASSWORD);
        if (log.isDebugEnabled())
            log.debug("Authenticating username '" + username + "'");
        principal = realm.authenticate(username, password);
        if (principal == null) {
            forwardToErrorPage(request, response, config);
            return (false);
        }

        if (log.isDebugEnabled())
            log.debug("Authentication of '" + username + "' was successful");

        if (session == null)
            session = request.getSessionInternal(false);
        if (session == null) {
            if (containerLog.isDebugEnabled())
                containerLog.debug
                    ("User took so long to log on the session expired");
            response.sendError(HttpServletResponse.SC_REQUEST_TIMEOUT,
                               sm.getString("authenticator.sessionExpired"));
            return (false);
        }

        // 将认证主体保存在会话中
        session.setNote(Constants.FORM_PRINCIPAL_NOTE, principal);

        // 保存用户名和密码
        session.setNote(Constants.SESS_USERNAME_NOTE, username);
        session.setNote(Constants.SESS_PASSWORD_NOTE, password);

        // 将用户重定向到原始请求URI(这将导致原始请求被恢复)
        requestURI = savedRequestURL(session);
        if (log.isDebugEnabled())
            log.debug("Redirecting to original '" + requestURI + "'");
        if (requestURI == null)
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                               sm.getString("authenticator.formlogin"));
        else
            response.sendRedirect(response.encodeRedirectURL(requestURI));
        return (false);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 转发到登录页面
     * 
     * @param request Request we are processing
     * @param response Response we are creating
     * @param config  描述如何进行身份验证的登录配置
     */
    protected void forwardToLoginPage(Request request, Response response, LoginConfig config) {
        RequestDispatcher disp =
            context.getServletContext().getRequestDispatcher
            (config.getLoginPage());
        try {
            disp.forward(request.getRequest(), response.getResponse());
            response.finishResponse();
        } catch (Throwable t) {
            log.warn("Unexpected error forwarding to login page", t);
        }
    }


    /**
     * 重定向到错误页
     * 
     * @param request Request we are processing
     * @param response Response we are creating
     * @param config  描述如何进行身份验证的登录配置
     */
    protected void forwardToErrorPage(Request request, Response response, LoginConfig config) {
        RequestDispatcher disp =
            context.getServletContext().getRequestDispatcher
            (config.getErrorPage());
        try {
            disp.forward(request.getRequest(), response.getResponse());
        } catch (Throwable t) {
            log.warn("Unexpected error forwarding to error page", t);
        }
    }


    /**
     * 这个请求是否与保存的请求相匹配?(因此它必须是在成功身份验证之后发出的重定向)
     *
     * @param request The request to be verified
     */
    protected boolean matchRequest(Request request) {

      // Has a session been created?
      Session session = request.getSessionInternal(false);
      if (session == null)
          return (false);

      // Is there a saved request?
      SavedRequest sreq = (SavedRequest)
          session.getNote(Constants.FORM_REQUEST_NOTE);
      if (sreq == null)
          return (false);

      // Is there a saved principal?
      if (session.getNote(Constants.FORM_PRINCIPAL_NOTE) == null)
          return (false);

      // Does the request URI match?
      String requestURI = request.getRequestURI();
      if (requestURI == null)
          return (false);
      return (requestURI.equals(request.getRequestURI()));

    }


    /**
     * 从会话中存储的信息恢复原始请求.
     * 如果原始请求不再存在 (由于会话超时), 返回<code>false</code>; 否则返回<code>true</code>.
     *
     * @param request 要恢复的请求
     * @param session 包含保存的信息的会话
     */
    protected boolean restoreRequest(Request request, Session session)
        throws IOException {

        // 从会话中检索和删除 SavedRequest对象
        SavedRequest saved = (SavedRequest)
            session.getNote(Constants.FORM_REQUEST_NOTE);
        session.removeNote(Constants.FORM_REQUEST_NOTE);
        session.removeNote(Constants.FORM_PRINCIPAL_NOTE);
        if (saved == null)
            return (false);

        // 修改当前请求以反映原始请求
        request.clearCookies();
        Iterator cookies = saved.getCookies();
        while (cookies.hasNext()) {
            request.addCookie((Cookie) cookies.next());
        }

        request.getCoyoteRequest().getMimeHeaders().recycle();
        Iterator names = saved.getHeaderNames();
        while (names.hasNext()) {
            String name = (String) names.next();
            Iterator values = saved.getHeaderValues(name);
            while (values.hasNext()) {
                request.addHeader(name, (String) values.next());
            }
        }
        
        request.clearLocales();
        Iterator locales = saved.getLocales();
        while (locales.hasNext()) {
            request.addLocale((Locale) locales.next());
        }
        
        request.getCoyoteRequest().getParameters().recycle();
        
        if ("POST".equalsIgnoreCase(saved.getMethod())) {
            ByteChunk body = saved.getBody();
            
            if (body != null) {
                request.getCoyoteRequest().action
                    (ActionCode.ACTION_REQ_SET_BODY_REPLAY, body);
    
                // Set content type
                MessageBytes contentType = MessageBytes.newInstance();
                contentType.setString("application/x-www-form-urlencoded");
                request.getCoyoteRequest().setContentType(contentType);
            }
        }
        request.getCoyoteRequest().method().setString(saved.getMethod());

        request.getCoyoteRequest().queryString().setString
            (saved.getQueryString());

        request.getCoyoteRequest().requestURI().setString
            (saved.getRequestURI());
        return (true);

    }


    /**
     * 将原始请求信息保存到会话中
     *
     * @param request 要保存的请求
     * @param session 包含保存信息的会话
     * @throws IOException 如果过程中发生了IO错误
     */
    protected void saveRequest(Request request, Session session) throws IOException {

        // 创建和填充一个 SavedRequest 对象
        SavedRequest saved = new SavedRequest();
        Cookie cookies[] = request.getCookies();
        if (cookies != null) {
            for (int i = 0; i < cookies.length; i++)
                saved.addCookie(cookies[i]);
        }
        Enumeration names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = (String) names.nextElement();
            Enumeration values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                String value = (String) values.nextElement();
                saved.addHeader(name, value);
            }
        }
        Enumeration locales = request.getLocales();
        while (locales.hasMoreElements()) {
            Locale locale = (Locale) locales.nextElement();
            saved.addLocale(locale);
        }

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            ByteChunk body = new ByteChunk();
            body.setLimit(request.getConnector().getMaxSavePostSize());

            byte[] buffer = new byte[4096];
            int bytesRead;
            InputStream is = request.getInputStream();
        
            while ( (bytesRead = is.read(buffer) ) >= 0) {
                body.append(buffer, 0, bytesRead);
            }
            saved.setBody(body);
        }

        saved.setMethod(request.getMethod());
        saved.setQueryString(request.getQueryString());
        saved.setRequestURI(request.getRequestURI());

        // 隐藏SavedRequest到session中，以后使用
        session.setNote(Constants.FORM_REQUEST_NOTE, saved);
    }


    /**
     * 返回保存的请求的URI (使用相应的查询字符串)，这样就可以重定向到它
     *
     * @param session 当前会话
     */
    protected String savedRequestURL(Session session) {

        SavedRequest saved =
            (SavedRequest) session.getNote(Constants.FORM_REQUEST_NOTE);
        if (saved == null)
            return (null);
        StringBuffer sb = new StringBuffer(saved.getRequestURI());
        if (saved.getQueryString() != null) {
            sb.append('?');
            sb.append(saved.getQueryString());
        }
        return (sb.toString());
    }
}
