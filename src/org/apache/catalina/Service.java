package org.apache.catalina;

import org.apache.catalina.connector.Connector;


/**
 * <strong>Service</strong>是一组或多组<strong>Connectors</strong>共享单个<strong>Container</strong>来处理它们各自的请求.
 * 例如，这种安排允许一个非SSL和SSL连接器共享相同数量的Web应用程序
 * <p>
 * 一个JVM可以包含多个Service 实例; 然而，他们是完全独立的，只共享基本JVM设备和类文件
 */
public interface Service {


    // ------------------------------------------------------------- Properties


    /**
     * 返回处理请求的<code>Container</code>
     */
    public Container getContainer();


    /**
     * 设置处理请求的<code>Container</code>
     *
     * @param container The new Container
     */
    public void setContainer(Container container);


    /**
     * 返回Service实现类的描述信息和版本号, 格式为
     * <code>&lt;描述&gt;/&lt;ver版本号sion&gt;</code>.
     */
    public String getInfo();


    /**
     * 返回Service的名称
     */
    public String getName();


    /**
     * 设置Service的名称
     *
     * @param name The new service name
     */
    public void setName(String name);


    /**
     * 返回关联的<code>Server</code>
     */
    public Server getServer();


    /**
     * 设置关联的<code>Server</code>
     *
     * @param server The server that owns this Service
     */
    public void setServer(Server server);

    
    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个新的Connector到其数组中，并将它与Service的 Container关联.
     *
     * @param connector The Connector to be added
     */
    public void addConnector(Connector connector);


    /**
     * 查找并返回关联的Connector
     */
    public Connector[] findConnectors();


    /**
     * 移除指定的Connector. 移除的Connector也将不再和Container关联.
     *
     * @param connector The Connector to be removed
     */
    public void removeConnector(Connector connector);

    /**
     * 调用预启动初始化. 这用于允许连接器在UNIX操作环境下绑定到受限端口
     *
     * @exception LifecycleException 如果服务器已经初始化
     */
    public void initialize() throws LifecycleException;

}
