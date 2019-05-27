package org.apache.jasper.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.List;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.apache.jasper.servlet.JspServletWrapper;

/**
 * 主要的JSP 编译器类. 这个类使用 Ant 编译.
 */
public abstract class Compiler {
    protected org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( Compiler.class );

    // ----------------------------------------------------------------- Static


    // 一些 javac 不是线程安全的; 使用一个锁序列化编译, 
    static Object javacLock = new Object();


    // ----------------------------------------------------- Instance Variables


    protected JspCompilationContext ctxt;

    protected ErrorDispatcher errDispatcher;
    protected PageInfo pageInfo;
    protected JspServletWrapper jsw;
    protected TagFileProcessor tfp;

    protected Options options;

    protected Node.Nodes pageNodes;
    // ------------------------------------------------------------ Constructor

    public void init(JspCompilationContext ctxt, JspServletWrapper jsw) {
        this.jsw = jsw;
        this.ctxt = ctxt;
        this.options = ctxt.getOptions();
    }

    // --------------------------------------------------------- Public Methods


    /** 
     * 编译JSP文件转换成等效的servlet, 在 .java 文件中
     * @return 当前JSP页面的smap, 如果其中一个被生成,否则返回null
     */
    protected String[] generateJava() throws Exception {

        String[] smapStr = null;

        long t1, t2, t3, t4;

        t1 = t2 = t3 = t4 = 0;
      
        if (log.isDebugEnabled()) {
            t1 = System.currentTimeMillis();
        }

        // 设置页面信息区域
        pageInfo = new PageInfo(new BeanRepository(ctxt.getClassLoader(),
                                                   errDispatcher),
                                ctxt.getJspFile());

        JspConfig jspConfig = options.getJspConfig();
        JspConfig.JspProperty jspProperty =
            jspConfig.findJspProperty(ctxt.getJspFile());

        /*
         * 如果当前URI与web.xml中的jsp-property-group指定的模式匹配, 使用这些属性初始化pageInfo.
         */
        pageInfo.setELIgnored(JspUtil.booleanValue(
                                            jspProperty.isELIgnored()));
        pageInfo.setScriptingInvalid(JspUtil.booleanValue(
                                            jspProperty.isScriptingInvalid()));
        if (jspProperty.getIncludePrelude() != null) {
            pageInfo.setIncludePrelude(jspProperty.getIncludePrelude());
        }
        if (jspProperty.getIncludeCoda() != null) {
	    pageInfo.setIncludeCoda(jspProperty.getIncludeCoda());
        }

        String javaFileName = ctxt.getServletJavaFileName();
        ServletWriter writer = null;

        try {
            // 设置 ServletWriter
            String javaEncoding = ctxt.getOptions().getJavaEncoding();
            OutputStreamWriter osw = null; 

            try {
                osw = new OutputStreamWriter(
                            new FileOutputStream(javaFileName), javaEncoding);
            } catch (UnsupportedEncodingException ex) {
                errDispatcher.jspError("jsp.error.needAlternateJavaEncoding",
                                       javaEncoding);
            }

            writer = new ServletWriter(new PrintWriter(osw));
            ctxt.setWriter(writer);

            // 重置生成器的临时变量计数器.
            JspUtil.resetTemporaryVariableName();

	    // 解析文件
	    ParserController parserCtl = new ParserController(ctxt, this);
	    pageNodes = parserCtl.parse(ctxt.getJspFile());

	    if (ctxt.isPrototypeMode()) {
                // 为标签文件生成原型 .java 文件
                Generator.generate(writer, this, pageNodes);
                writer.close();
                writer = null;
                return null;
            }

            // 验证和处理属性
            Validator.validate(this, pageNodes);

            if (log.isDebugEnabled()) {
                t2 = System.currentTimeMillis();
            }

            // 收集网页信息
            Collector.collect(this, pageNodes);

            // 编译并加载该编译单元中引用的标签文件.
            tfp = new TagFileProcessor();
            tfp.loadTagFiles(this, pageNodes);

            if (log.isDebugEnabled()) {
                t3 = System.currentTimeMillis();
            }
        
            // 确定哪些自定义标签需要声明哪些脚本变量
            ScriptingVariabler.set(pageNodes, errDispatcher);

            // 标签插件优化
            TagPluginManager tagPluginManager = options.getTagPluginManager();
            tagPluginManager.apply(pageNodes, errDispatcher, pageInfo);

            // 优化: 将相邻模板文本联系起来.
            TextOptimizer.concatenate(this, pageNodes);

            // 生成静态函数映射代码.
            ELFunctionMapper.map(this, pageNodes);

            // 生成servlet .java文件
            Generator.generate(writer, this, pageNodes);
            writer.close();
            writer = null;

            // 写入器仅在编译期间使用, 在 JspCompilationContext中间接引用, 完成时，允许它进行GC并保存内存.
            ctxt.setWriter(null);

            if (log.isDebugEnabled()) {
                t4 = System.currentTimeMillis();
                log.debug("Generated "+ javaFileName + " total="
                          + (t4-t1) + " generate=" + (t4-t3)
                          + " validate=" + (t2-t1));
            }

        } catch (Exception e) {
            if (writer != null) {
                try {
                    writer.close();
                    writer = null;
                } catch (Exception e1) {
                    // do nothing
                }
            }
            // 删除生成的 .java 文件
            new File(javaFileName).delete();
            throw e;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception e2) {
                    // do nothing
                }
            }
        }
        
        // JSR45 Support
        if (! options.isSmapSuppressed()) {
            smapStr = SmapUtil.generateSmap(ctxt, pageNodes);
        }

        // 如果生成了任何 .java 和 .class 文件, 原始的 .java 可能已被当前编译所取代 (如果标签文件是自身引用的),
        // 但是.class 文件需要被删除, 为了确保javac从新的.java文件生成 .class.
        tfp.removeProtoTypeFiles(ctxt.getClassFileName());

        return smapStr;
    }

    /** 
     * 编译servlet 从 .java 文件到 .class 文件
     */
    protected abstract void generateClass(String[] smap)
        throws FileNotFoundException, JasperException, Exception;
    
    
    /** 
     * 从当前引擎上下文编译JSP文件
     */
    public void compile() throws FileNotFoundException, JasperException, Exception {
        compile(true);
    }

    /**
     * 从当前引擎上下文编译JSP文件. 作为副作用, 此页引用的标签文件也被编译.
     * @param compileClass 如果是true, 生成 .java 和 .class 文件
     *                     如果是false, 只生成 .java 文件
     */
    public void compile(boolean compileClass)
        throws FileNotFoundException, JasperException, Exception
    {
        compile(compileClass, false);
    }

    /**
     * 从当前引擎上下文编译JSP文件. 作为副作用, 此页引用的标签文件也被编译.
     *
     * @param compileClass 如果是true, 生成 .java 和 .class 文件
     *                     如果是false, 只生成 .java 文件
     * @param jspcMode true 如果调用来自JspC, 否则返回false
     */
    public void compile(boolean compileClass, boolean jspcMode)
        throws FileNotFoundException, JasperException, Exception
    {
        if (errDispatcher == null) {
            this.errDispatcher = new ErrorDispatcher(jspcMode);
        }

        try {
            String[] smap = generateJava();
            if (compileClass) {
                generateClass(smap);
            }
        } finally {
            if (tfp != null) {
                tfp.removeProtoTypeFiles(null);
            }
            // 确定这些对象只用于JSP页面的生成和编译过程中被引用, 这样就可以进行GC并减少内存占用
            tfp = null;
            errDispatcher = null;
            pageInfo = null;
            pageNodes = null;
            if (ctxt.getWriter() != null) {
                ctxt.getWriter().close();
                ctxt.setWriter(null);
            }
        }
    }

    /**
     * 由编译器的子类重写. 编译方法使用它来完成所有编译. 
     */
    public boolean isOutDated() {
        return isOutDated( true );
    }

    /**
     * 确定通过检查JSP页面和对应的.class 或 .java 文件的时间戳来进行编译是必要的.
     * 如果页面有依赖关系, 检查也扩展到其依赖, 等等. 这个方法可以通过子类重写编译器.
     * @param checkClass 如果是true, 检查 .class 文件,
     *                   如果是false, 检查 .java 文件.
     */
    public boolean isOutDated(boolean checkClass) {

        String jsp = ctxt.getJspFile();

        if (jsw != null
                && (ctxt.getOptions().getModificationTestInterval() > 0)) {
 
            if (jsw.getLastModificationTest()
                    + (ctxt.getOptions().getModificationTestInterval() * 1000) 
                    > System.currentTimeMillis()) {
                return false;
            } else {
                jsw.setLastModificationTest(System.currentTimeMillis());
            }
        }
        
        long jspRealLastModified = 0;
        try {
            URL jspUrl = ctxt.getResource(jsp);
            if (jspUrl == null) {
                ctxt.incrementRemoved();
                return false;
            }
            URLConnection uc = jspUrl.openConnection();
            jspRealLastModified = uc.getLastModified();
            uc.getInputStream().close();
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }

        long targetLastModified = 0;
        File targetFile;
        
        if( checkClass ) {
            targetFile = new File(ctxt.getClassFileName());
        } else {
            targetFile = new File(ctxt.getServletJavaFileName());
        }
        
        if (!targetFile.exists()) {
            return true;
        }

        targetLastModified = targetFile.lastModified();
        if (checkClass && jsw != null) {
            jsw.setServletClassLastModifiedTime(targetLastModified);
        }   
        if (targetLastModified < jspRealLastModified) {
            if( log.isDebugEnabled() ) {
                log.debug("Compiler: outdated: " + targetFile + " " +
                    targetLastModified );
            }
            return true;
        }

        // 确定源依赖文件 (包括使用包含指令)已经被修改.
        if( jsw==null ) {
            return false;
        }
        
        List depends = jsw.getDependants();
        if (depends == null) {
            return false;
        }

        Iterator it = depends.iterator();
        while (it.hasNext()) {
            String include = (String)it.next();
            try {
                URL includeUrl = ctxt.getResource(include);
                if (includeUrl == null) {
                    return true;
                }

                URLConnection includeUconn = includeUrl.openConnection();
                long includeLastModified = includeUconn.getLastModified();
                includeUconn.getInputStream().close();

                if (includeLastModified > targetLastModified) {
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return true;
            }
        }
        return false;
    }

    
    /**
     * 获取错误分派器.
     */
    public ErrorDispatcher getErrorDispatcher() {
    	return errDispatcher;
    }


    /**
     * 获取有关正在编译的页面的信息
     */
    public PageInfo getPageInfo() {
    	return pageInfo;
    }


    public JspCompilationContext getCompilationContext() {
    	return ctxt;
    }


    /**
     * 删除生成的文件
     */
    public void removeGeneratedFiles() {
        try {
            String classFileName = ctxt.getClassFileName();
            if (classFileName != null) {
                File classFile = new File(classFileName);
                if( log.isDebugEnabled() )
                    log.debug( "Deleting " + classFile );
                classFile.delete();
            }
        } catch (Exception e) {
            // 尽可能地删除, 忽略可能的异常
        }
        try {
            String javaFileName = ctxt.getServletJavaFileName();
            if (javaFileName != null) {
                File javaFile = new File(javaFileName);
                if( log.isDebugEnabled() )
                    log.debug( "Deleting " + javaFile );
                javaFile.delete();
            }
        } catch (Exception e) {
            // 尽可能地删除, 忽略可能的异常
        }
    }

    public void removeGeneratedClassFiles() {
        try {
            String classFileName = ctxt.getClassFileName();
            if (classFileName != null) {
                File classFile = new File(classFileName);
                if( log.isDebugEnabled() )
                    log.debug( "Deleting " + classFile );
                classFile.delete();
            }
        } catch (Exception e) {
            // 尽可能地删除, 忽略可能的异常
        }
    }
}
