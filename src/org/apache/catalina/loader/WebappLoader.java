package org.apache.catalina.loader;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;
import java.util.ArrayList;
import java.util.jar.JarFile;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.naming.Binding;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.servlet.ServletContext;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Loader;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;
import org.apache.commons.modeler.Registry;
import org.apache.naming.resources.DirContextURLStreamHandler;
import org.apache.naming.resources.DirContextURLStreamHandlerFactory;
import org.apache.naming.resources.Resource;

/**
 * Classloader实现类，它专门以最有效的方式处理Web应用程序, 在Catalina意识中(所有资源的访问是通过DirContext接口).
 * 这个类装载器支持java类修改的检测, 它可以用来实现自动重新加载支持.
 * <p>
 * 这个类装载器是通过添加目录的路径配置,JAR 文件, 和ZIP 文件,通过<code>addRepository()</code>方法,
 * 在调用<code>start()</code>方法之前. 当需要一个新类时, 首先将咨询这些存储库以确定类的位置. 如果它不在, 将使用系统类装入器代替.
 */
public class WebappLoader implements Lifecycle, Loader, PropertyChangeListener, MBeanRegistration  {

    // ----------------------------------------------------------- Constructors


    public WebappLoader() {
        this(null);
    }


    /**
     * @param parent 父类加载器
     */
    public WebappLoader(ClassLoader parent) {
        super();
        this.parentClassLoader = parent;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 类的第一个加载.
     */
    private static boolean first = true;


    /**
     * 被管理的类加载器.
     */
    private WebappClassLoader classLoader = null;


    /**
     * 关联的Container.
     */
    private Container container = null;


    /**
     * 将用于ClassLoader配置的“遵循标准委托模型”标志.
     */
    private boolean delegate = false;


    /**
     * 描述信息.
     */
    private static final String info =
        "org.apache.catalina.loader.WebappLoader/1.0";


    /**
     * 生命周期事件支持.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * ClassLoader实现类的类名.
     * 这个类应该继承WebappClassLoader, 否则, 另外一个加载器实现类将被使用
     */
    private String loaderClass =
        "org.apache.catalina.loader.WebappClassLoader";


    /**
     * 将创建的类装入器的父类装入器.
     */
    private ClassLoader parentClassLoader = null;


    /**
     * 重载标志.
     */
    private boolean reloadable = false;


    /**
     * 关联的库集合.
     */
    private String repositories[] = new String[0];


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 是否已启动?
     */
    private boolean started = false;


    /**
     * 属性修改支持.
     */
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * 加载器的Classpath.
     */
    private String classpath = null;


    /**
     * 在加载器中设置的库, 对于JMX.
     */
    private ArrayList loaderRepositories = null;


    // ------------------------------------------------------------- Properties


    /**
     * 返回使用的Java类加载器.
     */
    public ClassLoader getClassLoader() {
        return ((ClassLoader) classLoader);
    }


    /**
     * 返回Logger关联的Container.
     */
    public Container getContainer() {
        return (container);
    }


    /**
     * 设置关联的Container.
     *
     * @param container The associated Container
     */
    public void setContainer(Container container) {
        // Deregister from the old Container (if any)
        if ((this.container != null) && (this.container instanceof Context))
            ((Context) this.container).removePropertyChangeListener(this);

        // Process this property change
        Container oldContainer = this.container;
        this.container = container;
        support.firePropertyChange("container", oldContainer, this.container);

        // Register with the new Container (if any)
        if ((this.container != null) && (this.container instanceof Context)) {
            setReloadable( ((Context) this.container).getReloadable() );
            ((Context) this.container).addPropertyChangeListener(this);
        }
    }


    /**
     * 返回用于配置ClassLoader的“遵循标准委托模型”标志
     */
    public boolean getDelegate() {
        return (this.delegate);
    }


    /**
     * 设置用于配置ClassLoader的“遵循标准委托模型”标志
     *
     * @param delegate The new flag
     */
    public void setDelegate(boolean delegate) {
        boolean oldDelegate = this.delegate;
        this.delegate = delegate;
        support.firePropertyChange("delegate", new Boolean(oldDelegate),
                                   new Boolean(this.delegate));
    }


    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 返回ClassLoader的类名
     */
    public String getLoaderClass() {
        return (this.loaderClass);
    }


    /**
     * 设置ClassLoader类名.
     *
     * @param loaderClass The new ClassLoader class name
     */
    public void setLoaderClass(String loaderClass) {
        this.loaderClass = loaderClass;
    }


    /**
     * 返回重新加载标志.
     */
    public boolean getReloadable() {
        return (this.reloadable);
    }


    /**
     * 设置重新加载标志.
     *
     * @param reloadable The new reloadable flag
     */
    public void setReloadable(boolean reloadable) {
        // Process this property change
        boolean oldReloadable = this.reloadable;
        this.reloadable = reloadable;
        support.firePropertyChange("reloadable",
                                   new Boolean(oldReloadable),
                                   new Boolean(this.reloadable));
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个属性修改监听器.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }


    /**
     * 添加一个新的库.
     *
     * @param repository Repository to be added
     */
    public void addRepository(String repository) {
        if (log.isDebugEnabled())
            log.debug(sm.getString("webappLoader.addRepository", repository));

        for (int i = 0; i < repositories.length; i++) {
            if (repository.equals(repositories[i]))
                return;
        }
        String results[] = new String[repositories.length + 1];
        for (int i = 0; i < repositories.length; i++)
            results[i] = repositories[i];
        results[repositories.length] = repository;
        repositories = results;

        if (started && (classLoader != null)) {
            classLoader.addRepository(repository);
            if( loaderRepositories != null ) loaderRepositories.add(repository);
            setClassPath();
        }
    }


    /**
     * 执行周期任务, 例如重新加载, etc. 该方法将在该容器的类加载上下文中被调用.
     * 异常将被捕获和记录.
     */
    public void backgroundProcess() {
        if (reloadable && modified()) {
            try {
                Thread.currentThread().setContextClassLoader
                    (WebappLoader.class.getClassLoader());
                if (container instanceof StandardContext) {
                    ((StandardContext) container).reload();
                }
            } finally {
                if (container.getLoader() != null) {
                    Thread.currentThread().setContextClassLoader
                        (container.getLoader().getClassLoader());
                }
            }
        } else {
            closeJARs(false);
        }
    }


    /**
     * 返回库定义的集合.
     * 如果没有, 返回零长度数组.(因为字符串是不可变的).
     */
    public String[] findRepositories() {
        return ((String[])repositories.clone());
    }

    public String[] getRepositories() {
        return ((String[])repositories.clone());
    }

    /** 此加载程序的额外存储库
     */
    public String getRepositoriesString() {
        StringBuffer sb=new StringBuffer();
        for( int i=0; i<repositories.length ; i++ ) {
            sb.append( repositories[i]).append(":");
        }
        return sb.toString();
    }

    public String[] getLoaderRepositories() {
        if( loaderRepositories==null ) return  null;
        String res[]=new String[ loaderRepositories.size()];
        loaderRepositories.toArray(res);
        return res;
    }

    public String getLoaderRepositoriesString() {
        String repositories[]=getLoaderRepositories();
        StringBuffer sb=new StringBuffer();
        for( int i=0; i<repositories.length ; i++ ) {
            sb.append( repositories[i]).append(":");
        }
        return sb.toString();
    }


    /** 
     * Classpath, 在org.apache.catalina.jsp_classpath 上下文属性中设置
     *
     * @return The classpath
     */
    public String getClasspath() {
        return classpath;
    }


    /**
     * 与此加载程序关联的内部存储库是否已被修改,
     * 这样，要重新加载类?
     */
    public boolean modified() {
        return (classLoader.modified());
    }


    /**
     * 用于周期性信号的类装载器释放JAR资源.
     */
    public void closeJARs(boolean force) {
        if (classLoader !=null){
            classLoader.closeJARs(force);
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
        StringBuffer sb = new StringBuffer("WebappLoader[");
        if (container != null)
            sb.append(container.getName());
        sb.append("]");
        return (sb.toString());
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * 添加一个生命周期事件监听器.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }


    /**
     * 获取这个生命周期关联的监听器.
     * 如果没有, 返回零长度数组.
     */
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }


    /**
     * 移除一个生命周期事件监听器.
     *
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }

    private boolean initialized=false;

    public void init() {
        initialized=true;

        if( oname==null ) {
            // not registered yet - standalone or API
            if( container instanceof StandardContext) {
                // Register ourself. The container must be a webapp
                try {
                    StandardContext ctx=(StandardContext)container;
                    Engine eng=(Engine)ctx.getParent().getParent();
                    String path = ctx.getPath();
                    if (path.equals("")) {
                        path = "/";
                    }   
                    oname=new ObjectName(ctx.getEngineName() + ":type=Loader,path=" +
                                path + ",host=" + ctx.getParent().getName());
                    Registry.getRegistry(null, null).registerComponent(this, oname, null);
                    controller=oname;
                } catch (Exception e) {
                    log.error("Error registering loader", e );
                }
            }
        }

        if( container == null ) {
            // JMX created the loader
            // TODO
        }
    }

    public void destroy() {
        if( controller==oname ) {
            // Self-registration, undo it
            Registry.getRegistry(null, null).unregisterComponent(oname);
            oname = null;
        }
        initialized = false;

    }

    /**
     * Start this component, initializing our associated class loader.
     *
     * @exception LifecycleException if a lifecycle error occurs
     */
    public void start() throws LifecycleException {
        // Validate and update our current component state
        if( ! initialized ) init();
        if (started)
            throw new LifecycleException
                (sm.getString("webappLoader.alreadyStarted"));
        if (log.isDebugEnabled())
            log.debug(sm.getString("webappLoader.starting"));
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        if (container.getResources() == null) {
            log.info("No resources for " + container);
            return;
        }
        // 注册一个JNDI协议流处理工厂
        URLStreamHandlerFactory streamHandlerFactory =
            new DirContextURLStreamHandlerFactory();
        if (first) {
            first = false;
            try {
                URL.setURLStreamHandlerFactory(streamHandlerFactory);
            } catch (Exception e) {
                // 记录并继续, 这不是关键
                log.error("Error registering jndi stream handler", e);
            } catch (Throwable t) {
                // 这很可能是双重注册
                log.info("Dual registration of jndi stream handler: " 
                         + t.getMessage());
            }
        }

        // 基于我们当前的存储库列表, 创建类加载器
        try {

            classLoader = createClassLoader();
            classLoader.setResources(container.getResources());
            classLoader.setDelegate(this.delegate);
            if (container instanceof StandardContext)
                classLoader.setAntiJARLocking(((StandardContext) container).getAntiJARLocking());

            for (int i = 0; i < repositories.length; i++) {
                classLoader.addRepository(repositories[i]);
            }

            // Configure our repositories
            setRepositories();
            setClassPath();

            setPermissions();

            if (classLoader instanceof Lifecycle)
                ((Lifecycle) classLoader).start();

            // Binding the Webapp class loader to the directory context
            DirContextURLStreamHandler.bind
                ((ClassLoader) classLoader, this.container.getResources());

            StandardContext ctx=(StandardContext)container;
            Engine eng=(Engine)ctx.getParent().getParent();
            String path = ctx.getPath();
            if (path.equals("")) {
                path = "/";
            }   
            ObjectName cloname = new ObjectName
                (ctx.getEngineName() + ":type=WebappClassLoader,path="
                 + path + ",host=" + ctx.getParent().getName());
            Registry.getRegistry(null, null)
                .registerComponent(classLoader, cloname, null);

        } catch (Throwable t) {
            log.error( "LifecycleException ", t );
            throw new LifecycleException("start: ", t);
        }

    }


    /**
     * Stop this component, finalizing our associated class loader.
     *
     * @exception LifecycleException if a lifecycle error occurs
     */
    public void stop() throws LifecycleException {

        // Validate and update our current component state
        if (!started)
            throw new LifecycleException
                (sm.getString("webappLoader.notStarted"));
        if (log.isDebugEnabled())
            log.debug(sm.getString("webappLoader.stopping"));
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        // Remove context attributes as appropriate
        if (container instanceof Context) {
            ServletContext servletContext =
                ((Context) container).getServletContext();
            servletContext.removeAttribute(Globals.CLASS_PATH_ATTR);
        }

        // Throw away our current class loader
        if (classLoader instanceof Lifecycle)
            ((Lifecycle) classLoader).stop();
        DirContextURLStreamHandler.unbind((ClassLoader) classLoader);

        try {
            StandardContext ctx=(StandardContext)container;
            Engine eng=(Engine)ctx.getParent().getParent();
            String path = ctx.getPath();
            if (path.equals("")) {
                path = "/";
            }
            ObjectName cloname = new ObjectName
                (ctx.getEngineName() + ":type=WebappClassLoader,path="
                 + path + ",host=" + ctx.getParent().getName());
            Registry.getRegistry(null, null).unregisterComponent(cloname);
        } catch (Throwable t) {
            log.error( "LifecycleException ", t );
        }

        classLoader = null;

        destroy();
    }


    // ----------------------------------------- PropertyChangeListener Methods


    /**
     * 处理属性修改事件.
     *
     * @param event The property change event that has occurred
     */
    public void propertyChange(PropertyChangeEvent event) {

        // Validate the source of this event
        if (!(event.getSource() instanceof Context))
            return;
        Context context = (Context) event.getSource();

        // Process a relevant property change
        if (event.getPropertyName().equals("reloadable")) {
            try {
                setReloadable
                    ( ((Boolean) event.getNewValue()).booleanValue() );
            } catch (NumberFormatException e) {
                log.error(sm.getString("webappLoader.reloadable",
                                 event.getNewValue().toString()));
            }
        }
    }


    // ------------------------------------------------------- Private Methods


    /**
     * 创建关联的classLoader.
     */
    private WebappClassLoader createClassLoader()
        throws Exception {

        Class clazz = Class.forName(loaderClass);
        WebappClassLoader classLoader = null;

        if (parentClassLoader == null) {
            parentClassLoader = Thread.currentThread().getContextClassLoader();
        }
        Class[] argTypes = { ClassLoader.class };
        Object[] args = { parentClassLoader };
        Constructor constr = clazz.getConstructor(argTypes);
        classLoader = (WebappClassLoader) constr.newInstance(args);

        return classLoader;
    }


    /**
     * 配置关联的类加载器权限.
     */
    private void setPermissions() {

        if (System.getSecurityManager() == null)
            return;
        if (!(container instanceof Context))
            return;

        // Tell the class loader the root of the context
        ServletContext servletContext =
            ((Context) container).getServletContext();

        // 分配工作目录的权限
        File workDir =
            (File) servletContext.getAttribute(Globals.WORK_DIR_ATTR);
        if (workDir != null) {
            try {
                String workDirPath = workDir.getCanonicalPath();
                classLoader.addPermission
                    (new FilePermission(workDirPath, "read,write"));
                classLoader.addPermission
                    (new FilePermission(workDirPath + File.separator + "-", 
                                        "read,write,delete"));
            } catch (IOException e) {
                // Ignore
            }
        }

        try {
            URL rootURL = servletContext.getResource("/");
            classLoader.addPermission(rootURL);

            String contextRoot = servletContext.getRealPath("/");
            if (contextRoot != null) {
                try {
                    contextRoot = (new File(contextRoot)).getCanonicalPath();
                    classLoader.addPermission(contextRoot);
                } catch (IOException e) {
                    // Ignore
                }
            }

            URL classesURL = servletContext.getResource("/WEB-INF/classes/");
            classLoader.addPermission(classesURL);
            URL libURL = servletContext.getResource("/WEB-INF/lib/");
            classLoader.addPermission(libURL);

            if (contextRoot != null) {
                if (libURL != null) {
                    File rootDir = new File(contextRoot);
                    File libDir = new File(rootDir, "WEB-INF/lib/");
                    try {
                        String path = libDir.getCanonicalPath();
                        classLoader.addPermission(path);
                    } catch (IOException e) {
                    }
                }
            } else {
                if (workDir != null) {
                    if (libURL != null) {
                        File libDir = new File(workDir, "WEB-INF/lib/");
                        try {
                            String path = libDir.getCanonicalPath();
                            classLoader.addPermission(path);
                        } catch (IOException e) {
                        }
                    }
                    if (classesURL != null) {
                        File classesDir = new File(workDir, "WEB-INF/classes/");
                        try {
                            String path = classesDir.getCanonicalPath();
                            classLoader.addPermission(path);
                        } catch (IOException e) {
                        }
                    }
                }
            }

        } catch (MalformedURLException e) {
        }
    }


    /**
     * 为类装入器配置存储库, 基于关联的Context.
     */
    private void setRepositories() {

        if (!(container instanceof Context))
            return;
        ServletContext servletContext =
            ((Context) container).getServletContext();
        if (servletContext == null)
            return;

        loaderRepositories=new ArrayList();
        // 加载工作目录
        File workDir =
            (File) servletContext.getAttribute(Globals.WORK_DIR_ATTR);
        if (workDir == null) {
            log.info("No work dir for " + servletContext);
        }

        if( log.isDebugEnabled()) 
            log.debug(sm.getString("webappLoader.deploy", workDir.getAbsolutePath()));

        classLoader.setWorkDir(workDir);

        DirContext resources = container.getResources();

        // 设置类存储库(/WEB-INF/classes), 如果它真的存在
        String classesPath = "/WEB-INF/classes";
        DirContext classes = null;

        try {
            Object object = resources.lookup(classesPath);
            if (object instanceof DirContext) {
                classes = (DirContext) object;
            }
        } catch(NamingException e) {
            // Silent catch: it's valid that no /WEB-INF/classes collection
            // exists
        }

        if (classes != null) {
            File classRepository = null;

            String absoluteClassesPath =
                servletContext.getRealPath(classesPath);

            if (absoluteClassesPath != null) {
                classRepository = new File(absoluteClassesPath);
            } else {
                classRepository = new File(workDir, classesPath);
                classRepository.mkdirs();
                copyDir(classes, classRepository);
            }
            if(log.isDebugEnabled())
                log.debug(sm.getString("webappLoader.classDeploy", classesPath,
                             classRepository.getAbsolutePath()));


            // Adding the repository to the class loader
            classLoader.addRepository(classesPath + "/", classRepository);
            loaderRepositories.add(classesPath + "/" );
        }

        // Setting up the JAR repository (/WEB-INF/lib), if it exists
        String libPath = "/WEB-INF/lib";

        classLoader.setJarPath(libPath);

        DirContext libDir = null;
        // Looking up directory /WEB-INF/lib in the context
        try {
            Object object = resources.lookup(libPath);
            if (object instanceof DirContext)
                libDir = (DirContext) object;
        } catch(NamingException e) {
            // Silent catch: it's valid that no /WEB-INF/lib collection
            // exists
        }

        if (libDir != null) {

            boolean copyJars = false;
            String absoluteLibPath = servletContext.getRealPath(libPath);

            File destDir = null;

            if (absoluteLibPath != null) {
                destDir = new File(absoluteLibPath);
            } else {
                copyJars = true;
                destDir = new File(workDir, libPath);
                destDir.mkdirs();
            }

            // Looking up directory /WEB-INF/lib in the context
            try {
                NamingEnumeration enumeration = resources.listBindings(libPath);
                while (enumeration.hasMoreElements()) {

                    Binding binding = (Binding) enumeration.nextElement();
                    String filename = libPath + "/" + binding.getName();
                    if (!filename.endsWith(".jar"))
                        continue;

                    // 在工作目录中复制JAR, 总是(否则JAR文件将被锁定, 这样就不可能在运行时更新或删除它)
                    File destFile = new File(destDir, binding.getName());

                    if( log.isDebugEnabled())
                    log.debug(sm.getString("webappLoader.jarDeploy", filename,
                                     destFile.getAbsolutePath()));

                    Resource jarResource = (Resource) binding.getObject();
                    if (copyJars) {
                        if (!copy(jarResource.streamContent(),
                                  new FileOutputStream(destFile)))
                            continue;
                    }

                    try {
                        JarFile jarFile = new JarFile(destFile);
                        classLoader.addJar(filename, jarFile, destFile);
                    } catch (Exception ex) {
                        // 如果有空JAR文件，则捕获异常
                        // 应该忽略和加载目录中的其他JAR文件
                    }
                    loaderRepositories.add( filename );
                }
            } catch (NamingException e) {
                // Silent catch: it's valid that no /WEB-INF/lib directory
                // exists
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 为类路径设置适当的上下文属性.
     * 这仅仅是因为Jasper需要它.
     */
    private void setClassPath() {

        // Validate our current state information
        if (!(container instanceof Context))
            return;
        ServletContext servletContext =
            ((Context) container).getServletContext();
        if (servletContext == null)
            return;

        if (container instanceof StandardContext) {
            String baseClasspath = 
                ((StandardContext) container).getCompilerClasspath();
            if (baseClasspath != null) {
                servletContext.setAttribute(Globals.CLASS_PATH_ATTR,
                                            baseClasspath);
                return;
            }
        }

        StringBuffer classpath = new StringBuffer();

        // 从类加载器链中组装类路径信息
        ClassLoader loader = getClassLoader();
        int layers = 0;
        int n = 0;
        while (loader != null) {
            if (!(loader instanceof URLClassLoader)) {
                String cp=getClasspath( loader );
                if( cp==null ) {
                    log.info( "Unknown loader " + loader + " " + loader.getClass());
                    break;
                } else {
                    if (n > 0) 
                        classpath.append(File.pathSeparator);
                    classpath.append(cp);
                    n++;
                }
                break;
                //continue;
            }
            URL repositories[] =
                ((URLClassLoader) loader).getURLs();
            for (int i = 0; i < repositories.length; i++) {
                String repository = repositories[i].toString();
                if (repository.startsWith("file://"))
                    repository = repository.substring(7);
                else if (repository.startsWith("file:"))
                    repository = repository.substring(5);
                else if (repository.startsWith("jndi:"))
                    repository =
                        servletContext.getRealPath(repository.substring(5));
                else
                    continue;
                if (repository == null)
                    continue;
                if (n > 0)
                    classpath.append(File.pathSeparator);
                classpath.append(repository);
                n++;
            }
            loader = loader.getParent();
            layers++;
        }

        this.classpath=classpath.toString();

        // 将组装的类路径存储为servlet上下文属性
        servletContext.setAttribute(Globals.CLASS_PATH_ATTR, classpath.toString());
    }

    // 尝试提取类路径, 从一个不是URLClassLoader的加载器
    private String getClasspath( ClassLoader loader ) {
        try {
            Method m=loader.getClass().getMethod("getClasspath", new Class[] {});
            if( log.isTraceEnabled())
                log.trace("getClasspath " + m );
            if( m==null ) return null;
            Object o=m.invoke( loader, new Object[] {} );
            if( log.isDebugEnabled() )
                log.debug("gotClasspath " + o);
            if( o instanceof String )
                return (String)o;
            return null;
        } catch( Exception ex ) {
            if (log.isDebugEnabled())
                log.debug("getClasspath ", ex);
        }
        return null;
    }

    /**
     * 复制目录.
     */
    private boolean copyDir(DirContext srcDir, File destDir) {

        try {
            NamingEnumeration enumeration = srcDir.list("");
            while (enumeration.hasMoreElements()) {
                NameClassPair ncPair =
                    (NameClassPair) enumeration.nextElement();
                String name = ncPair.getName();
                Object object = srcDir.lookup(name);
                File currentFile = new File(destDir, name);
                if (object instanceof Resource) {
                    InputStream is = ((Resource) object).streamContent();
                    OutputStream os = new FileOutputStream(currentFile);
                    if (!copy(is, os))
                        return false;
                } else if (object instanceof InputStream) {
                    OutputStream os = new FileOutputStream(currentFile);
                    if (!copy((InputStream) object, os))
                        return false;
                } else if (object instanceof DirContext) {
                    currentFile.mkdir();
                    copyDir((DirContext) object, currentFile);
                }
            }

        } catch (NamingException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
        return true;
    }


    /**
     * 将文件复制到指定的临时目录. 
     * 这仅仅是因为Jasper需要它.
     */
    private boolean copy(InputStream is, OutputStream os) {

        try {
            byte[] buf = new byte[4096];
            while (true) {
                int len = is.read(buf);
                if (len < 0)
                    break;
                os.write(buf, 0, len);
            }
            is.close();
            os.close();
        } catch (IOException e) {
            return false;
        }
        return true;
    }


    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( WebappLoader.class );

    private ObjectName oname;
    private MBeanServer mserver;
    private String domain;
    private ObjectName controller;

    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
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

    public ObjectName getController() {
        return controller;
    }

    public void setController(ObjectName controller) {
        this.controller = controller;
    }
}
