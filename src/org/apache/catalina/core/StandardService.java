package org.apache.catalina.core;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.ServerFactory;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.modeler.Registry;

/**
 * <code>Service</code>接口的标准实现类.
 * 关联的Container一般是一个Engine实例, 但这不是必需的.
 */
public class StandardService implements Lifecycle, Service, MBeanRegistration {
    private static Log log = LogFactory.getLog(StandardService.class);
   

    // ----------------------------------------------------- Instance Variables


    /**
     * 描述信息.
     */
    private static final String info =
        "org.apache.catalina.core.StandardService/1.0";


    /**
     * 这个service的名称.
     */
    private String name = null;


    /**
     * 生命周期事件支持
     */
    private LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * 属于这个Service的<code>Server</code>
     */
    private Server server = null;

    /**
     * 是否已启动?
     */
    private boolean started = false;


    /**
     * 属性修改支持
     */
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * 关联的Connectors集合.
     */
    protected Connector connectors[] = new Connector[0];


    /**
     * 关联的Container. (如果是org.apache.catalina.startup.Embedded 的子类, 这是最近添加的引擎.)
     */
    protected Container container = null;


    /**
     * 是否已经初始化?
     */
    protected boolean initialized = false;


    // ------------------------------------------------------------- Properties


    /**
     * 返回处理请求的<code>Container</code>.
     */
    public Container getContainer() {
        return (this.container);
    }


    /**
     * 设置处理请求的<code>Container</code>.
     *
     * @param container The new Container
     */
    public void setContainer(Container container) {

        Container oldContainer = this.container;
        if ((oldContainer != null) && (oldContainer instanceof Engine))
            ((Engine) oldContainer).setService(null);
        this.container = container;
        if ((this.container != null) && (this.container instanceof Engine))
            ((Engine) this.container).setService(this);
        if (started && (this.container != null) &&
            (this.container instanceof Lifecycle)) {
            try {
                ((Lifecycle) this.container).start();
            } catch (LifecycleException e) {
                ;
            }
        }
        synchronized (connectors) {
            for (int i = 0; i < connectors.length; i++)
                connectors[i].setContainer(this.container);
        }
        if (started && (oldContainer != null) &&
            (oldContainer instanceof Lifecycle)) {
            try {
                ((Lifecycle) oldContainer).stop();
            } catch (LifecycleException e) {
                ;
            }
        }
        // Report this property change to interested listeners
        support.firePropertyChange("container", oldContainer, this.container);
    }

    public ObjectName getContainerName() {
        if( container instanceof ContainerBase ) {
            return ((ContainerBase)container).getJmxName();
        }
        return null;
    }


    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 返回这个Service的名称.
     */
    public String getName() {
        return (this.name);
    }


    /**
     * 设置这个Service的名称.
     *
     * @param name The new service name
     */
    public void setName(String name) {
        this.name = name;
    }


    /**
     * 返回关联的<code>Server</code>.
     */
    public Server getServer() {
        return (this.server);
    }


    /**
     * 设置关联的<code>Server</code>.
     *
     * @param server The server that owns this Service
     */
    public void setServer(Server server) {
        this.server = server;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个新的Connector到定义的Connector集合, 并将其关联到Service的Container.
     *
     * @param connector The Connector to be added
     */
    public void addConnector(Connector connector) {

        synchronized (connectors) {
            connector.setContainer(this.container);
            connector.setService(this);
            Connector results[] = new Connector[connectors.length + 1];
            System.arraycopy(connectors, 0, results, 0, connectors.length);
            results[connectors.length] = connector;
            connectors = results;

            if (initialized) {
                try {
                    connector.initialize();
                } catch (LifecycleException e) {
                    log.error("Connector.initialize", e);
                }
            }

            if (started && (connector instanceof Lifecycle)) {
                try {
                    ((Lifecycle) connector).start();
                } catch (LifecycleException e) {
                    log.error("Connector.start", e);
                }
            }
            // Report this property change to interested listeners
            support.firePropertyChange("connector", null, connector);
        }

    }

    public ObjectName[] getConnectorNames() {
        ObjectName results[] = new ObjectName[connectors.length];
        for( int i=0; i<results.length; i++ ) {
            // if it's a coyote connector
            //if( connectors[i] instanceof CoyoteConnector ) {
            //    results[i]=((CoyoteConnector)connectors[i]).getJmxName();
            //}
        }
        return results;
    }


    /**
     * 添加一个属性修改监听器.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {

        support.addPropertyChangeListener(listener);

    }


    /**
     * 查找并返回关联的Connector集合.
     */
    public Connector[] findConnectors() {
        return (connectors);
    }


    /**
     * 移除指定的Connector.
     * 移除的Connector也将从Container去除关联.
     *
     * @param connector The Connector to be removed
     */
    public void removeConnector(Connector connector) {

        synchronized (connectors) {
            int j = -1;
            for (int i = 0; i < connectors.length; i++) {
                if (connector == connectors[i]) {
                    j = i;
                    break;
                }
            }
            if (j < 0)
                return;
            if (started && (connectors[j] instanceof Lifecycle)) {
                try {
                    ((Lifecycle) connectors[j]).stop();
                } catch (LifecycleException e) {
                    log.error("Connector.stop", e);
                }
            }
            connectors[j].setContainer(null);
            connector.setService(null);
            int k = 0;
            Connector results[] = new Connector[connectors.length - 1];
            for (int i = 0; i < connectors.length; i++) {
                if (i != j)
                    results[k++] = connectors[i];
            }
            connectors = results;
            // Report this property change to interested listeners
            support.firePropertyChange("connector", connector, null);
        }
    }


    /**
     * 移除一个属性修改监听器.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    public String toString() {
        StringBuffer sb = new StringBuffer("StandardService[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * 添加一个LifecycleEvent监听器.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }


    /**
     * 获取关联的生命周期监听器. 如果没有, 返回零长度数组.
     */
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }


    /**
     * 移除一个LifecycleEvent监听器.
     *
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }


    /**
     * 这个方法应该第一个调用. 
     * 它将发送一个START_EVENT类型的LifecycleEvent到所有注册的监听器.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    public void start() throws LifecycleException {

        // Validate and update our current component state
        if (log.isInfoEnabled() && started) {
            log.info(sm.getString("standardService.start.started"));
        }
        
        if( ! initialized )
            init(); 

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_START_EVENT, null);
        if(log.isInfoEnabled())
            log.info(sm.getString("standardService.start.name", this.name));
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        // Start our defined Container first
        if (container != null) {
            synchronized (container) {
                if (container instanceof Lifecycle) {
                    ((Lifecycle) container).start();
                }
            }
        }

        // Start our defined Connectors second
        synchronized (connectors) {
            for (int i = 0; i < connectors.length; i++) {
                if (connectors[i] instanceof Lifecycle)
                    ((Lifecycle) connectors[i]).start();
            }
        }

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(AFTER_START_EVENT, null);
    }


    /**
     * 这个方法应该最后一个调用.
     * 它将发送一个STOP_EVENT类型的LifecycleEvent到所有注册的监听器.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public void stop() throws LifecycleException {

        // Validate and update our current component state
        if (!started) {
            return;
        }

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_STOP_EVENT, null);

        // Stop our defined Connectors first
        synchronized (connectors) {
            for (int i = 0; i < connectors.length; i++) {
                connectors[i].pause();
            }
        }

        // Heuristic: 休眠一段时间以确保连接器的暂停
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // Ignore
        }

        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        if(log.isInfoEnabled())
            log.info
                (sm.getString("standardService.stop.name", this.name));
        started = false;

        // Stop our defined Container second
        if (container != null) {
            synchronized (container) {
                if (container instanceof Lifecycle) {
                    ((Lifecycle) container).stop();
                }
            }
        }
        // FIXME pero -- 为什么首先停止容器? 保持连接发送请求! 
        // 首先停止定义的连接器
        synchronized (connectors) {
            for (int i = 0; i < connectors.length; i++) {
                if (connectors[i] instanceof Lifecycle)
                    ((Lifecycle) connectors[i]).stop();
            }
        }

        if( oname==controller ) {
            // 注册了init().
            // 这应该是典型的例子 - 这个对象只不过是向后兼容, 不应该加载它
            Registry.getRegistry(null, null).unregisterComponent(oname);
        }
        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(AFTER_STOP_EVENT, null);
    }


    /**
     * 调用预启动初始化. 这用于允许连接器在UNIX操作环境下绑定到受限端口.
     */
    public void initialize()
            throws LifecycleException
    {
        // 服务不应该与嵌入式一起使用, 所以没关系
        if (initialized) {
            if(log.isInfoEnabled())
                log.info(sm.getString("standardService.initialize.initialized"));
            return;
        }
        initialized = true;

        if( oname==null ) {
            try {
                // Hack - 服务器应该废弃...
                Container engine=this.getContainer();
                domain=engine.getName();
                oname=new ObjectName(domain + ":type=Service,serviceName="+name);
                this.controller=oname;
                Registry.getRegistry(null, null)
                    .registerComponent(this, oname, null);
            } catch (Exception e) {
                log.error(sm.getString("standardService.register.failed",domain),e);
            }
            
            
        }
        if( server==null ) {
            // 在服务器注册
            // HACK: ServerFactory 应该移除...
            ServerFactory.getServer().addService(this);
        }
               

        // 初始化定义的连接器
        synchronized (connectors) {
                for (int i = 0; i < connectors.length; i++) {
                    connectors[i].initialize();
                }
        }
    }
    
    public void destroy() throws LifecycleException {
        if( started ) stop();
        // FIXME 这里应该是注销 -- stop doing that ?
    }

    public void init() {
        try {
            initialize();
        } catch( Throwable t ) {
            log.error(sm.getString("standardService.initialize.failed",domain),t);
        }
    }

    protected String type;
    protected String domain;
    protected String suffix;
    protected ObjectName oname;
    protected ObjectName controller;
    protected MBeanServer mserver;

    public ObjectName getObjectName() {
        return oname;
    }

    public String getDomain() {
        return domain;
    }

    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        oname=name;
        mserver=server;
        domain=name.getDomain();
        return name;
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() throws Exception {
    }

    public void postDeregister() {
    }
}
