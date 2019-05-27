package org.apache.catalina.util;

import java.io.File;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

/**
 * 封装流程环境和规则，以从servlet容器和请求信息导出该环境.
 */
public class ProcessEnvironment {
    
    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( ProcessEnvironment.class );
    
    /** 封闭servlet的上下文 */
    private ServletContext context = null;

    /** 封闭servlet Web应用程序的真实文件系统目录 */
    private String webAppRootDir = null;

    /** 封闭servlet的上下文路径 */
    private String contextPath = null;

    /** 当前请求的路径 */
    protected String pathInfo = null;

    /** 封闭servlet的servlet URI */
    private String servletPath = null;

    /** 派生处理环境 */
    protected Hashtable env = null;

    /** 要调用的命令 */
    protected String command = null;

    /** 这个对象是否有效  */
    protected boolean valid = false;

    /** 调试等级. */
    protected int debug = 0;

    /** 工作目录 */
    protected File workingDirectory = null;


    /**
     * 创建一个ProcessEnvironment并获取必要的环境,工作目录, 命令, 等.
     * @param  req       HttpServletRequest for information provided by
     *                   the Servlet API
     * @param  context   ServletContext for information provided by
     *                   the Servlet API
     */
    public ProcessEnvironment(HttpServletRequest req,
        ServletContext context) {
        this(req, context, 0);
    }


    /**
     * 创建一个ProcessEnvironment并获取必要的环境,工作目录, 命令, 等.
     * @param  req       HttpServletRequest for information provided by
     *                   the Servlet API
     * @param  context   ServletContext for information provided by
     *                   the Servlet API
     * @param  debug     int debug level (0 == none, 4 == medium, 6 == lots)
     */
    public ProcessEnvironment(HttpServletRequest req,
        ServletContext context, int debug) {
            this.debug = debug;
            setupFromContext(context);
            setupFromRequest(req);
            this.valid = deriveProcessEnvironment(req);
            if (log.isDebugEnabled()) 
                log.debug(this.getClass().getName() + "() ctor, debug level " + 
                          debug);
    }


    /**
     * 使用ServletContext设置一些流程变量
     * @param  context   ServletContext for information provided by
     *                   the Servlet API
     */
    protected void setupFromContext(ServletContext context) {
        this.context = context;
        this.webAppRootDir = context.getRealPath("/");
    }


    /**
     * 使用HttpServletRequest设置大多数流程变量
     * @param  req   HttpServletRequest for information provided by
     *               the Servlet API
     */
    protected void setupFromRequest(HttpServletRequest req) {
        this.contextPath = req.getContextPath();
        this.pathInfo = req.getPathInfo();
        this.servletPath = req.getServletPath();
    }


    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("<TABLE border=2>");
        sb.append("<tr><th colspan=2 bgcolor=grey>");
        sb.append("ProcessEnvironment Info</th></tr>");
        sb.append("<tr><td>Debug Level</td><td>");
        sb.append(debug);
        sb.append("</td></tr>");
        sb.append("<tr><td>Validity:</td><td>");
        sb.append(isValid());
        sb.append("</td></tr>");
        if (isValid()) {
            Enumeration envk = env.keys();
            while (envk.hasMoreElements()) {
                String s = (String)envk.nextElement();
                sb.append("<tr><td>");
                sb.append(s);
                sb.append("</td><td>");
                sb.append(blanksToString((String)env.get(s),
                    "[will be set to blank]"));
                    sb.append("</td></tr>");
            }
        }
        sb.append("<tr><td colspan=2><HR></td></tr>");
        sb.append("<tr><td>Derived Command</td><td>");
        sb.append(nullsToBlanks(command));
        sb.append("</td></tr>");
        sb.append("<tr><td>Working Directory</td><td>");
        if (workingDirectory != null) {
            sb.append(workingDirectory.toString());
        }
        sb.append("</td></tr>");
        sb.append("</TABLE><p>end.");
        return sb.toString();
    }


    /**
     * 获取命令字符串
     * @return  command string
     */
    public String getCommand() {
        return command;
    }


    /**
     * 设置命令字符串
     * @param   command String command as desired
     * @return  command string
     */
    protected String setCommand(String command) {
        return command;
    }


    /**
     * 获取工作目录
     * @return  working directory
     */
    public File getWorkingDirectory() {
        return workingDirectory;
    }


    /**
     * 获取环境
     * @return   process' environment
     */
    public Hashtable getEnvironment() {
        return env;
    }


    /**
     * 设置环境
     * @param    env process' environment
     * @return   Hashtable to which the process' environment was set
     */
    public Hashtable setEnvironment(Hashtable env) {
        this.env = env;
        return this.env;
    }


    /**
     * 获取有效状态
     * @return   true 如果这个环境是有效的, 否则false
     */
    public boolean isValid() {
        return valid;
    }


    /**
     * 将null转换为空白字符串("")
     * @param    string to be converted if necessary
     * @return   non-null string, 要么是原始的要么是空的字符串("")
     */
    protected String nullsToBlanks(String s) {
        return nullsToString(s, "");
    }


    /**
     * 将null转换为另一个字符串
     * @param    string to be converted if necessary
     * @param    string to return instead of a null string
     * @return   non-null string, 原始的或替代的字符串
     */
    protected String nullsToString(String couldBeNull, String subForNulls) {
        return (couldBeNull == null ? subForNulls : couldBeNull);
    }


    /**
     * 将空白字符串转换为另一个字符串
     * @param    string to be converted if necessary
     * @param    string to return instead of a blank string
     * @return   a non-null string, 原始的或替代的字符串
     */
    protected String blanksToString(String couldBeBlank,
        String subForBlanks) {
            return (("".equals(couldBeBlank) || couldBeBlank == null) ?
                subForBlanks : couldBeBlank);
    }


    /**
     * 构建要提供给被调用进程的流程环境. 定义没有环境变量的环境.
     * <p>
     * 应该被子类重写来执行有用的设置.
     * </p>
     *
     * @param    req request associated with the
     *           Process' invocation
     * @return   true if environment was set OK, false if there was a problem
     *           and no environment was set
     */
    protected boolean deriveProcessEnvironment(HttpServletRequest req) {

        Hashtable envp = new Hashtable();
        command = getCommand();
        if (command != null) {
            workingDirectory = new
                File(command.substring(0,
                command.lastIndexOf(File.separator)));
                envp.put("X_TOMCAT_COMMAND_PATH", command); //for kicks
        }
        this.env = envp;
        return true;
    }


    /**
     * 获取此进程所属的Web应用程序的根目录
     * @return  root directory
     */
    public String getWebAppRootDir() {
        return webAppRootDir;
    }


    public String getContextPath(){
            return contextPath;
        }


    public ServletContext getContext(){
            return context;
        }


    public String getServletPath(){
            return servletPath;
        }
}
