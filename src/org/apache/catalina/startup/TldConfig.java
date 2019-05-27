package org.apache.catalina.startup;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.util.StringManager;
import org.apache.tomcat.util.digester.Digester;
import org.xml.sax.InputSource;

/**
 * 启动<b>Context</b>的配置属性和相关servlet的监听器.
 */
public final class TldConfig  {

    // 已知不包含任何 TLD的JAR的名称
    private static HashSet noTldJars;

    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( TldConfig.class );

    private static final String FILE_URL_PREFIX = "file:";
    private static final int FILE_URL_PREFIX_LEN = FILE_URL_PREFIX.length();


    /*
     * 初始化已知不包含任何 TLD的JAR集合
     */
    static {
        noTldJars = new HashSet();
        noTldJars.add("catalina.jar");
        noTldJars.add("catalina-ant.jar");
        noTldJars.add("catalina-cluster.jar");
        noTldJars.add("catalina-optional.jar");
        noTldJars.add("commons-el.jar");
        noTldJars.add("commons-logging-api.jar");
        noTldJars.add("commons-modeler.jar");
        noTldJars.add("jasper-compiler.jar");
        noTldJars.add("jasper-compiler-jdt.jar");
        noTldJars.add("jasper-runtime.jar");
        noTldJars.add("jsp-api.jar");
        noTldJars.add("naming-resources.jar");
        noTldJars.add("naming-factory.jar");
        noTldJars.add("naming-factory-dbcp.jar");
        noTldJars.add("servlet-api.jar");
        noTldJars.add("servlets-cgi.jar");
        noTldJars.add("servlets-default.jar");
        noTldJars.add("servlets-invoker.jar");
        noTldJars.add("servlets-ssi.jar");
        noTldJars.add("servlets-webdav.jar");
        noTldJars.add("tomcat-ajp.jar");
        noTldJars.add("tomcat-coyote.jar");
        noTldJars.add("tomcat-http.jar");
        noTldJars.add("tomcat-util.jar");
        // i18n JARs
        noTldJars.add("catalina-i18n-en.jar");
        noTldJars.add("catalina-i18n-es.jar");
        noTldJars.add("catalina-i18n-fr.jar");
        noTldJars.add("catalina-i18n-ja.jar");
        // Misc JARs not included with Tomcat
        noTldJars.add("ant.jar");
        noTldJars.add("commons-dbcp.jar");
        noTldJars.add("commons-beanutils.jar");
        noTldJars.add("commons-fileupload-1.0.jar");
        noTldJars.add("commons-pool.jar");
        noTldJars.add("commons-digester.jar");
        noTldJars.add("commons-logging.jar");
        noTldJars.add("commons-collections.jar");
        noTldJars.add("jmx.jar");
        noTldJars.add("jmx-tools.jar");
        noTldJars.add("xercesImpl.jar");
        noTldJars.add("xmlParserAPIs.jar");
        noTldJars.add("xml-apis.jar");
        // JARs from J2SE runtime
        noTldJars.add("sunjce_provider.jar");
        noTldJars.add("ldapsec.jar");
        noTldJars.add("localedata.jar");
        noTldJars.add("dnsns.jar");
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * 关联的Context.
     */
    private Context context = null;


    /**
     * The string resources for this package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * 用于处理tag存储库描述符文件的<code>Digester</code>.
     */
    private static Digester tldDigester = null;


    /**
     * 用于打开/关闭TLD验证的属性值
     */
     private static boolean tldValidation = false;


    /**
     * 用来打开/关闭TLD命名空间感知的属性值.
     */
    private static boolean tldNamespaceAware = false;

    private boolean rescan=true;

    private ArrayList listeners=new ArrayList();

    // --------------------------------------------------------- Public Methods

    /**
     * 设置已知不包含TLD的JAR集合.
     *
     * @param jarNames jar文件的逗号分隔名称的列表
     */
    public static void setNoTldJars(String jarNames) {
        if (jarNames != null) {
            noTldJars.clear();
            StringTokenizer tokenizer = new StringTokenizer(jarNames, ",");
            while (tokenizer.hasMoreElements()) {
                noTldJars.add(tokenizer.nextToken());
            }
        }
    }

    /**
     * 设置解析XML实例时是否使用的XML解析器的验证功能.
     * 
     * @param tldValidation true 启用XML实例验证
     */
    public void setTldValidation(boolean tldValidation){
        TldConfig.tldValidation = tldValidation;
    }

    /**
     * 获取 server.xml <host> 属性的 xmlValidation.
     * 
     * @return true if validation is enabled.
     */
    public boolean getTldValidation(){
        return tldValidation;
    }

    /**
     * 获取 server.xml <host> 属性的 xmlNamespaceAware.
     * 
     * @return true if namespace awarenes is enabled.
     */
    public boolean getTldNamespaceAware(){
        return tldNamespaceAware;
    }


    /**
     * 设置解析XML实例时使用的XML解析器的命名空间感知功能.
     * 
     * @param tldNamespaceAware true 启用命名空间感知功能
     */
    public void setTldNamespaceAware(boolean tldNamespaceAware){
        TldConfig.tldNamespaceAware = tldNamespaceAware;
    }    


    public boolean isRescan() {
        return rescan;
    }

    public void setRescan(boolean rescan) {
        this.rescan = rescan;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void addApplicationListener( String s ) {
        //if(log.isDebugEnabled())
            log.debug( "Add tld listener " + s);
        listeners.add(s);
    }

    public String[] getTldListeners() {
        String result[]=new String[listeners.size()];
        listeners.toArray(result);
        return result;
    }


    /**
     * 扫描并配置在这个Web应用程序中发现的所有标记库描述符.
     *
     * @exception Exception if a fatal input/output or parsing error occurs
     */
    public void execute() throws Exception {
        long t1=System.currentTimeMillis();

        File tldCache=null;

        if (context instanceof StandardContext) {
            File workDir= (File)
                ((StandardContext)context).getServletContext().getAttribute(Globals.WORK_DIR_ATTR);
            tldCache=new File( workDir, "tldCache.ser");
        }

        // 不重新扫描选项
        if( ! rescan ) {
            // find the cache
            if( tldCache!= null && tldCache.exists()) {
                // just read it...
                processCache(tldCache);
                return;
            }
        }

        /*
         * 获取TLD资源路径列表, 可能嵌入JAR文件中
         */
        Set resourcePaths = tldScanResourcePaths();
        Map jarPaths = getJarPaths();

        // 检查一下是否可以使用缓存监听器
        if (tldCache != null && tldCache.exists()) {
            long lastModified = getLastModified(resourcePaths, jarPaths);
            if (lastModified < tldCache.lastModified()) {
                processCache(tldCache);
                return;
            }
        }

        // 扫描每个累加的TLD资源路径
        Iterator paths = resourcePaths.iterator();
        while (paths.hasNext()) {
            String path = (String) paths.next();
            if (path.endsWith(".jar")) {
                tldScanJar(path);
            } else {
                tldScanTld(path);
            }
        }
        if (jarPaths != null) {
            paths = jarPaths.values().iterator();
            while (paths.hasNext()) {
                tldScanJar((File) paths.next());
            }
        }

        String list[] = getTldListeners();

        if( tldCache!= null ) {
            log.debug( "Saving tld cache: " + tldCache + " " + list.length);
            try {
                FileOutputStream out=new FileOutputStream(tldCache);
                ObjectOutputStream oos=new ObjectOutputStream( out );
                oos.writeObject( list );
                oos.close();
            } catch( IOException ex ) {
                ex.printStackTrace();
            }
        }

        if( log.isDebugEnabled() )
            log.debug( "Adding tld listeners:" + list.length);
        for( int i=0; list!=null && i<list.length; i++ ) {
            context.addApplicationListener(list[i]);
        }

        long t2=System.currentTimeMillis();
        if( context instanceof StandardContext ) {
            ((StandardContext)context).setTldScanTime(t2-t1);
        }
    }

    // -------------------------------------------------------- Private Methods

    /*
     * 返回给定资源集的最后一个修改日期.
     *
     * @param resourcePaths
     * @param jarPaths
     *
     * @return Last modification date
     */
    private long getLastModified(Set resourcePaths, Map jarPaths)
            throws Exception {

        long lastModified = 0;

        Iterator paths = resourcePaths.iterator();
        while (paths.hasNext()) {
            String path = (String) paths.next();
            URL url = context.getServletContext().getResource(path);
            if (url == null) {
                log.debug( "Null url "+ path );
                break;
            }
            long lastM = url.openConnection().getLastModified();
            if (lastM > lastModified) lastModified = lastM;
            if (log.isDebugEnabled()) {
                log.debug( "Last modified " + path + " " + lastM);
            }
        }

        if (jarPaths != null) {
            paths = jarPaths.values().iterator();
            while (paths.hasNext()) {
                File jarFile = (File) paths.next();
                long lastM = jarFile.lastModified();
                if (lastM > lastModified) lastModified = lastM;
                if (log.isDebugEnabled()) {
                    log.debug("Last modified " + jarFile.getAbsolutePath()
                              + " " + lastM);
                }
            }
        }

        return lastModified;
    }

    private void processCache(File tldCache ) throws IOException {
        // read the cache and return;
        try {
            FileInputStream in=new FileInputStream(tldCache);
            ObjectInputStream ois=new ObjectInputStream( in );
            String list[]=(String [])ois.readObject();
            if( log.isDebugEnabled() )
                log.debug("Reusing tldCache " + tldCache + " " + list.length);
            for( int i=0; list!=null && i<list.length; i++ ) {
                context.addApplicationListener(list[i]);
            }
            ois.close();
        } catch( ClassNotFoundException ex ) {
            ex.printStackTrace();
        }
    }

    /**
     * 创建并返回一个配置为处理标记库描述符的Digester, 寻找要注册的附加监听器类.
     */
    private static Digester createTldDigester() {
        return DigesterFactory.newDigester(tldValidation, 
                                           tldNamespaceAware, 
                                           new TldRuleSet());
    }


    /**
     * 在<code>META-INF</code>的子目录中扫描指定的资源路径下的 JAR文件, 并为需要注册的应用事件监听器扫描每个 TLD.
     *
     * @param resourcePath 要扫描的JAR文件的资源路径
     *
     * @exception Exception 如果在扫描这个JAR时出现异常
     */
    private void tldScanJar(String resourcePath) throws Exception {

        if (log.isDebugEnabled()) {
            log.debug(" Scanning JAR at resource path '" + resourcePath + "'");
        }

        URL url = context.getServletContext().getResource(resourcePath);
        if (url == null) {
            throw new IllegalArgumentException
                                (sm.getString("contextConfig.tldResourcePath",
                                              resourcePath));
        }

        File file = new File(url.getFile());
        file = file.getCanonicalFile();
        tldScanJar(file);
    }

    /**
     * 为应用程序监听器在给定JAR中扫描所有TLD条目.
     *
     * @param file 为应用程序侦听器扫描其TLD条目的JAR文件
     */
    private void tldScanJar(File file) throws Exception {

        JarFile jarFile = null;
        String name = null;

        String jarPath = file.getAbsolutePath();

        try {
            jarFile = new JarFile(file);
            Enumeration entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = (JarEntry) entries.nextElement();
                name = entry.getName();
                if (!name.startsWith("META-INF/")) {
                    continue;
                }
                if (!name.endsWith(".tld")) {
                    continue;
                }
                if (log.isTraceEnabled()) {
                    log.trace("  Processing TLD at '" + name + "'");
                }
                try {
                    tldScanStream(new InputSource(jarFile.getInputStream(entry)));
                } catch (Exception e) {
                    log.error(sm.getString("contextConfig.tldEntryException",
                                           name, jarPath, context.getPath()),
                              e);
                }
            }
        } catch (Exception e) {
            log.error(sm.getString("contextConfig.tldJarException",
                                   jarPath, context.getPath()),
                      e);
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (Throwable t) {
                    // Ignore
                }
            }
        }
    }

    /**
     * 扫描指定输入流中的TLD内容, 并注册在那里找到的所有应用程序事件监听器.
     * <b>NOTE</b> - 调用者应该在方法返回后关闭输入流.
     *
     * @param resourceStream 包含一个标签库描述符的输入流
     *
     * @exception Exception 如果在扫描此TLD时发生异常
     */
    private void tldScanStream(InputSource resourceStream)
        throws Exception {
        if (tldDigester == null){
            tldDigester = createTldDigester();
        }
        
        synchronized (tldDigester) {
            try {
                tldDigester.push(this);
                tldDigester.parse(resourceStream);
            } finally {
                tldDigester.reset();
            }
        }
    }

    /**
     * 在指定的资源路径上扫描TLD内容, 并注册在那里找到的所有应用程序事件监听器.
     *
     * @param resourcePath 正在扫描的资源路径
     *
     * @exception Exception 如果在扫描此TLD时发生异常
     */
    private void tldScanTld(String resourcePath) throws Exception {

        if (log.isDebugEnabled()) {
            log.debug(" Scanning TLD at resource path '" + resourcePath + "'");
        }

        InputSource inputSource = null;
        try {
            inputSource =
                new InputSource(
                    context.getServletContext().getResourceAsStream(resourcePath));
            if (inputSource == null) {
                throw new IllegalArgumentException
                    (sm.getString("contextConfig.tldResourcePath",
                                  resourcePath));
            }
            tldScanStream(inputSource);
        } catch (Exception e) {
             throw new ServletException
                 (sm.getString("contextConfig.tldFileException", resourcePath,
                               context.getPath()),
                  e);
        } 
    }

    /**
     * 为标记库描述符累积和返回一组要分析的资源路径.
     * 返回集合中的每个元素都将是标记库描述符文件的上下文相对路径, 或者<code>META-INF</code>子目录中的包含标签库描述符的JAR文件.
     *
     * @exception IOException 如果在累加资源路径列表时发生输入/输出错误
     */
    private Set tldScanResourcePaths() throws IOException {
        if (log.isDebugEnabled()) {
            log.debug(" Accumulating TLD resource paths");
        }
        Set resourcePaths = new HashSet();

        // 累加在Web应用程序部署描述符中显式列出的资源路径
        if (log.isTraceEnabled()) {
            log.trace("  Scanning <taglib> elements in web.xml");
        }
        String taglibs[] = context.findTaglibs();
        for (int i = 0; i < taglibs.length; i++) {
            String resourcePath = context.findTaglib(taglibs[i]);
            // FIXME - Servlet 2.4 DTD implies that the location MUST be
            // a context-relative path starting with '/'?
            if (!resourcePath.startsWith("/")) {
                resourcePath = "/WEB-INF/" + resourcePath;
            }
            if (log.isTraceEnabled()) {
                log.trace("   Adding path '" + resourcePath +
                    "' for URI '" + taglibs[i] + "'");
            }
            resourcePaths.add(resourcePath);
        }

        DirContext resources = context.getResources();
        if (resources != null) {
            tldScanResourcePathsWebInf(resources, "/WEB-INF", resourcePaths);
        }
        // Return the completed set
        return (resourcePaths);
    }

    /*
     * 扫描通过rootPath指定的Web应用程序的子目录, 随着它的子目录, 对于 TLDs.
     *
     * 最初, rootPath 等于 /WEB-INF. /WEB-INF/classes 和 /WEB-INF/lib 子目录被排除在搜索之外, 按照JSP 2.0规范.
     *
     * @param resources Web应用程序的资源
     * @param rootPath 其子目录被搜索的路径
     * @param tldPaths 要添加到的TLD资源路径的集合
     */
    private void tldScanResourcePathsWebInf(DirContext resources,
                                            String rootPath,
                                            Set tldPaths) 
            throws IOException {

        if (log.isTraceEnabled()) {
            log.trace("  Scanning TLDs in " + rootPath + " subdirectory");
        }

        try {
            NamingEnumeration items = resources.list(rootPath);
            while (items.hasMoreElements()) {
                NameClassPair item = (NameClassPair) items.nextElement();
                String resourcePath = rootPath + "/" + item.getName();
                if (!resourcePath.endsWith(".tld")
                        && (resourcePath.startsWith("/WEB-INF/classes")
                            || resourcePath.startsWith("/WEB-INF/lib"))) {
                    continue;
                }
                if (resourcePath.endsWith(".tld")) {
                    if (log.isTraceEnabled()) {
                        log.trace("   Adding path '" + resourcePath + "'");
                    }
                    tldPaths.add(resourcePath);
                } else {
                    tldScanResourcePathsWebInf(resources, resourcePath,
                                               tldPaths);
                }
            }
        } catch (NamingException e) {
            ; // Silent catch: 即使 /WEB-INF 目录不存在也是可用的
        }
    }

    /**
     * Returns a map of the paths to all JAR files that are accessible to the
     * webapp and will be scanned for TLDs.
     *
     * 这个Map总是包含WEB-INF/lib目录下的所有JAR, 以及在web应用程序的类加载器的类加载器委托链共享的JAR.
     *
     * 后者是对JSP规范中定义的TLD搜索顺序的Tomcat特定扩展.
     * 它允许打包成jar文件的标签库通过Web应用程序共享，只需将它们放到所有Web应用程序都可以访问的位置(即 <CATALINA_HOME>/common/lib).
     *
     * 为TLD扫描到的共享JAR集合通过<tt>noTldJars</tt>类变量缩小, 包含已知不包含任何TLD的JAR名称.
     *
     * @return Map of JAR file paths
     */
    private Map getJarPaths() {

        HashMap jarPathMap = null;

        ClassLoader webappLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader loader = webappLoader;
        while (loader != null) {
            if (loader instanceof URLClassLoader) {
                URL[] urls = ((URLClassLoader) loader).getURLs();
                for (int i=0; i<urls.length; i++) {
                    // Expect file URLs
                    // This is definitely not as clean as using JAR URLs either
                    // over file or the custom jndi handler, but a lot less
                    // buggy overall
                    File file = new File(urls[i].getFile());
                    try {
                        file = file.getCanonicalFile();
                    } catch (IOException e) {
                        // Ignore
                    }
                    if (!file.exists()) {
                        continue;
                    }
                    String path = file.getAbsolutePath();
                    if (!path.endsWith(".jar")) {
                        continue;
                    }
                    /*
                     * 扫描所有WEB-INF/lib下的JAR, 加上任何共享的不知道是否包含TLD的JAR
                     */
                    if (loader == webappLoader
                            || noTldJars == null
                            || !noTldJars.contains(file.getName())) {
                        if (jarPathMap == null) {
                            jarPathMap = new HashMap();
                            jarPathMap.put(path, file);
                        } else if (!jarPathMap.containsKey(path)) {
                            jarPathMap.put(path, file);
                        }
                    }
                }
            }
            loader = loader.getParent();
        }
        return jarPathMap;
    }
}
