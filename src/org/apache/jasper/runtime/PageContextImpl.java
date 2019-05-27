package org.apache.jasper.runtime;

import java.io.IOException;
import java.io.Writer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.el.ELContext;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspFactory;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.el.ELException;
import javax.servlet.jsp.el.ExpressionEvaluator;
import javax.servlet.jsp.el.VariableResolver;
import javax.servlet.jsp.tagext.BodyContent;

import org.apache.commons.el.ExpressionEvaluatorImpl;
import org.apache.commons.el.VariableResolverImpl;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jasper.Constants;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.security.SecurityUtil;

/**
 * 从JSP规范的PageContext 类的实现. 也可作为EL的 VariableResolver.
 */
public class PageContextImpl extends PageContext implements VariableResolver {

    private static Log log = LogFactory.getLog(PageContextImpl.class);

    // 表达式计算器, 用于计算 EL 表达式.
    private static ExpressionEvaluatorImpl elExprEval
	= new ExpressionEvaluatorImpl(false);

    // 变量解析器, 用于计算 EL 表达式.
    private VariableResolverImpl variableResolver;

    private BodyContentImpl[] outs;
    private int depth;

    // per-servlet state
    private Servlet servlet;
    private ServletConfig config;
    private ServletContext context;
    private JspFactory factory;
    private boolean needsSession;
    private String errorPageURL;
    private boolean autoFlush;
    private int	bufferSize;

    // 页面范围属性
    private transient Hashtable	attributes;

    // per-request state
    private transient ServletRequest request;
    private transient ServletResponse response;
    private transient Object page;
    private transient HttpSession session;
    private boolean isIncluded;

    // 初始输出流
    private transient JspWriter out;
    private transient JspWriterImpl baseOut;

    PageContextImpl(JspFactory factory) {
        this.factory = factory;
		this.variableResolver = new VariableResolverImpl(this);
		this.outs = new BodyContentImpl[0];
		this.attributes = new Hashtable(16);
		this.depth = -1;
    }

    public void initialize(Servlet servlet,
			   ServletRequest request, ServletResponse response,
			   String errorPageURL, boolean needsSession,
			   int bufferSize, boolean autoFlush) throws IOException {

		_initialize(servlet, request, response, errorPageURL, needsSession,
			    bufferSize, autoFlush);
    }

    private void _initialize(Servlet servlet,
			     ServletRequest request,
			     ServletResponse response,
			     String errorPageURL,
			     boolean needsSession,
			     int bufferSize,
			     boolean autoFlush) throws IOException {

		// initialize state
		this.servlet = servlet;
		this.config = servlet.getServletConfig();
		this.context = config.getServletContext();
		this.needsSession = needsSession;
		this.errorPageURL = errorPageURL;
		this.bufferSize = bufferSize;
		this.autoFlush = autoFlush;
		this.request = request;
	 	this.response = response;
	
		// 设置会话
		if (request instanceof HttpServletRequest && needsSession)
		    this.session = ((HttpServletRequest)request).getSession();
		if (needsSession && session == null)
		    throw new IllegalStateException
	                ("Page needs a session and none is available");
	
	        // 初始化初始输出 ...
	        depth = -1;
	        if (this.baseOut == null) {
	            this.baseOut = new JspWriterImpl(response, bufferSize, autoFlush);
	        } else {
	            this.baseOut.init(response, bufferSize, autoFlush);
	        }
	        this.out = baseOut;
	
		// 按照规范注册 names/values
		setAttribute(OUT, this.out);
		setAttribute(REQUEST, request);
		setAttribute(RESPONSE, response);
	
		if (session != null)
		    setAttribute(SESSION, session);
	
		setAttribute(PAGE, servlet);
		setAttribute(CONFIG, config);
		setAttribute(PAGECONTEXT, this);
		setAttribute(APPLICATION, context);
	
		isIncluded = request.getAttribute("javax.servlet.include.servlet_path") != null;
    }

    public void release() {
        out = baseOut;
		try {
		    if (isIncluded) {
			((JspWriterImpl)out).flushBuffer();
	                // 将它放入 jspWriter
		    } else {
	                // Old code:
	                //out.flush();
	                // 即使没有包括在内, 也不要刷新缓冲区(即我们是主页. servlet 将刷新它并关闭流.
	                ((JspWriterImpl)out).flushBuffer();
	            }
		} catch (IOException ex) {
		    log.warn("Internal error flushing the buffer in release()");
		}

		servlet = null;
		config = null;
		context = null;
		needsSession = false;
		errorPageURL = null;
		bufferSize = JspWriter.DEFAULT_BUFFER;
		autoFlush = true;
		request = null;
		response = null;
	        depth = -1;
		baseOut.recycle();
		session = null;
	
		attributes.clear();
    }

    public Object getAttribute(final String name) {

        if (name == null) {
            throw new NullPointerException(
                    Localizer.getMessage("jsp.error.attribute.null_name"));
        }

        if (SecurityUtil.isPackageProtectionEnabled()){
            return AccessController.doPrivileged(new PrivilegedAction(){
                public Object run(){
                    return doGetAttribute(name);
                }
            });
        } else {
            return doGetAttribute(name);
        }

    }

    private Object doGetAttribute(String name){
        return attributes.get(name);
    }

    public Object getAttribute(final String name, final int scope) {

        if (name == null) {
            throw new NullPointerException(
                    Localizer.getMessage("jsp.error.attribute.null_name"));
        }

        if (SecurityUtil.isPackageProtectionEnabled()){
            return AccessController.doPrivileged(new PrivilegedAction(){
                public Object run(){
                    return doGetAttribute(name, scope);
                }
            });
        } else {
            return doGetAttribute(name, scope);
        }
    }

    private Object doGetAttribute(String name, int scope){
        switch (scope) {
            case PAGE_SCOPE:
            	return attributes.get(name);

            case REQUEST_SCOPE:
            	return request.getAttribute(name);

            case SESSION_SCOPE:
	            if (session == null) {
	                throw new IllegalStateException(
	                        Localizer.getMessage("jsp.error.page.noSession"));
	            }
	            return session.getAttribute(name);

            case APPLICATION_SCOPE:
            	return context.getAttribute(name);

            default:
            	throw new IllegalArgumentException("Invalid scope");
        }
    }

    public void setAttribute(final String name, final Object attribute) {

        if (name == null) {
            throw new NullPointerException(
                    Localizer.getMessage("jsp.error.attribute.null_name"));
        }

        if (SecurityUtil.isPackageProtectionEnabled()){
            AccessController.doPrivileged(new PrivilegedAction(){
                public Object run(){
                    doSetAttribute(name, attribute);
                    return null;
                }
            });
        } else {
            doSetAttribute(name, attribute);
        }
    }

    private void doSetAttribute(String name, Object attribute){
        if (attribute != null) {
            attributes.put(name, attribute);
        } else {
            removeAttribute(name, PAGE_SCOPE);
        }
    }

    public void setAttribute(final String name, final Object o, final int scope) {

        if (name == null) {
            throw new NullPointerException(
                    Localizer.getMessage("jsp.error.attribute.null_name"));
        }

        if (SecurityUtil.isPackageProtectionEnabled()){
            AccessController.doPrivileged(new PrivilegedAction(){
                public Object run(){
                    doSetAttribute(name, o, scope);
                    return null;
                }
            });
        } else {
            doSetAttribute(name, o, scope);
        }
    }

    private void doSetAttribute(String name, Object o, int scope ){
        if (o != null) {
            switch (scope) {
	            case PAGE_SCOPE:
		            attributes.put(name, o);
		            break;
	
	            case REQUEST_SCOPE:
		            request.setAttribute(name, o);
		            break;
	
	            case SESSION_SCOPE:
		            if (session == null) {
		                throw new IllegalStateException(
		                        Localizer.getMessage("jsp.error.page.noSession"));
		            }
		            session.setAttribute(name, o);
		            break;
	
	            case APPLICATION_SCOPE:
		            context.setAttribute(name, o);
		            break;
	
	            default:
	            	throw new IllegalArgumentException("Invalid scope");
            }
        } else {
            removeAttribute(name, scope);
        }
    }

    public void removeAttribute(final String name, final int scope) {

        if (name == null) {
            throw new NullPointerException(
                    Localizer.getMessage("jsp.error.attribute.null_name"));
        }
        if (SecurityUtil.isPackageProtectionEnabled()){
            AccessController.doPrivileged(new PrivilegedAction(){
                public Object run(){
                    doRemoveAttribute(name, scope);
                    return null;
                }
            });
        } else {
            doRemoveAttribute(name, scope);
        }
    }

    private void doRemoveAttribute(String name, int scope){
        switch (scope) {
	        case PAGE_SCOPE:
	            attributes.remove(name);
	            break;
	
	        case REQUEST_SCOPE:
	            request.removeAttribute(name);
	            break;
	
	        case SESSION_SCOPE:
	            if (session == null) {
	            throw new IllegalStateException(
	                        Localizer.getMessage("jsp.error.page.noSession"));
	            }
	            session.removeAttribute(name);
	            break;
	
	        case APPLICATION_SCOPE:
	            context.removeAttribute(name);
	            break;
	            
	        default:
	            throw new IllegalArgumentException("Invalid scope");
        }
    }

    public int getAttributesScope(final String name) {

        if (name == null) {
            throw new NullPointerException(
                    Localizer.getMessage("jsp.error.attribute.null_name"));
        }

        if (SecurityUtil.isPackageProtectionEnabled()){
            return ((Integer)AccessController.doPrivileged(new PrivilegedAction(){
                public Object run(){
                    return new Integer(doGetAttributeScope(name));
                }
            })).intValue();
        } else {
            return doGetAttributeScope(name);
        }
    }

    private int doGetAttributeScope(String name){
        if (attributes.get(name) != null)
            return PAGE_SCOPE;

        if (request.getAttribute(name) != null)
            return REQUEST_SCOPE;

        if (session != null) {
            if (session.getAttribute(name) != null)
                return SESSION_SCOPE;
        }

        if (context.getAttribute(name) != null)
            return APPLICATION_SCOPE;

        return 0;
    }

    public Object findAttribute(final String name) {
        if (SecurityUtil.isPackageProtectionEnabled()){
            return AccessController.doPrivileged(new PrivilegedAction(){
                public Object run(){
                    if (name == null) {
                        throw new NullPointerException(
                                Localizer.getMessage("jsp.error.attribute.null_name"));
                    }

                    return doFindAttribute(name);
                }
            });
        } else {
            if (name == null) {
                throw new NullPointerException(
                        Localizer.getMessage("jsp.error.attribute.null_name"));
            }

            return doFindAttribute(name);
        }
    }

    private Object doFindAttribute(String name){

        Object o = attributes.get(name);
        if (o != null)
            return o;

        o = request.getAttribute(name);
        if (o != null)
            return o;

        if (session != null) {
            o = session.getAttribute(name);
            if (o != null)
                return o;
        }

        return context.getAttribute(name);
    }


    public Enumeration getAttributeNamesInScope(final int scope) {
        if (SecurityUtil.isPackageProtectionEnabled()){
            return (Enumeration)
                    AccessController.doPrivileged(new PrivilegedAction(){
                public Object run(){
                    return doGetAttributeNamesInScope(scope);
                }
            });
        } else {
            return doGetAttributeNamesInScope(scope);
        }
    }

    private Enumeration doGetAttributeNamesInScope(int scope){
        switch (scope) {
	        case PAGE_SCOPE:
	            return attributes.keys();
	            
	        case REQUEST_SCOPE:
	            return request.getAttributeNames();
	
	        case SESSION_SCOPE:
	            if (session == null) {
	            throw new IllegalStateException(
	                        Localizer.getMessage("jsp.error.page.noSession"));
	            }
	            return session.getAttributeNames();
	
	        case APPLICATION_SCOPE:
	            return context.getAttributeNames();
	
	        default:
	            throw new IllegalArgumentException("Invalid scope");
        }
    }

    public void removeAttribute(final String name) {

        if (name == null) {
            throw new NullPointerException(
                    Localizer.getMessage("jsp.error.attribute.null_name"));
        }

        if (SecurityUtil.isPackageProtectionEnabled()){
            AccessController.doPrivileged(new PrivilegedAction(){
                public Object run(){
                    doRemoveAttribute(name);
                    return null;
                }
            });
        } else {
            doRemoveAttribute(name);
        }
	}


    private void doRemoveAttribute(String name){
        try {
            removeAttribute(name, PAGE_SCOPE);
            removeAttribute(name, REQUEST_SCOPE);
            if( session != null ) {
                removeAttribute(name, SESSION_SCOPE);
            }
            removeAttribute(name, APPLICATION_SCOPE);
        } catch (Exception ex) {
	        // 尽可能多地删除, 忽略可能的异常
        }
    }

    public JspWriter getOut() {
    	return out;
    }

    public HttpSession getSession() { return session; }
    public Servlet getServlet() { return servlet; }
    public ServletConfig getServletConfig() { return config; }
    public ServletContext getServletContext() {
    	return config.getServletContext();
    }
    public ServletRequest getRequest() { return request; }
    public ServletResponse getResponse() { return response; }


    /**
     * 返回与此页上下文关联的异常.
     * <p/>
     * 添加包装的 Throwables 以避免 ClassCastException: see Bugzilla 31171 for details.
     *
     * @return 与此页上下文关联的异常.
     */
    public Exception getException() {
        Throwable t = JspRuntimeLibrary.getThrowable(request);

        // Only wrap if needed
        if((t != null) && (! (t instanceof Exception))) {
            t = new JspException(t);
        }

        return (Exception) t;
    }


    public Object getPage() { return servlet; }


    private final String getAbsolutePathRelativeToContext(String relativeUrlPath) {
        String path = relativeUrlPath;

        if (!path.startsWith("/")) {
	    String uri = (String)
		request.getAttribute("javax.servlet.include.servlet_path");
	    if (uri == null)
		uri = ((HttpServletRequest) request).getServletPath();
            String baseURI = uri.substring(0, uri.lastIndexOf('/'));
            path = baseURI+'/'+path;
        }

        return path;
    }

    public void include(String relativeUrlPath)
	        throws ServletException, IOException {
        JspRuntimeLibrary.include(request, response, relativeUrlPath, out,
				  true);
    }

    public void include(final String relativeUrlPath, final boolean flush)
	        throws ServletException, IOException {
        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                AccessController.doPrivileged(new PrivilegedExceptionAction(){
                    public Object run() throws Exception{
                        doInclude(relativeUrlPath, flush);
                        return null;
                    }
                });
            } catch (PrivilegedActionException e){
                Exception ex =  e.getException();
                if (ex instanceof IOException){
                    throw (IOException)ex;
                } else {
                    throw (ServletException)ex;
                }
            }
        } else {
            doInclude(relativeUrlPath, flush);
        }
    }

    private void doInclude(String relativeUrlPath, boolean flush)
	        throws ServletException, IOException {
        JspRuntimeLibrary.include(request, response, relativeUrlPath, out,
				  flush);
    }

    public VariableResolver getVariableResolver() {
    	return this;
    }

    public void forward(final String relativeUrlPath)
        throws ServletException, IOException {
        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                AccessController.doPrivileged(new PrivilegedExceptionAction(){
                    public Object run() throws Exception{
                        doForward(relativeUrlPath);
                        return null;
                    }
                });
            } catch (PrivilegedActionException e){
                Exception ex =  e.getException();
                if (ex instanceof IOException){
                    throw (IOException)ex;
                } else {
                    throw (ServletException)ex;
                }
            }
        } else {
            doForward(relativeUrlPath);
        }
    }

    private void doForward(String relativeUrlPath)
        throws ServletException, IOException{

        // JSP.4.5 如果缓冲区被刷新, 抛出 IllegalStateException
        try {
            out.clear();
        } catch (IOException ex) {
            IllegalStateException ise =
                new IllegalStateException(Localizer.getMessage("jsp.error.attempt_to_clear_flushed_buffer"));
            ise.initCause(ex);
            throw ise;
        }

        // 请确保响应对象不是包含的包装器
        while (response instanceof ServletResponseWrapperInclude) {
            response = ((ServletResponseWrapperInclude)response).getResponse();
        }

        final String path = getAbsolutePathRelativeToContext(relativeUrlPath);
        String includeUri = (String) request.getAttribute(Constants.INC_SERVLET_PATH);
            
        final ServletResponse fresponse = response;
        final ServletRequest frequest = request;
            
        if (includeUri != null)
            request.removeAttribute(Constants.INC_SERVLET_PATH);
        try {
            context.getRequestDispatcher(path).forward(request, response);
        } finally {
            if (includeUri != null)
                request.setAttribute(Constants.INC_SERVLET_PATH, includeUri);
            request.setAttribute(Constants.FORWARD_SEEN, "true");
        }
    }

    public BodyContent pushBody() {
    	return (BodyContent) pushBody(null);
    }

    public JspWriter pushBody(Writer writer) {
        depth++;
        if (depth >= outs.length) {
            BodyContentImpl[] newOuts = new BodyContentImpl[depth + 1];
            for (int i=0; i<outs.length; i++) {
                newOuts[i] = outs[i];
            }
            newOuts[depth] = new BodyContentImpl(out);
            outs = newOuts;
        }

        outs[depth].setWriter(writer);
        out = outs[depth];

		// 更新页面范围属性命名空间中的 "out"属性的值
		setAttribute(OUT, out);
        return outs[depth];
    }

    public JspWriter popBody() {
        depth--;
        if (depth >= 0) {
            out = outs[depth];
        } else {
            out = baseOut;
        }

		// 更新页面范围属性命名空间中的 "out"属性的值
		setAttribute(OUT, out);

        return out;
    }

    /**
     * 提供对ExpressionEvaluator程序的编程访问.
     * JSP Container 必须返回一个ExpressionEvaluator的有效实例, 用于解析 EL 表达式.
     */
    public ExpressionEvaluator getExpressionEvaluator() {
        return elExprEval;
    }

    public void handlePageException(Exception ex)
        throws IOException, ServletException {
        // 绝对不能被调用, 因为在生成的servlet调用handleException() 抛出Throwable.
        handlePageException((Throwable) ex);
    }

    public void handlePageException(final Throwable t)
        throws IOException, ServletException {
        if (t == null)
            throw new NullPointerException("null Throwable");

        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                AccessController.doPrivileged(new PrivilegedExceptionAction(){
                    public Object run() throws Exception{
                        doHandlePageException(t);
                        return null;
                    }
                });
            } catch (PrivilegedActionException e){
                Exception ex =  e.getException();
                if (ex instanceof IOException){
                    throw (IOException)ex;
                } else {
                    throw (ServletException)ex;
                }
            }
        } else {
            doHandlePageException(t);
        }
    }

    private void doHandlePageException(Throwable t)
        throws IOException, ServletException {

        if (errorPageURL != null && !errorPageURL.equals("")) {

            /*
             * 设置请求属性.
             * 不要在这里设置 javax.servlet.error.exception 属性
             * (相反, 在生成的servlet设置错误页面) 为了防止 ErrorReportValve, 作为请求向错误页面发送请求的一部分调用它, 从抛出它,
             * 如果尚未提交响应(如果错误页面是JSP页面，响应将被提交).
             */
            request.setAttribute("javax.servlet.jsp.jspException", t);
            request.setAttribute("javax.servlet.error.status_code",
                new Integer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
            request.setAttribute("javax.servlet.error.request_uri",
            ((HttpServletRequest) request).getRequestURI());
            request.setAttribute("javax.servlet.error.servlet_name",
                     config.getServletName());
            try {
                forward(errorPageURL);
            } catch (IllegalStateException ise) {
                include(errorPageURL);
            }

            // 错误页面可以在一个 include中.
            Object newException = request.getAttribute("javax.servlet.error.exception");

            // t==null 表示属性未设置.
            if( (newException!= null) && (newException==t) ) {
                request.removeAttribute("javax.servlet.error.exception");
            }

            // 现在清除错误代码 - 防止双重处理.
            request.removeAttribute("javax.servlet.error.status_code");
            request.removeAttribute("javax.servlet.error.request_uri");
            request.removeAttribute("javax.servlet.error.status_code");
            request.removeAttribute("javax.servlet.jsp.jspException");

        } else {
            // 否则抛出一个ServletException.
            // 在ServletException中设置根异常, 来获取真正问题的堆栈跟踪
            if (t instanceof IOException) throw (IOException)t;
            if (t instanceof ServletException) throw (ServletException)t;
            if (t instanceof RuntimeException) throw (RuntimeException)t;

            Throwable rootCause = null;
            if (t instanceof JspException) {
                rootCause = ((JspException) t).getRootCause();
            } else if (t instanceof ELException) {
                rootCause = ((ELException) t).getRootCause();
            }

            if (rootCause != null) {
                throw new ServletException(t.getClass().getName() + ": " + 
                                           t.getMessage(), rootCause);
            }
            throw new ServletException(t);
        }
    }

    /**
     * VariableResolver interface
     */
    public Object resolveVariable(String pName) throws ELException {
    	return variableResolver.resolveVariable(pName);
    }

    private static String XmlEscape(String s) {
        if (s == null) return null;
        
    	StringBuffer sb = new StringBuffer();
        for(int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '\'') {
                sb.append("&#039;");	// &apos;
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == '"') {
                sb.append("&#034;");	// &quot;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 计算EL表达式的专有方法.
     * XXX - 一旦EL解释器移出JSTL并移动到自己的项目中, 这个方法将会消失. 现在, 这是必要的，因为标准机器太慢了.
     *
     * @param expression 待计算表达式
     * @param expectedType 预期结果类型
     * @param pageContext 页面上下文
     * @param functionMap Map, 映射前缀和名字到 Method
     * @return 计算的结果
     */
    public static Object proprietaryEvaluate(final String expression, 
					     final Class expectedType,
					     final PageContext pageContext,
					     final ProtectedFunctionMapper functionMap,
					     final boolean escape) throws ELException {
    	Object retValue;
        if (SecurityUtil.isPackageProtectionEnabled()){
            try {
                retValue = AccessController.doPrivileged(new PrivilegedExceptionAction(){

                    public Object run() throws Exception{
                        return elExprEval.evaluate(expression,
						   expectedType,
						   pageContext.getVariableResolver(),
						   functionMap);
                    }
                });
            } catch (PrivilegedActionException ex) {
                Exception realEx = ex.getException();
				if (realEx instanceof ELException) {
				    throw (ELException) realEx;
				} else {
				    throw new ELException(realEx);
				}
            }
        } else {
		    retValue = elExprEval.evaluate(expression,
						   expectedType,
						   pageContext.getVariableResolver(),
						   functionMap);
        }
		if (escape) {
		    retValue = XmlEscape(retValue.toString());
		}
		return retValue;
    }

    public ELContext getELContext() {return null;};
}
