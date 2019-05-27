package org.apache.catalina.startup;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.StringTokenizer;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import org.apache.catalina.security.SecurityClassLoad;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Catalina加载类. 这个应用程序构建为装载Catalina内部类的类加载器(通过积累的所有JAR文件中找到的"catalina.home"目录下的"server"目录),
 * 并开始定期执行容器. 这种迂回的方法的目的是保持Catalina内部类的类路径(以及它们所依赖的任何其他类，如XML解析器)，因此系统应用程序的类不可见.
 */
public final class Bootstrap {

    private static Log log = LogFactory.getLog(Bootstrap.class);
    
    // -------------------------------------------------------------- Constants


    protected static final String CATALINA_HOME_TOKEN = "${catalina.home}";
    protected static final String CATALINA_BASE_TOKEN = "${catalina.base}";


    // ------------------------------------------------------- Static Variables


    private static final String JMX_ERROR_MESSAGE =
        "This release of Apache Tomcat was packaged to run on J2SE 5.0 \n"
        + "or later. It can be run on earlier JVMs by downloading and \n"
        + "installing a compatibility package from the Apache Tomcat \n"
        + "binary download page.";


    /**
     * main方法使用的守护对象.
     */
    private static Bootstrap daemon = null;


    // -------------------------------------------------------------- Variables


    /**
     * 守护对象的引用.
     */
    private Object catalinaDaemon = null;


    protected ClassLoader commonLoader = null;
    protected ClassLoader catalinaLoader = null;
    protected ClassLoader sharedLoader = null;


    // -------------------------------------------------------- Private Methods


    private void initClassLoaders() {
        try {
            commonLoader = createClassLoader("common", null);
            if( commonLoader == null ) {
                // 没有配置文件, 默认是这个loader - 我们可能在一个 '单例'环境中.
                commonLoader=this.getClass().getClassLoader();
            }
            catalinaLoader = createClassLoader("server", commonLoader);
            sharedLoader = createClassLoader("shared", commonLoader);
        } catch (Throwable t) {
            log.error("Class loader creation threw exception", t);
            System.exit(1);
        }
    }


    private ClassLoader createClassLoader(String name, ClassLoader parent)
        throws Exception {

        String value = CatalinaProperties.getProperty(name + ".loader");
        if ((value == null) || (value.equals("")))
            return parent;

        ArrayList unpackedList = new ArrayList();
        ArrayList packedList = new ArrayList();
        ArrayList urlList = new ArrayList();

        StringTokenizer tokenizer = new StringTokenizer(value, ",");
        while (tokenizer.hasMoreElements()) {
            String repository = tokenizer.nextToken();

            // Local repository
            boolean packed = false;
            if (repository.startsWith(CATALINA_HOME_TOKEN)) {
                repository = getCatalinaHome()
                    + repository.substring(CATALINA_HOME_TOKEN.length());
            } else if (repository.startsWith(CATALINA_BASE_TOKEN)) {
                repository = getCatalinaBase()
                    + repository.substring(CATALINA_BASE_TOKEN.length());
            }

            // 检查一个 JAR URL 存储库
            try {
                urlList.add(new URL(repository));
                continue;
            } catch (MalformedURLException e) {
                // Ignore
            }

            if (repository.endsWith("*.jar")) {
                packed = true;
                repository = repository.substring
                    (0, repository.length() - "*.jar".length());
            }
            if (packed) {
                packedList.add(new File(repository));
            } else {
                unpackedList.add(new File(repository));
            }
        }

        File[] unpacked = (File[]) unpackedList.toArray(new File[0]);
        File[] packed = (File[]) packedList.toArray(new File[0]);
        URL[] urls = (URL[]) urlList.toArray(new URL[0]);

        ClassLoader classLoader = ClassLoaderFactory.createClassLoader
            (unpacked, packed, urls, parent);

        // 检索 MBean 服务器
        MBeanServer mBeanServer = null;
        if (MBeanServerFactory.findMBeanServer(null).size() > 0) {
            mBeanServer =
                (MBeanServer) MBeanServerFactory.findMBeanServer(null).get(0);
        } else {
            mBeanServer = MBeanServerFactory.createMBeanServer();
        }

        // 注册服务器类加载器
        ObjectName objectName =
            new ObjectName("Catalina:type=ServerClassLoader,name=" + name);
        mBeanServer.registerMBean(classLoader, objectName);

        return classLoader;
    }


    /**
     * 初始化守护进程.
     */
    public void init() throws Exception {

        // Set Catalina path
        setCatalinaHome();
        setCatalinaBase();

        initClassLoaders();

        Thread.currentThread().setContextClassLoader(catalinaLoader);

        SecurityClassLoad.securityClassLoad(catalinaLoader);

        // 加载启动类并调用它的 process() 方法
        if (log.isDebugEnabled())
            log.debug("Loading startup class");
        Class startupClass =
            catalinaLoader.loadClass
            ("org.apache.catalina.startup.Catalina");
        Object startupInstance = startupClass.newInstance();

        // 设置共享扩展类装入器
        if (log.isDebugEnabled())
            log.debug("Setting startup class properties");
        String methodName = "setParentClassLoader";
        Class paramTypes[] = new Class[1];
        paramTypes[0] = Class.forName("java.lang.ClassLoader");
        Object paramValues[] = new Object[1];
        paramValues[0] = sharedLoader;
        Method method =
            startupInstance.getClass().getMethod(methodName, paramTypes);
        method.invoke(startupInstance, paramValues);

        catalinaDaemon = startupInstance;
    }


    /**
     * 加载守护进程.
     */
    private void load(String[] arguments) throws Exception {

        // 调用 load() 方法
        String methodName = "load";
        Object param[];
        Class paramTypes[];
        if (arguments==null || arguments.length==0) {
            paramTypes = null;
            param = null;
        } else {
            paramTypes = new Class[1];
            paramTypes[0] = arguments.getClass();
            param = new Object[1];
            param[0] = arguments;
        }
        Method method = 
            catalinaDaemon.getClass().getMethod(methodName, paramTypes);
        if (log.isDebugEnabled())
            log.debug("Calling startup class " + method);
        method.invoke(catalinaDaemon, param);
    }


    // ----------------------------------------------------------- Main Program


    /**
     * 加载 Catalina 守护进程.
     */
    public void init(String[] arguments) throws Exception {
        init();
        load(arguments);
    }


    /**
     * 启动 Catalina 守护进程.
     */
    public void start() throws Exception {
        if( catalinaDaemon==null ) init();

        Method method = catalinaDaemon.getClass().getMethod("start", null);
        method.invoke(catalinaDaemon, null);
    }


    /**
     * 关闭 Catalina 守护进程.
     */
    public void stop() throws Exception {
        Method method = catalinaDaemon.getClass().getMethod("stop", null);
        method.invoke(catalinaDaemon, null);
    }


    /**
     * 停止standlone服务器.
     */
    public void stopServer() throws Exception {
        Method method = catalinaDaemon.getClass().getMethod("stopServer", null);
        method.invoke(catalinaDaemon, null);
    }


   /**
     * 停止standlone服务器.
     */
    public void stopServer(String[] arguments) throws Exception {

        Object param[];
        Class paramTypes[];
        if (arguments==null || arguments.length==0) {
            paramTypes = null;
            param = null;
        } else {
            paramTypes = new Class[1];
            paramTypes[0] = arguments.getClass();
            param = new Object[1];
            param[0] = arguments;
        }
        Method method = catalinaDaemon.getClass().getMethod("stopServer", paramTypes);
        method.invoke(catalinaDaemon, param);
    }


    /**
     * Set flag.
     */
    public void setAwait(boolean await) throws Exception {

        Class paramTypes[] = new Class[1];
        paramTypes[0] = Boolean.TYPE;
        Object paramValues[] = new Object[1];
        paramValues[0] = new Boolean(await);
        Method method = 
            catalinaDaemon.getClass().getMethod("setAwait", paramTypes);
        method.invoke(catalinaDaemon, paramValues);
    }

    public boolean getAwait() throws Exception {
        Class paramTypes[] = new Class[0];
        Object paramValues[] = new Object[0];
        Method method =
            catalinaDaemon.getClass().getMethod("getAwait", paramTypes);
        Boolean b=(Boolean)method.invoke(catalinaDaemon, paramValues);
        return b.booleanValue();
    }


    /**
     * 销毁 Catalina 守护进程.
     */
    public void destroy() {
    }


    /**
     * Main method, 只用于测试.
     *
     * @param args 要处理的命令行参数
     */
    public static void main(String args[]) {

        try {
            // 尝试加载JMX类
            new ObjectName("test:foo=bar");
        } catch (Throwable t) {
            System.out.println(JMX_ERROR_MESSAGE);
            try {
                // 在退出之前给用户一些时间阅读消息
                Thread.sleep(5000);
            } catch (Exception ex) {
            }
            return;
        }

        if (daemon == null) {
            daemon = new Bootstrap();
            try {
                daemon.init();
            } catch (Throwable t) {
                t.printStackTrace();
                return;
            }
        }

        try {
            String command = "start";
            if (args.length > 0) {
                command = args[args.length - 1];
            }

            if (command.equals("startd")) {
                args[0] = "start";
                daemon.load(args);
                daemon.start();
            } else if (command.equals("stopd")) {
                args[0] = "stop";
                daemon.stop();
            } else if (command.equals("start")) {
                daemon.setAwait(true);
                daemon.load(args);
                daemon.start();
            } else if (command.equals("stop")) {
                daemon.stopServer(args);
            } else {
                log.warn("Bootsrap: command \"" + command + "\" does not exist.");
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void setCatalinaHome(String s) {
        System.setProperty( "catalina.home", s );
    }

    public void setCatalinaBase(String s) {
        System.setProperty( "catalina.base", s );
    }


    /**
     * 设置<code>catalina.base</code>系统属性为当前工作目录，如果还没有设置.
     */
    private void setCatalinaBase() {

        if (System.getProperty("catalina.base") != null)
            return;
        if (System.getProperty("catalina.home") != null)
            System.setProperty("catalina.base", System.getProperty("catalina.home"));
        else
            System.setProperty("catalina.base", System.getProperty("user.dir"));
    }


    /**
     * 设置<code>catalina.home</code>系统属性为当前工作目录，如果还没有设置.
     */
    private void setCatalinaHome() {

        if (System.getProperty("catalina.home") != null)
            return;
        File bootstrapJar = 
            new File(System.getProperty("user.dir"), "bootstrap.jar");
        if (bootstrapJar.exists()) {
            try {
                System.setProperty("catalina.home", 
                     (new File(System.getProperty("user.dir"), ".."))
                     .getCanonicalPath());
            } catch (Exception e) {
                // Ignore
                System.setProperty("catalina.home", System.getProperty("user.dir"));
            }
        } else {
            System.setProperty("catalina.home", System.getProperty("user.dir"));
        }
    }


    /**
     * 获取catalina.home 环境变量的值.
     */
    public static String getCatalinaHome() {
        return System.getProperty("catalina.home", System.getProperty("user.dir"));
    }


    /**
     * 获取 catalina.base 环境变量的值.
     */
    public static String getCatalinaBase() {
        return System.getProperty("catalina.base", getCatalinaHome());
    }
}
