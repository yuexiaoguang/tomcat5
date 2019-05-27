package org.apache.catalina;


/**
 * 全局常量，适用于Catalina中的多个包
 */
public final class Globals {

    /**
     * 为这个Web应用程序存储的备用部署描述符的servlet上下文属性
     */
    public static final String ALT_DD_ATTR = "org.apache.catalina.deploy.alt_dd";

    /**
     * 请求属性,保存的X509Certificate对象的数组作为客户端提交的证书链
     */
    public static final String CERTIFICATES_ATTR = "javax.servlet.request.X509Certificate";

    /**
     * 请求属性,保存的SSL连接上使用的密码套件的名称(java.lang.String类型).
     */
    public static final String CIPHER_SUITE_ATTR = "javax.servlet.request.cipher_suite";


    /**
     * servlet上下文属性，为加载Servlet保存的classLoader(java.lang.ClassLoader).
     */
    public static final String CLASS_LOADER_ATTR = "org.apache.catalina.classloader";

    /**
     * 请求调度器状态
     */
    public static final String DISPATCHER_TYPE_ATTR = "org.apache.catalina.core.DISPATCHER_TYPE";

    /**
     * 请求调度器路径
     */
    public static final String DISPATCHER_REQUEST_PATH_ATTR = "org.apache.catalina.core.DISPATCHER_REQUEST_PATH";

    /**
     * JNDI目录上下文. 此上下文可用于处理静态文件
     */
    public static final String RESOURCES_ATTR = "org.apache.catalina.resources";


    /**
     * servlet上下文属性，保存的应用类加载器路径,
     * 为这个平台使用适当的路径分隔符分隔
     */
    public static final String CLASS_PATH_ATTR = "org.apache.catalina.jsp_classpath";


    /**
     * 请求属性，重定向Java异常到错误页面
     */
    public static final String EXCEPTION_ATTR = "javax.servlet.error.exception";


    /**
     * 请求属性，发生错误时，重定向页面的请求路径
     */
    public static final String EXCEPTION_PAGE_ATTR = "javax.servlet.error.request_uri";


    /**
     * 请求属性，重定向Java异常类型到错误页面
     */
    public static final String EXCEPTION_TYPE_ATTR = "javax.servlet.error.exception_type";


    /**
     * 请求属性，重定向HTTP状态信息到错误页面
     */
    public static final String ERROR_MESSAGE_ATTR = "javax.servlet.error.message";


    /**
     * 请求属性，调用servlet将保存正在调用的servlet路径，如果它被用来间接地执行一个servlet而不是通过一个Servlet映射
     */
    public static final String INVOKED_ATTR = "org.apache.catalina.INVOKED";


    /**
     * 请求属性，展示<code>&lt;jsp-file&gt;</code>的值
     */
    public static final String JSP_FILE_ATTR = "org.apache.catalina.jsp_file";


    /**
     * 请求属性，用于SSL连接的秘钥大小(java.lang.Integer类型).
     */
    public static final String KEY_SIZE_ATTR = "javax.servlet.request.key_size";

    /**
     * 存储用于此SSL连接的会话ID的请求属性(java.lang.String类型).
     */
    public static final String SSL_SESSION_ID_ATTR = "javax.servlet.request.ssl_session";


    /**
     * Servlet上下文属性，将被保存的bean注册表
     */
    public static final String MBEAN_REGISTRY_ATTR = "org.apache.catalina.Registry";


    /**
     * Servlet上下文属性，将被保存的MBeanServer
     */
    public static final String MBEAN_SERVER_ATTR = "org.apache.catalina.MBeanServer";


    /**
     * 请求属性，将servlet名称存储在指定的调度请求上
     */
    public static final String NAMED_DISPATCHER_ATTR = "org.apache.catalina.NAMED";


    /**
     * 请求属性，原始请求URI存储在一个包含的调度请求中
     */
    public static final String INCLUDE_REQUEST_URI_ATTR = "javax.servlet.include.request_uri";


    /**
     * 请求属性，其中包含的servlet的上下文路径存储在包含的调度器请求中.
     */
    public static final String INCLUDE_CONTEXT_PATH_ATTR = "javax.servlet.include.context_path";


    /**
     * 请求属性，其中包含的servlet的路径信息存储在包含的调度器请求中.
     */
    public static final String INCLUDE_PATH_INFO_ATTR = "javax.servlet.include.path_info";


    /**
     * 请求属性，原始servlet路径存储在一个包含的调度请求中
     */
    public static final String INCLUDE_SERVLET_PATH_ATTR = "javax.servlet.include.servlet_path";


    /**
     * 请求属性，其中包含的servlet的查询字符串存储在包含的调度器请求中.
     */
    public static final String INCLUDE_QUERY_STRING_ATTR = "javax.servlet.include.query_string";


    public static final String FORWARD_REQUEST_URI_ATTR = "javax.servlet.forward.request_uri";
    
    
    public static final String FORWARD_CONTEXT_PATH_ATTR = "javax.servlet.forward.context_path";


    public static final String FORWARD_PATH_INFO_ATTR = "javax.servlet.forward.path_info";


    public static final String FORWARD_SERVLET_PATH_ATTR = "javax.servlet.forward.servlet_path";


    public static final String FORWARD_QUERY_STRING_ATTR = "javax.servlet.forward.query_string";


    /**
     * 将servlet名称转发给错误页面的请求属性.
     */
    public static final String SERVLET_NAME_ATTR = "javax.servlet.error.servlet_name";

    
    /**
     * 用于与客户端来回传递会话标识符的cookie的名称.
     */
    public static final String SESSION_COOKIE_NAME = "JSESSIONID";


    /**
     * 用于与客户端来回传递会话标识符的路径参数的名称
     */
    public static final String SESSION_PARAMETER_NAME = "jsessionid";


    /**
     * Servlet上下文属性，保存一个标识，用于标记已经被SSIServlet处理过的请求
    * 这样做，是因为当一起使用CGIServlet和SSI Servlet的时候，路径信息损坏将会发生
     */
     public static final String SSI_FLAG_ATTR = "org.apache.catalina.ssi.SSIServlet";


    /**
     * 请求属性，重定向一个HTTP状态码到一个错误页面
     */
    public static final String STATUS_CODE_ATTR = "javax.servlet.error.status_code";


    /**
     * AccessControlContext运行的主题.
     */
    public static final String SUBJECT_ATTR = "javax.security.auth.subject";

    
    /**
     * Servlet上下文属性，记录欢迎文件集合(String[]类型) 
     */
    public static final String WELCOME_FILES_ATTR = "org.apache.catalina.WELCOME_FILES";


    /**
     * Servlet上下文属性，保存一个Servlet使用的临时工作目录
     */
    public static final String WORK_DIR_ATTR = "javax.servlet.context.tempdir";

}
