package org.apache.catalina;

import org.apache.catalina.deploy.NamingResources;

/**
 * <code>Server</code>代表整个Catalina servlet容器
 * 它的属性代表servlet容器的整体特性. <code>Server</code>可能包含一个或多个<code>Services</code>, 以及顶级的命名资源集
 * <p>
 * 通常, 该接口的实现类也将实现<code>Lifecycle</code>, 因此当<code>start()</code>和
 * <code>stop()</code>方法被调用,所有定义的<code>Services</code>也将启动和关闭.
 * <p>
 * 在两者之间，实现必须在<code>port</code>属性指定的端口号上打开服务器套接字。
 * 当连接被接受时,读取第一行，并与指定的关闭命令进行比较.
 * 如果命令匹配，将关闭服务器
 * <p>
 * <strong>NOTE</strong> - 接口的所有实现类应该注册单例的实例到<code>ServerFactory</code>
 */
public interface Server {

    // ------------------------------------------------------------- Properties

    /**
     * 返回实现类描述信息和版本号, 格式为
     * <code>&lt;描述&gt;/&lt;版本号&gt;</code>.
     */
    public String getInfo();


    /**
     * 返回全局命名资源
     */
    public NamingResources getGlobalNamingResources();


    /**
     * 设置全局命名资源
     * 
     * @param globalNamingResources The new global naming resources
     */
    public void setGlobalNamingResources(NamingResources globalNamingResources);


    /**
     * 返回为关闭命令监听的端口号
     */
    public int getPort();


    /**
     * 设置为关闭命令监听的端口号
     *
     * @param port The new port number
     */
    public void setPort(int port);


    /**
     * 返回等待的关闭命令字符串
     */
    public String getShutdown();


    /**
     * 设置等待的关闭命令字符串
     *
     * @param shutdown The new shutdown command
     */
    public void setShutdown(String shutdown);


    // --------------------------------------------------------- Public Methods

    public void addService(Service service);


    /**
     * 等待接收到正确的关机命令，然后返回
     */
    public void await();


    /**
     * 返回指定的Service;或者<code>null</code>.
     *
     * @param name Name of the Service to be returned
     */
    public Service findService(String name);


    /**
     * 返回所有定义的Services
     */
    public Service[] findServices();


    /**
     * 移除指定的Service
     *
     * @param service The Service to be removed
     */
    public void removeService(Service service);

    /**
     * 调用预启动初始化. 这用于允许连接器在UNIX操作环境下绑定到受限端口.
     *
     * @exception LifecycleException 如果此服务器已初始化.
     */
    public void initialize() throws LifecycleException;
}
