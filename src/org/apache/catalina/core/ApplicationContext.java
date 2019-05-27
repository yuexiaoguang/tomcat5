package org.apache.catalina.core;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.naming.Binding;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.FilterRegistration.Dynamic;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Wrapper;
import org.apache.catalina.deploy.ApplicationParameter;
import org.apache.catalina.util.Enumerator;
import org.apache.catalina.util.ResourceSet;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.StringManager;
import org.apache.naming.resources.DirContextURLStreamHandler;
import org.apache.naming.resources.Resource;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.mapper.MappingData;

/**
 * <code>ServletContext</code>的标准实现类，表示Web应用程序的执行环境. 
 * 这个类的实例被关联到每个<code>StandardContext</code>实例
 */
public class ApplicationContext implements ServletContext {

    // ----------------------------------------------------------- Constructors


    /**
     * @param context 关联的Context实例
     */
    public ApplicationContext(String basePath, StandardContext context) {
        super();
        this.context = context;
        this.basePath = basePath;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 上下文属性
     */
    private HashMap attributes = new HashMap();


    /**
     * 此上下文的只读属性列表
     */
    private HashMap readOnlyAttributes = new HashMap();


    /**
     * 关联的Context
     */
    private StandardContext context = null;


    /**
     * 空集合，空枚举的基础.
     * <strong>不要添加任何元素到这个集合中</strong>
     */
    private static final ArrayList empty = new ArrayList();


    /**
     * 封装的外观模式
     */
    private ServletContext facade = new ApplicationContextFacade(this);


    /**
     * 合并的上下文初始化参数
     */
    private HashMap parameters = null;


    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 基础路径
     */
    private String basePath = null;


    /**
     * 线程本地映射数据.
     */
    private ThreadLocal localMappingData = new ThreadLocal();


    /**
     * 线程本地URI消息字节
     */
    private ThreadLocal localUriMB = new ThreadLocal();


    // --------------------------------------------------------- Public Methods


    /**
     * 返回映射到指定路径的资源对象.
     * 路径必须以 "/" 开头，并被解释为相对于当前上下文的根路径.
     */
    public DirContext getResources() {
        return context.getResources();
    }


    // ------------------------------------------------- ServletContext Methods


    /**
     * 返回指定上下文属性的值; 或者<code>null</code>.
     *
     * @param name 要返回的上下文属性的名称
     */
    public Object getAttribute(String name) {
        synchronized (attributes) {
            return (attributes.get(name));
        }
    }


    /**
     * 返回上下文属性名称的枚举
     */
    public Enumeration getAttributeNames() {
        synchronized (attributes) {
            return new Enumerator(attributes.keySet(), true);
        }
    }


    /**
     * 返回一个<code>ServletContext</code>对象， 对应于服务器上的指定URI. 
     * 这个方法允许servlet来获取服务器的各个部分的上下文访问, 并根据需要从需要获得<code>RequestDispatcher</code>对象或资源. 
     * 给定的路径必须是绝对路径("/"开头), 并基于虚拟主机的根路径进行解释
     *
     * @param uri 服务器上资源的绝对URI
     */
    public ServletContext getContext(String uri) {

        // 验证指定参数的格式
        if ((uri == null) || (!uri.startsWith("/")))
            return (null);

        // 如果请求返回当前上下文
        String contextPath = context.getPath();
        if (!contextPath.endsWith("/"))
            contextPath = contextPath + "/";

        if (((contextPath.length() > 1) && (uri.startsWith(contextPath))) ||
            ((contextPath.equals("/")) && (uri.equals("/")))) {
            return (this);
        }

        // 只有在允许的情况下返回其他上下文
        if (!context.getCrossContext())
            return (null);
        try {
            Host host = (Host) context.getParent();
            Context child = null;
            String mapuri = uri;
            while (true) {
                child = (Context) host.findChild(mapuri);
                if (child != null)
                    break;
                int slash = mapuri.lastIndexOf('/');
                if (slash < 0)
                    break;
                mapuri = mapuri.substring(0, slash);
            }
            if (child == null) {
                child = (Context) host.findChild("");
            }
            if (child != null)
                return (child.getServletContext());
            else
                return (null);
        } catch (Throwable t) {
            return (null);
        }
    }


    /**
     * 返回指定初始化参数的值,或<code>null</code>
     *
     * @param name 要检索的初始化参数的名称
     */
    public String getInitParameter(final String name) {
        mergeParameters();
        synchronized (parameters) {
            return ((String) parameters.get(name));
        }
    }


    /**
     * 返回上下文初始化参数的名称, 或空枚举
     */
    public Enumeration getInitParameterNames() {
        mergeParameters();
        synchronized (parameters) {
           return (new Enumerator(parameters.keySet()));
        }
    }


    /**
     * 返回当前使用的Java Servlet API主要版本
     */
    public int getMajorVersion() {
        return (Constants.MAJOR_VERSION);
    }


    /**
     * 返回当前使用的Java Servlet API次要版本
     */
    public int getMinorVersion() {
        return (Constants.MINOR_VERSION);
    }


    /**
     * 返回指定文件的 MIME类型, 或者<code>null</code>
     *
     * @param file 用于识别MIME类型的文件名
     */
    public String getMimeType(String file) {
        if (file == null)
            return (null);
        int period = file.lastIndexOf(".");
        if (period < 0)
            return (null);
        String extension = file.substring(period + 1);
        if (extension.length() < 1)
            return (null);
        return (context.findMimeMapping(extension));
    }


    /**
     * 返回一个作为命名servlet的封装的<code>RequestDispatcher</code>对象.
     *
     * @param name 请求调度器的servlet的名称
     */
    public RequestDispatcher getNamedDispatcher(String name) {
        // 验证名称参数
        if (name == null)
            return (null);

        // 创建并返回相应的请求调度器
        Wrapper wrapper = (Wrapper) context.findChild(name);
        if (wrapper == null)
            return (null);
        
        return new ApplicationDispatcher(wrapper, null, null, null, null, name);
    }


    /**
     * 返回给定虚拟路径的实际路径; 或者<code>null</code>.
     *
     * @param path 所需资源的路径
     */
    public String getRealPath(String path) {
        if (!context.isFilesystemBased())
            return null;

        if (path == null) {
            return null;
        }

        File file = new File(basePath, path);
        return (file.getAbsolutePath());
    }


    /**
     * 返回一个<code>RequestDispatcher</code>实例， 作为给定路径上资源的包装器. 
     * 路径必须以"/"开头， 被解释为相对于当前上下文的根路径.
     *
     * @param path 所需资源的路径.
     */
    public RequestDispatcher getRequestDispatcher(String path) {

        // Validate the path argument
        if (path == null)
            return (null);
        if (!path.startsWith("/"))
            throw new IllegalArgumentException
                (sm.getString
                 ("applicationContext.requestDispatcher.iae", path));
        path = normalize(path);
        if (path == null)
            return (null);

        // Retrieve the thread local URI
        MessageBytes uriMB = (MessageBytes) localUriMB.get();
        if (uriMB == null) {
            uriMB = MessageBytes.newInstance();
            CharChunk uriCC = uriMB.getCharChunk();
            uriCC.setLimit(-1);
            localUriMB.set(uriMB);
        } else {
            uriMB.recycle();
        }

        // Get query string
        String queryString = null;
        int pos = path.indexOf('?');
        if (pos >= 0) {
            queryString = path.substring(pos + 1);
        } else {
            pos = path.length();
        }
 
        // Retrieve the thread local mapping data
        MappingData mappingData = (MappingData) localMappingData.get();
        if (mappingData == null) {
            mappingData = new MappingData();
            localMappingData.set(mappingData);
        }

        // Map the URI
        CharChunk uriCC = uriMB.getCharChunk();
        try {
            uriCC.append(context.getPath(), 0, context.getPath().length());
            /*
             * 忽略任何尾随的路径参数(使用 ';'分隔)
             */
            int semicolon = path.indexOf(';');
            if (pos >= 0 && semicolon > pos) {
                semicolon = -1;
            }
            uriCC.append(path, 0, semicolon > 0 ? semicolon : pos);
            context.getMapper().map(uriMB, mappingData);
            if (mappingData.wrapper == null) {
                return (null);
            }
            /*
             * 追加任何尾随的路径参数 (使用 ';'分隔) 为了映射的目的忽略了这一点, 因此，他们反映在RequestDispatcher的requestURI
             */
            if (semicolon > 0) {
                uriCC.append(path, semicolon, pos - semicolon);
            }
        } catch (Exception e) {
            // Should never happen
            log(sm.getString("applicationContext.mapping.error"), e);
            return (null);
        }

        Wrapper wrapper = (Wrapper) mappingData.wrapper;
        String wrapperPath = mappingData.wrapperPath.toString();
        String pathInfo = mappingData.pathInfo.toString();

        mappingData.recycle();
        
        // 创建一个RequestDispatcher处理这个请求
        return new ApplicationDispatcher(wrapper, uriCC.toString(), wrapperPath, pathInfo, 
             queryString, null);

    }



    /**
     * 将URL返回到映射到指定路径的资源.
     * 路径必须以"/"开头， 被解释为相对于当前上下文的根路径.
     *
     * @param path 所需资源的路径
     *
     * @exception MalformedURLException 如果路径没有以正确的形式给出
     */
    public URL getResource(String path)
        throws MalformedURLException {

        if (path == null || !path.startsWith("/")) {
            throw new MalformedURLException(sm.getString("applicationContext.requestDispatcher.iae", path));
        }
        
        path = normalize(path);
        if (path == null)
            return (null);

        String libPath = "/WEB-INF/lib/";
        if ((path.startsWith(libPath)) && (path.endsWith(".jar"))) {
            File jarFile = null;
            if (context.isFilesystemBased()) {
                jarFile = new File(basePath, path);
            } else {
                jarFile = new File(context.getWorkPath(), path);
            }
            if (jarFile.exists()) {
                return jarFile.toURL();
            } else {
                return null;
            }
        } else {

            DirContext resources = context.getResources();
            if (resources != null) {
                String fullPath = context.getName() + path;
                String hostName = context.getParent().getName();
                try {
                    resources.lookup(path);
                    return new URL
                        ("jndi", "", 0, getJNDIUri(hostName, fullPath),
                         new DirContextURLStreamHandler(resources));
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
        return (null);
    }


    /**
     * 返回请求资源作为一个<code>InputStream</code>. 
     * 必须根据下面<code>getResource</code>描述的规则指定路径 . 如果没有这样的资源可以识别,返回<code>null</code>.
     *
     * @param path 所需资源的路径
     */
    public InputStream getResourceAsStream(String path) {
        path = normalize(path);
        if (path == null)
            return (null);

        DirContext resources = context.getResources();
        if (resources != null) {
            try {
                Object resource = resources.lookup(path);
                if (resource instanceof Resource)
                    return (((Resource) resource).streamContent());
            } catch (Exception e) {
            }
        }
        return (null);
    }


    /**
     * 返回包含指定集合资源成员的资源路径的集合. 
     * 路径必须以"/"开头. 返回的集合是不可变的.
     *
     * @param path Collection path
     */
    public Set getResourcePaths(String path) {

        // Validate the path argument
        if (path == null) {
            return null;
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException
                (sm.getString("applicationContext.resourcePaths.iae", path));
        }

        path = normalize(path);
        if (path == null)
            return (null);

        DirContext resources = context.getResources();
        if (resources != null) {
            return (getResourcePathsInternal(resources, path));
        }
        return (null);
    }


    /**
     * getResourcesPath()内部实现.
     *
     * @param resources 要搜索的目录上下文
     * @param path Collection path
     */
    private Set getResourcePathsInternal(DirContext resources, String path) {

        ResourceSet set = new ResourceSet();
        try {
            listCollectionPaths(set, resources, path);
        } catch (NamingException e) {
            return (null);
        }
        set.setLocked(true);
        return (set);
    }


    /**
     * 返回servlet容器的名称和版本
     */
    public String getServerInfo() {
        return (ServerInfo.getServerInfo());
    }


    /**
     * @deprecated As of Java Servlet API 2.1, with no direct replacement.
     */
    public Servlet getServlet(String name) {
        return (null);
    }


    /**
     * 返回此Web应用程序的显示名称
     */
    public String getServletContextName() {
        return (context.getDisplayName());
    }


    /**
     * @deprecated As of Java Servlet API 2.1, with no direct replacement.
     */
    public Enumeration getServletNames() {
        return (new Enumerator(empty));
    }


    /**
     * @deprecated As of Java Servlet API 2.1, with no direct replacement.
     */
    public Enumeration getServlets() {
        return (new Enumerator(empty));
    }


    /**
     * 将指定的消息写入servlet日志文件
     *
     * @param message Message to be written
     */
    public void log(String message) {
        context.getLogger().info(message);
    }


    /**
     * 将指定的消息写入servlet日志文件
     *
     * @param exception Exception to be reported
     * @param message Message to be written
     *
     * @deprecated As of Java Servlet API 2.1, use
     *  <code>log(String, Throwable)</code> instead
     */
    public void log(Exception exception, String message) {
        context.getLogger().error(message, exception);
    }


    /**
     * 将指定的消息写入servlet日志文件
     *
     * @param message Message to be written
     * @param throwable Exception to be reported
     */
    public void log(String message, Throwable throwable) {
        context.getLogger().error(message, throwable);
    }


    /**
     * 移除指定的上下文属性
     *
     * @param name Name of the context attribute to be removed
     */
    public void removeAttribute(String name) {

        Object value = null;
        boolean found = false;

        // Remove the specified attribute
        synchronized (attributes) {
            // Check for read only attribute
           if (readOnlyAttributes.containsKey(name))
                return;
            found = attributes.containsKey(name);
            if (found) {
                value = attributes.get(name);
                attributes.remove(name);
            } else {
                return;
            }
        }

        // 通知感兴趣的应用事件监听器
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0))
            return;
        ServletContextAttributeEvent event =
          new ServletContextAttributeEvent(context.getServletContext(),
                                            name, value);
        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof ServletContextAttributeListener))
                continue;
            ServletContextAttributeListener listener =
                (ServletContextAttributeListener) listeners[i];
            try {
                context.fireContainerEvent("beforeContextAttributeRemoved",
                                           listener);
                listener.attributeRemoved(event);
                context.fireContainerEvent("afterContextAttributeRemoved",
                                           listener);
            } catch (Throwable t) {
                context.fireContainerEvent("afterContextAttributeRemoved",
                                           listener);
                // FIXME - should we do anything besides log these?
                log(sm.getString("applicationContext.attributeEvent"), t);
            }
        }
    }


    /**
     * 设置上下文属性值
     *
     * @param name Attribute name to be bound
     * @param value New attribute value to be bound
     */
    public void setAttribute(String name, Object value) {

        // Name cannot be null
        if (name == null)
            throw new IllegalArgumentException
                (sm.getString("applicationContext.setAttribute.namenull"));

        // Null 值等同于 removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        Object oldValue = null;
        boolean replaced = false;

        // 添加或替换指定的属性
        synchronized (attributes) {
            // 检查只读属性
            if (readOnlyAttributes.containsKey(name))
                return;
            oldValue = attributes.get(name);
            if (oldValue != null)
                replaced = true;
            attributes.put(name, value);
        }

        // 通知感兴趣的应用程序事件监听器
        Object listeners[] = context.getApplicationEventListeners();
        if ((listeners == null) || (listeners.length == 0))
            return;
        ServletContextAttributeEvent event = null;
        if (replaced)
            event =
                new ServletContextAttributeEvent(context.getServletContext(),
                                                 name, oldValue);
        else
            event =
                new ServletContextAttributeEvent(context.getServletContext(),
                                                 name, value);

        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof ServletContextAttributeListener))
                continue;
            ServletContextAttributeListener listener =
                (ServletContextAttributeListener) listeners[i];
            try {
                if (replaced) {
                    context.fireContainerEvent
                        ("beforeContextAttributeReplaced", listener);
                    listener.attributeReplaced(event);
                    context.fireContainerEvent("afterContextAttributeReplaced",
                                               listener);
                } else {
                    context.fireContainerEvent("beforeContextAttributeAdded",
                                               listener);
                    listener.attributeAdded(event);
                    context.fireContainerEvent("afterContextAttributeAdded",
                                               listener);
                }
            } catch (Throwable t) {
                if (replaced)
                    context.fireContainerEvent("afterContextAttributeReplaced",
                                               listener);
                else
                    context.fireContainerEvent("afterContextAttributeAdded",
                                               listener);
                // FIXME - should we do anything besides log these?
                log(sm.getString("applicationContext.attributeEvent"), t);
            }
        }
    }


    // -------------------------------------------------------- Package Methods


    /**
     * 清除所有应用程序创建的属性.
     */
    void clearAttributes() {

        // 创建要删除的属性列表
        ArrayList list = new ArrayList();
        synchronized (attributes) {
            Iterator iter = attributes.keySet().iterator();
            while (iter.hasNext()) {
                list.add(iter.next());
            }
        }

        // 删除应用程序原始属性 (只读属性将被放置在适当的位置)
        Iterator keys = list.iterator();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            removeAttribute(key);
        }
    }
    
    
    /**
     * 返回这个ApplicationContext关联的外观.
     */
    protected ServletContext getFacade() {
        return (this.facade);
    }


    /**
     * 设置只读属性.
     */
    void setAttributeReadOnly(String name) {
        synchronized (attributes) {
            if (attributes.containsKey(name))
                readOnlyAttributes.put(name, name);
        }
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 返回上下文相对路径, 用"/"开头, 表示指定路径的规范版本，在".."和 "."元素解析之后. 
     * 如果指定的路径试图超出当前上下文的边界 (i.e. 太多".."导致), 返回<code>null</code>.
     *
     * @param path 标准化路径
     */
    private String normalize(String path) {

        if (path == null) {
            return null;
        }

        String normalized = path;

        // 规范的斜线，必要时加上斜杠
        if (normalized.indexOf('\\') >= 0)
            normalized = normalized.replace('\\', '/');

        // 解析标准化路径中的"/../"
        while (true) {
            int index = normalized.indexOf("/../");
            if (index < 0)
                break;
            if (index == 0)
                return (null);  // 试图脱离我们的环境
            int index2 = normalized.lastIndexOf('/', index - 1);
            normalized = normalized.substring(0, index2) +
                normalized.substring(index + 3);
        }

        // 返回已完成的标准化路径
        return (normalized);
    }


    /**
     * 将应用程序部署描述符中指定的上下文初始化参数与服务器配置中描述的应用程序参数合并, 慎重对待应用程序参数的<code>override</code>
     */
    private void mergeParameters() {

        if (parameters != null)
            return;
        HashMap results = new HashMap();
        String names[] = context.findParameters();
        for (int i = 0; i < names.length; i++)
            results.put(names[i], context.findParameter(names[i]));
        ApplicationParameter params[] =
            context.findApplicationParameters();
        for (int i = 0; i < params.length; i++) {
            if (params[i].getOverride()) {
                if (results.get(params[i].getName()) == null)
                    results.put(params[i].getName(), params[i].getValue());
            } else {
                results.put(params[i].getName(), params[i].getValue());
            }
        }
        parameters = results;
    }


    /**
     * 列出资源路径（递归），并将它们存储在给定的集合中
     */
    private static void listPaths(Set set, DirContext resources, String path)
        throws NamingException {

        Enumeration childPaths = resources.listBindings(path);
        while (childPaths.hasMoreElements()) {
            Binding binding = (Binding) childPaths.nextElement();
            String name = binding.getName();
            String childPath = path + "/" + name;
            set.add(childPath);
            Object object = binding.getObject();
            if (object instanceof DirContext) {
                listPaths(set, resources, childPath);
            }
        }
    }


    /**
     * 列出资源路径（递归），并将它们存储在给定的集合中
     */
    private static void listCollectionPaths(Set set, DirContext resources, String path) throws NamingException {

        Enumeration childPaths = resources.listBindings(path);
        while (childPaths.hasMoreElements()) {
            Binding binding = (Binding) childPaths.nextElement();
            String name = binding.getName();
            StringBuffer childPath = new StringBuffer(path);
            if (!"/".equals(path) && !path.endsWith("/"))
                childPath.append("/");
            childPath.append(name);
            Object object = binding.getObject();
            if (object instanceof DirContext) {
                childPath.append("/");
            }
            set.add(childPath.toString());
        }
    }


    /**
     * 根据主机名和上下文路径获取完整路径
     */
    private static String getJNDIUri(String hostName, String path) {
        if (!path.startsWith("/"))
            return "/" + hostName + "/" + path;
        else
            return "/" + hostName + path;
    }


	@Override
	public Dynamic addFilter(String arg0, String arg1) {
		return null;
	}

/*************************自己加的，解决报错问题****************************/
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
