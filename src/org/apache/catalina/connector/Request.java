package org.apache.catalina.connector;

import java.io.InputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import javax.security.auth.Subject;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestAttributeEvent;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.buf.StringCache;
import org.apache.tomcat.util.http.Cookies;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.Parameters;
import org.apache.tomcat.util.http.ServerCookie;
import org.apache.tomcat.util.http.mapper.MappingData;

import org.apache.coyote.ActionCode;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Session;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.ApplicationFilterFactory;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.util.Enumerator;
import org.apache.catalina.util.ParameterMap;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.util.StringParser;

/**
 * Coyote request的包装器对象.
 */
public class Request implements HttpServletRequest {

    // ----------------------------------------------------------- Constructors

    static {
        // 确保为SM加载类
        new StringCache.ByteEntry();
        new StringCache.CharEntry();
    }

    public Request() {
        formats[0].setTimeZone(GMT_ZONE);
        formats[1].setTimeZone(GMT_ZONE);
        formats[2].setTimeZone(GMT_ZONE);
    }

    // ------------------------------------------------------------- Properties

    /**
     * Coyote request.
     */
    protected org.apache.coyote.Request coyoteRequest;

    /**
     * 设置Coyote request.
     * 
     * @param coyoteRequest The Coyote request
     */
    public void setCoyoteRequest(org.apache.coyote.Request coyoteRequest) {
        this.coyoteRequest = coyoteRequest;
        inputBuffer.setRequest(coyoteRequest);
    }

    /**
     * 获取Coyote request.
     */
    public org.apache.coyote.Request getCoyoteRequest() {
        return (this.coyoteRequest);
    }


    // ----------------------------------------------------- Variables


    protected static final TimeZone GMT_ZONE = TimeZone.getTimeZone("GMT");


    /**
     * The string manager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 关联的cookies.
     */
    protected Cookie[] cookies = null;


    /**
     * 在getDateHeader()中使用.
     *
     * 注意，因为SimpleDateFormat不是线程安全的, 不能声明formats[] 为一个静态变量.
     */
    protected SimpleDateFormat formats[] = {
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
        new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy", Locale.US)
    };


    /**
     * 默认区域设置.
     */
    protected static Locale defaultLocale = Locale.getDefault();


    /**
     * 与此请求相关联的属性, 使用属性名作为key.
     */
    protected HashMap attributes = new HashMap();


    /**
     * 此请求的只读属性列表.
     */
    private HashMap readOnlyAttributes = new HashMap();


    /**
     * 请求关联的首选区域.
     */
    protected ArrayList locales = new ArrayList();


    /**
     * 请求关联的内部注释, 通过Catalina组件和事件监听器.
     */
    private transient HashMap notes = new HashMap();


    /**
     * 身份验证类型
     */
    protected String authType = null;


    /**
     * 当前分派器类型.
     */
    protected Object dispatcherType = null;


    /**
     * 关联的输入缓冲区.
     */
    protected InputBuffer inputBuffer = new InputBuffer();


    /**
     * ServletInputStream.
     */
    protected CoyoteInputStream inputStream = new CoyoteInputStream(inputBuffer);


    /**
     * Reader.
     */
    protected CoyoteReader reader = new CoyoteReader(inputBuffer);


    /**
     * 使用流的标志.
     */
    protected boolean usingInputStream = false;


    /**
     * 使用写入器标志.
     */
    protected boolean usingReader = false;


    /**
     * User principal.
     */
    protected Principal userPrincipal = null;


    /**
     * Session 解析标志.
     */
    protected boolean sessionParsed = false;


    /**
     * 请求参数解析标志.
     */
    protected boolean parametersParsed = false;


    /**
     * Cookies 解析标志.
     */
    protected boolean cookiesParsed = false;


    /**
     * 安全标志.
     */
    protected boolean secure = false;

    
    /**
     * 当前AccessControllerContext关联的Subject
     */
    protected transient Subject subject = null;


    /**
     * Post 数据缓冲区.
     */
    protected static int CACHED_POST_LEN = 8192;
    protected byte[] postData = null;


    /**
     * 在getParametersMap 方法中使用的Map.
     */
    protected ParameterMap parameterMap = new ParameterMap();


    /**
     * 此请求的当前活动会话.
     */
    protected Session session = null;


    /**
     * 当前请求调度器路径.
     */
    protected Object requestDispatcherPath = null;


    /**
     * 是在cookie中接收到请求的会话id吗?
     */
    protected boolean requestedSessionCookie = false;


    /**
     * 该请求的会话 ID.
     */
    protected String requestedSessionId = null;


    /**
     * 在URL中接收到请求的会话id吗?
     */
    protected boolean requestedSessionURL = false;


    /**
     * 解析区域.
     */
    protected boolean localesParsed = false;


    /**
     * 将用于解析请求行的字符串解析器.
     */
    private StringParser parser = new StringParser();


    /**
     * 本地端口
     */
    protected int localPort = -1;

    /**
     * 远程地址.
     */
    protected String remoteAddr = null;


    /**
     * 远程主机.
     */
    protected String remoteHost = null;

    
    /**
     * 远程端口
     */
    protected int remotePort = -1;
    
    /**
     * 本地地址
     */
    protected String localAddr = null;

    
    /**
     * 本地地址
     */
    protected String localName = null;

    // --------------------------------------------------------- Public Methods

    /**
     * 释放所有对象引用, 初始化实例变量, 准备重用这个对象.
     */
    public void recycle() {

        context = null;
        wrapper = null;

        dispatcherType = null;
        requestDispatcherPath = null;

        authType = null;
        inputBuffer.recycle();
        usingInputStream = false;
        usingReader = false;
        userPrincipal = null;
        subject = null;
        sessionParsed = false;
        parametersParsed = false;
        cookiesParsed = false;
        locales.clear();
        localesParsed = false;
        secure = false;
        remoteAddr = null;
        remoteHost = null;
        remotePort = -1;
        localPort = -1;
        localAddr = null;
        localName = null;

        attributes.clear();
        notes.clear();
        cookies = null;

        if (session != null) {
            session.endAccess();
        }
        session = null;
        requestedSessionCookie = false;
        requestedSessionId = null;
        requestedSessionURL = false;

        parameterMap.setLocked(false);
        parameterMap.clear();

        mappingData.recycle();

        if (Constants.SECURITY) {
            if (facade != null) {
                facade.clear();
                facade = null;
            }
            if (inputStream != null) {
                inputStream.clear();
                inputStream = null;
            }
            if (reader != null) {
                reader.clear();
                reader = null;
            }
        }
    }


    // -------------------------------------------------------- Request Methods


    /**
     * 关联的Catalina 连接.
     */
    protected Connector connector;

    /**
     * 返回接收此请求的连接器.
     */
    public Connector getConnector() {
        return (this.connector);
    }

    /**
     * 设置接收此请求的连接器.
     *
     * @param connector The new connector
     */
    public void setConnector(Connector connector) {
        this.connector = connector;
    }


    /**
     * 关联的上下文.
     */
    protected Context context = null;

    /**
     * 返回正在处理此请求的上下文.
     */
    public Context getContext() {
        return (this.context);
    }


    /**
     * 设置正在处理此请求的上下文.
     * 一旦确定了适当的上下文，就必须调用它, 因为它标识<code>getContextPath()</code>返回的值,
     * 从而启用对请求URI的解析.
     *
     * @param context 新关联上下文
     */
    public void setContext(Context context) {
        this.context = context;
    }


    /**
     * 与请求相关联的过滤器链.
     */
    protected FilterChain filterChain = null;

    /**
     * 获取与请求相关联的过滤器链
     */
    public FilterChain getFilterChain() {
        return (this.filterChain);
    }

    /**
     * 设置与请求相关联的过滤器链
     * 
     * @param filterChain new filter chain
     */
    public void setFilterChain(FilterChain filterChain) {
        this.filterChain = filterChain;
    }


    /**
     * 返回正在处理此请求的主机.
     */
    public Host getHost() {
        if (getContext() == null)
            return null;
        return (Host) getContext().getParent();
        //return ((Host) mappingData.host);
    }


    /**
     * 设置正在处理此请求的主机.
     * 一旦找到合适的主机，就必须调用它, 在将请求传递到上下文之前.
     *
     * @param host 新关联的主机
     */
    public void setHost(Host host) {
        mappingData.host = host;
    }


    /**
     * 实现类的描述信息.
     */
    protected static final String info =
        "org.apache.coyote.catalina.CoyoteRequest/1.0";

    /**
     * 返回描述信息和相应版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * Mapping data.
     */
    protected MappingData mappingData = new MappingData();

    public MappingData getMappingData() {
        return (mappingData);
    }


    /**
     * 请求的外观
     */
    protected RequestFacade facade = null;

    /**
     * 返回原始<code>ServletRequest</code>. 此方法必须由子类实现.
     */
    public HttpServletRequest getRequest() {
        if (facade == null) {
            facade = new RequestFacade(this);
        } 
        return (facade);
    }


    /**
     * 这个请求关联的响应.
     */
    protected org.apache.catalina.connector.Response response = null;

    /**
     * 返回这个请求关联的响应.
     */
    public org.apache.catalina.connector.Response getResponse() {
        return (this.response);
    }

    /**
     * 设置这个请求关联的响应.
     *
     * @param response The new associated response
     */
    public void setResponse(org.apache.catalina.connector.Response response) {
        this.response = response;
    }

    /**
     * 返回与此请求关联的输入流.
     */
    public InputStream getStream() {
        if (inputStream == null) {
            inputStream = new CoyoteInputStream(inputBuffer);
        }
        return inputStream;
    }

    /**
     * 设置与此请求关联的输入流.
     *
     * @param stream The new input stream
     */
    public void setStream(InputStream stream) {
        // Ignore
    }


    /**
     * URI 字节到char转换器(不可回收).
     */
    protected B2CConverter URIConverter = null;

    /**
     * 返回URI转换器.
     */
    protected B2CConverter getURIConverter() {
        return URIConverter;
    }

    /**
     * 设置URI转换器.
     * 
     * @param URIConverter the new URI connverter
     */
    protected void setURIConverter(B2CConverter URIConverter) {
        this.URIConverter = URIConverter;
    }


    /**
     * 关联的wrapper.
     */
    protected Wrapper wrapper = null;

    /**
     * 返回处理这个请求的 Wrapper.
     */
    public Wrapper getWrapper() {
        return (this.wrapper);
    }


    /**
     * 设置处理这个请求的 Wrapper. 在请求最终传递给应用程序servlet之前, 一旦找到适当的Wrapper, 就必须调用它.
     * @param wrapper The newly associated Wrapper
     */
    public void setWrapper(Wrapper wrapper) {
        this.wrapper = wrapper;
    }


    // ------------------------------------------------- Request Public Methods


    /**
     * 创建并返回一个 ServletInputStream 读取与此请求相关联的内容.
     *
     * @exception IOException 如果发生输入/输出错误
     */
    public ServletInputStream createInputStream() 
        throws IOException {
        if (inputStream == null) {
            inputStream = new CoyoteInputStream(inputBuffer);
        }
        return inputStream;
    }


    /**
     * 执行刷新和关闭输入流或读取器所需的任何操作, 在一个操作中.
     *
     * @exception IOException 如果发生输入/输出错误
     */
    public void finishRequest() throws IOException {
        // 读取器和输入流不需要关闭
    }


    /**
     * 返回指定名称的绑定到该请求的内部注释的对象, 或者<code>null</code>如果没有这样的绑定.
     *
     * @param name 注释的名称
     */
    public Object getNote(String name) {
        return (notes.get(name));
    }


    /**
     * 返回这个请求绑定的所有注释的名称.
     */
    public Iterator getNoteNames() {
        return (notes.keySet().iterator());
    }


    /**
     * 移除指定名称的注释绑定的所有对象.
     *
     * @param name 注释的名称
     */
    public void removeNote(String name) {
        notes.remove(name);
    }


    /**
     * 将对象绑定到与此请求相关联的内部注释中的指定名称, 替换此名称的任何现有绑定.
     *
     * @param name 名称
     * @param value 对象
     */
    public void setNote(String name, Object value) {
        notes.put(name, value);
    }


    /**
     * 设置与此请求相关联的内容长度.
     *
     * @param length The new content length
     */
    public void setContentLength(int length) {
        // Not used
    }


    /**
     * 设置内容类型(也可以选择字符编码).
     * 例如, <code>text/html; charset=ISO-8859-4</code>.
     *
     * @param type 新的内容类型
     */
    public void setContentType(String type) {
        // Not used
    }


    /**
     * 设置与此请求相关联的协议名称和版本.
     *
     * @param protocol 协议名称和版本
     */
    public void setProtocol(String protocol) {
        // Not used
    }


    /**
     * 设置与此请求相关联的远程客户机的IP地址.
     *
     * @param remoteAddr 远程IP地址
     */
    public void setRemoteAddr(String remoteAddr) {
        // Not used
    }


    /**
     * 设置与此请求相关联的远程主机名.
     *
     * @param remoteHost 远程主机名
     */
    public void setRemoteHost(String remoteHost) {
        // Not used
    }


    /**
     * 设置与此请求相关联的协议的名称.
     * 通常为<code>http</code>, <code>https</code>, <code>ftp</code>.
     *
     * @param scheme The scheme
     */
    public void setScheme(String scheme) {
        // Not used
    }


    public void setSecure(boolean secure) {
        this.secure = secure;
    }


    /**
     * 设置处理此请求的服务器名称（虚拟主机）.
     *
     * @param name 服务器名
     */
    public void setServerName(String name) {
        coyoteRequest.serverName().setString(name);
    }


    /**
     * 设置服务器的端口号以处理此请求.
     *
     * @param port 服务器端口
     */
    public void setServerPort(int port) {
        coyoteRequest.setServerPort(port);
    }


    // ------------------------------------------------- ServletRequest Methods


    /**
     * 返回指定的请求属性，如果它存在; 否则返回<code>null</code>.
     *
     * @param name 要返回的请求属性的名称
     */
    public Object getAttribute(String name) {

        if (name.equals(Globals.DISPATCHER_TYPE_ATTR)) {
            return (dispatcherType == null) 
                ? ApplicationFilterFactory.REQUEST_INTEGER
                : dispatcherType;
        } else if (name.equals(Globals.DISPATCHER_REQUEST_PATH_ATTR)) {
            return (requestDispatcherPath == null) 
                ? getRequestPathMB().toString()
                : requestDispatcherPath.toString();
        }

        Object attr=attributes.get(name);

        if(attr!=null)
            return(attr);

        attr =  coyoteRequest.getAttribute(name);
        if(attr != null)
            return attr;
        if( isSSLAttribute(name) ) {
            coyoteRequest.action(ActionCode.ACTION_REQ_SSL_ATTRIBUTE, 
                                 coyoteRequest);
            attr = coyoteRequest.getAttribute(Globals.CERTIFICATES_ATTR);
            if( attr != null) {
                attributes.put(Globals.CERTIFICATES_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(Globals.CIPHER_SUITE_ATTR);
            if(attr != null) {
                attributes.put(Globals.CIPHER_SUITE_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(Globals.KEY_SIZE_ATTR);
            if(attr != null) {
                attributes.put(Globals.KEY_SIZE_ATTR, attr);
            }
            attr = coyoteRequest.getAttribute(Globals.SSL_SESSION_ID_ATTR);
            if(attr != null) {
                attributes.put(Globals.SSL_SESSION_ID_ATTR, attr);
            }
            attr = attributes.get(name);
        }
        return attr;
    }


    /**
     * 测试给定名称是否是特殊的servlet规范SSL属性之一.
     */
    static boolean isSSLAttribute(String name) {
        return Globals.CERTIFICATES_ATTR.equals(name) ||
            Globals.CIPHER_SUITE_ATTR.equals(name) ||
            Globals.KEY_SIZE_ATTR.equals(name)  ||
            Globals.SSL_SESSION_ID_ATTR.equals(name);
    }

    /**
     * 返回此请求的所有请求属性的名称, 或一个空的<code>Enumeration</code>.
     */
    public Enumeration getAttributeNames() {
        if (isSecure()) {
            getAttribute(Globals.CERTIFICATES_ATTR);
        }
        return new Enumerator(attributes.keySet(), true);
    }


    /**
     * 返回此请求的字符编码.
     */
    public String getCharacterEncoding() {
      return (coyoteRequest.getCharacterEncoding());
    }


    /**
     * 返回此请求的内容长度.
     */
    public int getContentLength() {
        return (coyoteRequest.getContentLength());
    }


    /**
     * 返回此请求的内容类型.
     */
    public String getContentType() {
        return (coyoteRequest.getContentType());
    }


    /**
     * 返回此请求的servlet输入流. 默认实现返回<code>createInputStream()</code>创建的输入流.
     *
     * @exception IllegalStateException 如果<code>getReader()</code>已经调用
     * @exception IOException 如果发生输入/输出错误
     */
    public ServletInputStream getInputStream() throws IOException {

        if (usingReader)
            throw new IllegalStateException
                (sm.getString("coyoteRequest.getInputStream.ise"));

        usingInputStream = true;
        if (inputStream == null) {
            inputStream = new CoyoteInputStream(inputBuffer);
        }
        return inputStream;
    }


    /**
     * 返回客户端将接受内容的首选区域, 基于第一个<code>Accept-Language</code> header的值.
     * 如果请求没有指定首选语言, 返回服务器的默认区域设置.
     */
    public Locale getLocale() {
        if (!localesParsed)
            parseLocales();

        if (locales.size() > 0) {
            return ((Locale) locales.get(0));
        } else {
            return (defaultLocale);
        }
    }


    /**
     * 返回客户端接受内容的首选区域集, 基于所有的<code>Accept-Language</code>header.
     * 如果请求没有指定首选语言, 返回服务器的默认区域设置.
     */
    public Enumeration getLocales() {

        if (!localesParsed)
            parseLocales();

        if (locales.size() > 0)
            return (new Enumerator(locales));
        ArrayList results = new ArrayList();
        results.add(defaultLocale);
        return (new Enumerator(results));
    }


    /**
     * 返回指定请求参数的值; 否则返回<code>null</code>. 如果定义了多个值, 返回第一个.
     *
     * @param name 期望的请求参数的名称
     */
    public String getParameter(String name) {
        if (!parametersParsed)
            parseParameters();

        return coyoteRequest.getParameters().getParameter(name);
    }



    /**
     * 返回请求参数的<code>Map</code>.
     * 请求参数是请求发送的额外信息. 对于HTTP servlets, 参数包含在查询字符串或已发布表单数据中.
     *
     * @return 参数名称作为key，参数值作为值的<code>Map</code>
     */
    public Map getParameterMap() {
        if (parameterMap.isLocked())
            return parameterMap;

        Enumeration enumeration = getParameterNames();
        while (enumeration.hasMoreElements()) {
            String name = enumeration.nextElement().toString();
            String[] values = getParameterValues(name);
            parameterMap.put(name, values);
        }

        parameterMap.setLocked(true);

        return parameterMap;
    }


    /**
     * 返回此请求的所有请求参数的名称.
     */
    public Enumeration getParameterNames() {
        if (!parametersParsed)
            parseParameters();

        return coyoteRequest.getParameters().getParameterNames();
    }


    /**
     * 返回指定的请求参数的值; 否则返回<code>null</code>.
     *
     * @param name 请求参数的名称
     */
    public String[] getParameterValues(String name) {
        if (!parametersParsed)
            parseParameters();

        return coyoteRequest.getParameters().getParameterValues(name);
    }


    /**
     * 返回用于生成此请求的协议和版本.
     */
    public String getProtocol() {
        return coyoteRequest.protocol().toString();
    }


    /**
     * 包装了该请求输入流的读取器.
     * 默认实现包装了<code>BufferedReader</code>环绕<code>createInputStream()</code>返回的servlet输入流.
     *
     * @exception IllegalStateException 如果<code>getInputStream()</code>已经被调用
     * @exception IOException if an input/output error occurs
     */
    public BufferedReader getReader() throws IOException {

        if (usingInputStream)
            throw new IllegalStateException
                (sm.getString("coyoteRequest.getReader.ise"));

        usingReader = true;
        inputBuffer.checkConverter();
        if (reader == null) {
            reader = new CoyoteReader(inputBuffer);
        }
        return reader;
    }


    /**
     * 返回指定虚拟路径的实际路径.
     *
     * @param path 要翻译的路径
     *
     * @deprecated As of version 2.1 of the Java Servlet API, use
     *  <code>ServletContext.getRealPath()</code>.
     */
    public String getRealPath(String path) {
        if (context == null)
            return (null);
        ServletContext servletContext = context.getServletContext();
        if (servletContext == null)
            return (null);
        else {
            try {
                return (servletContext.getRealPath(path));
            } catch (IllegalArgumentException e) {
                return (null);
            }
        }
    }


    /**
     * 返回此请求的远程IP地址.
     */
    public String getRemoteAddr() {
        if (remoteAddr == null) {
            coyoteRequest.action
                (ActionCode.ACTION_REQ_HOST_ADDR_ATTRIBUTE, coyoteRequest);
            remoteAddr = coyoteRequest.remoteAddr().toString();
        }
        return remoteAddr;
    }


    /**
     * 返回此请求的远程主机名.
     */
    public String getRemoteHost() {
        if (remoteHost == null) {
            if (!connector.getEnableLookups()) {
                remoteHost = getRemoteAddr();
            } else {
                coyoteRequest.action
                    (ActionCode.ACTION_REQ_HOST_ATTRIBUTE, coyoteRequest);
                remoteHost = coyoteRequest.remoteHost().toString();
            }
        }
        return remoteHost;
    }

    /**
     * 返回客户端的Internet协议（IP）资源端口或发送请求的最后一个代理.
     */    
    public int getRemotePort(){
        if (remotePort == -1) {
            coyoteRequest.action
                (ActionCode.ACTION_REQ_REMOTEPORT_ATTRIBUTE, coyoteRequest);
            remotePort = coyoteRequest.getRemotePort();
        }
        return remotePort;    
    }

    /**
     * 返回收到请求的Internet协议（IP）接口的主机名.
     */
    public String getLocalName(){
        if (localName == null) {
            coyoteRequest.action
                (ActionCode.ACTION_REQ_LOCAL_NAME_ATTRIBUTE, coyoteRequest);
            localName = coyoteRequest.localName().toString();
        }
        return localName;
    }

    /**
     * 返回接收请求的接口的Internet协议（IP）地址.
     */       
    public String getLocalAddr(){
        if (localAddr == null) {
            coyoteRequest.action
                (ActionCode.ACTION_REQ_LOCAL_ADDR_ATTRIBUTE, coyoteRequest);
            localAddr = coyoteRequest.localAddr().toString();
        }
        return localAddr;    
    }


    /**
     * 返回收到请求的接口的Internet协议（IP）端口号.
     */
    public int getLocalPort(){
        if (localPort == -1){
            coyoteRequest.action
                (ActionCode.ACTION_REQ_LOCALPORT_ATTRIBUTE, coyoteRequest);
            localPort = coyoteRequest.getLocalPort();
        }
        return localPort;
    }
    
    /**
     * 返回将资源包装在指定的路径上的RequestDispatcher, 它可以被解释为相对于当前请求路径.
     *
     * @param path 要包装的资源的路径
     */
    public RequestDispatcher getRequestDispatcher(String path) {

        if (context == null)
            return (null);

        // 如果路径已经是上下文相对的, 传递它
        if (path == null)
            return (null);
        else if (path.startsWith("/"))
            return (context.getServletContext().getRequestDispatcher(path));

        // 将请求相对路径转换为上下文相对路径
        String servletPath = (String) getAttribute(Globals.INCLUDE_SERVLET_PATH_ATTR);
        if (servletPath == null)
            servletPath = getServletPath();

        // 添加路径信息, 如果有
        String pathInfo = getPathInfo();
        String requestPath = null;

        if (pathInfo == null) {
            requestPath = servletPath;
        } else {
            requestPath = servletPath + pathInfo;
        }

        int pos = requestPath.lastIndexOf('/');
        String relative = null;
        if (pos >= 0) {
            relative = RequestUtil.normalize
                (requestPath.substring(0, pos + 1) + path);
        } else {
            relative = RequestUtil.normalize(requestPath + path);
        }

        return (context.getServletContext().getRequestDispatcher(relative));
    }


    /**
     * 返回用于生成此请求的协议.
     */
    public String getScheme() {
        return (coyoteRequest.scheme().toString());
    }


    /**
     * 返回响应此请求的服务器名称.
     */
    public String getServerName() {
        return (coyoteRequest.serverName().toString());
    }


    /**
     * 返回响应此请求的服务器端口.
     */
    public int getServerPort() {
        return (coyoteRequest.getServerPort());
    }


    /**
     * 这个请求是在安全连接上接收的吗?
     */
    public boolean isSecure() {
        return (secure);
    }


    /**
     * 如果存在指定的请求属性，则删除它.
     *
     * @param name 要删除的请求属性的名称
     */
    public void removeAttribute(String name) {
        Object value = null;
        boolean found = false;

        // 移除指定的属性
        // 检查只读属性
        // 请求是每个线程，所以同步不必要
        if (readOnlyAttributes.containsKey(name)) {
            return;
        }
        found = attributes.containsKey(name);
        if (found) {
            value = attributes.get(name);
            attributes.remove(name);
        } else {
            return;
        }

        // 通知感兴趣的应用程序事件侦听器
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0))
            return;
        ServletRequestAttributeEvent event =
          new ServletRequestAttributeEvent(context.getServletContext(),
                                           getRequest(), name, value);
        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof ServletRequestAttributeListener))
                continue;
            ServletRequestAttributeListener listener =
                (ServletRequestAttributeListener) listeners[i];
            try {
                listener.attributeRemoved(event);
            } catch (Throwable t) {
                context.getLogger().error(sm.getString("coyoteRequest.attributeEvent"), t);
                // Error valve will pick this execption up and display it to user
                attributes.put( Globals.EXCEPTION_ATTR, t );
            }
        }
    }


    /**
     * 将指定的请求属性设置为指定的值.
     *
     * @param name 要设置的请求属性的名称
     * @param value 关联的值
     */
    public void setAttribute(String name, Object value) {
	
        // Name cannot be null
        if (name == null)
            throw new IllegalArgumentException
                (sm.getString("coyoteRequest.setAttribute.namenull"));

        // Null 值相当于 removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        if (name.equals(Globals.DISPATCHER_TYPE_ATTR)) {
            dispatcherType = value;
            return;
        } else if (name.equals(Globals.DISPATCHER_REQUEST_PATH_ATTR)) {
            requestDispatcherPath = value;
            return;
        }

        Object oldValue = null;
        boolean replaced = false;

        // 移除指定的属性
        // 检查只读属性
        // 请求是每个线程，所以同步不必要
        if (readOnlyAttributes.containsKey(name)) {
            return;
        }

        oldValue = attributes.put(name, value);
        if (oldValue != null) {
            replaced = true;
        }

        // 将特殊属性传递到本机层次
        if (name.startsWith("org.apache.tomcat.")) {
            coyoteRequest.setAttribute(name, value);
        }
        
        // 通知感兴趣的应用程序事件侦听器
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0))
            return;
        ServletRequestAttributeEvent event = null;
        if (replaced)
            event =
                new ServletRequestAttributeEvent(context.getServletContext(),
                                                 getRequest(), name, oldValue);
        else
            event =
                new ServletRequestAttributeEvent(context.getServletContext(),
                                                 getRequest(), name, value);

        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof ServletRequestAttributeListener))
                continue;
            ServletRequestAttributeListener listener =
                (ServletRequestAttributeListener) listeners[i];
            try {
                if (replaced) {
                    listener.attributeReplaced(event);
                } else {
                    listener.attributeAdded(event);
                }
            } catch (Throwable t) {
                context.getLogger().error(sm.getString("coyoteRequest.attributeEvent"), t);
                // Error valve will pick this execption up and display it to user
                attributes.put( Globals.EXCEPTION_ATTR, t );
            }
        }
    }


    /**
     * 重写此请求正文中使用的字符编码的名称.
     * 在读取请求参数或使用<code>getReader()</code>读取输入之前必须调用此方法
     * @param enc 要使用的字符编码
     *
     * @exception UnsupportedEncodingException 如果不支持指定的编码
     */
    public void setCharacterEncoding(String enc)
        throws UnsupportedEncodingException {

        // 确保指定的编码是有效的
        byte buffer[] = new byte[1];
        buffer[0] = (byte) 'a';
        String dummy = new String(buffer, enc);

        // 保存经过验证的编码
        coyoteRequest.setCharacterEncoding(enc);
    }


    // ---------------------------------------------------- HttpRequest Methods


    /**
     * 将cookie添加到与此请求相关联的cookie集合中.
     *
     * @param cookie The new cookie
     */
    public void addCookie(Cookie cookie) {

        if (!cookiesParsed)
            parseCookies();

        int size = 0;
        if (cookies != null) {
            size = cookies.length;
        }

        Cookie[] newCookies = new Cookie[size + 1];
        for (int i = 0; i < size; i++) {
            newCookies[i] = cookies[i];
        }
        newCookies[size] = cookie;

        cookies = newCookies;
    }


    /**
     * 将标头添加到与此请求相关联的标头集中.
     *
     * @param name The new header name
     * @param value The new header value
     */
    public void addHeader(String name, String value) {
        // Not used
    }


    /**
     * 为该请求添加一组首选区域的区域设置. 第一个添加的Locale 将会是 getLocales()第一个返回的.
     *
     * @param locale The new preferred Locale
     */
    public void addLocale(Locale locale) {
        locales.add(locale);
    }


    /**
     * 将参数名称和相应的值集合添加到该请求中.
     * (这是在基于表单的登录恢复原始请求时使用的).
     *
     * @param name 此请求参数的名称
     * @param values 此请求参数的相应值
     */
    public void addParameter(String name, String values[]) {
        coyoteRequest.getParameters().addParameterValues(name, values);
    }


    /**
     * 清空与此请求相关联的cookie集合.
     */
    public void clearCookies() {
        cookiesParsed = true;
        cookies = null;
    }


    /**
     * 清空与此请求相关联的Header集合.
     */
    public void clearHeaders() {
        // Not used
    }


    /**
     * 清空与此请求相关联的Locale集合.
     */
    public void clearLocales() {
        locales.clear();
    }


    /**
     * 清空与此请求相关联的参数集合.
     */
    public void clearParameters() {
        // Not used
    }


    /**
     * 设置此请求所使用的身份验证类型; 否则设置类型为<code>null</code>.
     * 常用值为 "BASIC", "DIGEST", or "SSL".
     *
     * @param type 使用的认证类型
     */
    public void setAuthType(String type) {
        this.authType = type;
    }


    /**
     * 设置此请求的上下文路径. 当关联上下文将请求映射到特定包装器时，通常会调用它.
     *
     * @param path 上下文路径
     */
    public void setContextPath(String path) {
        if (path == null) {
            mappingData.contextPath.setString("");
        } else {
            mappingData.contextPath.setString(path);
        }
    }


    /**
     * 设置用于此请求的HTTP请求方法.
     *
     * @param method 请求方法
     */
    public void setMethod(String method) {
        // Not used
    }


    /**
     * 为这个请求设置查询字符串. 这通常会由HTTP连接器调用, 当它解析请求头时.
     *
     * @param query The query string
     */
    public void setQueryString(String query) {
        // Not used
    }


    /**
     * 设置此请求的路径信息. 当关联上下文将请求映射到特定包装器时，通常会调用它.
     *
     * @param path 路径信息
     */
    public void setPathInfo(String path) {
        mappingData.pathInfo.setString(path);
    }


    /**
     * 该请求的请求会话ID是否通过cookie进入.  这通常由HTTP连接器调用, 当它解析请求头时.
     *
     * @param flag The new flag
     */
    public void setRequestedSessionCookie(boolean flag) {
        this.requestedSessionCookie = flag;
    }


    /**
     * 为这个请求设置请求的会话ID.  这通常由HTTP连接器调用, 当它解析请求头时.
     *
     * @param id The new session id
     */
    public void setRequestedSessionId(String id) {
        this.requestedSessionId = id;
    }


    /**
     * 该请求的请求会话ID是否通过URL进入.  这通常由HTTP连接器调用, 当它解析请求头时.
     *
     * @param flag The new flag
     */
    public void setRequestedSessionURL(boolean flag) {
        this.requestedSessionURL = flag;
    }


    /**
     * 设置这个请求未解析的请求URI. 这通常由HTTP连接器调用, 当它解析请求头时.
     *
     * @param uri The request URI
     */
    public void setRequestURI(String uri) {
        // Not used
    }


    /**
     * 设置解码后的请求URI.
     * 
     * @param uri 解码的请求URI
     */
    public void setDecodedRequestURI(String uri) {
        // Not used
    }


    /**
     * 获取已解码的请求URI.
     */
    public String getDecodedRequestURI() {
        return (coyoteRequest.decodedURI().toString());
    }


    /**
     * 获取已解码的请求URI.
     */
    public MessageBytes getDecodedRequestURIMB() {
        return (coyoteRequest.decodedURI());
    }


    /**
     * 设置此请求的servlet路径.  这通常被调用, 当关联的 Context 映射Request 为一个特定的Wrapper时.
     *
     * @param path The servlet path
     */
    public void setServletPath(String path) {
        if (path != null)
            mappingData.wrapperPath.setString(path);
    }


    /**
     * 设置已为此请求进行身份验证的Principal.  此值还用于计算<code>getRemoteUser()</code>方法返回的值.
     *
     * @param principal The user Principal
     */
    public void setUserPrincipal(Principal principal) {

        if (System.getSecurityManager() != null){
            HttpSession session = getSession(false);
            if ( (subject != null) && 
                 (!subject.getPrincipals().contains(principal)) ){
                subject.getPrincipals().add(principal);         
            } else if (session != null &&
                        session.getAttribute(Globals.SUBJECT_ATTR) == null) {
                subject = new Subject();
                subject.getPrincipals().add(principal);         
            }
            if (session != null){
                session.setAttribute(Globals.SUBJECT_ATTR, subject);
            }
        } 

        this.userPrincipal = principal;
    }


    // --------------------------------------------- HttpServletRequest Methods


    /**
     * 返回此请求所使用的身份验证类型.
     */
    public String getAuthType() {
        return (authType);
    }


    /**
     * 返回用于选择请求上下文的请求URI的一部分.
     */
    public String getContextPath() {
        return (mappingData.contextPath.toString());
    }


    /**
     * 获取上下文路径.
     * 
     * @return the context path
     */
    public MessageBytes getContextPathMB() {
        return (mappingData.contextPath);
    }


    /**
     * 返回这个请求接收到的一组Cookie.
     */
    public Cookie[] getCookies() {
        if (!cookiesParsed)
            parseCookies();

        return cookies;
    }


    /**
     * 设置这个请求接收到的一组Cookie.
     */
    public void setCookies(Cookie[] cookies) {
        this.cookies = cookies;
    }


    /**
     * 返回指定日期标头的值; 否则返回 -1.
     *
     * @param name 请求的日期标头的名称
     *
     * @exception IllegalArgumentException 如果指定的标头值不能转换为日期
     */
    public long getDateHeader(String name) {

        String value = getHeader(name);
        if (value == null)
            return (-1L);

        // 尝试以多种格式转换日期标头
        long result = FastHttpDateFormat.parseDate(value, formats);
        if (result != (-1L)) {
            return result;
        }
        throw new IllegalArgumentException(value);

    }


    /**
     * 返回指定标头的第一个值; 否则返回<code>null</code>
     *
     * @param name 请求标头的名称
     */
    public String getHeader(String name) {
        return coyoteRequest.getHeader(name);
    }


    /**
     * 返回指定标头的所有值; 否则返回一个空枚举.
     *
     * @param name 请求标头的名称
     */
    public Enumeration getHeaders(String name) {
        return coyoteRequest.getMimeHeaders().values(name);
    }


    /**
     * 返回此请求接收到的所有标头的名称.
     */
    public Enumeration getHeaderNames() {
        return coyoteRequest.getMimeHeaders().names();
    }


    /**
     * 将指定标头的值作为integer返回, 或 -1 如果此请求没有这样的标头.
     *
     * @param name 请求标头的名称
     *
     * @exception IllegalArgumentException 如果指定的标头值不能转换为整数
     */
    public int getIntHeader(String name) {
        String value = getHeader(name);
        if (value == null) {
            return (-1);
        } else {
            return (Integer.parseInt(value));
        }
    }


    /**
     * 返回此请求中使用的HTTP请求方法.
     */
    public String getMethod() {
        return coyoteRequest.method().toString();
    }


    /**
     * 返回与此请求相关联的路径信息.
     */
    public String getPathInfo() {
        return (mappingData.pathInfo.toString());
    }


    /**
     * 获取路径信息.
     * 
     * @return 路径信息
     */
    public MessageBytes getPathInfoMB() {
        return (mappingData.pathInfo);
    }


    /**
     * 返回此请求的额外路径信息, 翻译成一个真实的路径.
     */
    public String getPathTranslated() {
        if (context == null)
            return (null);

        if (getPathInfo() == null) {
            return (null);
        } else {
            return (context.getServletContext().getRealPath(getPathInfo()));
        }
    }


    /**
     * 返回与此请求关联的查询字符串.
     */
    public String getQueryString() {
        String queryString = coyoteRequest.queryString().toString();
        if (queryString == null || queryString.equals("")) {
            return (null);
        } else {
            return queryString;
        }
    }


    /**
     * 返回已对此请求进行身份验证的远程用户的名称.
     */
    public String getRemoteUser() {
        if (userPrincipal != null) {
            return (userPrincipal.getName());
        } else {
            return (null);
        }
    }


    /**
     * 获取请求路径.
     */
    public MessageBytes getRequestPathMB() {
        return (mappingData.requestPath);
    }


    /**
     * 返回此请求中包含的会话标识符.
     */
    public String getRequestedSessionId() {
        return (requestedSessionId);
    }


    /**
     * 返回此请求的请求URI.
     */
    public String getRequestURI() {
        return coyoteRequest.requestURI().toString();
    }


    /**
     * 重建用于发送请求的客户端的URL.
     * 返回的URL包含一个协议, 服务器名称, 端口号, 和服务器路径, 但它不包含查询字符串参数.
     * <p>
     * 因为这个方法返回一个<code>StringBuffer</code>, 不是一个<code>String</code>, 您可以轻松地修改URL,
     * 例如, 追加查询参数.
     * <p>
     * 此方法对于创建重定向消息和报告错误非常有用.
     *
     * @return A <code>StringBuffer</code> object containing the
     *  reconstructed URL
     */
    public StringBuffer getRequestURL() {
        StringBuffer url = new StringBuffer();
        String scheme = getScheme();
        int port = getServerPort();
        if (port < 0)
            port = 80; // Work around java.net.URL bug

        url.append(scheme);
        url.append("://");
        url.append(getServerName());
        if ((scheme.equals("http") && (port != 80))
            || (scheme.equals("https") && (port != 443))) {
            url.append(':');
            url.append(port);
        }
        url.append(getRequestURI());

        return (url);
    }


    /**
     * 返回用于选择将处理此请求的servlet的请求URI的一部分.
     */
    public String getServletPath() {
        return (mappingData.wrapperPath.toString());
    }


    /**
     * 获取servlet路径.
     * 
     * @return the servlet path
     */
    public MessageBytes getServletPathMB() {
        return (mappingData.wrapperPath);
    }


    /**
     * 返回与此请求关联的会话, 必要时创建一个.
     */
    public HttpSession getSession() {
        Session session = doGetSession(true);
        if (session != null) {
            return session.getSession();
        } else {
            return null;
        }
    }


    /**
     * 返回与此请求关联的会话, 必要时创建一个.
     *
     * @param create 如果不存在，是否创建新会话
     */
    public HttpSession getSession(boolean create) {
        Session session = doGetSession(create);
        if (session != null) {
            return session.getSession();
        } else {
            return null;
        }
    }


    /**
     * 返回<code>true</code>, 如果包含在这个请求中的会话标识符来自cookie.
     */
    public boolean isRequestedSessionIdFromCookie() {
        if (requestedSessionId != null)
            return (requestedSessionCookie);
        else
            return (false);
    }


    /**
     * 返回<code>true</code>, 如果这个请求中包含的会话标识符来自请求URI.
     */
    public boolean isRequestedSessionIdFromURL() {
        if (requestedSessionId != null)
            return (requestedSessionURL);
        else
            return (false);
    }


    /**
     * 返回<code>true</code>, 如果这个请求中包含的会话标识符来自请求URI.
     *
     * @deprecated As of Version 2.1 of the Java Servlet API, use
     *  <code>isRequestedSessionIdFromURL()</code> instead.
     */
    public boolean isRequestedSessionIdFromUrl() {
        return (isRequestedSessionIdFromURL());
    }


    /**
     * 返回<code>true</code>, 如果此请求中包含的会话标识符标识会话有效.
     */
    public boolean isRequestedSessionIdValid() {
        if (requestedSessionId == null)
            return (false);
        if (context == null)
            return (false);
        Manager manager = context.getManager();
        if (manager == null)
            return (false);
        Session session = null;
        try {
            session = manager.findSession(requestedSessionId);
        } catch (IOException e) {
            session = null;
        }
        if ((session != null) && session.isValid())
            return (true);
        else
            return (false);
    }


    /**
     * 返回<code>true</code>, 如果经过验证的用户主体拥有指定的角色名.
     *
     * @param role 要验证的角色名
     */
    public boolean isUserInRole(String role) {

        // 已验证的主体?
        if (userPrincipal == null)
            return (false);

        // 将检查角色配置的Realm
        if (context == null)
            return (false);
        Realm realm = context.getRealm();
        if (realm == null)
            return (false);

        // 检查<security-role-ref>元素配置的角色别名
        if (wrapper != null) {
            String realRole = wrapper.findSecurityReference(role);
            if ((realRole != null) &&
                realm.hasRole(userPrincipal, realRole))
                return (true);
        }

        // 直接检查角色定义元素<security-role>
        return (realm.hasRole(userPrincipal, role));
    }


    /**
     * 返回已对此请求进行身份验证的主体.
     */
    public Principal getUserPrincipal() {
        if (userPrincipal instanceof GenericPrincipal) {
            return ((GenericPrincipal) userPrincipal).getUserPrincipal();
        } else {
            return (userPrincipal);
        }
    }


    /**
     * 返回与此请求关联的会话, 必要时创建一个.
     */
    public Session getSessionInternal() {
        return doGetSession(true);
    }


    /**
     * 返回与此请求关联的会话, 必要时创建一个.
     *
     * @param create 如果不存在，是否创建新会话
     */
    public Session getSessionInternal(boolean create) {
        return doGetSession(create);
    }


    // ------------------------------------------------------ Protected Methods


    protected Session doGetSession(boolean create) {
        // 如果还没有分配上下文，就不能有会话
        if (context == null)
            return (null);

        // 返回当前会话，如果它存在并且有效
        if ((session != null) && !session.isValid())
            session = null;
        if (session != null)
            return (session);

        // 返回请求的会话，如果它存在并且有效
        Manager manager = null;
        if (context != null)
            manager = context.getManager();
        if (manager == null)
            return (null);      // 不支持Session
        if (requestedSessionId != null) {
            try {
                session = manager.findSession(requestedSessionId);
            } catch (IOException e) {
                session = null;
            }
            if ((session != null) && !session.isValid())
                session = null;
            if (session != null) {
                session.access();
                return (session);
            }
        }

        // 如果请求，创建一个新会话，并且不提交响应
        if (!create)
            return (null);
        if ((context != null) && (response != null) &&
            context.getCookies() &&
            response.getResponse().isCommitted()) {
            throw new IllegalStateException
              (sm.getString("coyoteRequest.sessionCreateCommitted"));
        }

        // 如果在cookie中提交会话ID，则尝试重用会话ID
        // 如果会话ID来自URL，请不要重用它, 为了防止可能的网络钓鱼攻击
        if (connector.getEmptySessionPath() 
                && isRequestedSessionIdFromCookie()) {
            session = manager.createSession(getRequestedSessionId());
        } else {
            session = manager.createSession(null);
        }

        // 基于该会话创建一个新会话cookie
        if ((session != null) && (getContext() != null)
               && getContext().getCookies()) {
            Cookie cookie = new Cookie(Globals.SESSION_COOKIE_NAME,
                                       session.getIdInternal());
            configureSessionCookie(cookie);
            response.addCookie(cookie);
        }

        if (session != null) {
            session.access();
            return (session);
        } else {
            return (null);
        }
    }

    /**
     * 配置指定的JSESSIONID cookie.
     *
     * @param cookie 要配置的JSESSIONID cookie
     */
    protected void configureSessionCookie(Cookie cookie) {
        cookie.setMaxAge(-1);
        String contextPath = null;
        if (!connector.getEmptySessionPath() && (getContext() != null)) {
            contextPath = getContext().getEncodedPath();
        }
        if ((contextPath != null) && (contextPath.length() > 0)) {
            cookie.setPath(contextPath);
        } else {
            cookie.setPath("/");
        }
        if (isSecure()) {
            cookie.setSecure(true);
        }
    }

    /**
     * 解析cookie.
     */
    protected void parseCookies() {

        cookiesParsed = true;

        Cookies serverCookies = coyoteRequest.getCookies();
        int count = serverCookies.getCookieCount();
        if (count <= 0)
            return;

        cookies = new Cookie[count];

        int idx=0;
        for (int i = 0; i < count; i++) {
            ServerCookie scookie = serverCookies.getCookie(i);
            try {
                Cookie cookie = new Cookie(scookie.getName().toString(),
                                           scookie.getValue().toString());
                cookie.setPath(scookie.getPath().toString());
                cookie.setVersion(scookie.getVersion());
                String domain = scookie.getDomain().toString();
                if (domain != null) {
                    cookie.setDomain(scookie.getDomain().toString());
                }
                cookies[idx++] = cookie;
            } catch(IllegalArgumentException e) {
                // Ignore bad cookie
            }
        }
        if( idx < count ) {
            Cookie [] ncookies = new Cookie[idx];
            System.arraycopy(cookies, 0, ncookies, 0, idx);
            cookies = ncookies;
        }
    }

    /**
     * 解析请求参数.
     */
    protected void parseParameters() {

        parametersParsed = true;

        Parameters parameters = coyoteRequest.getParameters();

        // getCharacterEncoding() 可能已被重写以搜索包含请求编码的隐藏表单字段
        String enc = getCharacterEncoding();

        boolean useBodyEncodingForURI = connector.getUseBodyEncodingForURI();
        if (enc != null) {
            parameters.setEncoding(enc);
            if (useBodyEncodingForURI) {
                parameters.setQueryStringEncoding(enc);
            }
        } else {
            parameters.setEncoding
                (org.apache.coyote.Constants.DEFAULT_CHARACTER_ENCODING);
            if (useBodyEncodingForURI) {
                parameters.setQueryStringEncoding
                    (org.apache.coyote.Constants.DEFAULT_CHARACTER_ENCODING);
            }
        }

        parameters.handleQueryParameters();

        if (usingInputStream || usingReader)
            return;

        if (!getMethod().equalsIgnoreCase("POST"))
            return;

        String contentType = getContentType();
        if (contentType == null)
            contentType = "";
        int semicolon = contentType.indexOf(';');
        if (semicolon >= 0) {
            contentType = contentType.substring(0, semicolon).trim();
        } else {
            contentType = contentType.trim();
        }
        if (!("application/x-www-form-urlencoded".equals(contentType)))
            return;

        int len = getContentLength();

        if (len > 0) {
            int maxPostSize = connector.getMaxPostSize();
            if ((maxPostSize > 0) && (len > maxPostSize)) {
                context.getLogger().info
                    (sm.getString("coyoteRequest.postTooLarge"));
                throw new IllegalStateException("Post too large");
            }
            try {
                byte[] formData = null;
                if (len < CACHED_POST_LEN) {
                    if (postData == null)
                        postData = new byte[CACHED_POST_LEN];
                    formData = postData;
                } else {
                    formData = new byte[len];
                }
                int actualLen = readPostBody(formData, len);
                if (actualLen == len) {
                    parameters.processParameters(formData, 0, len);
                }
            } catch (Throwable t) {
                ; // Ignore
            }
        }
    }


    /**
     * 读取数组中的POST正文.
     */
    protected int readPostBody(byte body[], int len) throws IOException {

        int offset = 0;
        do {
            int inputLen = getStream().read(body, offset, len - offset);
            if (inputLen <= 0) {
                return offset;
            }
            offset += inputLen;
        } while ((len - offset) > 0);
        return len;
    }


    /**
     * 解析请求区域.
     */
    protected void parseLocales() {

        localesParsed = true;

        Enumeration values = getHeaders("accept-language");

        while (values.hasMoreElements()) {
            String value = values.nextElement().toString();
            parseLocalesHeader(value);
        }
    }


    /**
     * 解析accept-language header 值.
     */
    protected void parseLocalesHeader(String value) {

        // 存储在本地集合中请求的语言, 按质量值排序(因此，可以按降序添加区域设置).  值将会是ArrayLists, 包含相应要添加的Locales
        TreeMap locales = new TreeMap();

        // 预处理的值, 删除所有空格
        int white = value.indexOf(' ');
        if (white < 0)
            white = value.indexOf('\t');
        if (white >= 0) {
            StringBuffer sb = new StringBuffer();
            int len = value.length();
            for (int i = 0; i < len; i++) {
                char ch = value.charAt(i);
                if ((ch != ' ') && (ch != '\t'))
                    sb.append(ch);
            }
            value = sb.toString();
        }

        // 处理每个逗号分隔的语言规范
        parser.setString(value);        // ASSERT: parser是可用的
        int length = parser.getLength();
        while (true) {

            // 提取下一个逗号分隔的条目
            int start = parser.getIndex();
            if (start >= length)
                break;
            int end = parser.findChar(',');
            String entry = parser.extract(start, end).trim();
            parser.advance();   //以下条目

            // 提取此项的质量因数
            double quality = 1.0;
            int semi = entry.indexOf(";q=");
            if (semi >= 0) {
                try {
                    quality = Double.parseDouble(entry.substring(semi + 3));
                } catch (NumberFormatException e) {
                    quality = 0.0;
                }
                entry = entry.substring(0, semi);
            }

            // 跳过条目，不打算跟踪
            if (quality < 0.00005)
                continue;       // 零（或有效零）质量因数
            if ("*".equals(entry))
                continue;       // FIXME - "*" 未处理条目

            // 提取此条目的语言和国家
            String language = null;
            String country = null;
            String variant = null;
            int dash = entry.indexOf('-');
            if (dash < 0) {
                language = entry;
                country = "";
                variant = "";
            } else {
                language = entry.substring(0, dash);
                country = entry.substring(dash + 1);
                int vDash = country.indexOf('-');
                if (vDash > 0) {
                    String cTemp = country.substring(0, vDash);
                    variant = country.substring(vDash + 1);
                    country = cTemp;
                } else {
                    variant = "";
                }
            }

            // 为该质量级别的区域列表添加新的区域设置
            Locale locale = new Locale(language, country, variant);
            Double key = new Double(-quality);  // Reverse the order
            ArrayList values = (ArrayList) locales.get(key);
            if (values == null) {
                values = new ArrayList();
                locales.put(key, values);
            }
            values.add(locale);

        }

        // 处理最高等级的质量值->最低排序(due to negating the Double value when creating the key)
        Iterator keys = locales.keySet().iterator();
        while (keys.hasNext()) {
            Double key = (Double) keys.next();
            ArrayList list = (ArrayList) locales.get(key);
            Iterator values = list.iterator();
            while (values.hasNext()) {
                Locale locale = (Locale) values.next();
                addLocale(locale);
            }
        }

    }

/*************************自己加的，解决报错问题****************************/
	@Override
	public AsyncContext getAsyncContext() {
		return null;
	}

	@Override
	public DispatcherType getDispatcherType() {
		return null;
	}

	@Override
	public ServletContext getServletContext() {
		return null;
	}

	@Override
	public boolean isAsyncStarted() {
		return false;
	}

	@Override
	public boolean isAsyncSupported() {
		return false;
	}

	@Override
	public AsyncContext startAsync() {
		return null;
	}

	@Override
	public AsyncContext startAsync(ServletRequest arg0, ServletResponse arg1) {
		return null;
	}

	@Override
	public boolean authenticate(HttpServletResponse arg0) throws IOException, ServletException {
		return false;
	}

	@Override
	public Part getPart(String arg0) throws IOException, IllegalStateException, ServletException {
		return null;
	}

	@Override
	public Collection<Part> getParts() throws IOException, IllegalStateException, ServletException {
		return null;
	}

	@Override
	public void login(String arg0, String arg1) throws ServletException {
		
	}

	@Override
	public void logout() throws ServletException {
		
	}
/*************************自己加的，解决报错问题****************************/

}
