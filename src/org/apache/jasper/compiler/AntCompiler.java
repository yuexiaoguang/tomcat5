package org.apache.jasper.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.StringTokenizer;

import org.apache.jasper.JasperException;
import org.apache.jasper.util.SystemLogHandler;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.PatternSet;

/**
 * 主要的JSP 编译器类. 这个类使用Ant 编译.
 */
public class AntCompiler extends Compiler {

    static {
        System.setErr(new SystemLogHandler(System.err));
    }

    // ----------------------------------------------------- Instance Variables

    protected Project project=null;
    protected JasperAntLogger logger;

    // ------------------------------------------------------------ Constructor

    // Lazy eval - 如果我们不需要编译，我们可能不需要这个项目
    protected Project getProject() {
        
        if( project!=null ) return project;
        
        // 初始化项目
        project = new Project();
        logger = new JasperAntLogger();
        logger.setOutputPrintStream(System.out);
        logger.setErrorPrintStream(System.err);
        logger.setMessageOutputLevel(Project.MSG_INFO);
        project.addBuildListener( logger);
        if (System.getProperty("catalina.home") != null) {
            project.setBasedir( System.getProperty("catalina.home"));
        }
        
        if( options.getCompiler() != null ) {
            if( log.isDebugEnabled() )
                log.debug("Compiler " + options.getCompiler() );
            project.setProperty("build.compiler", options.getCompiler() );
        }
        project.init();
        return project;
    }
    
    class JasperAntLogger extends DefaultLogger {
        
        protected StringBuffer reportBuf = new StringBuffer();
        
        protected void printMessage(final String message,
                final PrintStream stream,
                final int priority) {
        }
        
        protected void log(String message) {
            reportBuf.append(message);
            reportBuf.append(System.getProperty("line.separator"));
        }
        
        protected String getReport() {
            String report = reportBuf.toString();
            reportBuf.setLength(0);
            return report;
        }
    }
    
    // --------------------------------------------------------- Public Methods


    /** 
     * 编译 servlet从.java 文件到 .class文件
     */
    protected void generateClass(String[] smap)
        throws FileNotFoundException, JasperException, Exception {
        
        long t1 = 0;
        if (log.isDebugEnabled()) {
            t1 = System.currentTimeMillis();
        }

        String javaEncoding = ctxt.getOptions().getJavaEncoding();
        String javaFileName = ctxt.getServletJavaFileName();
        String classpath = ctxt.getClassPath(); 
        
        String sep = System.getProperty("path.separator");
        
        StringBuffer errorReport = new StringBuffer();
        
        StringBuffer info=new StringBuffer();
        info.append("Compile: javaFileName=" + javaFileName + "\n" );
        info.append("    classpath=" + classpath + "\n" );
        
        // 开始捕捉 这个线程的System.err输出
        SystemLogHandler.setThread();
        
        // 初始化javac 任务
        getProject();
        Javac javac = (Javac) project.createTask("javac");
        
        // 初始化 classpath
        Path path = new Path(project);
        path.setPath(System.getProperty("java.class.path"));
        info.append("    cp=" + System.getProperty("java.class.path") + "\n");
        StringTokenizer tokenizer = new StringTokenizer(classpath, sep);
        while (tokenizer.hasMoreElements()) {
            String pathElement = tokenizer.nextToken();
            File repository = new File(pathElement);
            path.setLocation(repository);
            info.append("    cp=" + repository + "\n");
        }
        
        if( log.isDebugEnabled() )
            log.debug( "Using classpath: " + System.getProperty("java.class.path") + sep
                    + classpath);
        
        // 初始化 sourcepath
        Path srcPath = new Path(project);
        srcPath.setLocation(options.getScratchDir());
        
        info.append("    work dir=" + options.getScratchDir() + "\n");
        
        // 初始化并设置 java 扩展名
        String exts = System.getProperty("java.ext.dirs");
        if (exts != null) {
            Path extdirs = new Path(project);
            extdirs.setPath(exts);
            javac.setExtdirs(extdirs);
            info.append("    extension dir=" + exts + "\n");
        }

        // Add endorsed directories if any are specified and we're forking
        // See Bugzilla 31257
        if(ctxt.getOptions().getFork()) {
            String endorsed = System.getProperty("java.endorsed.dirs");
            if(endorsed != null) {
                Javac.ImplementationSpecificArgument endorsedArg = 
                    javac.createCompilerArg();
                endorsedArg.setLine("-J-Djava.endorsed.dirs="+endorsed);
                info.append("    endorsed dir=" + endorsed + "\n");
            } else {
                info.append("    no endorsed dirs specified\n");
            }
        }
        
        // 配置编译器对象
        javac.setEncoding(javaEncoding);
        javac.setClasspath(path);
        javac.setDebug(ctxt.getOptions().getClassDebugInfo());
        javac.setSrcdir(srcPath);
        javac.setTempdir(options.getScratchDir());
        javac.setOptimize(! ctxt.getOptions().getClassDebugInfo() );
        javac.setFork(ctxt.getOptions().getFork());
        info.append("    srcDir=" + srcPath + "\n" );
        
        // 设置要使用的Java 编译器
        if (options.getCompiler() != null) {
            javac.setCompiler(options.getCompiler());
            info.append("    compiler=" + options.getCompiler() + "\n");
        }

        if (options.getCompilerTargetVM() != null) {
            javac.setTarget(options.getCompilerTargetVM());
            info.append("   compilerTargetVM=" + options.getCompilerTargetVM() + "\n");
        }

        if (options.getCompilerSourceVM() != null) {
            javac.setSource(options.getCompilerSourceVM());
            info.append("   compilerSourceVM=" + options.getCompilerSourceVM() + "\n");
        }
        
        // Build includes path
        PatternSet.NameEntry includes = javac.createInclude();
        
        includes.setName(ctxt.getJavaPath());
        info.append("    include="+ ctxt.getJavaPath() + "\n" );
        
        BuildException be = null;
        
        try {
            if (ctxt.getOptions().getFork()) {
                javac.execute();
            } else {
                synchronized(javacLock) {
                    javac.execute();
                }
            }
        } catch (BuildException e) {
            be = e;
            log.error( "Javac exception ", e);
            log.error( "Env: " + info.toString());
        }
        
        errorReport.append(logger.getReport());

        // 停止捕获这个线程的 System.err 输出
        String errorCapture = SystemLogHandler.unsetThread();
        if (errorCapture != null) {
            errorReport.append(System.getProperty("line.separator"));
            errorReport.append(errorCapture);
        }

        if (!ctxt.keepGenerated()) {
            File javaFile = new File(javaFileName);
            javaFile.delete();
        }
        
        if (be != null) {
            String errorReportString = errorReport.toString();
            log.error("Error compiling file: " + javaFileName + " "
                    + errorReportString);
            JavacErrorDetail[] javacErrors = ErrorDispatcher.parseJavacErrors(
                    errorReportString, javaFileName, pageNodes);
            if (javacErrors != null) {
                errDispatcher.javacError(javacErrors);
            } else {
                errDispatcher.javacError(errorReportString, be);
            }
        }
        
        if( log.isDebugEnabled() ) {
            long t2=System.currentTimeMillis();
            log.debug("Compiled " + ctxt.getServletJavaFileName() + " "
                      + (t2-t1) + "ms");
        }
        
        logger = null;
        project = null;
        
        if (ctxt.isPrototypeMode()) {
            return;
        }
        
        // JSR45 Support
        if (! options.isSmapSuppressed()) {
            SmapUtil.installSmap(smap);
        }
    }
}
