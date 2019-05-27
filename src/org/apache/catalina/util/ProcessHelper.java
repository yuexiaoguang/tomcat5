package org.apache.catalina.util;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.servlet.http.HttpServletResponse;

/**
 * 封装了如何运行CGI脚本的知识, 给定脚本所需的环境和（可选）输入/输出流
 * <p>
 * 暴露<code>run</code>用于实际调用CGI的方法
 * </p>
 * <p>
 * CGI环境和设置都来源于信息传递给建设者.
 * </p>
 * <p>
 * 输入和输出流可以由<code>setInput</code>和<code>setResponse</code>方法设置, 分别地.
 * </p>
 */
public class ProcessHelper {

    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( ProcessHelper.class );
    
    /** 要执行的脚本/命令 */
    private String command = null;

    /** 调用CGI脚本时使用的环境 */
    private Hashtable env = null;

    /** 调用CGI脚本时使用的工作目录 */
    private File wd = null;

    /** 要传递给被调用脚本的查询参数 */
    private Hashtable params = null;

    /** 要传递给CGI脚本的stdin */
    private InputStream stdin = null;

    /** 用于设置标头和获取输出流的响应对象 */
    private HttpServletResponse response = null;

    /** 这个对象是否有足够的信息执行 run() */
    private boolean readyToRun = false;

    /** 调试等级 . */
    private int debug = 0;

    /** 等待客户端发送CGI输入数据的事件，毫秒 */
    private int iClientInputTimeout;

    /**
     *  创建一个ProcessHelper并初始化其环境, 工作目录, 查询参数.
	 *  <BR>
	 *  输入/输出流（可选）使用<code>setInput</code>和<code>setResponse</code>方法设置, 分别地.
	 *
	 * @param  command  要执行的命令的字符串完整路径
	 * @param  env      所需的脚本环境的Hashtable
	 * @param  wd       脚本所需的工作目录
	 * @param  params   脚本查询参数的Hashtable
     */
    public ProcessHelper(
        String command,
        Hashtable env,
        File wd,
        Hashtable params) {
        this.command = command;
        this.env = env;
        this.wd = wd;
        this.params = params;
        updateReadyStatus();
    }

    /**
     * 检查和设置就绪状态
     */
    protected void updateReadyStatus() {
        if (command != null
            && env != null
            && wd != null
            && params != null
            && response != null) {
            readyToRun = true;
        } else {
            readyToRun = false;
        }
    }

    /**
     * 准备状态
	 *
	 * @return   false: 如果没有准备好(<code>run</code>将抛出异常), true: 如果准备好
     */
    public boolean isReady() {
        return readyToRun;
    }

    /**
     * 设置HttpServletResponse对象， object 用于设置标头并将输出发送到
     *
     * @param  response   HttpServletResponse to be used
     */
    public void setResponse(HttpServletResponse response) {
        this.response = response;
        updateReadyStatus();
    }

    /**
     * 设置要传递给调用的CGI脚本的标准输入
     *
     * @param  stdin   InputStream to be used
     */
    public void setInput(InputStream stdin) {
        this.stdin = stdin;
        updateReadyStatus();
    }

    /**
     * 转换Hashtable成字符串数组，通过转换每个key/value对成字符串，格式为
	 * "key=value" (hashkey + "=" + hash.get(hashkey).toString())
	 *
	 * @param  h   Hashtable to convert
	 *
	 * @return 转换后的字符串数组
     *
     * @exception  NullPointerException   if a hash key has a null value
     */
    private String[] hashToStringArray(Hashtable h)
        throws NullPointerException {
        Vector v = new Vector();
        Enumeration e = h.keys();
        while (e.hasMoreElements()) {
            String k = e.nextElement().toString();
            v.add(k + "=" + h.get(k));
        }
        String[] strArr = new String[v.size()];
        v.copyInto(strArr);
        return strArr;
    }

    /**
     * 执行一个流程脚本所需的环境, 当前工作目录, 和输入/输出流
	 *
	 * <p>
	 * 这实现了以下CGI规范的建议:
	 * <UL>
	 * <LI> 服务器应该提供作为脚本命令行参数的脚本URI的 "<code>query</code>"组成部分, 
	 * 		如果它不包含任何非编码“=”字符和命令行参数, 可以以明确的方式生成.
	 * <LI> 服务器应该设置AUTH_TYPE元变量为"<code>Authorization</code>"的"<code>auth-scheme</code>" token的值,
	 * 		如果它作为请求标头的一部分.  See <code>getCGIEnvironment</code> method.
	 * <LI> 在适用, 服务器应该在调用脚本之前将当前工作目录设置为脚本所在的目录.
	 * <LI> 服务器实现应该为下列情况定义其行为:
	 *     <ul>
	 *     <LI> <u>允许的字符是</u>: 此实现不允许ASCII NUL或任何字符, 它们不能URL编码根据互联网标准;
	 *     <LI> <u>路径段中允许的字符</u>: 此实现不允许路径中的非终端NULL 分段 -- 可能抛出IOException;
	 *     <LI> <u>"<code>.</code>" 和"<code>..</code>" 路径段</u>:
	 *             此实现不允许路径中包含"<code>.</code>"和"<code>..</code>", 这样的字符会抛出IOException异常;
	 *     <LI> <u>实现的局限性</u>: 除了上述记录外，此实现没有任何限制. 此实现可能受到用于保存此实现的servlet容器的限制.
	 *             特别地, 所有主要CGI变量值都是直接或间接从容器的servlet API方法的实现中导出的.
	 *     </ul>
	 * </UL>
	 * </p>
	 *
	 * 更多信息, see java.lang.Runtime#exec(String command, String[] envp, File dir)
	 *
	 * @exception IOException 如果在读/写过程中出现问题
     *
     */
    public void run() throws IOException {

        /*
         * REMIND:  这个方法太大了，应该重写吗?
         */
        if (!isReady()) {
            throw new IOException(
                this.getClass().getName() + ": not ready to run.");
        }

        if (log.isDebugEnabled()) {
            log.debug("runCGI(envp=[" + env + "], command=" + command + ")");
        }

        if ((command.indexOf(File.separator + "." + File.separator) >= 0)
            || (command.indexOf(File.separator + "..") >= 0)
            || (command.indexOf(".." + File.separator) >= 0)) {
            throw new IOException(
                this.getClass().getName()
                    + "Illegal Character in CGI command "
                    + "path ('.' or '..') detected.  Not "
                    + "running CGI ["
                    + command
                    + "].");
        }

        /* original content/structure of this section taken from
         * http://developer.java.sun.com/developer/
         *                               bugParade/bugs/4216884.html
         * with major modifications by Martin Dengler
         */
        Runtime rt = null;
        BufferedReader commandsStdOut = null;
        BufferedReader commandsStdErr = null;
        BufferedOutputStream commandsStdIn = null;
        Process proc = null;
        byte[] bBuf = new byte[1024];
        char[] cBuf = new char[1024];
        int bufRead = -1;

        //create query arguments
        Enumeration paramNames = params.keys();
        StringBuffer cmdAndArgs = new StringBuffer(command);
        if (paramNames != null && paramNames.hasMoreElements()) {
            cmdAndArgs.append(" ");
            while (paramNames.hasMoreElements()) {
                String k = (String) paramNames.nextElement();
                String v = params.get(k).toString();
                if ((k.indexOf("=") < 0) && (v.indexOf("=") < 0)) {
                    cmdAndArgs.append(k);
                    cmdAndArgs.append("=");
                    v = java.net.URLEncoder.encode(v);
                    cmdAndArgs.append(v);
                    cmdAndArgs.append(" ");
                }
            }
        }

        String postIn = getPostInput(params);
        int contentLength =
            (postIn.length() + System.getProperty("line.separator").length());
        if ("POST".equals(env.get("REQUEST_METHOD"))) {
            env.put("CONTENT_LENGTH", new Integer(contentLength));
        }

        rt = Runtime.getRuntime();
        proc = rt.exec(cmdAndArgs.toString(), hashToStringArray(env), wd);

        /*
         * provide input to cgi
         * First  -- parameters
         * Second -- any remaining input
         */
        commandsStdIn = new BufferedOutputStream(proc.getOutputStream());
        if (log.isDebugEnabled()) {
            log.debug("runCGI stdin=[" + stdin + "], qs=" + env.get("QUERY_STRING"));
        }
        if ("POST".equals(env.get("REQUEST_METHOD"))) {
            if (log.isDebugEnabled()) {
                log.debug("runCGI: writing ---------------\n");
                log.debug(postIn);
                log.debug(
                    "runCGI: new content_length="
                        + contentLength
                        + "---------------\n");
            }
            commandsStdIn.write(postIn.getBytes());
        }
        if (stdin != null) {
            //REMIND: document this
            if (stdin.available() <= 0) {
                if (log.isDebugEnabled()) {
                    log.debug(
                        "runCGI stdin is NOT available ["
                            + stdin.available()
                            + "]");
                }
                try {
                    Thread.sleep(iClientInputTimeout);
                } catch (InterruptedException ignored) {
                }
            }
            if (stdin.available() > 0) {
                if (log.isDebugEnabled()) {
                    log.debug(
                        "runCGI stdin IS available ["
                            + stdin.available()
                            + "]");
                }
                bBuf = new byte[1024];
                bufRead = -1;
                try {
                    while ((bufRead = stdin.read(bBuf)) != -1) {
                        if (log.isDebugEnabled()) {
                            log.debug(
                                "runCGI: read ["
                                    + bufRead
                                    + "] bytes from stdin");
                        }
                        commandsStdIn.write(bBuf, 0, bufRead);
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("runCGI: DONE READING from stdin");
                    }
                } catch (IOException ioe) {
                    log.error("runCGI: couldn't write all bytes.", ioe);
                }
            }
        }
        commandsStdIn.flush();
        commandsStdIn.close();

        /* 要等待进程退出,  Process.waitFor() 对我们的处境毫无用处;
         * see http://developer.java.sun.com/developer/bugParade/bugs/4223650.html
         */
        boolean isRunning = true;
        commandsStdOut =
            new BufferedReader(new InputStreamReader(proc.getInputStream()));
        commandsStdErr =
            new BufferedReader(new InputStreamReader(proc.getErrorStream()));
        BufferedWriter servletContainerStdout = null;

        try {
            if (response.getOutputStream() != null) {
                servletContainerStdout =
                    new BufferedWriter(
                        new OutputStreamWriter(response.getOutputStream()));
            }
        } catch (IOException ignored) {
            //NOOP: no output will be written
        }

        while (isRunning) {
            try {
                //read stderr first
                cBuf = new char[1024];
                while ((bufRead = commandsStdErr.read(cBuf)) != -1) {
                    if (servletContainerStdout != null) {
                        servletContainerStdout.write(cBuf, 0, bufRead);
                    }
                }

                //set headers
                String line = null;
                while (((line = commandsStdOut.readLine()) != null)
                    && !("".equals(line))) {
                    if (log.isDebugEnabled()) {
                        log.debug("runCGI: addHeader(\"" + line + "\")");
                    }
                    if (line.startsWith("HTTP")) {
                        //TODO: should set status codes (NPH support)
                        /*
                         * response.setStatus(getStatusCode(line));
                         */
                    } else {
                        response.addHeader(
                            line.substring(0, line.indexOf(":")).trim(),
                            line.substring(line.indexOf(":") + 1).trim());
                    }
                }

                //write output
                cBuf = new char[1024];
                while ((bufRead = commandsStdOut.read(cBuf)) != -1) {
                    if (servletContainerStdout != null) {
                        if (log.isDebugEnabled()) {
                            log.debug("runCGI: write(\"" + new String(cBuf) + "\")");
                        }
                        servletContainerStdout.write(cBuf, 0, bufRead);
                    }
                }

                if (servletContainerStdout != null) {
                    servletContainerStdout.flush();
                }

                proc.exitValue(); // Throws exception if alive

                isRunning = false;

            } catch (IllegalThreadStateException e) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /**
     * 获取输入到POST CGI脚本的字符串
	 *
	 * @param  params   要传递给CGI脚本的查询参数的Hashtable
	 * @return          用作CGI脚本的输入
     */
    protected String getPostInput(Hashtable params) {
        String lineSeparator = System.getProperty("line.separator");
        Enumeration paramNames = params.keys();
        StringBuffer postInput = new StringBuffer("");
        StringBuffer qs = new StringBuffer("");
        if (paramNames != null && paramNames.hasMoreElements()) {
            while (paramNames.hasMoreElements()) {
                String k = (String) paramNames.nextElement();
                String v = params.get(k).toString();
                if ((k.indexOf("=") < 0) && (v.indexOf("=") < 0)) {
                    postInput.append(k);
                    qs.append(k);
                    postInput.append("=");
                    qs.append("=");
                    postInput.append(v);
                    qs.append(v);
                    postInput.append(lineSeparator);
                    qs.append("&");
                }
            }
        }
        qs.append(lineSeparator);
        return qs.append(postInput).toString();
    }

    public int getIClientInputTimeout() {
        return iClientInputTimeout;
    }

    public void setIClientInputTimeout(int iClientInputTimeout) {
        this.iClientInputTimeout = iClientInputTimeout;
    }
}
