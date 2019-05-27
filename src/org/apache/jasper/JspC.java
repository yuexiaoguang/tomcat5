package org.apache.jasper;

import java.io.BufferedReader;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jasper.compiler.Compiler;
import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldLocationsCache;
import org.apache.jasper.servlet.JspCServletContext;

import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.util.FileUtils;

/**
 * jspc 编译器Shell脚本. 处理与命令行相关联的所有选项，并创建编译上下文，然后根据指定的选项编译.
 *
 * 这个版本可以马上处理从 a _single_ webapp获取的文件, 即可以指定单个docbase.
 *
 * 它可以用作Ant任务:
 * <pre>
 *   &lt;taskdef classname="org.apache.jasper.JspC" name="jasper2" &gt;
 *      &lt;classpath&gt;
 *          &lt;pathelement location="${java.home}/../lib/tools.jar"/&gt;
 *          &lt;fileset dir="${ENV.CATALINA_HOME}/server/lib"&gt;
 *              &lt;include name="*.jar"/&gt;
 *          &lt;/fileset&gt;
 *          &lt;fileset dir="${ENV.CATALINA_HOME}/common/lib"&gt;
 *              &lt;include name="*.jar"/&gt;
 *          &lt;/fileset&gt;
 *          &lt;path refid="myjars"/&gt;
 *       &lt;/classpath&gt;
 *  &lt;/taskdef&gt;
 *
 *  &lt;jasper2 verbose="0"
 *           package="my.package"
 *           uriroot="${webapps.dir}/${webapp.name}"
 *           webXmlFragment="${build.dir}/generated_web.xml"
 *           outputDir="${webapp.dir}/${webapp.name}/WEB-INF/src/my/package" /&gt;
 * </pre>
 */
public class JspC implements Options {

    public static final String DEFAULT_IE_CLASS_ID =
            "clsid:8AD9C840-044E-11D1-B3E9-00805F499D93";

    // Logger
    private static Log log = LogFactory.getLog(JspC.class);

    private static final String SWITCH_VERBOSE = "-v";
    private static final String SWITCH_HELP = "-help";
    private static final String SWITCH_QUIET = "-q";
    private static final String SWITCH_OUTPUT_DIR = "-d";
    private static final String SWITCH_IE_CLASS_ID = "-ieplugin";
    private static final String SWITCH_PACKAGE_NAME = "-p";
    private static final String SWITCH_CACHE = "-cache";
    private static final String SWITCH_CLASS_NAME = "-c";
    private static final String SWITCH_FULL_STOP = "--";
    private static final String SWITCH_COMPILE = "-compile";
    private static final String SWITCH_SOURCE = "-source";
    private static final String SWITCH_TARGET = "-target";
    private static final String SWITCH_URI_BASE = "-uribase";
    private static final String SWITCH_URI_ROOT = "-uriroot";
    private static final String SWITCH_FILE_WEBAPP = "-webapp";
    private static final String SWITCH_WEBAPP_INC = "-webinc";
    private static final String SWITCH_WEBAPP_XML = "-webxml";
    private static final String SWITCH_MAPPED = "-mapped";
    private static final String SWITCH_XPOWERED_BY = "-xpoweredBy";
    private static final String SWITCH_TRIM_SPACES = "-trimSpaces";
    private static final String SWITCH_CLASSPATH = "-classpath";
    private static final String SWITCH_DIE = "-die";
    private static final String SWITCH_POOLING = "-poolingEnabled";
    private static final String SWITCH_ENCODING = "-javaEncoding";
    private static final String SWITCH_SMAP = "-smap";
    private static final String SWITCH_DUMP_SMAP = "-dumpsmap";

    private static final String SHOW_SUCCESS ="-s";
    private static final String LIST_ERRORS = "-l";
    private static final int NO_WEBXML = 0;
    private static final int INC_WEBXML = 10;
    private static final int ALL_WEBXML = 20;
    private static final int DEFAULT_DIE_LEVEL = 1;
    private static final int NO_DIE_LEVEL = 0;

    private static final String[] insertBefore =
    { "</web-app>", "<servlet-mapping>", "<session-config>",
      "<mime-mapping>", "<welcome-file-list>", "<error-page>", "<taglib>",
      "<resource-env-ref>", "<resource-ref>", "<security-constraint>",
      "<login-config>", "<security-role>", "<env-entry>", "<ejb-ref>",
      "<ejb-local-ref>" };

    private static int die;
    private String classPath = null;
    private URLClassLoader loader = null;
    private boolean trimSpaces = false;
    private boolean genStringAsCharArray = false;
    private boolean xpoweredBy;
    private boolean mappedFile = false;
    private boolean poolingEnabled = true;
    private File scratchDir;
    private String ieClassId = DEFAULT_IE_CLASS_ID;
    private String targetPackage;
    private String targetClassName;
    private String uriBase;
    private String uriRoot;
    private Project project;
    private int dieLevel;
    private boolean helpNeeded = false;
    private boolean compile = false;
    private boolean smapSuppressed = true;
    private boolean smapDumped = false;
    private boolean caching = true;
    private Map cache = new HashMap();

    private String compiler = null;

    private String compilerTargetVM = "1.4";
    private String compilerSourceVM = "1.4";

    private boolean classDebugInfo = true;

    /**
     * 如果出现编译错误, 抛出一个异常.
     * 默认是 true 保存旧的行为.
     */
    private boolean failOnError = true;

    /**
     * 要作为JSP文件处理的文件扩展名.
     * 默认列表是 .jsp 和 .jspx.
     */
    private List extensions;

    private Vector pages = new Vector();
    private boolean errorOnUseBeanInvalidClassAttribute = true;

    /**
     * java文件的编码.  默认是 UTF-8.  Added per bugzilla 19622.
     */
    private String javaEncoding = "UTF-8";

    // web.xml 片段的生成
    private String webxmlFile;
    private int webxmlLevel;
    private boolean addWebXmlMappings = false;

    private Writer mapout;
    private CharArrayWriter servletout;
    private CharArrayWriter mappingout;

    private JspCServletContext context;

    // 保持一个假的 JspRuntimeContext 编译标签文件
    private JspRuntimeContext rctxt;

    /**
     * TLD位置的缓存
     */
    private TldLocationsCache tldLocationsCache = null;

    private JspConfig jspConfig = null;
    private TagPluginManager tagPluginManager = null;

    private boolean verbose = false;
    private boolean listErrors = false;
    private boolean showSuccess = false;
    private int argPos;
    private boolean fullstop = false;
    private String args[];

    public static void main(String arg[]) {
        if (arg.length == 0) {
            System.out.println(Localizer.getMessage("jspc.usage"));
        } else {
            try {
                JspC jspc = new JspC();
                jspc.setArgs(arg);
                if (jspc.helpNeeded) {
                    System.out.println(Localizer.getMessage("jspc.usage"));
                } else {
                    jspc.execute();
                }
            } catch (JasperException je) {
                System.err.println(je);
                //System.err.println(je.getMessage());
                if (die != NO_DIE_LEVEL) {
                    System.exit(die);
                }
            }
        }
    }

    public void setArgs(String[] arg) throws JasperException {
        args = arg;
        String tok;

        dieLevel = NO_DIE_LEVEL;
        die = dieLevel;

        while ((tok = nextArg()) != null) {
            if (tok.equals(SWITCH_VERBOSE)) {
                verbose = true;
                showSuccess = true;
                listErrors = true;
            } else if (tok.equals(SWITCH_OUTPUT_DIR)) {
                tok = nextArg();
                setOutputDir( tok );
            } else if (tok.equals(SWITCH_PACKAGE_NAME)) {
                targetPackage = nextArg();
            } else if (tok.equals(SWITCH_COMPILE)) {
                compile=true;
            } else if (tok.equals(SWITCH_CLASS_NAME)) {
                targetClassName = nextArg();
            } else if (tok.equals(SWITCH_URI_BASE)) {
                uriBase=nextArg();
            } else if (tok.equals(SWITCH_URI_ROOT)) {
                setUriroot( nextArg());
            } else if (tok.equals(SWITCH_FILE_WEBAPP)) {
                setUriroot( nextArg());
            } else if ( tok.equals( SHOW_SUCCESS ) ) {
                showSuccess = true;
            } else if ( tok.equals( LIST_ERRORS ) ) {
                listErrors = true;
            } else if (tok.equals(SWITCH_WEBAPP_INC)) {
                webxmlFile = nextArg();
                if (webxmlFile != null) {
                    webxmlLevel = INC_WEBXML;
                }
            } else if (tok.equals(SWITCH_WEBAPP_XML)) {
                webxmlFile = nextArg();
                if (webxmlFile != null) {
                    webxmlLevel = ALL_WEBXML;
                }
            } else if (tok.equals(SWITCH_MAPPED)) {
                mappedFile = true;
            } else if (tok.equals(SWITCH_XPOWERED_BY)) {
                xpoweredBy = true;
            } else if (tok.equals(SWITCH_TRIM_SPACES)) {
                setTrimSpaces(true);
            } else if (tok.equals(SWITCH_CACHE)) {
                tok = nextArg();
                if ("false".equals(tok)) {
                    caching = false;
                } else {
                    caching = true;
                }            
            } else if (tok.equals(SWITCH_CLASSPATH)) {
                setClassPath(nextArg());
            } else if (tok.startsWith(SWITCH_DIE)) {
                try {
                    dieLevel = Integer.parseInt(
                        tok.substring(SWITCH_DIE.length()));
                } catch (NumberFormatException nfe) {
                    dieLevel = DEFAULT_DIE_LEVEL;
                }
                die = dieLevel;
            } else if (tok.equals(SWITCH_HELP)) {
                helpNeeded = true;
            } else if (tok.equals(SWITCH_POOLING)) {
                tok = nextArg();
                if ("false".equals(tok)) {
                    poolingEnabled = false;
                } else {
                    poolingEnabled = true;
                }
            } else if (tok.equals(SWITCH_ENCODING)) {
                setJavaEncoding(nextArg());
            } else if (tok.equals(SWITCH_SOURCE)) {
                setCompilerSourceVM(nextArg());
            } else if (tok.equals(SWITCH_TARGET)) {
                setCompilerTargetVM(nextArg());
            } else if (tok.equals(SWITCH_SMAP)) {
                smapSuppressed = false;
            } else if (tok.equals(SWITCH_DUMP_SMAP)) {
                smapDumped = true;
            } else {
                if (tok.startsWith("-")) {
                    throw new JasperException("Unrecognized option: " + tok +
                        ".  Use -help for help.");
                }
                if (!fullstop) {
                    argPos--;
                }
                // 开始将其余部分作为JSP页面处理
                break;
            }
        }

        // 将所有额外参数添加到文件列表中
        while( true ) {
            String file = nextFile();
            if( file==null ) break;
            pages.addElement( file );
        }
    }

    public boolean getKeepGenerated() {
        // isn't this why we are running jspc?
        return true;
    }

    public boolean getTrimSpaces() {
        return trimSpaces;
    }

    public void setTrimSpaces(boolean ts) {
        this.trimSpaces = ts;
    }

    public boolean isPoolingEnabled() {
        return poolingEnabled;
    }

    public void setPoolingEnabled(boolean poolingEnabled) {
        this.poolingEnabled = poolingEnabled;
    }

    public boolean isXpoweredBy() {
        return xpoweredBy;
    }

    public void setXpoweredBy(boolean xpoweredBy) {
        this.xpoweredBy = xpoweredBy;
    }

    public boolean getErrorOnUseBeanInvalidClassAttribute() {
        return errorOnUseBeanInvalidClassAttribute;
    }

    public void setErrorOnUseBeanInvalidClassAttribute(boolean b) {
        errorOnUseBeanInvalidClassAttribute = b;
    }

    public int getTagPoolSize() {
        return Constants.MAX_POOL_SIZE;
    }

    /**
     * 是否支持HTML 映射 servlets?
     */
    public boolean getMappedFile() {
        return mappedFile;
    }

    // 离线编译, 不需要安全管理器
    public Object getProtectionDomain() {
        return null;
    }

    public boolean getSendErrorToClient() {
        // 暗指发送到 System.err
        return true;
    }

    public void setClassDebugInfo( boolean b ) {
        classDebugInfo=b;
    }

    public boolean getClassDebugInfo() {
        // 用调试信息编译
        return classDebugInfo;
    }

     /**
      * @see Options#isCaching()
     */
    public boolean isCaching() {
        return caching;
    }

    /**
     * @see Options#isCaching()
     */
    public void setCaching(boolean caching) {
        this.caching = caching;
    }

    /**
     * @see Options#getCache()
     */
    public Map getCache() {
        return cache;
    }

    /**
     * 后台编译间隔, 秒
     */
    public int getCheckInterval() {
        return 0;
    }

    /**
     * 修改测试间隔.
     */
    public int getModificationTestInterval() {
        return 0;
    }

    /**
     * 是否在开发模式中使用Jasper?
     */
    public boolean getDevelopment() {
        return false;
    }

    /**
     * 是否阻止JSR45调试的SMAP信息的生成
     */
    public boolean isSmapSuppressed() {
        return smapSuppressed;
    }

    /**
     * Set smapSuppressed flag.
     */
    public void setSmapSuppressed(boolean smapSuppressed) {
        this.smapSuppressed = smapSuppressed;
    }

    
    /**
     * JSR45调试的SMAP信息是否保存到文件中
     */
    public boolean isSmapDumped() {
        return smapDumped;
    }

    /**
     * JSR45调试的SMAP信息是否保存到文件中
     */
    public void setSmapDumped(boolean smapDumped) {
        this.smapDumped = smapDumped;
    }

    
    /**
     * 文本字符串是否生成为char 数组, 在某些情况下可以提高性能.
     *
     * @param genStringAsCharArray true 如果文本字符串被生成为char 数组, 否则false
     */
    public void setGenStringAsCharArray(boolean genStringAsCharArray) {
        this.genStringAsCharArray = genStringAsCharArray;
    }

    /**
     * 是否将文本字符串生成为 char数组.
     *
     * @return true 如果文本字符串被生成为char 数组, 否则false
     */
    public boolean genStringAsCharArray() {
        return genStringAsCharArray;
    }

    /**
     * 设置class-id值发送给Internet Explorer, 当使用<jsp:plugin>标签时.
     *
     * @param ieClassId Class-id value
     */
    public void setIeClassId(String ieClassId) {
        this.ieClassId = ieClassId;
    }

    /**
     * 获取使用<jsp:plugin>标签时发送给Internet Explorer的class-id值.
     *
     * @return Class-id value
     */
    public String getIeClassId() {
        return ieClassId;
    }

    public File getScratchDir() {
        return scratchDir;
    }

    public Class getJspCompilerPlugin() {
       // 不编译, so this is meanlingless
        return null;
    }

    public String getJspCompilerPath() {
       // 不编译, so this is meanlingless
        return null;
    }

    /**
     * 使用的编译器.
     */
    public String getCompiler() {
        return compiler;
    }

    public void setCompiler(String c) {
        compiler=c;
    }

    public String getCompilerTargetVM() {
        return compilerTargetVM;
    }

    public void setCompilerTargetVM(String vm) {
        compilerTargetVM = vm;
    }

     public String getCompilerSourceVM() {
         return compilerSourceVM;
     }
        
    public void setCompilerSourceVM(String vm) {
        compilerSourceVM = vm;
    }

    public TldLocationsCache getTldLocationsCache() {
        return tldLocationsCache;
    }

    /**
     * 返回java文件使用的编码. 默认是 UTF-8.
     *
     * @return String The encoding
     */
    public String getJavaEncoding() {
        return javaEncoding;
    }

    /**
     * 设置java文件使用的编码.
     *
     * @param encodingName 名称, 即 "UTF-8"
     */
    public void setJavaEncoding(String encodingName) {
        javaEncoding = encodingName;
    }

    public boolean getFork() {
        return false;
    }

    public String getClassPath() {
        if( classPath != null )
            return classPath;
        return System.getProperty("java.class.path");
    }

    public void setClassPath(String s) {
        classPath=s;
    }

    /**
     * 返回被视为JSP文件的文件扩展名列表.
     *
     * @return 扩展名列表
     */
    public List getExtensions() {
        return extensions;
    }

    /**
     * 将给定的文件扩展名添加到作为JSP文件处理的扩展名列表中.
     *
     * @param extension 要添加的扩展名, 即 "myjsp"
     */
    protected void addExtension(final String extension) {
        if(extension != null) {
            if(extensions == null) {
                extensions = new Vector();
            }
            extensions.add(extension);
        }
    }

    /**
     * 设置项目.
     *
     * @param theProject The project
     */
    public void setProject(final Project theProject) {
        project = theProject;
    }

    /**
     * 返回项目: 可能是null, 如果不在Ant项目中运行.
     *
     * @return The project
     */
    public Project getProject() {
        return project;
    }

    /**
     * webapp基础目录. 用于生成类名和解析包含
     */
    public void setUriroot( String s ) {
        if( s==null ) {
            uriRoot = s;
            return;
        }
        try {
            uriRoot = resolveFile(s).getCanonicalPath();
        } catch( Exception ex ) {
            uriRoot = s;
        }
    }

    /**
     * 解析要处理的JSP文件的逗号分隔列表.
     *
     * <p>每个文件被解释相对于 uriroot, 除非它是绝对的,即它必须以 uriroot开头.
     *
     * @param jspFiles 要处理的JSP文件的逗号分隔列表
     */
    public void setJspFiles(String jspFiles) {
        StringTokenizer tok = new StringTokenizer(jspFiles, " ,");
        while (tok.hasMoreTokens()) {
            pages.addElement(tok.nextToken());
        }
    }

    public void setCompile( boolean b ) {
        compile=b;
    }

    public void setVerbose( int level ) {
        if (level > 0) {
            verbose = true;
            showSuccess = true;
            listErrors = true;
        }
    }

    public void setValidateXml( boolean b ) {
        org.apache.jasper.xmlparser.ParserUtils.validating=b;
    }

    public void setListErrors( boolean b ) {
        listErrors = b;
    }

    public void setOutputDir( String s ) {
        if( s!= null ) {
            scratchDir = resolveFile(s).getAbsoluteFile();
        } else {
            scratchDir=null;
        }
    }

    public void setPackage( String p ) {
        targetPackage=p;
    }

    /**
     * 生成文件的类名(不包括包名).
     * 只能在单个文件转换时使用.
     * XXX 需要这个特性吗?
     */
    public void setClassName( String p ) {
        targetClassName=p;
    }

    /**
     * 生成一个类的定义的web.xml片段File.
     */
    public void setWebXmlFragment( String s ) {
        webxmlFile=resolveFile(s).getAbsolutePath();
        webxmlLevel=INC_WEBXML;
    }

    /**
     * 生成一个完整的类的定义的web.xml文件.
     */
    public void setWebXml( String s ) {
        webxmlFile=resolveFile(s).getAbsolutePath();
        webxmlLevel=ALL_WEBXML;
    }

    public void setAddWebXmlMappings(boolean b) {
        addWebXmlMappings = b;
    }

    /**
     * 设置在编译错误时引发异常的选项.
     */
    public void setFailOnError(final boolean b) {
        failOnError = b;
    }

    public boolean getFailOnError() {
        return failOnError;
    }

    /**
     * 获取在web.xml中指定的JSP的配置信息.
     */
    public JspConfig getJspConfig() {
        return jspConfig;
    }

    public TagPluginManager getTagPluginManager() {
        return tagPluginManager;
    }

    public void generateWebMapping( String file, JspCompilationContext clctxt )
        throws IOException
    {
        String className = clctxt.getServletClassName();
        String packageName = clctxt.getServletPackageName();

        String thisServletName;
        if  ("".equals(packageName)) {
            thisServletName = className;
        } else {
            thisServletName = packageName + '.' + className;
        }

        if (servletout != null) {
            servletout.write("\n    <servlet>\n        <servlet-name>");
            servletout.write(thisServletName);
            servletout.write("</servlet-name>\n        <servlet-class>");
            servletout.write(thisServletName);
            servletout.write("</servlet-class>\n    </servlet>\n");
        }
        if (mappingout != null) {
            mappingout.write("\n    <servlet-mapping>\n        <servlet-name>");
            mappingout.write(thisServletName);
            mappingout.write("</servlet-name>\n        <url-pattern>");
            mappingout.write(file.replace('\\', '/'));
            mappingout.write("</url-pattern>\n    </servlet-mapping>\n");
        }
    }

    /**
     * 包括webapp的web.xml中生成的web.xml.
     */
    protected void mergeIntoWebXml() throws IOException {

        File webappBase = new File(uriRoot);
        File webXml = new File(webappBase, "WEB-INF/web.xml");
        File webXml2 = new File(webappBase, "WEB-INF/web2.xml");
        String insertStartMarker =
            Localizer.getMessage("jspc.webinc.insertStart");
        String insertEndMarker =
            Localizer.getMessage("jspc.webinc.insertEnd");

        BufferedReader reader = new BufferedReader(new FileReader(webXml));
        BufferedReader fragmentReader =
            new BufferedReader(new FileReader(webxmlFile));
        PrintWriter writer = new PrintWriter(new FileWriter(webXml2));

        // 插入<servlet>和<servlet-mapping>定义
        int pos = -1;
        String line = null;
        while (true) {
            line = reader.readLine();
            if (line == null) {
                break;
            }
            // 跳过以前生成的任何东西, 通过 JSPC
            if (line.indexOf(insertStartMarker) >= 0) {
                while (true) {
                    line = reader.readLine();
                    if (line == null) {
                        return;
                    }
                    if (line.indexOf(insertEndMarker) >= 0) {
                        line = reader.readLine();
                        line = reader.readLine();
                        if (line == null) {
                            return;
                        }
                        break;
                    }
                }
            }
            for (int i = 0; i < insertBefore.length; i++) {
                pos = line.indexOf(insertBefore[i]);
                if (pos >= 0)
                    break;
            }
            if (pos >= 0) {
                writer.print(line.substring(0, pos));
                break;
            } else {
                writer.println(line);
            }
        }

        writer.println(insertStartMarker);
        while (true) {
            String line2 = fragmentReader.readLine();
            if (line2 == null) {
                writer.println();
                break;
            }
            writer.println(line2);
        }
        writer.println(insertEndMarker);
        writer.println();

        for (int i = 0; i < pos; i++) {
            writer.print(" ");
        }
        writer.println(line.substring(pos));

        while (true) {
            line = reader.readLine();
            if (line == null) {
                break;
            }
            writer.println(line);
        }
        writer.close();

        reader.close();
        fragmentReader.close();

        FileInputStream fis = new FileInputStream(webXml2);
        FileOutputStream fos = new FileOutputStream(webXml);

        byte buf[] = new byte[512];
        while (true) {
            int n = fis.read(buf);
            if (n < 0) {
                break;
            }
            fos.write(buf, 0, n);
        }

        fis.close();
        fos.close();

        webXml2.delete();
        (new File(webxmlFile)).delete();

    }

    private void processFile(String file)
        throws JasperException
    {
        ClassLoader originalClassLoader = null;

        try {
            // set up a scratch/output dir if none is provided
            if (scratchDir == null) {
                String temp = System.getProperty("java.io.tmpdir");
                if (temp == null) {
                    temp = "";
                }
                scratchDir = new File(new File(temp).getAbsolutePath());
            }

            String jspUri=file.replace('\\','/');
            JspCompilationContext clctxt = new JspCompilationContext
                ( jspUri, false,  this, context, null, rctxt );

            /* Override the defaults */
            if ((targetClassName != null) && (targetClassName.length() > 0)) {
                clctxt.setServletClassName(targetClassName);
                targetClassName = null;
            }
            if (targetPackage != null) {
                clctxt.setServletPackageName(targetPackage);
            }

            originalClassLoader = Thread.currentThread().getContextClassLoader();
            if( loader==null ) {
                initClassLoader( clctxt );
            }
            Thread.currentThread().setContextClassLoader(loader);

            clctxt.setClassLoader(loader);
            clctxt.setClassPath(classPath);

            Compiler clc = clctxt.createCompiler();

            // 如果设置了编译, 生成 .java 和 .class, 如果 .jsp 文件比 .class 文件更新;
            // 否则只生成 .java, 如果 .jsp 文件比 .java 文件更新
            if( clc.isOutDated(compile) ) {
                clc.compile(compile, true);
            }

            // Generate mapping
            generateWebMapping( file, clctxt );
            if ( showSuccess ) {
                log.info( "Built File: " + file );
            }

        } catch (JasperException je) {
            Throwable rootCause = je;
            while (rootCause instanceof JasperException
                    && ((JasperException) rootCause).getRootCause() != null) {
                rootCause = ((JasperException) rootCause).getRootCause();
            }
            if (rootCause != je) {
                log.error(Localizer.getMessage("jspc.error.generalException",
                                               file),
                          rootCause);
            }

            // Bugzilla 35114.
            if(getFailOnError()) {
                throw je;
            } else {
                log.error(je.getMessage());
            }

        } catch (Exception e) {
            if ((e instanceof FileNotFoundException) && log.isWarnEnabled()) {
                log.warn(Localizer.getMessage("jspc.error.fileDoesNotExist",
                                              e.getMessage()));
            }
            throw new JasperException(e);
        } finally {
            if(originalClassLoader != null) {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        }
    }

    /**
     * 找到web应用中所有的jsp 文件的位置. 如果没有指定明确的JSP时使用.
     */
    public void scanFiles( File base ) throws JasperException {
        Stack dirs = new Stack();
        dirs.push(base);

        // 确保默认扩展名始终包括在内
        if ((getExtensions() == null) || (getExtensions().size() < 2)) {
            addExtension("jsp");
            addExtension("jspx");
        }

        while (!dirs.isEmpty()) {
            String s = dirs.pop().toString();
            File f = new File(s);
            if (f.exists() && f.isDirectory()) {
                String[] files = f.list();
                String ext;
                for (int i = 0; (files != null) && i < files.length; i++) {
                    File f2 = new File(s, files[i]);
                    if (f2.isDirectory()) {
                        dirs.push(f2.getPath());
                    } else {
                        String path = f2.getPath();
                        String uri = path.substring(uriRoot.length());
                        ext = files[i].substring(files[i].lastIndexOf('.') +1);
                        if (getExtensions().contains(ext) ||
                            jspConfig.isJspPage(uri)) {
                            pages.addElement(path);
                        }
                    }
                }
            }
        }
    }

    public void execute() throws JasperException {

        try {
            if (uriRoot == null) {
                if( pages.size() == 0 ) {
                    throw new JasperException(
                        Localizer.getMessage("jsp.error.jspc.missingTarget"));
                }
                String firstJsp=(String)pages.elementAt( 0 );
                File firstJspF = new File( firstJsp );
                if (!firstJspF.exists()) {
                    throw new JasperException(
                        Localizer.getMessage("jspc.error.fileDoesNotExist",
                                             firstJsp));
                }
                locateUriRoot( firstJspF );
            }

            if (uriRoot == null) {
                throw new JasperException(
                    Localizer.getMessage("jsp.error.jspc.no_uriroot"));
            }

            if( context==null ) {
                initServletContext();
            }

            // 没有明确的页面, 将处理web应用中所有的 .jsp
            if (pages.size() == 0) {
                scanFiles( new File( uriRoot ));
            }

            File uriRootF = new File(uriRoot);
            if (!uriRootF.exists() || !uriRootF.isDirectory()) {
                throw new JasperException(
                    Localizer.getMessage("jsp.error.jspc.uriroot_not_dir"));
            }

            initWebXml();

            Enumeration e = pages.elements();
            while (e.hasMoreElements()) {
                String nextjsp = e.nextElement().toString();
                File fjsp = new File(nextjsp);
                if (!fjsp.isAbsolute()) {
                    fjsp = new File(uriRootF, nextjsp);
                }
                if (!fjsp.exists()) {
                    if (log.isWarnEnabled()) {
                        log.warn
                            (Localizer.getMessage
                             ("jspc.error.fileDoesNotExist", fjsp.toString()));
                    }
                    continue;
                }
                String s = fjsp.getAbsolutePath();
                if (s.startsWith(uriRoot)) {
                    nextjsp = s.substring(uriRoot.length());
                }
                if (nextjsp.startsWith("." + File.separatorChar)) {
                    nextjsp = nextjsp.substring(2);
                }
                processFile(nextjsp);
            }

            completeWebXml();

            if (addWebXmlMappings) {
                mergeIntoWebXml();
            }

        } catch (IOException ioe) {
            throw new JasperException(ioe);

        } catch (JasperException je) {
            Throwable rootCause = je;
            while (rootCause instanceof JasperException
                    && ((JasperException) rootCause).getRootCause() != null) {
                rootCause = ((JasperException) rootCause).getRootCause();
            }
            if (rootCause != je) {
                rootCause.printStackTrace();
            }
            throw je;
        } finally {
            if (loader != null) {
                LogFactory.release(loader);
            }
        }
    }

    // ==================== Private utility methods ====================

    private String nextArg() {
        if ((argPos >= args.length)
            || (fullstop = SWITCH_FULL_STOP.equals(args[argPos]))) {
            return null;
        } else {
            return args[argPos++];
        }
    }

    private String nextFile() {
        if (fullstop) argPos++;
        if (argPos >= args.length) {
            return null;
        } else {
            return args[argPos++];
        }
    }

    private void initWebXml() {
        try {
            if (webxmlLevel >= INC_WEBXML) {
                File fmapings = new File(webxmlFile);
                mapout = new FileWriter(fmapings);
                servletout = new CharArrayWriter();
                mappingout = new CharArrayWriter();
            } else {
                mapout = null;
                servletout = null;
                mappingout = null;
            }
            if (webxmlLevel >= ALL_WEBXML) {
                mapout.write(Localizer.getMessage("jspc.webxml.header"));
                mapout.flush();
            } else if ((webxmlLevel>= INC_WEBXML) && !addWebXmlMappings) {
                mapout.write(Localizer.getMessage("jspc.webinc.header"));
                mapout.flush();
            }
        } catch (IOException ioe) {
            mapout = null;
            servletout = null;
            mappingout = null;
        }
    }

    private void completeWebXml() {
        if (mapout != null) {
            try {
                servletout.writeTo(mapout);
                mappingout.writeTo(mapout);
                if (webxmlLevel >= ALL_WEBXML) {
                    mapout.write(Localizer.getMessage("jspc.webxml.footer"));
                } else if ((webxmlLevel >= INC_WEBXML) && !addWebXmlMappings) {
                    mapout.write(Localizer.getMessage("jspc.webinc.footer"));
                }
                mapout.close();
            } catch (IOException ioe) {
                // noting to do if it fails since we are done with it
            }
        }
    }

    private void initServletContext() {
        try {
            context =new JspCServletContext
                (new PrintWriter(System.out),
                 new URL("file:" + uriRoot.replace('\\','/') + '/'));
            tldLocationsCache = new TldLocationsCache(context, true);
        } catch (MalformedURLException me) {
            System.out.println("**" + me);
        }
        rctxt = new JspRuntimeContext(context, this);
        jspConfig = new JspConfig(context);
        tagPluginManager = new TagPluginManager(context);
    }

    /**
     * 初始化的类装载器, 如果给定上下文需要编译.
     *
     * @param clctxt 编译环境
     * @throws IOException 如果出现错误
     */
    private void initClassLoader(JspCompilationContext clctxt)
        throws IOException {

        classPath = getClassPath();

        ClassLoader jspcLoader = getClass().getClassLoader();
        if (jspcLoader instanceof AntClassLoader) {
            classPath += File.pathSeparator
                + ((AntClassLoader) jspcLoader).getClasspath();
        }

        // 将classPath 转换为 URL
        ArrayList urls = new ArrayList();
        StringTokenizer tokenizer = new StringTokenizer(classPath,
                                                        File.pathSeparator);
        while (tokenizer.hasMoreTokens()) {
            String path = tokenizer.nextToken();
            try {
                File libFile = new File(path);
                urls.add(libFile.toURL());
            } catch (IOException ioe) {
                // Failing a toCanonicalPath on a file that
                // exists() should be a JVM regression test,
                // therefore we have permission to freak uot
                throw new RuntimeException(ioe.toString());
            }
        }

        File webappBase = new File(uriRoot);
        if (webappBase.exists()) {
            File classes = new File(webappBase, "/WEB-INF/classes");
            try {
                if (classes.exists()) {
                    classPath = classPath + File.pathSeparator
                        + classes.getCanonicalPath();
                    urls.add(classes.getCanonicalFile().toURL());
                }
            } catch (IOException ioe) {
                // failing a toCanonicalPath on a file that
                // exists() should be a JVM regression test,
                // therefore we have permission to freak out
                throw new RuntimeException(ioe.toString());
            }
            File lib = new File(webappBase, "/WEB-INF/lib");
            if (lib.exists() && lib.isDirectory()) {
                String[] libs = lib.list();
                for (int i = 0; i < libs.length; i++) {
                    if( libs[i].length() <5 ) continue;
                    String ext=libs[i].substring( libs[i].length() - 4 );
                    if (! ".jar".equalsIgnoreCase(ext)) {
                        if (".tld".equalsIgnoreCase(ext)) {
                            log.warn("TLD files should not be placed in "
                                     + "/WEB-INF/lib");
                        }
                        continue;
                    }
                    try {
                        File libFile = new File(lib, libs[i]);
                        classPath = classPath + File.pathSeparator
                            + libFile.getAbsolutePath();
                        urls.add(libFile.getAbsoluteFile().toURL());
                    } catch (IOException ioe) {
                        // failing a toCanonicalPath on a file that
                        // exists() should be a JVM regression test,
                        // therefore we have permission to freak out
                        throw new RuntimeException(ioe.toString());
                    }
                }
            }
        }

        // What is this ??
        urls.add(new File(clctxt.getRealPath("/")).getCanonicalFile().toURL());

        URL urlsA[]=new URL[urls.size()];
        urls.toArray(urlsA);
        loader = new URLClassLoader(urlsA, this.getClass().getClassLoader());

    }

    /**
     * 找到 WEB-INF 文件夹, 通过在目录树中查找.
     * 如果没有显式的设置docBase将使用, 但只有文件.
     * XXX 也许应该要求 docbase.
     */
    private void locateUriRoot( File f ) {
        String tUriBase = uriBase;
        if (tUriBase == null) {
            tUriBase = "/";
        }
        try {
            if (f.exists()) {
                f = new File(f.getAbsolutePath());
                while (f != null) {
                    File g = new File(f, "WEB-INF");
                    if (g.exists() && g.isDirectory()) {
                        uriRoot = f.getCanonicalPath();
                        uriBase = tUriBase;
                        if (log.isInfoEnabled()) {
                            log.info(Localizer.getMessage(
                                        "jspc.implicit.uriRoot",
                                        uriRoot));
                        }
                        break;
                    }
                    if (f.exists() && f.isDirectory()) {
                        tUriBase = "/" + f.getName() + "/" + tUriBase;
                    }

                    String fParent = f.getParent();
                    if (fParent == null) {
                        break;
                    } else {
                        f = new File(fParent);
                    }
                    // 如果没有可以接受的候选, uriRoot 将保持 null 表示 CompilerContext 使用当前 working/user 目录.
                }
                if (uriRoot != null) {
                    File froot = new File(uriRoot);
                    uriRoot = froot.getCanonicalPath();
                }
            }
        } catch (IOException ioe) {
            // 因为这是一个可选的默认值, uriRoot是null具有非错误意义, 可以直接通过
        }
    }

    /**
     * 在Ant和命令行的情况下, 正确的解析相对或绝对路径.  如果Ant开始处理我们, 我们应该使用当前项目的basedir 解析相对路径.
     *
     * See Bugzilla 35571.
     *
     * @param s 文件
     * @return 解析的文件
     */
     protected File resolveFile(final String s) {
         if(getProject() == null) {
             // Note FileUtils.getFileUtils replaces FileUtils.newFileUtils in Ant 1.6.3
             return FileUtils.newFileUtils().resolveFile(null, s);
         } else {
             return FileUtils.newFileUtils().resolveFile(getProject().getBaseDir(), s);
         }
     }
}
