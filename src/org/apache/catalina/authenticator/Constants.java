package org.apache.catalina.authenticator;


public class Constants {

    public static final String Package = "org.apache.catalina.authenticator";

    // 登录配置的身份验证方法
    public static final String BASIC_METHOD = "BASIC";
    public static final String CERT_METHOD = "CLIENT-CERT";
    public static final String DIGEST_METHOD = "DIGEST";
    public static final String FORM_METHOD = "FORM";

    // 传输保证的用户数据约束
    public static final String NONE_TRANSPORT = "NONE";
    public static final String INTEGRAL_TRANSPORT = "INTEGRAL";
    public static final String CONFIDENTIAL_TRANSPORT = "CONFIDENTIAL";

    // 基于表单的身份验证常量
    public static final String FORM_ACTION = "/j_security_check";
    public static final String FORM_PASSWORD = "j_password";
    public static final String FORM_USERNAME = "j_username";

    // 单点登录支持的cookie名称
    public static final String SINGLE_SIGN_ON_COOKIE = "JSESSIONIDSSO";


    // --------------------------------------------------------- Request Notes


    /**
     * <p>如果用户已经通过网络层的认证, 用一个不同于CLIENT_CERT的登录方法, 
     * 用于验证用户的用户名和密码，将作为附注附加到请求，被其他服务器组件使用。
     * 服务器组件还可以根据请求调用多个现有方法，以确定是否已对所有用户进行了身份验证:</p>
     * <ul>
     * <li><strong>((HttpServletRequest) getRequest()).getAuthType()</strong>
     *     将返回 BASIC, CLIENT-CERT, DIGEST, FORM, 或<code>null</code>
     *     如果没有经过身份验证的用户.</li>
     * <li><strong>((HttpServletRequest) getRequest()).getUserPrincipal()</strong>
     *     将返回经过身份验证的<code>Principal</code>（验证此用户的域返回的）.</li>
     * </ul>
     * <p>如果CLIENT_CERT认证进行, 证书链将作为请求属性可用, 如servlet规范中定义的.</p>
     */


    /**
     * 用于验证此用户的密码的key
     */
    public static final String REQ_PASSWORD_NOTE = "org.apache.catalina.request.PASSWORD";


    /**
     * 用于验证此用户的用户名的key
     */
    public static final String REQ_USERNAME_NOTE = "org.apache.catalina.request.USERNAME";


    /**
     * 跟踪此请求关联的单点登录的ID的key
     */
    public static final String REQ_SSOID_NOTE = "org.apache.catalina.request.SSOID";


    // ---------------------------------------------------------- Session Notes


    /**
     * 如果身份认证的属性<code>cache</code>被设置, 当前请求是会话的一部分, 验证信息将被缓存，以避免重复调用
     * <code>Realm.authenticate()</code>, 下面是key:
     */


    /**
     * 用于验证此用户的密码的key
     */
    public static final String SESS_PASSWORD_NOTE = "org.apache.catalina.session.PASSWORD";


    /**
     * 用于验证此用户的用户名的key
     */
    public static final String SESS_USERNAME_NOTE = "org.apache.catalina.session.USERNAME";


    /**
     * 下面的key用于在表单登录处理过程中缓存所需的信息，在完成身份验证之前
     */


    /**
     * 先前认证的principal (如果缓存被禁用).
     */
    public static final String FORM_PRINCIPAL_NOTE = "org.apache.catalina.authenticator.PRINCIPAL";


    /**
     * 原始请求数据, 如果验证成功，用户将被重定向
     */
    public static final String FORM_REQUEST_NOTE = "org.apache.catalina.authenticator.REQUEST";

}
