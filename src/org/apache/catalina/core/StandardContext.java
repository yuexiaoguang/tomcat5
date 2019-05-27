package org.apache.catalina.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Stack;
import java.util.TreeMap;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionListener;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.InstanceListener;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Loader;
import org.apache.catalina.Wrapper;
import org.apache.catalina.deploy.ApplicationParameter;
import org.apache.catalina.deploy.ErrorPage;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.MessageDestination;
import org.apache.catalina.deploy.MessageDestinationRef;
import org.apache.catalina.deploy.NamingResources;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.TldConfig;
import org.apache.catalina.util.CharsetMapper;
import org.apache.catalina.util.ExtensionValidator;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.URLEncoder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.modeler.Registry;
import org.apache.naming.ContextBindings;
import org.apache.naming.resources.BaseDirContext;
import org.apache.naming.resources.DirContextURLStreamHandler;
import org.apache.naming.resources.FileDirContext;
import org.apache.naming.resources.ProxyDirContext;
import org.apache.naming.resources.WARDirContext;
import org.apache.tomcat.util.compat.JdkCompat;

/**
 * <b>Context</b>接口的标准实现类. 
 * 每个子容器必须是一个Wrapper实现类，用于处理指向特定servlet的请求.
 */
public class StandardContext extends ContainerBase
    implements Context, Serializable, NotificationEmitter {
    private static transient Log log = LogFactory.getLog(StandardContext.class);

    // ----------------------------------------------------------- Constructors

    public StandardContext() {
        super();
        pipeline.setBasic(new StandardContextValve());
        broadcaster = new NotificationBroadcasterSupport();
    }


    // ----------------------------------------------------- Class Variables


    /**
     * 实现类描述问题.
     */
    private static final String info =
        "org.apache.catalina.core.StandardContext/1.0";


    /**
     * JDK 兼容支持
     */
    private static final JdkCompat jdkCompat = JdkCompat.getJdkCompat();


    /**
     * 包含安全字符集的数组.
     */
    protected static URLEncoder urlEncoder;


    /**
     * GMT 时区 - 所有http日期都是格林尼治时间
     */
    static {
        urlEncoder = new URLEncoder();
        urlEncoder.addSafeCharacter('~');
        urlEncoder.addSafeCharacter('-');
        urlEncoder.addSafeCharacter('_');
        urlEncoder.addSafeCharacter('.');
        urlEncoder.addSafeCharacter('*');
        urlEncoder.addSafeCharacter('/');
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 备用部署描述符名称.
     */
    private String altDDName = null;


    /**
     * 关联主机名
     */
    private String hostName;


    /**
     * 这个上下文的antiJARLocking 标志
     */
    private boolean antiJARLocking = false;

    
    /**
     * 这个上下文的antiResourceLocking 标志
     */
    private boolean antiResourceLocking = false;

    
    /**
     * 配置的应用监听器类名集合, 使用它们在web.xml文件中定义的位置排序.
     */
    private String applicationListeners[] = new String[0];


    /**
     * 实例化的应用程序事件监听器对象集</code>.
     */
    private transient Object applicationEventListenersObjects[] = new Object[0];


    /**
     * 实例化的应用程序生命周期监听器对象集合.</code>.
     */
    private transient Object applicationLifecycleListenersObjects[] = new Object[0];


    /**
     * 定义的应用程序参数集合.
     */
    private ApplicationParameter applicationParameters[] = new ApplicationParameter[0];


    /**
     * 应用程序可用标志.
     */
    private boolean available = false;
    
    /**
     * 发送j2ee 通知的broadcaster. 
     */
    private NotificationBroadcasterSupport broadcaster = null;
    
    /**
     * 字符集映射器的区域设置.
     */
    private transient CharsetMapper charsetMapper = null;


    /**
     * 被创建的CharsetMapper类的类名.
     */
    private String charsetMapperClass = "org.apache.catalina.util.CharsetMapper";


    /**
     * 保存上下文信息的文件的路径.
     */
    private String configFile = null;


    /**
     * 此上下文的“正确配置”标志.
     */
    private boolean configured = false;


    /**
     * 安全约束.
     */
    private SecurityConstraint constraints[] = new SecurityConstraint[0];


    /**
     * 关联的ServletContext实现类.
     */
    private transient ApplicationContext context = null;


    /**
     * 使用的编译器路径.
     */
    private String compilerClasspath = null;


    /**
     * 应该尝试使用cookie进行会话ID通信吗?
     */
    private boolean cookies = true;


    /**
     * 是否应该允许<code>ServletContext.getContext()</code>方法
     * 访问此服务器中其他Web应用程序的上下文?
     */
    private boolean crossContext = false;

    
    /**
     * 编码的路径
     */
    private String encodedPath = null;
    

    /**
     * 用于配置ClassLoader的"遵循标准委托模型"标志.
     */
    private boolean delegate = false;


     /**
     * 此Web应用程序的显示名称
     */
    private String displayName = null;


    /** 
     * 覆盖默认上下文XML位置.
     */
    private String defaultContextXml;


    /** 
     * 覆盖默认的Web XML位置.
     */
    private String defaultWebXml;


    /**
     * 此Web应用程序的发布标志
     */
    private boolean distributable = false;


    /**
     * 此Web应用程序的文档根目录
     */
    private String docBase = null;


    /**
     * 此Web应用程序的异常页, Java异常类名的完全限定名作为key.
     */
    private HashMap exceptionPages = new HashMap();


    /**
     * 初始化的配置的过滤器集合 (以及关联的过滤器实例), 过滤器名作为key.
     */
    private HashMap filterConfigs = new HashMap();


    /**
     * 此应用程序的过滤器集合, 过滤器名作为key.
     */
    private HashMap filterDefs = new HashMap();


    /**
     * 此应用程序的过滤器映射集合, 按照它们在部署描述符中定义的顺序排序.
     */
    private FilterMap filterMaps[] = new FilterMap[0];


    /**
     * InstanceListener的类名集合，将被添加到每个使用<code>createWrapper()</code>新创建的Wrapper.
     */
    private String instanceListeners[] = new String[0];


    /**
     * 此Web应用程序的登录配置描述
     */
    private LoginConfig loginConfig = null;


    /**
     * 这个上下文关联的mapper.
     */
    private org.apache.tomcat.util.http.mapper.Mapper mapper = 
        new org.apache.tomcat.util.http.mapper.Mapper();


    /**
     * 此Web应用程序的命名上下文监听器
     */
    private transient NamingContextListener namingContextListener = null;


    /**
     * 此Web应用程序的命名资源
     */
    private NamingResources namingResources = null;


    /**
     * 此Web应用程序的消息目标.
     */
    private HashMap messageDestinations = new HashMap();


    /**
     * 此Web应用程序的MIME映射, 使用扩展作为key.
     */
    private HashMap mimeMappings = new HashMap();


     /**
      * 特殊情况: 状态200的错误页面
      */
     private ErrorPage okErrorPage = null;


    /**
     * 此Web应用程序的上下文初始化参数,使用名称作为key
     */
    private HashMap parameters = new HashMap();


    /**
     * 请求处理暂停标志 (在重载时)
     */
    private boolean paused = false;


    /**
     * Web应用程序部署描述符版本的DTD的公共标识符. 
     * 这是用来支持轻松验证规则在处理2.2版的web.xml文件.
     */
    private String publicId = null;


    /**
     * 应用的重新加载标志.
     */
    private boolean reloadable = false;


    /**
     * 是否解压 WAR.
     */
    private boolean unpackWAR = true;


    /**
     * DefaultContext覆盖标志.
     */
    private boolean override = false;


    /**
     * 此Web应用程序的特权标志.
     */
    private boolean privileged = false;


    /**
     * 下一个调用<code>addWelcomeFile()</code>方法导致任何已经存在的欢迎文件的替换? 
     * 这将在处理Web应用程序的部署描述符之前设置, 以便应用程序指定选择<strong>replace</strong>,而不是附加到全局描述符中定义的那些
     */
    private boolean replaceWelcomeFiles = false;


    /**
     * 此应用程序的安全角色映射, 使用角色名称作为key(在应用程序中使用).
     */
    private HashMap roleMappings = new HashMap();


    /**
     * 此应用程序的安全角色, 使用角色名称作为key.
     */
    private String securityRoles[] = new String[0];


    /**
     * 此Web应用程序的servlet映射, 使用匹配表达式作为key
     */
    private HashMap servletMappings = new HashMap();


    /**
     * 会话超时时间(in minutes)
     */
    private int sessionTimeout = 30;

    /**
     * 通知序列号.
     */
    private long sequenceNumber = 0;
    
    /**
     * 此Web应用程序的状态代码错误页, 使用HTTP状态码作为key(Integer类型).
     */
    private HashMap statusPages = new HashMap();


    /**
     * 设置标记为true 将导致system.out 和system.err 在执行servlet时要重定向到logger.
     */
    private boolean swallowOutput = false;


    /**
     * 此Web应用程序的JSP标记库, 使用URI作为key
     */
    private HashMap taglibs = new HashMap();


    /**
     * 此应用程序的监视资源.
     */
    private String watchedResources[] = new String[0];


    /**
     * 此应用程序的欢迎文件
     */
    private String welcomeFiles[] = new String[0];


    /**
     * LifecycleListener的类名集合, 将被添加到新创建的Wrapper.
     */
    private String wrapperLifecycles[] = new String[0];


    /**
     * ContainerListener的类名集合, 将被添加到新创建的Wrapper.
     */
    private String wrapperListeners[] = new String[0];


    /**
     * 这个上下文的工作目录的路径名 (相对于服务器的home目录，如果不是绝对的).
     */
    private String workDir = null;


    /**
     * 使用的Wrapper实现类的类名.
     */
    private String wrapperClassName = StandardWrapper.class.getName();
    private Class wrapperClass = null;


    /**
     * JNDI使用标记.
     */
    private boolean useNaming = true;


    /**
     * Filesystem 基于标记.
     */
    private boolean filesystemBased = false;


    /**
     * 关联的命名上下文名称
     */
    private String namingContextName = null;


    /**
     * 是否允许缓存
     */
    private boolean cachingAllowed = true;


    /**
     * 大小写敏感性.
     */
    protected boolean caseSensitive = true;


    /**
     * 是否允许链接.
     */
    protected boolean allowLinking = false;


    /**
     * 缓存最大大小 KB.
     */
    protected int cacheMaxSize = 10240; // 10 MB


    /**
     * 缓存 TTL ms.
     */
    protected int cacheTTL = 5000;


    private boolean lazy=true;

    /**
     * 非代理资源.
     */
    private transient DirContext webappResources = null;

    private long startupTime;
    private long startTime;
    private long tldScanTime;

    /** 
     * 引擎名称. 如果是null, 使用域名.
     */ 
    private String engineName = null;
    private String j2EEApplication="none";
    private String j2EEServer="none";


    /**
     * 用于打开/关闭XML验证的属性值
     */
     private boolean webXmlValidation = false;


    /**
     * 用于打开/关闭XML名称空间验证的属性值
     */
     private boolean webXmlNamespaceAware = false;

    /**
     * 用于打开/关闭TLD处理的属性值
     */
    private boolean processTlds = true;

    /**
     * 用于打开/关闭XML验证的属性值
     */
     private boolean tldValidation = false;


    /**
     * 用于打开/关闭TLD名称空间验证的属性值
     */
     private boolean tldNamespaceAware = false;


    /**
     * 是否应该保存配置.
     */
    private boolean saveConfig = true;


    // ----------------------------------------------------- Context Properties


    public String getEncodedPath() {
        return encodedPath;
    }


    public void setName( String name ) {
        super.setName( name );
        encodedPath = urlEncoder.encode(name);
    }


    /**
     * 是否允许缓存 ?
     */
    public boolean isCachingAllowed() {
        return cachingAllowed;
    }


    /**
     * 是否允许缓存.
     */
    public void setCachingAllowed(boolean cachingAllowed) {
        this.cachingAllowed = cachingAllowed;
    }


    /**
     * 大小写敏感性.
     */
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }


    /**
     * 大小写敏感 ?
     */
    public boolean isCaseSensitive() {
        return caseSensitive;
    }


    /**
     * 是否允许链接.
     */
    public void setAllowLinking(boolean allowLinking) {
        this.allowLinking = allowLinking;
    }


    /**
     * 是否允许链接.
     */
    public boolean isAllowLinking() {
        return allowLinking;
    }


    /**
     * 设置缓存 TTL.
     */
    public void setCacheTTL(int cacheTTL) {
        this.cacheTTL = cacheTTL;
    }


    /**
     * 获取缓存 TTL.
     */
    public int getCacheTTL() {
        return cacheTTL;
    }


    /**
     * 缓存最大大小 KB.
     */
    public int getCacheMaxSize() {
        return cacheMaxSize;
    }


    /**
     * 缓存最大大小 KB.
     */
    public void setCacheMaxSize(int cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }


    /**
     * 用于配置ClassLoader的"遵循标准委托模型"标志.
     */
    public boolean getDelegate() {
        return (this.delegate);
    }


    /**
     * 用于配置ClassLoader的"遵循标准委托模型"标志.
     *
     * @param delegate The new flag
     */
    public void setDelegate(boolean delegate) {
        boolean oldDelegate = this.delegate;
        this.delegate = delegate;
        support.firePropertyChange("delegate", new Boolean(oldDelegate),
                                   new Boolean(this.delegate));
    }


    /**
     * 如果使用内部命名支持，则返回true.
     */
    public boolean isUseNaming() {
        return (useNaming);
    }


    /**
     * 启用或禁用命名.
     */
    public void setUseNaming(boolean useNaming) {
        this.useNaming = useNaming;
    }


    /**
     * 如果与此上下文关联的资源是基于文件系统的，则返回true.
     */
    public boolean isFilesystemBased() {
        return (filesystemBased);
    }


    /**
     * 返回初始化的应用程序事件侦听器对象的集合, 按照在Web应用程序部署描述符中指定的顺序.
     *
     * @exception IllegalStateException 如果在应用程序启动之前, 或者在它被停止之后调用此方法
     */
    public Object[] getApplicationEventListeners() {
        return (applicationEventListenersObjects);
    }


    /**
     * 存储初始化的应用程序事件监听器对象集, 按照在Web应用程序部署描述符中指定的顺序.
     *
     * @param listeners 实例化的监听器对象集.
     */
    public void setApplicationEventListeners(Object listeners[]) {
        applicationEventListenersObjects = listeners;
    }


    /**
     * 返回初始化的应用程序生命周期监听器对象集, 按照在Web应用程序部署描述符中指定的顺序.
     *
     * @exception IllegalStateException 如果在应用程序启动之前, 或者在它被停止之后调用此方法
     */
    public Object[] getApplicationLifecycleListeners() {
        return (applicationLifecycleListenersObjects);
    }


    /**
     * 返回初始化的应用程序生命周期监听器对象集, 按照在Web应用程序部署描述符中指定的顺序.
     *
     * @param listeners 实例化的监听器对象集.
     */
    public void setApplicationLifecycleListeners(Object listeners[]) {
        applicationLifecycleListenersObjects = listeners;
    }


    /**
     * 返回这个上下文的antiJARLocking 标记.
     */
    public boolean getAntiJARLocking() {
        return (this.antiJARLocking);
    }


    /**
     * 返回这个上下文的antiResourceLocking 标记.
     */
    public boolean getAntiResourceLocking() {
        return (this.antiResourceLocking);
    }


    /**
     * 设置这个上下文的antiJARLocking 标记.
     *
     * @param antiJARLocking The new flag value
     */
    public void setAntiJARLocking(boolean antiJARLocking) {
        boolean oldAntiJARLocking = this.antiJARLocking;
        this.antiJARLocking = antiJARLocking;
        support.firePropertyChange("antiJARLocking",
                                   new Boolean(oldAntiJARLocking),
                                   new Boolean(this.antiJARLocking));
    }


    /**
     * 设置这个上下文的antiResourceLocking 标记.
     *
     * @param antiResourceLocking The new flag value
     */
    public void setAntiResourceLocking(boolean antiResourceLocking) {
        boolean oldAntiResourceLocking = this.antiResourceLocking;
        this.antiResourceLocking = antiResourceLocking;
        support.firePropertyChange("antiResourceLocking",
                                   new Boolean(oldAntiResourceLocking),
                                   new Boolean(this.antiResourceLocking));
    }


    /**
     * 返回此上下文的应用程序可用标志.
     */
    public boolean getAvailable() {
        return (this.available);
    }


    /**
     * 设置此上下文的应用程序可用标志
     *
     * @param available 应用程序可用标志
     */
    public void setAvailable(boolean available) {
        boolean oldAvailable = this.available;
        this.available = available;
        support.firePropertyChange("available",
                                   new Boolean(oldAvailable),
                                   new Boolean(this.available));
    }


    /**
     * 将该区域设置为此上下文的字符集映射器.
     */
    public CharsetMapper getCharsetMapper() {

        // 第一次创建映射器
        if (this.charsetMapper == null) {
            try {
                Class clazz = Class.forName(charsetMapperClass);
                this.charsetMapper =
                  (CharsetMapper) clazz.newInstance();
            } catch (Throwable t) {
                this.charsetMapper = new CharsetMapper();
            }
        }
        return (this.charsetMapper);
    }


    /**
     * 将区域设置设置为此上下文的字符集映射器.
     *
     * @param mapper The new mapper
     */
    public void setCharsetMapper(CharsetMapper mapper) {
        CharsetMapper oldCharsetMapper = this.charsetMapper;
        this.charsetMapper = mapper;
        if( mapper != null )
            this.charsetMapperClass= mapper.getClass().getName();
        support.firePropertyChange("charsetMapper", oldCharsetMapper,
                                   this.charsetMapper);
    }

    /**
     * 返回文件的路径保存上下文信息.
     */
    public String getConfigFile() {
        return (this.configFile);
    }


    /**
     * 设置文件的路径以保存此上下文信息.
     *
     * @param configFile 保存上下文信息的文件的路径.
     */
    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }


    /**
     * 此上下文的“正确配置”标志.
     */
    public boolean getConfigured() {
        return (this.configured);
    }


    /**
     * 此上下文的“正确配置”标志. 这可以通过启动监听器设置为false，该监听器检测致命的配置错误以避免应用程序可用.
     *
     * @param configured 正确配置的标志
     */
    public void setConfigured(boolean configured) {
        boolean oldConfigured = this.configured;
        this.configured = configured;
        support.firePropertyChange("configured",
                                   new Boolean(oldConfigured),
                                   new Boolean(this.configured));
    }


    /**
     * 返回"为会话ID使用cookie"标志.
     */
    public boolean getCookies() {
        return (this.cookies);
    }


    /**
     * 设置"为会话ID使用cookie"标志.
     *
     * @param cookies The new flag
     */
    public void setCookies(boolean cookies) {
        boolean oldCookies = this.cookies;
        this.cookies = cookies;
        support.firePropertyChange("cookies",
                                   new Boolean(oldCookies),
                                   new Boolean(this.cookies));
    }


    /**
     * 返回 "允许交叉servlet上下文"标志.
     */
    public boolean getCrossContext() {
        return (this.crossContext);
    }


    /**
     * 设置"允许交叉servlet上下文"标志.
     *
     * @param crossContext 跨上下文标志
     */
    public void setCrossContext(boolean crossContext) {
        boolean oldCrossContext = this.crossContext;
        this.crossContext = crossContext;
        support.firePropertyChange("crossContext",
                                   new Boolean(oldCrossContext),
                                   new Boolean(this.crossContext));
    }

    public String getDefaultContextXml() {
        return defaultContextXml;
    }

    /** 
     * 设置将使用的默认上下文XML的位置.
     * 如果不是绝对的, 这将是相对于engine的基本目录( 默认为catalina.base系统属性 ).
     *
     * @param defaultContextXml 默认的Web xml
     */
    public void setDefaultContextXml(String defaultContextXml) {
        this.defaultContextXml = defaultContextXml;
    }

    public String getDefaultWebXml() {
        return defaultWebXml;
    }

    /** 
     * 设置要使用的默认Web XML的位置.
     * 如果不是绝对的, 这将是相对于engine的基本目录( 默认为catalina.base系统属性 ).
     *
     * @param defaultWebXml 默认的Web xml
     */
    public void setDefaultWebXml(String defaultWebXml) {
        this.defaultWebXml = defaultWebXml;
    }

    /**
     * 获取启动此上下文所需的时间（以毫秒为单位）.
     *
     * @return Time （以毫秒为单位）启动这个上下文.
     */
    public long getStartupTime() {
        return startupTime;
    }

    public void setStartupTime(long startupTime) {
        this.startupTime = startupTime;
    }

    public long getTldScanTime() {
        return tldScanTime;
    }

    public void setTldScanTime(long tldScanTime) {
        this.tldScanTime = tldScanTime;
    }

    /**
     * 返回此Web应用程序的显示名称.
     */
    public String getDisplayName() {
        return (this.displayName);
    }


    /**
     * 返回备用部署描述符名称.
     */
    public String getAltDDName(){
        return altDDName;
    }


    /**
     * 设置一个备用部署描述符名称.
     */
    public void setAltDDName(String altDDName) {
        this.altDDName = altDDName;
        if (context != null) {
            context.setAttribute(Globals.ALT_DD_ATTR,altDDName);
        }
    }


    /**
     * 返回编译器的路径.
     */
    public String getCompilerClasspath(){
        return compilerClasspath;
    }


    /**
     * 设置编译器的路径.
     */
    public void setCompilerClasspath(String compilerClasspath) {
        this.compilerClasspath = compilerClasspath;
    }


    /**
     * 设置此Web应用程序的显示名称.
     *
     * @param displayName 显示名称
     */
    public void setDisplayName(String displayName) {
        String oldDisplayName = this.displayName;
        this.displayName = displayName;
        support.firePropertyChange("displayName", oldDisplayName,
                                   this.displayName);
    }


    /**
     * 返回该Web应用程序的发布标志.
     */
    public boolean getDistributable() {
        return (this.distributable);
    }

    /**
     * 设置该Web应用程序的发布标志.
     *
     * @param distributable 发布标志
     */
    public void setDistributable(boolean distributable) {
        boolean oldDistributable = this.distributable;
        this.distributable = distributable;
        support.firePropertyChange("distributable",
                                   new Boolean(oldDistributable),
                                   new Boolean(this.distributable));

        // Bugzilla 32866
        if(getManager() != null) {
            if(log.isDebugEnabled()) {
                log.debug("Propagating distributable=" + distributable
                          + " to manager");
            }
            getManager().setDistributable(distributable);
        }
    }


    /**
     * 返回此上下文的文档根目录.  这是一个绝对路径名, 相对路径, 或一个 URL.
     */
    public String getDocBase() {
        return (this.docBase);
    }


    /**
     * 设置此上下文的文档根目录. 这是一个绝对路径名, 相对路径, 或一个 URL.
     *
     * @param docBase 文档根目录
     */
    public void setDocBase(String docBase) {
        this.docBase = docBase;
    }

    // experimental
    public boolean isLazy() {
        return lazy;
    }

    public void setLazy(boolean lazy) {
        this.lazy = lazy;
    }


    /**
     * 返回描述信息和相应版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }

    public String getEngineName() {
        if( engineName != null ) return engineName;
        return domain;
    }

    public void setEngineName(String engineName) {
        this.engineName = engineName;
    }

    public String getJ2EEApplication() {
        return j2EEApplication;
    }

    public void setJ2EEApplication(String j2EEApplication) {
        this.j2EEApplication = j2EEApplication;
    }

    public String getJ2EEServer() {
        return j2EEServer;
    }

    public void setJ2EEServer(String j2EEServer) {
        this.j2EEServer = j2EEServer;
    }


    /**
     * 设置与此上下文关联的Loader.
     *
     * @param loader The newly associated loader
     */
    public synchronized void setLoader(Loader loader) {
        super.setLoader(loader);
    }


    /**
     * 返回此Web应用程序的登录配置描述符.
     */
    public LoginConfig getLoginConfig() {
        return (this.loginConfig);
    }


    /**
     * 设置此Web应用程序的登录配置描述符.
     *
     * @param config 登录配置
     */
    public void setLoginConfig(LoginConfig config) {

        // Validate the incoming property value
        if (config == null)
            throw new IllegalArgumentException(sm.getString("standardContext.loginConfig.required"));
        String loginPage = config.getLoginPage();
        if ((loginPage != null) && !loginPage.startsWith("/")) {
            if (isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("standardContext.loginConfig.loginWarning", loginPage));
                config.setLoginPage("/" + loginPage);
            } else {
                throw new IllegalArgumentException(sm.getString("standardContext.loginConfig.loginPage", loginPage));
            }
        }
        String errorPage = config.getErrorPage();
        if ((errorPage != null) && !errorPage.startsWith("/")) {
            if (isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("standardContext.loginConfig.errorWarning", errorPage));
                config.setErrorPage("/" + errorPage);
            } else {
                throw new IllegalArgumentException(sm.getString("standardContext.loginConfig.errorPage", errorPage));
            }
        }

        // 处理属性设置更改
        LoginConfig oldLoginConfig = this.loginConfig;
        this.loginConfig = config;
        support.firePropertyChange("loginConfig",
                                   oldLoginConfig, this.loginConfig);
    }


    /**
     * 获取与上下文关联的mapper.
     */
    public org.apache.tomcat.util.http.mapper.Mapper getMapper() {
        return (mapper);
    }


    /**
     * 返回与此Web应用程序相关联的命名资源.
     */
    public NamingResources getNamingResources() {
        if (namingResources == null) {
            setNamingResources(new NamingResources());
        }
        return (namingResources);
    }


    /**
     * 设置与此Web应用程序相关联的命名资源.
     *
     * @param namingResources 命名资源
     */
    public void setNamingResources(NamingResources namingResources) {
        // 处理属性设置更改
        NamingResources oldNamingResources = this.namingResources;
        this.namingResources = namingResources;
        namingResources.setContainer(this);
        support.firePropertyChange("namingResources",
                                   oldNamingResources, this.namingResources);
    }


    /**
     * 返回此上下文的上下文路径.
     */
    public String getPath() {
        return (getName());
    }

    
    /**
     * 设置此上下文的上下文路径.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>:  上下文路径作为上下文的 "name", 因为它必须是唯一的.
     *
     * @param path 上下文路径
     */
    public void setPath(String path) {
        // XXX Use host in name
        setName(RequestUtil.URLDecode(path));
    }


    /**
     * 返回当前正在解析的部署描述符DTD的公共标识符.
     */
    public String getPublicId() {
        return (this.publicId);
    }


    /**
     * 设置当前正在解析的部署描述符DTD的公共标识符.
     *
     * @param publicId The public identifier
     */
    public void setPublicId(String publicId) {
        if (log.isDebugEnabled())
            log.debug("Setting deployment descriptor public ID to '" +
                publicId + "'");

        String oldPublicId = this.publicId;
        this.publicId = publicId;
        support.firePropertyChange("publicId", oldPublicId, publicId);
    }


    /**
     * 返回这个Web应用的重新加载标志.
     */
    public boolean getReloadable() {
        return (this.reloadable);
    }


    /**
     * 返回这个Web应用的DefaultContext 覆盖标志.
     */
    public boolean getOverride() {
        return (this.override);
    }


    /**
     * 返回此Web应用程序的特权标志.
     */
    public boolean getPrivileged() {
        return (this.privileged);
    }


    /**
     * 设置此Web应用程序的特权标志.
     *
     * @param privileged 特权标志
     */
    public void setPrivileged(boolean privileged) {
        boolean oldPrivileged = this.privileged;
        this.privileged = privileged;
        support.firePropertyChange("privileged",
                                   new Boolean(oldPrivileged),
                                   new Boolean(this.privileged));
    }


    /**
     * 设置此应用的重新加载标志.
     *
     * @param reloadable The new reloadable flag
     */
    public void setReloadable(boolean reloadable) {
        boolean oldReloadable = this.reloadable;
        this.reloadable = reloadable;
        support.firePropertyChange("reloadable",
                                   new Boolean(oldReloadable),
                                   new Boolean(this.reloadable));
    }


    /**
     * 设置这个Web应用的DefaultContext 覆盖标志.
     *
     * @param override The new override flag
     */
    public void setOverride(boolean override) {
        boolean oldOverride = this.override;
        this.override = override;
        support.firePropertyChange("override",
                                   new Boolean(oldOverride),
                                   new Boolean(this.override));
    }


    /**
     * 返回"欢迎文件替换" 属性.
     */
    public boolean isReplaceWelcomeFiles() {
        return (this.replaceWelcomeFiles);
    }


    /**
     * 设置"欢迎文件替换" 属性.
     *
     * @param replaceWelcomeFiles 属性值
     */
    public void setReplaceWelcomeFiles(boolean replaceWelcomeFiles) {
        boolean oldReplaceWelcomeFiles = this.replaceWelcomeFiles;
        this.replaceWelcomeFiles = replaceWelcomeFiles;
        support.firePropertyChange("replaceWelcomeFiles",
                                   new Boolean(oldReplaceWelcomeFiles),
                                   new Boolean(this.replaceWelcomeFiles));
    }


    /**
     * 返回此上下文为外观的servlet上下文.
     */
    public ServletContext getServletContext() {
        if (context == null) {
            context = new ApplicationContext(getBasePath(), this);
            if (altDDName != null)
                context.setAttribute(Globals.ALT_DD_ATTR,altDDName);
        }
        return (context.getFacade());
    }


    /**
     * 返回默认会话超时时间(in minutes)
     */
    public int getSessionTimeout() {
        return (this.sessionTimeout);
    }


    /**
     * 设置默认会话超时时间(in minutes)
     *
     * @param timeout 默认会话超时时间
     */
    public void setSessionTimeout(int timeout) {

        int oldSessionTimeout = this.sessionTimeout;
        /*
         * SRV.13.4 ("Deployment Descriptor"):
         * 如果超时时间是 0 或负值, 容器确保会话的默认行为永远不会超时.
         */
        this.sessionTimeout = (timeout == 0) ? -1 : timeout;
        support.firePropertyChange("sessionTimeout",
                                   new Integer(oldSessionTimeout),
                                   new Integer(this.sessionTimeout));
    }


    /**
     * 返回swallowOutput 标志的值.
     */
    public boolean getSwallowOutput() {
        return (this.swallowOutput);
    }


    /**
     * 设置swallowOutput 标志的值. 如果设置为 true, 将导致system.out 和system.err 在执行servlet时要重定向到logger.
     *
     * @param swallowOutput The new value
     */
    public void setSwallowOutput(boolean swallowOutput) {
        boolean oldSwallowOutput = this.swallowOutput;
        this.swallowOutput = swallowOutput;
        support.firePropertyChange("swallowOutput",
                                   new Boolean(oldSwallowOutput),
                                   new Boolean(this.swallowOutput));
    }


    /**
     * 是否解压 WAR 标志.
     */
    public boolean getUnpackWAR() {
        return (unpackWAR);
    }


    /**
     * 是否解压 WAR 标志.
     */
    public void setUnpackWAR(boolean unpackWAR) {
        this.unpackWAR = unpackWAR;
    }

    /**
     * 返回Wrapper实现类的类名， 用于在这个Context中注册servlet.
     */
    public String getWrapperClass() {
        return (this.wrapperClassName);
    }


    /**
     * 设置Wrapper实现类的类名， 用于在这个Context中注册servlet.
     *
     * @param wrapperClassName 包装器类名
     *
     * @throws IllegalArgumentException 如果指定的包装器类不能被发现或不是StandardWrapper子类
     */
    public void setWrapperClass(String wrapperClassName) {

        this.wrapperClassName = wrapperClassName;

        try {
            wrapperClass = Class.forName(wrapperClassName);         
            if (!StandardWrapper.class.isAssignableFrom(wrapperClass)) {
                throw new IllegalArgumentException(
                    sm.getString("standardContext.invalidWrapperClass",
                                 wrapperClassName));
            }
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalArgumentException(cnfe.getMessage());
        }
    }


    /**
     * 设置资源的 DirContext对象
     *
     * @param resources The newly associated DirContext
     */
    public synchronized void setResources(DirContext resources) {

        if (started) {
            throw new IllegalStateException
                (sm.getString("standardContext.resources.started"));
        }
        DirContext oldResources = this.webappResources;
        if (oldResources == resources)
            return;

        if (resources instanceof BaseDirContext) {
            ((BaseDirContext) resources).setCached(isCachingAllowed());
            ((BaseDirContext) resources).setCacheTTL(getCacheTTL());
            ((BaseDirContext) resources).setCacheMaxSize(getCacheMaxSize());
        }
        if (resources instanceof FileDirContext) {
            filesystemBased = true;
            ((FileDirContext) resources).setCaseSensitive(isCaseSensitive());
            ((FileDirContext) resources).setAllowLinking(isAllowLinking());
        }
        this.webappResources = resources;

        // The proxied resources will be refreshed on start
        this.resources = null;

        support.firePropertyChange("resources", oldResources,
                                   this.webappResources);
    }


    // ------------------------------------------------------ Public Properties


    /**
     * 返回区域设置到字符集映射器类.
     */
    public String getCharsetMapperClass() {
        return (this.charsetMapperClass);
    }


    /**
     * 设置区域到字符集映射器类.
     *
     * @param mapper The new mapper class
     */
    public void setCharsetMapperClass(String mapper) {
        String oldCharsetMapperClass = this.charsetMapperClass;
        this.charsetMapperClass = mapper;
        support.firePropertyChange("charsetMapperClass",
                                   oldCharsetMapperClass,
                                   this.charsetMapperClass);
    }


    /** 获取工作目录的绝对路径.
     *  避免重复.
     * 
     * @return The work path
     */ 
    public String getWorkPath() {
        File workDir = new File(getWorkDir());
        if (!workDir.isAbsolute()) {
            File catalinaHome = engineBase();
            String catalinaHomePath = null;
            try {
                catalinaHomePath = catalinaHome.getCanonicalPath();
                workDir = new File(catalinaHomePath,
                        getWorkDir());
            } catch (IOException e) {
                log.warn("Exception obtaining work path for " + getPath());
            }
        }
        return workDir.getAbsolutePath();
    }
    
    /**
     * 返回此上下文的工作目录.
     */
    public String getWorkDir() {
        return (this.workDir);
    }


    /**
     * 设置此上下文的工作目录.
     *
     * @param workDir 工作目录
     */
    public void setWorkDir(String workDir) {
        this.workDir = workDir;
        if (started) {
            postWorkDirectory();
        }
    }


    /**
     * 是否保存配置?
     */
    public boolean isSaveConfig() {
        return saveConfig;
    }


    /**
     * 设置是否保存配置.
     */
    public void setSaveConfig(boolean saveConfig) {
        this.saveConfig = saveConfig;
    }


    // -------------------------------------------------------- Context Methods


    /**
     * 添加一个新的监听器类名到配置的监听器集合.
     *
     * @param listener Java class name of a listener class
     */
    public void addApplicationListener(String listener) {
        synchronized (applicationListeners) {
            String results[] =new String[applicationListeners.length + 1];
            for (int i = 0; i < applicationListeners.length; i++) {
                if (listener.equals(applicationListeners[i]))
                    return;
                results[i] = applicationListeners[i];
            }
            results[applicationListeners.length] = listener;
            applicationListeners = results;
        }
        fireContainerEvent("addApplicationListener", listener);

        // FIXME - add instance if already started?
    }


    /**
     * 添加一个新的应用参数
     *
     * @param parameter 应用参数
     */
    public void addApplicationParameter(ApplicationParameter parameter) {
        synchronized (applicationParameters) {
            String newName = parameter.getName();
            for (int i = 0; i < applicationParameters.length; i++) {
                if (newName.equals(applicationParameters[i].getName()) &&
                    !applicationParameters[i].getOverride())
                    return;
            }
            ApplicationParameter results[] =
                new ApplicationParameter[applicationParameters.length + 1];
            System.arraycopy(applicationParameters, 0, results, 0,
                             applicationParameters.length);
            results[applicationParameters.length] = parameter;
            applicationParameters = results;
        }
        fireContainerEvent("addApplicationParameter", parameter);
    }


    /**
     * 添加一个子级Container, 只有当其是Wrapper的实现类时.
     *
     * @param child Child container to be added
     *
     * @exception IllegalArgumentException 如果容器不是Wrapper的实现类
     */
    public void addChild(Container child) {

        // Global JspServlet
        Wrapper oldJspServlet = null;

        if (!(child instanceof Wrapper)) {
            throw new IllegalArgumentException
                (sm.getString("standardContext.notWrapper"));
        }

        Wrapper wrapper = (Wrapper) child;
        boolean isJspServlet = "jsp".equals(child.getName());

        // Allow webapp to override JspServlet inherited from global web.xml.
        if (isJspServlet) {
            oldJspServlet = (Wrapper) findChild("jsp");
            if (oldJspServlet != null) {
                removeChild(oldJspServlet);
            }
        }

        String jspFile = wrapper.getJspFile();
        if ((jspFile != null) && !jspFile.startsWith("/")) {
            if (isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("standardContext.wrapper.warning", 
                                       jspFile));
                wrapper.setJspFile("/" + jspFile);
            } else {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.wrapper.error", jspFile));
            }
        }

        super.addChild(child);

        if (isJspServlet && oldJspServlet != null) {
            /*
             * The webapp-specific JspServlet inherits all the mappings
             * specified in the global web.xml, and may add additional ones.
             */
            String[] jspMappings = oldJspServlet.findMappings();
            for (int i=0; jspMappings!=null && i<jspMappings.length; i++) {
                addServletMapping(jspMappings[i], child.getName());
            }
        }
    }


    /**
     * 为该Web应用程序添加一个安全约束
     */
    public void addConstraint(SecurityConstraint constraint) {

        // 验证所提出的约束
        SecurityCollection collections[] = constraint.findCollections();
        for (int i = 0; i < collections.length; i++) {
            String patterns[] = collections[i].findPatterns();
            for (int j = 0; j < patterns.length; j++) {
                patterns[j] = adjustURLPattern(patterns[j]);
                if (!validateURLPattern(patterns[j]))
                    throw new IllegalArgumentException
                        (sm.getString
                         ("standardContext.securityConstraint.pattern",
                          patterns[j]));
            }
        }

        // 将此约束添加到Web应用程序的集合中
        synchronized (constraints) {
            SecurityConstraint results[] =
                new SecurityConstraint[constraints.length + 1];
            for (int i = 0; i < constraints.length; i++)
                results[i] = constraints[i];
            results[constraints.length] = constraint;
            constraints = results;
        }
    }



    /**
     * 添加一个指定错误或Java异常对应的错误页面.
     *
     * @param errorPage The error page definition to be added
     */
    public void addErrorPage(ErrorPage errorPage) {
        // Validate the input parameters
        if (errorPage == null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.errorPage.required"));
        String location = errorPage.getLocation();
        if ((location != null) && !location.startsWith("/")) {
            if (isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("standardContext.errorPage.warning",
                                 location));
                errorPage.setLocation("/" + location);
            } else {
                throw new IllegalArgumentException
                    (sm.getString("standardContext.errorPage.error",
                                  location));
            }
        }

        // 向内部集合添加指定的错误页面
        String exceptionType = errorPage.getExceptionType();
        if (exceptionType != null) {
            synchronized (exceptionPages) {
                exceptionPages.put(exceptionType, errorPage);
            }
        } else {
            synchronized (statusPages) {
                if (errorPage.getErrorCode() == 200) {
                    this.okErrorPage = errorPage;
                }
                statusPages.put(new Integer(errorPage.getErrorCode()),
                                errorPage);
            }
        }
        fireContainerEvent("addErrorPage", errorPage);
    }


    /**
     * 添加一个过滤器定义
     *
     * @param filterDef The filter definition to be added
     */
    public void addFilterDef(FilterDef filterDef) {
        synchronized (filterDefs) {
            filterDefs.put(filterDef.getFilterName(), filterDef);
        }
        fireContainerEvent("addFilterDef", filterDef);
    }


    /**
     * 添加一个过滤器映射
     *
     * @param filterMap The filter mapping to be added
     *
     * @exception IllegalArgumentException 如果指定的过滤器名不匹配已经存在的过滤器定义，或者过滤器映射是错误的
     */
    public void addFilterMap(FilterMap filterMap) {

        // Validate the proposed filter mapping
        String filterName = filterMap.getFilterName();
        String servletName = filterMap.getServletName();
        String urlPattern = filterMap.getURLPattern();
        if (findFilterDef(filterName) == null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.filterMap.name", filterName));
        if ((servletName == null) && (urlPattern == null))
            throw new IllegalArgumentException
                (sm.getString("standardContext.filterMap.either"));
        if ((servletName != null) && (urlPattern != null))
            throw new IllegalArgumentException
                (sm.getString("standardContext.filterMap.either"));
        // Because filter-pattern is new in 2.3, no need to adjust
        // for 2.2 backwards compatibility
        if ((urlPattern != null) && !validateURLPattern(urlPattern))
            throw new IllegalArgumentException
                (sm.getString("standardContext.filterMap.pattern",
                              urlPattern));

        // Add this filter mapping to our registered set
        synchronized (filterMaps) {
            FilterMap results[] =new FilterMap[filterMaps.length + 1];
            System.arraycopy(filterMaps, 0, results, 0, filterMaps.length);
            results[filterMaps.length] = filterMap;
            filterMaps = results;
        }
        fireContainerEvent("addFilterMap", filterMap);

    }


    /**
     * 添加一个InstanceListener类名到每个Wrapper.
     *
     * @param listener Java class name of an InstanceListener class
     */
    public void addInstanceListener(String listener) {

        synchronized (instanceListeners) {
            String results[] =new String[instanceListeners.length + 1];
            for (int i = 0; i < instanceListeners.length; i++)
                results[i] = instanceListeners[i];
            results[instanceListeners.length] = listener;
            instanceListeners = results;
        }
        fireContainerEvent("addInstanceListener", listener);
    }

    /**
     * 添加给定URL模式为JSP属性组.  这将映射与给定模式相匹配的资源，以便将它们传递给JSP容器.
     * 尽管属性组中还有其它元素, 只关心这里的URL模式. JSP容器将解析其余的.
     *
     * @param pattern 要映射的URL模式
     */
    public void addJspMapping(String pattern) {
        String servletName = findServletMapping("*.jsp");
        if (servletName == null) {
            servletName = "jsp";
        }

        if( findChild(servletName) != null) {
            addServletMapping(pattern, servletName, true);
        } else {
            if(log.isDebugEnabled())
                log.debug("Skiping " + pattern + " , no servlet " + servletName);
        }
    }


    /**
     * 添加到本地编码映射 (see Sec 5.4 of Servlet spec 2.4)
     *
     * @param locale 区域设置以映射编码
     * @param encoding 用于给定区域设置的编码
     */
    public void addLocaleEncodingMappingParameter(String locale, String encoding){
        getCharsetMapper().addCharsetMappingFromDeploymentDescriptor(locale, encoding);
    }


    /**
     * 为这个Web应用程序添加一个消息.
     *
     * @param md New message destination
     */
    public void addMessageDestination(MessageDestination md) {

        synchronized (messageDestinations) {
            messageDestinations.put(md.getName(), md);
        }
        fireContainerEvent("addMessageDestination", md.getName());
    }


    /**
     * 为这个Web应用程序添加一个消息目的地引用.
     *
     * @param mdr New message destination reference
     */
    public void addMessageDestinationRef(MessageDestinationRef mdr) {
        namingResources.addMessageDestinationRef(mdr);
        fireContainerEvent("addMessageDestinationRef", mdr.getName());
    }


    /**
     * 添加一个新的MIME映射, 将指定的扩展名替换为现有映射.
     *
     * @param extension 映射的文件扩展名
     * @param mimeType 相应的MIME类型
     */
    public void addMimeMapping(String extension, String mimeType) {
        synchronized (mimeMappings) {
            mimeMappings.put(extension, mimeType);
        }
        fireContainerEvent("addMimeMapping", extension);
    }


    /**
     * 添加一个新的上下文初始化参数.
     *
     * @param name Name of the new parameter
     * @param value Value of the new  parameter
     *
     * @exception IllegalArgumentException 如果缺少名称或值，或者此上下文初始化参数已注册
     */
    public void addParameter(String name, String value) {
        // Validate the proposed context initialization parameter
        if ((name == null) || (value == null))
            throw new IllegalArgumentException
                (sm.getString("standardContext.parameter.required"));
        if (parameters.get(name) != null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.parameter.duplicate", name));

        // Add this parameter to our defined set
        synchronized (parameters) {
            parameters.put(name, value);
        }
        fireContainerEvent("addParameter", name);
    }


    /**
     * 添加安全角色引用.
     *
     * @param role 应用程序中使用的安全角色
     * @param link 要检查的实际安全角色
     */
    public void addRoleMapping(String role, String link) {
        synchronized (roleMappings) {
            roleMappings.put(role, link);
        }
        fireContainerEvent("addRoleMapping", role);
    }


    /**
     * 添加一个新的安全角色.
     *
     * @param role New security role
     */
    public void addSecurityRole(String role) {
        synchronized (securityRoles) {
            String results[] =new String[securityRoles.length + 1];
            for (int i = 0; i < securityRoles.length; i++)
                results[i] = securityRoles[i];
            results[securityRoles.length] = role;
            securityRoles = results;
        }
        fireContainerEvent("addSecurityRole", role);
    }


    /**
     * 添加一个新的servlet映射, 为指定的模式替换任何现有映射.
     *
     * @param pattern URL 映射模式
     * @param name 要执行的对应servlet的名称
     *
     * @exception IllegalArgumentException 如果该上下文不知道指定的servlet名称
     */
    public void addServletMapping(String pattern, String name) {
        addServletMapping(pattern, name, false);
    }


    /**
     * 添加一个新的servlet映射, 为指定的模式替换任何现有映射.
     *
     * @param pattern URL 映射模式
     * @param name 要执行的对应servlet的名称
     * @param jspWildCard true 如果名称标识JspServlet和模式包含一个通配符; 否则false
     *
     * @exception IllegalArgumentException 如果该上下文不知道指定的servlet名称
     */
    public void addServletMapping(String pattern, String name,
                                  boolean jspWildCard) {
        // Validate the proposed mapping
        if (findChild(name) == null)
            throw new IllegalArgumentException
                (sm.getString("standardContext.servletMap.name", name));
        pattern = adjustURLPattern(RequestUtil.URLDecode(pattern));
        if (!validateURLPattern(pattern))
            throw new IllegalArgumentException
                (sm.getString("standardContext.servletMap.pattern", pattern));

        // Add this mapping to our registered set
        synchronized (servletMappings) {
            String name2 = (String) servletMappings.get(pattern);
            if (name2 != null) {
                // Don't allow more than one servlet on the same pattern
                Wrapper wrapper = (Wrapper) findChild(name2);
                wrapper.removeMapping(pattern);
                mapper.removeWrapper(pattern);
            }
            servletMappings.put(pattern, name);
        }
        Wrapper wrapper = (Wrapper) findChild(name);
        wrapper.addMapping(pattern);

        // Update context mapper
        mapper.addWrapper(pattern, wrapper, jspWildCard);

        fireContainerEvent("addServletMapping", pattern);
    }


    /**
     * 为指定的URI添加JSP标记库
     *
     * @param uri 这个标记库的URI, 相对于 web.xml文件
     * @param location 标记库描述符的位置
     */
    public void addTaglib(String uri, String location) {
        synchronized (taglibs) {
            taglibs.put(uri, location);
        }
        fireContainerEvent("addTaglib", uri);
    }


    /**
     * 向该上下文识别的集合添加新的监视资源.
     *
     * @param name 监视资源文件名称
     */
    public void addWatchedResource(String name) {
        synchronized (watchedResources) {
            String results[] = new String[watchedResources.length + 1];
            for (int i = 0; i < watchedResources.length; i++)
                results[i] = watchedResources[i];
            results[watchedResources.length] = name;
            watchedResources = results;
        }
        fireContainerEvent("addWatchedResource", name);
    }


    /**
     * 向该上下文识别的集合添加一个新的欢迎文件.
     *
     * @param name New welcome file name
     */
    public void addWelcomeFile(String name) {

        synchronized (welcomeFiles) {
            // 完全的应用程序部署描述符中的,欢迎文件替换默认的conf/web.xml文件定义的欢迎文件
            if (replaceWelcomeFiles) {
                welcomeFiles = new String[0];
                setReplaceWelcomeFiles(false);
            }
            String results[] =new String[welcomeFiles.length + 1];
            for (int i = 0; i < welcomeFiles.length; i++)
                results[i] = welcomeFiles[i];
            results[welcomeFiles.length] = name;
            welcomeFiles = results;
        }
        postWelcomeFiles();
        fireContainerEvent("addWelcomeFile", name);
    }


    /**
     * 添加一个LifecycleListener类名，被添加到每个Wrapper.
     *
     * @param listener Java class name of a LifecycleListener class
     */
    public void addWrapperLifecycle(String listener) {
        synchronized (wrapperLifecycles) {
            String results[] =new String[wrapperLifecycles.length + 1];
            for (int i = 0; i < wrapperLifecycles.length; i++)
                results[i] = wrapperLifecycles[i];
            results[wrapperLifecycles.length] = listener;
            wrapperLifecycles = results;
        }
        fireContainerEvent("addWrapperLifecycle", listener);
    }


    /**
     * 添加一个ContainerListener类名，被添加到每个Wrapper.
     *
     * @param listener Java class name of a ContainerListener class
     */
    public void addWrapperListener(String listener) {

        synchronized (wrapperListeners) {
            String results[] =new String[wrapperListeners.length + 1];
            for (int i = 0; i < wrapperListeners.length; i++)
                results[i] = wrapperListeners[i];
            results[wrapperListeners.length] = listener;
            wrapperListeners = results;
        }
        fireContainerEvent("addWrapperListener", listener);
    }


    /**
     * 工厂方法创建并返回一个Wrapper实例.
     * Wrapper的构造方法将被调用, 但没有设置属性.
     */
    public Wrapper createWrapper() {
        Wrapper wrapper = null;
        if (wrapperClass != null) {
            try {
                wrapper = (Wrapper) wrapperClass.newInstance();
            } catch (Throwable t) {
                log.error("createWrapper", t);
                return (null);
            }
        } else {
            wrapper = new StandardWrapper();
        }

        synchronized (instanceListeners) {
            for (int i = 0; i < instanceListeners.length; i++) {
                try {
                    Class clazz = Class.forName(instanceListeners[i]);
                    InstanceListener listener =
                      (InstanceListener) clazz.newInstance();
                    wrapper.addInstanceListener(listener);
                } catch (Throwable t) {
                    log.error("createWrapper", t);
                    return (null);
                }
            }
        }

        synchronized (wrapperLifecycles) {
            for (int i = 0; i < wrapperLifecycles.length; i++) {
                try {
                    Class clazz = Class.forName(wrapperLifecycles[i]);
                    LifecycleListener listener =
                      (LifecycleListener) clazz.newInstance();
                    if (wrapper instanceof Lifecycle)
                        ((Lifecycle) wrapper).addLifecycleListener(listener);
                } catch (Throwable t) {
                    log.error("createWrapper", t);
                    return (null);
                }
            }
        }

        synchronized (wrapperListeners) {
            for (int i = 0; i < wrapperListeners.length; i++) {
                try {
                    Class clazz = Class.forName(wrapperListeners[i]);
                    ContainerListener listener =
                      (ContainerListener) clazz.newInstance();
                    wrapper.addContainerListener(listener);
                } catch (Throwable t) {
                    log.error("createWrapper", t);
                    return (null);
                }
            }
        }
        return (wrapper);
    }


    /**
     * 返回配置的应用监听器类名集合.
     */
    public String[] findApplicationListeners() {
        return (applicationListeners);
    }


    /**
     * 返回应用参数集合.
     */
    public ApplicationParameter[] findApplicationParameters() {
        return (applicationParameters);
    }


    /**
     * 返回此Web应用程序的安全约束.
     * 如果没有，返回一个零长度的数组.
     */
    public SecurityConstraint[] findConstraints() {
        return (constraints);
    }


    /**
     * 返回指定的HTTP错误代码对应的错误页面,
     * 或者返回<code>null</code>.
     *
     * @param errorCode Error code to look up
     */
    public ErrorPage findErrorPage(int errorCode) {
        if (errorCode == 200) {
            return (okErrorPage);
        } else {
            return ((ErrorPage) statusPages.get(new Integer(errorCode)));
        }
    }


    /**
     * 返回指定异常类型对应的错误页面; 或者返回<code>null</code>.
     *
     * @param exceptionType Exception type to look up
     */
    public ErrorPage findErrorPage(String exceptionType) {
        synchronized (exceptionPages) {
            return ((ErrorPage) exceptionPages.get(exceptionType));
        }
    }


    /**
     * 返回定义的所有错误页面集合，包括指定错误码和异常类型的.
     */
    public ErrorPage[] findErrorPages() {

        synchronized(exceptionPages) {
            synchronized(statusPages) {
                ErrorPage results1[] = new ErrorPage[exceptionPages.size()];
                results1 =
                    (ErrorPage[]) exceptionPages.values().toArray(results1);
                ErrorPage results2[] = new ErrorPage[statusPages.size()];
                results2 =
                    (ErrorPage[]) statusPages.values().toArray(results2);
                ErrorPage results[] =
                    new ErrorPage[results1.length + results2.length];
                for (int i = 0; i < results1.length; i++)
                    results[i] = results1[i];
                for (int i = results1.length; i < results.length; i++)
                    results[i] = results2[i - results1.length];
                return (results);
            }
        }
    }


    /**
     * 返回指定名称对应的过滤器定义; 或者<code>null</code>.
     *
     * @param filterName Filter name to look up
     */
    public FilterDef findFilterDef(String filterName) {
        synchronized (filterDefs) {
            return ((FilterDef) filterDefs.get(filterName));
        }
    }


    /**
     * 返回定义的过滤器集合.
     */
    public FilterDef[] findFilterDefs() {
        synchronized (filterDefs) {
            FilterDef results[] = new FilterDef[filterDefs.size()];
            return ((FilterDef[]) filterDefs.values().toArray(results));
        }
    }


    /**
     * 返回过滤器映射集合.
     */
    public FilterMap[] findFilterMaps() {
        return (filterMaps);
    }


    /**
     * 返回InstanceListener类名集合，将被添加到新创建的Wrapper.
     */
    public String[] findInstanceListeners() {
        return (instanceListeners);
    }


    /**
     * FIXME: Fooling introspection ...
     */
    public Context findMappingObject() {
        return (Context) getMappingObject();
    }
    
    
    /**
     * 返回指定名称的消息目标; 或者<code>null</code>.
     *
     * @param name 消息目标的名称
     */
    public MessageDestination findMessageDestination(String name) {
        synchronized (messageDestinations) {
            return ((MessageDestination) messageDestinations.get(name));
        }
    }


    /**
     * 返回这个应用的所有消息目标. 如果没有，返回零长度数组.
     */
    public MessageDestination[] findMessageDestinations() {
        synchronized (messageDestinations) {
            MessageDestination results[] =
                new MessageDestination[messageDestinations.size()];
            return ((MessageDestination[])
                    messageDestinations.values().toArray(results));
        }
    }


    /**
     * 返回指定名称的消息目标引用; 或者<code>null</code>.
     *
     * @param name 消息目标引用的名称
     */
    public MessageDestinationRef findMessageDestinationRef(String name) {
        return namingResources.findMessageDestinationRef(name);
    }


    /**
     * 返回这个应用的所有消息目标引用. 如果没有，返回零长度数组.
     */
    public MessageDestinationRef[] findMessageDestinationRefs() {
        return namingResources.findMessageDestinationRefs();
    }


    /**
     * 返回指定的扩展名映射到的MIME类型;或者返回<code>null</code>.
     *
     * @param extension 映射到MIME 类型的扩展名
     */
    public String findMimeMapping(String extension) {
        return ((String) mimeMappings.get(extension));
    }


    /**
     * 返回定义MIME映射的扩展名. 
     * 如果没有，则返回零长度数组.
     */
    public String[] findMimeMappings() {
        synchronized (mimeMappings) {
            String results[] = new String[mimeMappings.size()];
            return
                ((String[]) mimeMappings.keySet().toArray(results));
        }
    }


    /**
     * 返回指定的上下文初始化参数名称的值; 否则返回<code>null</code>.
     *
     * @param name 要返回的参数的名称
     */
    public String findParameter(String name) {
        synchronized (parameters) {
            return ((String) parameters.get(name));
        }
    }


    /**
     * 返回所有定义的上下文初始化参数的名称. 
     * 如果没有定义参数，则返回零长度数组.
     */
    public String[] findParameters() {
        synchronized (parameters) {
            String results[] = new String[parameters.size()];
            return ((String[]) parameters.keySet().toArray(results));
        }
    }


    /**
     * 为给定的安全角色 (应用程序所使用的), 返回相应的角色名 (Realm定义的). 
     * 否则，返回指定的角色不变.
     *
     * @param role 映射的安全角色
     */
    public String findRoleMapping(String role) {
        String realRole = null;
        synchronized (roleMappings) {
            realRole = (String) roleMappings.get(role);
        }
        if (realRole != null)
            return (realRole);
        else
            return (role);
    }


    /**
     * 返回<code>true</code>如果定义了指定的安全角色; 或者返回<code>false</code>.
     *
     * @param role 要验证的安全角色
     */
    public boolean findSecurityRole(String role) {
        synchronized (securityRoles) {
            for (int i = 0; i < securityRoles.length; i++) {
                if (role.equals(securityRoles[i]))
                    return (true);
            }
        }
        return (false);
    }


    /**
     * 返回为该应用程序定义的安全角色. 
     * 如果没有，返回零长度数组.
     */
    public String[] findSecurityRoles() {
        return (securityRoles);
    }


    /**
     * 返回指定模式映射的servlet名称;
     * 或者返回<code>null</code>.
     *
     * @param pattern 请求映射的模式
     */
    public String findServletMapping(String pattern) {
        synchronized (servletMappings) {
            return ((String) servletMappings.get(pattern));
        }
    }


    /**
     * 返回所有定义的servlet映射的模式. 
     * 如果没有，返回零长度数组.
     */
    public String[] findServletMappings() {
        synchronized (servletMappings) {
            String results[] = new String[servletMappings.size()];
            return
               ((String[]) servletMappings.keySet().toArray(results));
        }
    }


    /**
     * 指定的HTTP状态码对应的错误页面的上下文相对URI; 或者返回<code>null</code>.
     *
     * @param status HTTP status code to look up
     */
    public String findStatusPage(int status) {
        return ((String) statusPages.get(new Integer(status)));
    }


    /**
     * 返回指定错误页面的HTTP状态代码集合.
     * 如果没有，返回零长度数组.
     */
    public int[] findStatusPages() {

        synchronized (statusPages) {
            int results[] = new int[statusPages.size()];
            Iterator elements = statusPages.keySet().iterator();
            int i = 0;
            while (elements.hasNext())
                results[i++] = ((Integer) elements.next()).intValue();
            return (results);
        }

    }


    /**
     * 返回指定URI的标记库描述符位置; 或者返回<code>null</code>.
     *
     * @param uri URI, relative to the web.xml file
     */
    public String findTaglib(String uri) {
        synchronized (taglibs) {
            return ((String) taglibs.get(uri));
        }
    }


    /**
     * 返回所有标签库的URI，已指定标记库描述符位置.
     * 如果没有，返回零长度数组.
     */
    public String[] findTaglibs() {
        synchronized (taglibs) {
            String results[] = new String[taglibs.size()];
            return ((String[]) taglibs.keySet().toArray(results));
        }
    }


    /**
     * 返回<code>true</code>如果定义了指定的欢迎文件;
     * 或者返回<code>false</code>.
     *
     * @param name Welcome file to verify
     */
    public boolean findWelcomeFile(String name) {
        synchronized (welcomeFiles) {
            for (int i = 0; i < welcomeFiles.length; i++) {
                if (name.equals(welcomeFiles[i]))
                    return (true);
            }
        }
        return (false);
    }


    /**
     * 返回此上下文的监视资源集. 如果没有，返回零长度数组.
     */
    public String[] findWatchedResources() {
        return watchedResources;
    }
    
    
    /**
     * 返回定义的欢迎文件集合. 
     * 如果没有，返回零长度数组.
     */
    public String[] findWelcomeFiles() {
        return (welcomeFiles);
    }


    /**
     * 返回LifecycleListener类名集合，将被添加到新创建的Wrapper.
     */
    public String[] findWrapperLifecycles() {
        return (wrapperLifecycles);
    }


    /**
     * 返回ContainerListener类名集合，将被添加到新创建的Wrapper.
     */
    public String[] findWrapperListeners() {
        return (wrapperListeners);
    }


    /**
     * 如果支持重新加载，则重新加载此Web应用程序.
     * <p>
     * <b>实现注意</b>: 这种方法的目的是，在类加载器的底层库中，通过修改class文件重新加载. 
     * 它不处理Web应用程序部署描述符的更改. 如果发生这种情况, 你应该停止当前
     * Context，并创建(并启动)一个新的Context实例.
     *
     * @exception IllegalStateException 如果<code>reloadable</code>属性被设置为<code>false</code>.
     */
    public synchronized void reload() {

        // 验证当前组件状态
        if (!started)
            throw new IllegalStateException
                (sm.getString("containerBase.notStarted", logName()));

        // 确定重载是支持的
        //      if (!reloadable)
        //          throw new IllegalStateException
        //              (sm.getString("standardContext.notReloadable"));
        if(log.isInfoEnabled())
            log.info(sm.getString("standardContext.reloadingStarted"));

        // 暂时停止接受请求
        setPaused(true);

        try {
            stop();
        } catch (LifecycleException e) {
            log.error(sm.getString("standardContext.stoppingContext"), e);
        }

        try {
            start();
        } catch (LifecycleException e) {
            log.error(sm.getString("standardContext.startingContext"), e);
        }

        setPaused(false);
    }


    /**
     * 从监听器集合中删除指定的应用程序监听器.
     *
     * @param listener 要删除的监听器的Java类名
     */
    public void removeApplicationListener(String listener) {

        synchronized (applicationListeners) {

            // 请确保此欢迎文件当前存在
            int n = -1;
            for (int i = 0; i < applicationListeners.length; i++) {
                if (applicationListeners[i].equals(listener)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // 删除指定的约束
            int j = 0;
            String results[] = new String[applicationListeners.length - 1];
            for (int i = 0; i < applicationListeners.length; i++) {
                if (i != n)
                    results[j++] = applicationListeners[i];
            }
            applicationListeners = results;

        }

        // 通知感兴趣的监听器
        fireContainerEvent("removeApplicationListener", listener);

        // FIXME - behavior if already started?
    }


    /**
     * 从集合中移除指定名称的应用程序参数.
     *
     * @param name 要移除的应用程序参数的名称
     */
    public void removeApplicationParameter(String name) {

        synchronized (applicationParameters) {

            // 请确保此参数当前存在
            int n = -1;
            for (int i = 0; i < applicationParameters.length; i++) {
                if (name.equals(applicationParameters[i].getName())) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // 移除指定参数
            int j = 0;
            ApplicationParameter results[] =
                new ApplicationParameter[applicationParameters.length - 1];
            for (int i = 0; i < applicationParameters.length; i++) {
                if (i != n)
                    results[j++] = applicationParameters[i];
            }
            applicationParameters = results;

        }

        // 通知感兴趣的监听器
        fireContainerEvent("removeApplicationParameter", name);
    }


    /**
     * 删除一个子容器.
     *
     * @param child Child container to be added
     *
     * @exception IllegalArgumentException 如果要添加的子容器不是Wrapper实现类
     */
    public void removeChild(Container child) {

        if (!(child instanceof Wrapper)) {
            throw new IllegalArgumentException
                (sm.getString("standardContext.notWrapper"));
        }

        super.removeChild(child);
    }


    /**
     * 从这个Web应用程序中移除指定的安全约束.
     *
     * @param constraint 要删除的约束
     */
    public void removeConstraint(SecurityConstraint constraint) {
        synchronized (constraints) {

            // 请确保此约束当前存在
            int n = -1;
            for (int i = 0; i < constraints.length; i++) {
                if (constraints[i].equals(constraint)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified constraint
            int j = 0;
            SecurityConstraint results[] =
                new SecurityConstraint[constraints.length - 1];
            for (int i = 0; i < constraints.length; i++) {
                if (i != n)
                    results[j++] = constraints[i];
            }
            constraints = results;
        }
        // 通知感兴趣的监听器
        fireContainerEvent("removeConstraint", constraint);
    }


    /**
     * 移除指定错误码或Java异常对应的错误页面; 否则，什么都不做
     *
     * @param errorPage 要移除的错误页面
     */
    public void removeErrorPage(ErrorPage errorPage) {
        String exceptionType = errorPage.getExceptionType();
        if (exceptionType != null) {
            synchronized (exceptionPages) {
                exceptionPages.remove(exceptionType);
            }
        } else {
            synchronized (statusPages) {
                if (errorPage.getErrorCode() == 200) {
                    this.okErrorPage = null;
                }
                statusPages.remove(new Integer(errorPage.getErrorCode()));
            }
        }
        fireContainerEvent("removeErrorPage", errorPage);
    }


    /**
     * 移除指定的过滤器定义; 否则，什么都不做.
     *
     * @param filterDef Filter definition to be removed
     */
    public void removeFilterDef(FilterDef filterDef) {
        synchronized (filterDefs) {
            filterDefs.remove(filterDef.getFilterName());
        }
        fireContainerEvent("removeFilterDef", filterDef);
    }


    /**
     * 移除过滤器映射
     *
     * @param filterMap The filter mapping to be removed
     */
    public void removeFilterMap(FilterMap filterMap) {
        synchronized (filterMaps) {

            // Make sure this filter mapping is currently present
            int n = -1;
            for (int i = 0; i < filterMaps.length; i++) {
                if (filterMaps[i] == filterMap) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified filter mapping
            FilterMap results[] = new FilterMap[filterMaps.length - 1];
            System.arraycopy(filterMaps, 0, results, 0, n);
            System.arraycopy(filterMaps, n + 1, results, n,
                             (filterMaps.length - 1) - n);
            filterMaps = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeFilterMap", filterMap);
    }


    /**
     * 从InstanceListener类名集合中移除指定监听器.
     *
     * @param listener Class name of an InstanceListener class to be removed
     */
    public void removeInstanceListener(String listener) {

        synchronized (instanceListeners) {
            // Make sure this welcome file is currently present
            int n = -1;
            for (int i = 0; i < instanceListeners.length; i++) {
                if (instanceListeners[i].equals(listener)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified constraint
            int j = 0;
            String results[] = new String[instanceListeners.length - 1];
            for (int i = 0; i < instanceListeners.length; i++) {
                if (i != n)
                    results[j++] = instanceListeners[i];
            }
            instanceListeners = results;
        }

        // Inform interested listeners
        fireContainerEvent("removeInstanceListener", listener);
    }


    /**
     * 删除指定名称的消息目标.
     *
     * @param name Name of the message destination to remove
     */
    public void removeMessageDestination(String name) {

        synchronized (messageDestinations) {
            messageDestinations.remove(name);
        }
        fireContainerEvent("removeMessageDestination", name);
    }


    /**
     * 删除指定名称的消息目标引用.
     *
     * @param name Name of the message destination ref to remove
     */
    public void removeMessageDestinationRef(String name) {
        namingResources.removeMessageDestinationRef(name);
        fireContainerEvent("removeMessageDestinationRef", name);
    }


    /**
     * 移除指定扩展名对应的MIME映射; 否则，什么都不做.
     *
     * @param extension Extension to remove the mapping for
     */
    public void removeMimeMapping(String extension) {
        synchronized (mimeMappings) {
            mimeMappings.remove(extension);
        }
        fireContainerEvent("removeMimeMapping", extension);
    }


    /**
     * 移除指定名称对应的上下文初始化参数; 否则，什么都不做.
     *
     * @param name Name of the parameter to remove
     */
    public void removeParameter(String name) {
        synchronized (parameters) {
            parameters.remove(name);
        }
        fireContainerEvent("removeParameter", name);
    }


    /**
     * 移除指定名称对应的所有安全角色
     *
     * @param role Security role (as used in the application) to remove
     */
    public void removeRoleMapping(String role) {
        synchronized (roleMappings) {
            roleMappings.remove(role);
        }
        fireContainerEvent("removeRoleMapping", role);
    }


    /**
     * 移除指定名称对应的所有安全角色.
     *
     * @param role Security role to remove
     */
    public void removeSecurityRole(String role) {

        synchronized (securityRoles) {

            // Make sure this security role is currently present
            int n = -1;
            for (int i = 0; i < securityRoles.length; i++) {
                if (role.equals(securityRoles[i])) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified security role
            int j = 0;
            String results[] = new String[securityRoles.length - 1];
            for (int i = 0; i < securityRoles.length; i++) {
                if (i != n)
                    results[j++] = securityRoles[i];
            }
            securityRoles = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeSecurityRole", role);
    }


    /**
     * 移除指定模式对应的所有servlet映射; 否则，什么都不做.
     *
     * @param pattern URL pattern of the mapping to remove
     */
    public void removeServletMapping(String pattern) {
        String name = null;
        synchronized (servletMappings) {
            name = (String) servletMappings.remove(pattern);
        }
        Wrapper wrapper = (Wrapper) findChild(name);
        if( wrapper != null ) {
            wrapper.removeMapping(pattern);
        }
        mapper.removeWrapper(pattern);
        fireContainerEvent("removeServletMapping", pattern);
    }


    /**
     * 为指定的标记库URI删除标记库位置.
     *
     * @param uri URI, 相对于web.xml文件
     */
    public void removeTaglib(String uri) {

        synchronized (taglibs) {
            taglibs.remove(uri);
        }
        fireContainerEvent("removeTaglib", uri);
    }


    /**
     * 从与此上下文关联的列表中删除指定的受监视资源名称.
     * 
     * @param name 要删除的被监视资源的名称
     */
    public void removeWatchedResource(String name) {
        
        synchronized (watchedResources) {
            // Make sure this watched resource is currently present
            int n = -1;
            for (int i = 0; i < watchedResources.length; i++) {
                if (watchedResources[i].equals(name)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified watched resource
            int j = 0;
            String results[] = new String[watchedResources.length - 1];
            for (int i = 0; i < watchedResources.length; i++) {
                if (i != n)
                    results[j++] = watchedResources[i];
            }
            watchedResources = results;
        }
        fireContainerEvent("removeWatchedResource", name);
    }
    
    
    /**
     * 从指定的列表中删除指定的欢迎文件名
     *
     * @param name 要移除的欢迎文件名
     */
    public void removeWelcomeFile(String name) {

        synchronized (welcomeFiles) {

            // Make sure this welcome file is currently present
            int n = -1;
            for (int i = 0; i < welcomeFiles.length; i++) {
                if (welcomeFiles[i].equals(name)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified constraint
            int j = 0;
            String results[] = new String[welcomeFiles.length - 1];
            for (int i = 0; i < welcomeFiles.length; i++) {
                if (i != n)
                    results[j++] = welcomeFiles[i];
            }
            welcomeFiles = results;

        }

        // Inform interested listeners
        postWelcomeFiles();
        fireContainerEvent("removeWelcomeFile", name);
    }


    /**
     * 从LifecycleListener类集合中移除指定的类名，将被添加到新创建的Wrapper.
     *
     * @param listener Class name of a LifecycleListener class to be removed
     */
    public void removeWrapperLifecycle(String listener) {


        synchronized (wrapperLifecycles) {

            // Make sure this welcome file is currently present
            int n = -1;
            for (int i = 0; i < wrapperLifecycles.length; i++) {
                if (wrapperLifecycles[i].equals(listener)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified constraint
            int j = 0;
            String results[] = new String[wrapperLifecycles.length - 1];
            for (int i = 0; i < wrapperLifecycles.length; i++) {
                if (i != n)
                    results[j++] = wrapperLifecycles[i];
            }
            wrapperLifecycles = results;

        }

        // Inform interested listeners
        fireContainerEvent("removeWrapperLifecycle", listener);
    }


    /**
     * 从ContainerListener类集合中移除指定的类名，将被添加到新创建的Wrapper.
     *
     * @param listener Class name of a ContainerListener class to be removed
     */
    public void removeWrapperListener(String listener) {


        synchronized (wrapperListeners) {

            // Make sure this welcome file is currently present
            int n = -1;
            for (int i = 0; i < wrapperListeners.length; i++) {
                if (wrapperListeners[i].equals(listener)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified constraint
            int j = 0;
            String results[] = new String[wrapperListeners.length - 1];
            for (int i = 0; i < wrapperListeners.length; i++) {
                if (i != n)
                    results[j++] = wrapperListeners[i];
            }
            wrapperListeners = results;
        }
        // Inform interested listeners
        fireContainerEvent("removeWrapperListener", listener);
    }


    /**
     * 获取StandardContext中所有servlet的累计处理时间.
     *
     * @return Cumulative 所有servlet的累计处理时间
     */
    public long getProcessingTime() {
        
        long result = 0;

        Container[] children = findChildren();
        if (children != null) {
            for( int i=0; i< children.length; i++ ) {
                result += ((StandardWrapper)children[i]).getProcessingTime();
            }
        }
        return result;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 配置和初始化一组过滤器.
     * 返回<code>true</code> 如果所有过滤器初始化成功完成; 否则返回<code>false</code>.
     */
    public boolean filterStart() {

        if (getLogger().isDebugEnabled())
            getLogger().debug("Starting filters");
        // 为每个定义的过滤器实例化并记录FilterConfig
        boolean ok = true;
        synchronized (filterConfigs) {
            filterConfigs.clear();
            Iterator names = filterDefs.keySet().iterator();
            while (names.hasNext()) {
                String name = (String) names.next();
                if (getLogger().isDebugEnabled())
                    getLogger().debug(" Starting filter '" + name + "'");
                ApplicationFilterConfig filterConfig = null;
                try {
                    filterConfig = new ApplicationFilterConfig
                      (this, (FilterDef) filterDefs.get(name));
                    filterConfigs.put(name, filterConfig);
                } catch (Throwable t) {
                    getLogger().error
                        (sm.getString("standardContext.filterStart", name), t);
                    ok = false;
                }
            }
        }
        return (ok);
    }


    /**
     * 结束并释放一组过滤器
     * 返回<code>true</code> 如果所有过滤器初始化成功完成; 否则返回<code>false</code>.
     */
    public boolean filterStop() {

        if (getLogger().isDebugEnabled())
            getLogger().debug("Stopping filters");

        // 释放Filter和FilterConfig实例
        synchronized (filterConfigs) {
            Iterator names = filterConfigs.keySet().iterator();
            while (names.hasNext()) {
                String name = (String) names.next();
                if (getLogger().isDebugEnabled())
                    getLogger().debug(" Stopping filter '" + name + "'");
                ApplicationFilterConfig filterConfig =
                  (ApplicationFilterConfig) filterConfigs.get(name);
                filterConfig.release();
            }
            filterConfigs.clear();
        }
        return (true);
    }


    /**
     * 查找并返回指定名称的初始化的<code>FilterConfig</code>; 或者返回<code>null</code>.
     *
     * @param name 所需过滤器的名称
     */
    public FilterConfig findFilterConfig(String name) {
        return ((FilterConfig) filterConfigs.get(name));
    }


    /**
     * 配置一组应用事件监听器.
     * 返回<code>true</code>如果所有监听器初始化成功,否则返回<code>false</code>.
     */
    public boolean listenerStart() {

        if (log.isDebugEnabled())
            log.debug("Configuring application event listeners");

        // 初始化必须的listeners
        ClassLoader loader = getLoader().getClassLoader();
        String listeners[] = findApplicationListeners();
        Object results[] = new Object[listeners.length];
        boolean ok = true;
        for (int i = 0; i < results.length; i++) {
            if (getLogger().isDebugEnabled())
                getLogger().debug(" Configuring event listener class '" +
                    listeners[i] + "'");
            try {
                Class clazz = loader.loadClass(listeners[i]);
                results[i] = clazz.newInstance();
            } catch (Throwable t) {
                getLogger().error
                    (sm.getString("standardContext.applicationListener",
                                  listeners[i]), t);
                ok = false;
            }
        }
        if (!ok) {
            getLogger().error(sm.getString("standardContext.applicationSkipped"));
            return (false);
        }

        // Sort listeners in two arrays
        ArrayList eventListeners = new ArrayList();
        ArrayList lifecycleListeners = new ArrayList();
        for (int i = 0; i < results.length; i++) {
            if ((results[i] instanceof ServletContextAttributeListener)
                || (results[i] instanceof ServletRequestAttributeListener)
                || (results[i] instanceof ServletRequestListener)
                || (results[i] instanceof HttpSessionAttributeListener)) {
                eventListeners.add(results[i]);
            }
            if ((results[i] instanceof ServletContextListener)
                || (results[i] instanceof HttpSessionListener)) {
                lifecycleListeners.add(results[i]);
            }
        }

        setApplicationEventListeners(eventListeners.toArray());
        setApplicationLifecycleListeners(lifecycleListeners.toArray());

        // Send application start events
        if (getLogger().isDebugEnabled())
            getLogger().debug("Sending application start events");

        Object instances[] = getApplicationLifecycleListeners();
        if (instances == null)
            return (ok);
        ServletContextEvent event =
          new ServletContextEvent(getServletContext());
        for (int i = 0; i < instances.length; i++) {
            if (instances[i] == null)
                continue;
            if (!(instances[i] instanceof ServletContextListener))
                continue;
            ServletContextListener listener =
                (ServletContextListener) instances[i];
            try {
                fireContainerEvent("beforeContextInitialized", listener);
                listener.contextInitialized(event);
                fireContainerEvent("afterContextInitialized", listener);
            } catch (Throwable t) {
                fireContainerEvent("afterContextInitialized", listener);
                getLogger().error
                    (sm.getString("standardContext.listenerStart",
                                  instances[i].getClass().getName()), t);
                ok = false;
            }
        }
        return (ok);
    }


    /**
     * 发送一个应用停止事件给所有内部的监听器.
     * 返回<code>true</code>，如果所有事件发送成功, 否则返回<code>false</code>.
     */
    public boolean listenerStop() {

        if (log.isDebugEnabled())
            log.debug("Sending application stop events");

        boolean ok = true;
        Object listeners[] = getApplicationLifecycleListeners();
        if (listeners == null)
            return (ok);
        ServletContextEvent event =
          new ServletContextEvent(getServletContext());
        for (int i = 0; i < listeners.length; i++) {
            int j = (listeners.length - 1) - i;
            if (listeners[j] == null)
                continue;
            if (!(listeners[j] instanceof ServletContextListener))
                continue;
            ServletContextListener listener =
                (ServletContextListener) listeners[j];
            try {
                fireContainerEvent("beforeContextDestroyed", listener);
                listener.contextDestroyed(event);
                fireContainerEvent("afterContextDestroyed", listener);
            } catch (Throwable t) {
                fireContainerEvent("afterContextDestroyed", listener);
                getLogger().error
                    (sm.getString("standardContext.listenerStop",
                                  listeners[j].getClass().getName()), t);
                ok = false;
            }
        }

        setApplicationEventListeners(null);
        setApplicationLifecycleListeners(null);

        return (ok);
    }


    /**
     * 分配资源，包括代理.
     * 返回<code>true</code>，如果初始化成功, 否则返回<code>false</code>.
     */
    public boolean resourcesStart() {

        boolean ok = true;

        Hashtable env = new Hashtable();
        if (getParent() != null)
            env.put(ProxyDirContext.HOST, getParent().getName());
        env.put(ProxyDirContext.CONTEXT, getName());

        try {
            ProxyDirContext proxyDirContext =
                new ProxyDirContext(env, webappResources);
            if (webappResources instanceof FileDirContext) {
                filesystemBased = true;
                ((FileDirContext) webappResources).setCaseSensitive
                    (isCaseSensitive());
                ((FileDirContext) webappResources).setAllowLinking
                    (isAllowLinking());
            }
            if (webappResources instanceof BaseDirContext) {
                ((BaseDirContext) webappResources).setDocBase(getBasePath());
                ((BaseDirContext) webappResources).setCached
                    (isCachingAllowed());
                ((BaseDirContext) webappResources).setCacheTTL(getCacheTTL());
                ((BaseDirContext) webappResources).setCacheMaxSize
                    (getCacheMaxSize());
                ((BaseDirContext) webappResources).allocate();
            }
            // Register the cache in JMX
            if (isCachingAllowed()) {
                ObjectName resourcesName = 
                    new ObjectName(this.getDomain() + ":type=Cache,host=" 
                                   + getHostname() + ",path=" 
                                   + (("".equals(getPath()))?"/":getPath()));
                Registry.getRegistry(null, null).registerComponent
                    (proxyDirContext.getCache(), resourcesName, null);
            }
            this.resources = proxyDirContext;
        } catch (Throwable t) {
            log.error(sm.getString("standardContext.resourcesStart"), t);
            ok = false;
        }
        return (ok);
    }


    /**
     * 释放资源并销毁代理
     */
    public boolean resourcesStop() {

        boolean ok = true;
        try {
            if (resources != null) {
                if (resources instanceof Lifecycle) {
                    ((Lifecycle) resources).stop();
                }
                if (webappResources instanceof BaseDirContext) {
                    ((BaseDirContext) webappResources).release();
                }
                // Unregister the cache in JMX
                if (isCachingAllowed()) {
                    ObjectName resourcesName = 
                        new ObjectName(this.getDomain()
                                       + ":type=Cache,host=" 
                                       + getHostname() + ",path=" 
                                       + (("".equals(getPath()))?"/"
                                          :getPath()));
                    Registry.getRegistry(null, null)
                        .unregisterComponent(resourcesName);
                }
            }
        } catch (Throwable t) {
            log.error(sm.getString("standardContext.resourcesStop"), t);
            ok = false;
        }

        this.resources = null;
        return (ok);
    }


    /**
     * 加载并初始化所有servlet,在定义的时候标有"load on startup".
     *
     * @param children 所有当前定义的servlet包装器数组(包括未声明load on startup)
     */
    public void loadOnStartup(Container children[]) {

        // 需要初始化的"load on startup"servlet集合
        TreeMap map = new TreeMap();
        for (int i = 0; i < children.length; i++) {
            Wrapper wrapper = (Wrapper) children[i];
            int loadOnStartup = wrapper.getLoadOnStartup();
            if (loadOnStartup < 0)
                continue;
            if (loadOnStartup == 0)     // Arbitrarily put them last
                loadOnStartup = Integer.MAX_VALUE;
            Integer key = new Integer(loadOnStartup);
            ArrayList list = (ArrayList) map.get(key);
            if (list == null) {
                list = new ArrayList();
                map.put(key, list);
            }
            list.add(wrapper);
        }

        // 加载"load on startup" servlet集合
        Iterator keys = map.keySet().iterator();
        while (keys.hasNext()) {
            Integer key = (Integer) keys.next();
            ArrayList list = (ArrayList) map.get(key);
            Iterator wrappers = list.iterator();
            while (wrappers.hasNext()) {
                Wrapper wrapper = (Wrapper) wrappers.next();
                try {
                    wrapper.load();
                } catch (ServletException e) {
                    getLogger().error(sm.getString("standardWrapper.loadException",
                                      getName()), StandardWrapper.getRootCause(e));
                    // NOTE: 加载错误(包括从init()方法抛出UnavailableException的servlet)对应用程序启动没有致命影响
                }
            }
        }
    }


    /**
     * @exception LifecycleException if a startup error occurs
     */
    public synchronized void start() throws LifecycleException {
        //if (lazy ) return;
        if (started) {
            if(log.isInfoEnabled())
                log.info(sm.getString("containerBase.alreadyStarted", logName()));
            return;
        }
        if( !initialized ) { 
            try {
                init();
            } catch( Exception ex ) {
                throw new LifecycleException("Error initializaing ", ex);
            }
        }
        if(log.isDebugEnabled())
            log.debug("Starting " + ("".equals(getName()) ? "ROOT" : getName()));

        // Set JMX object name for proper pipeline registration
        preRegisterJMX();

        if ((oname != null) && 
            (Registry.getRegistry(null, null).getMBeanServer().isRegistered(oname))) {
            // 事情取决于JMX注册, 上下文必须重新注册一次，以便正确初始化
            Registry.getRegistry(null, null).unregisterComponent(oname);
        }

        // 通知感兴趣的 LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_START_EVENT, null);

        setAvailable(false);
        setConfigured(false);
        boolean ok = true;

        // 必要时添加缺少的组件
        if (webappResources == null) {   // (1) Required by Loader
            if (log.isDebugEnabled())
                log.debug("Configuring default Resources");
            try {
                if ((docBase != null) && (docBase.endsWith(".war")) && (!(new File(getBasePath())).isDirectory()))
                    setResources(new WARDirContext());
                else
                    setResources(new FileDirContext());
            } catch (IllegalArgumentException e) {
                log.error("Error initializing resources: " + e.getMessage());
                ok = false;
            }
        }
        if (ok) {
            if (!resourcesStart()) {
                log.error( "Error in resourceStart()");
                ok = false;
            }
        }

        // 查找一个 realm - 之前已经配置. 
        // 如果 realm 在上下文之后添加 - 它将设置自己.
        if( realm == null && mserver != null ) {
            ObjectName realmName=null;
            try {
                realmName=new ObjectName( getEngineName() + ":type=Realm,host=" + 
                        getHostname() + ",path=" + getPath());
                if( mserver.isRegistered(realmName ) ) {
                    mserver.invoke(realmName, "init", 
                            new Object[] {},
                            new String[] {}
                    );            
                }
            } catch( Throwable t ) {
                if(log.isDebugEnabled())
                    log.debug("No realm for this host " + realmName);
            }
        }
        
        if (getLoader() == null) {
            ClassLoader parent = null;
            if (getPrivileged()) {
                if (log.isDebugEnabled())
                    log.debug("Configuring privileged default Loader");
                parent = this.getClass().getClassLoader();
            } else {
                if (log.isDebugEnabled())
                    log.debug("Configuring non-privileged default Loader");
                parent = getParentClassLoader();
            }
            WebappLoader webappLoader = new WebappLoader(parent);
            webappLoader.setDelegate(getDelegate());
            setLoader(webappLoader);
        }

        // 初始化字符集映射器
        getCharsetMapper();

        // Post 工作目录
        postWorkDirectory();

        // 验证所需的扩展
        boolean dependencyCheck = true;
        try {
            dependencyCheck = ExtensionValidator.validateApplication
                (getResources(), this);
        } catch (IOException ioe) {
            log.error("Error in dependencyCheck", ioe);
            dependencyCheck = false;
        }

        if (!dependencyCheck) {
            // 不让应用程序可用, 如果depency检查失败
            ok = false;
        }

        // 读取 "catalina.useNaming" 环境变量
        String useNamingProperty = System.getProperty("catalina.useNaming");
        if ((useNamingProperty != null)
            && (useNamingProperty.equals("false"))) {
            useNaming = false;
        }

        if (ok && isUseNaming()) {
            if (namingContextListener == null) {
                namingContextListener = new NamingContextListener();
                namingContextListener.setName(getNamingContextName());
                addLifecycleListener(namingContextListener);
            }
        }

        // 绑定线程
        ClassLoader oldCCL = bindThread();

        // 标准容器启动
        if (log.isDebugEnabled())
            log.debug("Processing standard container startup");

        if (ok) {

            boolean mainOk = false;
            try {

                started = true;

                // 启动从属组件, if any
                if ((loader != null) && (loader instanceof Lifecycle))
                    ((Lifecycle) loader).start();

                // 解绑线程
                unbindThread(oldCCL);

                // 绑定线程
                oldCCL = bindThread();

                if ((logger != null) && (logger instanceof Lifecycle))
                    ((Lifecycle) logger).start();
                if ((cluster != null) && (cluster instanceof Lifecycle))
                    ((Lifecycle) cluster).start();
                if ((realm != null) && (realm instanceof Lifecycle))
                    ((Lifecycle) realm).start();
                if ((resources != null) && (resources instanceof Lifecycle))
                    ((Lifecycle) resources).start();

                // 启动子容器
                Container children[] = findChildren();
                for (int i = 0; i < children.length; i++) {
                    if (children[i] instanceof Lifecycle)
                        ((Lifecycle) children[i]).start();
                }

                // 启动pipeline中的Valves(包括基础的)
                if (pipeline instanceof Lifecycle) {
                    ((Lifecycle) pipeline).start();
                }
                
                if(getProcessTlds()) {
                    processTlds();
                }
                
                // 通知感兴趣的LifecycleListeners
                lifecycle.fireLifecycleEvent(START_EVENT, null);

                // Start manager
                if ((manager != null) && (manager instanceof Lifecycle)) {
                    ((Lifecycle) getManager()).start();
                }

                // Start ContainerBackgroundProcessor thread
                super.threadStart();

                mainOk = true;

            } finally {
                // Unbinding thread
                unbindThread(oldCCL);
                if (!mainOk) {
                    // An exception occurred
                    // Register with JMX anyway, to allow management
                    registerJMX();
                }
            }

        }
        if (!getConfigured()) {
            log.error( "Error getConfigured");
            ok = false;
        }

        // 将资源放入servlet上下文中
        if (ok)
            getServletContext().setAttribute
                (Globals.RESOURCES_ATTR, getResources());

        // 初始化相关的映射
        mapper.setContext(getPath(), welcomeFiles, resources);

        // Binding thread
        oldCCL = bindThread();

        // 创建需要的上下文属性
        if (ok) {
            postWelcomeFiles();
        }

        if (ok) {
            // 通知感兴趣的 LifecycleListeners
            lifecycle.fireLifecycleEvent(AFTER_START_EVENT, null);
        }

        // 配置和调用应用程序事件监听器和过滤器
        if (ok) {
            if (!listenerStart()) {
                log.error( "Error listenerStart");
                ok = false;
            }
        }
        if (ok) {
            if (!filterStart()) {
                log.error( "Error filterStart");
                ok = false;
            }
        }

        // 加载和初始化所有的"load on startup" servlets
        if (ok) {
            loadOnStartup(findChildren());
        }

        // Unbinding thread
        unbindThread(oldCCL);

        // 根据启动成功设置可用状态
        if (ok) {
            if (log.isDebugEnabled())
                log.debug("Starting completed");
            setAvailable(true);
        } else {
            log.error(sm.getString("standardContext.startFailed", getName()));
            try {
                stop();
            } catch (Throwable t) {
                log.error(sm.getString("standardContext.startCleanup"), t);
            }
            setAvailable(false);
        }

        // JMX registration
        registerJMX();

        startTime=System.currentTimeMillis();
        
        // Send j2ee.state.running notification 
        if (ok && (this.getObjectName() != null)) {
            Notification notification = 
                new Notification("j2ee.state.running", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

        // 立即关闭所有jar，以避免在启动时总是打开文件的峰值数量
        if (getLoader() instanceof WebappLoader) {
            ((WebappLoader) getLoader()).closeJARs(true);
        }

        // 重新初始化出错的话
        if (!ok && started) {
            stop();
        }

        //cacheContext();
    }

    /**
     * 处理 TLDs.
     *
     * @throws LifecycleException If an error occurs
     */
     protected void processTlds() throws LifecycleException {
       TldConfig tldConfig = new TldConfig();
       tldConfig.setContext(this);

       // (1)  检查属性是否已在上下文元素上定义.
       tldConfig.setTldValidation(tldValidation);
       tldConfig.setTldNamespaceAware(tldNamespaceAware);

       // (2) 如果属性未在上下文中定义，请尝试主机.
       if (!tldValidation) {
         tldConfig.setTldValidation
           (((StandardHost) getParent()).getXmlValidation());
       }

       if (!tldNamespaceAware) {
         tldConfig.setTldNamespaceAware
           (((StandardHost) getParent()).getXmlNamespaceAware());
       }
                    
       try {
         tldConfig.execute();
       } catch (Exception ex) {
         log.error("Error reading tld listeners " 
                    + ex.toString(), ex); 
       }
     }
    
    private void cacheContext() {
        try {
            File workDir=new File( getWorkPath() );
            
            File ctxSer=new File( workDir, "_tomcat_context.ser");
            FileOutputStream fos=new FileOutputStream( ctxSer );
            ObjectOutputStream oos=new ObjectOutputStream( fos );
            oos.writeObject(this);
            oos.close();
            fos.close();
        } catch( Throwable t ) {
            if(log.isInfoEnabled())
                log.info("Error saving context.ser ", t);
        }
    }

    
    /**
     * @exception LifecycleException if a shutdown error occurs
     */
    public synchronized void stop() throws LifecycleException {

        // 验证并更新当前的组件状态
        if (!started) {
            if(log.isInfoEnabled())
                log.info(sm.getString("containerBase.notStarted", logName()));
            return;
        }

        // 通知感兴趣的 LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_STOP_EVENT, null);
        
        // Send j2ee.state.stopping notification 
        if (this.getObjectName() != null) {
            Notification notification = 
                new Notification("j2ee.state.stopping", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
        // 标记此应用程序在关闭时不可用
        setAvailable(false);

        // Binding thread
        ClassLoader oldCCL = bindThread();

        // Stop our filters
        filterStop();

        // Stop ContainerBackgroundProcessor thread
        super.threadStop();

        if ((manager != null) && (manager instanceof Lifecycle)) {
            ((Lifecycle) manager).stop();
        }

        // 销毁字符集映射器
        setCharsetMapper(null);

        // 正常容器关闭处理
        if (log.isDebugEnabled())
            log.debug("Processing standard container shutdown");
        // 通知感兴趣的 LifecycleListeners
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        try {

            // 停止pipeline中的 Valves(包括基础的)
            if (pipeline instanceof Lifecycle) {
                ((Lifecycle) pipeline).stop();
            }

            // 停止子容器
            Container[] children = findChildren();
            for (int i = 0; i < children.length; i++) {
                if (children[i] instanceof Lifecycle)
                    ((Lifecycle) children[i]).stop();
            }

            // 停止应用程序监听器
            listenerStop();

            // 清除所有应用程序原始的servlet上下文属性
            if (context != null)
                context.clearAttributes();

            // Stop resources
            resourcesStop();

            if ((realm != null) && (realm instanceof Lifecycle)) {
                ((Lifecycle) realm).stop();
            }
            if ((cluster != null) && (cluster instanceof Lifecycle)) {
                ((Lifecycle) cluster).stop();
            }
            if ((logger != null) && (logger instanceof Lifecycle)) {
                ((Lifecycle) logger).stop();
            }
            if ((loader != null) && (loader instanceof Lifecycle)) {
                ((Lifecycle) loader).stop();
            }
        } finally {
            // Unbinding thread
            unbindThread(oldCCL);
        }

        // Send j2ee.state.stopped notification 
        if (this.getObjectName() != null) {
            Notification notification = 
                new Notification("j2ee.state.stopped", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
        // 重置应用上下文
        context = null;

        // 此对象将不再可见或使用. 
        try {
            resetContext();
        } catch( Exception ex ) {
            log.error( "Error reseting context " + this + " " + ex, ex );
        }
        
        // 通知感兴趣的 LifecycleListeners
        lifecycle.fireLifecycleEvent(AFTER_STOP_EVENT, null);

        if (log.isDebugEnabled())
            log.debug("Stopping complete");
    }

    /** 销毁需要完全清除上下文.
     * 
     * 问题是, 撤销start()的所有配置, 并尽可能修复'fresh'状态. 在stop()/destroy()/init()/start()之后, 
     * 如果刷新开始完成, 我们应该有相同的状态 - 即读取修改后的web.xml, etc. 这只能通过完全删除上下文对象和映射一个新的, 或者清空所有来完成.
     * 
     * XXX Should this be done in stop() ?
     */ 
    public void destroy() throws Exception {
        if( oname != null ) { 
            // Send j2ee.object.deleted notification 
            Notification notification = 
                new Notification("j2ee.object.deleted", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        } 
        super.destroy();

        // 通知感兴趣的 LifecycleListeners
        lifecycle.fireLifecycleEvent(DESTROY_EVENT, null);

        instanceListeners = new String[0];
        applicationListeners = new String[0];
    }
    
    private void resetContext() throws Exception, MBeanRegistrationException {
        // 还原原始状态(启动时预读取 web.xml)
        // 如果你继承这个 - 覆盖此方法并确保清理
        children=new HashMap();
        startupTime = 0;
        startTime = 0;
        tldScanTime = 0;

        // Bugzilla 32867
        distributable = false;

        if(log.isDebugEnabled())
            log.debug("resetContext " + oname + " " + mserver);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        if (getParent() != null) {
            sb.append(getParent().toString());
            sb.append(".");
        }
        sb.append("StandardContext[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 调整URL模式以一个斜杠开始, 如果合适的话 (即我们正在运行servlet 2.2应用程序). 
     * 否则，返回指定的URL模式不变.
     *
     * @param urlPattern 要调整和返回的URL模式
     */
    protected String adjustURLPattern(String urlPattern) {
        if (urlPattern == null)
            return (urlPattern);
        if (urlPattern.startsWith("/") || urlPattern.startsWith("*."))
            return (urlPattern);
        if (!isServlet22())
            return (urlPattern);
        if(log.isDebugEnabled())
            log.debug(sm.getString("standardContext.urlPattern.patternWarning",
                         urlPattern));
        return ("/" + urlPattern);
    }


    /**
     * 正在处理一个版本2.2的部署描述符吗?
     */
    protected boolean isServlet22() {
        if (this.publicId == null)
            return (false);
        if (this.publicId.equals
            (org.apache.catalina.startup.Constants.WebDtdPublicId_22))
            return (true);
        else
            return (false);
    }


    /**
     * 返回表示整个servlet容器的基目录的文件对象 (即Engine容器如果存在).
     */
    protected File engineBase() {
        String base=System.getProperty("catalina.base");
        if( base == null ) {
            StandardEngine eng=(StandardEngine)this.getParent().getParent();
            base=eng.getBaseDir();
        }
        return (new File(base));
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 绑定当前线程, 对CL目的和JNDI ENC的支持 : 上下文的startup, shutdown, realoading
     * 
     * @return 前一个上下文类加载器
     */
    private ClassLoader bindThread() {

        ClassLoader oldContextClassLoader =
            Thread.currentThread().getContextClassLoader();

        if (getResources() == null)
            return oldContextClassLoader;

        if (getLoader().getClassLoader() != null) {
            Thread.currentThread().setContextClassLoader
                (getLoader().getClassLoader());
        }

        DirContextURLStreamHandler.bind(getResources());

        if (isUseNaming()) {
            try {
                ContextBindings.bindThread(this, this);
            } catch (NamingException e) {
                // 因为这是一个正常的情况，在早期启动阶段
            }
        }
        return oldContextClassLoader;
    }


    /**
     * 解绑线程
     */
    private void unbindThread(ClassLoader oldContextClassLoader) {

        Thread.currentThread().setContextClassLoader(oldContextClassLoader);

        oldContextClassLoader = null;

        if (isUseNaming()) {
            ContextBindings.unbindThread(this, this);
        }
        DirContextURLStreamHandler.unbind();
    }



    /**
     * 获取基础路径
     */
    private String getBasePath() {
        String docBase = null;
        Container container = this;
        while (container != null) {
            if (container instanceof Host)
                break;
            container = container.getParent();
        }
        File file = new File(getDocBase());
        if (!file.isAbsolute()) {
            if (container == null) {
                docBase = (new File(engineBase(), getDocBase())).getPath();
            } else {
                // Use the "appBase" property of this container
                String appBase = ((Host) container).getAppBase();
                file = new File(appBase);
                if (!file.isAbsolute())
                    file = new File(engineBase(), appBase);
                docBase = (new File(file, getDocBase())).getPath();
            }
        } else {
            docBase = file.getPath();
        }
        return docBase;
    }


    /**
     * Get app base.
     */
    private String getAppBase() {
        String appBase = null;
        Container container = this;
        while (container != null) {
            if (container instanceof Host)
                break;
            container = container.getParent();
        }
        if (container != null) {
            appBase = ((Host) container).getAppBase();
        }
        return appBase;
    }


    /**
     * Get config base.
     */
    public File getConfigBase() {
        File configBase = 
            new File(System.getProperty("catalina.base"), "conf");
        if (!configBase.exists()) {
            return null;
        }
        Container container = this;
        Container host = null;
        Container engine = null;
        while (container != null) {
            if (container instanceof Host)
                host = container;
            if (container instanceof Engine)
                engine = container;
            container = container.getParent();
        }
        if (engine != null) {
            configBase = new File(configBase, engine.getName());
        }
        if (host != null) {
            configBase = new File(configBase, host.getName());
        }
        if (saveConfig) {
            configBase.mkdirs();
        }
        return configBase;
    }


    /**
     * 给定一个上下文路径，获取配置文件名.
     */
    protected String getDefaultConfigFile() {
        String basename = null;
        String path = getPath();
        if (path.equals("")) {
            basename = "ROOT";
        } else {
            basename = path.substring(1).replace('/', '#');
        }
        return (basename + ".xml");
    }


    /**
     * Copy a file.
     */
    private boolean copy(File src, File dest) {
        FileInputStream is = null;
        FileOutputStream os = null;
        try {
            is = new FileInputStream(src);
            os = new FileOutputStream(dest);
            byte[] buf = new byte[4096];
            while (true) {
                int len = is.read(buf);
                if (len < 0)
                    break;
                os.write(buf, 0, len);
            }
            is.close();
            os.close();
        } catch (IOException e) {
            return false;
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (Exception e) {
                // Ignore
            }
            try {
                if (os != null) {
                    os.close();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        return true;
    }


    /**
     * 获取命名上下文全名
     */
    private String getNamingContextName() {
	    if (namingContextName == null) {
	        Container parent = getParent();
	        if (parent == null) {
	        namingContextName = getName();
	        } else {
	        Stack stk = new Stack();
	        StringBuffer buff = new StringBuffer();
	        while (parent != null) {
	            stk.push(parent.getName());
	            parent = parent.getParent();
	        }
	        while (!stk.empty()) {
	            buff.append("/" + stk.pop());
	        }
	        buff.append(getName());
	        namingContextName = buff.toString();
	        }
	    }
	    return namingContextName;
    }


    /**
     * 返回请求处理暂停标志
     */
    public boolean getPaused() {
        return (this.paused);
    }


    /**
     * 将Web应用程序资源的副本作为servlet上下文属性发布.
     */
    private void postResources() {
        getServletContext().setAttribute(Globals.RESOURCES_ATTR, getResources());
    }


    /**
     * 将当前欢迎文件列表复制为servlet上下文属性，以便默认servlet可以找到它们.
     */
    private void postWelcomeFiles() {
        getServletContext().setAttribute("org.apache.catalina.WELCOME_FILES", welcomeFiles);
    }

    public String getHostname() {
        Container parentHost = getParent();
        if (parentHost != null) {
            hostName = parentHost.getName();
        }
        if ((hostName == null) || (hostName.length() < 1))
            hostName = "_";
        return hostName;
    }

    /**
     * 为工作目录设置适当的上下文属性.
     */
    private void postWorkDirectory() {

        // 获取（或计算）工作目录路径
        String workDir = getWorkDir();
        if (workDir == null) {

            // 检索父级(通常是一个主机)名称
            String hostName = null;
            String engineName = null;
            String hostWorkDir = null;
            Container parentHost = getParent();
            if (parentHost != null) {
                hostName = parentHost.getName();
                if (parentHost instanceof StandardHost) {
                    hostWorkDir = ((StandardHost)parentHost).getWorkDir();
                }
                Container parentEngine = parentHost.getParent();
                if (parentEngine != null) {
                   engineName = parentEngine.getName();
                }
            }
            if ((hostName == null) || (hostName.length() < 1))
                hostName = "_";
            if ((engineName == null) || (engineName.length() < 1))
                engineName = "_";

            String temp = getPath();
            if (temp.startsWith("/"))
                temp = temp.substring(1);
            temp = temp.replace('/', '_');
            temp = temp.replace('\\', '_');
            if (temp.length() < 1)
                temp = "_";
            if (hostWorkDir != null ) {
                workDir = hostWorkDir + File.separator + temp;
            } else {
                workDir = "work" + File.separator + engineName +
                    File.separator + hostName + File.separator + temp;
            }
            setWorkDir(workDir);
        }

        // 创建目录，如果必要
        File dir = new File(workDir);
        if (!dir.isAbsolute()) {
            File catalinaHome = engineBase();
            String catalinaHomePath = null;
            try {
                catalinaHomePath = catalinaHome.getCanonicalPath();
                dir = new File(catalinaHomePath, workDir);
            } catch (IOException e) {
            }
        }
        dir.mkdirs();

        // 设置适当的servlet上下文属性
        getServletContext().setAttribute(Globals.WORK_DIR_ATTR, dir);
        if (getServletContext() instanceof ApplicationContext)
            ((ApplicationContext) getServletContext()).setAttributeReadOnly(Globals.WORK_DIR_ATTR);
    }


    /**
     * 设置请求处理暂停标志
     *
     * @param paused The new request processing paused flag
     */
    private void setPaused(boolean paused) {
        this.paused = paused;
    }


    /**
     * 验证提议的语法 <code>&lt;url-pattern&gt;</code>是否符合规格要求
     *
     * @param urlPattern 要验证的URL模式
     */
    private boolean validateURLPattern(String urlPattern) {
        if (urlPattern == null)
            return (false);
        if (urlPattern.indexOf('\n') >= 0 || urlPattern.indexOf('\r') >= 0) {
            getLogger().warn(sm.getString("standardContext.crlfinurl",urlPattern));
        }
        if (urlPattern.startsWith("*.")) {
            if (urlPattern.indexOf('/') < 0)
                return (true);
            else
                return (false);
        }
        if ( (urlPattern.startsWith("/")) &&
                (urlPattern.indexOf("*.") < 0))
            return (true);
        else
            return (false);
    }


    // ------------------------------------------------------------- Operations


    /**
     * JSR77 部署描述符属性
     *
     * @return string deployment descriptor 
     */
    public String getDeploymentDescriptor() {
    
        InputStream stream = null;
        ServletContext servletContext = getServletContext();
        if (servletContext != null) {
            stream = servletContext.getResourceAsStream(
                org.apache.catalina.startup.Constants.ApplicationWebXml);
        }
        if (stream == null) {
            return "";
        }
        BufferedReader br = new BufferedReader(
                                new InputStreamReader(stream));
        StringBuffer sb = new StringBuffer();
        String strRead = "";
        try {
            while (strRead != null) {
                sb.append(strRead);
                strRead = br.readLine();
            }
        } catch (IOException e) {
            return "";
        }

        return sb.toString(); 
    
    }
    
    
    /**
     * JSR77 servlet属性
     *
     * @return 所有的servlet (我们知道的)
     */
    public String[] getServlets() {
        
        String[] result = null;
        Container[] children = findChildren();
        if (children != null) {
            result = new String[children.length];
            for( int i=0; i< children.length; i++ ) {
                result[i] = ((StandardWrapper)children[i]).getObjectName();
            }
        }
        return result;
    }
    

    public ObjectName createObjectName(String hostDomain, ObjectName parentName)
            throws MalformedObjectNameException {
        String onameStr;
        StandardHost hst=(StandardHost)getParent();
        
        String pathName=getName();
        String hostName=getParent().getName();
        String name= "//" + ((hostName==null)? "DEFAULT" : hostName) +
                (("".equals(pathName))?"/":pathName );

        String suffix=",J2EEApplication=" +
                getJ2EEApplication() + ",J2EEServer=" +
                getJ2EEServer();

        onameStr="j2eeType=WebModule,name=" + name + suffix;
        if( log.isDebugEnabled())
            log.debug("Registering " + onameStr + " for " + oname);
        
        // default case - no domain explictely set.
        if( getDomain() == null ) domain=hst.getDomain();

        ObjectName oname=new ObjectName(getDomain() + ":" + onameStr);
        return oname;        
    }    
    
    private void preRegisterJMX() {
        try {
            StandardHost host = (StandardHost) getParent();
            if ((oname == null) 
                || (oname.getKeyProperty("j2eeType") == null)) {
                oname = createObjectName(host.getDomain(), host.getJmxName());
                controller = oname;
            }
        } catch(Exception ex) {
            if(log.isInfoEnabled())
                log.info("Error registering ctx with jmx " + this + " " +
                     oname + " " + ex.toString(), ex );
        }
    }

    private void registerJMX() {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Checking for " + oname );
            }
            if(! Registry.getRegistry(null, null)
                .getMBeanServer().isRegistered(oname)) {
                controller = oname;
                Registry.getRegistry(null, null)
                    .registerComponent(this, oname, null);
                
                // Send j2ee.object.created notification 
                if (this.getObjectName() != null) {
                    Notification notification = new Notification(
                                                        "j2ee.object.created", 
                                                        this.getObjectName(), 
                                                        sequenceNumber++);
                    broadcaster.sendNotification(notification);
                }
            }
            Container children[] = findChildren();
            for (int i=0; children!=null && i<children.length; i++) {
                ((StandardWrapper)children[i]).registerJMX( this );
            }
        } catch (Exception ex) {
            if(log.isInfoEnabled())
                log.info("Error registering wrapper with jmx " + this + " " +
                    oname + " " + ex.toString(), ex );
        }
    }

    /** 两种情况:
     *   1. 上下文是通过内部api创建和注册的
     *   2. 上下文是由JMX创建的, 而且它会自己注册.
     *
     * @param server 服务器
     * @param name 对象名
     * @return ObjectName 对象名
     * @throws Exception If an error occurs
     */
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        if( oname != null ) {
            //log.info( "Already registered " + oname + " " + name);
            // Temporary - /admin uses the old names
            return name;
        }
        ObjectName result=super.preRegister(server,name);
        return name;
    }

    public void preDeregister() throws Exception {
        if( started ) {
            try {
                stop();
            } catch( Exception ex ) {
                log.error( "error stopping ", ex);
            }
        }
    }

    public void init() throws Exception {

        if( this.getParent() == null ) {
            ObjectName parentName=getParentName();
            
            if( ! mserver.isRegistered(parentName)) {
                if(log.isDebugEnabled())
                    log.debug("No host, creating one " + parentName);
                StandardHost host=new StandardHost();
                host.setName(hostName);
                host.setAutoDeploy(false);
                Registry.getRegistry(null, null)
                    .registerComponent(host, parentName, null);
                mserver.invoke(parentName, "init", new Object[] {}, new String[] {} );
            }
            ContextConfig config = new ContextConfig();
            this.addLifecycleListener(config);

            if(log.isDebugEnabled())
                 log.debug( "AddChild " + parentName + " " + this);
            try {
                mserver.invoke(parentName, "addChild", new Object[] { this },
                               new String[] {"org.apache.catalina.Container"});
            } catch (Exception e) {
                destroy();
                throw e;
            }
        }
        super.init();
        
        // 通知感兴趣的 LifecycleListeners
        lifecycle.fireLifecycleEvent(INIT_EVENT, null);

        // Send j2ee.state.starting notification 
        if (this.getObjectName() != null) {
            Notification notification = new Notification("j2ee.state.starting", 
                                                        this.getObjectName(), 
                                                        sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
    }

    public ObjectName getParentName() throws MalformedObjectNameException {
        // "Life" update
        String path=oname.getKeyProperty("name");
        if( path == null ) {
            log.error( "No name attribute " +name );
            return null;
        }
        if( ! path.startsWith( "//")) {
            log.error("Invalid name " + name);
        }
        path=path.substring(2);
        int delim=path.indexOf( "/" );
        hostName="localhost"; // Should be default...
        if( delim > 0 ) {
            hostName=path.substring(0, delim);
            path = path.substring(delim);
            if (path.equals("/")) {
                this.setName("");
            } else {
                this.setName(path);
            }
        } else {
            if(log.isDebugEnabled())
                log.debug("Setting path " +  path );
            this.setName( path );
        }
        // XXX 服务和域名应该相同.
        String parentDomain=getEngineName();
        if( parentDomain == null ) parentDomain=domain;
        ObjectName parentName=new ObjectName( parentDomain + ":" +
                "type=Host,host=" + hostName);
        return parentName;
    }
    
    public void create() throws Exception{
        init();
    }

    /* Remove a JMX notficationListener 
     * @see javax.management.NotificationEmitter#removeNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    public void removeNotificationListener(NotificationListener listener, 
            NotificationFilter filter, Object object) throws ListenerNotFoundException {
    	broadcaster.removeNotificationListener(listener,filter,object);
    	
    }
    
    private MBeanNotificationInfo[] notificationInfo;
    
    /* Get JMX Broadcaster Info
     * @TODO use StringManager for international support!
     * @TODO This two events we not send j2ee.state.failed and j2ee.attribute.changed!
     * @see javax.management.NotificationBroadcaster#getNotificationInfo()
     */
    public MBeanNotificationInfo[] getNotificationInfo() {
    	// FIXME: i18n
    	if(notificationInfo == null) {
    		notificationInfo = new MBeanNotificationInfo[]{
    				new MBeanNotificationInfo(new String[] {
    				"j2ee.object.created"},
					Notification.class.getName(),
					"web application is created"
    				), 
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.starting"},
					Notification.class.getName(),
					"change web application is starting"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.running"},
					Notification.class.getName(),
					"web application is running"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.stopped"},
					Notification.class.getName(),
					"web application start to stopped"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.object.stopped"},
					Notification.class.getName(),
					"web application is stopped"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.object.deleted"},
					Notification.class.getName(),
					"web application is deleted"
					)
    		};
    	}
    	return notificationInfo;
    }
    
    
    /* Add a JMX-NotificationListener
     * @see javax.management.NotificationBroadcaster#addNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    public void addNotificationListener(NotificationListener listener, 
            NotificationFilter filter, Object object) throws IllegalArgumentException {
    	broadcaster.addNotificationListener(listener,filter,object);
    }
    
    
    /**
     * Remove a JMX-NotificationListener 
     * @see javax.management.NotificationBroadcaster#removeNotificationListener(javax.management.NotificationListener)
     */
    public void removeNotificationListener(NotificationListener listener) throws ListenerNotFoundException {
    	broadcaster.removeNotificationListener(listener);
    }
    
    // ------------------------------------------------------------- Attributes

    /**
     * 返回与此Web应用程序相关联的命名资源.
     */
    public javax.naming.directory.DirContext getStaticResources() {
        return getResources();
    }


    /**
     * 返回与此Web应用程序相关联的命名资源.
     * FIXME: Fooling introspection ... 
     */
    public javax.naming.directory.DirContext findStaticResources() {
        return getResources();
    }


    public String[] getWelcomeFiles() {
        return findWelcomeFiles();
    }

     /**
     * 设置解析XML实例时使用的XML解析器的验证特性.
     * @param webXmlValidation true 启用XML实例验证
     */
    public void setXmlValidation(boolean webXmlValidation){
        this.webXmlValidation = webXmlValidation;
    }

    /**
     * 获取server.xml <context> 属性的 xmlValidation.
     * @return true if validation is enabled.
     */
    public boolean getXmlValidation(){
        return webXmlValidation;
    }


    /**
     * 获取 server.xml <context> 属性的 xmlNamespaceAware.
     * @return true if namespace awarenes is enabled.
     */
    public boolean getXmlNamespaceAware(){
        return webXmlNamespaceAware;
    }


    /**
     * 设置解析XML实例时使用的XML解析器的命名空间感知特性.
     * @param webXmlNamespaceAware true 启用命名空间感知
     */
    public void setXmlNamespaceAware(boolean webXmlNamespaceAware){
        this.webXmlNamespaceAware= webXmlNamespaceAware;
    }    


    /**
     * 设置XML解析器解析tld文件时使用的验证功能.
     *  
     * @param tldValidation true 启用XML实例验证
     */
    public void setTldValidation(boolean tldValidation){
        this.tldValidation = tldValidation;
    }

    /**
     * 获取server.xml <context> 属性的 webXmlValidation.
     * @return true if validation is enabled.
     */
    public boolean getTldValidation(){
        return tldValidation;
    }

    /**
     * 设置处理 TLDs 属性.
     *
     * @param newProcessTlds The new value
     */
    public void setProcessTlds(boolean newProcessTlds) {
    	processTlds = newProcessTlds;
    }

    /**
     * 返回 processTlds 属性值.
     */
    public boolean getProcessTlds() {
    	return processTlds;
    }

    /**
     * 获取server.xml <host>属性的 xmlNamespaceAware.
     * @return true 如果启用命名空间感知
     */
    public boolean getTldNamespaceAware(){
        return tldNamespaceAware;
    }


    /**
     * 设置解析XML实例时使用的XML解析器的命名空间感知功能.
     * 
     * @param tldNamespaceAware true 启用命名空间感知功能
     */
    public void setTldNamespaceAware(boolean tldNamespaceAware){
        this.tldNamespaceAware= tldNamespaceAware;
    }    


    /** 
     * 用于支持"stateManageable" JSR77 
     */
    public boolean isStateManageable() {
        return true;
    }
    
    public void startRecursive() throws LifecycleException {
        // 无需开始递归, servlet将通过 load-on-startup启动
        start();
    }
    
    public int getState() {
        if( started ) {
            return 1; // RUNNING
        }
        if( initialized ) {
            return 0; // starting ? 
        }
        if( ! available ) { 
            return 4; //FAILED
        }
        // 2 - STOPPING
        return 3; // STOPPED
    }
    
    /**
     * 此模块部署在的J2EE Server ObjectName.
     */     
    private String server = null;
    
    /**
     * 运行此模块的Java虚拟机上.
     */       
    private String[] javaVMs = null;
    
    public String getServer() {
        return server;
    }
        
    public String setServer(String server) {
        return this.server=server;
    }
        
    public String[] getJavaVMs() {
        return javaVMs;
    }
        
    public String[] setJavaVMs(String[] javaVMs) {
        return this.javaVMs = javaVMs;
    }
    
    /**
     * 获取此上下文启动的时间.
     *
     * @return Time (毫秒, 自从 January 1, 1970, 00:00:00) 当这个上下文启动的时候
     */
    public long getStartTime() {
        return startTime;
    }
    
    public boolean isEventProvider() {
        return false;
    }
    
    public boolean isStatisticsProvider() {
        return false;
    }
}
