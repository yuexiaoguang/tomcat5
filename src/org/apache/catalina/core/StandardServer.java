package org.apache.catalina.core;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.AccessControlException;
import java.util.Random;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.catalina.ServerFactory;
import org.apache.catalina.Service;
import org.apache.catalina.deploy.NamingResources;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.modeler.Registry;
import org.apache.tomcat.util.buf.StringCache;

/**
 * <b>Server</b>接口的标准实现类, 在部署和启动Catalina时，可用(不是必须的).
 */
public final class StandardServer implements Lifecycle, Server, MBeanRegistration {
    private static Log log = LogFactory.getLog(StandardServer.class);
   
    // -------------------------------------------------------------- Constants

    /**
     * ServerLifecycleListener类名.
     */
    private static String SERVER_LISTENER_CLASS_NAME = "org.apache.catalina.mbeans.ServerLifecycleListener";

    // ------------------------------------------------------------ Constructor

    public StandardServer() {
        super();
        ServerFactory.setServer(this);

        globalNamingResources = new NamingResources();
        globalNamingResources.setContainer(this);

        if (isUseNaming()) {
            if (namingContextListener == null) {
                namingContextListener = new NamingContextListener();
                addLifecycleListener(namingContextListener);
            }
        }
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 全局命名资源上下文
     */
    private javax.naming.Context globalNamingContext = null;


    /**
     * 全局命名资源.
     */
    private NamingResources globalNamingResources = null;


    /**
     * 描述信息
     */
    private static final String info =
        "org.apache.catalina.core.StandardServer/1.0";


    /**
     * 生命周期事件支持.
     */
    private LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * 命名上下文监听器.
     */
    private NamingContextListener namingContextListener = null;


    /**
     * 等待关机命令的端口号.
     */
    private int port = 8005;


    /**
     * 随机数发生器,只有在关闭命令字符串长于1024个字符时，才会使用.
     */
    private Random random = null;


    /**
     * 这个Server关联的Services.
     */
    private Service services[] = new Service[0];


    /**
     * 正在寻找的关闭命令字符串.
     */
    private String shutdown = "SHUTDOWN";


    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 是否已经启动?
     */
    private boolean started = false;


    /**
     * 是否已经初始化?
     */
    private boolean initialized = false;


    /**
     * 属性修改支持.
     */
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);


    // ------------------------------------------------------------- Properties


    /**
     * 返回全局命名资源上下文.
     */
    public javax.naming.Context getGlobalNamingContext() {
        return (this.globalNamingContext);
    }


    /**
     * 设置全局命名资源上下文.
     *
     * @param globalNamingContext The new global naming resource context
     */
    public void setGlobalNamingContext(javax.naming.Context globalNamingContext) {
        this.globalNamingContext = globalNamingContext;
    }


    /**
     * 返回全局命名资源
     */
    public NamingResources getGlobalNamingResources() {
        return (this.globalNamingResources);
    }


    /**
     * 设置全局命名资源.
     *
     * @param globalNamingResources The new global naming resources
     */
    public void setGlobalNamingResources(NamingResources globalNamingResources) {

        NamingResources oldGlobalNamingResources =
            this.globalNamingResources;
        this.globalNamingResources = globalNamingResources;
        this.globalNamingResources.setContainer(this);
        support.firePropertyChange("globalNamingResources",
                                   oldGlobalNamingResources,
                                   this.globalNamingResources);
    }


    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 返回为关闭命令监听的端口号.
     */
    public int getPort() {
        return (this.port);
    }


    /**
     * 设置为关闭命令监听的端口号.
     *
     * @param port The new port number
     */
    public void setPort(int port) {
        this.port = port;
    }


    /**
     * 返回正在等待的关闭命令字符串.
     */
    public String getShutdown() {
        return (this.shutdown);
    }


    /**
     * 设置正在等待的关闭命令字符串.
     *
     * @param shutdown The new shutdown command
     */
    public void setShutdown(String shutdown) {
        this.shutdown = shutdown;
    }


    // --------------------------------------------------------- Server Methods


    /**
     * 添加一个新的Service到定义的Service集合.
     *
     * @param service The Service to be added
     */
    public void addService(Service service) {

        service.setServer(this);

        synchronized (services) {
            Service results[] = new Service[services.length + 1];
            System.arraycopy(services, 0, results, 0, services.length);
            results[services.length] = service;
            services = results;

            if (initialized) {
                try {
                    service.initialize();
                } catch (LifecycleException e) {
                    log.error(e);
                }
            }

            if (started && (service instanceof Lifecycle)) {
                try {
                    ((Lifecycle) service).start();
                } catch (LifecycleException e) {
                    ;
                }
            }
            // Report this property change to interested listeners
            support.firePropertyChange("service", null, service);
        }
    }


    /**
     * 等待接收到正确的关机命令，然后返回.
     */
    public void await() {

        // Set up a server socket to wait on
        ServerSocket serverSocket = null;
        try {
            serverSocket =
                new ServerSocket(port, 1,
                                 InetAddress.getByName("127.0.0.1"));
        } catch (IOException e) {
            log.error("StandardServer.await: create[" + port
                               + "]: ", e);
            System.exit(1);
        }

        // 循环等待连接和有效命令
        while (true) {

            // 等待下一个连接
            Socket socket = null;
            InputStream stream = null;
            try {
                socket = serverSocket.accept();
                socket.setSoTimeout(10 * 1000);  // Ten seconds
                stream = socket.getInputStream();
            } catch (AccessControlException ace) {
                log.warn("StandardServer.accept security exception: "
                                   + ace.getMessage(), ace);
                continue;
            } catch (IOException e) {
                log.error("StandardServer.await: accept: ", e);
                System.exit(1);
            }

            // 从套接字读取一组字符
            StringBuffer command = new StringBuffer();
            int expected = 1024; // Cut off to avoid DoS attack
            while (expected < shutdown.length()) {
                if (random == null)
                    random = new Random(System.currentTimeMillis());
                expected += (random.nextInt() % 1024);
            }
            while (expected > 0) {
                int ch = -1;
                try {
                    ch = stream.read();
                } catch (IOException e) {
                    log.warn("StandardServer.await: read: ", e);
                    ch = -1;
                }
                if (ch < 32)  // 控制字符或EOF终止循环
                    break;
                command.append((char) ch);
                expected--;
            }

            // 关闭套接字
            try {
                socket.close();
            } catch (IOException e) {
                ;
            }

            // 与命令字符串匹配
            boolean match = command.toString().equals(shutdown);
            if (match) {
                break;
            } else
                log.warn("StandardServer.await: Invalid command '" +
                                   command.toString() + "' received");

        }

        // 关闭服务器套接字并返回
        try {
            serverSocket.close();
        } catch (IOException e) {
            ;
        }
    }


    /**
     * 返回指定的Service; 或者<code>null</code>.
     *
     * @param name 返回的Service的名称
     */
    public Service findService(String name) {
        if (name == null) {
            return (null);
        }
        synchronized (services) {
            for (int i = 0; i < services.length; i++) {
                if (name.equals(services[i].getName())) {
                    return (services[i]);
                }
            }
        }
        return (null);
    }


    /**
     * 返回这个Server中定义的所有Service.
     */
    public Service[] findServices() {
        return (services);
    }
    
    /** 
     * 返回 JMX 服务名称.
     */
    public ObjectName[] getServiceNames() {
        ObjectName onames[]=new ObjectName[ services.length ];
        for( int i=0; i<services.length; i++ ) {
            onames[i]=((StandardService)services[i]).getObjectName();
        }
        return onames;
    }


    /**
     * 移除指定的Service.
     *
     * @param service The Service to be removed
     */
    public void removeService(Service service) {

        synchronized (services) {
            int j = -1;
            for (int i = 0; i < services.length; i++) {
                if (service == services[i]) {
                    j = i;
                    break;
                }
            }
            if (j < 0)
                return;
            if (services[j] instanceof Lifecycle) {
                try {
                    ((Lifecycle) services[j]).stop();
                } catch (LifecycleException e) {
                    ;
                }
            }
            int k = 0;
            Service results[] = new Service[services.length - 1];
            for (int i = 0; i < services.length; i++) {
                if (i != j)
                    results[k++] = services[i];
            }
            services = results;
            // Report this property change to interested listeners
            support.firePropertyChange("service", service, null);
        }
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加属性更改监听器.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * 移除属性更改监听器.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    public String toString() {
        StringBuffer sb = new StringBuffer("StandardServer[");
        sb.append(getPort());
        sb.append("]");
        return (sb.toString());
    }


    /**
     * 将这个<code>Server</code>的配置信息写入server.xml配置文件.
     *
     * @exception javax.management.InstanceNotFoundException 如果找不到托管资源对象
     * @exception javax.management.MBeanException 如果对象的初始化器抛出异常，则不支持持久性
     * @exception javax.management.RuntimeOperationsException 如果持久性机制报告异常
     */
    public synchronized void storeConfig() throws Exception {
        ObjectName sname = null;    
        try {
           sname = new ObjectName("Catalina:type=StoreConfig");
           if(mserver.isRegistered(sname)) {
               mserver.invoke(sname, "storeConfig", null, null);            
           } else
               log.error("StoreConfig mbean not registered" + sname);
        } catch (Throwable t) {
            log.error(t);
        }
    }


    /**
     * 将这个<code>Context</code>的配置信息写入指定的配置文件.
     *
     * @exception javax.management.InstanceNotFoundException 如果找不到托管资源对象
     * @exception javax.management.MBeanException 如果对象的初始化器抛出异常，则不支持持久性
     * @exception javax.management.RuntimeOperationsException 如果持久性机制报告异常
     */
    public synchronized void storeContext(Context context) throws Exception {
        
        ObjectName sname = null;    
        try {
           sname = new ObjectName("Catalina:type=StoreConfig");
           if(mserver.isRegistered(sname)) {
               mserver.invoke(sname, "store",
                   new Object[] {context}, 
                   new String [] { "java.lang.String"});
           } else
               log.error("StoreConfig mbean not registered" + sname);
        } catch (Throwable t) {
            log.error(t);
        }
 
    }


    /**
     * 如果使用命名，返回true.
     */
    private boolean isUseNaming() {
        boolean useNaming = true;
        // 读取 "catalina.useNaming" 环境变量
        String useNamingProperty = System.getProperty("catalina.useNaming");
        if ((useNamingProperty != null)
            && (useNamingProperty.equals("false"))) {
            useNaming = false;
        }
        return useNaming;
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * 添加一个LifecycleEvent监听器
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }


    /**
     * 获取这个生命周期内的所有生命周期监听器.
     * 如果这个Lifecycle没有监听器, 返回零长度数组.
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

        // 验证并更新当前的组件状态
        if (started) {
            log.debug(sm.getString("standardServer.start.started"));
            return;
        }

        // 通知所有 LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_START_EVENT, null);

        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        // Start our defined Services
        synchronized (services) {
            for (int i = 0; i < services.length; i++) {
                if (services[i] instanceof Lifecycle)
                    ((Lifecycle) services[i]).start();
            }
        }
        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(AFTER_START_EVENT, null);
    }


    /**
     * 这将被最后一个调用.
     * 它将发送一个STOP_EVENT类型的LifecycleEvent到所有监听器.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public void stop() throws LifecycleException {

        // Validate and update our current component state
        if (!started)
            return;

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_STOP_EVENT, null);

        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        // Stop our defined Services
        for (int i = 0; i < services.length; i++) {
            if (services[i] instanceof Lifecycle)
                ((Lifecycle) services[i]).stop();
        }
        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(AFTER_STOP_EVENT, null);
    }

    public void init() throws Exception {
        initialize();
    }
    
    /**
     * 调用预启动初始化.
     * 这用于允许连接器在UNIX操作环境下绑定到受限端口.
     */
    public void initialize() throws LifecycleException {
        if (initialized) {
                log.info(sm.getString("standardServer.initialize.initialized"));
            return;
        }
        lifecycle.fireLifecycleEvent(INIT_EVENT, null);
        initialized = true;

        if( oname==null ) {
            try {
                oname=new ObjectName( "Catalina:type=Server");
                Registry.getRegistry(null, null)
                    .registerComponent(this, oname, null );
            } catch (Exception e) {
                log.error("Error registering ",e);
            }
        }
        
        // Register global String cache
        try {
            ObjectName oname2 = 
                new ObjectName(oname.getDomain() + ":type=StringCache");
            Registry.getRegistry(null, null)
                .registerComponent(new StringCache(), oname2, null );
        } catch (Exception e) {
            log.error("Error registering ",e);
        }

        // Initialize our defined Services
        for (int i = 0; i < services.length; i++) {
            services[i].initialize();
        }
    }
    
    protected String type;
    protected String domain;
    protected String suffix;
    protected ObjectName oname;
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
