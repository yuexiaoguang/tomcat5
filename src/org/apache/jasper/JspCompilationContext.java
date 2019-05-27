package org.apache.jasper;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Hashtable;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.jsp.tagext.TagInfo;

import org.apache.jasper.compiler.Compiler;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.JspUtil;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.compiler.ServletWriter;
import org.apache.jasper.servlet.JasperLoader;
import org.apache.jasper.servlet.JspServletWrapper;

/**
 * 用于通过JSP引擎使用的各种事物的占位符. 这是每个请求/每个上下文数据结构. 一些实例变量设置在不同的点上.
 *
 * 大部分与路径相关的东西都在这里 - 名称, 版本, 目录,加载的资源, 处理的URI. 
 */
public class JspCompilationContext {

    protected org.apache.commons.logging.Log log =
        org.apache.commons.logging.LogFactory.getLog(JspCompilationContext.class);

    private Hashtable tagFileJarUrls;
    private boolean isPackagedTagFile;

    private String className;
    private String jspUri;
    private boolean isErrPage;
    private String basePackageName;
    private String derivedPackageName;
    private String servletJavaFileName;
    private String javaPath;
    private String classFileName;
    private String contentType;
    private ServletWriter writer;
    private Options options;
    private JspServletWrapper jsw;
    private Compiler jspCompiler;
    private String classPath;

    private String baseURI;
    private String baseOutputDir;
    private String outputDir;
    private ServletContext context;
    private URLClassLoader loader;

    private JspRuntimeContext rctxt;

    private int removed = 0;

    private URLClassLoader jspLoader;
    private URL baseUrl;
    private Class servletClass;

    private boolean isTagFile;
    private boolean protoTypeMode;
    private TagInfo tagInfo;
    private URL tagFileJarUrl;

    // jspURI _must_ be relative to the context
    public JspCompilationContext(String jspUri,
                                 boolean isErrPage,
                                 Options options,
                                 ServletContext context,
                                 JspServletWrapper jsw,
                                 JspRuntimeContext rctxt) {

        this.jspUri = canonicalURI(jspUri);
        this.isErrPage = isErrPage;
        this.options = options;
        this.jsw = jsw;
        this.context = context;

        this.baseURI = jspUri.substring(0, jspUri.lastIndexOf('/') + 1);
        // hack fix for resolveRelativeURI
        if (baseURI == null) {
            baseURI = "/";
        } else if (baseURI.charAt(0) != '/') {
            // 删除斜线, 因为它将与 uriBase 生成一个文件
            baseURI = "/" + baseURI;
        }
        if (baseURI.charAt(baseURI.length() - 1) != '/') {
            baseURI += '/';
        }

        this.rctxt = rctxt;
        this.tagFileJarUrls = new Hashtable();
        this.basePackageName = Constants.JSP_PACKAGE_NAME;
    }

    public JspCompilationContext(String tagfile,
                                 TagInfo tagInfo, 
                                 Options options,
                                 ServletContext context,
                                 JspServletWrapper jsw,
                                 JspRuntimeContext rctxt,
                                 URL tagFileJarUrl) {
        this(tagfile, false, options, context, jsw, rctxt);
        this.isTagFile = true;
        this.tagInfo = tagInfo;
        this.tagFileJarUrl = tagFileJarUrl;
        if (tagFileJarUrl != null) {
            isPackagedTagFile = true;
        }
    }

    /* ==================== Methods to override ==================== */
    
    /** ---------- Class path and loader ---------- */

    /**
     * Java编译器的classpath. 
     */
    public String getClassPath() {
        if( classPath != null )
            return classPath;
        return rctxt.getClassPath();
    }

    /**
     * Java编译器的classpath. 
     */
    public void setClassPath(String classPath) {
        this.classPath = classPath;
    }

    /**
     * 在编译JSP时使用什么类装入器加载类?
     */
    public ClassLoader getClassLoader() {
        if( loader != null )
            return loader;
        return rctxt.getParentClassLoader();
    }

    public void setClassLoader(URLClassLoader loader) {
        this.loader = loader;
    }

    public ClassLoader getJspLoader() {
        if( jspLoader == null ) {
            jspLoader = new JasperLoader
            (new URL[] {baseUrl},
                    getClassLoader(),
                    rctxt.getPermissionCollection(),
                    rctxt.getCodeSource());
        }
        return jspLoader;
    }

    /** ---------- Input/Output  ---------- */
    
    /**
     * 生成代码的输出目录. 输出目录由在选项中提供的暂存目录组成, 加上包名派生的目录.
     */
    public String getOutputDir() {
		if (outputDir == null) {
		    createOutputDir();
		}
        return outputDir;
    }

    /**
     * 创建一个"Compiler"对象, 基于一些初始化参数数据. 这还没有完成. 现在我们只是硬编码创建的实际的编译器. 
     */
    public Compiler createCompiler() throws JasperException {
        if (jspCompiler != null ) {
            return jspCompiler;
        }
        jspCompiler = null;
        if (options.getCompiler() == null) {
            jspCompiler = createCompiler("org.apache.jasper.compiler.JDTCompiler");
            if (jspCompiler == null) {
                jspCompiler = createCompiler("org.apache.jasper.compiler.AntCompiler");
            }
        } else {
            jspCompiler = createCompiler("org.apache.jasper.compiler.AntCompiler");
            if (jspCompiler == null) {
                jspCompiler = createCompiler("org.apache.jasper.compiler.JDTCompiler");
            }
        }
        if (jspCompiler == null) {
            throw new IllegalStateException(Localizer.getMessage("jsp.error.compiler"));
        }
        jspCompiler.init(this, jsw);
        return jspCompiler;
    }

    private Compiler createCompiler(String className) {
        Compiler compiler = null; 
        try {
            compiler = (Compiler) Class.forName(className).newInstance();
        } catch (Throwable t) {
            if (log.isDebugEnabled()) {
                log.debug(Localizer.getMessage("jsp.error.compiler"), t);
            }
        }
        return compiler;
    }
    
    public Compiler getCompiler() {
        return jspCompiler;
    }

    /** ---------- Access resources in the webapp ---------- */

    /** 
     * 使用当前文件为基础得到一个URI相对于这个编译上下文的全部值.
     */
    public String resolveRelativeUri(String uri) {
        // 有时从文件中得到URI的信息, 所以检查根目录deperator字符
        if (uri.startsWith("/") || uri.startsWith(File.separator)) {
            return uri;
        } else {
            return baseURI + uri;
        }
    }

    /**
     * 将资源作为流获取, 相对于上下文实现的含义.
     * @return  null 如果无法找到资源或代表一个 InputStream.
     */
    public java.io.InputStream getResourceAsStream(String res) {
        return context.getResourceAsStream(canonicalURI(res));
    }


    public URL getResource(String res) throws MalformedURLException {
        return context.getResource(canonicalURI(res));
    }

    public Set getResourcePaths(String path) {
        return context.getResourcePaths(canonicalURI(path));
    }

    /** 
     * 获取与编译上下文相关的URI的实际路径.
     */
    public String getRealPath(String path) {
        if (context != null) {
            return context.getRealPath(path);
        }
        return path;
    }

    /**
     * 返回这个编译单元 tag-file-name-to-JAR-file的Map, 它将文件名映射到包含打包的标签文件的JAR文件中.
     *
     * 这个map被填充, 当解析所有引入的标签库的TLD的 tag-file元素时. 
     */
    public Hashtable getTagFileJarUrls() {
        return this.tagFileJarUrls;
    }

    /**
     * 返回创建这个JspCompilationContext的JAR文件中的标签文件, 或 null, 如果这个JspCompilationContext 没有对应于一个标签文件, 或者对应的标签文件没有打包进JAR.
     */
    public URL getTagFileJarUrl() {
        return this.tagFileJarUrl;
    }

    /* ==================== Common implementation ==================== */

    /**
     * 生成类的类名（不包括包名）. 
     */
    public String getServletClassName() {

        if (className != null) {
            return className;
        }

        if (isTagFile) {
            className = tagInfo.getTagClassName();
            int lastIndex = className.lastIndexOf('.');
            if (lastIndex != -1) {
                className = className.substring(lastIndex + 1);
            }
        } else {
            int iSep = jspUri.lastIndexOf('/') + 1;
            className = JspUtil.makeJavaIdentifier(jspUri.substring(iSep));
        }
        return className;
    }

    public void setServletClassName(String className) {
        this.className = className;
    }
    
    /**
     * JSP URI的路径. 注意，这不是一个文件名. 这是JSP文件的基于上下文的URI. 
     */
    public String getJspFile() {
        return jspUri;
    }

    /**
     * 是否是一个errorpage? 
     */
    public boolean isErrorPage() {
        return isErrPage;
    }

    public void setErrorPage(boolean isErrPage) {
        this.isErrPage = isErrPage;
    }

    public boolean isTagFile() {
        return isTagFile;
    }

    public TagInfo getTagInfo() {
        return tagInfo;
    }

    public void setTagInfo(TagInfo tagi) {
        tagInfo = tagi;
    }

    /**
     * True 如果在原型模式下编译一个标签文件.
     * IE只生成带有空方法体的标签处理程序的类代码.
     */
    public boolean isPrototypeMode() {
        return protoTypeMode;
    }

    public void setPrototypeMode(boolean pm) {
        protoTypeMode = pm;
    }

    /**
     * 生成的类的包名由基本包名组成, 用户可设置, 以及派生包名. 导出的包名直接反映了JSP页面文件的文件结构.
     */
    public String getServletPackageName() {
        if (isTagFile()) {
            String className = tagInfo.getTagClassName();
            int lastIndex = className.lastIndexOf('.');
            String pkgName = "";
            if (lastIndex != -1) {
                pkgName = className.substring(0, lastIndex);
            }
            return pkgName;
        } else {
            String dPackageName = getDerivedPackageName();
            if (dPackageName.length() == 0) {
                return basePackageName;
            }
            return basePackageName + '.' + getDerivedPackageName();
        }
    }

    private String getDerivedPackageName() {
        if (derivedPackageName == null) {
            int iSep = jspUri.lastIndexOf('/');
            derivedPackageName = (iSep > 0) ?
                    JspUtil.makeJavaPackage(jspUri.substring(1,iSep)) : "";
        }
        return derivedPackageName;
    }
	    
    /**
     * 生成servlet类的包名.
     */
    public void setServletPackageName(String servletPackageName) {
        this.basePackageName = servletPackageName;
    }

    /**
     * java文件的完整路径名是由servlet产生的. 
     */
    public String getServletJavaFileName() {

        if (servletJavaFileName == null) {
            servletJavaFileName =
		getOutputDir() + getServletClassName() + ".java";
        } else {
            // 确保输出目录存在
            makeOutputDir();
        }
        return servletJavaFileName;
    }

    public void setServletJavaFileName(String servletJavaFileName) {
        this.servletJavaFileName = servletJavaFileName;
    }

    /**
     * 获取此上下文的选项对象. 
     */
    public Options getOptions() {
        return options;
    }

    public ServletContext getServletContext() {
        return context;
    }

    public JspRuntimeContext getRuntimeContext() {
        return rctxt;
    }

    /**
     * java文件相对于工作目录的路径.
     */
    public String getJavaPath() {

        if (javaPath != null) {
            return javaPath;
        }

        if (isTagFile()) {
	    String tagName = tagInfo.getTagClassName();
            javaPath = tagName.replace('.', '/') + ".java";
        } else {
            javaPath = getServletPackageName().replace('.', '/') + '/' +
                       getServletClassName() + ".java";
	}
        return javaPath;
    }

    public String getClassFileName() {

        if (classFileName == null) {
            classFileName = getOutputDir() + getServletClassName() + ".class";
        } else {
            // 确保输出目录存在
            makeOutputDir();
        }
        return classFileName;
    }

    /**
     * 获取此JSP的内容类型.
     *
     * 内容类型包括内容类型和编码.
     */
    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * 生成的servlet在哪里?
     */
    public ServletWriter getWriter() {
        return writer;
    }

    public void setWriter(ServletWriter writer) {
        this.writer = writer;
    }

    /**
     * 获取给定标签库'uri'相关的TLD地 'location'.
     * 
     * @return 两个字符串数组: 第一个元素表示TLD的真正路径. 如果指向TLD的路径指向JAR文件, 然后，第二个元素表示JAR文件中TLD条目的名称.
     * 返回 null， 如果给定的URI与Web应用程序中公开的任何标记库都不关联.
     */
    public String[] getTldLocation(String uri) throws JasperException {
        String[] location = 
            getOptions().getTldLocationsCache().getLocation(uri);
        return location;
    }

    /**
     * 是否保留生成的代码?
     */
    public boolean keepGenerated() {
        return getOptions().getKeepGenerated();
    }

    // ==================== Removal ==================== 

    public void incrementRemoved() {
        if (removed > 1) {
            jspCompiler.removeGeneratedFiles();
            if( rctxt != null )
                rctxt.removeWrapper(jspUri);
        }
        removed++;
    }

    public boolean isRemoved() {
        if (removed > 1 ) {
            return true;
        }
        return false;
    }

    // ==================== Compile and reload ====================
    
    public void compile() throws JasperException, FileNotFoundException {
        createCompiler();
        if (isPackagedTagFile || jspCompiler.isOutDated()) {
            try {
                jspLoader = null;
                jspCompiler.compile();
                jsw.setReload(true);
                jsw.setCompilationException(null);
            } catch (JasperException ex) {
                // 缓存编译异常
                jsw.setCompilationException(ex);
                throw ex;
            } catch (Exception ex) {
                ex.printStackTrace();
                JasperException je = new JasperException(
                            Localizer.getMessage("jsp.error.unable.compile"),
                            ex);
                // 缓存编译异常
                jsw.setCompilationException(je);
                throw je;
            }
        }
    }

    // ==================== Manipulating the class ====================

    public Class load() 
        throws JasperException, FileNotFoundException
    {
        try {
            getJspLoader();
            
            String name;
            if (isTagFile()) {
                name = tagInfo.getTagClassName();
            } else {
                name = getServletPackageName() + "." + getServletClassName();
            }
            servletClass = jspLoader.loadClass(name);
        } catch (ClassNotFoundException cex) {
            throw new JasperException(Localizer.getMessage("jsp.error.unable.load"),
                                      cex);
        } catch (Exception ex) {
            throw new JasperException(Localizer.getMessage("jsp.error.unable.compile"),
                                      ex);
        }
        removed = 0;
        return servletClass;
    }

    // ==================== Private methods ==================== 

    static Object outputDirLock = new Object();

    private void makeOutputDir() {
        synchronized(outputDirLock) {
            File outDirFile = new File(outputDir);
            outDirFile.mkdirs();
        }
    }

    private void createOutputDir() {
        String path = null;
        if (isTagFile()) {
	    String tagName = tagInfo.getTagClassName();
            path = tagName.replace('.', '/');
	    path = path.substring(0, path.lastIndexOf('/'));
        } else {
            path = getServletPackageName().replace('.', '/');
	}

        try {
            // 将servlet或标记处理程序路径添加到目录中
            baseUrl = options.getScratchDir().toURL();
            String outUrlString = baseUrl.toString() + '/' + path;
            URL outUrl = new URL(outUrlString);
            outputDir = outUrl.getFile() + File.separator;
            makeOutputDir();
        } catch (Exception e) {
            throw new IllegalStateException("No output directory: " +
                                            e.getMessage());
        }
    }
    
    private static final boolean isPathSeparator(char c) {
       return (c == '/' || c == '\\');
    }

    private static final String canonicalURI(String s) {
       if (s == null) return null;
       StringBuffer result = new StringBuffer();
       final int len = s.length();
       int pos = 0;
       while (pos < len) {
           char c = s.charAt(pos);
           if ( isPathSeparator(c) ) {
               /*
                * multiple path separators.
                * 'foo///bar' -> 'foo/bar'
                */
               while (pos+1 < len && isPathSeparator(s.charAt(pos+1))) {
                   ++pos;
               }

               if (pos+1 < len && s.charAt(pos+1) == '.') {
                   /*
                    * 路径末端的一个点 - 已经完成.
                    */
                   if (pos+2 >= len) break;

                   switch (s.charAt(pos+2)) {
                       /*
                        * self directory in path
                        * foo/./bar -> foo/bar
                        */
                   case '/':
                   case '\\':
                       pos += 2;
                       continue;

                       /*
                        * 路径中的两个点: 返回上级目录.
                        * foo/bar/../baz -> foo/baz
                        */
                   case '.':
                       // 只要有确切的 _two_ dots.
                       if (pos+3 < len && isPathSeparator(s.charAt(pos+3))) {
                           pos += 3;
                           int separatorPos = result.length()-1;
                           while (separatorPos >= 0 && 
                                  ! isPathSeparator(result
                                                    .charAt(separatorPos))) {
                               --separatorPos;
                           }
                           if (separatorPos >= 0)
                               result.setLength(separatorPos);
                           continue;
                       }
                   }
               }
           }
           result.append(c);
           ++pos;
       }
       return result.toString();
    }
}

