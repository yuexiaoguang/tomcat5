package org.apache.catalina.deploy;

import org.apache.catalina.util.RequestUtil;
import java.io.Serializable;

/**
 * Web应用程序的登录配置元素的表示,在部署描述中使用<code>&lt;login-config&gt;</code>元素表示
 */
public class LoginConfig implements Serializable {

    // ----------------------------------------------------------- Constructors

    public LoginConfig() {
        super();
    }


    /**
     * @param authMethod 身份验证方法
     * @param realmName The realm name
     * @param loginPage The login page URI
     * @param errorPage The error page URI
     */
    public LoginConfig(String authMethod, String realmName,
                       String loginPage, String errorPage) {
        super();
        setAuthMethod(authMethod);
        setRealmName(realmName);
        setLoginPage(loginPage);
        setErrorPage(errorPage);
    }


    // ------------------------------------------------------------- Properties


    /**
     * 用于应用程序登录的身份验证方法. 
     * 必须是 BASIC, DIGEST, FORM, or CLIENT-CERT.
     */
    private String authMethod = null;

    public String getAuthMethod() {
        return (this.authMethod);
    }

    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }


    /**
     * 表单登录错误页面的上下文相对URI
     */
    private String errorPage = null;

    public String getErrorPage() {
        return (this.errorPage);
    }

    public void setErrorPage(String errorPage) {
        //        if ((errorPage == null) || !errorPage.startsWith("/"))
        //            throw new IllegalArgumentException
        //                ("Error Page resource path must start with a '/'");
        this.errorPage = RequestUtil.URLDecode(errorPage);
    }


    /**
     * 表单登录的登录页的上下文相对URI
     */
    private String loginPage = null;

    public String getLoginPage() {
        return (this.loginPage);
    }

    public void setLoginPage(String loginPage) {
        //        if ((loginPage == null) || !loginPage.startsWith("/"))
        //            throw new IllegalArgumentException
        //                ("Login Page resource path must start with a '/'");
        this.loginPage = RequestUtil.URLDecode(loginPage);
    }


    /**
     * 在向用户询问身份验证凭据时使用的realm名称.
     */
    private String realmName = null;

    public String getRealmName() {
        return (this.realmName);
    }

    public void setRealmName(String realmName) {
        this.realmName = realmName;
    }

    // --------------------------------------------------------- Public Methods

    public String toString() {
        StringBuffer sb = new StringBuffer("LoginConfig[");
        sb.append("authMethod=");
        sb.append(authMethod);
        if (realmName != null) {
            sb.append(", realmName=");
            sb.append(realmName);
        }
        if (loginPage != null) {
            sb.append(", loginPage=");
            sb.append(loginPage);
        }
        if (errorPage != null) {
            sb.append(", errorPage=");
            sb.append(errorPage);
        }
        sb.append("]");
        return (sb.toString());
    }
}
