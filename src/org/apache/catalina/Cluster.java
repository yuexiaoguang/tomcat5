package org.apache.catalina;

import java.io.IOException;
import java.net.URL;

/**
 * <b>Cluster</b>作为本地主机的集群客户机/服务器，可以使用不同的集群实现来支持在集群中进行不同方式的通信.
 * 一个Cluster实现类负责在集群中建立通信方式, 也提供"ClientApplications" 使用<code>ClusterSender</code>,
 * 当在Cluster 和<code>ClusterInfo</code>发送信息的时候使用, 当在Cluster接收信息的时候使用.
 */
public interface Cluster {

    // ------------------------------------------------------------- Properties

    /**
     * 返回描述信息和相应版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo();

    /**
     * 返回集群的名称, 此服务器当前配置运行所在的.
     *
     * @return 这个服务器关联的集群名称
     */
    public String getClusterName();

    /**
     * 设置要加入的集群名称, 如果没有此名称的集群，则创建一个.
     *
     * @param clusterName 要加入的集群名称
     */
    public void setClusterName(String clusterName);

    /**
     * 设置关联的Container
     *
     * @param container The Container to use
     */
    public void setContainer(Container container);

    /**
     * 获取关联的Container
     *
     * @return The Container associated with our Cluster
     */
    public Container getContainer();

    /**
     * 设置协议参数
     *
     * @param protocol The protocol used by the cluster
     * @deprecated
     */
    public void setProtocol(String protocol);

    /**
     * 获取协议参数
     *
     * @return The protocol
     * @deprecated
     */
    public String getProtocol();

    // --------------------------------------------------------- Public Methods

    /**
     * 创建一个新的管理器，该管理器将使用此集群复制其会话.
     *
     * @param name 管理器关联的应用名称(key)
     */
    public Manager createManager(String name);

    // --------------------------------------------------------- Cluster Wide Deployments
    
    
    /**
     * 执行周期任务, 例如重新加载, 等.
     * 该方法将被在该容器的类加载上下文中调用. 以外的异常将被捕获和记录.
     */
    public void backgroundProcess();


    /**
     * 启动Web应用程序, 附加到集群中所有其他节点的指定上下文路径中.
     * 只有在Web应用程序不运行时, 才启动它.
     *
     * @param contextPath 要启动的应用程序的上下文路径
     *
     * @exception IllegalArgumentException 如果指定的上下文路径是错误的(必须是 "" 或以斜杠开头)
     * @exception IllegalArgumentException 如果指定的上下文路径未标识当前已安装的Web应用程序
     * @exception IOException 如果在启动期间出现输入/输出错误
     * @deprecated
     */
    public void startContext(String contextPath) throws IOException;


    /**
     * 安装一个新的Web应用程序, 其Web应用程序归档文件在指定的URL中, 通过指定的上下文路径进入这个容器.
     * "" (空字符串)上下文路径应用于此容器的根应用程序. 否则, 上下文路径必须以斜杠开头.
     * <p>
     * 如果此应用程序成功安装, 一个<code>PRE_INSTALL_EVENT</code>类型的ContainerEvent将被发送到注册的监听器,
     * 在关联的Context启动之前, 并且一个<code>INSTALL_EVENT</code>类型的ContainerEvent将被发送到所有注册的监听器,
     * 在关联的Context启动之后, 并将新创建的<code>Context</code>作为一个参数.
     *
     * @param contextPath 应该安装此应用程序的上下文路径(必须唯一)
     * @param war "jar:"类型的URL指向一个WAR 文件; 或者"file:"类型的指向一个解压后的目录, 其中包含要安装的Web应用程序
     *
     * @exception IllegalArgumentException 如果指定的上下文路径是错误的(必须是 "" 或以斜杠开头)
     * @exception IllegalStateException 如果指定的上下文路径已经连接到其他Web应用程序
     * @deprecated
     */
    public void installContext(String contextPath, URL war);

    /**
     * 关闭Web应用程序, 附加到指定的上下文路径. 仅关闭正在运行的Web应用程序.
     *
     * @param contextPath 要关闭的应用程序的上下文路径
     *
     * @exception IllegalArgumentException 如果指定的上下文路径是错误的(必须是 "" 或以斜杠开头)
     * @exception IllegalArgumentException 如果指定的上下文路径未标识当前已安装的Web应用程序
     * @exception IOException 如果在停止Web应用程序时发生输入/输出错误
     * @deprecated
     */
    public void stop(String contextPath) throws IOException;


}
