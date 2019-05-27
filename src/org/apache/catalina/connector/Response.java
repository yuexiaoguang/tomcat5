package org.apache.catalina.connector;


import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Session;
import org.apache.catalina.Wrapper;
import org.apache.catalina.util.CharsetMapper;
import org.apache.catalina.util.DateTool;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.security.SecurityUtil;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.UEncoder;
import org.apache.tomcat.util.http.FastHttpDateFormat;
import org.apache.tomcat.util.http.MimeHeaders;
import org.apache.tomcat.util.http.ServerCookie;
import org.apache.tomcat.util.net.URL;
import org.apache.tomcat.util.compat.JdkCompat;

/**
 * 原始response的封装对象.
 */
public class Response implements HttpServletResponse {

    // ----------------------------------------------------------- Constructors

    static {
        // 确保为SM加载URL
        URL.isSchemeChar('c');
    }

    public Response() {
        urlEncoder.addSafeCharacter('/');
    }


    // ----------------------------------------------------- Class Variables


    /**
     * JDK兼容支持
     */
    private static final JdkCompat jdkCompat = JdkCompat.getJdkCompat();


    /**
     * 实现类描述信息
     */
    protected static final String info = "org.apache.coyote.tomcat5.CoyoteResponse/1.0";


    /**
     * The string manager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);


    // ----------------------------------------------------- Instance Variables

    /**
     * 用于创建日期标头的日期格式.
     */
    protected SimpleDateFormat format = null;


    // ------------------------------------------------------------- Properties


    /**
     * 关联的Catalina 连接器.
     */
    protected Connector connector;

    /**
     * 返回接收此请求的连接器.
     */
    public Connector getConnector() {
        return (this.connector);
    }

    /**
     * 设置接收该请求的连接器.
     *
     * @param connector The new connector
     */
    public void setConnector(Connector connector) {
        this.connector = connector;
        if("AJP/1.3".equals(connector.getProtocol())) {
            // 一个ajp-packet的默认大小
            outputBuffer = new OutputBuffer(8184);
        } else {
            outputBuffer = new OutputBuffer();
        }
        outputStream = new CoyoteOutputStream(outputBuffer);
        writer = new CoyoteWriter(outputBuffer);
    }


    /**
     * Coyote response.
     */
    protected org.apache.coyote.Response coyoteResponse;

    public void setCoyoteResponse(org.apache.coyote.Response coyoteResponse) {
        this.coyoteResponse = coyoteResponse;
        outputBuffer.setResponse(coyoteResponse);
    }

    public org.apache.coyote.Response getCoyoteResponse() {
        return (coyoteResponse);
    }


    /**
     * 返回正在处理此请求的上下文.
     */
    public Context getContext() {
        return (request.getContext());
    }

    /**
     * 设置正在处理此请求的上下文. 
     * 一旦确定了适当的上下文，就必须调用它, 因为它确定<code>getContextPath()</code>返回的值, 从而启用对请求URI的解析.
     *
     * @param context 新关联的Context
     */
    public void setContext(Context context) {
        request.setContext(context);
    }


    /**
     * 关联的输出缓冲区.
     */
    protected OutputBuffer outputBuffer;


    /**
     * 关联的输出流.
     */
    protected CoyoteOutputStream outputStream;


    protected CoyoteWriter writer;


    /**
     * 应用程序提交标志.
     */
    protected boolean appCommitted = false;


    /**
     * 包含标记.
     */
    protected boolean included = false;

    
    /**
     * 字符编码标志
     */
    private boolean isCharacterEncodingSet = false;
    
    /**
     * 上下文类型标志
     */    
    private boolean isContentTypeSet = false;

    
    /**
     * 错误标志.
     */
    protected boolean error = false;


    /**
     * 相应关联的一组Cookie.
     */
    protected ArrayList cookies = new ArrayList();


    /**
     * 使用输出流标志.
     */
    protected boolean usingOutputStream = false;


    /**
     * 使用writer标志.
     */
    protected boolean usingWriter = false;


    /**
     * URL encoder.
     */
    protected UEncoder urlEncoder = new UEncoder();


    /**
     * 可回收的缓冲区来保存重定向URL.
     */
    protected CharChunk redirectURLCC = new CharChunk();


    // --------------------------------------------------------- Public Methods


    /**
     * 释放所有对象引用, 初始化实例变量, 准备重用这个对象.
     */
    public void recycle() {
        outputBuffer.recycle();
        usingOutputStream = false;
        usingWriter = false;
        appCommitted = false;
        included = false;
        error = false;
        isContentTypeSet = false;
        isCharacterEncodingSet = false;
        
        cookies.clear();

        if (Constants.SECURITY) {
            if (facade != null) {
                facade.clear();
                facade = null;
            }
            if (outputStream != null) {
                outputStream.clear();
                outputStream = null;
            }
            if (writer != null) {
                writer.clear();
                writer = null;
            }
        } else {
            writer.recycle();
        }
    }


    // ------------------------------------------------------- Response Methods


    /**
     * 返回实际写入输出流的字节数.
     */
    public int getContentCount() {
        return outputBuffer.getContentWritten();
    }


    /**
     * 设置应用程序提交标志.
     * 
     * @param appCommitted 新应用程序提交的标志值
     */
    public void setAppCommitted(boolean appCommitted) {
        this.appCommitted = appCommitted;
    }


    /**
     * 应用程序提交标志.
     */
    public boolean isAppCommitted() {
        return (this.appCommitted || isCommitted() || isSuspended()
                || ((getContentLength() > 0) 
                    && (getContentCount() >= getContentLength())));
    }


    /**
     * 返回"内部包含的处理"标志.
     */
    public boolean getIncluded() {
        return included;
    }


    /**
     * 设置"内部包含的处理"标志.
     *
     * @param included <code>true</code>如果当期是在RequestDispatcher.include()中, 或者<code>false</code>
     */
    public void setIncluded(boolean included) {
        this.included = included;
    }


    /**
     * 返回描述信息和相应版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 与此响应相关联的请求.
     */
    protected Request request = null;

    /**
     * 返回与此响应相关联的请求.
     */
    public org.apache.catalina.connector.Request getRequest() {
        return (this.request);
    }

    /**
     * 设置与此响应相关联的请求.
     *
     * @param request The new associated request
     */
    public void setRequest(org.apache.catalina.connector.Request request) {
        this.request = (Request) request;
    }


    /**
     * 与此响应相关联的外观.
     */
    protected ResponseFacade facade = null;

    /**
     * 返回原始<code>ServletResponse</code>.
     */
    public HttpServletResponse getResponse() {
        if (facade == null) {
            facade = new ResponseFacade(this);
        }
        return (facade);
    }


    /**
     * 返回与此响应相关联的输出流.
     */
    public OutputStream getStream() {
        if (outputStream == null) {
            outputStream = new CoyoteOutputStream(outputBuffer);
        }
        return outputStream;
    }


    /**
     * 设置与此响应相关联的输出流.
     *
     * @param stream 新的输出流
     */
    public void setStream(OutputStream stream) {
        // This method is evil
    }


    /**
     * 设置暂停标志.
     * 
     * @param suspended 是否暂停
     */
    public void setSuspended(boolean suspended) {
        outputBuffer.setSuspended(suspended);
    }


    /**
     * 是否暂停.
     */
    public boolean isSuspended() {
        return outputBuffer.isSuspended();
    }


    /**
     * 设置错误标志.
     */
    public void setError() {
        error = true;
    }


    /**
     * 是否错误.
     */
    public boolean isError() {
        return error;
    }


    /**
     * 创建并返回一个写入内容的ServletOutputStream.
     *
     * @exception IOException 如果出现输入/输出错误
     */
    public ServletOutputStream createOutputStream() 
        throws IOException {
        // Probably useless
        if (outputStream == null) {
            outputStream = new CoyoteOutputStream(outputBuffer);
        }
        return outputStream;
    }


    /**
     * 执行刷新和关闭输出流或写入器所需的任何操作, 在一个操作中.
     *
     * @exception IOException 如果出现输入/输出错误
     */
    public void finishResponse() throws IOException {
        // Writing leftover bytes
        try {
            outputBuffer.close();
        } catch(IOException e) {
	    ;
        } catch(Throwable t) {
	    t.printStackTrace();
        }
    }


    /**
     * 返回该响应设置或计算的内容长度.
     */
    public int getContentLength() {
        return (coyoteResponse.getContentLength());
    }


    /**
     * 返回该响应设置或计算的内容类型, 或<code>null</code>如果没有设置内容类型.
     */
    public String getContentType() {
        return (coyoteResponse.getContentType());
    }


    /**
     * 返回可以用来输出错误消息的PrintWriter, 不管流或写入器是否已经被获取.
     *
     * @return Writer 可用于错误报告. 
     * 如果响应不是一个sendError返回的错误报告，或者处理servlet期间抛出的异常触发(只有在那种情况下), 如果响应流已经使用返回null.
     *
     * @exception IOException 如果出现输入/输出错误
     */
    public PrintWriter getReporter() throws IOException {
        if (outputBuffer.isNew()) {
            outputBuffer.checkConverter();
            if (writer == null) {
                writer = new CoyoteWriter(outputBuffer);
            }
            return writer;
        } else {
            return null;
        }
    }


    // ------------------------------------------------ ServletResponse Methods


    /**
     * 刷新缓冲区并提交此响应.
     *
     * @exception IOException if an input/output error occurs
     */
    public void flushBuffer() 
        throws IOException {
        outputBuffer.flush();
    }


    /**
     * 返回用于此响应的实际缓冲区大小.
     */
    public int getBufferSize() {
        return outputBuffer.getBufferSize();
    }


    /**
     * 返回用于此响应的字符编码.
     */
    public String getCharacterEncoding() {
        return (coyoteResponse.getCharacterEncoding());
    }


    /**
     * 返回与此响应相关联的servlet输出流.
     *
     * @exception IllegalStateException 如果这个响应的<code>getWriter</code>已经被调用
     * @exception IOException 如果出现输入/输出错误
     */
    public ServletOutputStream getOutputStream() 
        throws IOException {

        if (usingWriter)
            throw new IllegalStateException
                (sm.getString("coyoteResponse.getOutputStream.ise"));

        usingOutputStream = true;
        if (outputStream == null) {
            outputStream = new CoyoteOutputStream(outputBuffer);
        }
        return outputStream;

    }


    /**
     * 返回分配给此响应的区域设置
     */
    public Locale getLocale() {
        return (coyoteResponse.getLocale());
    }


    /**
     * 返回与此响应相关联的 writer.
     *
     * @exception IllegalStateException 如果这个响应的<code>getOutputStream</code>已经被调用
     * @exception IOException 如果出现输入/输出错误
     */
    public PrintWriter getWriter() 
        throws IOException {

        if (usingOutputStream)
            throw new IllegalStateException
                (sm.getString("coyoteResponse.getWriter.ise"));

        /**
         * 如果响应的字符编码没有如<code>getCharacterEncoding</code> 所描述的那样指定(i.e., 该方法只返回默认值<code>ISO-8859-1</code>),
         * <code>getWriter</code>更新它为<code>ISO-8859-1</code>
         * (随后调用getContentType()的结果将包含一个charset=ISO-8859-1 组件, 也将反映在Content-Type响应头中,
         * 从而满足servlet规范要求容器必须传递用于servlet响应的writer的字符编码到客户端).
         */
        setCharacterEncoding(getCharacterEncoding());

        usingWriter = true;
        outputBuffer.checkConverter();
        if (writer == null) {
            writer = new CoyoteWriter(outputBuffer);
        }
        return writer;
    }


    /**
     * 这个响应的输出已经被提交了吗?
     */
    public boolean isCommitted() {
        return (coyoteResponse.isCommitted());
    }


    /**
     * 清除写入缓冲区的内容.
     *
     * @exception IllegalStateException 如果此响应已经提交
     */
    public void reset() {

        if (included)
            return;     // 忽略包含servlet的任何调用

        coyoteResponse.reset();
        outputBuffer.reset();
    }


    /**
     * 重置数据缓冲区，但不设置任何状态或标头信息.
     *
     * @exception IllegalStateException 如果响应已经提交
     */
    public void resetBuffer() {
        if (isCommitted())
            throw new IllegalStateException
                (sm.getString("coyoteResponse.resetBuffer.ise"));

        outputBuffer.reset();
    }


    /**
     * 设置用于此响应的缓冲区大小.
     *
     * @param size 新缓冲区大小
     *
     * @exception IllegalStateException 如果在该响应已提交输出之后调用此方法
     */
    public void setBufferSize(int size) {
        if (isCommitted() || !outputBuffer.isNew())
            throw new IllegalStateException(sm.getString("coyoteResponse.setBufferSize.ise"));

        outputBuffer.setBufferSize(size);
    }


    /**
     * 设置此响应的内容长度（字节）.
     *
     * @param length 内容长度
     */
    public void setContentLength(int length) {
        if (isCommitted())
            return;

        // 忽略包含的servlet的任何调用
        if (included)
            return;
        
        if (usingWriter)
            return;
        
        coyoteResponse.setContentLength(length);
    }


    /**
     * 设置此响应的内容类型.
     *
     * @param type 内容类型
     */
    public void setContentType(String type) {

        if (isCommitted())
            return;

        // 忽略包含的servlet的任何调用
        if (included)
            return;

        // 如果getWriter()已经被调用忽略字符集
        if (usingWriter) {
            if (type != null) {
                int index = type.indexOf(";");
                if (index != -1) {
                    type = type.substring(0, index);
                }
            }
        }

        coyoteResponse.setContentType(type);

        // 看看内容类型包含的字符集
        if (type != null) {
            int index = type.indexOf(";");
            if (index != -1) {
                int len = type.length();
                index++;
                while (index < len && Character.isSpace(type.charAt(index))) {
                    index++;
                }
                if (index+7 < len
                        && type.charAt(index) == 'c'
                        && type.charAt(index+1) == 'h'
                        && type.charAt(index+2) == 'a'
                        && type.charAt(index+3) == 'r'
                        && type.charAt(index+4) == 's'
                        && type.charAt(index+5) == 'e'
                        && type.charAt(index+6) == 't'
                        && type.charAt(index+7) == '=') {
                    isCharacterEncodingSet = true;
                }
            }
        }

        isContentTypeSet = true;    
    }


    /*
     * 重写请求正文中使用的字符编码的名称. 在读取请求参数或使用getReader()读取输入之前必须调用此方法.
     *
     * @param charset 字符串编码的名称.
     */
    public void setCharacterEncoding(String charset) {

        if (isCommitted())
            return;
        
        // 忽略包含servlet的任何调用
        if (included)
            return;     
        
        // 忽略getWriter调用之后的任何调用
        // 使用默认的
        if (usingWriter)
            return;

        coyoteResponse.setCharacterEncoding(charset);
        isCharacterEncodingSet = true;
    }

    
    
    /**
     * 设置适合此响应的区域设置, 包括设置适当的字符编码.
     *
     * @param locale The new locale
     */
    public void setLocale(Locale locale) {

        if (isCommitted())
            return;

        // 忽略包含servlet的任何调用
        if (included)
            return;

        coyoteResponse.setLocale(locale);

        // 忽略getWriter调用之后的任何调用
        // 使用默认的
        if (usingWriter)
            return;

        if (isCharacterEncodingSet) {
            return;
        }

        CharsetMapper cm = getContext().getCharsetMapper();
        String charset = cm.getCharset( locale );
        if ( charset != null ){
            coyoteResponse.setCharacterEncoding(charset);
        }
    }


    // --------------------------------------------------- HttpResponse Methods


    /**
     * 返回此响应设置的所有cookie,或空数组.
     */
    public Cookie[] getCookies() {
        return ((Cookie[]) cookies.toArray(new Cookie[cookies.size()]));
    }


    /**
     * 返回指定标头的值, 或<code>null</code> .  如果为这个名称添加了一个以上的值, 只返回第一个; 使用getHeaderValues()检索所有的值.
     *
     * @param name Header name to look up
     */
    public String getHeader(String name) {
        return coyoteResponse.getMimeHeaders().getHeader(name);
    }


    /**
     * 返回为该响应设置的所有标头名称, 或空数组.
     */
    public String[] getHttpHeaderNames() {

        MimeHeaders headers = coyoteResponse.getMimeHeaders();
        int n = headers.size();
        String[] result = new String[n];
        for (int i = 0; i < n; i++) {
            result[i] = headers.getName(i).toString();
        }
        return result;
    }


    /**
     * 返回指定标头名称的所有标头值, 或空数组.
     *
     * @param name Header name to look up
     */
    public String[] getHeaderValues(String name) {

        Enumeration enumeration = coyoteResponse.getMimeHeaders().values(name);
        Vector result = new Vector();
        while (enumeration.hasMoreElements()) {
            result.addElement(enumeration.nextElement());
        }
        String[] resultArray = new String[result.size()];
        result.copyInto(resultArray);
        return resultArray;

    }


    /**
     * 返回使用<code>sendError()</code>设置的错误信息
     */
    public String getMessage() {
        return coyoteResponse.getMessage();
    }


    /**
     * 返回这个响应的HTTP 状态码.
     */
    public int getStatus() {
        return coyoteResponse.getStatus();
    }


    /**
     * 重置此响应, 并指定HTTP状态码和相应消息的值.
     *
     * @exception IllegalStateException 如果此响应已经提交
     */
    public void reset(int status, String message) {
        reset();
        setStatus(status, message);
    }


    // -------------------------------------------- HttpServletResponse Methods


    /**
     * 将指定的cookie添加到将包含在该响应中的cookie中.
     *
     * @param cookie Cookie to be added
     */
    public void addCookie(final Cookie cookie) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        cookies.add(cookie);

        final StringBuffer sb = new StringBuffer();
        if (SecurityUtil.isPackageProtectionEnabled()) {
            AccessController.doPrivileged(new PrivilegedAction() {
                public Object run(){
                    ServerCookie.appendCookieValue
                        (sb, cookie.getVersion(), cookie.getName(), 
                         cookie.getValue(), cookie.getPath(), 
                         cookie.getDomain(), cookie.getComment(), 
                         cookie.getMaxAge(), cookie.getSecure());
                    return null;
                }
            });
        } else {
            ServerCookie.appendCookieValue
                (sb, cookie.getVersion(), cookie.getName(), cookie.getValue(),
                     cookie.getPath(), cookie.getDomain(), cookie.getComment(), 
                     cookie.getMaxAge(), cookie.getSecure());
        }

        // 标头值是Set-Cookie, 对于 "old" 和 v.1 ( RFC2109 )
        // 浏览器不支持RFC2965, servlet规范要求 2109.
        addHeader("Set-Cookie", sb.toString());
    }


    /**
     * 将指定的日期标头添加到指定的值.
     *
     * @param name 要设置的标头的名称
     * @param value 要设置的日期值
     */
    public void addDateHeader(String name, long value) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        if (format == null) {
            format = new SimpleDateFormat(DateTool.HTTP_RESPONSE_DATE_HEADER,
                                          Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        addHeader(name, FastHttpDateFormat.formatDate(value, format));
    }


    /**
     * 将指定的标头添加到指定的值.
     *
     * @param name 要设置的标头的名称
     * @param value 要设置的值
     */
    public void addHeader(String name, String value) {
        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        coyoteResponse.addHeader(name, value);
    }


    /**
     * 将指定的整数标头添加到指定的值.
     *
     * @param name 要设置的标头的名称
     * @param value 要设置的整数值
     */
    public void addIntHeader(String name, int value) {
        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        addHeader(name, "" + value);
    }


    /**
     * 这个响应中已经设置了指定的标头吗?
     *
     * @param name 要检查的标头名称
     */
    public boolean containsHeader(String name) {
        return coyoteResponse.containsHeader(name);
    }


    /**
     * 将与此响应相关联的会话标识符编码到指定的重定向URL中.
     *
     * @param url 要编码的URL
     */
    public String encodeRedirectURL(String url) {
        if (isEncodeable(toAbsolute(url))) {
            return (toEncoded(url, request.getSessionInternal().getIdInternal()));
        } else {
            return (url);
        }
    }


    /**
     * 将与此响应相关联的会话标识符编码到指定的重定向URL中.
     *
     * @param url 要编码的URL
     *
     * @deprecated As of Version 2.1 of the Java Servlet API, use
     *  <code>encodeRedirectURL()</code> instead.
     */
    public String encodeRedirectUrl(String url) {
        return (encodeRedirectURL(url));
    }


    /**
     * 将与此响应相关联的会话标识符编码到指定的URL中.
     *
     * @param url 要编码的URL
     */
    public String encodeURL(String url) {
        String absolute = toAbsolute(url);
        if (isEncodeable(absolute)) {
            // W3C规范明确表示
            if (url.equalsIgnoreCase("")){
                url = absolute;
            }
            return (toEncoded(url, request.getSessionInternal().getIdInternal()));
        } else {
            return (url);
        }
    }


    /**
     * 将与此响应相关联的会话标识符编码到指定的URL中.
     *
     * @param url 要编码的URL
     *
     * @deprecated As of Version 2.1 of the Java Servlet API, use
     *  <code>encodeURL()</code> instead.
     */
    public String encodeUrl(String url) {
        return (encodeURL(url));
    }


    /**
     * 发送请求的确认.
     * 
     * @exception IOException 如果出现输入/输出错误
     */
    public void sendAcknowledgement() throws IOException {

        if (isCommitted())
            return;

        // 忽略包含servlet的任何调用
        if (included)
            return; 

        coyoteResponse.acknowledge();
    }


    /**
     * 用指定的状态和默认消息发送错误响应.
     *
     * @param status 要发送的HTTP状态码
     *
     * @exception IllegalStateException 如果此响应已经提交
     * @exception IOException 如果出现输入/输出错误
     */
    public void sendError(int status) throws IOException {
        sendError(status, null);
    }


    /**
     * 用指定的状态和消息发送错误响应
     *
     * @param status 要发送的HTTP状态码
     * @param message 发送相应的消息
     *
     * @exception IllegalStateException 如果此响应已经提交
     * @exception IOException 如果出现输入/输出错误
     */
    public void sendError(int status, String message) throws IOException {

        if (isCommitted())
            throw new IllegalStateException
                (sm.getString("coyoteResponse.sendError.ise"));

        // Ignore any call from an included servlet
        if (included)
            return; 

        Wrapper wrapper = getRequest().getWrapper();
        if (wrapper != null) {
            wrapper.incrementErrorCount();
        } 

        setError();

        coyoteResponse.setStatus(status);
        coyoteResponse.setMessage(message);

        // 清除已缓冲的所有数据内容
        resetBuffer();

        // 使响应完成(从应用程序的角度)
        setSuspended(true);
    }


    /**
     * 将临时重定向发送到指定的重定向位置URL.
     *
     * @param location 重定向到的位置URL
     *
     * @exception IllegalStateException 如果此响应已经提交
     * @exception IOException 如果出现输入/输出错误
     */
    public void sendRedirect(String location) throws IOException {

        if (isCommitted())
            throw new IllegalStateException
                (sm.getString("coyoteResponse.sendRedirect.ise"));

        // Ignore any call from an included servlet
        if (included)
            return; 

        // 清除已缓冲的所有数据内容
        resetBuffer();

        // 生成临时重定向到指定位置
        try {
            String absolute = toAbsolute(location);
            setStatus(SC_FOUND);
            setHeader("Location", absolute);
        } catch (IllegalArgumentException e) {
            setStatus(SC_NOT_FOUND);
        }

        // 使响应完成(从应用程序的角度)
        setSuspended(true);
    }


    /**
     * Set the specified date header to the specified value.
     *
     * @param name Name of the header to set
     * @param value Date value to be set
     */
    public void setDateHeader(String name, long value) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included) {
            return;
        }

        if (format == null) {
            format = new SimpleDateFormat(DateTool.HTTP_RESPONSE_DATE_HEADER,
                                          Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
        }

        setHeader(name, FastHttpDateFormat.formatDate(value, format));
    }


    /**
     * 将指定的标头设置为指定的值.
     *
     * @param name 要设置的标头的名称
     * @param value 要设置的值
     */
    public void setHeader(String name, String value) {
        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        coyoteResponse.setHeader(name, value);
    }


    /**
     * 将指定的整数标头设置为指定的值.
     *
     * @param name 要设置的标头的名称
     * @param value 要设置的整数值
     */
    public void setIntHeader(String name, int value) {
        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        setHeader(name, "" + value);
    }


    /**
     * 设置要用此响应返回的HTTP状态.
     *
     * @param status 新HTTP状态
     */
    public void setStatus(int status) {
        setStatus(status, null);
    }


    /**
     * 设置要返回的HTTP状态和消息.
     *
     * @param status 新HTTP状态
     * @param message 关联的文本消息
     *
     * @deprecated As of Version 2.1 of the Java Servlet API, this method
     *  has been deprecated due to the ambiguous meaning of the message
     *  parameter.
     */
    public void setStatus(int status, String message) {

        if (isCommitted())
            return;

        // Ignore any call from an included servlet
        if (included)
            return;

        coyoteResponse.setStatus(status);
        coyoteResponse.setMessage(message);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 返回<code>true</code> 如果指定的URL应该用会话标识符编码. 如果满足以下所有条件，这将是true:
     * <ul>
     * <li>正在响应的请求需要一个有效的会话
     * <li>通过cookie没有接收到所请求的会话ID
     * <li>指定的URL指向Web应用程序中响应此请求的某个地方
     * </ul>
     *
     * @param location 要验证的绝对URL
     */
    protected boolean isEncodeable(final String location) {

        if (location == null)
            return (false);

        // 这是文档内引用吗?
        if (location.startsWith("#"))
            return (false);

        // 是否在一个有效的会话中不使用cookie?
        final Request hreq = request;
        final Session session = hreq.getSessionInternal(false);
        if (session == null)
            return (false);
        if (hreq.isRequestedSessionIdFromCookie())
            return (false);
        
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return ((Boolean)
                AccessController.doPrivileged(new PrivilegedAction() {

                public Object run(){
                    return new Boolean(doIsEncodeable(hreq, session, location));
                }
            })).booleanValue();
        } else {
            return doIsEncodeable(hreq, session, location);
        }
    }

    private boolean doIsEncodeable(Request hreq, Session session, 
                                   String location) {
        // 这是一个有效的绝对URL吗?
        URL url = null;
        try {
            url = new URL(location);
        } catch (MalformedURLException e) {
            return (false);
        }

        // 这个URL是否匹配（并包括）上下文路径?
        if (!hreq.getScheme().equalsIgnoreCase(url.getProtocol()))
            return (false);
        if (!hreq.getServerName().equalsIgnoreCase(url.getHost()))
            return (false);
        int serverPort = hreq.getServerPort();
        if (serverPort == -1) {
            if ("https".equals(hreq.getScheme()))
                serverPort = 443;
            else
                serverPort = 80;
        }
        int urlPort = url.getPort();
        if (urlPort == -1) {
            if ("https".equals(url.getProtocol()))
                urlPort = 443;
            else
                urlPort = 80;
        }
        if (serverPort != urlPort)
            return (false);

        String contextPath = getContext().getPath();
        if (contextPath != null) {
            String file = url.getFile();
            if ((file == null) || !file.startsWith(contextPath))
                return (false);
            if( file.indexOf(";jsessionid=" + session.getIdInternal()) >= 0 )
                return (false);
        }

        // 此URL属于我们的Web应用程序, 因此它是可编码的
        return (true);
    }


    /**
     * 转换（如果需要的话）并返回该URL可能引用的资源的绝对URL.  如果这个URL已经是绝对的, 返回未修改的.
     *
     * @param location 要转换的URL
     *
     * @exception IllegalArgumentException 当将相对URL转换为绝对URL时, 如果抛出 MalformedURLException
     */
    private String toAbsolute(String location) {

        if (location == null)
            return (location);

        boolean leadingSlash = location.startsWith("/");

        if (leadingSlash || !hasScheme(location)) {

            redirectURLCC.recycle();

            String scheme = request.getScheme();
            String name = request.getServerName();
            int port = request.getServerPort();

            try {
                redirectURLCC.append(scheme, 0, scheme.length());
                redirectURLCC.append("://", 0, 3);
                redirectURLCC.append(name, 0, name.length());
                if ((scheme.equals("http") && port != 80)
                    || (scheme.equals("https") && port != 443)) {
                    redirectURLCC.append(':');
                    String portS = port + "";
                    redirectURLCC.append(portS, 0, portS.length());
                }
                if (!leadingSlash) {
                    String relativePath = request.getDecodedRequestURI();
                    int pos = relativePath.lastIndexOf('/');
                    relativePath = relativePath.substring(0, pos);
                    
                    String encodedURI = null;
                    final String frelativePath = relativePath;
                    if (SecurityUtil.isPackageProtectionEnabled() ){
                        try{
                            encodedURI = (String)AccessController.doPrivileged( 
                                new PrivilegedExceptionAction(){                                
                                    public Object run() throws IOException{
                                        return urlEncoder.encodeURL(frelativePath);
                                    }
                           });   
                        } catch (PrivilegedActionException pae){
                            IllegalArgumentException iae =
                                new IllegalArgumentException(location);
                            jdkCompat.chainException(iae, pae.getException());
                            throw iae;
                        }
                    } else {
                        encodedURI = urlEncoder.encodeURL(relativePath);
                    }
                    redirectURLCC.append(encodedURI, 0, encodedURI.length());
                    redirectURLCC.append('/');
                }
                redirectURLCC.append(location, 0, location.length());
            } catch (IOException e) {
                IllegalArgumentException iae =
                    new IllegalArgumentException(location);
                jdkCompat.chainException(iae, e);
                throw iae;
            }
            return redirectURLCC.toString();
        } else {
            return (location);
        }
    }


    /**
     * 确定URI字符串是否具有<code>scheme</code>部分.
     */
    private boolean hasScheme(String uri) {
        int len = uri.length();
        for(int i=0; i < len ; i++) {
            char c = uri.charAt(i);
            if(c == ':') {
                return i > 0;
            } else if(!URL.isSchemeChar(c)) {
                return false;
            }
        }
        return false;
    }

    /**
     * 返回指定的URL 和适当编码的指定的会话标识符.
     *
     * @param url 要用会话ID编码的URL
     * @param sessionId 将包含在编码URL中的会话ID
     */
    protected String toEncoded(String url, String sessionId) {

        if ((url == null) || (sessionId == null))
            return (url);

        String path = url;
        String query = "";
        String anchor = "";
        int question = url.indexOf('?');
        if (question >= 0) {
            path = url.substring(0, question);
            query = url.substring(question);
        }
        int pound = path.indexOf('#');
        if (pound >= 0) {
            anchor = path.substring(pound);
            path = path.substring(0, pound);
        }
        StringBuffer sb = new StringBuffer(path);
        if( sb.length() > 0 ) { // jsessionid can't be first.
            sb.append(";jsessionid=");
            sb.append(sessionId);
        }
        sb.append(anchor);
        sb.append(query);
        return (sb.toString());

    }
    
/*************************自己加的，解决报错问题****************************/
	@Override
	public Collection<String> getHeaderNames() {
		return null;
	}

	@Override
	public Collection<String> getHeaders(String arg0) {
		return null;
	}
/*************************自己加的，解决报错问题****************************/
}
