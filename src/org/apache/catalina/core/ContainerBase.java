package org.apache.catalina.core;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.Serializable;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.naming.directory.DirContext;
import javax.servlet.ServletException;

import org.apache.catalina.Cluster;
import org.apache.catalina.Container;
import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Loader;
import org.apache.catalina.Manager;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Realm;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.modeler.Registry;
import org.apache.naming.resources.ProxyDirContext;

/**
 * <b>Container</b>接口的抽象实现类,提供几乎所有实现所需的公共功能. 
 * 继承这个类的类必须实现<code>getInfo()</code>方法, 并可能实现覆盖<code>invoke()</code>方法.
 * <p>
 * 这个抽象基类的所有子类将包括对Pipeline对象的支持，该Pipeline对象定义要接收的每个请求执行的处理 通过这个类的<code>invoke()</code>方法, 
 * 运用“责任链”设计模式. 
 * 子类应该将自己的处理功能封装为<code>Valve</code>, 并通过调用<code>setBasic()</code>将此Valve配置到Pipeline中.
 * <p>
 * 此实现类触发属性更改事件, 根据JavaBeans设计模式, 对于单属性的更改. 
 * 此外，它触发以下<code>ContainerEvent</code>事件，监听使用<code>addContainerListener()</code>注册自己的实例:
 * <table border=1>
 *   <tr>
 *     <th>Type</th>
 *     <th>Data</th>
 *     <th>Description</th>
 *   </tr>
 *   <tr>
 *     <td align=center><code>addChild</code></td>
 *     <td align=center><code>Container</code></td>
 *     <td>Child container added to this Container.</td>
 *   </tr>
 *   <tr>
 *     <td align=center><code>addValve</code></td>
 *     <td align=center><code>Valve</code></td>
 *     <td>Valve added to this Container.</td>
 *   </tr>
 *   <tr>
 *     <td align=center><code>removeChild</code></td>
 *     <td align=center><code>Container</code></td>
 *     <td>Child container removed from this Container.</td>
 *   </tr>
 *   <tr>
 *     <td align=center><code>removeValve</code></td>
 *     <td align=center><code>Valve</code></td>
 *     <td>Valve removed from this Container.</td>
 *   </tr>
 *   <tr>
 *     <td align=center><code>start</code></td>
 *     <td align=center><code>null</code></td>
 *     <td>Container was started.</td>
 *   </tr>
 *   <tr>
 *     <td align=center><code>stop</code></td>
 *     <td align=center><code>null</code></td>
 *     <td>Container was stopped.</td>
 *   </tr>
 * </table>
 * 引发其他事件的子类应该在实现类的类注释中记录它们.
 */
public abstract class ContainerBase
    implements Container, Lifecycle, Pipeline, MBeanRegistration, Serializable {

    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( ContainerBase.class );

    /**
     * 随着这个类的权限执行addChild.
     * addChild可以通过堆栈上的XML解析器调用,
     * 这允许XML解析器拥有比Tomcat更少的特权.
     */
    protected class PrivilegedAddChild implements PrivilegedAction {

        private Container child;

        PrivilegedAddChild(Container child) {
            this.child = child;
        }

        public Object run() {
            addChildInternal(child);
            return null;
        }
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 属于这个Container的子Container, 使用名称作为key.
     */
    protected HashMap children = new HashMap();


    /**
     * 此组件的处理器延迟.
     */
    protected int backgroundProcessorDelay = -1;


    /**
     * 生命周期事件支持.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * 容器事件监听器.
     */
    protected ArrayList listeners = new ArrayList();


    protected Loader loader = null;


    protected Log logger = null;


    protected String logName = null;
    

    protected Manager manager = null;


    protected Cluster cluster = null;

    
    protected String name = null;


    /**
     * 父级 Container.
     */
    protected Container parent = null;


    /**
     * 安装Loader时要配置的父类加载器..
     */
    protected ClassLoader parentClassLoader = null;


    protected Pipeline pipeline = new StandardPipeline(this);


    protected Realm realm = null;


    protected DirContext resources = null;


    /**
     * The string manager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 是否启动?
     */
    protected boolean started = false;

    protected boolean initialized=false;

    /**
     * 属性修改支持
     */
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * 后台线程.
     */
    private Thread thread = null;


    /**
     * 后台线程完成信号量.
     */
    private boolean threadDone = false;


    // ------------------------------------------------------------- Properties


    /**
     * 在这个容器和它的子容器的backgroundProcess 方法的执行间隔.
     * 如果它们的延迟值不是负值, 不会调用子容器 (这意味着他们正在使用自己的线程). 将此值设置为正值将导致线程生成. 在等待指定的时间之后, 
     * 该线程将调用这个容器和它的子容器的executePeriodic 方法.
     */
    public int getBackgroundProcessorDelay() {
        return backgroundProcessorDelay;
    }


    /**
     * 设置在这个容器和它的子容器的backgroundProcess 方法的执行间隔.
     * 
     * @param delay backgroundProcess 方法的执行间隔, 秒
     */
    public void setBackgroundProcessorDelay(int delay) {
        backgroundProcessorDelay = delay;
    }


    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return this.getClass().getName();
    }


    /**
     * 返回关联的Loader. 
     * 如果没有关联的Loader, 返回父级Container关联的Loader; 否则返回<code>null</code>.
     */
    public Loader getLoader() {
        if (loader != null)
            return (loader);
        if (parent != null)
            return (parent.getLoader());
        return (null);
    }


    /**
     * 设置Container关联的Loader.
     *
     * @param loader The newly associated loader
     */
    public synchronized void setLoader(Loader loader) {

        // Change components if necessary
        Loader oldLoader = this.loader;
        if (oldLoader == loader)
            return;
        this.loader = loader;

        // Stop the old component if necessary
        if (started && (oldLoader != null) &&
            (oldLoader instanceof Lifecycle)) {
            try {
                ((Lifecycle) oldLoader).stop();
            } catch (LifecycleException e) {
                log.error("ContainerBase.setLoader: stop: ", e);
            }
        }

        // Start the new component if necessary
        if (loader != null)
            loader.setContainer(this);
        if (started && (loader != null) &&
            (loader instanceof Lifecycle)) {
            try {
                ((Lifecycle) loader).start();
            } catch (LifecycleException e) {
                log.error("ContainerBase.setLoader: start: ", e);
            }
        }

        // Report this property change to interested listeners
        support.firePropertyChange("loader", oldLoader, this.loader);
    }


    /**
     * 返回关联的Logger. 
     * 如果没有关联的Logger, 返回父级关联的Logger; 否则返回<code>null</code>.
     */
    public Log getLogger() {
        if (logger != null)
            return (logger);
        logger = LogFactory.getLog(logName());
        return (logger);
    }


    /**
     * 返回关联的Manager. 
     * 如果没有关联的Manager, 返回父级关联的Manager; 否则返回<code>null</code>.
     */
    public Manager getManager() {

        if (manager != null)
            return (manager);
        if (parent != null)
            return (parent.getManager());
        return (null);

    }


    /**
     * 设置关联的Manager
     *
     * @param manager The newly associated Manager
     */
    public synchronized void setManager(Manager manager) {

        // Change components if necessary
        Manager oldManager = this.manager;
        if (oldManager == manager)
            return;
        this.manager = manager;

        // Stop the old component if necessary
        if (started && (oldManager != null) &&
            (oldManager instanceof Lifecycle)) {
            try {
                ((Lifecycle) oldManager).stop();
            } catch (LifecycleException e) {
                log.error("ContainerBase.setManager: stop: ", e);
            }
        }

        // Start the new component if necessary
        if (manager != null)
            manager.setContainer(this);
        if (started && (manager != null) &&
            (manager instanceof Lifecycle)) {
            try {
                ((Lifecycle) manager).start();
            } catch (LifecycleException e) {
                log.error("ContainerBase.setManager: start: ", e);
            }
        }

        // Report this property change to interested listeners
        support.firePropertyChange("manager", oldManager, this.manager);
    }


    /**
     * 返回一个可用于映射到该组件的对象
     */
    public Object getMappingObject() {
        return this;
    }


    /**
     * 返回关联的Cluster. 
     * 如果没有关联的Cluster, 返回父级关联的Cluster; 否则返回<code>null</code>.
     */
    public Cluster getCluster() {
        if (cluster != null)
            return (cluster);

        if (parent != null)
            return (parent.getCluster());

        return (null);
    }


    /**
     * 设置关联的Cluster
     *
     * @param cluster The newly associated Cluster
     */
    public synchronized void setCluster(Cluster cluster) {
        // Change components if necessary
        Cluster oldCluster = this.cluster;
        if (oldCluster == cluster)
            return;
        this.cluster = cluster;

        // Stop the old component if necessary
        if (started && (oldCluster != null) &&
            (oldCluster instanceof Lifecycle)) {
            try {
                ((Lifecycle) oldCluster).stop();
            } catch (LifecycleException e) {
                log.error("ContainerBase.setCluster: stop: ", e);
            }
        }

        // Start the new component if necessary
        if (cluster != null)
            cluster.setContainer(this);

        if (started && (cluster != null) &&
            (cluster instanceof Lifecycle)) {
            try {
                ((Lifecycle) cluster).start();
            } catch (LifecycleException e) {
                log.error("ContainerBase.setCluster: start: ", e);
            }
        }

        // Report this property change to interested listeners
        support.firePropertyChange("cluster", oldCluster, this.cluster);
    }


    /**
     * 返回Container的名称(适合人类使用). 
     * 在属于特定父类的子容器中, Container名称必须唯一.
     */
    public String getName() {
        return (name);
    }


    /**
     * 设置Container的名称(适合人类使用). 
     * 在属于特定父类的子容器中, Container名称必须唯一.
     *
     * @param name 容器的名称
     *
     * @exception IllegalStateException 如果这个容器已经添加到父容器的子目录中(此后，名称不得更改)
     */
    public void setName(String name) {

        String oldName = this.name;
        this.name = name;
        support.firePropertyChange("name", oldName, this.name);
    }


    /**
     * 返回父级 Container. 如果没有，返回<code>null</code>.
     */
    public Container getParent() {
        return (parent);
    }


    /**
     * 设置父级 Container. 
     * 通过抛出异常，这个容器可以拒绝连接到指定的容器.
     *
     * @param container 父级容器
     *
     * @exception IllegalArgumentException 这个容器拒绝连接到指定的容器.
     */
    public void setParent(Container container) {
        Container oldParent = this.parent;
        this.parent = container;
        support.firePropertyChange("parent", oldParent, this.parent);
    }


    /**
     * 返回父类加载器.
     * 只有在一个Loader已经配置之后，这个调用才有意义
     */
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return (parentClassLoader);
        if (parent != null) {
            return (parent.getParentClassLoader());
        }
        return (ClassLoader.getSystemClassLoader());
    }


    /**
     * 设置父类加载器
     * 只有在一个Loader配置之前，这个调用才有意义, 并且指定的值（如果非null）应作为参数传递给类装入器构造函数.
     *
     * @param parent The new parent class loader
     */
    public void setParentClassLoader(ClassLoader parent) {
        ClassLoader oldParentClassLoader = this.parentClassLoader;
        this.parentClassLoader = parent;
        support.firePropertyChange("parentClassLoader", oldParentClassLoader,
                                   this.parentClassLoader);
    }


    /**
     * 返回管理Valves的Pipeline对象
     */
    public Pipeline getPipeline() {
        return (this.pipeline);
    }


    /**
     * 返回关联的Realm. 
     * 如果没有关联的Realm, 返回父级关联的Realm; 否则返回<code>null</code>.
     */
    public Realm getRealm() {
        if (realm != null)
            return (realm);
        if (parent != null)
            return (parent.getRealm());
        return (null);
    }


    /**
     * 设置关联的Realm. 
     *
     * @param realm The newly associated Realm
     */
    public synchronized void setRealm(Realm realm) {

        // Change components if necessary
        Realm oldRealm = this.realm;
        if (oldRealm == realm)
            return;
        this.realm = realm;

        // Stop the old component if necessary
        if (started && (oldRealm != null) &&
            (oldRealm instanceof Lifecycle)) {
            try {
                ((Lifecycle) oldRealm).stop();
            } catch (LifecycleException e) {
                log.error("ContainerBase.setRealm: stop: ", e);
            }
        }

        // Start the new component if necessary
        if (realm != null)
            realm.setContainer(this);
        if (started && (realm != null) &&
            (realm instanceof Lifecycle)) {
            try {
                ((Lifecycle) realm).start();
            } catch (LifecycleException e) {
                log.error("ContainerBase.setRealm: start: ", e);
            }
        }

        // Report this property change to interested listeners
        support.firePropertyChange("realm", oldRealm, this.realm);
    }


    /**
      * 返回关联的资源DirContext对象. 
      * 如果没哟关联的资源对象, 返回父级关联的资源对象; 否则返回<code>null</code>.
     */
    public DirContext getResources() {
        if (resources != null)
            return (resources);
        if (parent != null)
            return (parent.getResources());
        return (null);
    }


    /**
     * 设置关联的资源DirContext对象.
     *
     * @param resources The newly associated DirContext
     */
    public synchronized void setResources(DirContext resources) {
        // Called from StandardContext.setResources()
        //              <- StandardContext.start() 
        //              <- ContainerBase.addChildInternal() 

        // Change components if necessary
        DirContext oldResources = this.resources;
        if (oldResources == resources)
            return;
        Hashtable env = new Hashtable();
        if (getParent() != null)
            env.put(ProxyDirContext.HOST, getParent().getName());
        env.put(ProxyDirContext.CONTEXT, getName());
        this.resources = new ProxyDirContext(env, resources);
        // Report this property change to interested listeners
        support.firePropertyChange("resources", oldResources, this.resources);
    }


    // ------------------------------------------------------ Container Methods


    /**
     * 添加一个子级Container如果支持的话.
     * 在将该容器添加到子组之前, 子容器的<code>setParent()</code>方法必须被调用, 将这个Container作为一个参数. 
     * 这个方法可能抛出一个<code>IllegalArgumentException</code>, 如果这个Container选择不附加到指定的容器, 
     * 在这种情况下，它不会被添加
     *
     * @param child New child Container to be added
     *
     * @exception IllegalArgumentException 如果子级Container的<code>setParent()</code>方法抛出异常
     * @exception IllegalArgumentException 如果子容器没有一个唯一名称
     * @exception IllegalStateException 如果这个Container不支持子级Containers
     */
    public void addChild(Container child) {
        if (System.getSecurityManager() != null) {
            PrivilegedAction dp =
                new PrivilegedAddChild(child);
            AccessController.doPrivileged(dp);
        } else {
            addChildInternal(child);
        }
    }

    private void addChildInternal(Container child) {

        if( log.isDebugEnabled() )
            log.debug("Add child " + child + " " + this);
        synchronized(children) {
            if (children.get(child.getName()) != null)
                throw new IllegalArgumentException("addChild:  Child name '" +
                                                   child.getName() +
                                                   "' is not unique");
            child.setParent(this);  // May throw IAE
            children.put(child.getName(), child);

            // Start child
            if (started && (child instanceof Lifecycle)) {
                boolean success = false;
                try {
                    ((Lifecycle) child).start();
                    success = true;
                } catch (LifecycleException e) {
                    log.error("ContainerBase.addChild: start: ", e);
                    throw new IllegalStateException
                        ("ContainerBase.addChild: start: " + e);
                } finally {
                    if (!success) {
                        children.remove(child.getName());
                    }
                }
            }

            fireContainerEvent(ADD_CHILD_EVENT, child);
        }
    }


    /**
     * 添加一个容器事件监听器
     *
     * @param listener The listener to add
     */
    public void addContainerListener(ContainerListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }


    /**
     * 添加一个属性修改监听器
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * 返回指定名称的子级Container; 否则返回<code>null</code>
     *
     * @param name 要检索的子容器的名称
     */
    public Container findChild(String name) {
        if (name == null)
            return (null);
        synchronized (children) {       // Required by post-start changes
            return ((Container) children.get(name));
        }
    }


    /**
     * 返回子级Container集合.
     * 如果没有子级容器, 将返回一个零长度数组.
     */
    public Container[] findChildren() {
        synchronized (children) {
            Container results[] = new Container[children.size()];
            return ((Container[]) children.values().toArray(results));
        }
    }


    /**
     * 返回容器监听器集合.
     * 如果没有, 将返回一个零长度数组.
     */
    public ContainerListener[] findContainerListeners() {
        synchronized (listeners) {
            ContainerListener[] results = 
                new ContainerListener[listeners.size()];
            return ((ContainerListener[]) listeners.toArray(results));
        }
    }


    /**
     * 处理指定的Request, 产生相应的Response,
     * 通过调用第一个Valve, 或者其他的基础Valve
     *
     * @param request Request to be processed
     * @param response Response to be produced
     *
     * @exception IllegalStateException 如果没有pipeline或一个基础Valve被配置
     * @exception IOException if an input/output error occurred while
     *  processing
     * @exception ServletException if a ServletException was thrown
     *  while processing this request
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        pipeline.getFirst().invoke(request, response);
    }


    /**
     * 移除子级Container
     *
     * @param child Existing child Container to be removed
     */
    public void removeChild(Container child) {

        synchronized(children) {
            if (children.get(child.getName()) == null)
                return;
            children.remove(child.getName());
        }
        
        if (started && (child instanceof Lifecycle)) {
            try {
                if( child instanceof ContainerBase ) {
                    if( ((ContainerBase)child).started ) {
                        ((Lifecycle) child).stop();
                    }
                } else {
                    ((Lifecycle) child).stop();
                }
            } catch (LifecycleException e) {
                log.error("ContainerBase.removeChild: stop: ", e);
            }
        }
        
        fireContainerEvent(REMOVE_CHILD_EVENT, child);
        
        // child.setParent(null);
    }


    /**
     * 移除一个容器事件监听器
     *
     * @param listener The listener to remove
     */
    public void removeContainerListener(ContainerListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }


    /**
     * 移除一个属性修改监听器
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * 添加一个生命周期事件监听器
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }


    /**
     * 获取关联的生命周期事件监听器. 如果没有，返回零长度数组.
     */
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }


    /**
     * 移除一个生命周期监听器
     *
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }


    /**
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents it from being started
     */
    public synchronized void start() throws LifecycleException {

        // Validate and update our current component state
        if (started) {
            if(log.isInfoEnabled())
                log.info(sm.getString("containerBase.alreadyStarted", logName()));
            return;
        }
        
        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_START_EVENT, null);

        started = true;

        // Start our subordinate components, if any
        if ((loader != null) && (loader instanceof Lifecycle))
            ((Lifecycle) loader).start();
        getLogger();
        if ((logger != null) && (logger instanceof Lifecycle))
            ((Lifecycle) logger).start();
        if ((manager != null) && (manager instanceof Lifecycle))
            ((Lifecycle) manager).start();
        if ((cluster != null) && (cluster instanceof Lifecycle))
            ((Lifecycle) cluster).start();
        if ((realm != null) && (realm instanceof Lifecycle))
            ((Lifecycle) realm).start();
        if ((resources != null) && (resources instanceof Lifecycle))
            ((Lifecycle) resources).start();

        // Start our child containers, if any
        Container children[] = findChildren();
        for (int i = 0; i < children.length; i++) {
            if (children[i] instanceof Lifecycle)
                ((Lifecycle) children[i]).start();
        }

        // Start the Valves in our pipeline (including the basic), if any
        if (pipeline instanceof Lifecycle)
            ((Lifecycle) pipeline).start();

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(START_EVENT, null);

        // Start our thread
        threadStart();

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(AFTER_START_EVENT, null);
    }


    /**
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public synchronized void stop() throws LifecycleException {

        // Validate and update our current component state
        if (!started) {
            if(log.isInfoEnabled())
                log.info(sm.getString("containerBase.notStarted", logName()));
            return;
        }

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_STOP_EVENT, null);

        // Stop our thread
        threadStop();

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        // Stop the Valves in our pipeline (including the basic), if any
        if (pipeline instanceof Lifecycle) {
            ((Lifecycle) pipeline).stop();
        }

        // Stop our child containers, if any
        Container children[] = findChildren();
        for (int i = 0; i < children.length; i++) {
            if (children[i] instanceof Lifecycle)
                ((Lifecycle) children[i]).stop();
        }
        // Remove children - so next start can work
        children = findChildren();
        for (int i = 0; i < children.length; i++) {
            removeChild(children[i]);
        }

        // Stop our subordinate components, if any
        if ((resources != null) && (resources instanceof Lifecycle)) {
            ((Lifecycle) resources).stop();
        }
        if ((realm != null) && (realm instanceof Lifecycle)) {
            ((Lifecycle) realm).stop();
        }
        if ((cluster != null) && (cluster instanceof Lifecycle)) {
            ((Lifecycle) cluster).stop();
        }
        if ((manager != null) && (manager instanceof Lifecycle)) {
            ((Lifecycle) manager).stop();
        }
        if ((logger != null) && (logger instanceof Lifecycle)) {
            ((Lifecycle) logger).stop();
        }
        if ((loader != null) && (loader instanceof Lifecycle)) {
            ((Lifecycle) loader).stop();
        }

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(AFTER_STOP_EVENT, null);
    }

    /** 初始化方法, MBean 生命周期的部分.
     *  如果通过JMX 添加容器, 它会与父级一起注册, 使用 ObjectName约定定位父节点.
     * 
     *  如果容器是直接添加的，则它没有ObjectName,
     * 这将创建一个名称和使用JMX控制台注册本身. 在destroy(), 对象将被注销.
     * 
     * @throws Exception
     */ 
    public void init() throws Exception {

        if( this.getParent() == null ) {
            // "Life" update
            ObjectName parentName=getParentName();

            //log.info("Register " + parentName );
            if( parentName != null && 
                    mserver.isRegistered(parentName)) 
            {
                mserver.invoke(parentName, "addChild", new Object[] { this },
                        new String[] {"org.apache.catalina.Container"});
            }
        }
        initialized=true;
    }
    
    public ObjectName getParentName() throws MalformedObjectNameException {
        return null;
    }
    
    public void destroy() throws Exception {
        if( started ) {
            stop();
        }
        initialized=false;

        // unregister this component
        if ( oname != null ) {
            try {
                if( controller == oname ) {
                    Registry.getRegistry(null, null)
                        .unregisterComponent(oname);
                    if(log.isDebugEnabled())
                        log.debug("unregistering " + oname);
                }
            } catch( Throwable t ) {
                log.error("Error unregistering ", t );
            }
        }

        if (parent != null) {
            parent.removeChild(this);
        }

        // Stop our child containers, if any
        Container children[] = findChildren();
        for (int i = 0; i < children.length; i++) {
            removeChild(children[i]);
        }
    }

    // ------------------------------------------------------- Pipeline Methods


    /**
     * 添加一个新的Valve到管道的末尾. 
     * 在添加Valve之前, Valve的<code>setContainer</code>方法必须调用, 将这个Container作为一个参数.
     * 这个方法可能抛出一个<code>IllegalArgumentException</code>，如果这个Valve不能关联到这个Container;
     * 或者<code>IllegalStateException</code>,如果已经关联到另外一个Container.
     *
     * @param valve Valve to be added
     *
     * @exception IllegalArgumentException 如果这个Container拒绝接受指定的Valve
     * @exception IllegalArgumentException 如果指定的Valve拒绝关联到Container
     * @exception IllegalStateException 如果指定的Valve已经关联到另外一个Container
     */
    public synchronized void addValve(Valve valve) {
        pipeline.addValve(valve);
        fireContainerEvent(ADD_VALVE_EVENT, valve);
    }

    public ObjectName[] getValveObjectNames() {
        return ((StandardPipeline)pipeline).getValveObjectNames();
    }
    
    /**
     * <p>返回Valve实例， 被这个Pipeline认为是基础Valve
     */
    public Valve getBasic() {
        return (pipeline.getBasic());
    }


    /**
     * 返回pipeline中第一个valve.
     */
    public Valve getFirst() {
        return (pipeline.getFirst());
    }


    /**
     * 返回管道中的Valves集合, 包括基础Valve. 
     * 如果没有Valves, 返回一个零长度的数组.
     */
    public Valve[] getValves() {
        return (pipeline.getValves());
    }


    /**
     * 从管道中移除指定的Valve; 否则什么都不做.
     *
     * @param valve Valve to be removed
     */
    public synchronized void removeValve(Valve valve) {
        pipeline.removeValve(valve);
        fireContainerEvent(REMOVE_VALVE_EVENT, valve);
    }


    /**
     * <p>设置基础Valve实例. 
     * 设置之前, Valve的<code>setContainer()</code>将被调用, 如果它实现了<code>Contained</code>,并将Container 作为一个参数.
     * 方法可能抛出一个<code>IllegalArgumentException</code>，如果这个Valve不能关联到Container；
     * 或者<code>IllegalStateException</code>，如果已经关联到另外一个Container.</p>
     *
     * @param valve Valve to be distinguished as the basic Valve
     */
    public void setBasic(Valve valve) {
        pipeline.setBasic(valve);
    }


    /**
     * 执行周期任务, 例如重新加载, etc. 该方法将在该容器的类加载上下文中被调用.
     * 异常将被捕获和记录.
     */
    public void backgroundProcess() {
        
        if (!started)
            return;

        if (cluster != null) {
            try {
                cluster.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.cluster", cluster), e);                
            }
        }
        if (loader != null) {
            try {
                loader.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.loader", loader), e);                
            }
        }
        if (manager != null) {
            try {
                manager.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.manager", manager), e);                
            }
        }
        if (realm != null) {
            try {
                realm.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.realm", realm), e);                
            }
        }
        Valve current = pipeline.getFirst();
        while (current != null) {
            try {
                current.backgroundProcess();
            } catch (Exception e) {
                log.warn(sm.getString("containerBase.backgroundProcess.valve", current), e);                
            }
            current = current.getNext();
        }
        lifecycle.fireLifecycleEvent(Lifecycle.PERIODIC_EVENT, null);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 通知所有容器事件监听器，该容器已发生特定事件.  默认实现使用调用线程同步执行此通知.
     *
     * @param type Event type
     * @param data Event data
     */
    public void fireContainerEvent(String type, Object data) {

        if (listeners.size() < 1)
            return;
        ContainerEvent event = new ContainerEvent(this, type, data);
        ContainerListener list[] = new ContainerListener[0];
        synchronized (listeners) {
            list = (ContainerListener[]) listeners.toArray(list);
        }
        for (int i = 0; i < list.length; i++)
            ((ContainerListener) list[i]).containerEvent(event);
    }


    /**
     * 返回该容器的缩写名称， 用于记录日志
     */
    protected String logName() {

        if (logName != null) {
            return logName;
        }
        String loggerName = null;
        Container current = this;
        while (current != null) {
            String name = current.getName();
            if ((name == null) || (name.equals(""))) {
                name = "/";
            }
            loggerName = "[" + name + "]" 
                + ((loggerName != null) ? ("." + loggerName) : "");
            current = current.getParent();
        }
        logName = ContainerBase.class.getName() + "." + loggerName;
        return logName;
    }

    
    // -------------------- JMX and Registration  --------------------
    protected String type;
    protected String domain;
    protected String suffix;
    protected ObjectName oname;
    protected ObjectName controller;
    protected transient MBeanServer mserver;

    public ObjectName getJmxName() {
        return oname;
    }
    
    public String getObjectName() {
        if (oname != null) {
            return oname.toString();
        } else return null;
    }

    public String getDomain() {
        if( domain==null ) {
            Container parent=this;
            while( parent != null &&
                    !( parent instanceof StandardEngine) ) {
                parent=parent.getParent();
            }
            if( parent instanceof StandardEngine ) {
                domain=((StandardEngine)parent).getDomain();
            } 
        }
        return domain;
    }

    public void setDomain(String domain) {
        this.domain=domain;
    }
    
    public String getType() {
        return type;
    }

    protected String getJSR77Suffix() {
        return suffix;
    }

    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
        oname=name;
        mserver=server;
        if (name == null ){
            return null;
        }

        domain=name.getDomain();

        type=name.getKeyProperty("type");
        if( type==null ) {
            type=name.getKeyProperty("j2eeType");
        }

        String j2eeApp=name.getKeyProperty("J2EEApplication");
        String j2eeServer=name.getKeyProperty("J2EEServer");
        if( j2eeApp==null ) {
            j2eeApp="none";
        }
        if( j2eeServer==null ) {
            j2eeServer="none";
        }
        suffix=",J2EEApplication=" + j2eeApp + ",J2EEServer=" + j2eeServer;
        return name;
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() throws Exception {
    }

    public void postDeregister() {
    }

    public ObjectName[] getChildren() {
        ObjectName result[]=new ObjectName[children.size()];
        Iterator it=children.values().iterator();
        int i=0;
        while( it.hasNext() ) {
            Object next=it.next();
            if( next instanceof ContainerBase ) {
                result[i++]=((ContainerBase)next).getJmxName();
            }
        }
        return result;
    }

    public ObjectName createObjectName(String domain, ObjectName parent)
        throws Exception
    {
        if( log.isDebugEnabled())
            log.debug("Create ObjectName " + domain + " " + parent );
        return null;
    }

    public String getContainerSuffix() {
        Container container=this;
        Container context=null;
        Container host=null;
        Container servlet=null;
        
        StringBuffer suffix=new StringBuffer();
        
        if( container instanceof StandardHost ) {
            host=container;
        } else if( container instanceof StandardContext ) {
            host=container.getParent();
            context=container;
        } else if( container instanceof StandardWrapper ) {
            context=container.getParent();
            host=context.getParent();
            servlet=container;
        }
        if( context!=null ) {
            String path=((StandardContext)context).getPath();
            suffix.append(",path=").append((path.equals("")) ? "/" : path);
        } 
        if( host!=null ) suffix.append(",host=").append( host.getName() );
        if( servlet != null ) {
            String name=container.getName();
            suffix.append(",servlet=");
            suffix.append((name=="") ? "/" : name);
        }
        return suffix.toString();
    }


    /**
     * 启动后台线程将定期检查会话超时.
     */
    protected void threadStart() {

        if (thread != null)
            return;
        if (backgroundProcessorDelay <= 0)
            return;

        threadDone = false;
        String threadName = "ContainerBackgroundProcessor[" + toString() + "]";
        thread = new Thread(new ContainerBackgroundProcessor(), threadName);
        thread.setDaemon(true);
        thread.start();
    }


    /**
     * 停止定期检查会话超时的后台线程.
     */
    protected void threadStop() {
        if (thread == null)
            return;

        threadDone = true;
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException e) {
            ;
        }
        thread = null;
    }


    // -------------------------------------- ContainerExecuteDelay Inner Class


    /**
     * 执行这个容器和子容器的 backgroundProcess方法，在固定延迟后.
     */
    protected class ContainerBackgroundProcessor implements Runnable {

        public void run() {
            while (!threadDone) {
                try {
                    Thread.sleep(backgroundProcessorDelay * 1000L);
                } catch (InterruptedException e) {
                    ;
                }
                if (!threadDone) {
                    Container parent = (Container) getMappingObject();
                    ClassLoader cl = 
                        Thread.currentThread().getContextClassLoader();
                    if (parent.getLoader() != null) {
                        cl = parent.getLoader().getClassLoader();
                    }
                    processChildren(parent, cl);
                }
            }
        }

        protected void processChildren(Container container, ClassLoader cl) {
            try {
                if (container.getLoader() != null) {
                    Thread.currentThread().setContextClassLoader
                        (container.getLoader().getClassLoader());
                }
                container.backgroundProcess();
            } catch (Throwable t) {
                log.error("Exception invoking periodic operation: ", t);
            } finally {
                Thread.currentThread().setContextClassLoader(cl);
            }
            Container[] children = container.findChildren();
            for (int i = 0; i < children.length; i++) {
                if (children[i].getBackgroundProcessorDelay() <= 0) {
                    processChildren(children[i], cl);
                }
            }
        }
    }
}
