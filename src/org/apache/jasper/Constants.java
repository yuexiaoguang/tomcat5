package org.apache.jasper;


/**
 * 编译器和运行时使用的一些常量和其他全局数据.
 */
public class Constants {
    /**
     * 所生成的servlet基类. 
     */
    public static final String JSP_SERVLET_BASE = "org.apache.jasper.runtime.HttpJspBase";

    /**
     * _jspService 是方法的名称通过HttpJspBase.service()调用. 这是大多数从JSP生成的代码的地方.
     */
    public static final String SERVICE_METHOD_NAME = "_jspService";

    /**
     * 默认servlet内容类型.
     */
    public static final String SERVLET_CONTENT_TYPE = "text/html";

    /**
     * 这些classes/packages 生成代码的时候自动导入. 
     */
    public static final String[] STANDARD_IMPORTS = { 
		"javax.servlet.*", 
		"javax.servlet.http.*", 
		"javax.servlet.jsp.*"
    };

    /**
     * FIXME
     * classpath的ServletContext 属性. 这是Tomcat特有的. 
     * 如果希望JSP引擎在其上运行，则其他servlet引擎可以选择支持此属性. 
     */
    public static final String SERVLET_CLASSPATH = "org.apache.catalina.jsp_classpath";

    /**
     * FIXME
     * servlet的<code>&lt;jsp-file&gt;</code>元素的请求属性.
     * 如果发出请求, 它覆盖<code>request.getServletPath()</code>返回的值选择要执行的JSP页面.
     */
    public static final String JSP_FILE = "org.apache.catalina.jsp_file";


    /**
     * FIXME
     * 类加载器的ServletContext 属性. 这是Tomcat特有的. 
     * 如果希望JSP引擎在其上运行，则其他servlet引擎可以选择支持此属性. 
     */
    //public static final String SERVLET_CLASS_LOADER = "org.apache.tomcat.classloader";
    public static final String SERVLET_CLASS_LOADER = "org.apache.catalina.classloader";

    /**
     * JSP缓冲区的默认大小
     */
    public static final int K = 1024;
    public static final int DEFAULT_BUFFER_SIZE = 8*K;

    /**
     * 标签缓冲区的默认大小.
     */
    public static final int DEFAULT_TAG_BUFFER_SIZE = 512;

    /**
     * 默认标签处理程序池大小.
     */
    public static final int MAX_POOL_SIZE = 5;

    /**
     * 查询参数，JSP引擎只是预先生成servlet, 但不调用它. 
     */
    public static final String PRECOMPILE = "jsp_precompile";

    /**
     * 已编译JSP页面的默认包名.
     */
    public static final String JSP_PACKAGE_NAME = "org.apache.jsp";

    /**
     * 标签文件生成的标签处理程序的默认包名
     */
    public static final String TAG_FILE_PACKAGE_NAME = "org.apache.jsp.tag";

    /**
     * JSP引擎使用的servlet上下文和请求属性. 
     */
    public static final String INC_REQUEST_URI = "javax.servlet.include.request_uri";
    public static final String INC_SERVLET_PATH = "javax.servlet.include.servlet_path";
    public static final String TMP_DIR = "javax.servlet.context.tempdir";
    public static final String FORWARD_SEEN = "javax.servlet.forward.seen";

    // 必须和 org/apache/catalina/Globals.java保持同步
    public static final String ALT_DD_ATTR = "org.apache.catalina.deploy.alt_dd";

    /**
     * DTD的标签库描述符的公共 Id 和资源路径 (缓存副本的) 
     */
    public static final String TAGLIB_DTD_PUBLIC_ID_11 = 
	"-//Sun Microsystems, Inc.//DTD JSP Tag Library 1.1//EN";
    public static final String TAGLIB_DTD_RESOURCE_PATH_11 = 
	"/javax/servlet/jsp/resources/web-jsptaglibrary_1_1.dtd";
    public static final String TAGLIB_DTD_PUBLIC_ID_12 = 
	"-//Sun Microsystems, Inc.//DTD JSP Tag Library 1.2//EN";
    public static final String TAGLIB_DTD_RESOURCE_PATH_12 = 
	"/javax/servlet/jsp/resources/web-jsptaglibrary_1_2.dtd";

    /**
     * DTD的web应用部署描述符的公共 Id 和资源路径 (缓存副本的) 
     */
    public static final String WEBAPP_DTD_PUBLIC_ID_22 = 
	"-//Sun Microsystems, Inc.//DTD Web Application 2.2//EN";
    public static final String WEBAPP_DTD_RESOURCE_PATH_22 = 
	"/javax/servlet/resources/web-app_2_2.dtd";
    public static final String WEBAPP_DTD_PUBLIC_ID_23 = 
	"-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN";
    public static final String WEBAPP_DTD_RESOURCE_PATH_23 = 
	"/javax/servlet/resources/web-app_2_3.dtd";

    /**
     * 缓存的公共ID, 以及它们相关的位置. 通过使用EntityResolver 返回DTD缓存的副本的位置.
     */
    public static final String[] CACHED_DTD_PUBLIC_IDS = {
	TAGLIB_DTD_PUBLIC_ID_11,
	TAGLIB_DTD_PUBLIC_ID_12,
	WEBAPP_DTD_PUBLIC_ID_22,
	WEBAPP_DTD_PUBLIC_ID_23,
    };
    public static final String[] CACHED_DTD_RESOURCE_PATHS = {
	TAGLIB_DTD_RESOURCE_PATH_11,
	TAGLIB_DTD_RESOURCE_PATH_12,
	WEBAPP_DTD_RESOURCE_PATH_22,
	WEBAPP_DTD_RESOURCE_PATH_23,
    };
    
    /**
     * 下载Netscape和IE插件的默认 URL.
     */
    public static final String NS_PLUGIN_URL = 
        "http://java.sun.com/products/plugin/";

    public static final String IE_PLUGIN_URL = 
        "http://java.sun.com/products/plugin/1.2.2/jinstall-1_2_2-win.cab#Version=1,2,2,0";

    /**
     * 用于生成临时变量名的前缀
     */
    public static final String TEMP_VARIABLE_NAME_PREFIX =
        "_jspx_temp";

    /**
     * 替换字符 "\$".
     * XXX 这是一个避免改变EL解释器来识别 "\$"的程序
     */
    public static final char ESC='\u001b';
    public static final String ESCStr="'\\u001b'";
}

