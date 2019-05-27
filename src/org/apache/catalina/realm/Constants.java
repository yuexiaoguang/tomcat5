package org.apache.catalina.realm;


/**
 * Manifest constants for this Java package.
 */
public final class Constants {

    public static final String Package = "org.apache.catalina.realm";
    
    // 登录配置的身份验证方法
    public static final String FORM_METHOD = "FORM";

    // 基于表单的身份验证常量
    public static final String FORM_ACTION = "/j_security_check";

    // 传输保证的用户数据约束
    public static final String NONE_TRANSPORT = "NONE";
    public static final String INTEGRAL_TRANSPORT = "INTEGRAL";
    public static final String CONFIDENTIAL_TRANSPORT = "CONFIDENTIAL";

}
