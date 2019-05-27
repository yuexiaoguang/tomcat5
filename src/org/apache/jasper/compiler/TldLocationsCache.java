package org.apache.jasper.compiler;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.xmlparser.ParserUtils;
import org.apache.jasper.xmlparser.TreeNode;

/**
 * 为Web应用程序定义“全局”的所有标记库的容器.
 * 
 * 标签库可以通过以下两种方式之一全局定义:
 *   1. 通过web.xml中的<taglib>元素:
 *      标记库的URI和位置在<taglib>元素中指定.
 *   2. 通过打包的在META-INF目录或其子目录中包含.tld文件的jar文件. taglib 是'global', 如果它拥有<uri>元素.
 *
 * taglib URI 和它对应的TaglibraryInfoImpl之间的映射保存在这个容器中.
 * 然而, 因为这个方法TagLibraryInfo 和 TagInfo 已经被定义, 不能在页面调用中共享一个TagLibraryInfo实例.
 * 一个bug已经提交给规范领导.
 * 与此同时, 我们要做的就是保存用Taglib URI关联的TLD可以发现的位置.
 *
 * 当一个JSP页面有一个taglib伪指令, 首先搜索这个容器中的映射(see method getLocation()).
 * 如果找到映射, 然后返回TLD的位置. 如果找不到映射, 然后在taglib指令指定的URI可以被解释为对该标签库的TLD位置.
 */
public class TldLocationsCache {

    // Logger
    private Log log = LogFactory.getLog(TldLocationsCache.class);

    /**
     * URI的类型可以为标记库指定
     */
    public static final int ABS_URI = 0;
    public static final int ROOT_REL_URI = 1;
    public static final int NOROOT_REL_URI = 2;

    private static final String WEB_XML = "/WEB-INF/web.xml";
    private static final String FILE_PROTOCOL = "file:";
    private static final String JAR_FILE_SUFFIX = ".jar";

    // 已知不包含任何TLD的JAR名称
    private static HashSet noTldJars;

    /**
     * “全局”标记库URI映射到与该标记库关联的TLD的位置（资源路径）.
     * 位置作为一个String 数组返回:
     *    [0] 位置
     *    [1] 如果位置是JAR文件, 这是TLD的位置.
     */
    private Hashtable mappings;

    private boolean initialized;
    private ServletContext ctxt;
    private boolean redeployMode;

    //*********************************************************************
    // Constructor and Initilizations

    /*
     * 初始化一组已知不包含任何TLD的JAR
     */
    static {
        noTldJars = new HashSet();
        noTldJars.add("ant.jar");
        noTldJars.add("catalina.jar");
        noTldJars.add("catalina-ant.jar");
        noTldJars.add("catalina-cluster.jar");
        noTldJars.add("catalina-optional.jar");
        noTldJars.add("catalina-i18n-fr.jar");
        noTldJars.add("catalina-i18n-ja.jar");
        noTldJars.add("catalina-i18n-es.jar");
        noTldJars.add("commons-dbcp.jar");
        noTldJars.add("commons-modeler.jar");
        noTldJars.add("commons-logging-api.jar");
        noTldJars.add("commons-beanutils.jar");
        noTldJars.add("commons-fileupload-1.0.jar");
        noTldJars.add("commons-pool.jar");
        noTldJars.add("commons-digester.jar");
        noTldJars.add("commons-logging.jar");
        noTldJars.add("commons-collections.jar");
        noTldJars.add("commons-el.jar");
        noTldJars.add("jakarta-regexp-1.2.jar");
        noTldJars.add("jasper-compiler.jar");
        noTldJars.add("jasper-runtime.jar");
        noTldJars.add("jmx.jar");
        noTldJars.add("jmx-tools.jar");
        noTldJars.add("jsp-api.jar");
        noTldJars.add("jkshm.jar");
        noTldJars.add("jkconfig.jar");
        noTldJars.add("naming-common.jar");
        noTldJars.add("naming-resources.jar");
        noTldJars.add("naming-factory.jar");
        noTldJars.add("naming-java.jar");
        noTldJars.add("servlet-api.jar");
        noTldJars.add("servlets-default.jar");
        noTldJars.add("servlets-invoker.jar");
        noTldJars.add("servlets-common.jar");
        noTldJars.add("servlets-webdav.jar");
        noTldJars.add("tomcat-util.jar");
        noTldJars.add("tomcat-http11.jar");
        noTldJars.add("tomcat-jni.jar");
        noTldJars.add("tomcat-jk.jar");
        noTldJars.add("tomcat-jk2.jar");
        noTldJars.add("tomcat-coyote.jar");
        noTldJars.add("xercesImpl.jar");
        noTldJars.add("xmlParserAPIs.jar");
        noTldJars.add("xml-apis.jar");
        // JARs from J2SE runtime
        noTldJars.add("sunjce_provider.jar");
        noTldJars.add("ldapsec.jar");
        noTldJars.add("localedata.jar");
        noTldJars.add("dnsns.jar");
    }
    
    public TldLocationsCache(ServletContext ctxt) {
        this(ctxt, true);
    }

    /**
     * @param ctxt Jasper运行在的web应用的 servlet上下文
     * @param redeployMode 如果是true, 则编译器将允许从相同的jar重新部署标记库, 以降低服务器的速度为代价.
     * 必须是JDK 1.3.1_01a 或更新, 因为JDK bug 4211817 在这个版本解决的.
     * 如果redeployMode是 false, 使用速度更快但能力较低的模式.
     */
    public TldLocationsCache(ServletContext ctxt, boolean redeployMode) {
        this.ctxt = ctxt;
        this.redeployMode = redeployMode;
        mappings = new Hashtable();
        initialized = false;
    }

    /**
     * 设置已知不包含任何TLD的一组JAR.
     *
     * @param jarNames 逗号分隔的已知不包含任何TLD的JAR文件名列表
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
     * 获取与给定taglib的URI关联的TLD的'location'.
     *
     * 返回null, 如果URI不与Web应用程序中的任何标记库关联.
     * 标签库是'exposed', 无论是明确在web.xml中的, 或隐式地通过一个JAR文件(WEB-INF/lib)中部署的taglib的TLD中的URI标签.
     * 
     * @param uri taglib uri
     *
     * @return 两个String数组: 第一个元素表示TLD的真正路径. 如果指向TLD的路径指向JAR文件, 然后，第二个元素表示JAR文件中TLD条目的名称.
     * 返回null, 如果URI没有和任何标签库关联.
     */
    public String[] getLocation(String uri) throws JasperException {
        if (!initialized) {
            init();
        }
        return (String[]) mappings.get(uri);
    }

    /** 
     * 返回URI的类型:
     *     ABS_URI
     *     ROOT_REL_URI
     *     NOROOT_REL_URI
     */
    public static int uriType(String uri) {
        if (uri.indexOf(':') != -1) {
            return ABS_URI;
        } else if (uri.startsWith("/")) {
            return ROOT_REL_URI;
        } else {
            return NOROOT_REL_URI;
        }
    }

    private void init() throws JasperException {
        if (initialized) return;
        try {
            processWebDotXml();
            scanJars();
            processTldsInFileSystem("/WEB-INF/");
            initialized = true;
        } catch (Exception ex) {
            throw new JasperException(Localizer.getMessage(
                    "jsp.error.internal.tldinit", ex.getMessage()));
        }
    }

    /*
     * 填充web.xml中描述的taglib map.
     */    
    private void processWebDotXml() throws Exception {

        InputStream is = null;

        try {
            // 获取输入流到Web应用程序部署描述符
            String altDDName = (String)ctxt.getAttribute(
                                                    Constants.ALT_DD_ATTR);
            if (altDDName != null) {
                try {
                    is = new FileInputStream(altDDName);
                } catch (FileNotFoundException e) {
                    if (log.isWarnEnabled()) {
                        log.warn(Localizer.getMessage(
                                            "jsp.error.internal.filenotfound",
                                            altDDName));
                    }
                }
            } else {
                is = ctxt.getResourceAsStream(WEB_XML);
                if (is == null && log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage(
                                            "jsp.error.internal.filenotfound",
                                            WEB_XML));
                }
            }

            if (is == null) {
                return;
            }

            // 解析Web应用程序部署描述符
            TreeNode webtld = null;
            // altDDName 是DD的绝对路径
            if (altDDName != null) {
                webtld = new ParserUtils().parseXMLDocument(altDDName, is);
            } else {
                webtld = new ParserUtils().parseXMLDocument(WEB_XML, is);
            }

            // 允许Taglib是一个根或JSP配置的元素(JSP2.0)
            TreeNode jspConfig = webtld.findChild("jsp-config");
            if (jspConfig != null) {
                webtld = jspConfig;
            }
            Iterator taglibs = webtld.findChildren("taglib");
            while (taglibs.hasNext()) {

                // 解析下一个 <taglib> 元素
                TreeNode taglib = (TreeNode) taglibs.next();
                String tagUri = null;
                String tagLoc = null;
                TreeNode child = taglib.findChild("taglib-uri");
                if (child != null)
                    tagUri = child.getBody();
                child = taglib.findChild("taglib-location");
                if (child != null)
                    tagLoc = child.getBody();

                // 如果适当的话，保存这个位置
                if (tagLoc == null)
                    continue;
                if (uriType(tagLoc) == NOROOT_REL_URI)
                    tagLoc = "/WEB-INF/" + tagLoc;
                String tagLoc2 = null;
                if (tagLoc.endsWith(JAR_FILE_SUFFIX)) {
                    tagLoc = ctxt.getResource(tagLoc).toString();
                    tagLoc2 = "META-INF/taglib.tld";
                }
                mappings.put(tagUri, new String[] { tagLoc, tagLoc2 });
            }
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Throwable t) {}
            }
        }
    }

    /**
     * 扫描给定的JarURLConnection 为位于META-INF或子目录中的TLD 文件, 添加一个隐式的 map entry到 taglib map,
     * 对于有一个<uri>元素的TLD.
     *
     * @param conn 要扫描到JAR文件中的JarURLConnection
     * @param ignore true 如果有任何异常抛出, 当处理给定JAR时应忽略, 否则false
     */
    private void scanJar(JarURLConnection conn, boolean ignore)
                throws JasperException {

        JarFile jarFile = null;
        String resourcePath = conn.getJarFileURL().toString();
        try {
            if (redeployMode) {
                conn.setUseCaches(false);
            }
            jarFile = conn.getJarFile();
            Enumeration entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = (JarEntry) entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("META-INF/")) continue;
                if (!name.endsWith(".tld")) continue;
                InputStream stream = jarFile.getInputStream(entry);
                try {
                    String uri = getUriFromTld(resourcePath, stream);
                    // 只有在map中没有URI时才添加隐式映射条目
                    if (uri != null && mappings.get(uri) == null) {
                        mappings.put(uri, new String[]{ resourcePath, name });
                    }
                } finally {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (Throwable t) {
                            // do nothing
                        }
                    }
                }
            }
        } catch (Exception ex) {
            if (!redeployMode) {
                // 如果不重新部署模式, 关闭 jar 以防出错
                if (jarFile != null) {
                    try {
                        jarFile.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
            }
            if (!ignore) {
                throw new JasperException(ex);
            }
        } finally {
            if (redeployMode) {
                // 如果在部署模式, 总是关闭 jar
                if (jarFile != null) {
                    try {
                        jarFile.close();
                    } catch (Throwable t) {
                        // ignore
                    }
                }
            }
        }
    }

    /*
     * 在文件系统/WEB-INF目录中搜索TLD 文件, 并添加一个隐式的map entry到 taglib map, 对于有一个<uri>元素的TLD.
     */
    private void processTldsInFileSystem(String startPath)
            throws Exception {

        Set dirList = ctxt.getResourcePaths(startPath);
        if (dirList != null) {
            Iterator it = dirList.iterator();
            while (it.hasNext()) {
                String path = (String) it.next();
                if (path.endsWith("/")) {
                    processTldsInFileSystem(path);
                }
                if (!path.endsWith(".tld")) {
                    continue;
                }
                InputStream stream = ctxt.getResourceAsStream(path);
                String uri = null;
                try {
                    uri = getUriFromTld(path, stream);
                } finally {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (Throwable t) {
                            // do nothing
                        }
                    }
                }
                // 只有在map中没有URI时才添加隐式映射条目
                if (uri != null && mappings.get(uri) == null) {
                    mappings.put(uri, new String[] { path, null });
                }
            }
        }
    }

    /*
     * 返回给定TLD的URI元素的值, 或者null 如果给定的TLD不包含任何这样的元素.
     */
    private String getUriFromTld(String resourcePath, InputStream in) 
        throws JasperException {
        // 在指定的资源路径上解析标记库描述符
        TreeNode tld = new ParserUtils().parseXMLDocument(resourcePath, in);
        TreeNode uri = tld.findChild("uri");
        if (uri != null) {
            String body = uri.getBody();
            if (body != null)
                return body;
        }

        return null;
    }

    /*
     * 扫描所有访问Web应用的类加载器和它的父类加载器的JAR.
     * 
     * JAR列表总是包含WEB-INF/lib下的JAR, 以及所有Web应用的类加载器的类加载器委托链中的共享的JAR.
     *
     * 考虑类加载委托链中的 JAR建立一个Tomcat的特定扩展到JSP规范中定义的TLD搜索顺序.
     * 它允许标签库打包成JAR文件通过web应用共享, 只要把它们放到一个所有Web应用可以访问的位置(e.g.,
     * <CATALINA_HOME>/common/lib).
     *
     * 为TLD扫描的一组共享JAR 被<tt>noTldJars</tt>类变量缩小, 包含已知没有任何TLD的JAR的名称.
     */
    private void scanJars() throws Exception {

        ClassLoader webappLoader
            = Thread.currentThread().getContextClassLoader();
        ClassLoader loader = webappLoader;

        while (loader != null) {
            if (loader instanceof URLClassLoader) {
                URL[] urls = ((URLClassLoader) loader).getURLs();
                for (int i=0; i<urls.length; i++) {
                    URLConnection conn = urls[i].openConnection();
                    if (conn instanceof JarURLConnection) {
                        if (needScanJar(loader, webappLoader,
                                        ((JarURLConnection) conn).getJarFile().getName())) {
                            scanJar((JarURLConnection) conn, true);
                        }
                    } else {
                        String urlStr = urls[i].toString();
                        if (urlStr.startsWith(FILE_PROTOCOL)
                                && urlStr.endsWith(JAR_FILE_SUFFIX)
                                && needScanJar(loader, webappLoader, urlStr)) {
                            URL jarURL = new URL("jar:" + urlStr + "!/");
                            scanJar((JarURLConnection) jarURL.openConnection(),
                                    true);
                        }
                    }
                }
            }

            loader = loader.getParent();
        }
    }

    /*
     * 确定给定<tt>jarPath</tt>的JAR 文件需要被TLD扫描.
     *
     * @param loader 在父链中的当前类加载器
     * @param webappLoader Web应用类加载器
     * @param jarPath JAR 文件路径
     *
     * @return TRUE 如果由<tt>jarPath</tt>标识的JAR文件需要被TLD扫描, 否则FALSE
     */
    private boolean needScanJar(ClassLoader loader, ClassLoader webappLoader,
                                String jarPath) {
        if (loader == webappLoader) {
            // WEB-INF/lib目录下的JAR 根据规范必须无条件扫描.
            return true;
        } else {
            String jarName = jarPath;
            int slash = jarPath.lastIndexOf('/');
            if (slash >= 0) {
                jarName = jarPath.substring(slash + 1);
            }
            return (!noTldJars.contains(jarName));
        }
    }
}
