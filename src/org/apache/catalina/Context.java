package org.apache.catalina;


import javax.servlet.ServletContext;

import org.apache.tomcat.util.http.mapper.Mapper;

import org.apache.catalina.deploy.ApplicationParameter;
import org.apache.catalina.deploy.ErrorPage;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.NamingResources;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.util.CharsetMapper;


/**
 * <b>Context</b>是一个容器，表示一个servlet上下文, 即在Catalina servlet引擎中一个单独的Web应用程序.
 * 因此，它几乎在每个Catalina部署中都是有用的(即使一个Connector连接到一个Web服务器,如Apache Web服务器)
 * 使用Web服务器的工具来识别适当的Wrapper来处理此请求
 * 它还提供了一个方便的机制使用拦截器，查看由这个特定Web应用程序处理的每个请求.
 * <p>
 * 附加到上下文的父Container通常是一个Host，也可能是一些其他实现，而且如果没有必要，可以省略
 * <p>
 * 附加在上下文中的子容器通常是Wrapper的实现（表示单个servlet定义）
 * <p>
 */
public interface Context extends Container {

    // ----------------------------------------------------- Manifest Constants

    /**
     * 当上下文被重载的时候，LifecycleEvent类型将会被发送
     */
    public static final String RELOAD_EVENT = "reload";


    // ------------------------------------------------------------- Properties


    /**
     * 返回初始化的应用程序监听器对象集合, 按照在Web应用程序部署描述符中指定的顺序
     *
     * @exception IllegalStateException 如果该方法在应用启动之前调用，或者已经停止之后调用
     */
    public Object[] getApplicationEventListeners();


    /**
     * 存储初始化应用监听器的对象集合,按照在Web应用程序部署描述符中指定的顺序
     *
     * @param listeners 实例化的监听器对象集合
     */
    public void setApplicationEventListeners(Object listeners[]);


    /**
     * 返回初始化的应用程序生命周期监听器对象集合, 按照在Web应用程序部署描述符中指定的顺序
     *
     * @exception IllegalStateException 如果该方法在应用启动之前调用，或者已经停止之后调用
     */
    public Object[] getApplicationLifecycleListeners();


    /**
     * 存储初始化应用程序生命周期监听器的对象集合,按照在Web应用程序部署描述符中指定的顺序
     *
     * @param listeners 实例化的监听器对象集合
     */
    public void setApplicationLifecycleListeners(Object listeners[]);


    /**
     * 返回此上下文的应用程序可用标志
     */
    public boolean getAvailable();


    /**
     * 设置此上下文的应用程序可用标志
     *
     * @param available The new application available flag
     */
    public void setAvailable(boolean available);


    /**
     * 返回字符集映射的区域
     */
    public CharsetMapper getCharsetMapper();


    /**
     * 设置字符集映射的区域
     *
     * @param mapper The new mapper
     */
    public void setCharsetMapper(CharsetMapper mapper);


    /**
     * 返回保存这个Context信息的文件的路径
     */
    public String getConfigFile();


    /**
     * 设置保存这个Context信息的文件的路径
     *
     * @param configFile 保存上下文信息的文件的路径
     */
    public void setConfigFile(String configFile);


    /**
     * 返回是否“正确配置”的标志
     */
    public boolean getConfigured();


    /**
     * 设置是否“正确配置”的标志。  可以通过启动监听器设置为false，为了避免使用中的应用检测到致命的配置错误
     *
     * @param configured 正确配置标志
     */
    public void setConfigured(boolean configured);


    /**
     * 返回“使用cookie作为会话ID”标志
     */
    public boolean getCookies();


    /**
     * 设置“使用cookie作为会话ID”标志
     *
     * @param cookies The new flag
     */
    public void setCookies(boolean cookies);


    /**
     * 返回“允许交叉servlet上下文”标志
     */
    public boolean getCrossContext();


    
    /**
     * 返回备用部署描述符名称
     */
    public String getAltDDName();
    
    
    /**
     * 设置备用部署描述符名称
     */
    public void setAltDDName(String altDDName) ;
    
    
    /**
     * 设置“允许交叉servlet上下文”标志
     *
     * @param crossContext The new cross contexts flag
     */
    public void setCrossContext(boolean crossContext);


    /**
     * 返回此Web应用程序的显示名称
     */
    public String getDisplayName();


    /**
     * 设置此Web应用程序的显示名称
     *
     * @param displayName 显示名称
     */
    public void setDisplayName(String displayName);


    /**
     * 返回该Web应用程序的发布标志
     */
    public boolean getDistributable();


    /**
     * 设置该Web应用程序的发布标志
     *
     * @param distributable The new distributable flag
     */
    public void setDistributable(boolean distributable);


    /**
     * 返回此上下文的文档根目录。这可以是绝对路径，相对路径，或一个URL
     */
    public String getDocBase();


    /**
     * 设置此上下文的文档根目录。这可以是绝对路径，相对路径，或一个URL
     *
     * @param docBase 文档根目录
     */
    public void setDocBase(String docBase);


    /**
     * 返回URL编码的上下文路径, 使用UTF-8.
     */
    public String getEncodedPath();


    /**
     * 返回此Web应用程序的登录配置
     */
    public LoginConfig getLoginConfig();


    /**
     * 设置此Web应用程序的登录配置
     *
     * @param config 登录配置
     */
    public void setLoginConfig(LoginConfig config);


    /**
     * 获取请求调度器映射器
     */
    public Mapper getMapper();


    /**
     * 返回与此Web应用程序相关联的命名资源
     */
    public NamingResources getNamingResources();


    /**
     * 设置与此Web应用程序相关联的命名资源
     *
     * @param namingResources 命名资源
     */
    public void setNamingResources(NamingResources namingResources);


    /**
     * 返回此Web应用程序的上下文路径
     */
    public String getPath();


    /**
     * 设置此Web应用程序的上下文路径
     *
     * @param path 上下文路径
     */
    public void setPath(String path);


    /**
     * 返回当前正在解析的部署描述符DTD的公共标识符
     */
    public String getPublicId();


    /**
     * 设置当前正在解析的部署描述符DTD的公共标识符
     *
     * @param publicId 公共标识符
     */
    public void setPublicId(String publicId);


    /**
     * 返回是否可以重载的标识
     */
    public boolean getReloadable();


    /**
     * 设置是否可以重载的标识
     *
     * @param reloadable The new reloadable flag
     */
    public void setReloadable(boolean reloadable);


    /**
     * 返回此Web应用程序的覆盖标志
     */
    public boolean getOverride();


    /**
     * 设置此Web应用程序的覆盖标志
     *
     * @param override The new override flag
     */
    public void setOverride(boolean override);


    /**
     * 返回此Web应用程序的特权标志
     */
    public boolean getPrivileged();


    /**
     * 设置此Web应用程序的特权标志
     *
     * @param privileged The new privileged flag
     */
    public void setPrivileged(boolean privileged);


    /**
     * 返回servlet上下文， 这个上下文是一个外观模式.
     */
    public ServletContext getServletContext();


    /**
     * 返回此Web应用程序的默认会话超时（分钟）
     */
    public int getSessionTimeout();


    /**
     * 设置此Web应用程序的默认会话超时（分钟）
     *
     * @param timeout 默认超时时间
     */
    public void setSessionTimeout(int timeout);


    /**
     * Return the value of the swallowOutput flag.
     */
    public boolean getSwallowOutput();


    /**
     * 设置swallowOutput标记.
     * 如果设置为true, system.out和 system.err 将被重定向到logger, 在servlet执行期间.
     *
     * @param swallowOutput The new value
     */
    public void setSwallowOutput(boolean swallowOutput);


    /**
     * 返回这个Context中用于注册servlet的Wrapper实现类的java类名.
     */
    public String getWrapperClass();


    /**
     * 设置这个Context中用于注册servlet的Wrapper实现类的java类名.
     *
     * @param wrapperClass Wrapper实现类类名
     */
    public void setWrapperClass(String wrapperClass);


    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个监听器类名到配置的监听器集合中
     *
     * @param listener 监听器Java类名
     */
    public void addApplicationListener(String listener);


    /**
     * 添加应用参数
     *
     * @param parameter 应用参数
     */
    public void addApplicationParameter(ApplicationParameter parameter);


    /**
     * 添加一个安全约束
     */
    public void addConstraint(SecurityConstraint constraint);


    /**
     * 为指定的错误或Java异常添加一个错误页面
     *
     * @param errorPage 错误页面
     */
    public void addErrorPage(ErrorPage errorPage);


    /**
     * 在此上下文中添加过滤器定义
     *
     * @param filterDef 过滤器定义
     */
    public void addFilterDef(FilterDef filterDef);


    /**
     * 添加一个过滤器映射
     *
     * @param filterMap 过滤器映射
     */
    public void addFilterMap(FilterMap filterMap);


    /**
     * 添加到每个附加在当前上下文的Wrapper的InstanceListener类名 
     *
     * @param listener Java class name of an InstanceListener class
     */
    public void addInstanceListener(String listener);


    /**
     * 将给定URL模式添加为JSP属性组.
     * 这将映射与给定模式相匹配的资源，以便将它们传递给JSP容器. 尽管属性组中还有其他元素, 在这里只关心URL模式.
     * JSP容器将解析其余的.
     *
     * @param pattern 要映射的URL模式
     */
    public void addJspMapping(String pattern);


    /**
     * 添加到本地编码映射(see Sec 5.4 of Servlet spec 2.4)
     *
     * @param locale 映射编码的区域
     * @param encoding 用于给定区域的编码
     */
    public void addLocaleEncodingMappingParameter(String locale, String encoding);


    /**
     * 添加一个新的MIME映射，以替换指定扩展名的任何现有映射
     *
     * @param extension 文件扩展名映射
     * @param mimeType 相应的MIME类型
     */
    public void addMimeMapping(String extension, String mimeType);


    /**
     * 添加一个新的上下文初始化参数，替换指定名称的任何现有值
     *
     * @param name 参数名
     * @param value 参数值
     */
    public void addParameter(String name, String value);


    /**
     * 添加安全角色引用
     *
     * @param role 应用程序中使用的安全角色
     * @param link 实际要检查的安全角色
     */
    public void addRoleMapping(String role, String link);


    /**
     * 添加一个新的安全角色
     *
     * @param role New security role
     */
    public void addSecurityRole(String role);


    /**
     * 添加一个新的servlet映射，以替换指定模式的所有现有映射
     *
     * @param pattern 要映射的URL模式
     * @param name 要执行的对应servlet的名称
     */
    public void addServletMapping(String pattern, String name);


    /**
     * 添加一个指定URI的JSP标签库
     *
     * @param uri URI，这个标签库相对于web.xml文件的地址
     * @param location 标记库描述符的位置
     */
    public void addTaglib(String uri, String location);

    
    /**
     * 添加一个通过主机自动部署重新加载的被监视的资源.
     * Note: 这不会在嵌入式模式下使用.
     * 
     * @param name Path to the resource, relative to docBase
     */
    public void addWatchedResource(String name);
    

    /**
     * 向该上下文识别的集合添加一个新的欢迎文件
     *
     * @param name 新的欢迎文件名称
     */
    public void addWelcomeFile(String name);


    /**
     * 添加LifecycleListener类名
     *
     * @param listener LifecycleListener 类的Java类名
     */
    public void addWrapperLifecycle(String listener);


    /**
     * 添加ContainerListener类名
     *
     * @param listener Java class name of a ContainerListener class
     */
    public void addWrapperListener(String listener);


    /**
     * 创建并返回一个Wrapper实例的工厂方法, Context适当的实现类.
     * 初始化的Wrapper构造方法将被调用, 但没有设置属性.
     */
    public Wrapper createWrapper();


    /**
     * 返回配置的应用监听器类名集合
     */
    public String[] findApplicationListeners();


    /**
     * 返回应用参数集合
     */
    public ApplicationParameter[] findApplicationParameters();


    /**
     * 返回此Web应用程序的安全约束集合。如果没有，则返回零长度数组
     */
    public SecurityConstraint[] findConstraints();


    /**
     * 返回指定HTTP错误代码的错误页面；如果没有，返回<code>null</code>.
     *
     * @param errorCode 查找的异常状态码
     */
    public ErrorPage findErrorPage(int errorCode);


    /**
     * 返回指定Java异常类型的错误页面；如果没有，返回<code>null</code>.
     *
     * @param exceptionType 查找的异常类型
     */
    public ErrorPage findErrorPage(String exceptionType);



    /**
     * 返回所有指定的错误代码和异常类型的定义错误页面集合
     */
    public ErrorPage[] findErrorPages();


    /**
     * 返回指定名称的过滤器;如果没有，返回 <code>null</code>.
     *
     * @param filterName 要查找的过滤器名称
     */
    public FilterDef findFilterDef(String filterName);


    /**
     * 返回所有的过滤器
     */
    public FilterDef[] findFilterDefs();


    /**
     * 返回所有过滤器映射集合
     */
    public FilterMap[] findFilterMaps();


    /**
     * 返回InstanceListener类名集合
     */
    public String[] findInstanceListeners();


    /**
     * 返回映射的指定扩展名的 MIME类型; 如果没有，返回<code>null</code>.
     *
     * @param extension Extension to map to a MIME type
     */
    public String findMimeMapping(String extension);


    /**
     * 返回定义MIME映射的扩展名。如果没有，则返回零长度数组
     */
    public String[] findMimeMappings();


    /**
     * 返回指定的上下文初始化参数名称的值; 如果没有，返回<code>null</code>.
     *
     * @param name 返回参数的名称
     */
    public String findParameter(String name);


    /**
     * 返回所有定义的上下文初始化参数的名称。如果没有定义参数，则返回零长度数组
     */
    public String[] findParameters();


    /**
     * 对于给定的安全角色（应用程序所使用的安全角色），如果有一个角色，返回相应的角色名称（由基础域定义）。否则，返回指定的角色不变
     *
     * @param role 要映射的安全角色
     */
    public String findRoleMapping(String role);


    /**
     * 如果指定的安全角色被定义，返回 <code>true</code>;否则返回 <code>false</code>.
     *
     * @param role 安全角色
     */
    public boolean findSecurityRole(String role);


    /**
     * 返回为该应用程序定义的安全角色。如果没有，则返回零长度数组
     */
    public String[] findSecurityRoles();


    /**
     * 返回指定模式映射的servlet名称;如果没有，返回<code>null</code>.
     *
     * @param pattern 请求映射的模式
     */
    public String findServletMapping(String pattern);


    /**
     * 返回所有servlet映射的模式。如果没有，则返回零长度数组
     */
    public String[] findServletMappings();


    /**
     * 返回指定的HTTP状态代码对应的错误页面路径; 如果没有，返回<code>null</code>.
     *
     * @param status HTTP status code to look up
     */
    public String findStatusPage(int status);


    /**
     * 返回指定错误页面的HTTP状态代码集合。如果没有指定，则返回零长度数组
     */
    public int[] findStatusPages();


    /**
     * 返回指定的标签URI的标签库描述符的位置;如果没有，返回<code>null</code>.
     *
     * @param uri URI, 相对于 web.xml 文件
     */
    public String findTaglib(String uri);


    /**
     * 返回所有标签库的URI。如果没有，则返回零长度数组
     */
    public String[] findTaglibs();


    /**
     * 返回所有被监视的资源. 如果没有，则返回零长度数组
     */
    public String[] findWatchedResources();
    

    /**
     * 如果指定的欢迎文件被指定，返回<code>true</code>; 否则，返回<code>false</code>.
     *
     * @param name Welcome file to verify
     */
    public boolean findWelcomeFile(String name);

    
    /**
     * 返回为此上下文定义的欢迎文件集合。如果没有，则返回零长度数组
     */
    public String[] findWelcomeFiles();


    /**
     * 返回LifecycleListener类名集合
     */
    public String[] findWrapperLifecycles();


    /**
     * 返回ContainerListener类名集合
     */
    public String[] findWrapperListeners();


    /**
     * 如果支持重新加载，则重新加载此Web应用程序
     *
     * @exception IllegalStateException 如果<code>reloadable</code>属性被设置为<code>false</code>.
     */
    public void reload();


    /**
     * 移除指定的监听器
     *
     * @param listener 要删除的监听器的Java 类名
     */
    public void removeApplicationListener(String listener);


    /**
     * 移除指定的应用参数
     *
     * @param name 要删除的应用程序参数的名称
     */
    public void removeApplicationParameter(String name);


    /**
     * 删除指定的安全约束
     *
     * @param constraint 要删除的约束
     */
    public void removeConstraint(SecurityConstraint constraint);


    /**
     * 移除指定错误编码或Java异常对应的错误页面；如果没有，什么都不做
     *
     * @param errorPage 要删除的错误页面定义
     */
    public void removeErrorPage(ErrorPage errorPage);


    /**
     * 移除指定过滤器定义;如果没有，什么都不做
     *
     * @param filterDef 要删除的过滤器定义
     */
    public void removeFilterDef(FilterDef filterDef);


    /**
     * 删除过滤器器映射
     *
     * @param filterMap 要删除的筛选器映射
     */
    public void removeFilterMap(FilterMap filterMap);


    /**
     * 移除指定类名的InstanceListener
     *
     * @param listener 要删除的InstanceListener类的类名
     */
    public void removeInstanceListener(String listener);


    /**
     * 删除指定扩展名的MIME映射；如果不存在，将不采取任何操作
     *
     * @param extension 扩展名
     */
    public void removeMimeMapping(String extension);


    /**
     * 移除指定名称的上下文初始化参数；如果没有，不采取任何操作
     *
     * @param name 要移除的参数的名称
     */
    public void removeParameter(String name);


    /**
     * 删除指定名称的任何安全角色引用
     *
     * @param role 安全角色
     */
    public void removeRoleMapping(String role);


    /**
     * 删除指定名称的安全角色
     *
     * @param role 要删除的安全角色
     */
    public void removeSecurityRole(String role);


    /**
     * 删除指定模式的任何servlet映射;如果没有，不采取任何操作
     *
     * @param pattern 要删除的映射的URL模式
     */
    public void removeServletMapping(String pattern);


    /**
     * 移除指定URI的标签库地址
     *
     * @param uri URI, 相对于web.xml 文件
     */
    public void removeTaglib(String uri);

    
    /**
     * 删除指定名称的被监视的资源名称
     * 
     * @param name 要删除的被监视资源的名称
     */
    public void removeWatchedResource(String name);
    

    /**
     * 从该上下文识别的列表中删除指定的欢迎文件名
     *
     * @param name 要删除的欢迎文件的名称
     */
    public void removeWelcomeFile(String name);


    /**
     * 删除指定名称的LifecycleListener
     *
     * @param listener Class name of a LifecycleListener class to be removed
     */
    public void removeWrapperLifecycle(String listener);


    /**
     * 移除指定名称的ContainerListener
     *
     * @param listener Class name of a ContainerListener class to be removed
     */
    public void removeWrapperListener(String listener);


    /**
     * 获取server.xml <context>属性的 xmlNamespaceAware.
     * @return true if namespace awarenes is enabled.
     */
    public boolean getXmlNamespaceAware();


    /**
     * 获取server.xml <context>属性的xmlValidation.
     * @return true if validation is enabled.
     */
    public boolean getXmlValidation();


    /**
     * 设置解析XML实例时使用的XML解析器的验证特性
     * 
     * @param xmlValidation true 启用XML实例验证
     */
    public void setXmlValidation(boolean xmlValidation);


   /**
     * 设置解析XML实例时使用的XML解析器的名称空间感知特性
     * 
     * @param xmlNamespaceAware true 启用名称空间特性
     */
    public void setXmlNamespaceAware(boolean xmlNamespaceAware);

    /**
     * 设置XML解析器解析tld文件时使用的验证功能.
     *  
     * @param tldValidation true 启用XML实例验证
     */
    public void setTldValidation(boolean tldValidation);


    /**
     * 获取server.xml <context>属性的webXmlValidation.
     * 
     * @return true if validation is enabled.
     */
    public boolean getTldValidation();


    /**
     * 获取server.xml <host>属性的xmlNamespaceAware.
     * 
     * @return true if namespace awarenes is enabled.
     */
    public boolean getTldNamespaceAware();


    /**
     * 设置解析XML实例时使用的XML解析器的名称空间感知特性
     * 
     * @param tldNamespaceAware true 启用名称空间感知功能
     */
    public void setTldNamespaceAware(boolean tldNamespaceAware);


}

