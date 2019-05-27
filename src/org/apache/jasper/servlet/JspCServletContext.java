package org.apache.jasper.servlet;


import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

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


/**
 * 简单的<code>ServletContext</code>实现不包括HTTP指定的方法.
 */
public class JspCServletContext implements ServletContext {

    // ----------------------------------------------------- Instance Variables

    /**
     * Servlet 上下文属性.
     */
    protected Hashtable myAttributes;


    /**
     * 使用的日志 writer.
     */
    protected PrintWriter myLogWriter;


    /**
     * 这个上下文的基础URL (文档根).
     */
    protected URL myResourceBaseURL;


    // ----------------------------------------------------------- Constructors


    /**
     * @param aLogWriter <code>log()</code>调用的PrintWriter
     * @param aResourceBaseURL 资源基础URL
     */
    public JspCServletContext(PrintWriter aLogWriter, URL aResourceBaseURL) {
        myAttributes = new Hashtable();
        myLogWriter = aLogWriter;
        myResourceBaseURL = aResourceBaseURL;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 返回指定的上下文属性.
     *
     * @param name 请求属性的名称
     */
    public Object getAttribute(String name) {
        return (myAttributes.get(name));
    }


    /**
     * 返回上下文属性名称的枚举.
     */
    public Enumeration getAttributeNames() {
        return (myAttributes.keys());
    }


    /**
     * 返回指定路径的 servlet上下文.
     *
     * @param uripath Server相对路径, 以'/'开头
     */
    public ServletContext getContext(String uripath) {
        return (null);
    }


    /**
     * 返回指定的上下文初始化参数.
     *
     * @param name 请求参数的名称
     */
    public String getInitParameter(String name) {
        return (null);
    }


    /**
     * 返回上下文初始化参数名称的枚举.
     */
    public Enumeration getInitParameterNames() {
        return (new Vector().elements());
    }


    /**
     * 返回 Servlet API 主版本号.
     */
    public int getMajorVersion() {
        return (2);
    }


    /**
     * 返回指定文件名的 MIME 类型.
     *
     * @param file 请求的MIME类型的文件名
     */
    public String getMimeType(String file) {
        return (null);
    }


    /**
     * 返回Servlet API 次要版本号.
     */
    public int getMinorVersion() {
        return (3);
    }


    /**
     * 返回指定的servlet名称的请求分派器.
     *
     * @param name 请求的servlet的名称
     */
    public RequestDispatcher getNamedDispatcher(String name) {
        return (null);
    }


    /**
     * 返回指定上下文相对虚拟路径的实际路径.
     *
     * @param path 要解析的上下文相关虚拟路径
     */
    public String getRealPath(String path) {

        if (!myResourceBaseURL.getProtocol().equals("file"))
            return (null);
        if (!path.startsWith("/"))
            return (null);
        try {
            return
                (getResource(path).getFile().replace('/', File.separatorChar));
        } catch (Throwable t) {
            return (null);
        }
    }
            
            
    /**
     * 返回指定上下文相对路径的请求分配器.
     *
     * @param path 获取调度程序的上下文相对路径
     */
    public RequestDispatcher getRequestDispatcher(String path) {
        return (null);
    }


    /**
     * 返回一个资源的 URL 对象, 映射到指定的上下文相对路径.
     *
     * @param path 上下文所需资源的相对路径
     *
     * @exception MalformedURLException 如果资源路径没有正确形成
     */
    public URL getResource(String path) throws MalformedURLException {

        if (!path.startsWith("/"))
            throw new MalformedURLException("Path '" + path +
                                            "' does not start with '/'");
        URL url = new URL(myResourceBaseURL, path.substring(1));
        InputStream is = null;
        try {
            is = url.openStream();
        } catch (Throwable t) {
            url = null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Throwable t2) {
                    // Ignore
                }
            }
        }
        return url;
    }


    /**
     * 返回一个 InputStream 允许在指定上下文相对路径中访问资源.
     *
     * @param path 上下文所需资源的相对路径
     */
    public InputStream getResourceAsStream(String path) {
        try {
            return (getResource(path).openStream());
        } catch (Throwable t) {
            return (null);
        }
    }


    /**
     * 返回指定上下文路径的"directory"的资源路径集.
     *
     * @param path 上下文相关基础路径
     */
    public Set getResourcePaths(String path) {

        Set thePaths = new HashSet();
        if (!path.endsWith("/"))
            path += "/";
        String basePath = getRealPath(path);
        if (basePath == null)
            return (thePaths);
        File theBaseDir = new File(basePath);
        if (!theBaseDir.exists() || !theBaseDir.isDirectory())
            return (thePaths);
        String theFiles[] = theBaseDir.list();
        for (int i = 0; i < theFiles.length; i++) {
            File testFile = new File(basePath + File.separator + theFiles[i]);
            if (testFile.isFile())
                thePaths.add(path + theFiles[i]);
            else if (testFile.isDirectory())
                thePaths.add(path + theFiles[i] + "/");
        }
        return (thePaths);
    }


    /**
     * 返回此服务器的描述性信息.
     */
    public String getServerInfo() {
        return ("JspCServletContext/1.0");
    }


    /**
     * 返回一个指定servlet名称的 null 引用.
     *
     * @param name 请求servlet的名称
     *
     * @deprecated This method has been deprecated with no replacement
     */
    public Servlet getServlet(String name) throws ServletException {
        return (null);
    }


    /**
     * 返回此servlet上下文的名称.
     */
    public String getServletContextName() {
        return (getServerInfo());
    }


    /**
     * 返回servlet名称的空枚举.
     *
     * @deprecated This method has been deprecated with no replacement
     */
    public Enumeration getServletNames() {
        return (new Vector().elements());
    }


    /**
     * 返回servlet空枚举.
     *
     * @deprecated This method has been deprecated with no replacement
     */
    public Enumeration getServlets() {
        return (new Vector().elements());
    }


    /**
     * 记录指定的消息.
     *
     * @param message 要记录的消息
     */
    public void log(String message) {
        myLogWriter.println(message);
    }


    /**
     * 记录指定的消息和异常.
     *
     * @param exception 要记录的异常
     * @param message 要记录的消息
     *
     * @deprecated Use log(String,Throwable) instead
     */
    public void log(Exception exception, String message) {
        log(message, exception);
    }


    /**
     * 记录指定的消息和异常.
     *
     * @param message 要记录的消息
     * @param exception 要记录的异常
     */
    public void log(String message, Throwable exception) {
        myLogWriter.println(message);
        exception.printStackTrace(myLogWriter);
    }


    /**
     * 删除指定的上下文属性.
     *
     * @param name 要删除的属性的名称
     */
    public void removeAttribute(String name) {
        myAttributes.remove(name);
    }


    /**
     * 设置或替换指定的上下文属性.
     *
     * @param name 要设置的上下文属性的名称
     * @param value 相应的属性值
     */
    public void setAttribute(String name, Object value) {
        myAttributes.put(name, value);
    }

/***************自己加的*************/
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
/***************自己加的*************/
}
