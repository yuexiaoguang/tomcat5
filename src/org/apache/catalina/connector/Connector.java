package org.apache.catalina.connector;

import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.HashMap;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Service;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.modeler.Registry;
import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.http.mapper.Mapper;


/**
 * Tomcat 5的连接器实现类.
 */
public class Connector implements Lifecycle, MBeanRegistration {
    private static Log log = LogFactory.getLog(Connector.class);

    // ------------------------------------------------------------ Constructor

    public Connector()
        throws Exception {
        this(null);
    }
    
    public Connector(String protocol) 
        throws Exception {
        setProtocol(protocol);
        // 实例化协议处理程序
        try {
            Class clazz = Class.forName(protocolHandlerClassName);
            this.protocolHandler = (ProtocolHandler) clazz.newInstance();
        } catch (Exception e) {
            log.error
                (sm.getString
                 ("coyoteConnector.protocolHandlerInstantiationFailed", e));
        }
    }
    
    
    // ----------------------------------------------------- Instance Variables


    /**
     * 关联的<code>Service</code>.
     */
    protected Service service = null;


    /**
     * 是否允许TRACE ?
     */
    protected boolean allowTrace = false;


    /**
     * 处理这个Connector接收到的请求的Container.
     */
    protected Container container = null;


    /**
     * 是否使用 "/" 作为会话cookies的路径 ?
     */
    protected boolean emptySessionPath = false;


    /**
     * 这个Connector的"启用DNS查找"标志.
     */
    protected boolean enableLookups = false;


    /*
     * 启用或禁用X-Powered-By响应 header?
     */
    protected boolean xpoweredBy = false;


    /**
     * 这个Connector实现类的描述信息.
     */
    protected static final String info =
        "org.apache.catalina.connector.Connector/2.1";


    /**
     * 生命周期事件支持
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * 请求监听的端口号
     */
    protected int port = 0;


    /**
     * 代理服务器名称.
     * 当操作代理服务器后面的Tomcat时，这非常有用, 因此将得到正确的创建.
     * 如果未指定, 使用<code>Host</code> header包含的服务器名称.
     */
    protected String proxyName = null;


    /**
     * 代理服务器端口. 当操作代理服务器后面的Tomcat时，这非常有用, 因此将得到正确的创建.
     * 如果未指定, 使用<code>port</code>属性指定的端口号.
     */
    protected int proxyPort = 0;


    /**
     * non-SSL 转发到 SSL 的端口号.
     */
    protected int redirectPort = 443;


    /**
     * 将通过此连接器接收的所有请求设置的请求方案.
     */
    protected String scheme = "http";


    /**
     * 将通过该连接器接收的所有请求设置的安全连接标志.
     */
    protected boolean secure = false;


    /**
     * The string manager for this package.
     */
    protected StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 将由容器自动解析的POST的最大大小. 默认2MB.
     */
    protected int maxPostSize = 2 * 1024 * 1024;


    /**
     * 在验证期间容器将保存的POST最大大小. 默认4kB
     */
    protected int maxSavePostSize = 4 * 1024;


    /**
     * 是否已初始化?
     */
    protected boolean initialized = false;


    /**
     * 是否已启动?
     */
    protected boolean started = false;


    /**
     * 后台线程的关闭信号
     */
    protected boolean stopped = false;

    /**
     * 使用基于IP的虚拟主机的标志
     */
    protected boolean useIPVHosts = false;

    /**
     * 后台线程
     */
    protected Thread thread = null;


    /**
     * 协议处理程序类名.
     * 默认是 HTTP/1.1 协议处理器.
     */
    protected String protocolHandlerClassName =
        "org.apache.coyote.http11.Http11Protocol";


    /**
     * 协议处理器.
     */
    protected ProtocolHandler protocolHandler = null;


    /**
     * 适配器.
     */
    protected Adapter adapter = null;


     /**
      * Mapper.
      */
     protected Mapper mapper = new Mapper();


     /**
      * Mapper监听器.
      */
     protected MapperListener mapperListener = new MapperListener(mapper);


     /**
      * URI 编码.
      */
     protected String URIEncoding = null;


     /**
      * URI 编码主体.
      */
     protected boolean useBodyEncodingForURI = false;


     protected static HashMap replacements = new HashMap();
     static {
         replacements.put("acceptCount", "backlog");
         replacements.put("connectionLinger", "soLinger");
         replacements.put("connectionTimeout", "soTimeout");
         replacements.put("connectionUploadTimeout", "timeout");
         replacements.put("clientAuth", "clientauth");
         replacements.put("keystoreFile", "keystore");
         replacements.put("randomFile", "randomfile");
         replacements.put("rootFile", "rootfile");
         replacements.put("keystorePass", "keypass");
         replacements.put("keystoreType", "keytype");
         replacements.put("sslProtocol", "protocol");
         replacements.put("sslProtocols", "protocols");
     }
     
     
    // ------------------------------------------------------------- Properties


    /**
     * 返回配置的属性
     */
    public Object getProperty(String name) {
        String repl = name;
        if (replacements.get(name) != null) {
            repl = (String) replacements.get(name);
        }
        return IntrospectionUtils.getProperty(protocolHandler, repl);
    }

    
    /**
     * 设置配置的属性
     */
    public void setProperty(String name, String value) {
        String repl = name;
        if (replacements.get(name) != null) {
            repl = (String) replacements.get(name);
        }
        IntrospectionUtils.setProperty(protocolHandler, repl, value);
    }

    
    /**
     * 返回配置的属性
     */
    public Object getAttribute(String name) {
        return getProperty(name);
    }

    
    /**
     * 设置配置的属性
     */
    public void setAttribute(String name, Object value) {
        setProperty(name, String.valueOf(value));
    }

    
    /** 
     * 删除配置的属性
     */
    public void removeProperty(String name) {
        // FIXME !
        //protocolHandler.removeAttribute(name);
    }

    
    /**
     * 返回关联的<code>Service</code>.
     */
    public Service getService() {
        return (this.service);
    }


    /**
     * 设置关联的<code>Service</code>.
     *
     * @param service The service that owns this Engine
     */
    public void setService(Service service) {
        this.service = service;
        // FIXME: setProperty("service", service);
    }


    /**
     * True, 如果允许TRACE 方法. 默认 "false".
     */
    public boolean getAllowTrace() {
        return (this.allowTrace);
    }


    /**
     * 设置allowTrace 标志, 禁用或启用TRACE HTTP 方法.
     *
     * @param allowTrace The new allowTrace flag
     */
    public void setAllowTrace(boolean allowTrace) {
        this.allowTrace = allowTrace;
        setProperty("allowTrace", String.valueOf(allowTrace));
    }

    /**
     * 此连接器可用于处理请求吗?
     */
    public boolean isAvailable() {
        return (started);
    }


    /**
     * 返回此连接器的输入缓冲区大小.
     * 
     * @deprecated
     */
    public int getBufferSize() {
        return 2048;
    }

    /**
     * 设置此连接器的输入缓冲区大小.
     *
     * @param bufferSize The new input buffer size.
     * @deprecated
     */
    public void setBufferSize(int bufferSize) {
    }

    
    /**
     * 返回用于处理这个Connector接收到的请求的Container.
     */
    public Container getContainer() {
        if( container==null ) {
            // Lazy - 可能稍后加载
            findContainer();     
        }
        return (container);

    }


    /**
     * 设置用于处理这个Connector接收到的请求的Container.
     *
     * @param container The new Container to use
     */
    public void setContainer(Container container) {
        this.container = container;
    }


    /**
     * 返回 "空会话路径" 标志.
     */
    public boolean getEmptySessionPath() {
        return (this.emptySessionPath);
    }


    /**
     * 设置 "空会话路径" 标志.
     *
     * @param emptySessionPath The new "empty session path" flag value
     */
    public void setEmptySessionPath(boolean emptySessionPath) {
        this.emptySessionPath = emptySessionPath;
        setProperty("emptySessionPath", String.valueOf(emptySessionPath));
    }


    /**
     * 返回 "启用DNS查找" 标志.
     */
    public boolean getEnableLookups() {
        return (this.enableLookups);
    }


    /**
     * 设置 "启用DNS查找" 标志.
     *
     * @param enableLookups The new "enable DNS lookups" flag value
     */
    public void setEnableLookups(boolean enableLookups) {
        this.enableLookups = enableLookups;
        setProperty("enableLookups", String.valueOf(enableLookups));
    }


    /**
     * 返回实现类描述信息.
     */
    public String getInfo() {
        return (info);
    }


     public Mapper getMapper() {
         return (mapper);
     }


    /**
     * 将由容器自动解析的POST的最大大小. 默认2MB
     */
    public int getMaxPostSize() {
        return (maxPostSize);
    }


    /**
     * 设置将由容器自动解析的POST的最大大小
     *
     * @param maxPostSize 将由容器自动解析的POST的最大大小
     */
    public void setMaxPostSize(int maxPostSize) {
        this.maxPostSize = maxPostSize;
    }


    /**
     * 在验证期间容器将保存的POST最大大小. 默认4kB
     */
    public int getMaxSavePostSize() {
        return (maxSavePostSize);
    }


    /**
     * 设置在验证期间容器将保存的POST最大大小
     *
     * @param maxSavePostSize 在验证期间容器将保存的POST最大大小
     */
    public void setMaxSavePostSize(int maxSavePostSize) {
        this.maxSavePostSize = maxSavePostSize;
        setProperty("maxSavePostSize", String.valueOf(maxSavePostSize));
    }


    /**
     * 请求监听的端口号
     */
    public int getPort() {
        return (this.port);
    }


    /**
     * 设置请求监听的端口号.
     *
     * @param port The new port number
     */
    public void setPort(int port) {
        this.port = port;
        setProperty("port", String.valueOf(port));
    }


    /**
     * 返回使用的协议.
     */
    public String getProtocol() {
        if ("org.apache.coyote.http11.Http11Protocol".equals
            (getProtocolHandlerClassName())
            || "org.apache.coyote.http11.Http11AprProtocol".equals
            (getProtocolHandlerClassName())) {
            return "HTTP/1.1";
        } else if ("org.apache.jk.server.JkCoyoteHandler".equals
                   (getProtocolHandlerClassName())
                   || "org.apache.coyote.ajp.AjpAprProtocol".equals
                   (getProtocolHandlerClassName())) {
            return "AJP/1.3";
        }
        return getProtocolHandlerClassName();
    }


    /**
     * 设置连接器使用的协议.
     *
     * @param protocol 协议名称
     */
    public void setProtocol(String protocol) {

        // Test APR support
        boolean apr = false;
        try {
            String methodName = "initialize";
            Class paramTypes[] = new Class[1];
            paramTypes[0] = String.class;
            Object paramValues[] = new Object[1];
            paramValues[0] = null;
            Method method = Class.forName("org.apache.tomcat.jni.Library")
                .getMethod(methodName, paramTypes);
            method.invoke(null, paramValues);
            apr = true;
        } catch (Throwable t) {
            // Ignore
        }

        if (apr) {
            if ("HTTP/1.1".equals(protocol)) {
                setProtocolHandlerClassName
                    ("org.apache.coyote.http11.Http11AprProtocol");
            } else if ("AJP/1.3".equals(protocol)) {
                setProtocolHandlerClassName
                    ("org.apache.coyote.ajp.AjpAprProtocol");
            } else if (protocol != null) {
                setProtocolHandlerClassName(protocol);
            } else {
                setProtocolHandlerClassName
                    ("org.apache.coyote.http11.Http11AprProtocol");
            }
        } else {
            if ("HTTP/1.1".equals(protocol)) {
                setProtocolHandlerClassName
                    ("org.apache.coyote.http11.Http11Protocol");
            } else if ("AJP/1.3".equals(protocol)) {
                setProtocolHandlerClassName
                    ("org.apache.jk.server.JkCoyoteHandler");
            } else if (protocol != null) {
                setProtocolHandlerClassName(protocol);
            }
        }
    }


    /**
     * 返回使用的协议处理器的类名.
     */
    public String getProtocolHandlerClassName() {
        return (this.protocolHandlerClassName);
    }


    /**
     * 设置使用的协议处理器的类名.
     *
     * @param protocolHandlerClassName The new class name
     */
    public void setProtocolHandlerClassName(String protocolHandlerClassName) {
        this.protocolHandlerClassName = protocolHandlerClassName;
    }


    /**
     * 返回关联的协议处理器.
     */
    public ProtocolHandler getProtocolHandler() {
        return (this.protocolHandler);
    }


    /**
     * 设置关联的协议处理器.
     */
    public String getProxyName() {
        return (this.proxyName);
    }


    /**
     * 设置代理服务器名.
     *
     * @param proxyName The new proxy server name
     */
    public void setProxyName(String proxyName) {
        if(proxyName != null && proxyName.length() > 0) {
            this.proxyName = proxyName;
            setProperty("proxyName", proxyName);
        } else {
            this.proxyName = null;
            removeProperty("proxyName");
        }
    }


    /**
     * 返回代理服务器端口
     */
    public int getProxyPort() {
        return (this.proxyPort);
    }


    /**
     * 设置代理服务器端口
     *
     * @param proxyPort 新代理服务器端口
     */
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
        setProperty("proxyPort", String.valueOf(proxyPort));
    }


    /**
     * 返回请求重定向的端口号, 如果它出现在一个非SSL端口上，该传输保证使用SSL进行安全约束.
     */
    public int getRedirectPort() {
        return (this.redirectPort);
    }


    /**
     * 设置请求重定向的端口号
     *
     * @param redirectPort 重定向的端口号(non-SSL to SSL)
     */
    public void setRedirectPort(int redirectPort) {
        this.redirectPort = redirectPort;
        setProperty("redirectPort", String.valueOf(redirectPort));
    }

    
    /**
     * 将通过此连接器接收的所有请求设置的请求方案. 默认是"http".
     */
    public String getScheme() {
        return (this.scheme);
    }


    /**
     * 设置将通过此连接器接收的所有请求设置的请求方案.
     *
     * @param scheme The new scheme
     */
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }


    /**
     * 将通过该连接器接收的所有请求设置的安全连接标志. 默认是"false".
     */
    public boolean getSecure() {
        return (this.secure);
    }


    /**
     * 设置将通过该连接器接收的所有请求设置的安全连接标志.
     *
     * @param secure 新的安全连接标志
     */
    public void setSecure(boolean secure) {
        this.secure = secure;
        setProperty("secure", Boolean.toString(secure));
    }

     /**
      * 返回用于URI的字符编码.
      */
     public String getURIEncoding() {
         return (this.URIEncoding);
     }


     /**
      * 设置用于URI的字符编码.
      *
      * @param URIEncoding The new URI character encoding.
      */
     public void setURIEncoding(String URIEncoding) {
         this.URIEncoding = URIEncoding;
         setProperty("uRIEncoding", URIEncoding);
     }


     /**
      * 如果URI使用实体正文编码，则返回true.
      */
     public boolean getUseBodyEncodingForURI() {
         return (this.useBodyEncodingForURI);
     }


     /**
      * 设置URI是否使用实体正文编码.
      *
      * @param useBodyEncodingForURI The new value for the flag.
      */
     public void setUseBodyEncodingForURI(boolean useBodyEncodingForURI) {
         this.useBodyEncodingForURI = useBodyEncodingForURI;
         setProperty
             ("useBodyEncodingForURI", String.valueOf(useBodyEncodingForURI));
     }


    /**
     * 启用或禁用 X-Powered-By 响应标头.
     *
     * @return true 启用, false 禁用
     */
    public boolean getXpoweredBy() {
        return xpoweredBy;
    }


    /**
     * 启用或禁用 X-Powered-By 响应标头.
     *
     * @param xpoweredBy true 启用, false 禁用
     */
    public void setXpoweredBy(boolean xpoweredBy) {
        this.xpoweredBy = xpoweredBy;
        setProperty("xpoweredBy", String.valueOf(xpoweredBy));
    }

    /**
     * 启用基于IP的虚拟主机的使用.
     *
     * @param useIPVHosts <code>true</code>如果主机是通过IP标识的,
     *                    <code>false</code>如果主机是按名称标识的.
     */
    public void setUseIPVHosts(boolean useIPVHosts) {
        this.useIPVHosts = useIPVHosts;
        setProperty("useIPVHosts", String.valueOf(useIPVHosts));
    }

    /**
     * 测试是否启用了基于IP的虚拟主机.
     */
    public boolean getUseIPVHosts() {
        return useIPVHosts;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * 创建（或分配）并返回一个请求对象，用于将请求的内容指定给负责的容器.
     */
    public Request createRequest() {
        Request request = new Request();
        request.setConnector(this);
        return (request);
    }


    /**
     * 创建（或分配）并返回一个响应对象，用于接收来自负责的容器的响应内容.
     */
    public Response createResponse() {
        Response response = new Response();
        response.setConnector(this);
        return (response);
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * 添加生命周期事件监听器
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
     * 删除生命周期事件监听器
     *
     * @param listener The listener to add
     */
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }

    
    protected ObjectName createObjectName(String domain, String type)
            throws MalformedObjectNameException {
        String encodedAddr = null;
        if (getProperty("address") != null) {
            encodedAddr = URLEncoder.encode(getProperty("address").toString());
        }
        String addSuffix = (getProperty("address") == null) ? "" : ",address="
                + encodedAddr;
        ObjectName _oname = new ObjectName(domain + ":type=" + type + ",port="
                + getPort() + addSuffix);
        return _oname;
    }
    
    /**
     * 初始化此连接器(在这里创建 ServerSocket!)
     */
    public void initialize()
        throws LifecycleException
    {
        if (initialized) {
            if(log.isInfoEnabled())
                log.info(sm.getString("coyoteConnector.alreadyInitialized"));
           return;
        }

        this.initialized = true;

        if( oname == null && (container instanceof StandardEngine)) {
            try {
                // 直接加载, 通过API - 没有名字
                StandardEngine cb=(StandardEngine)container;
                oname = createObjectName(cb.getName(), "Connector");
                Registry.getRegistry(null, null)
                    .registerComponent(this, oname, null);
                controller=oname;
            } catch (Exception e) {
                log.error( "Error registering connector ", e);
            }
            if(log.isDebugEnabled())
                log.debug("Creating name for connector " + oname);
        }

        // Initializa adapter
        adapter = new CoyoteAdapter(this);
        protocolHandler.setAdapter(adapter);

        IntrospectionUtils.setProperty(protocolHandler, "jkHome",
                                       System.getProperty("catalina.base"));

        try {
            protocolHandler.init();
        } catch (Exception e) {
            throw new LifecycleException
                (sm.getString
                 ("coyoteConnector.protocolHandlerInitializationFailed", e));
        }
    }


    /**
     * 暂停连接器.
     */
    public void pause() throws LifecycleException {
        try {
            protocolHandler.pause();
        } catch (Exception e) {
            log.error(sm.getString
                      ("coyoteConnector.protocolHandlerPauseFailed"), e);
        }
    }


    /**
     * 暂停连接器.
     */
    public void resume()
        throws LifecycleException {
        try {
            protocolHandler.resume();
        } catch (Exception e) {
            log.error(sm.getString
                      ("coyoteConnector.protocolHandlerResumeFailed"), e);
        }
    }


    /**
     * 通过这个连接器开始处理请求.
     *
     * @exception LifecycleException 如果出现致命的启动错误
     */
    public void start() throws LifecycleException {
        if( !initialized )
            initialize();

        // 验证并更新当前状态
        if (started ) {
            if(log.isInfoEnabled())
                log.info(sm.getString("coyoteConnector.alreadyStarted"));
            return;
        }
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        // 不能提前注册 - Server.start回调中的JMX注册情况
        if ( this.oname != null ) {
            // 注册 - register the adapter as well.
            try {
                Registry.getRegistry(null, null).registerComponent
                    (protocolHandler, createObjectName(this.domain,"ProtocolHandler"), null);
            } catch (Exception ex) {
                log.error(sm.getString
                          ("coyoteConnector.protocolRegistrationFailed"), ex);
            }
        } else {
            if(log.isInfoEnabled())
                log.info(sm.getString
                     ("coyoteConnector.cannotRegisterProtocol"));
        }

        try {
            protocolHandler.start();
        } catch (Exception e) {
            throw new LifecycleException
                (sm.getString
                 ("coyoteConnector.protocolHandlerStartFailed", e));
        }

        if( this.domain != null ) {
            mapperListener.setDomain( domain );
            //mapperListener.setEngine( service.getContainer().getName() );
            mapperListener.init();
            try {
                ObjectName mapperOname = createObjectName(this.domain,"Mapper");
                if (log.isDebugEnabled())
                    log.debug(sm.getString(
                            "coyoteConnector.MapperRegistration", mapperOname));
                Registry.getRegistry(null, null).registerComponent
                    (mapper, mapperOname, "Mapper");
            } catch (Exception ex) {
                log.error(sm.getString
                        ("coyoteConnector.protocolRegistrationFailed"), ex);
            }
        }
    }


    /**
     * 通过此连接器终止处理请求.
     *
     * @exception LifecycleException 如果发生致命关机错误
     */
    public void stop() throws LifecycleException {

        // Validate and update our current state
        if (!started) {
            log.error(sm.getString("coyoteConnector.notStarted"));
            return;

        }
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        try {
            mapperListener.destroy();
            Registry.getRegistry(null, null).unregisterComponent
                (createObjectName(this.domain,"Mapper"));
            Registry.getRegistry(null, null).unregisterComponent
                (createObjectName(this.domain,"ProtocolHandler"));
        } catch (MalformedObjectNameException e) {
            log.error( sm.getString
                    ("coyoteConnector.protocolUnregistrationFailed"), e);
        }
        try {
            protocolHandler.destroy();
        } catch (Exception e) {
            throw new LifecycleException
                (sm.getString
                 ("coyoteConnector.protocolHandlerDestroyFailed", e));
        }

    }


    // -------------------- JMX registration  --------------------
    protected String domain;
    protected ObjectName oname;
    protected MBeanServer mserver;
    ObjectName controller;

    public ObjectName getController() {
        return controller;
    }

    public void setController(ObjectName controller) {
        this.controller = controller;
    }

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
        try {
            if( started ) {
                stop();
            }
        } catch( Throwable t ) {
            log.error( "Unregistering - can't stop", t);
        }
    }
    
    protected void findContainer() {
        try {
            // 向服务注册
            ObjectName parentName=new ObjectName( domain + ":" + "type=Service");
            
            if(log.isDebugEnabled())
                log.debug("Adding to " + parentName );
            if( mserver.isRegistered(parentName )) {
                mserver.invoke(parentName, "addConnector", new Object[] { this },
                        new String[] {"org.apache.catalina.connector.Connector"});
                // 作为副作用，将得到容器字段集
                // 初始化也将被调用
                //return;
            }
            // XXX Go directly to the Engine
            // initialize(); - is called by addConnector
            ObjectName engName=new ObjectName( domain + ":" + "type=Engine");
            if( mserver.isRegistered(engName )) {
                Object obj=mserver.getAttribute(engName, "managedResource");
                if(log.isDebugEnabled())
                      log.debug("Found engine " + obj + " " + obj.getClass());
                container=(Container)obj;
                
                // 内部初始化 - we now have the Engine
                initialize();
                
                if(log.isDebugEnabled())
                    log.debug("Initialized");
                // As a side effect we'll get the container field set
                // Also initialize will be called
                return;
            }
        } catch( Exception ex ) {
            log.error( "Error finding container " + ex);
        }
    }

    public void init() throws Exception {

        if( this.getService() != null ) {
            if(log.isDebugEnabled())
                 log.debug( "Already configured" );
            return;
        }
        if( container==null ) {
            findContainer();
        }
    }

    public void destroy() throws Exception {
        if( oname!=null && controller==oname ) {
            if(log.isDebugEnabled())
                 log.debug("Unregister itself " + oname );
            Registry.getRegistry(null, null).unregisterComponent(oname);
        }
        if( getService() == null)
            return;
        getService().removeConnector(this);
    }
}
