package org.apache.catalina.loader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes.Name;

import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.util.StringManager;
import org.apache.naming.JndiPermission;
import org.apache.naming.resources.Resource;
import org.apache.naming.resources.ResourceAttributes;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.compat.JdkCompat;

/**
 * 指定的Web应用程序类装入器.
 * <p>
 * 这类装载器是一个JDK的<code>URLClassLoader</code>的完整实现.
 * 它设计用来充分和标准的<code>URLClassLoader</code>兼容, 虽然它的内部行为可能完全不同.
 * <p>
 * <strong>实现注意</strong> - 这个类加载器忠实地遵循规范中推荐的委托模型.
 * 系统类装入器将首先查询, 然后是本地存储库, 只有到父类装入器的委托才会出现.
 * 这使得Web应用程序重写任何共享类, 除了J2SE的类.
 * 特殊处理, 是从使用XML解析器接口、JNDI接口、 以及来自servlet API的类提供, 这些从来没有从web应用程序库加载.
 * <p>
 * <strong>实现注意</strong> - 由于Jasper编译技术的局限性, 任何包含servlet类的存储库都将被类装入器忽略.
 * <p>
 * <strong>实现注意</strong> - 类装入器生成资源URL，其中包含一个类从JAR文件加载时包含的完整JAR URL,
 * 允许在类级别设置安全权限, 甚至当一个类包含在一个JAR中时.
 * <p>
 * <strong>实现注意</strong> - 本地存储库的搜索顺序，是通过初始构造函数，
 * 和任何后续调用<code>addRepository()</code>或<code>addJar()</code>方法.
 * <p>
 * <strong>实现注意</strong> - 不进行密封违规或安全检查，除非安全管理器存在.
 */
public class WebappClassLoader extends URLClassLoader implements Reloader, Lifecycle {

    protected static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( WebappClassLoader.class );

    protected class PrivilegedFindResource
        implements PrivilegedAction {

        protected File file;
        protected String path;

        PrivilegedFindResource(File file, String path) {
            this.file = file;
            this.path = path;
        }

        public Object run() {
            return findResourceInternal(file, path);
        }

    }


    // ------------------------------------------------------- Static Variables


    /**
     * 触发器类集合，将导致一个建议的存储库不被添加，如果这个类对装载了这个工厂类的类加载器可见. 
     * 通常情况下, 触发器类将被列出, 已经融入了JDK以后版本的组件,
     * 但是需要在早期版本上运行相应JAR文件.
     */
    protected static final String[] triggers = {
        "javax.servlet.Servlet"                     // Servlet API
    };

    /** 
     * Jdk 兼容支持.
     */
    protected static JdkCompat jdkCompat = JdkCompat.getJdkCompat();

    /**
     * 包名集合，不允许未经授权从一个webapp类加载器加载..
     */
    protected static final String[] packageTriggers = {
    };


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    
    /**
     * 使用反JAR锁定代码，URL在访问资源时进行重新路由.
     */
    boolean antiJARLocking = false; 
    

    // ----------------------------------------------------------- Constructors


    public WebappClassLoader() {
        super(new URL[0]);
        this.parent = getParent();
        system = getSystemClassLoader();
        securityManager = System.getSecurityManager();

        if (securityManager != null) {
            refreshPolicy();
        }
    }


    public WebappClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
                
        this.parent = getParent();
        
        system = getSystemClassLoader();
        securityManager = System.getSecurityManager();

        if (securityManager != null) {
            refreshPolicy();
        }
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 相关目录上下文给访问的资源.
     */
    protected DirContext resources = null;


    /**
     * 加载的类和资源的ResourceEntry缓存,使用资源名称作为key
     */
    protected HashMap resourceEntries = new HashMap();


    /**
     * 未找到的资源集合
     */
    protected HashMap notFoundResources = new HashMap();


    /**
     * 这个类是否应该将委托装载到父类装入器，在搜索自己的库(即通常的Java2的委托模型)之前?
     * 如果设置为<code>false</code>, 这个类装入器将首先搜索自己的存储库, 只有在本地找不到类或资源时，才委托给父级.
     */
    protected boolean delegate = false;


    /**
     * 上次访问一个JAR的时间.
     */
    protected long lastJarAccessed = 0L;


    /**
     * 本地库的集合, 按照顺序，应该搜索本地加载的类或资源.
     */
    protected String[] repositories = new String[0];


     /**
      * 库的URL，用于缓存的getURLs结果 .
      */
     protected URL[] repositoryURLs = null;


    /**
     * 在工作目录中，存储库转换为路径(原始的Jasper), 但这是用来生成虚假网址，应该调用getURLs.
     */
    protected File[] files = new File[0];


    /**
     * JAR集合, 按照顺序，应该搜索本地加载的类或资源.
     */
    protected JarFile[] jarFiles = new JarFile[0];


    /**
     * JAR集合, 按照顺序，应该搜索本地加载的类或资源.
     */
    protected File[] jarRealFiles = new File[0];


    /**
     * 将监视添加的JAR文件路径.
     */
    protected String jarPath = null;


    /**
     * JAR集合, 按照顺序，应该搜索本地加载的类或资源.
     */
    protected String[] jarNames = new String[0];


    /**
     * 最后修改日期的JAR集合, 按照顺序，应该搜索本地加载的类或资源
     */
    protected long[] lastModifiedDates = new long[0];


    /**
     * 检查修改时应检查的资源列表.
     */
    protected String[] paths = new String[0];


    /**
     * 如果这个加载程序是Web应用程序上下文的，读取文件和JNDI权限要求的集合
     */
    protected ArrayList permissionList = new ArrayList();


    /**
     * 从JAR加载资源将被提取的路径.
     */
    protected File loaderDir = null;


    /**
     * 每个CodeSource的PermissionCollection
     */
    protected HashMap loaderPC = new HashMap();


    /**
     * 安装的SecurityManager实例.
     */
    protected SecurityManager securityManager = null;


    /**
     * 父类加载器.
     */
    protected ClassLoader parent = null;


    /**
     * 系统类加载器.
     */
    protected ClassLoader system = null;


    /**
     * 是否已启动?
     */
    protected boolean started = false;


    /**
     * 是否拥有外部存储库.
     */
    protected boolean hasExternalRepositories = false;

    /**
     * 属性文件是否需要转换
     */
    protected boolean needConvert = false;


    /**
     * 所有权限.
     */
    protected Permission allPermission = new java.security.AllPermission();


    // ------------------------------------------------------------- Properties


    /**
     * 获取相关资源
     */
    public DirContext getResources() {
        return this.resources;
    }


    /**
     * 设置相关资源
     */
    public void setResources(DirContext resources) {
        this.resources = resources;
    }


    /**
     * 返回"delegate first"标志
     */
    public boolean getDelegate() {
        return (this.delegate);
    }


    /**
     * 设置"delegate first"标志.
     *
     * @param delegate The new "delegate first" flag
     */
    public void setDelegate(boolean delegate) {
        this.delegate = delegate;
    }


    /**
     * @return Returns the antiJARLocking.
     */
    public boolean getAntiJARLocking() {
        return antiJARLocking;
    }
    
    
    /**
     * @param antiJARLocking The antiJARLocking to set.
     */
    public void setAntiJARLocking(boolean antiJARLocking) {
        this.antiJARLocking = antiJARLocking;
    }

    
    /**
     * 如果有一个Java SecurityManager创建了一个读文件权限FilePermission
     * 或JndiPermission为文件目录路径
     *
     * @param path file directory path
     */
    public void addPermission(String path) {
        if (path == null) {
            return;
        }

        if (securityManager != null) {
            Permission permission = null;
            if( path.startsWith("jndi:") || path.startsWith("jar:jndi:") ) {
                if (!path.endsWith("/")) {
                    path = path + "/";
                }
                permission = new JndiPermission(path + "*");
                addPermission(permission);
            } else {
                if (!path.endsWith(File.separator)) {
                    permission = new FilePermission(path, "read");
                    addPermission(permission);
                    path = path + File.separator;
                }
                permission = new FilePermission(path + "-", "read");
                addPermission(permission);
            }
        }
    }


    /**
     * 如果有一个Java SecurityManager创建了一个读文件权限FilePermission或JndiPermission为URL.
     *
     * @param url URL for a file or directory on local system
     */
    public void addPermission(URL url) {
        if (url != null) {
            addPermission(url.toString());
        }
    }


    /**
     * 如果有一个Java SecurityManager创建了一个Permission.
     *
     * @param permission The permission
     */
    public void addPermission(Permission permission) {
        if ((securityManager != null) && (permission != null)) {
            permissionList.add(permission);
        }
    }


    /**
     * 返回JAR路径
     */
    public String getJarPath() {
        return this.jarPath;
    }


    /**
     * 修改Jar路径
     */
    public void setJarPath(String jarPath) {
        this.jarPath = jarPath;
    }


    /**
     * 修改工作目录.
     */
    public void setWorkDir(File workDir) {
        this.loaderDir = new File(workDir, "loader");
    }


    // ------------------------------------------------------- Reloader Methods


    /**
     * 添加一个新库到集合中，这个ClassLoader可以找到并加载类的地方.
     *
     * @param repository 要加载的类资源的名称, 例如一个目录的路径名, 一个JAR文件的路径名, 或一个zip文件的路径名
     *
     * @exception IllegalArgumentException if the specified repository is
     *  invalid or does not exist
     */
    public void addRepository(String repository) {

        // 忽略任何标准库,他们可以用addJar 或 addRepository设置
        if (repository.startsWith("/WEB-INF/lib")
            || repository.startsWith("/WEB-INF/classes"))
            return;

        // 将这个库添加到底层类加载器
        try {
            URL url = new URL(repository);
            super.addURL(url);
            hasExternalRepositories = true;
            repositoryURLs = null;
        } catch (MalformedURLException e) {
            IllegalArgumentException iae = new IllegalArgumentException
                ("Invalid repository: " + repository); 
            jdkCompat.chainException(iae, e);
            throw iae;
        }
    }


    /**
     * 添加一个新库到集合中，这个ClassLoader可以找到并加载类的地方.
     *
     * @param repository 要加载的类资源的名称, 例如一个目录的路径名, 一个JAR文件的路径名, 或一个zip文件的路径名
     *
     * @exception IllegalArgumentException if the specified repository is
     *  invalid or does not exist
     */
    synchronized void addRepository(String repository, File file) {

        // Note : 应该只有一个, 但我认为我们应该保持这种通用性
        if (repository == null)
            return;

        if (log.isDebugEnabled())
            log.debug("addRepository(" + repository + ")");

        int i;

        // Add this repository to our internal list
        String[] result = new String[repositories.length + 1];
        for (i = 0; i < repositories.length; i++) {
            result[i] = repositories[i];
        }
        result[repositories.length] = repository;
        repositories = result;

        // Add the file to the list
        File[] result2 = new File[files.length + 1];
        for (i = 0; i < files.length; i++) {
            result2[i] = files[i];
        }
        result2[files.length] = file;
        files = result2;
    }


    synchronized void addJar(String jar, JarFile jarFile, File file)
        throws IOException {

        if (jar == null)
            return;
        if (jarFile == null)
            return;
        if (file == null)
            return;

        if (log.isDebugEnabled())
            log.debug("addJar(" + jar + ")");

        int i;

        if ((jarPath != null) && (jar.startsWith(jarPath))) {

            String jarName = jar.substring(jarPath.length());
            while (jarName.startsWith("/"))
                jarName = jarName.substring(1);

            String[] result = new String[jarNames.length + 1];
            for (i = 0; i < jarNames.length; i++) {
                result[i] = jarNames[i];
            }
            result[jarNames.length] = jarName;
            jarNames = result;

        }

        try {

            // Register the JAR for tracking
            long lastModified =
                ((ResourceAttributes) resources.getAttributes(jar))
                .getLastModified();

            String[] result = new String[paths.length + 1];
            for (i = 0; i < paths.length; i++) {
                result[i] = paths[i];
            }
            result[paths.length] = jar;
            paths = result;

            long[] result3 = new long[lastModifiedDates.length + 1];
            for (i = 0; i < lastModifiedDates.length; i++) {
                result3[i] = lastModifiedDates[i];
            }
            result3[lastModifiedDates.length] = lastModified;
            lastModifiedDates = result3;

        } catch (NamingException e) {
            // Ignore
        }

        // 如果JAR当前包含无效类, 实际上不使用它的方法
        if (!validateJarFile(file))
            return;

        JarFile[] result2 = new JarFile[jarFiles.length + 1];
        for (i = 0; i < jarFiles.length; i++) {
            result2[i] = jarFiles[i];
        }
        result2[jarFiles.length] = jarFile;
        jarFiles = result2;

        // Add the file to the list
        File[] result4 = new File[jarRealFiles.length + 1];
        for (i = 0; i < jarRealFiles.length; i++) {
            result4[i] = jarRealFiles[i];
        }
        result4[jarRealFiles.length] = file;
        jarRealFiles = result4;
    }


    /**
     * 为这个类装入器返回当前库的String数组. 
     * 如果没有，返回零长度数组.由于安全原因, 返回一个克隆后的数组(因为字符串是不可变的).
     */
    public String[] findRepositories() {
        return ((String[])repositories.clone());
    }


    /**
     * 修改了一个或多个类或资源，以便重新加载?
     */
    public boolean modified() {
        if (log.isDebugEnabled())
            log.debug("modified()");

        // Checking for modified loaded resources
        int length = paths.length;

        // 在两个数组更新时可能出现罕见的竞争情况
        // 如果没有添加最新的类，则完全可以检查 (下次检查
        int length2 = lastModifiedDates.length;
        if (length > length2)
            length = length2;

        for (int i = 0; i < length; i++) {
            try {
                long lastModified =
                    ((ResourceAttributes) resources.getAttributes(paths[i]))
                    .getLastModified();
                if (lastModified != lastModifiedDates[i]) {
                    if( log.isDebugEnabled() ) 
                        log.debug("  Resource '" + paths[i]
                                  + "' was modified; Date is now: "
                                  + new java.util.Date(lastModified) + " Was: "
                                  + new java.util.Date(lastModifiedDates[i]));
                    return (true);
                }
            } catch (NamingException e) {
                log.error("    Resource '" + paths[i] + "' is missing");
                return (true);
            }
        }

        length = jarNames.length;

        // Check if JARs have been added or removed
        if (getJarPath() != null) {

            try {
                NamingEnumeration enumeration = resources.listBindings(getJarPath());
                int i = 0;
                while (enumeration.hasMoreElements() && (i < length)) {
                    NameClassPair ncPair = (NameClassPair) enumeration.nextElement();
                    String name = ncPair.getName();
                    // Ignore non JARs present in the lib folder
                    if (!name.endsWith(".jar"))
                        continue;
                    if (!name.equals(jarNames[i])) {
                        // Missing JAR
                        log.info("    Additional JARs have been added : '" 
                                 + name + "'");
                        return (true);
                    }
                    i++;
                }
                if (enumeration.hasMoreElements()) {
                    while (enumeration.hasMoreElements()) {
                        NameClassPair ncPair = 
                            (NameClassPair) enumeration.nextElement();
                        String name = ncPair.getName();
                        // Additional non-JAR files are allowed
                        if (name.endsWith(".jar")) {
                            // There was more JARs
                            log.info("    Additional JARs have been added");
                            return (true);
                        }
                    }
                } else if (i < jarNames.length) {
                    // There was less JARs
                    log.info("    Additional JARs have been added");
                    return (true);
                }
            } catch (NamingException e) {
                if (log.isDebugEnabled())
                    log.debug("    Failed tracking modifications of '"
                        + getJarPath() + "'");
            } catch (ClassCastException e) {
                log.error("    Failed tracking modifications of '"
                          + getJarPath() + "' : " + e.getMessage());
            }

        }
        // No classes have been modified
        return (false);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer("WebappClassLoader\r\n");
        sb.append("  delegate: ");
        sb.append(delegate);
        sb.append("\r\n");
        sb.append("  repositories:\r\n");
        if (repositories != null) {
            for (int i = 0; i < repositories.length; i++) {
                sb.append("    ");
                sb.append(repositories[i]);
                sb.append("\r\n");
            }
        }
        if (this.parent != null) {
            sb.append("----------> Parent Classloader:\r\n");
            sb.append(this.parent.toString());
            sb.append("\r\n");
        }
        return (sb.toString());
    }


    // ---------------------------------------------------- ClassLoader Methods


     /**
      * 添加指定的URL到类装载器.
      */
     protected void addURL(URL url) {
         super.addURL(url);
         hasExternalRepositories = true;
         repositoryURLs = null;
     }


    /**
     * 在本地库中查找指定的类. 
     * 如果没找到, 抛出<code>ClassNotFoundException</code>
     *
     * @param name 要加载的类名
     *
     * @exception ClassNotFoundException 如果未找到该类
     */
    public Class findClass(String name) throws ClassNotFoundException {

        if (log.isDebugEnabled())
            log.debug("    findClass(" + name + ")");

        // (1) 使用SecurityManager时定义此类的权限
        if (securityManager != null) {
            int i = name.lastIndexOf('.');
            if (i >= 0) {
                try {
                    if (log.isTraceEnabled())
                        log.trace("      securityManager.checkPackageDefinition");
                    securityManager.checkPackageDefinition(name.substring(0,i));
                } catch (Exception se) {
                    if (log.isTraceEnabled())
                        log.trace("      -->Exception-->ClassNotFoundException", se);
                    throw new ClassNotFoundException(name, se);
                }
            }
        }

        // 从父类找到这个类 (如果未找到，抛出ClassNotFoundException)
        Class clazz = null;
        try {
            if (log.isTraceEnabled())
                log.trace("      findClassInternal(" + name + ")");
            try {
                clazz = findClassInternal(name);
            } catch(ClassNotFoundException cnfe) {
                if (!hasExternalRepositories) {
                    throw cnfe;
                }
            } catch(AccessControlException ace) {
                throw new ClassNotFoundException(name, ace);
            } catch (RuntimeException e) {
                if (log.isTraceEnabled())
                    log.trace("      -->RuntimeException Rethrown", e);
                throw e;
            }
            if ((clazz == null) && hasExternalRepositories) {
                try {
                    clazz = super.findClass(name);
                } catch(AccessControlException ace) {
                    throw new ClassNotFoundException(name, ace);
                } catch (RuntimeException e) {
                    if (log.isTraceEnabled())
                        log.trace("      -->RuntimeException Rethrown", e);
                    throw e;
                }
            }
            if (clazz == null) {
                if (log.isDebugEnabled())
                    log.debug("    --> Returning ClassNotFoundException");
                throw new ClassNotFoundException(name);
            }
        } catch (ClassNotFoundException e) {
            if (log.isTraceEnabled())
                log.trace("    --> Passing on ClassNotFoundException");
            throw e;
        }

        // Return the class we have located
        if (log.isTraceEnabled())
            log.debug("      Returning class " + clazz);
        if ((log.isTraceEnabled()) && (clazz != null))
            log.debug("      Loaded by " + clazz.getClassLoader());
        return (clazz);
    }


    /**
     * 在本地存储库中找到指定的资源, 并返回一个<code>URL</code>在这, 或者<code>null</code>，如果资源未找到.
     *
     * @param name 要查找的资源的名称
     */
    public URL findResource(final String name) {

        if (log.isDebugEnabled())
            log.debug("    findResource(" + name + ")");

        URL url = null;

        ResourceEntry entry = (ResourceEntry) resourceEntries.get(name);
        if (entry == null) {
            entry = findResourceInternal(name, name);
        }
        if (entry != null) {
            url = entry.source;
        }

        if ((url == null) && hasExternalRepositories)
            url = super.findResource(name);

        if (log.isDebugEnabled()) {
            if (url != null)
                log.debug("    --> Returning '" + url.toString() + "'");
            else
                log.debug("    --> Resource not found, returning null");
        }
        return (url);

    }


    /**
     * 返回枚举<code>URLs</code>用指定名称表示所有资源.
     * 如果没找到, 返回空枚举.
     *
     * @param name 要查找的资源的名称
     *
     * @exception IOException 如果发生输入/输出错误
     */
    public Enumeration findResources(String name) throws IOException {

        if (log.isDebugEnabled())
            log.debug("    findResources(" + name + ")");

        Vector result = new Vector();

        int jarFilesLength = jarFiles.length;
        int repositoriesLength = repositories.length;

        int i;

        // Looking at the repositories
        for (i = 0; i < repositoriesLength; i++) {
            try {
                String fullPath = repositories[i] + name;
                resources.lookup(fullPath);
                // Note : 在这里没有异常意味着找到了资源
                try {
                    result.addElement(getURI(new File(files[i], name)));
                } catch (MalformedURLException e) {
                    // Ignore
                }
            } catch (NamingException e) {
            }
        }

        // Looking at the JAR files
        synchronized (jarFiles) {
            openJARs();
            for (i = 0; i < jarFilesLength; i++) {
                JarEntry jarEntry = jarFiles[i].getJarEntry(name);
                if (jarEntry != null) {
                    try {
                        String jarFakeUrl = getURI(jarRealFiles[i]).toString();
                        jarFakeUrl = "jar:" + jarFakeUrl + "!/" + name;
                        result.addElement(new URL(jarFakeUrl));
                    } catch (MalformedURLException e) {
                        // Ignore
                    }
                }
            }
        }

        // 添加一个调用父类的结果
        if (hasExternalRepositories) {

            Enumeration otherResourcePaths = super.findResources(name);

            while (otherResourcePaths.hasMoreElements()) {
                result.addElement(otherResourcePaths.nextElement());
            }

        }
        return result.elements();
    }


    /**
     * 查找给定名称的资源.
     * 资源是一些数据(images, audio, text, etc.)可以通过与代码位置无关的方式来访问类代码. 
     * 资源的名称是一个 "/"-分隔标识资源的路径名.
     * 如果找不到资源, 返回<code>null</code>.
     * <p>
     * 此方法根据以下算法进行搜索, 找到合适的URL后返回. 如果找不到资源, 返回<code>null</code>.
     * <ul>
     * <li>如果<code>delegate</code>属性被设置为<code>true</code>,
     *     调用父类加载器的<code>getResource()</code>方法.</li>
     * <li>调用<code>findResource()</code>在本地定义的库中查找此资源.</li>
     * <li>调用父类加载器的<code>getResource()</code>方法.</li>
     * </ul>
     *
     * @param name 返回URL的资源的名称
     */
    public URL getResource(String name) {

        if (log.isDebugEnabled())
            log.debug("getResource(" + name + ")");
        URL url = null;

        // (1) 委托给父级
        if (delegate) {
            if (log.isDebugEnabled())
                log.debug("  Delegating to parent classloader " + parent);
            ClassLoader loader = parent;
            if (loader == null)
                loader = system;
            url = loader.getResource(name);
            if (url != null) {
                if (log.isDebugEnabled())
                    log.debug("  --> Returning '" + url.toString() + "'");
                return (url);
            }
        }

        // (2) 搜索本地库
        url = findResource(name);
        if (url != null) {
            // 在JAR的情况下特殊处理定位存储库
            if (antiJARLocking) {
                ResourceEntry entry = (ResourceEntry) resourceEntries.get(name);
                try {
                    String repository = entry.codeBase.toString();
                    if ((repository.endsWith(".jar")) 
                            && (!(name.endsWith(".class")))) {
                        // Copy binary content to the work directory if not present
                        File resourceFile = new File(loaderDir, name);
                        url = resourceFile.toURL();
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
            if (log.isDebugEnabled())
                log.debug("  --> Returning '" + url.toString() + "'");
            return (url);
        }

        // (3) 如果没有尝试，无条件地委托给父服务器
        if( !delegate ) {
            ClassLoader loader = parent;
            if (loader == null)
                loader = system;
            url = loader.getResource(name);
            if (url != null) {
                if (log.isDebugEnabled())
                    log.debug("  --> Returning '" + url.toString() + "'");
                return (url);
            }
        }

        // (4) 找不到资源
        if (log.isDebugEnabled())
            log.debug("  --> Resource not found, returning null");
        return (null);

    }


    /**
     * 查找给定名称的资源, 并返回一个可以用来读取它的输入流.
     * 搜索顺序如 <code>getResource()</code>所述, 在检查资源数据是否已被缓存之前.
     * 如果找不到资源, 返回<code>null</code>.
     *
     * @param name 返回输入流的资源的名称
     */
    public InputStream getResourceAsStream(String name) {

        if (log.isDebugEnabled())
            log.debug("getResourceAsStream(" + name + ")");
        InputStream stream = null;

        // (0) 检查此资源的缓存副本
        stream = findLoadedResource(name);
        if (stream != null) {
            if (log.isDebugEnabled())
                log.debug("  --> Returning stream from cache");
            return (stream);
        }

        // (1) 委托给父级
        if (delegate) {
            if (log.isDebugEnabled())
                log.debug("  Delegating to parent classloader " + parent);
            ClassLoader loader = parent;
            if (loader == null)
                loader = system;
            stream = loader.getResourceAsStream(name);
            if (stream != null) {
                // FIXME - cache???
                if (log.isDebugEnabled())
                    log.debug("  --> Returning stream from parent");
                return (stream);
            }
        }

        // (2) 搜索本地库
        if (log.isDebugEnabled())
            log.debug("  Searching local repositories");
        URL url = findResource(name);
        if (url != null) {
            // FIXME - cache???
            if (log.isDebugEnabled())
                log.debug("  --> Returning stream from local");
            stream = findLoadedResource(name);
            try {
                if (hasExternalRepositories && (stream == null))
                    stream = url.openStream();
            } catch (IOException e) {
                ; // Ignore
            }
            if (stream != null)
                return (stream);
        }

        // (3) 无条件委托给父级
        if (!delegate) {
            if (log.isDebugEnabled())
                log.debug("  Delegating to parent classloader unconditionally " + parent);
            ClassLoader loader = parent;
            if (loader == null)
                loader = system;
            stream = loader.getResourceAsStream(name);
            if (stream != null) {
                // FIXME - cache???
                if (log.isDebugEnabled())
                    log.debug("  --> Returning stream from parent");
                return (stream);
            }
        }

        // (4) 找不到资源
        if (log.isDebugEnabled())
            log.debug("  --> Resource not found, returning null");
        return (null);

    }


    /**
     * 用指定的名称加载类. 
     * 这个方法搜索类的方式和<code>loadClass(String, boolean)</code>一样，第二个参数为<code>false</code>.
     *
     * @param name 要加载的类的二进制名称
     *
     * @exception ClassNotFoundException 如果未找到该类
     */
    public Class loadClass(String name) throws ClassNotFoundException {
        return (loadClass(name, false));
    }


    /**
     * 用指定的名称加载类, 使用以下算法搜索，直到找到并返回类为止.
     * 如果找不到类, 返回<code>ClassNotFoundException</code>.
     * <ul>
     * <li>调用<code>findLoadedClass(String)</code>检查类是否已加载.
     * 	如果已存在, 返回相同的<code>Class</code>对象.</li>
     * <li>如果<code>delegate</code>属性被设置为<code>true</code>,
     *     调用父类加载器的<code>loadClass()</code>方法.</li>
     * <li>调用<code>findClass()</code>在本地定义的存储库中找到这个类.</li>
     * <li>调用父类加载器的<code>loadClass()</code>方法.</li>
     * </ul>
     * 如果使用上述步骤找到类, 以及<code>resolve</code>标记是<code>true</code>, 
     * 这个方法随后将在结果Class对象调用<code>resolveClass(Class)</code>.
     *
     * @param name 要加载的类的名称
     * @param resolve 如果是<code>true</code>，然后解决这个类
     *
     * @exception ClassNotFoundException 如果未找到该类
     */
    public Class loadClass(String name, boolean resolve)
        throws ClassNotFoundException {

        if (log.isDebugEnabled())
            log.debug("loadClass(" + name + ", " + resolve + ")");
        Class clazz = null;

        // 如果类装入器停止，则不加载类
        if (!started) {
            log.info(sm.getString("webappClassLoader.stopped", name));
            throw new ThreadDeath();
        }

        // (0) 检查以前加载的本地类缓存
        clazz = findLoadedClass0(name);
        if (clazz != null) {
            if (log.isDebugEnabled())
                log.debug("  Returning class from cache");
            if (resolve)
                resolveClass(clazz);
            return (clazz);
        }

        // (0.1) 检查我们以前加载的类缓存
        clazz = findLoadedClass(name);
        if (clazz != null) {
            if (log.isDebugEnabled())
                log.debug("  Returning class from cache");
            if (resolve)
                resolveClass(clazz);
            return (clazz);
        }

        // (0.2) 尝试用系统类装入器加载类, 为了防止程序重写J2SE类
        try {
            clazz = system.loadClass(name);
            if (clazz != null) {
                if (resolve)
                    resolveClass(clazz);
                return (clazz);
            }
        } catch (ClassNotFoundException e) {
            // Ignore
        }

        // (0.5) 访问这个类的权限，当使用一个SecurityManager的时候
        if (securityManager != null) {
            int i = name.lastIndexOf('.');
            if (i >= 0) {
                try {
                    securityManager.checkPackageAccess(name.substring(0,i));
                } catch (SecurityException se) {
                    String error = "Security Violation, attempt to use " +
                        "Restricted Class: " + name;
                    log.info(error, se);
                    throw new ClassNotFoundException(error, se);
                }
            }
        }

        boolean delegateLoad = delegate || filter(name);

        // (1) 委托给父级
        if (delegateLoad) {
            if (log.isDebugEnabled())
                log.debug("  Delegating to parent classloader1 " + parent);
            ClassLoader loader = parent;
            if (loader == null)
                loader = system;
            try {
                clazz = loader.loadClass(name);
                if (clazz != null) {
                    if (log.isDebugEnabled())
                        log.debug("  Loading class from parent");
                    if (resolve)
                        resolveClass(clazz);
                    return (clazz);
                }
            } catch (ClassNotFoundException e) {
                ;
            }
        }

        // (2) 搜索本地库
        if (log.isDebugEnabled())
            log.debug("  Searching local repositories");
        try {
            clazz = findClass(name);
            if (clazz != null) {
                if (log.isDebugEnabled())
                    log.debug("  Loading class from local repository");
                if (resolve)
                    resolveClass(clazz);
                return (clazz);
            }
        } catch (ClassNotFoundException e) {
            ;
        }

        // (3) 无条件委托给父级
        if (!delegateLoad) {
            if (log.isDebugEnabled())
                log.debug("  Delegating to parent classloader at end: " + parent);
            ClassLoader loader = parent;
            if (loader == null)
                loader = system;
            try {
                clazz = loader.loadClass(name);
                if (clazz != null) {
                    if (log.isDebugEnabled())
                        log.debug("  Loading class from parent");
                    if (resolve)
                        resolveClass(clazz);
                    return (clazz);
                }
            } catch (ClassNotFoundException e) {
                ;
            }
        }

        throw new ClassNotFoundException(name);
    }


    /**
     * 得到一个CodeSource权限. 
     * 如果这个WebappClassLoader实例是web应用上下文,
     * 添加read FilePermission 或 JndiPermissions 对于基本目录 (if unpacked), 上下文URL, 和jar文件资源.
     *
     * @param codeSource 代码是从哪里加载的
     * @return PermissionCollection for CodeSource
     */
    protected PermissionCollection getPermissions(CodeSource codeSource) {
        String codeUrl = codeSource.getLocation().toString();
        PermissionCollection pc;
        if ((pc = (PermissionCollection)loaderPC.get(codeUrl)) == null) {
            pc = super.getPermissions(codeSource);
            if (pc != null) {
                Iterator perms = permissionList.iterator();
                while (perms.hasNext()) {
                    Permission p = (Permission)perms.next();
                    pc.add(p);
                }
                loaderPC.put(codeUrl,pc);
            }
        }
        return (pc);
    }


    /**
     * 返回用于加载类和资源的URL的搜索路径.
     * This includes the original list of URLs specified to the constructor,
     * along with any URLs subsequently appended by the addURL() method.
     * 
     * @return 用于加载类和资源的URL的搜索路径.
     */
    public URL[] getURLs() {

        if (repositoryURLs != null) {
            return repositoryURLs;
        }

        URL[] external = super.getURLs();

        int filesLength = files.length;
        int jarFilesLength = jarRealFiles.length;
        int length = filesLength + jarFilesLength + external.length;
        int i;

        try {

            URL[] urls = new URL[length];
            for (i = 0; i < length; i++) {
                if (i < filesLength) {
                    urls[i] = getURL(files[i]);
                } else if (i < filesLength + jarFilesLength) {
                    urls[i] = getURL(jarRealFiles[i - filesLength]);
                } else {
                    urls[i] = external[i - filesLength - jarFilesLength];
                }
            }

            repositoryURLs = urls;

        } catch (MalformedURLException e) {
            repositoryURLs = new URL[0];
        }
        return repositoryURLs;
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * 添加生命周期事件监听器.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {
    }


    /**
     * 获取所有的生命周期监听器.
     * 如果没有, 返回零长度数组.
     */
    public LifecycleListener[] findLifecycleListeners() {
        return new LifecycleListener[0];
    }


    /**
     * 移除生命周期事件监听器.
     *
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener) {
    }


    /**
     * 开始类加载器.
     *
     * @exception LifecycleException 如果发生生命周期错误
     */
    public void start() throws LifecycleException {

        started = true;
        String encoding = null;
        try {
            encoding = System.getProperty("file.encoding");
        } catch (Exception e) {
            return;
        }
        if (encoding.indexOf("EBCDIC")!=-1) {
            needConvert = true;
        }

    }


    /**
     * 停止类加载器
     *
     * @exception LifecycleException 如果发生生命周期错误
     */
    public void stop() throws LifecycleException {

        started = false;

        int length = files.length;
        for (int i = 0; i < length; i++) {
            files[i] = null;
        }

        length = jarFiles.length;
        for (int i = 0; i < length; i++) {
            try {
                if (jarFiles[i] != null) {
                    jarFiles[i].close();
                }
            } catch (IOException e) {
                // Ignore
            }
            jarFiles[i] = null;
        }

        notFoundResources.clear();
        resourceEntries.clear();
        resources = null;
        repositories = null;
        repositoryURLs = null;
        files = null;
        jarFiles = null;
        jarRealFiles = null;
        jarPath = null;
        jarNames = null;
        lastModifiedDates = null;
        paths = null;
        hasExternalRepositories = false;
        parent = null;

        permissionList.clear();
        loaderPC.clear();

        if (loaderDir != null) {
            deleteDir(loaderDir);
        }

        // 清空common-logging中的类加载器引用
        org.apache.commons.logging.LogFactory.release(this);
        // 清空VM的bean 中的类加载器引用
        java.beans.Introspector.flushCaches();
        // 清空IntrospectionUtils 缓存
        IntrospectionUtils.clear();
    }


    /**
     * 用于周期性信号的类加载器释放JAR 资源.
     */
    public void closeJARs(boolean force) {
        if (jarFiles.length > 0) {
                synchronized (jarFiles) {
                    if (force || (System.currentTimeMillis() 
                                  > (lastJarAccessed + 90000))) {
                        for (int i = 0; i < jarFiles.length; i++) {
                            try {
                            	if (jarFiles[i] != null) {
                            		jarFiles[i].close();
                            		jarFiles[i] = null;
                            	}
                            } catch (IOException e) {
                                log.warn("Failed to close JAR", e);
                            }
                        }
                    }
                }
        }
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 用于周期性信号的类加载器释放JAR 资源..
     */
    protected void openJARs() {
        if (started && (jarFiles.length > 0)) {
            lastJarAccessed = System.currentTimeMillis();
            if (jarFiles[0] == null) {
                for (int i = 0; i < jarFiles.length; i++) {
                	try {
                		jarFiles[i] = new JarFile(jarRealFiles[i]);
                	} catch (IOException e) {
                		log.warn("Failed to open JAR", e);
                	}
                }
            }
        }
    }


    /**
     * 在本地存储库中找到指定的类.
     *
     * @return 加载的类, 如果找不到类，则为null
     */
    protected Class findClassInternal(String name)
        throws ClassNotFoundException {

        if (!validate(name))
            throw new ClassNotFoundException(name);

        String tempPath = name.replace('.', '/');
        String classPath = tempPath + ".class";

        ResourceEntry entry = null;

        entry = findResourceInternal(name, classPath);

        if (entry == null)
            throw new ClassNotFoundException(name);

        Class clazz = entry.loadedClass;
        if (clazz != null)
            return clazz;

        synchronized (this) {
            if (entry.binaryContent == null && entry.loadedClass == null)
                throw new ClassNotFoundException(name);
        }

        // 查找包
        String packageName = null;
        int pos = name.lastIndexOf('.');
        if (pos != -1)
            packageName = name.substring(0, pos);

        Package pkg = null;

        if (packageName != null) {

            pkg = getPackage(packageName);

            // 定义包(if null)
            if (pkg == null) {
                if (entry.manifest == null) {
                    definePackage(packageName, null, null, null, null, null,
                                  null, null);
                } else {
                    definePackage(packageName, entry.manifest, entry.codeBase);
                }
            }

        }

        // 创建代码源对象
        CodeSource codeSource =
            new CodeSource(entry.codeBase, entry.certificates);

        if (securityManager != null) {

            // 检查密封
            if (pkg != null) {
                boolean sealCheck = true;
                if (pkg.isSealed()) {
                    sealCheck = pkg.isSealed(entry.codeBase);
                } else {
                    sealCheck = (entry.manifest == null)
                        || !isPackageSealed(packageName, entry.manifest);
                }
                if (!sealCheck)
                    throw new SecurityException
                        ("Sealing violation loading " + name + " : Package "
                         + packageName + " is sealed.");
            }

        }

        synchronized (this) {
            if (entry.loadedClass == null) {
                clazz = defineClass(name, entry.binaryContent, 0,
                        entry.binaryContent.length, 
                        codeSource);
                entry.loadedClass = clazz;
                entry.binaryContent = null;
                entry.source = null;
                entry.codeBase = null;
                entry.manifest = null;
                entry.certificates = null;
            } else {
                clazz = entry.loadedClass;
            }
        }
        return clazz;
    }

    /**
     * 在本地存储库中找到指定的类. 这一块将在AccessControl.doPrivilege块之后执行.
     *
     * @return 加载的资源, 或 null
     */
    protected ResourceEntry findResourceInternal(File file, String path){
        ResourceEntry entry = new ResourceEntry();
        try {
            entry.source = getURI(new File(file, path));
            entry.codeBase = getURL(new File(file, path));
        } catch (MalformedURLException e) {
            return null;
        }   
        return entry;
    }
    

    /**
     * 在本地存储库中找到指定的资源.
     *
     * @return 加载的资源, 或 null
     */
    protected ResourceEntry findResourceInternal(String name, String path) {

        if (!started) {
            log.info(sm.getString("webappClassLoader.stopped", name));
            return null;
        }

        if ((name == null) || (path == null))
            return null;

        ResourceEntry entry = (ResourceEntry) resourceEntries.get(name);
        if (entry != null)
            return entry;

        int contentLength = -1;
        InputStream binaryStream = null;

        int jarFilesLength = jarFiles.length;
        int repositoriesLength = repositories.length;

        int i;

        Resource resource = null;

        boolean fileNeedConvert = false;

        for (i = 0; (entry == null) && (i < repositoriesLength); i++) {
            try {

                String fullPath = repositories[i] + path;

                Object lookupResult = resources.lookup(fullPath);
                if (lookupResult instanceof Resource) {
                    resource = (Resource) lookupResult;
                }

                // Note : 在这里没有异常意味着找到了资源
                 if (securityManager != null) {
                    PrivilegedAction dp =
                        new PrivilegedFindResource(files[i], path);
                    entry = (ResourceEntry)AccessController.doPrivileged(dp);
                 } else {
                    entry = findResourceInternal(files[i], path);
                 }

                ResourceAttributes attributes =
                    (ResourceAttributes) resources.getAttributes(fullPath);
                contentLength = (int) attributes.getContentLength();
                entry.lastModified = attributes.getLastModified();

                if (resource != null) {

                    try {
                        binaryStream = resource.streamContent();
                    } catch (IOException e) {
                        return null;
                    }

                    if (needConvert) {
                        if (path.endsWith(".properties")) {
                            fileNeedConvert = true;
                        }
                    }

                    // 注册修改检查的完整路径
                    // Note: 只有在不断的同步对象的需要
                    synchronized (allPermission) {

                        int j;

                        long[] result2 = 
                            new long[lastModifiedDates.length + 1];
                        for (j = 0; j < lastModifiedDates.length; j++) {
                            result2[j] = lastModifiedDates[j];
                        }
                        result2[lastModifiedDates.length] = entry.lastModified;
                        lastModifiedDates = result2;

                        String[] result = new String[paths.length + 1];
                        for (j = 0; j < paths.length; j++) {
                            result[j] = paths[j];
                        }
                        result[paths.length] = fullPath;
                        paths = result;
                    }
                }
            } catch (NamingException e) {
            }
        }

        if ((entry == null) && (notFoundResources.containsKey(name)))
            return null;

        JarEntry jarEntry = null;

        synchronized (jarFiles) {
            openJARs();
            for (i = 0; (entry == null) && (i < jarFilesLength); i++) {

                jarEntry = jarFiles[i].getJarEntry(path);

                if (jarEntry != null) {

                    entry = new ResourceEntry();
                    try {
                        entry.codeBase = getURL(jarRealFiles[i]);
                        String jarFakeUrl = getURI(jarRealFiles[i]).toString();
                        jarFakeUrl = "jar:" + jarFakeUrl + "!/" + path;
                        entry.source = new URL(jarFakeUrl);
                        entry.lastModified = jarRealFiles[i].lastModified();
                    } catch (MalformedURLException e) {
                        return null;
                    }
                    contentLength = (int) jarEntry.getSize();
                    try {
                        entry.manifest = jarFiles[i].getManifest();
                        binaryStream = jarFiles[i].getInputStream(jarEntry);
                    } catch (IOException e) {
                        return null;
                    }

                    // 提取JAR 中资源的workDir
                    if (antiJARLocking && !(path.endsWith(".class"))) {
                        byte[] buf = new byte[1024];
                        File resourceFile = new File
                            (loaderDir, jarEntry.getName());
                        if (!resourceFile.exists()) {
                            Enumeration entries = jarFiles[i].entries();
                            while (entries.hasMoreElements()) {
                                JarEntry jarEntry2 = 
                                    (JarEntry) entries.nextElement();
                                if (!(jarEntry2.isDirectory()) 
                                    && (!jarEntry2.getName().endsWith
                                        (".class"))) {
                                    resourceFile = new File
                                        (loaderDir, jarEntry2.getName());
                                    resourceFile.getParentFile().mkdirs();
                                    FileOutputStream os = null;
                                    InputStream is = null;
                                    try {
                                        is = jarFiles[i].getInputStream
                                            (jarEntry2);
                                        os = new FileOutputStream
                                            (resourceFile);
                                        while (true) {
                                            int n = is.read(buf);
                                            if (n <= 0) {
                                                break;
                                            }
                                            os.write(buf, 0, n);
                                        }
                                    } catch (IOException e) {
                                        // Ignore
                                    } finally {
                                        try {
                                            if (is != null) {
                                                is.close();
                                            }
                                        } catch (IOException e) {
                                        }
                                        try {
                                            if (os != null) {
                                                os.close();
                                            }
                                        } catch (IOException e) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (entry == null) {
                synchronized (notFoundResources) {
                    notFoundResources.put(name, name);
                }
                return null;
            }

            if (binaryStream != null) {

                byte[] binaryContent = new byte[contentLength];

                int pos = 0;
                try {

                    while (true) {
                        int n = binaryStream.read(binaryContent, pos,
                                                  binaryContent.length - pos);
                        if (n <= 0)
                            break;
                        pos += n;
                    }
                    binaryStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }

                if (fileNeedConvert) {
                    String str = new String(binaryContent,0,pos);
                    try {
                        binaryContent = str.getBytes("UTF-8");
                    } catch (Exception e) {
                        return null;
                    }
                }
                entry.binaryContent = binaryContent;

                // 证书只有在输入流的JarEntry已经被全部读取之后才可用
                if (jarEntry != null) {
                    entry.certificates = jarEntry.getCertificates();
                }
            }
        }

        // 在本地资源存储库中添加条目
        synchronized (resourceEntries) {
            // 确保所有可能在竞争中的线程使用相同的ResourceEntry实例加载特定的类直到结束
            ResourceEntry entry2 = (ResourceEntry) resourceEntries.get(name);
            if (entry2 == null) {
                resourceEntries.put(name, entry);
            } else {
                entry = entry2;
            }
        }
        return entry;
    }


    /**
     * 如果指定的包名是根据给定的清单密封的，则返回true.
     */
    protected boolean isPackageSealed(String name, Manifest man) {

        String path = name.replace('.', '/') + '/';
        Attributes attr = man.getAttributes(path); 
        String sealed = null;
        if (attr != null) {
            sealed = attr.getValue(Name.SEALED);
        }
        if (sealed == null) {
            if ((attr = man.getMainAttributes()) != null) {
                sealed = attr.getValue(Name.SEALED);
            }
        }
        return "true".equalsIgnoreCase(sealed);
    }


    /**
     * 如果先前由该类装入器加载并缓存，则查找具有给定名称的资源, 并将输入流返回到资源数据.
     * 如果此资源未被缓存, 返回<code>null</code>.
     *
     * @param name 要返回的资源的名称
     */
    protected InputStream findLoadedResource(String name) {
        ResourceEntry entry = (ResourceEntry) resourceEntries.get(name);
        if (entry != null) {
            if (entry.binaryContent != null)
                return new ByteArrayInputStream(entry.binaryContent);
        }
        return (null);
    }


    /**
     * 如果先前由该类装入器加载并缓存，则查找具有给定名称的类.
     * 如果这个类没有被缓存, 返回<code>null</code>.
     *
     * @param name 返回的资源的名称
     */
    protected Class findLoadedClass0(String name) {
        ResourceEntry entry = (ResourceEntry) resourceEntries.get(name);
        if (entry != null) {
            return entry.loadedClass;
        }
        return (null);  // FIXME - findLoadedResource()
    }


    /**
     * 刷新系统策略文件，以获取最终更改.
     */
    protected void refreshPolicy() {
        try {
            // 策略文件可能已被修改以调整权限, 因此，我们在加载或重新加载上下文时重新加载它
            Policy policy = Policy.getPolicy();
            policy.refresh();
        } catch (AccessControlException e) {
            // 有些策略文件可能会限制这一点, 即使是核心, 因此忽略了这个异常
        }
    }


    /**
     * 过滤器类.
     * 
     * @param name class name
     * @return true if the class should be filtered
     */
    protected boolean filter(String name) {

        if (name == null)
            return false;

        // Looking up the package
        String packageName = null;
        int pos = name.lastIndexOf('.');
        if (pos != -1)
            packageName = name.substring(0, pos);
        else
            return false;

        for (int i = 0; i < packageTriggers.length; i++) {
            if (packageName.startsWith(packageTriggers[i]))
                return true;
        }
        return false;
    }


    /**
     * 验证一个类名. As per SRV.9.7.2, 我们必须制约加载类从J2SE(java.*) 以及servlet API 
     * (javax.servlet.*). 这将增强健壮性并防止一些用户错误(在旧版本的servlet.jar将出现在 /WEB-INF/lib).
     * 
     * @param name class name
     * @return true if the name is valid
     */
    protected boolean validate(String name) {
        if (name == null)
            return false;
        if (name.startsWith("java."))
            return false;

        return true;
    }


    /**
     * 检查指定的JAR文件, 并返回<code>true</code>，如果它不包含任何触发器类.
     *
     * @param jarfile 要验证的JAR文件
     *
     * @exception IOException if an input/output error occurs
     */
    protected boolean validateJarFile(File jarfile)
        throws IOException {

        if (triggers == null)
            return (true);
        JarFile jarFile = new JarFile(jarfile);
        for (int i = 0; i < triggers.length; i++) {
            Class clazz = null;
            try {
                if (parent != null) {
                    clazz = parent.loadClass(triggers[i]);
                } else {
                    clazz = Class.forName(triggers[i]);
                }
            } catch (Throwable t) {
                clazz = null;
            }
            if (clazz == null)
                continue;
            String name = triggers[i].replace('.', '/') + ".class";
            if (log.isDebugEnabled())
                log.debug(" Checking for " + name);
            JarEntry jarEntry = jarFile.getJarEntry(name);
            if (jarEntry != null) {
                log.info("validateJarFile(" + jarfile + 
                    ") - jar not loaded. See Servlet Spec 2.3, "
                    + "section 9.7.2. Offending class: " + name);
                jarFile.close();
                return (false);
            }
        }
        jarFile.close();
        return (true);
    }


    /**
     * 获取URL.
     */
    protected URL getURL(File file)
        throws MalformedURLException {

        File realFile = file;
        try {
            realFile = realFile.getCanonicalFile();
        } catch (IOException e) {
            // Ignore
        }
        return realFile.toURL();
    }


    /**
     * 获取URL.
     */
    protected URL getURI(File file) throws MalformedURLException {
        return jdkCompat.getURI(file);
    }


    /**
     * 删除指定的目录, 包括所有的内容和递归子目录.
     *
     * @param dir 表示要删除的目录的文件对象
     */
    protected static void deleteDir(File dir) {
        String files[] = dir.list();
        if (files == null) {
            files = new String[0];
        }
        for (int i = 0; i < files.length; i++) {
            File file = new File(dir, files[i]);
            if (file.isDirectory()) {
                deleteDir(file);
            } else {
                file.delete();
            }
        }
        dir.delete();
    }
}
