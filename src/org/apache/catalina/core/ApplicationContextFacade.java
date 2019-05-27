package org.apache.catalina.core;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;

import org.apache.catalina.security.SecurityUtil;

/**
 * 外观模式，包装内部的<code>ApplicationContext</code>对象
 */
public final class ApplicationContextFacade implements ServletContext {
        
    // ---------------------------------------------------------- Attributes
    /**
     * 用于反射的缓存类对象.
     */
    private HashMap classCache;
    
    
    /**
     * 缓存的方法对象.
     */
    private HashMap objectCache;
    
    
    private static org.apache.commons.logging.Log sysLog=
        org.apache.commons.logging.LogFactory.getLog( ApplicationContextFacade.class );

        
    // ----------------------------------------------------------- Constructors


    /**
     * @param context 关联的Context实例
     */
    public ApplicationContextFacade(ApplicationContext context) {
        super();
        this.context = context;
        
        classCache = new HashMap();
        objectCache = new HashMap();
        initClassCache();
    }
    
    
    private void initClassCache(){
        Class[] clazz = new Class[]{String.class};
        classCache.put("getContext", clazz);
        classCache.put("getMimeType", clazz);
        classCache.put("getResourcePaths", clazz);
        classCache.put("getResource", clazz);
        classCache.put("getResourceAsStream", clazz);
        classCache.put("getRequestDispatcher", clazz);
        classCache.put("getNamedDispatcher", clazz);
        classCache.put("getServlet", clazz);
        classCache.put("getInitParameter", clazz);
        classCache.put("setAttribute", new Class[]{String.class, Object.class});
        classCache.put("removeAttribute", clazz);
        classCache.put("getRealPath", clazz);
        classCache.put("getAttribute", clazz);
        classCache.put("log", clazz);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Wrapped application context.
     */
    private ApplicationContext context = null;
    


    // ------------------------------------------------- ServletContext Methods


    public ServletContext getContext(String uripath) {
        ServletContext theContext = null;
        if (SecurityUtil.isPackageProtectionEnabled()) {
            theContext = (ServletContext)
                doPrivileged("getContext", new Object[]{uripath});
        } else {
            theContext = context.getContext(uripath);
        }
        if ((theContext != null) &&
            (theContext instanceof ApplicationContext)){
            theContext = ((ApplicationContext)theContext).getFacade();
        }
        return (theContext);
    }


    public int getMajorVersion() {
        return context.getMajorVersion();
    }


    public int getMinorVersion() {
        return context.getMinorVersion();
    }


    public String getMimeType(String file) {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return (String)doPrivileged("getMimeType", new Object[]{file});
        } else {
            return context.getMimeType(file);
        }
    }


    public Set getResourcePaths(String path) {
        if (SecurityUtil.isPackageProtectionEnabled()){
            return (Set)doPrivileged("getResourcePaths", new Object[]{path});
        } else {
            return context.getResourcePaths(path);
        }
    }


    public URL getResource(String path)
        throws MalformedURLException {
        if (System.getSecurityManager() != null) {
            try {
                return (URL) invokeMethod(context, "getResource", 
                                          new Object[]{path});
            } catch(Throwable t) {
                if (t instanceof MalformedURLException){
                    throw (MalformedURLException)t;
                }
                return null;
            }
        } else {
            return context.getResource(path);
        }
    }


    public InputStream getResourceAsStream(String path) {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return (InputStream) doPrivileged("getResourceAsStream", 
                                              new Object[]{path});
        } else {
            return context.getResourceAsStream(path);
        }
    }


    public RequestDispatcher getRequestDispatcher(final String path) {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return (RequestDispatcher) doPrivileged("getRequestDispatcher", 
                                                    new Object[]{path});
        } else {
            return context.getRequestDispatcher(path);
        }
    }


    public RequestDispatcher getNamedDispatcher(String name) {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return (RequestDispatcher) doPrivileged("getNamedDispatcher", 
                                                    new Object[]{name});
        } else {
            return context.getNamedDispatcher(name);
        }
    }


    public Servlet getServlet(String name)
        throws ServletException {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            try {
                return (Servlet) invokeMethod(context, "getServlet", 
                                              new Object[]{name});
            } catch (Throwable t) {
                if (t instanceof ServletException) {
                    throw (ServletException) t;
                }
                return null;
            }
        } else {
            return context.getServlet(name);
        }
    }


    public Enumeration getServlets() {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return (Enumeration) doPrivileged("getServlets", null);
        } else {
            return context.getServlets();
        }
    }


    public Enumeration getServletNames() {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return (Enumeration) doPrivileged("getServletNames", null);
        } else {
            return context.getServletNames();
        }
   }


    public void log(String msg) {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            doPrivileged("log", new Object[]{msg} );
        } else {
            context.log(msg);
        }
    }


    public void log(Exception exception, String msg) {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            doPrivileged("log", new Class[]{Exception.class, String.class}, 
                         new Object[]{exception,msg});
        } else {
            context.log(exception, msg);
        }
    }


    public void log(String message, Throwable throwable) {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            doPrivileged("log", new Class[]{String.class, Throwable.class}, 
                         new Object[]{message, throwable});
        } else {
            context.log(message, throwable);
        }
    }


    public String getRealPath(String path) {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return (String) doPrivileged("getRealPath", new Object[]{path});
        } else {
            return context.getRealPath(path);
        }
    }


    public String getServerInfo() {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return (String) doPrivileged("getServerInfo", null);
        } else {
            return context.getServerInfo();
        }
    }


    public String getInitParameter(String name) {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return (String) doPrivileged("getInitParameter", 
                                         new Object[]{name});
        } else {
            return context.getInitParameter(name);
        }
    }


    public Enumeration getInitParameterNames() {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return (Enumeration) doPrivileged("getInitParameterNames", null);
        } else {
            return context.getInitParameterNames();
        }
    }


    public Object getAttribute(String name) {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return doPrivileged("getAttribute", new Object[]{name});
        } else {
            return context.getAttribute(name);
        }
     }


    public Enumeration getAttributeNames() {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return (Enumeration) doPrivileged("getAttributeNames", null);
        } else {
            return context.getAttributeNames();
        }
    }


    public void setAttribute(String name, Object object) {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            doPrivileged("setAttribute", new Object[]{name,object});
        } else {
            context.setAttribute(name, object);
        }
    }


    public void removeAttribute(String name) {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            doPrivileged("removeAttribute", new Object[]{name});
        } else {
            context.removeAttribute(name);
        }
    }


    public String getServletContextName() {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            return (String) doPrivileged("getServletContextName", null);
        } else {
            return context.getServletContextName();
        }
    }

       
    /**
     * 使用反射调用所请求的方法. 缓存方法对象以加快处理
     * 
     * @param appContext 方法将被调用的AppliationContext对象
     * @param methodName 要调用的方法名称.
     * @param params 传递给调用方法的参数.
     */
    private Object doPrivileged(ApplicationContext appContext,
                                final String methodName, 
                                final Object[] params) {
        try{
            return invokeMethod(appContext, methodName, params );
        } catch (Throwable t){
            throw new RuntimeException(t.getMessage());
        }

    }


    /**
     * 使用反射调用所请求的方法. 缓存方法对象以加快处理
     * @param methodName 要调用的方法名称.
     * @param params 传递给调用方法的参数
     */
    private Object doPrivileged(final String methodName, final Object[] params){
        try{
            return invokeMethod(context, methodName, params);
        }catch(Throwable t){
            throw new RuntimeException(t.getMessage());
        }
    }

    
    /**
     * 使用反射调用所请求的方法. 缓存方法对象以加快处理
     * @param appContext 方法将被调用的AppliationContext对象
     * @param methodName 要调用的方法名称.
     * @param params 传递给调用方法的参数
     */
    private Object invokeMethod(ApplicationContext appContext,
                                final String methodName, 
                                Object[] params) 
        throws Throwable{

        try{
            Method method = (Method)objectCache.get(methodName);
            if (method == null){
                method = appContext.getClass()
                    .getMethod(methodName, (Class[])classCache.get(methodName));
                objectCache.put(methodName, method);
            }
            
            return executeMethod(method,appContext,params);
        } catch (Exception ex){
            handleException(ex, methodName);
            return null;
        } finally {
            params = null;
        }
    }
    
    /**
     * 使用反射调用所请求的方法. 缓存方法对象以加快处理
     * @param methodName 要调用的方法名称.
     * @param clazz 方法所在的类.
     * @param params 传递给调用方法的参数.
     */    
    private Object doPrivileged(final String methodName, 
                                final Class[] clazz,
                                Object[] params){

        try{
            Method method = context.getClass()
                    .getMethod(methodName, (Class[])clazz);
            return executeMethod(method,context,params);
        } catch (Exception ex){
            try{
                handleException(ex, methodName);
            }catch (Throwable t){
                throw new RuntimeException(t.getMessage());
            }
            return null;
        } finally {
            params = null;
        }
    }
    
    
    /**
     * 执行指定的<code>ApplicationContext</code>的方法
     * 
     * @param method 要调用的方法对象.
     * @param context 方法将被调用的AppliationContext对象
     * @param params 传递给调用方法的参数.
     */
    private Object executeMethod(final Method method, 
                                 final ApplicationContext context,
                                 final Object[] params) 
            throws PrivilegedActionException, 
                   IllegalAccessException,
                   InvocationTargetException {
                                     
        if (SecurityUtil.isPackageProtectionEnabled()){
           return AccessController.doPrivileged(new PrivilegedExceptionAction(){
                public Object run() throws IllegalAccessException, InvocationTargetException{
                    return method.invoke(context,  params);
                }
            });
        } else {
            return method.invoke(context, params);
        }        
    }

    
    /**
     * @param ex The current exception
     */
    private void handleException(Exception ex, String methodName)
	    throws Throwable {

        Throwable realException;

        if (sysLog.isDebugEnabled()) {   
            sysLog.debug("ApplicationContextFacade." + methodName, ex);
        }

	if (ex instanceof PrivilegedActionException) {
            ex = ((PrivilegedActionException) ex).getException();
	}

        if (ex instanceof InvocationTargetException) {
            realException =
		((InvocationTargetException) ex).getTargetException();
        } else {
            realException = ex;
        }   

        throw realException;
    }

/*************************自己加的，解决报错问题****************************/
	@Override
	public Dynamic addFilter(String arg0, String arg1) {
		return null;
	}


	@Override
	public Dynamic addFilter(String arg0, Filter arg1) {
		return null;
	}


	@Override
	public Dynamic addFilter(String arg0, Class<? extends Filter> arg1) {
		return null;
	}


	@Override
	public void addListener(String arg0) {
	}


	@Override
	public <T extends EventListener> void addListener(T arg0) {
	}


	@Override
	public void addListener(Class<? extends EventListener> arg0) {
	}


	@Override
	public javax.servlet.ServletRegistration.Dynamic addServlet(String arg0, String arg1) {
		return null;
	}


	@Override
	public javax.servlet.ServletRegistration.Dynamic addServlet(String arg0, Servlet arg1) {
		return null;
	}


	@Override
	public javax.servlet.ServletRegistration.Dynamic addServlet(String arg0, Class<? extends Servlet> arg1) {
		return null;
	}


	@Override
	public <T extends Filter> T createFilter(Class<T> arg0) throws ServletException {
		return null;
	}


	@Override
	public <T extends EventListener> T createListener(Class<T> arg0) throws ServletException {
		return null;
	}


	@Override
	public <T extends Servlet> T createServlet(Class<T> arg0) throws ServletException {
		return null;
	}


	@Override
	public void declareRoles(String... arg0) {
	}


	@Override
	public ClassLoader getClassLoader() {
		return null;
	}


	@Override
	public String getContextPath() {
		return null;
	}


	@Override
	public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
		return null;
	}


	@Override
	public int getEffectiveMajorVersion() {
		return 0;
	}


	@Override
	public int getEffectiveMinorVersion() {
		return 0;
	}


	@Override
	public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
		return null;
	}


	@Override
	public FilterRegistration getFilterRegistration(String arg0) {
		return null;
	}


	@Override
	public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
		return null;
	}


	@Override
	public JspConfigDescriptor getJspConfigDescriptor() {
		return null;
	}


	@Override
	public ServletRegistration getServletRegistration(String arg0) {
		return null;
	}


	@Override
	public Map<String, ? extends ServletRegistration> getServletRegistrations() {
		return null;
	}


	@Override
	public SessionCookieConfig getSessionCookieConfig() {
		return null;
	}


	@Override
	public boolean setInitParameter(String arg0, String arg1) {
		return false;
	}


	@Override
	public void setSessionTrackingModes(Set<SessionTrackingMode> arg0)
			throws IllegalStateException, IllegalArgumentException {
	}
/*************************自己加的，解决报错问题****************************/
}
