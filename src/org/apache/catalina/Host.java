package org.apache.catalina;

/**
 * <b>Host</b>是一个容器，代表在Catalina servlet引擎的虚拟主机 . 
 * 它在以下类型的场景中很有用:
 * <ul>
 * <li>你想使用拦截器，查看由这个特定虚拟主机处理的每个请求
 * <li>你想运行Catalina在一个独立的HTTP连接器中，但是仍然想支持多个虚拟主机
 * </ul>
 * 通常, 你不会使用Host，当部署 Catalina连接到一个web服务器(例如 Apache), 
 * 因为连接器将利用Web服务器的设施来确定应该使用哪些上下文（甚至哪一个Wrapper）来处理这个请求
 * <p>
 * Host的父容器通常是一个Engine, 也可能是其他一些实现类, 又或者可以省略
 * <p>
 * Host的子容器通常是Context的实现类 (表示单个servlet上下文)
 */
public interface Host extends Container {

    // ----------------------------------------------------- Manifest Constants

    /**
     * 当<code>addAlias()</code>方法添加一个新别名的时候， ContainerEvent事件类型将被发送
     */
    public static final String ADD_ALIAS_EVENT = "addAlias";


    /**
     * 当<code>removeAlias()</code>方法移除一个旧别名的时候， ContainerEvent事件类型将被发送
     */
    public static final String REMOVE_ALIAS_EVENT = "removeAlias";


    // ------------------------------------------------------------- Properties


    /**
     * 返回应用程序根目录 。可能是相对路径、绝对路径、URL
     */
    public String getAppBase();


    /**
     * 设置应用程序根目录 。可能是相对路径、绝对路径、URL
     *
     * @param appBase The new application root
     */
    public void setAppBase(String appBase);


    /**
     * 是否自动部署. 如果是true，表明该主机的子应用应该被自动发现并部署
     */
    public boolean getAutoDeploy();


    /**
     * 设置自动部署
     * 
     * @param autoDeploy 自动部署标记
     */
    public void setAutoDeploy(boolean autoDeploy);


    /**
     * 返回Web应用程序新的上下文配置类的java类的名称
     */
    public String getConfigClass();

    
    /**
     * 设置Web应用程序新的上下文配置类的java类的名称
     *
     * @param configClass 新的上下文配置类
     */
    public void setConfigClass(String configClass);

        
    /**
     * 启动时是否自动部署.
     * 如果是true, 这表明该主机的子应用能够自动发现并部署
     */
    public boolean getDeployOnStartup();


    /**
     * 启动时是否自动部署.
     * 
     * @param deployOnStartup The new deploy on startup flag
     */
    public void setDeployOnStartup(boolean deployOnStartup);


    /**
     * 返回此容器表示的虚拟主机的规范的、完全限定的名称
     */
    public String getName();


    /**
     * 设置此容器代表的虚拟主机的规范的、完全限定的名称
     *
     * @param name 虚拟主机名称
     *
     * @exception IllegalArgumentException 如果名称是null
     */
    public void setName(String name);


    /**
     * 获取server.xml <host> 属性的 xmlNamespaceAware.
     * 
     * @return true 如果启用命名空间意识
     */
    public boolean getXmlNamespaceAware();


    /**
     * 获取server.xml <host> 属性的 xmlValidation.
     * @return true 如果启用验证
     */
    public boolean getXmlValidation();


    /**
     * 设置解析XML实例时使用的XML解析器的验证功能
     * 
     * @param xmlValidation true启用XML实例验证
     */
    public void setXmlValidation(boolean xmlValidation);


   /**
     * 设置解析XML实例时使用的XML解析器的命名空间感知特性.
     * 
     * @param xmlNamespaceAware true 启用命名空间感知特性
     */
    public void setXmlNamespaceAware(boolean xmlNamespaceAware);


    // --------------------------------------------------------- Public Methods


    /**
     * 添加映射到同一主机的别名
     *
     * @param alias The alias to be added
     */
    public void addAlias(String alias);


    /**
     * 返回别名集合. 如果没有,返回零长度的数组
     */
    public String[] findAliases();


    /**
     * 返回Context，用于处理指定的host相对请求路径；如果没有，返回<code>null</code>
     *
     * @param uri Request URI to be mapped
     */
    public Context map(String uri);


    /**
     * 移除指定的别名
     *
     * @param alias Alias name to be removed
     */
    public void removeAlias(String alias);


}
