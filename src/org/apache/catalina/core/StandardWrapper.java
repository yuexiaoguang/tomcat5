package org.apache.catalina.core;

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.SingleThreadModel;
import javax.servlet.UnavailableException;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.InstanceEvent;
import org.apache.catalina.InstanceListener;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Loader;
import org.apache.catalina.Wrapper;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.util.Enumerator;
import org.apache.catalina.util.InstanceSupport;
import org.apache.commons.modeler.Registry;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.log.SystemLogHandler;

/**
 * <b>Wrapper</b>接口的标准实现类表示单个servlet定义. 
 * 不允许有子级Containers, 父级Container必须是一个Context.
 */
public class StandardWrapper extends ContainerBase implements ServletConfig, Wrapper, NotificationEmitter {

    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( StandardWrapper.class );

    private static final String[] DEFAULT_SERVLET_METHODS = new String[] {
                                                    "GET", "HEAD", "POST" };

    // ----------------------------------------------------------- Constructors

    public StandardWrapper() {
        super();
        swValve=new StandardWrapperValve();
        pipeline.setBasic(swValve);
        broadcaster = new NotificationBroadcasterSupport();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 此servlet将可用的日期和时间 (毫秒), 如果servlet可用，则为零.
     * 如果这个值等于Long.MAX_VALUE, 这个servlet的不可用性被认为是永久性的.
     */
    private long available = 0L;
    
    /**
     * 用于发送 j2ee 通知. 
     */
    private NotificationBroadcasterSupport broadcaster = null;
    
    /**
     * 当前活动的分配数(即使它们是相同的实例，在非STM servlet上也是如此).
     */
    private int countAllocated = 0;


    /**
     * 关联的外观模式
     */
    private StandardWrapperFacade facade = new StandardWrapperFacade(this);


    /**
     * 描述信息.
     */
    private static final String info = "org.apache.catalina.core.StandardWrapper/1.0";


    /**
     * 这个servlet的实例.
     */
    private Servlet instance = null;


    /**
     * 实例监听器的支持对象.
     */
    private InstanceSupport instanceSupport = new InstanceSupport(this);


    /**
     * 此servlet的JSP文件的上下文相对URI.
     */
    private String jspFile = null;


    /**
     * load-on-startup加载顺序值(负值表示第一个调用).
     */
    private int loadOnStartup = -1;


    private ArrayList mappings = new ArrayList();


    /**
     * 这个servlet的初始化参数, 使用参数名作为key.
     */
    private HashMap parameters = new HashMap();


    /**
     * 此servlet的安全角色引用, 使用角色名作为key.
     * 相应的值是Web应用程序本身的角色名.
     */
    private HashMap references = new HashMap();


    /**
     * run-as标识符
     */
    private String runAs = null;

    /**
     * 通知序列号
     */
    private long sequenceNumber = 0;

    /**
     * 完全限定的servlet类名.
     */
    private String servletClass = null;


    /**
     * 这个servlet是否实现了SingleThreadModel接口?
     */
    private boolean singleThreadModel = false;


    /**
     * 正在卸载servlet实例吗?
     */
    private boolean unloading = false;


    /**
     * STM实例的最大数目
     */
    private int maxInstances = 20;


    /**
     * 一个STM servlet当前加载的实例数.
     */
    private int nInstances = 0;


    /**
     * 包含STM实例的堆栈
     */
    private Stack instancePool = null;


    /**
     * True 如果这个StandardWrapper是用于 JspServlet
     */
    private boolean isJspServlet;


    /**
     * JSP监控MBean的 ObjectName
     */
    private ObjectName jspMonitorON;


    /**
     * 是否转发到 System.out
     */
    private boolean swallowOutput = false;

    // 用于支持 jmx 属性
    private StandardWrapperValve swValve;
    private long loadTime=0;
    private int classLoadTime=0;
    
    /**
     * 当开启 SecurityManager并调用<code>Servlet.init</code>的时候使用.
     */
    private static Class[] classType = new Class[]{ServletConfig.class};
    
    
    /**
     * 当开启 SecurityManager并调用<code>Servlet.service</code>的时候使用.
     */                                                 
    private static Class[] classTypeUsedInService = new Class[]{
                                                         ServletRequest.class,
                                                         ServletResponse.class};
    // ------------------------------------------------------------- Properties


    /**
     * 返回可用的 date/time, 毫秒. 
     * 如果日期/时间在将来, 任何这个servlet的请求将返回一个SC_SERVICE_UNAVAILABLE错误.
     * 如果是零,servlet当前可用.  如果等于Long.MAX_VALUE被认为是永久不可用的.
     */
    public long getAvailable() {
        return (this.available);
    }


    /**
     * 设置可用的date/time, 毫秒. 
     * 如果日期/时间在将来, 任何这个servlet的请求将返回一个SC_SERVICE_UNAVAILABLE错误.
     *
     * @param available The new available date/time
     */
    public void setAvailable(long available) {
        long oldAvailable = this.available;
        if (available > System.currentTimeMillis())
            this.available = available;
        else
            this.available = 0L;
        support.firePropertyChange("available", new Long(oldAvailable),
                                   new Long(this.available));
    }


    /**
     * 返回此servlet的活动分配数, 即使它们都是同一个实例(将真正的servlet没有实现<code>SingleThreadModel</code>.
     */
    public int getCountAllocated() {
        return (this.countAllocated);
    }


    public String getEngineName() {
        return ((StandardContext)getParent()).getEngineName();
    }


    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 返回InstanceSupport对象
     */
    public InstanceSupport getInstanceSupport() {
        return (this.instanceSupport);
    }


    /**
     * 返回JSP文件的上下文相对URI.
     */
    public String getJspFile() {
        return (this.jspFile);
    }


    /**
     * 设置JSP文件的上下文相对URI.
     *
     * @param jspFile JSP file URI
     */
    public void setJspFile(String jspFile) {
        String oldJspFile = this.jspFile;
        this.jspFile = jspFile;
        support.firePropertyChange("jspFile", oldJspFile, this.jspFile);

        // 每一个JSP文件需要由自己的JspServlet及相应的JspMonitoring mbean表示, 因为它可能使用自己的初始化参数初始化
        isJspServlet = true;
    }


    /**
     * 返回load-on-startup属性值(负值表示第一个调用).
     */
    public int getLoadOnStartup() {

        if (isJspServlet && loadOnStartup < 0) {
            /*
             * JspServlet 必须总是预加载, 因为它的实例在注册JMX的时候会使用 (注册JSP监控MBean时)
             */
             return Integer.MAX_VALUE;
        } else {
            return (this.loadOnStartup);
        }
    }


    /**
     * 设置load-on-startup属性值(负值表示第一个调用).
     *
     * @param value New load-on-startup value
     */
    public void setLoadOnStartup(int value) {
        int oldLoadOnStartup = this.loadOnStartup;
        this.loadOnStartup = value;
        support.firePropertyChange("loadOnStartup",
                                   new Integer(oldLoadOnStartup),
                                   new Integer(this.loadOnStartup));
    }



    /**
     * 设置load-on-startup属性值.
     * 为规范, 任何缺少或非数值的值都被转换为零, 这样servlet在启动时仍然会被加载, 但以任意顺序.
     *
     * @param value New load-on-startup value
     */
    public void setLoadOnStartupString(String value) {
        try {
            setLoadOnStartup(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            setLoadOnStartup(0);
        }
    }

    public String getLoadOnStartupString() {
        return Integer.toString( getLoadOnStartup());
    }


    /**
     * 返回当使用单个线程模型servlet时, 将分配的实例的最大数量.
     */
    public int getMaxInstances() {
        return (this.maxInstances);
    }


    /**
     * 设置当使用单个线程模型servlet时, 将分配的实例的最大数量.
     *
     * @param maxInstances New value of maxInstances
     */
    public void setMaxInstances(int maxInstances) {
        int oldMaxInstances = this.maxInstances;
        this.maxInstances = maxInstances;
        support.firePropertyChange("maxInstances", oldMaxInstances,
                                   this.maxInstances);
    }


    /**
     * 设置父级Container, 但只有当它是Context.
     *
     * @param container Proposed parent Container
     */
    public void setParent(Container container) {

        if ((container != null) &&
            !(container instanceof Context))
            throw new IllegalArgumentException
                (sm.getString("standardWrapper.notContext"));
        if (container instanceof StandardContext) {
            swallowOutput = ((StandardContext)container).getSwallowOutput();
        }
        super.setParent(container);

    }


    /**
     * 返回run-as标识符
     */
    public String getRunAs() {
        return (this.runAs);
    }


    /**
     * 设置run-as标识符.
     *
     * @param runAs New run-as identity value
     */
    public void setRunAs(String runAs) {
        String oldRunAs = this.runAs;
        this.runAs = runAs;
        support.firePropertyChange("runAs", oldRunAs, this.runAs);
    }


    /**
     * 返回完全限定的servlet类名.
     */
    public String getServletClass() {
        return (this.servletClass);
    }


    /**
     * 设置完全限定的servlet类名.
     *
     * @param servletClass Servlet class name
     */
    public void setServletClass(String servletClass) {

        String oldServletClass = this.servletClass;
        this.servletClass = servletClass;
        support.firePropertyChange("servletClass", oldServletClass,
                                   this.servletClass);
        if (Constants.JSP_SERVLET_CLASS.equals(servletClass)) {
            isJspServlet = true;
        }
    }



    /**
     * 设置这个servlet的名称.
     * 这个是一个<code>Container.setName()</code>方法的别名, 以及<code>ServletConfig</code>接口要求的<code>getServletName()</code>方法
     *
     * @param name The new name of this servlet
     */
    public void setServletName(String name) {
        setName(name);
    }


    /**
     * 返回<code>true</code>，如果servlet类实现了<code>SingleThreadModel</code>接口.
     */
    public boolean isSingleThreadModel() {
        try {
            loadServlet();
        } catch (Throwable t) {
            ;
        }
        return (singleThreadModel);
    }


    /**
     * 这个servlet当前不可用吗?
     */
    public boolean isUnavailable() {
        if (available == 0L)
            return (false);
        else if (available <= System.currentTimeMillis()) {
            available = 0L;
            return (false);
        } else
            return (true);
    }


    /**
     * 获取底层servlet支持的方法的名称.
     *
     * 底层servlet处理的OPTIONS请求方法和响应头中包含的方法相同.
     *
     * @return Array of names of the methods supported by the underlying
     * servlet
     */
    public String[] getServletMethods() throws ServletException {

        Class servletClazz = loadServlet().getClass();
        if (!javax.servlet.http.HttpServlet.class.isAssignableFrom(
                                                        servletClazz)) {
            return DEFAULT_SERVLET_METHODS;
        }

        HashSet allow = new HashSet();
        allow.add("TRACE");
        allow.add("OPTIONS");
	
        Method[] methods = getAllDeclaredMethods(servletClazz);
        for (int i=0; methods != null && i<methods.length; i++) {
            Method m = methods[i];
	    
            if (m.getName().equals("doGet")) {
                allow.add("GET");
                allow.add("HEAD");
            } else if (m.getName().equals("doPost")) {
                allow.add("POST");
            } else if (m.getName().equals("doPut")) {
                allow.add("PUT");
            } else if (m.getName().equals("doDelete")) {
                allow.add("DELETE");
            }
        }

        String[] methodNames = new String[allow.size()];
        return (String[]) allow.toArray(methodNames);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 从servlet异常中提取根异常.
     * 
     * @param e The servlet exception
     */
    public static Throwable getRootCause(ServletException e) {
        Throwable rootCause = e;
        Throwable rootCauseCheck = null;
        // Extra aggressive rootCause finding
        do {
            try {
                rootCauseCheck = (Throwable)IntrospectionUtils.getProperty
                                            (rootCause, "rootCause");
                if (rootCauseCheck!=null)
                    rootCause = rootCauseCheck;

            } catch (ClassCastException ex) {
                rootCauseCheck = null;
            }
        } while (rootCauseCheck != null);
        return rootCause;
    }


    /**
     * 拒绝再添加子级Container,因为Wrapper是Container体系结构中的最低层级.
     *
     * @param child Child container to be added
     */
    public void addChild(Container child) {
        throw new IllegalStateException(sm.getString("standardWrapper.notChild"));
    }


    /**
     * 添加一个新的servlet初始化参数.
     *
     * @param name Name of this initialization parameter to add
     * @param value Value of this initialization parameter to add
     */
    public void addInitParameter(String name, String value) {
        synchronized (parameters) {
            parameters.put(name, value);
        }
        fireContainerEvent("addInitParameter", name);
    }


    /**
     * 添加一个新的监听器到InstanceEvents.
     *
     * @param listener The new listener
     */
    public void addInstanceListener(InstanceListener listener) {
        instanceSupport.addInstanceListener(listener);
    }


    /**
     * 添加关联的映射.
     *
     * @param mapping 包装器映射
     */
    public void addMapping(String mapping) {
        synchronized (mappings) {
            mappings.add(mapping);
        }
        fireContainerEvent("addMapping", mapping);
    }


    /**
     * 向记录集添加一个新的安全角色引用记录.
     *
     * @param name 此servlet中使用的角色名称
     * @param link Web应用程序中使用的角色名
     */
    public void addSecurityReference(String name, String link) {
        synchronized (references) {
            references.put(name, link);
        }
        fireContainerEvent("addSecurityReference", name);
    }


    /**
     * 分配该servlet的初始化实例，该servlet准备就绪调用它的<code>service()</code>方法.
     * 如果servlet类没有实现<code>SingleThreadModel</code>, 可以立即返回初始化实例.
     * 如果servlet类实现了<code>SingleThreadModel</code>, Wrapper实现类必须确保这个实例不会被再次分配，
     * 直到它被<code>deallocate()</code>释放
     *
     * @exception ServletException 如果servlet init()方法抛出异常
     * @exception ServletException 如果发生加载错误
     */
    public Servlet allocate() throws ServletException {

        // 如果我们正在卸载这个servlet, throw an exception
        if (unloading)
            throw new ServletException
              (sm.getString("standardWrapper.unloading", getName()));

        // If not SingleThreadedModel, return the same instance every time
        if (!singleThreadModel) {

            // Load and initialize our instance if necessary
            if (instance == null) {
                synchronized (this) {
                    if (instance == null) {
                        try {
                            if (log.isDebugEnabled())
                                log.debug("Allocating non-STM instance");

                            instance = loadServlet();
                        } catch (ServletException e) {
                            throw e;
                        } catch (Throwable e) {
                            throw new ServletException
                                (sm.getString("standardWrapper.allocate"), e);
                        }
                    }
                }
            }

            if (!singleThreadModel) {
                if (log.isTraceEnabled())
                    log.trace("  Returning non-STM instance");
                countAllocated++;
                return (instance);
            }

        }

        synchronized (instancePool) {

            while (countAllocated >= nInstances) {
                // Allocate a new instance if possible, or else wait
                if (nInstances < maxInstances) {
                    try {
                        instancePool.push(loadServlet());
                        nInstances++;
                    } catch (ServletException e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new ServletException
                            (sm.getString("standardWrapper.allocate"), e);
                    }
                } else {
                    try {
                        instancePool.wait();
                    } catch (InterruptedException e) {
                        ;
                    }
                }
            }
            if (log.isTraceEnabled())
                log.trace("  Returning allocated STM instance");
            countAllocated++;
            return (Servlet) instancePool.pop();
        }
    }


    /**
     * 将先前分配的servlet返回到可用实例池中.
     * 如果这个servlet类没有实现SingleThreadModel,实际上不需要任何动作
     *
     * @param servlet The servlet to be returned
     *
     * @exception ServletException 如果发生了分配错误
     */
    public void deallocate(Servlet servlet) throws ServletException {

        // If not SingleThreadModel, no action is required
        if (!singleThreadModel) {
            countAllocated--;
            return;
        }

        // Unlock and free this instance
        synchronized (instancePool) {
            countAllocated--;
            instancePool.push(servlet);
            instancePool.notify();
        }
    }


    /**
     * 返回指定的初始化参数名称的值; 或者<code>null</code>.
     *
     * @param name 请求的初始化参数的名称
     */
    public String findInitParameter(String name) {
        synchronized (parameters) {
            return ((String) parameters.get(name));
        }
    }


    /**
     * 返回所有定义的初始化参数的名称.
     */
    public String[] findInitParameters() {
        synchronized (parameters) {
            String results[] = new String[parameters.size()];
            return ((String[]) parameters.keySet().toArray(results));
        }
    }


    /**
     * 返回关联的映射.
     */
    public String[] findMappings() {
        synchronized (mappings) {
            return (String[]) mappings.toArray(new String[mappings.size()]);
        }
    }


    /**
     * 为指定的安全角色引用名称返回安全角色链接; 或者<code>null</code>.
     *
     * @param name 在servlet中使用的安全角色引用
     */
    public String findSecurityReference(String name) {
        synchronized (references) {
            return ((String) references.get(name));
        }
    }


    /**
     * 返回安全角色引用名称的集合; 否则返回一个零长度数组.
     */
    public String[] findSecurityReferences() {
        synchronized (references) {
            String results[] = new String[references.size()];
            return ((String[]) references.keySet().toArray(results));
        }
    }


    /**
     * FIXME: Fooling introspection ...
     */
    public Wrapper findMappingObject() {
        return (Wrapper) getMappingObject();
    }


    /**
     * 加载并初始化此servlet的实例, 如果没有一个初始化实例.
     * 这可以使用，例如，加载servlet被标记在部署描述符是在服务器启动时加载.
     * <p>
     * <b>实现注意</b>: servlet的类名称以<code>org.apache.catalina.</code>开始 (so-called "container" servlets)
     * 由加载这个类的同一个类加载器加载, 而不是当前Web应用程序的类加载器.
     * 这使此类访问Catalina, 防止为Web应用程序加载的类.
     *
     * @exception ServletException 如果servlet init()方法抛出异常
     * @exception ServletException 如果出现其他加载问题
     */
    public synchronized void load() throws ServletException {
        instance = loadServlet();
    }


    /**
     * 加载并初始化此servlet的实例, 如果没有一个初始化实例.
     * 这可以使用，例如，加载servlet被标记在部署描述符是在服务器启动时加载.
     */
    public synchronized Servlet loadServlet() throws ServletException {

        // 如果已经有实例或实例池，则无需做任何事情
        if (!singleThreadModel && (instance != null))
            return instance;

        PrintStream out = System.out;
        if (swallowOutput) {
            SystemLogHandler.startCapture();
        }

        Servlet servlet;
        try {
            long t1=System.currentTimeMillis();
            // 如果这个“servlet”实际上是一个JSP文件，请获取正确的类.
            // HOLD YOUR NOSE - 这是一个问题，避免了在Jasper中的Catalina特定代码 - 为了完全有效, 它也需要通过<jsp-file>元素内容替换servlet 路径
            String actualClass = servletClass;
            if ((actualClass == null) && (jspFile != null)) {
                Wrapper jspWrapper = (Wrapper)
                    ((Context) getParent()).findChild(Constants.JSP_SERVLET_NAME);
                if (jspWrapper != null) {
                    actualClass = jspWrapper.getServletClass();
                    // Merge init parameters
                    String paramNames[] = jspWrapper.findInitParameters();
                    for (int i = 0; i < paramNames.length; i++) {
                        if (parameters.get(paramNames[i]) == null) {
                            parameters.put
                                (paramNames[i], 
                                 jspWrapper.findInitParameter(paramNames[i]));
                        }
                    }
                }
            }

            // 如果没有指定servlet类，则要进行投诉
            if (actualClass == null) {
                unavailable(null);
                throw new ServletException
                    (sm.getString("standardWrapper.notClass", getName()));
            }

            // 获取要使用的类装入器的实例
            Loader loader = getLoader();
            if (loader == null) {
                unavailable(null);
                throw new ServletException
                    (sm.getString("standardWrapper.missingLoader", getName()));
            }

            ClassLoader classLoader = loader.getClassLoader();

            // 容器的特殊case类装入器提供servlet
            if (isContainerProvidedServlet(actualClass) && 
                    ! ((Context)getParent()).getPrivileged() ) {
                // 如果是一个特权上下文 - 使用自己的类装入器工作, 因为它是容器加载器的子级
                classLoader = this.getClass().getClassLoader();
            }

            // 从适当的类装入器加载指定的servlet类
            Class classClass = null;
            try {
                if (SecurityUtil.isPackageProtectionEnabled()){
                    final ClassLoader fclassLoader = classLoader;
                    final String factualClass = actualClass;
                    try{
                        classClass = (Class)AccessController.doPrivileged(
                                new PrivilegedExceptionAction(){
                                    public Object run() throws Exception{
                                        if (fclassLoader != null) {
                                            return fclassLoader.loadClass(factualClass);
                                        } else {
                                            return Class.forName(factualClass);
                                        }
                                    }
                        });
                    } catch(PrivilegedActionException pax){
                        Exception ex = pax.getException();
                        if (ex instanceof ClassNotFoundException){
                            throw (ClassNotFoundException)ex;
                        } else {
                            getServletContext().log( "Error loading "
                                + fclassLoader + " " + factualClass, ex );
                        }
                    }
                } else {
                    if (classLoader != null) {
                        classClass = classLoader.loadClass(actualClass);
                    } else {
                        classClass = Class.forName(actualClass);
                    }
                }
            } catch (ClassNotFoundException e) {
                unavailable(null);
                getServletContext().log( "Error loading " + classLoader + " " + actualClass, e );
                throw new ServletException
                    (sm.getString("standardWrapper.missingClass", actualClass),
                     e);
            }

            if (classClass == null) {
                unavailable(null);
                throw new ServletException
                    (sm.getString("standardWrapper.missingClass", actualClass));
            }

            // 实例化和初始化servlet类本身的实例
            try {
                servlet = (Servlet) classClass.newInstance();
            } catch (ClassCastException e) {
                unavailable(null);
                // 恢复上下文类加载器
                throw new ServletException
                    (sm.getString("standardWrapper.notServlet", actualClass), e);
            } catch (Throwable e) {
                unavailable(null);
                // 恢复上下文类加载器
                throw new ServletException
                    (sm.getString("standardWrapper.instantiate", actualClass), e);
            }

            // 检查是否允许在这个Web应用程序中加载servlet
            if (!isServletAllowed(servlet)) {
                throw new SecurityException
                    (sm.getString("standardWrapper.privilegedServlet",
                                  actualClass));
            }

            // ContainerServlet实例的特殊处理
            if ((servlet instanceof ContainerServlet) &&
                  (isContainerProvidedServlet(actualClass) ||
                    ((Context)getParent()).getPrivileged() )) {
                ((ContainerServlet) servlet).setWrapper(this);
            }

            classLoadTime=(int) (System.currentTimeMillis() -t1);
            // 调用此servlet的初始化方法
            try {
                instanceSupport.fireInstanceEvent(InstanceEvent.BEFORE_INIT_EVENT,
                                                  servlet);

                if( System.getSecurityManager() != null) {

                    Object[] args = new Object[]{((ServletConfig)facade)};
                    SecurityUtil.doAsPrivilege("init",
                                               servlet,
                                               classType,
                                               args);
                    args = null;
                } else {
                    servlet.init(facade);
                }

                // Invoke jspInit on JSP pages
                if ((loadOnStartup >= 0) && (jspFile != null)) {
                    // Invoking jspInit
                    DummyRequest req = new DummyRequest();
                    req.setServletPath(jspFile);
                    req.setQueryString("jsp_precompile=true");
                    DummyResponse res = new DummyResponse();

                    if( System.getSecurityManager() != null) {
                        Object[] args = new Object[]{req, res};
                        SecurityUtil.doAsPrivilege("service",
                                                   servlet,
                                                   classTypeUsedInService,
                                                   args);
                        args = null;
                    } else {
                        servlet.service(req, res);
                    }
                }
                instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                                  servlet);
            } catch (UnavailableException f) {
                instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                                  servlet, f);
                unavailable(f);
                throw f;
            } catch (ServletException f) {
                instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                                  servlet, f);
                // 如果servlet想不可用，它会这么说的, 所以不要调用不可用的(null).
                throw f;
            } catch (Throwable f) {
                getServletContext().log("StandardWrapper.Throwable", f );
                instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                                  servlet, f);
                // 如果servlet想不可用，它会这么说的, 所以不要调用不可用的(null).
                throw new ServletException
                    (sm.getString("standardWrapper.initException", getName()), f);
            }

            // 注册新初始化的实例
            singleThreadModel = servlet instanceof SingleThreadModel;
            if (singleThreadModel) {
                if (instancePool == null)
                    instancePool = new Stack();
            }
            fireContainerEvent("load", this);

            loadTime=System.currentTimeMillis() -t1;
        } finally {
            if (swallowOutput) {
                String log = SystemLogHandler.stopCapture();
                if (log != null && log.length() > 0) {
                    if (getServletContext() != null) {
                        getServletContext().log(log);
                    } else {
                        out.println(log);
                    }
                }
            }
        }
        return servlet;
    }


    /**
     * 删除指定的初始化参数.
     *
     * @param name 要删除的初始化参数名称
     */
    public void removeInitParameter(String name) {
        synchronized (parameters) {
            parameters.remove(name);
        }
        fireContainerEvent("removeInitParameter", name);
    }


    /**
     * 移除一个监听器.
     *
     * @param listener The listener to remove
     */
    public void removeInstanceListener(InstanceListener listener) {
        instanceSupport.removeInstanceListener(listener);
    }


    /**
     * 删除关联的映射
     *
     * @param mapping 要删除的模式
     */
    public void removeMapping(String mapping) {
        synchronized (mappings) {
            mappings.remove(mapping);
        }
        fireContainerEvent("removeMapping", mapping);
    }


    /**
     * 删除指定角色名称的任何安全角色引用.
     *
     * @param name 要删除此servlet中使用的安全角色
     */
    public void removeSecurityReference(String name) {
        synchronized (references) {
            references.remove(name);
        }
        fireContainerEvent("removeSecurityReference", name);
    }


    public String toString() {
        StringBuffer sb = new StringBuffer();
        if (getParent() != null) {
            sb.append(getParent().toString());
            sb.append(".");
        }
        sb.append("StandardWrapper[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());
    }


    /**
     * 处理一个UnavailableException, 标记此servlet在指定的时间内不可用.
     *
     * @param unavailable 发生的异常, 或<code>null</code>将此servlet标记为永久不可用
     */
    public void unavailable(UnavailableException unavailable) {
        getServletContext().log(sm.getString("standardWrapper.unavailable", getName()));
        if (unavailable == null)
            setAvailable(Long.MAX_VALUE);
        else if (unavailable.isPermanent())
            setAvailable(Long.MAX_VALUE);
        else {
            int unavailableSeconds = unavailable.getUnavailableSeconds();
            if (unavailableSeconds <= 0)
                unavailableSeconds = 60;        // Arbitrary default
            setAvailable(System.currentTimeMillis() +
                         (unavailableSeconds * 1000L));
        }
    }


    /**
     * 卸载此servlet的所有初始化实例, 调用<code>destroy()</code>方法之后.
     * 例如，可以在关闭整个servlet引擎之前使用它, 或者在加载与Loader的存储库相关联的加载器的所有类之前.
     *
     * @exception ServletException 如果destroy()方法抛出异常
     */
    public synchronized void unload() throws ServletException {

        // Nothing to do if we have never loaded the instance
        if (!singleThreadModel && (instance == null))
            return;
        unloading = true;

        // 如果当前实例被分配，就花一段时间
        // (possibly more than once if non-STM)
        if (countAllocated > 0) {
            int nRetries = 0;
            while ((nRetries < 21) && (countAllocated > 0)) {
                if ((nRetries % 10) == 0) {
                    log.info(sm.getString("standardWrapper.waiting",
                                          new Integer(countAllocated)));
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    ;
                }
                nRetries++;
            }
        }

        ClassLoader oldCtxClassLoader =
            Thread.currentThread().getContextClassLoader();
        ClassLoader classLoader = instance.getClass().getClassLoader();

        PrintStream out = System.out;
        if (swallowOutput) {
            SystemLogHandler.startCapture();
        }

        // Call the servlet destroy() method
        try {
            instanceSupport.fireInstanceEvent
              (InstanceEvent.BEFORE_DESTROY_EVENT, instance);

            Thread.currentThread().setContextClassLoader(classLoader);
            if( System.getSecurityManager() != null) {
                SecurityUtil.doAsPrivilege("destroy",
                                           instance);
                SecurityUtil.remove(instance);                           
            } else {
                instance.destroy();
            }

            instanceSupport.fireInstanceEvent
              (InstanceEvent.AFTER_DESTROY_EVENT, instance);
        } catch (Throwable t) {
            instanceSupport.fireInstanceEvent
              (InstanceEvent.AFTER_DESTROY_EVENT, instance, t);
            instance = null;
            instancePool = null;
            nInstances = 0;
            fireContainerEvent("unload", this);
            unloading = false;
            throw new ServletException
                (sm.getString("standardWrapper.destroyException", getName()),
                 t);
        } finally {
            // restore the context ClassLoader
            Thread.currentThread().setContextClassLoader(oldCtxClassLoader);
            // Write captured output
            if (swallowOutput) {
                String log = SystemLogHandler.stopCapture();
                if (log != null && log.length() > 0) {
                    if (getServletContext() != null) {
                        getServletContext().log(log);
                    } else {
                        out.println(log);
                    }
                }
            }
        }

        // Deregister the destroyed instance
        instance = null;

        if (singleThreadModel && (instancePool != null)) {
            try {
                Thread.currentThread().setContextClassLoader(classLoader);
                while (!instancePool.isEmpty()) {
                    if( System.getSecurityManager() != null) {
                        SecurityUtil.doAsPrivilege("destroy",
                                                   ((Servlet) instancePool.pop()));
                        SecurityUtil.remove(instance);                           
                    } else {
                        ((Servlet) instancePool.pop()).destroy();
                    }
                }
            } catch (Throwable t) {
                instancePool = null;
                nInstances = 0;
                unloading = false;
                fireContainerEvent("unload", this);
                throw new ServletException
                    (sm.getString("standardWrapper.destroyException",
                                  getName()), t);
            } finally {
                // restore the context ClassLoader
                Thread.currentThread().setContextClassLoader
                    (oldCtxClassLoader);
            }
            instancePool = null;
            nInstances = 0;
        }

        singleThreadModel = false;

        unloading = false;
        fireContainerEvent("unload", this);
    }


    // -------------------------------------------------- ServletConfig Methods


    /**
     * 返回指定名称的初始化参数值; 或者<code>null</code>.
     *
     * @param name 要检索的初始化参数的名称
     */
    public String getInitParameter(String name) {
        return (findInitParameter(name));
    }


    /**
     * 返回定义的初始化参数名称集. 如果没有, 返回零长度.
     */
    public Enumeration getInitParameterNames() {
        synchronized (parameters) {
            return (new Enumerator(parameters.keySet()));
        }
    }


    /**
     * 返回关联的servlet上下文.
     */
    public ServletContext getServletContext() {
        if (parent == null)
            return (null);
        else if (!(parent instanceof Context))
            return (null);
        else
            return (((Context) parent).getServletContext());
    }


    /**
     * 返回这个servlet的名称.
     */
    public String getServletName() {
        return (getName());
    }

    public long getProcessingTime() {
        return swValve.getProcessingTime();
    }

    public void setProcessingTime(long processingTime) {
        swValve.setProcessingTime(processingTime);
    }

    public long getMaxTime() {
        return swValve.getMaxTime();
    }

    public void setMaxTime(long maxTime) {
        swValve.setMaxTime(maxTime);
    }

    public long getMinTime() {
        return swValve.getMinTime();
    }

    public void setMinTime(long minTime) {
        swValve.setMinTime(minTime);
    }

    public int getRequestCount() {
        return swValve.getRequestCount();
    }

    public void setRequestCount(int requestCount) {
        swValve.setRequestCount(requestCount);
    }

    public int getErrorCount() {
        return swValve.getErrorCount();
    }

    public void setErrorCount(int errorCount) {
           swValve.setErrorCount(errorCount);
    }

    /**
     * 增加用于监视的错误计数.
     */
    public void incrementErrorCount(){
        swValve.setErrorCount(swValve.getErrorCount() + 1);
    }

    public long getLoadTime() {
        return loadTime;
    }

    public void setLoadTime(long loadTime) {
        this.loadTime = loadTime;
    }

    public int getClassLoadTime() {
        return classLoadTime;
    }

    // -------------------------------------------------------- Private Methods

    /**
     * 添加一个默认的Mapper实现类，如果没有显式配置.
     *
     * @param mapperClass Java class name of the default Mapper
     */
    protected void addDefaultMapper(String mapperClass) {
        ;       // No need for a default Mapper on a Wrapper
    }


    /**
     * 返回<code>true</code>，如果指定的类名表示容器提供应该由服务器类装入器装入的servlet类.
     *
     * @param classname 要检查的类的名称
     */
    private boolean isContainerProvidedServlet(String classname) {
        if (classname.startsWith("org.apache.catalina.")) {
            return (true);
        }
        try {
            Class clazz =
                this.getClass().getClassLoader().loadClass(classname);
            return (ContainerServlet.class.isAssignableFrom(clazz));
        } catch (Throwable t) {
            return (false);
        }
    }


    /**
     * 返回<code>true</code>，如果允许加载这个servlet.
     */
    private boolean isServletAllowed(Object servlet) {

        if (servlet instanceof ContainerServlet) {
            if (((Context) getParent()).getPrivileged()
                || (servlet.getClass().getName().equals
                    ("org.apache.catalina.servlets.InvokerServlet"))) {
                return (true);
            } else {
                return (false);
            }
        }
        return (true);
    }


    private Method[] getAllDeclaredMethods(Class c) {

        if (c.equals(javax.servlet.http.HttpServlet.class)) {
            return null;
        }

        Method[] parentMethods = getAllDeclaredMethods(c.getSuperclass());

        Method[] thisMethods = c.getDeclaredMethods();
        if (thisMethods == null) {
            return parentMethods;
        }

        if ((parentMethods != null) && (parentMethods.length > 0)) {
            Method[] allMethods =
                new Method[parentMethods.length + thisMethods.length];
		    System.arraycopy(parentMethods, 0, allMethods, 0,
	                             parentMethods.length);
		    System.arraycopy(thisMethods, 0, allMethods, parentMethods.length,
	                             thisMethods.length);
	
		    thisMethods = allMethods;
		}
		return thisMethods;
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Start this component, 如果 load-on-startup被适当设置，则预加载servlet.
     *
     * @exception LifecycleException if a fatal error occurs during startup
     */
    public void start() throws LifecycleException {
    
        // Send j2ee.state.starting notification 
        if (this.getObjectName() != null) {
            Notification notification = new Notification("j2ee.state.starting", 
                                                        this.getObjectName(), 
                                                        sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
        // Start up this component
        super.start();

        if( oname != null )
            registerJMX((StandardContext)getParent());
        
        // 如果请求，加载并初始化这个servlet的实例
        // MOVED TO StandardContext START() METHOD

        setAvailable(0L);
        
        // Send j2ee.state.running notification 
        if (this.getObjectName() != null) {
            Notification notification = 
                new Notification("j2ee.state.running", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
    }


    /**
     * @exception LifecycleException if a fatal error occurs during shutdown
     */
    public void stop() throws LifecycleException {

        setAvailable(Long.MAX_VALUE);
        
        // Send j2ee.state.stopping notification 
        if (this.getObjectName() != null) {
            Notification notification = 
                new Notification("j2ee.state.stopping", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
        // Shut down our servlet instance (if it has been initialized)
        try {
            unload();
        } catch (ServletException e) {
            getServletContext().log(sm.getString
                      ("standardWrapper.unloadException", getName()), e);
        }

        // Shut down this component
        super.stop();

        // Send j2ee.state.stoppped notification 
        if (this.getObjectName() != null) {
            Notification notification = 
                new Notification("j2ee.state.stopped", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
        
        if( oname != null ) {
            Registry.getRegistry(null, null).unregisterComponent(oname);
            
            // Send j2ee.object.deleted notification 
            Notification notification = 
                new Notification("j2ee.object.deleted", this.getObjectName(), 
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

        if (isJspServlet && jspMonitorON != null ) {
            Registry.getRegistry(null, null).unregisterComponent(jspMonitorON);
        }
    }

    protected void registerJMX(StandardContext ctx) {

        String parentName = ctx.getName();
        parentName = ("".equals(parentName)) ? "/" : parentName;

        String hostName = ctx.getParent().getName();
        hostName = (hostName==null) ? "DEFAULT" : hostName;

        String domain = ctx.getDomain();

        String webMod= "//" + hostName + parentName;
        String onameStr = domain + ":j2eeType=Servlet,name=" + getName() +
                          ",WebModule=" + webMod + ",J2EEApplication=" +
                          ctx.getJ2EEApplication() + ",J2EEServer=" +
                          ctx.getJ2EEServer();
        try {
            oname=new ObjectName(onameStr);
            controller=oname;
            Registry.getRegistry(null, null)
                .registerComponent(this, oname, null );
            
            // Send j2ee.object.created notification 
            if (this.getObjectName() != null) {
                Notification notification = new Notification(
                                                "j2ee.object.created", 
                                                this.getObjectName(), 
                                                sequenceNumber++);
                broadcaster.sendNotification(notification);
            }
        } catch( Exception ex ) {
            log.info("Error registering servlet with jmx " + this);
        }

        if (isJspServlet) {
            // Register JSP monitoring mbean
            onameStr = domain + ":type=JspMonitor,name=" + getName()
                       + ",WebModule=" + webMod
                       + ",J2EEApplication=" + ctx.getJ2EEApplication()
                       + ",J2EEServer=" + ctx.getJ2EEServer();
            try {
                jspMonitorON = new ObjectName(onameStr);
                Registry.getRegistry(null, null)
                    .registerComponent(instance, jspMonitorON, null);
            } catch( Exception ex ) {
                log.info("Error registering JSP monitoring with jmx " +
                         instance);
            }
        }
    }
    

    /* Remove a JMX notficationListener 
     * @see javax.management.NotificationEmitter#removeNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    public void removeNotificationListener(NotificationListener listener, 
    		NotificationFilter filter, Object object) throws ListenerNotFoundException {
    	broadcaster.removeNotificationListener(listener,filter,object);
    	
    }
    
    private MBeanNotificationInfo[] notificationInfo;
    
    /* Get JMX Broadcaster Info
     * @TODO use StringManager for international support!
     * @TODO This two events we not send j2ee.state.failed and j2ee.attribute.changed!
     * @see javax.management.NotificationBroadcaster#getNotificationInfo()
     */
    public MBeanNotificationInfo[] getNotificationInfo() {
    	
    	if(notificationInfo == null) {
    		notificationInfo = new MBeanNotificationInfo[]{
    				new MBeanNotificationInfo(new String[] {
    				"j2ee.object.created"},
					Notification.class.getName(),
					"servlet is created"
    				), 
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.starting"},
					Notification.class.getName(),
					"servlet is starting"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.running"},
					Notification.class.getName(),
					"servlet is running"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.state.stopped"},
					Notification.class.getName(),
					"servlet start to stopped"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.object.stopped"},
					Notification.class.getName(),
					"servlet is stopped"
					),
					new MBeanNotificationInfo(new String[] {
					"j2ee.object.deleted"},
					Notification.class.getName(),
					"servlet is deleted"
					)
    		};
    	}
    	return notificationInfo;
    }
    
    
    /* Add a JMX-NotificationListener
     * @see javax.management.NotificationBroadcaster#addNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    public void addNotificationListener(NotificationListener listener, 
            NotificationFilter filter, Object object) throws IllegalArgumentException {
    	broadcaster.addNotificationListener(listener,filter,object);
    }
    
    
    /**
     * Remove a JMX-NotificationListener 
     * @see javax.management.NotificationBroadcaster#removeNotificationListener(javax.management.NotificationListener)
     */
    public void removeNotificationListener(NotificationListener listener) 
        throws ListenerNotFoundException {
    	broadcaster.removeNotificationListener(listener);
    }
    
     // ------------------------------------------------------------- Attributes
        
    public boolean isEventProvider() {
        return false;
    }
    
    public boolean isStateManageable() {
        return false;
    }
    
    public boolean isStatisticsProvider() {
        return false;
    }
}
