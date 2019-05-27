package org.apache.catalina.startup;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;

import org.apache.catalina.Authenticator;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Loader;
import org.apache.catalina.Realm;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.security.SecurityConfig;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.log.SystemLogHandler;

/**
 * 嵌入Catalina servlet容器环境内的另一个应用方便类.
 * 您必须按照以下顺序调用该类的方法，以确保正确操作.
 *
 * <ul>
 * <li>实例化这个类的一个新实例.</li>
 * <li>设置此对象本身的相关属性. 特别是,
 *     您将要建立要使用的默认Logger, 以及默认的Realm, 如果您使用容器管理的安全性.</li>
 * <li>调用<code>createEngine()</code>创建一个Engine对象, 然后按需要调用它的属性Setter.</li>
 * <li>调用<code>createHost()</code>创建至少一个与新创建的Engine相关联的虚拟主机, 然后按需要调用它的属性设置器. 
 * 		自定义此主机之后, 将其添加到Engine 通过<code>engine.addChild(host)</code>.</li>
 * <li>调用<code>createContext()</code> 创建与每个新创建的主机相关联的至少一个上下文, 然后按需要调用它的属性Setter.
 * 		你应该创建一个路径名等于零长度字符串的上下文, 它将用于处理未映射到其他上下文的所有请求.
 * 		自定义此上下文之后, 将其添加到相应的Host使用<code>host.addChild(context)</code>方法.</li>
 * <li>调用<code>addEngine()</code>附加这个Engine到这个对象的Engine集合.</li>
 * <li>调用<code>createConnector()</code>创建至少一个TCP/IP连接, 然后按需要调用它的属性Setter.</li>
 * <li>调用<code>addConnector()</code>附加这个Connector到这个对象的Connector集合. 添加的Connector将使用最近添加的Engine来处理其接收的请求.</li>
 * <li>按照需要重复上述步骤(虽然通常只创建一个Engine实例).</li>
 * <li>调用<code>start()</code>启动所有附加组件的正常操作.</li>
 * </ul>
 *
 * 正常运算开始之后, 可以添加和删除Connectors, Engines, Hosts, Contexts. 但是, 一旦删除了某个特定组件, 必须扔掉它 -- 
 * 如果只想重新启动，可以创建具有相同特性的新特性.
 * <p>
 * 正常关闭, 调用这个对象的<code>stop()</code>方法.
 * <p>
 */
public class Embedded  extends StandardService implements Lifecycle {
    private static Log log = LogFactory.getLog(Embedded.class);

    // ----------------------------------------------------------- Constructors

    public Embedded() {
        this(null);
    }


    /**
     * @param realm 要由所有组件继承的Realm实现类(除非进一步覆盖容器层次结构)
     */
    public Embedded(Realm realm) {
        super();
        setRealm(realm);
        setSecurityProtection();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 命名可用?
     */
    protected boolean useNaming = true;


    /**
     * 是否启用了标准流重定向 ?
     */
    protected boolean redirectStreams = true;


    /**
     * 部署在这个服务器中的一组Engine. 正常情况下只有一个.
     */
    protected Engine engines[] = new Engine[0];


    /**
     * 用于验证的自定义登录方法
     */
    protected HashMap authenticators;


    /**
     * 实现类的描述信息
     */
    protected static final String info =
        "org.apache.catalina.startup.Embedded/1.0";


    /**
     * 生命周期事件支持.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * 所有容器使用的默认的realm.
     */
    protected Realm realm = null;


    /**
     * The string manager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 是否已启动?
     */
    protected boolean started = false;

    /**
     * 是否使用等待.
     */
    protected boolean await = false;


    // ------------------------------------------------------------- Properties


    /**
     * 如果命名可用，返回true.
     */
    public boolean isUseNaming() {
        return (this.useNaming);
    }


    /**
     * 启用或禁用命名支持.
     *
     * @param useNaming The new use naming value
     */
    public void setUseNaming(boolean useNaming) {
        boolean oldUseNaming = this.useNaming;
        this.useNaming = useNaming;
        support.firePropertyChange("useNaming", new Boolean(oldUseNaming),
                                   new Boolean(this.useNaming));
    }


    /**
     * 如果启用了标准流重定向 ，返回 true.
     */
    public boolean isRedirectStreams() {
        return (this.redirectStreams);
    }


    /**
     * 启用或禁用命名支持.
     *
     * @param useNaming The new use naming value
     */
    public void setRedirectStreams(boolean redirectStreams) {
        boolean oldRedirectStreams = this.redirectStreams;
        this.redirectStreams = redirectStreams;
        support.firePropertyChange("redirectStreams", new Boolean(oldRedirectStreams),
                                   new Boolean(this.redirectStreams));
    }


    /**
     * 返回默认的 Realm.
     */
    public Realm getRealm() {
        return (this.realm);
    }


    /**
     * 设置默认的 Realm.
     *
     * @param realm The new default realm
     */
    public void setRealm(Realm realm) {
        Realm oldRealm = this.realm;
        this.realm = realm;
        support.firePropertyChange("realm", oldRealm, this.realm);
    }

    public void setAwait(boolean b) {
        await = b;
    }

    public boolean isAwait() {
        return await;
    }

    public void setCatalinaHome( String s ) {
        System.setProperty( "catalina.home", s);
    }

    public void setCatalinaBase( String s ) {
        System.setProperty( "catalina.base", s);
    }

    public String getCatalinaHome() {
        return System.getProperty("catalina.home");
    }

    public String getCatalinaBase() {
        return System.getProperty("catalina.base");
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 添加一个Connector. 新添加的Connector将被关联到最近添加的Engine.
     *
     * @param connector The connector to be added
     *
     * @exception IllegalStateException if no engines have been added yet
     */
    public synchronized void addConnector(Connector connector) {
        if( log.isDebugEnabled() ) {
            log.debug("Adding connector (" + connector.getInfo() + ")");
        }

        // Make sure we have a Container to send requests to
        if (engines.length < 1)
            throw new IllegalStateException
                (sm.getString("embedded.noEngines"));

        /*
         * Add the connector. This will set the connector's container to the
         * most recently added Engine
         */
        super.addConnector(connector);
    }


    /**
     * 添加一个Engine.
     *
     * @param engine The engine to be added
     */
    public synchronized void addEngine(Engine engine) {

        if( log.isDebugEnabled() )
            log.debug("Adding engine (" + engine.getInfo() + ")");

        // Add this Engine to our set of defined Engines
        Engine results[] = new Engine[engines.length + 1];
        for (int i = 0; i < engines.length; i++)
            results[i] = engines[i];
        results[engines.length] = engine;
        engines = results;

        // Start this Engine if necessary
        if (started && (engine instanceof Lifecycle)) {
            try {
                ((Lifecycle) engine).start();
            } catch (LifecycleException e) {
                log.error("Engine.start", e);
            }
        }
        this.container = engine;
    }


    /**
     * 创建, 配置, 并返回一个新TCP/IP套接字连接, 基于指定的属性.
     *
     * @param address 监听的地址, 或者<code>null</code>监听服务器上所有的地址
     * @param port 监听的端口号
     * @param secure 这个端口应该启用SSL吗?
     */
    public Connector createConnector(InetAddress address, int port,
                                     boolean secure) {
	return createConnector(address != null? address.toString() : null,
			       port, secure);
    }

    public Connector createConnector(String address, int port,
                                     boolean secure) {
        String protocol = "http";
        if (secure) {
            protocol = "https";
        }

        return createConnector(address, port, protocol);
    }


    public Connector createConnector(InetAddress address, int port,
                                     String protocol) {
	return createConnector(address != null? address.toString() : null,
			       port, protocol);
    }

    public Connector createConnector(String address, int port,
				     String protocol) {

        Connector connector = null;

		if (address != null) {
		    /*
		     * InetAddress.toString() 返回"<hostname>/<literal_IP>"字符串.
		     * 得到后半部分, 这样就可以使用InetAddress.getByName()解析地址为 InetAddress.
		     */
		    int index = address.indexOf('/');
		    if (index != -1) {
			address = address.substring(index + 1);
		    }
		}
	
		if (log.isDebugEnabled()) {
	            log.debug("Creating connector for address='" +
			      ((address == null) ? "ALL" : address) +
			      "' port='" + port + "' protocol='" + protocol + "'");
		}

        try {

            if (protocol.equals("ajp")) {
                connector = new Connector("org.apache.jk.server.JkCoyoteHandler");
            } else if (protocol.equals("memory")) {
                connector = new Connector("org.apache.coyote.memory.MemoryProtocolHandler");
            } else if (protocol.equals("http")) {
                connector = new Connector();
            } else if (protocol.equals("https")) {
                connector = new Connector();
                connector.setScheme("https");
                connector.setSecure(true);
                // FIXME !!!! SET SSL PROPERTIES
            }

            if (address != null) {
                IntrospectionUtils.setProperty(connector, "address", 
                                               "" + address);
            }
            IntrospectionUtils.setProperty(connector, "port", "" + port);

        } catch (Exception e) {
            log.error("Couldn't create connector.");
        } 
        return (connector);
    }

    /**
     * 创建, 配置, 并返回一个Context, 将处理所有从相关Connectors接收到的HTTP请求,
     * 并在上下文连接的虚拟主机上指向指定的上下文路径.
     * <p>
     * 自定义这个上下文的属性, 监听器, Valves之后, 您必须将其附加到相应的Host, 通过调用:
     * <pre>
     *   host.addChild(context);
     * </pre>
     * 如果主机已经启动，这也将导致上下文启动.
     *
     * @param path 应用程序的上下文路径("" 对于该主机的默认应用程序, 否则必须以一个斜杠开头)
     * @param docBase 此Web应用程序的文档库目录的绝对路径名
     *
     * @exception IllegalArgumentException 如果指定了无效参数
     */
    public Context createContext(String path, String docBase) {

        if( log.isDebugEnabled() )
            log.debug("Creating context '" + path + "' with docBase '" +
                       docBase + "'");

        StandardContext context = new StandardContext();

        context.setDocBase(docBase);
        context.setPath(path);

        ContextConfig config = new ContextConfig();
        config.setCustomAuthenticators(authenticators);
        ((Lifecycle) context).addLifecycleListener(config);

        return (context);

    }


    /**
     * 创建, 配置, 并返回一个Engine, 将处理所有从相关的一个Connector接收到的HTTP请求,
     * 基于指定的属性.
     */
    public Engine createEngine() {

        if( log.isDebugEnabled() )
            log.debug("Creating engine");

        StandardEngine engine = new StandardEngine();

        // Default host will be set to the first host added
        engine.setRealm(realm);         // Inherited by all children

        return (engine);

    }


    /**
     * 创建, 配置, 并返回一个Host, 将处理所有从相关的一个Connector接收到的HTTP请求,
     * 并重定向到指定的虚拟主机.
     * <p>
     * 自定义这个主机的属性, 监听器, Valves之后, 必须将其附加到相应的Engine通过调用:
     * <pre>
     *   engine.addChild(host);
     * </pre>
     * 如果Engine已经启动，这也会导致Host启动. 如果这是默认的(或唯一的) Host, 
     * 你也可以告诉Engine将未分配给另一虚拟主机的所有请求传递给这个:
     * <pre>
     *   engine.setDefaultHost(host.getName());
     * </pre>
     *
     * @param name 此虚拟主机的规范名称
     * @param appBase 此虚拟主机的应用基础目录的绝对路径名
     *
     * @exception IllegalArgumentException 如果指定了无效参数
     */
    public Host createHost(String name, String appBase) {

        if( log.isDebugEnabled() )
            log.debug("Creating host '" + name + "' with appBase '" +
                       appBase + "'");

        StandardHost host = new StandardHost();

        host.setAppBase(appBase);
        host.setName(name);

        return (host);
    }


    /**
     * 创建并返回一个可自定义的类加载器的管理器, 并附加到一个Context, 在它启动之前.
     *
     * @param parent ClassLoader that will be the parent of the one
     *  created by this Loader
     */
    public Loader createLoader(ClassLoader parent) {

        if( log.isDebugEnabled() )
            log.debug("Creating Loader with parent class loader '" +
                       parent + "'");

        WebappLoader loader = new WebappLoader(parent);
        return (loader);
    }


    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 删除指定的Context. 如果这是这个Host的最后一个Context, 这个Host也将被删除.
     *
     * @param context The Context to be removed
     */
    public synchronized void removeContext(Context context) {

        if( log.isDebugEnabled() )
            log.debug("Removing context[" + context.getPath() + "]");

        // Is this Context actually among those that are defined?
        boolean found = false;
        for (int i = 0; i < engines.length; i++) {
            Container hosts[] = engines[i].findChildren();
            for (int j = 0; j < hosts.length; j++) {
                Container contexts[] = hosts[j].findChildren();
                for (int k = 0; k < contexts.length; k++) {
                    if (context == (Context) contexts[k]) {
                        found = true;
                        break;
                    }
                }
                if (found)
                    break;
            }
            if (found)
                break;
        }
        if (!found)
            return;

        // Remove this Context from the associated Host
        if( log.isDebugEnabled() )
            log.debug(" Removing this Context");
        context.getParent().removeChild(context);
    }


    /**
     * 删除指定的Engine, 连同所有相关的Hosts和Contexts.  所有相关的Connector也会被删除.
     *
     * @param engine The Engine to be removed
     */
    public synchronized void removeEngine(Engine engine) {

        if( log.isDebugEnabled() )
            log.debug("Removing engine (" + engine.getInfo() + ")");

        // Is the specified Engine actually defined?
        int j = -1;
        for (int i = 0; i < engines.length; i++) {
            if (engine == engines[i]) {
                j = i;
                break;
            }
        }
        if (j < 0)
            return;

        // Remove any Connector that is using this Engine
        if( log.isDebugEnabled() )
            log.debug(" Removing related Containers");
        while (true) {
            int n = -1;
            for (int i = 0; i < connectors.length; i++) {
                if (connectors[i].getContainer() == (Container) engine) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                break;
            removeConnector(connectors[n]);
        }

        // Stop this Engine if necessary
        if (engine instanceof Lifecycle) {
            if( log.isDebugEnabled() )
                log.debug(" Stopping this Engine");
            try {
                ((Lifecycle) engine).stop();
            } catch (LifecycleException e) {
                log.error("Engine.stop", e);
            }
        }

        // Remove this Engine from our set of defined Engines
        if( log.isDebugEnabled() )
            log.debug(" Removing this Engine");
        int k = 0;
        Engine results[] = new Engine[engines.length - 1];
        for (int i = 0; i < engines.length; i++) {
            if (i != j)
                results[k++] = engines[i];
        }
        engines = results;
    }


    /**
     * 删除指定的Host, 以及所有相关的Contexts. 如果这是这个Engine的最后一个Host, 这个Engine也将被删除.
     *
     * @param host The Host to be removed
     */
    public synchronized void removeHost(Host host) {

        if( log.isDebugEnabled() )
            log.debug("Removing host[" + host.getName() + "]");

        // Is this Host actually among those that are defined?
        boolean found = false;
        for (int i = 0; i < engines.length; i++) {
            Container hosts[] = engines[i].findChildren();
            for (int j = 0; j < hosts.length; j++) {
                if (host == (Host) hosts[j]) {
                    found = true;
                    break;

                }
            }
            if (found)
                break;
        }
        if (!found)
            return;

        // Remove this Host from the associated Engine
        if( log.isDebugEnabled() )
            log.debug(" Removing this Host");
        host.getParent().removeChild(host);
    }


    /*
     * 映射指定的登录方法到指定的Authenticator, 允许 org/apache/catalina/startup/Authenticators.properties中重写.
     *
     * @param authenticator 为指定的登录方法处理验证的Authenticator
     * @param loginMethod 登录方法映射到指定的 authenticator
     *
     * @throws IllegalArgumentException 如果指定的authenticator 没有实现 org.apache.catalina.Valve 接口
     */
    public void addAuthenticator(Authenticator authenticator,
                                 String loginMethod) {
        if (!(authenticator instanceof Valve)) {
            throw new IllegalArgumentException(
                sm.getString("embedded.authenticatorNotInstanceOfValve"));
        }
        if (authenticators == null) {
            synchronized (this) {
                if (authenticators == null) {
                    authenticators = new HashMap();
                }
            }
        }
        authenticators.put(loginMethod, authenticator);
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * 添加生命周期事件监听器.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }


    /**
     * 获取所有的生命周期事件监听器. 如果没有, 返回零长度数组.
     */
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }


    /**
     * 删除一个生命周期事件监听器.
     *
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }


    /**
     * 这个方法应该在<code>configure()</code>方法之后调用, 并在其他方法之前调用.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    public void start() throws LifecycleException {

        if( log.isInfoEnabled() )
            log.info("Starting tomcat server");

        // Validate the setup of our required system properties
        initDirs();

        // Initialize some naming specific properties
        initNaming();

        // Validate and update our current component state
        if (started)
            throw new LifecycleException
                (sm.getString("embedded.alreadyStarted"));
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;
        initialized = true;

        // Start our defined Engines first
        for (int i = 0; i < engines.length; i++) {
            if (engines[i] instanceof Lifecycle)
                ((Lifecycle) engines[i]).start();
        }

        // Start our defined Connectors second
        for (int i = 0; i < connectors.length; i++) {
            connectors[i].initialize();
            if (connectors[i] instanceof Lifecycle)
                ((Lifecycle) connectors[i]).start();
        }

    }


    /**
     * 这个方法应该最后一个调用.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public void stop() throws LifecycleException {

        if( log.isDebugEnabled() )
            log.debug("Stopping embedded server");

        // Validate and update our current component state
        if (!started)
            throw new LifecycleException
                (sm.getString("embedded.notStarted"));
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        // Stop our defined Connectors first
        for (int i = 0; i < connectors.length; i++) {
            if (connectors[i] instanceof Lifecycle)
                ((Lifecycle) connectors[i]).stop();
        }

        // Stop our defined Engines second
        for (int i = 0; i < engines.length; i++) {
            if (engines[i] instanceof Lifecycle)
                ((Lifecycle) engines[i]).stop();
        }
    }


    // ------------------------------------------------------ Protected Methods


    /** 初始化命名 - 这个只能启用 java:env 和根命名.
     * 如果Tomcat嵌入到已经定义这些的应用程序中 -它不应该做这个.
     *
     * XXX 2者应该分开, 你可能希望启用 java: 但不初始化上下文和倒转
     * XXX Can we "guess" - i.e. lookup java: and if something is returned assume
     * false ?
     * XXX We have a major problem with the current setting for java: url
     */
    protected void initNaming() {
        // Setting additional variables
        if (!useNaming) {
            log.info( "Catalina naming disabled");
            System.setProperty("catalina.useNaming", "false");
        } else {
            System.setProperty("catalina.useNaming", "true");
            String value = "org.apache.naming";
            String oldValue =
                System.getProperty(javax.naming.Context.URL_PKG_PREFIXES);
            if (oldValue != null) {
                value = value + ":" + oldValue;
            }
            System.setProperty(javax.naming.Context.URL_PKG_PREFIXES, value);
            if( log.isDebugEnabled() )
                log.debug("Setting naming prefix=" + value);
            value = System.getProperty
                (javax.naming.Context.INITIAL_CONTEXT_FACTORY);
            if (value == null) {
                System.setProperty
                    (javax.naming.Context.INITIAL_CONTEXT_FACTORY,
                     "org.apache.naming.java.javaURLContextFactory");
            } else {
                log.debug( "INITIAL_CONTEXT_FACTORY alread set " + value );
            }
        }
    }


    protected void initDirs() {

        String catalinaHome = System.getProperty("catalina.home");
        if (catalinaHome == null) {
            // Backwards compatibility patch for J2EE RI 1.3
            String j2eeHome = System.getProperty("com.sun.enterprise.home");
            if (j2eeHome != null) {
                catalinaHome=System.getProperty("com.sun.enterprise.home");
            } else if (System.getProperty("catalina.base") != null) {
                catalinaHome = System.getProperty("catalina.base");
            } else {
                // Use IntrospectionUtils and guess the dir
                catalinaHome = IntrospectionUtils.guessInstall
                    ("catalina.home", "catalina.base", "catalina.jar");
                if (catalinaHome == null) {
                    catalinaHome = IntrospectionUtils.guessInstall
                        ("tomcat.install", "catalina.home", "tomcat.jar");
                }
            }
        }
        if (catalinaHome != null) {
            File home = new File(catalinaHome);
            if (!home.isAbsolute()) {
                try {
                    catalinaHome = home.getCanonicalPath();
                } catch (IOException e) {
                    catalinaHome = home.getAbsolutePath();
                }
            }
            System.setProperty("catalina.home", catalinaHome);
        }

        if (System.getProperty("catalina.base") == null) {
            System.setProperty("catalina.base",
                               System.getProperty("catalina.home"));
        } else {
            String catalinaBase = System.getProperty("catalina.base");
            File base = new File(catalinaBase);
            if (!base.isAbsolute()) {
                try {
                    catalinaBase = base.getCanonicalPath();
                } catch (IOException e) {
                    catalinaBase = base.getAbsolutePath();
                }
            }
            System.setProperty("catalina.base", catalinaBase);
        }
        
        String temp = System.getProperty("java.io.tmpdir");
        if (temp == null || (!(new File(temp)).exists())
                || (!(new File(temp)).isDirectory())) {
            log.error(sm.getString("embedded.notmp", temp));
        }

    }

    
    protected void initStreams() {
        if (redirectStreams) {
            // Replace System.out and System.err with a custom PrintStream
            SystemLogHandler systemlog = new SystemLogHandler(System.out);
            System.setOut(systemlog);
            System.setErr(systemlog);
        }
    }
    

    // -------------------------------------------------------- Private Methods

    /**
     * 设置安全包访问/保护.
     */
    protected void setSecurityProtection(){
        SecurityConfig securityConfig = SecurityConfig.newInstance();
        securityConfig.setPackageDefinition();
        securityConfig.setPackageAccess();
    }
}
