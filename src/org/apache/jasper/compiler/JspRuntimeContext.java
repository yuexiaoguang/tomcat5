package org.apache.jasper.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilePermission;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.jsp.JspFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jasper.Constants;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.apache.jasper.runtime.JspFactoryImpl;
import org.apache.jasper.security.SecurityClassLoad;
import org.apache.jasper.servlet.JspServletWrapper;

/**
 * 跟踪 JSP 编译时间文件依赖项, 当&060;%@include file="..."%&062的时候; 使用指令.
 *
 * 后台线程定期检查JSP页面所依赖的文件. 如果一个依赖文件修改包含它的JSP页面会被重新编译.
 *
 * 仅当Web应用程序上下文是目录时才使用.
 */
public final class JspRuntimeContext implements Runnable {

    private Log log = LogFactory.getLog(JspRuntimeContext.class);

    /*
     * 重新加载web应用的JSP多少次.
     */
    private int jspReloadCount;

    /**
     * 通过JSP servlet预加载运行时所需的类, 这样就得不到defineClassInPackage 安全异常.
     */
    static {
        JspFactoryImpl factory = new JspFactoryImpl();
        SecurityClassLoad.securityClassLoad(factory.getClass().getClassLoader());
        JspFactory.setDefaultFactory(factory);
    }

    // ----------------------------------------------------------- Constructors

    /**
     * 从文件中生成的任何依赖项中的加载.
     *
     * @param context web应用的ServletContext
     */
    public JspRuntimeContext(ServletContext context, Options options) {

        this.context = context;
        this.options = options;
        // Get the parent class loader
        parentClassLoader =
            (URLClassLoader) Thread.currentThread().getContextClassLoader();
        if (parentClassLoader == null) {
            parentClassLoader =
                (URLClassLoader)this.getClass().getClassLoader();
        }

        if (log.isDebugEnabled()) {
		    if (parentClassLoader != null) {
		    	log.debug(Localizer.getMessage("jsp.message.parent_class_loader_is", parentClassLoader.toString()));
		    } else {
		    	log.debug(Localizer.getMessage("jsp.message.parent_class_loader_is", "<none>"));
		    }
        }

        initClassPath();

		if (context instanceof org.apache.jasper.servlet.JspCServletContext) {
		    return;
		}

        if (System.getSecurityManager() != null) {
            initSecurity();
        }

        // 如果这个Web应用程序上下文从目录中运行, 启动后台编译线程
        String appBase = context.getRealPath("/");         
        if (!options.getDevelopment()
                && appBase != null
                && options.getCheckInterval() > 0) {
            if (appBase.endsWith(File.separator) ) {
                appBase = appBase.substring(0,appBase.length()-1);
            }
            String directory =
                appBase.substring(appBase.lastIndexOf(File.separator));
            threadName = threadName + "[" + directory + "]";
            threadStart();
        }                                            
    }

    // ----------------------------------------------------- Instance Variables

    /**
     * 这个Web应用程序的 ServletContext
     */
    private ServletContext context;
    private Options options;
    private URLClassLoader parentClassLoader;
    private PermissionCollection permissionCollection;
    private CodeSource codeSource;                    
    private String classpath;

    /**
     * JSP 页面映射到它们的 JspServletWrapper
     */
    private Map jsps = Collections.synchronizedMap( new HashMap());
 

    /**
     * 后台线程.
     */
    private Thread thread = null;


    /**
     * 后台线程完成信号量.
     */
    private boolean threadDone = false;


    /**
     * 注册后台线程的名称.
     */
    private String threadName = "JspRuntimeContext";

    // ------------------------------------------------------ Public Methods

    /**
     * 添加一个 JspServletWrapper.
     *
     * @param jspUri JSP URI
     * @param jsw Servlet wrapper for JSP
     */
    public void addWrapper(String jspUri, JspServletWrapper jsw) {
        jsps.remove(jspUri);
        jsps.put(jspUri,jsw);
    }

    /**
     * 获取一个现有的JspServletWrapper.
     *
     * @param jspUri JSP URI
     * @return JspServletWrapper for JSP
     */
    public JspServletWrapper getWrapper(String jspUri) {
        return (JspServletWrapper) jsps.get(jspUri);
    }

    /**
     * 删除JspServletWrapper.
     *
     * @param jspUri JSP URI of JspServletWrapper to remove
     */
    public void removeWrapper(String jspUri) {
        jsps.remove(jspUri);
    }

    /**
     * 返回存在JspServletWrappers的JSP的数量, 即, 已经被加载进应用的JSP的数量.
     */
    public int getJspCount() {
        return jsps.size();
    }

    /**
     * 获取该应用上下文的 SecurityManager Policy CodeSource.
     *
     * @return CodeSource for JSP
     */
    public CodeSource getCodeSource() {
        return codeSource;
    }

    /**
     * 获取父级 URLClassLoader.
     *
     * @return URLClassLoader parent
     */
    public URLClassLoader getParentClassLoader() {
        return parentClassLoader;
    }

    /**
     * 获取该应用上下文的 SecurityManager PermissionCollection.
     */
    public PermissionCollection getPermissionCollection() {
        return permissionCollection;
    }

    /**
     * 处理web应用上下文的"destory"事件.
     */                                                        
    public void destroy() {
        threadStop();

        Iterator servlets = jsps.values().iterator();
        while (servlets.hasNext()) {
            ((JspServletWrapper) servlets.next()).destroy();
        }
    }

    /**
     * 递增JSP重新加载计数器.
     */
    public synchronized void incrementJspReloadCount() {
        jspReloadCount++;
    }

    /**
     * 重置JSP重新加载计数器.
     *
     * @param count Value to which to reset the JSP reload counter
     */
    public synchronized void setJspReloadCount(int count) {
        this.jspReloadCount = count;
    }

    /**
     * 获取JSP重新加载计数器的当前值.
     */
    public int getJspReloadCount() {
        return jspReloadCount;
    }


    // -------------------------------------------------------- Private Methods

    /**
     * 后台线程使用该方法检查检查此类注册的JSP依赖项.
     */
    private void checkCompile() {
        Object [] wrappers = jsps.values().toArray();
        for (int i = 0; i < wrappers.length; i++ ) {
            JspServletWrapper jsw = (JspServletWrapper)wrappers[i];
            JspCompilationContext ctxt = jsw.getJspEngineContext();
            // JspServletWrapper 也同步, 当它检测到它必须重新加载
            synchronized(jsw) {
                try {
                    ctxt.compile();
                } catch (FileNotFoundException ex) {
                    ctxt.incrementRemoved();
                } catch (Throwable t) {
                    jsw.getServletContext().log("Background compile failed",
						t);
                }
            }
        }
    }

    /**
     * 传递给Java编译器的classpath.
     */
    public String getClassPath() {
        return classpath;
    }

    /**
     * 用于初始化classpath.
     */
    private void initClassPath() {

        URL [] urls = parentClassLoader.getURLs();
        StringBuffer cpath = new StringBuffer();
        String sep = System.getProperty("path.separator");

        for(int i = 0; i < urls.length; i++) {
            // Tomcat 4 可以使用 URL的除了文件URL的, 除文件以外的协议: 将生成一个错误的文件系统路径, 所以只添加文件:
            // 协议的URL的路径.
            if( urls[i].getProtocol().equals("file") ) {
                cpath.append((String)urls[i].getFile()+sep);
            }
        }    

        cpath.append(options.getScratchDir() + sep);

        String cp = (String) context.getAttribute(Constants.SERVLET_CLASSPATH);
        if (cp == null || cp.equals("")) {
            cp = options.getClassPath();
        }

        classpath = cpath.toString() + cp;

        if(log.isDebugEnabled()) {
            log.debug("Compilation classpath initialized: " + getClassPath());
        }
    }

    /**
     * 初始化SecurityManager 数据.
     */
    private void initSecurity() {

        // 设置这个Web应用上下文的 PermissionCollection, 基于配置的权限, 为web应用上下文目录的根目录, 然后为该目录添加一个文件读取权限.
        Policy policy = Policy.getPolicy();
        if( policy != null ) {
            try {          
                // 获取Web应用程序上下文的权限
                String docBase = context.getRealPath("/");
                if( docBase == null ) {
                    docBase = options.getScratchDir().toString();
                }
                String codeBase = docBase;
                if (!codeBase.endsWith(File.separator)){
                    codeBase = codeBase + File.separator;
                }
                File contextDir = new File(codeBase);
                URL url = contextDir.getCanonicalFile().toURL();
                codeSource = new CodeSource(url,(Certificate[])null);
                permissionCollection = policy.getPermissions(codeSource);

                // 为Web应用程序上下文目录创建一个文件读取权限
                if (!docBase.endsWith(File.separator)){
                    permissionCollection.add
                        (new FilePermission(docBase,"read"));
                    docBase = docBase + File.separator;
                } else {
                    permissionCollection.add
                        (new FilePermission
                            (docBase.substring(0,docBase.length() - 1),"read"));
                }
                docBase = docBase + "-";
                permissionCollection.add(new FilePermission(docBase,"read"));

                // 创建一个Web 应用 TempDir目录的文件读取权限
                String workDir = options.getScratchDir().toString();
                if (!workDir.endsWith(File.separator)){
                    permissionCollection.add
                        (new FilePermission(workDir,"read"));
                    workDir = workDir + File.separator;
                }
                workDir = workDir + "-";
                permissionCollection.add(new FilePermission(workDir,"read"));

                // 允许JSP 访问 org.apache.jasper.runtime.HttpJspBase
                permissionCollection.add( new RuntimePermission(
                    "accessClassInPackage.org.apache.jasper.runtime") );

                if (parentClassLoader instanceof URLClassLoader) {
                    URL [] urls = parentClassLoader.getURLs();
                    String jarUrl = null;
                    String jndiUrl = null;
                    for (int i=0; i<urls.length; i++) {
                        if (jndiUrl == null
                                && urls[i].toString().startsWith("jndi:") ) {
                            jndiUrl = urls[i].toString() + "-";
                        }
                        if (jarUrl == null
                                && urls[i].toString().startsWith("jar:jndi:")
                                ) {
                            jarUrl = urls[i].toString();
                            jarUrl = jarUrl.substring(0,jarUrl.length() - 2);
                            jarUrl = jarUrl.substring(0,
                                     jarUrl.lastIndexOf('/')) + "/-";
                        }
                    }
                    if (jarUrl != null) {
                        permissionCollection.add(
                                new FilePermission(jarUrl,"read"));
                        permissionCollection.add(
                                new FilePermission(jarUrl.substring(4),"read"));
                    }
                    if (jndiUrl != null)
                        permissionCollection.add(
                                new FilePermission(jndiUrl,"read") );
                }
            } catch(Exception e) {
                context.log("Security Init for context failed",e);
            }
        }
    }


    // -------------------------------------------------------- Thread Support

    /**
     * 启动后台线程，周期性地检查JSP中包含的文件的编译时间的更改.
     *
     * @exception IllegalStateException 如果现在不应该启动后台线程
     */
    protected void threadStart() {

        // 后台线程是否已经启动?
        if (thread != null) {
            return;
        }

        // Start the background thread
        threadDone = false;
        thread = new Thread(this, threadName);
        thread.setDaemon(true);
        thread.start();

    }


    /**
     * 关闭后台线程.
     */ 
    protected void threadStop() {

        if (thread == null) {
            return;
        }

        threadDone = true;
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException e) {
            ;
        }
        thread = null;
    }

    /**
     * 睡眠时间, 通过<code>checkInterval</code>属性指定.
     */ 
    protected void threadSleep() {
        try {
            Thread.sleep(options.getCheckInterval() * 1000L);
        } catch (InterruptedException e) {
            ;
        }
    }   
    
    
    // ------------------------------------------------------ Background Thread
        
        
    /**
     * 后台线程, 检查JSP和重新编译需要的标志包含的文件的修改.
     */ 
    public void run() {
        
        // 循环直到终止信号量被设置
        while (!threadDone) {

            // 等待检查间隔
            threadSleep();

            // 检查包含的文件，这些文件比使用它们的JSP更新.
            try {
                checkCompile();
            } catch (Throwable t) {
                log.error("Exception checking if recompile needed: ", t);
            }
        }
    }
}
