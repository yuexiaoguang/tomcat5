package org.apache.catalina.startup;


import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import org.apache.catalina.Container;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Server;
import org.apache.catalina.core.StandardServer;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;


/**
 * Catalina的Startup/Shutdown脚本程序. 识别以下命令行选项:
 * <ul>
 * <li><b>-config {pathname}</b> - 设置要处理的配置文件的路径名. 如果指定了相对路径, 
 * 				它将被解释为相对于由"catalina.base"系统属性指定的目录路径名.   [conf/server.xml]
 * <li><b>-help</b> - 显示使用信息.
 * <li><b>-stop</b> - 停止当前正在运行的Catalina实例.
 * </u>
 *
 * 应该做与嵌入式相同的事情, 但是使用server.xml 文件.
 */
public class Catalina extends Embedded {


    // ----------------------------------------------------- Instance Variables


    /**
     * 服务器配置文件的路径名.
     */
    protected String configFile = "conf/server.xml";

    // XXX Should be moved to embedded
    /**
     * 此服务器的共享扩展类装入器.
     */
    protected ClassLoader parentClassLoader = Catalina.class.getClassLoader();


    /**
     * 正在启动或停止的服务器组件
     */
    protected Server server = null;


    /**
     * 是否启动?
     */
    protected boolean starting = false;


    /**
     * 是否停止?
     */
    protected boolean stopping = false;


    /**
     * 是否使用关闭挂钩.
     */
    protected boolean useShutdownHook = true;


    /**
     * 关闭挂钩.
     */
    protected Thread shutdownHook = null;


    // ------------------------------------------------------------- Properties


    public void setConfig(String file) {
        configFile = file;
    }


    public void setConfigFile(String file) {
        configFile = file;
    }


    public String getConfigFile() {
        return configFile;
    }


    public void setUseShutdownHook(boolean useShutdownHook) {
        this.useShutdownHook = useShutdownHook;
    }


    public boolean getUseShutdownHook() {
        return useShutdownHook;
    }


    /**
     * 设置共享扩展类装入器.
     *
     * @param parentClassLoader The shared extensions class loader.
     */
    public void setParentClassLoader(ClassLoader parentClassLoader) {
        this.parentClassLoader = parentClassLoader;
    }


    /**
     * 设置正在配置的服务器实例.
     *
     * @param server The new server
     */
    public void setServer(Server server) {
        this.server = server;
    }

    // ----------------------------------------------------------- Main Program

    /**
     * @param args 命令行参数
     */
    public static void main(String args[]) {
        (new Catalina()).process(args);
    }


    /**
     * 实例主程序
     *
     * @param args 命令行参数
     */
    public void process(String args[]) {

        setAwait(true);
        setCatalinaHome();
        setCatalinaBase();
        try {
            if (arguments(args)) {
                if (starting) {
                    load(args);
                    start();
                } else if (stopping) {
                    stopServer();
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 处理指定的命令行参数, 并返回<code>true</code>，如果我们继续处理; 或者<code>false</code>.
     *
     * @param args 要处理的命令行参数
     */
    protected boolean arguments(String args[]) {

        boolean isConfig = false;

        if (args.length < 1) {
            usage();
            return (false);
        }

        for (int i = 0; i < args.length; i++) {
            if (isConfig) {
                configFile = args[i];
                isConfig = false;
            } else if (args[i].equals("-config")) {
                isConfig = true;
            } else if (args[i].equals("-nonaming")) {
                setUseNaming( false );
            } else if (args[i].equals("-help")) {
                usage();
                return (false);
            } else if (args[i].equals("start")) {
                starting = true;
                stopping = false;
            } else if (args[i].equals("stop")) {
                starting = false;
                stopping = true;
            } else {
                usage();
                return (false);
            }
        }
        return (true);
    }


    /**
     * 返回表示配置文件的文件对象.
     */
    protected File configFile() {
        File file = new File(configFile);
        if (!file.isAbsolute())
            file = new File(System.getProperty("catalina.base"), configFile);
        return (file);
    }


    /**
     * 创建和配置将用于启动的Digester.
     */
    protected Digester createStartDigester() {
        long t1=System.currentTimeMillis();
        // Initialize the digester
        Digester digester = new Digester();
        digester.setValidating(false);
        digester.setClassLoader(StandardServer.class.getClassLoader());

        // 配置将使用的操作
        digester.addObjectCreate("Server",
                                 "org.apache.catalina.core.StandardServer",
                                 "className");
        digester.addSetProperties("Server");
        digester.addSetNext("Server",
                            "setServer",
                            "org.apache.catalina.Server");

        digester.addObjectCreate("Server/GlobalNamingResources",
                                 "org.apache.catalina.deploy.NamingResources");
        digester.addSetProperties("Server/GlobalNamingResources");
        digester.addSetNext("Server/GlobalNamingResources",
                            "setGlobalNamingResources",
                            "org.apache.catalina.deploy.NamingResources");

        digester.addObjectCreate("Server/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties("Server/Listener");
        digester.addSetNext("Server/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        digester.addObjectCreate("Server/Service",
                                 "org.apache.catalina.core.StandardService",
                                 "className");
        digester.addSetProperties("Server/Service");
        digester.addSetNext("Server/Service",
                            "addService",
                            "org.apache.catalina.Service");

        digester.addObjectCreate("Server/Service/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties("Server/Service/Listener");
        digester.addSetNext("Server/Service/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        digester.addRule("Server/Service/Connector",
                         new ConnectorCreateRule());
        digester.addRule("Server/Service/Connector", 
                         new SetAllPropertiesRule());
        digester.addSetNext("Server/Service/Connector",
                            "addConnector",
                            "org.apache.catalina.connector.Connector");

        digester.addObjectCreate("Server/Service/Connector/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties("Server/Service/Connector/Listener");
        digester.addSetNext("Server/Service/Connector/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        // Add RuleSets for nested elements
        digester.addRuleSet(new NamingRuleSet("Server/GlobalNamingResources/"));
        digester.addRuleSet(new EngineRuleSet("Server/Service/"));
        digester.addRuleSet(new HostRuleSet("Server/Service/Engine/"));
        digester.addRuleSet(new ContextRuleSet("Server/Service/Engine/Host/"));
        digester.addRuleSet(new ClusterRuleSet("Server/Service/Engine/Host/Cluster/"));
        digester.addRuleSet(new NamingRuleSet("Server/Service/Engine/Host/Context/"));

        // When the 'engine' is found, set the parentClassLoader.
        digester.addRule("Server/Service/Engine",
                         new SetParentClassLoaderRule(parentClassLoader));
        digester.addRuleSet(new ClusterRuleSet("Server/Service/Engine/Cluster/"));

        long t2=System.currentTimeMillis();
        if (log.isDebugEnabled())
            log.debug("Digester for server.xml created " + ( t2-t1 ));
        return (digester);
    }


    /**
     * 创建和配置将用于关机的Digester
     */
    protected Digester createStopDigester() {

        // Initialize the digester
        Digester digester = new Digester();

        // Configure the rules we need for shutting down
        digester.addObjectCreate("Server",
                                 "org.apache.catalina.core.StandardServer",
                                 "className");
        digester.addSetProperties("Server");
        digester.addSetNext("Server",
                            "setServer",
                            "org.apache.catalina.Server");
        return (digester);
    }


    public void stopServer() {
        stopServer(null);
    }

    public void stopServer(String[] arguments) {

        if (arguments != null) {
            arguments(arguments);
        }

        if( server == null ) {
            // Create and execute our Digester
            Digester digester = createStopDigester();
            digester.setClassLoader(Thread.currentThread().getContextClassLoader());
            File file = configFile();
            try {
                InputSource is =
                    new InputSource("file://" + file.getAbsolutePath());
                FileInputStream fis = new FileInputStream(file);
                is.setByteStream(fis);
                digester.push(this);
                digester.parse(is);
                fis.close();
            } catch (Exception e) {
                log.error("Catalina.stop: ", e);
                System.exit(1);
            }
        }

        // Stop the existing server
        try {
            Socket socket = new Socket("127.0.0.1", server.getPort());
            OutputStream stream = socket.getOutputStream();
            String shutdown = server.getShutdown();
            for (int i = 0; i < shutdown.length(); i++)
                stream.write(shutdown.charAt(i));
            stream.flush();
            stream.close();
            socket.close();
        } catch (IOException e) {
            log.error("Catalina.stop: ", e);
            System.exit(1);
        }
    }


    /**
     * 如果还没有设置工作目录，设置<code>catalina.base</code>系统属性为当前工作目录.
     * @deprecated Use initDirs()
     */
    public void setCatalinaBase() {
        initDirs();
    }

    /**
     * 如果还没有设置工作目录，设置<code>catalina.home</code>系统属性为当前工作目录.
     * @deprecated Use initDirs()
     */
    public void setCatalinaHome() {
        initDirs();
    }

    /**
     * 启动一个新服务器实例
     */
    public void load() {

        initDirs();

        // Before digester - it may be needed
        initNaming();

        // 创建并执行 Digester
        Digester digester = createStartDigester();
        long t1 = System.currentTimeMillis();

        Exception ex = null;
        InputSource inputSource = null;
        InputStream inputStream = null;
        File file = null;
        try {
            file = configFile();
            inputStream = new FileInputStream(file);
            inputSource = new InputSource("file://" + file.getAbsolutePath());
        } catch (Exception e) {
            ;
        }
        if (inputStream == null) {
            try {
                inputStream = getClass().getClassLoader()
                    .getResourceAsStream(getConfigFile());
                inputSource = new InputSource
                    (getClass().getClassLoader()
                     .getResource(getConfigFile()).toString());
            } catch (Exception e) {
                ;
            }
        }

        if ((inputStream == null) && (file != null)) {
            log.warn("Can't load server.xml from " + file.getAbsolutePath());
            return;
        }

        try {
            inputSource.setByteStream(inputStream);
            digester.push(this);
            digester.parse(inputSource);
            inputStream.close();
        } catch (Exception e) {
            log.warn("Catalina.start using "
                               + getConfigFile() + ": " , e);
            return;
        }

        // 流重定向
        initStreams();

        // 启动新服务器
        if (server instanceof Lifecycle) {
            try {
                server.initialize();
            } catch (LifecycleException e) {
                log.error("Catalina.start", e);
            }
        }

        long t2 = System.currentTimeMillis();
        if(log.isInfoEnabled())
            log.info("Initialization processed in " + (t2 - t1) + " ms");

    }


    /* 
     * 加载使用参数
     */
    public void load(String args[]) {
        try {
            if (arguments(args))
                load();
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    public void create() {

    }

    public void destroy() {

    }

    /**
     * 启动一个服务器实例.
     */
    public void start() {

        if (server == null) {
            load();
        }

        long t1 = System.currentTimeMillis();

        // Start the new server
        if (server instanceof Lifecycle) {
            try {
                ((Lifecycle) server).start();
            } catch (LifecycleException e) {
                log.error("Catalina.start: ", e);
            }
        }

        long t2 = System.currentTimeMillis();
        if(log.isInfoEnabled())
            log.info("Server startup in " + (t2 - t1) + " ms");

        try {
            // Register shutdown hook
            if (useShutdownHook) {
                if (shutdownHook == null) {
                    shutdownHook = new CatalinaShutdownHook();
                }
                Runtime.getRuntime().addShutdownHook(shutdownHook);
            }
        } catch (Throwable t) {
            // This will fail on JDK 1.2. Ignoring, as Tomcat can run
            // fine without the shutdown hook.
        }
        if (await) {
            await();
            stop();
        }
    }


    /**
     * 停止现有服务器实例
     */
    public void stop() {

        try {
            // 首先删除 ShutdownHook, 因此 server.stop() 不会执行两次
            if (useShutdownHook) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
        } catch (Throwable t) {
            // This will fail on JDK 1.2. Ignoring, as Tomcat can run
            // fine without the shutdown hook.
        }

        // Shut down the server
        if (server instanceof Lifecycle) {
            try {
                ((Lifecycle) server).stop();
            } catch (LifecycleException e) {
                log.error("Catalina.stop", e);
            }
        }
    }


    /**
     * 等待和关机.
     */
    public void await() {
        server.await();
    }


    /**
     * 打印此应用程序的使用信息.
     */
    protected void usage() {
        System.out.println
            ("usage: java org.apache.catalina.startup.Catalina"
             + " [ -config {pathname} ]"
             + " [ -nonaming ] { start | stop }");
    }


    // --------------------------------------- CatalinaShutdownHook Inner Class

    // XXX Should be moved to embedded !
    /**
     * 关闭钩子，干净的关闭 Catalina.
     */
    protected class CatalinaShutdownHook extends Thread {
        public void run() {
            if (server != null) {
                Catalina.this.stop();
            }
        }
    }
    
    
    private static org.apache.commons.logging.Log log= org.apache.commons.logging.LogFactory.getLog( Catalina.class );

}


// ------------------------------------------------------------ Private Classes


/**
 * 为堆栈上的顶级对象设置父类装入器的规则, 必须是一个<code>Container</code>.
 */
final class SetParentClassLoaderRule extends Rule {

    public SetParentClassLoaderRule(ClassLoader parentClassLoader) {

        this.parentClassLoader = parentClassLoader;

    }

    ClassLoader parentClassLoader = null;

    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {

        if (digester.getLogger().isDebugEnabled())
            digester.getLogger().debug("Setting parent class loader");

        Container top = (Container) digester.peek();
        top.setParentClassLoader(parentClassLoader);

    }
}
