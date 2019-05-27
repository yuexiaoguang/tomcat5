package org.apache.jasper;

import java.io.File;
import java.util.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.apache.jasper.compiler.TldLocationsCache;
import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.xmlparser.ParserUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 用于保存特定于JSP引擎的所有init参数. 
 */
public final class EmbeddedServletOptions implements Options {
    
    private Log log = LogFactory.getLog(EmbeddedServletOptions.class);
    
    private Properties settings = new Properties();
    
    /**
     * 在开发模式中是否使用Jasper?
     */
    private boolean development = true;
    
    /**
     * Should Ant fork its java compiles of JSP pages.
     */
    public boolean fork = true;
    
    /**
     * 是否保留生成的Java文件?
     */
    private boolean keepGenerated = true;
    
    /**
     * 指令或操作之间的空格是否应该被删除?
     */
    private boolean trimSpaces = false;
    
    /**
     * 是否启用标签处理程序池.
     */
    private boolean isPoolingEnabled = true;
    
    /**
     * 是否支持 "mapped"文件? 这将生成servlet，每行的JSP文件有一个打印语句.
     * 似乎是一个非常好的调试功能.
     */
    private boolean mappedFile = true;
    
    /**
     * 是否要堆栈跟踪，并显示在客户端浏览器中? 如果是false, 如果标准错误被重定向, 消息将转到标准错误流或日志文件. 
     */
    private boolean sendErrorToClient = false;
    
    /**
     * 是否在类文件中包含调试信息?
     */
    private boolean classDebugInfo = true;
    
    /**
     * 后台编译线程检查间隔, 以秒为单位.
     */
    private int checkInterval = 0;
    
    /**
     * 是否阻止JSR45调试的SMAP信息的生成?
     */
    private boolean isSmapSuppressed = false;
    
    /**
     * JSR45调试的SMAP信息是否保存到文件中?
     */
    private boolean isSmapDumped = false;
    
    /**
     * 文本字符串是否被生成为char数组?
     */
    private boolean genStringAsCharArray = false;
    
    private boolean errorOnUseBeanInvalidClassAttribute = true;
    
    /**
     * 生成的servlet所在的目录
     */
    private File scratchDir;
    
    /**
     * 需要有IE 4和5版本. 可以从initParam设置, 所以如果它在未来发生变化, 就是要有一个ieClassId="<value>"类型的jsp initParam
     */
    private String ieClassId = "clsid:8AD9C840-044E-11D1-B3E9-00805F499D93";
    
    /**
     * 当编译生成servlet时, 应该使用的classpath?
     */
    private String classpath = null;
    
    /**
     * 使用的编译器.
     */
    private String compiler = null;
    
    /**
     * 编译器目标 VM.
     */
    private String compilerTargetVM = "1.5";
    
    /**
     * 编译器源 VM.
     */
    private String compilerSourceVM = "1.5";
    
    /**
     * TLD位置的缓存
     */
    private TldLocationsCache tldLocationsCache = null;
    
    /**
     * JSP的配置信息
     */
    private JspConfig jspConfig = null;
    
    /**
     * TagPluginManager
     */
    private TagPluginManager tagPluginManager = null;
    
    /**
     * java平台的编码生成的JSP页面servlet.
     */
    private String javaEncoding = "UTF8";
    
    /**
     * 修改测试间隔.
     */
    private int modificationTestInterval = 4;
    
    /**
     * 是否生成 X-Powered-By 响应头?
     */
    private boolean xpoweredBy;
    
    public String getProperty(String name ) {
        return settings.getProperty( name );
    }
    
    public void setProperty(String name, String value ) {
        if (name != null && value != null){ 
            settings.setProperty( name, value );
        }
    }
    
    /**
     * 是否保留生成的代码?
     */
    public boolean getKeepGenerated() {
        return keepGenerated;
    }
    
    /**
     * 指令或操作之间的空格是否应该被删除?
     */
    public boolean getTrimSpaces() {
        return trimSpaces;
    }
    
    public boolean isPoolingEnabled() {
        return isPoolingEnabled;
    }
    
    /**
     * 是否支持HTML映射servlet?
     */
    public boolean getMappedFile() {
        return mappedFile;
    }
    
    /**
     * 该错误是否应该被发送到客户端或抛出到标准错误流?
     */
    public boolean getSendErrorToClient() {
        return sendErrorToClient;
    }
    
    /**
     * 是否应该用调试信息编译类文件?
     */
    public boolean getClassDebugInfo() {
        return classDebugInfo;
    }
    
    /**
     * 后台JSP编译线程检查间隔
     */
    public int getCheckInterval() {
        return checkInterval;
    }
    
    /**
     * 修改测试间隔.
     */
    public int getModificationTestInterval() {
        return modificationTestInterval;
    }
    
    /**
     * 是否在开发模式使用Jasper?
     */
    public boolean getDevelopment() {
        return development;
    }
    
    /**
     * 是否阻止JSR45调试的SMAP信息的生成
     */
    public boolean isSmapSuppressed() {
        return isSmapSuppressed;
    }
    
    /**
     * JSR45调试的SMAP信息是否保存到文件中
     */
    public boolean isSmapDumped() {
        return isSmapDumped;
    }
    
    /**
     * 文本字符串是否被生成为char数组
     */
    public boolean genStringAsCharArray() {
        return this.genStringAsCharArray;
    }
    
    /**
     * 当浏览器是IE时，在标签库中使用的Class ID. 
     */
    public String getIeClassId() {
        return ieClassId;
    }
    
    /**
     * 生成的servlet所在的目录
     */
    public File getScratchDir() {
        return scratchDir;
    }
    
    /**
     * 当编译生成servlet时, 应该使用的classpath
     */
    public String getClassPath() {
        return classpath;
    }
    
    /**
     * 是否生成 X-Powered-By 响应头
     */
    public boolean isXpoweredBy() {
        return xpoweredBy;
    }
    
    /**
     * 使用的编译器.
     */
    public String getCompiler() {
        return compiler;
    }
    
    /**
     * @see Options#getCompilerTargetVM
     */
    public String getCompilerTargetVM() {
        return compilerTargetVM;
    }
    
    /**
     * @see Options#getCompilerSourceVM
     */
    public String getCompilerSourceVM() {
        return compilerSourceVM;
    }
    
    public boolean getErrorOnUseBeanInvalidClassAttribute() {
        return errorOnUseBeanInvalidClassAttribute;
    }
    
    public void setErrorOnUseBeanInvalidClassAttribute(boolean b) {
        errorOnUseBeanInvalidClassAttribute = b;
    }
    
    public TldLocationsCache getTldLocationsCache() {
        return tldLocationsCache;
    }
    
    public void setTldLocationsCache( TldLocationsCache tldC ) {
        tldLocationsCache = tldC;
    }
    
    public String getJavaEncoding() {
        return javaEncoding;
    }
    
    public boolean getFork() {
        return fork;
    }
    
    public JspConfig getJspConfig() {
        return jspConfig;
    }
    
    public TagPluginManager getTagPluginManager() {
        return tagPluginManager;
    }
    
    public boolean isCaching() {
        return false;
    }
    
    public Map getCache() {
        return null;
    }

    /**
     * 创建一个EmbeddedServletOption对象，使用从ServletConfig 和 ServletContext获取的有效数据. 
     */
    public EmbeddedServletOptions(ServletConfig config, ServletContext context) {
        
        // JVM version numbers
        try {
            if (Float.parseFloat(System.getProperty("java.specification.version")) > 1.4) {
                compilerSourceVM = compilerTargetVM = "1.5";
            } else {
                compilerSourceVM = compilerTargetVM = "1.4";
            }
        } catch (NumberFormatException e) {
            // Ignore
        }
        
        Enumeration enumeration=config.getInitParameterNames();
        while( enumeration.hasMoreElements() ) {
            String k=(String)enumeration.nextElement();
            String v=config.getInitParameter( k );
            setProperty( k, v);
        }
        
        // quick hack
        String validating=config.getInitParameter( "validating");
        if( "false".equals( validating )) ParserUtils.validating=false;
        
        String keepgen = config.getInitParameter("keepgenerated");
        if (keepgen != null) {
            if (keepgen.equalsIgnoreCase("true")) {
                this.keepGenerated = true;
            } else if (keepgen.equalsIgnoreCase("false")) {
                this.keepGenerated = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.keepgen"));
                }
            }
        }
        
        
        String trimsp = config.getInitParameter("trimSpaces"); 
        if (trimsp != null) {
            if (trimsp.equalsIgnoreCase("true")) {
                trimSpaces = true;
            } else if (trimsp.equalsIgnoreCase("false")) {
                trimSpaces = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.trimspaces"));
                }
            }
        }
        
        this.isPoolingEnabled = true;
        String poolingEnabledParam
        = config.getInitParameter("enablePooling"); 
        if (poolingEnabledParam != null
                && !poolingEnabledParam.equalsIgnoreCase("true")) {
            if (poolingEnabledParam.equalsIgnoreCase("false")) {
                this.isPoolingEnabled = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.enablePooling"));
                }		       	   
            }
        }
        
        String mapFile = config.getInitParameter("mappedfile"); 
        if (mapFile != null) {
            if (mapFile.equalsIgnoreCase("true")) {
                this.mappedFile = true;
            } else if (mapFile.equalsIgnoreCase("false")) {
                this.mappedFile = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.mappedFile"));
                }
            }
        }
        
        String senderr = config.getInitParameter("sendErrToClient");
        if (senderr != null) {
            if (senderr.equalsIgnoreCase("true")) {
                this.sendErrorToClient = true;
            } else if (senderr.equalsIgnoreCase("false")) {
                this.sendErrorToClient = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.sendErrToClient"));
                }
            }
        }
        
        String debugInfo = config.getInitParameter("classdebuginfo");
        if (debugInfo != null) {
            if (debugInfo.equalsIgnoreCase("true")) {
                this.classDebugInfo  = true;
            } else if (debugInfo.equalsIgnoreCase("false")) {
                this.classDebugInfo  = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.classDebugInfo"));
                }
            }
        }
        
        String checkInterval = config.getInitParameter("checkInterval");
        if (checkInterval != null) {
            try {
                this.checkInterval = Integer.parseInt(checkInterval);
                if (this.checkInterval == 0) {
                    this.checkInterval = 300;
                    if (log.isWarnEnabled()) {
                        log.warn(Localizer.getMessage("jsp.warning.checkInterval"));
                    }
                }
            } catch(NumberFormatException ex) {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.checkInterval"));
                }
            }
        }
        
        String modificationTestInterval = config.getInitParameter("modificationTestInterval");
        if (modificationTestInterval != null) {
            try {
                this.modificationTestInterval = Integer.parseInt(modificationTestInterval);
            } catch(NumberFormatException ex) {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.modificationTestInterval"));
                }
            }
        }
        
        String development = config.getInitParameter("development");
        if (development != null) {
            if (development.equalsIgnoreCase("true")) {
                this.development = true;
            } else if (development.equalsIgnoreCase("false")) {
                this.development = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.development"));
                }
            }
        }
        
        String suppressSmap = config.getInitParameter("suppressSmap");
        if (suppressSmap != null) {
            if (suppressSmap.equalsIgnoreCase("true")) {
                isSmapSuppressed = true;
            } else if (suppressSmap.equalsIgnoreCase("false")) {
                isSmapSuppressed = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.suppressSmap"));
                }
            }
        }
        
        String dumpSmap = config.getInitParameter("dumpSmap");
        if (dumpSmap != null) {
            if (dumpSmap.equalsIgnoreCase("true")) {
                isSmapDumped = true;
            } else if (dumpSmap.equalsIgnoreCase("false")) {
                isSmapDumped = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.dumpSmap"));
                }
            }
        }
        
        String genCharArray = config.getInitParameter("genStrAsCharArray");
        if (genCharArray != null) {
            if (genCharArray.equalsIgnoreCase("true")) {
                genStringAsCharArray = true;
            } else if (genCharArray.equalsIgnoreCase("false")) {
                genStringAsCharArray = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.genchararray"));
                }
            }
        }
        
        String errBeanClass =
            config.getInitParameter("errorOnUseBeanInvalidClassAttribute");
        if (errBeanClass != null) {
            if (errBeanClass.equalsIgnoreCase("true")) {
                errorOnUseBeanInvalidClassAttribute = true;
            } else if (errBeanClass.equalsIgnoreCase("false")) {
                errorOnUseBeanInvalidClassAttribute = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.errBean"));
                }
            }
        }
        
        String ieClassId = config.getInitParameter("ieClassId");
        if (ieClassId != null)
            this.ieClassId = ieClassId;
        
        String classpath = config.getInitParameter("classpath");
        if (classpath != null)
            this.classpath = classpath;
        
        /*
         * scratchdir
         */
        String dir = config.getInitParameter("scratchdir"); 
        if (dir != null) {
            scratchDir = new File(dir);
        } else {
            // First try the Servlet 2.2 javax.servlet.context.tempdir property
            scratchDir = (File) context.getAttribute(Constants.TMP_DIR);
            if (scratchDir == null) {
                // Not running in a Servlet 2.2 container.
                // Try to get the JDK 1.2 java.io.tmpdir property
                dir = System.getProperty("java.io.tmpdir");
                if (dir != null)
                    scratchDir = new File(dir);
            }
        }      
        if (this.scratchDir == null) {
            log.fatal(Localizer.getMessage("jsp.error.no.scratch.dir"));
            return;
        }
        
        if (!(scratchDir.exists() && scratchDir.canRead() &&
                scratchDir.canWrite() && scratchDir.isDirectory()))
            log.fatal(Localizer.getMessage("jsp.error.bad.scratch.dir",
                    scratchDir.getAbsolutePath()));
        
        this.compiler = config.getInitParameter("compiler");
        
        String compilerTargetVM = config.getInitParameter("compilerTargetVM");
        if(compilerTargetVM != null) {
            this.compilerTargetVM = compilerTargetVM;
        }
        
        String compilerSourceVM = config.getInitParameter("compilerSourceVM");
        if(compilerSourceVM != null) {
            this.compilerSourceVM = compilerSourceVM;
        }
        
        String javaEncoding = config.getInitParameter("javaEncoding");
        if (javaEncoding != null) {
            this.javaEncoding = javaEncoding;
        }
        
        String fork = config.getInitParameter("fork");
        if (fork != null) {
            if (fork.equalsIgnoreCase("true")) {
                this.fork = true;
            } else if (fork.equalsIgnoreCase("false")) {
                this.fork = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.fork"));
                }
            }
        }
        
        String xpoweredBy = config.getInitParameter("xpoweredBy"); 
        if (xpoweredBy != null) {
            if (xpoweredBy.equalsIgnoreCase("true")) {
                this.xpoweredBy = true;
            } else if (xpoweredBy.equalsIgnoreCase("false")) {
                this.xpoweredBy = false;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage("jsp.warning.xpoweredBy"));
                }
            }
        }
        
        // 设置这个Web应用的全局Tag Libraries位置.
        tldLocationsCache = new TldLocationsCache(context);
        
        // Setup the jsp config info for this web app.
        jspConfig = new JspConfig(context);
        
        // Create a Tag plugin instance
        tagPluginManager = new TagPluginManager(context);
    }
}

