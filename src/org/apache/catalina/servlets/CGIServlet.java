package org.apache.catalina.servlets;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.UnavailableException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.catalina.Globals;
import org.apache.catalina.util.IOTools;


/**
 * Web应用的CGI-invoking servlet, 用于执行符合公共网关接口（CGI）规范的脚本，并在调用此servlet的路径信息中指定.
 *
 * <p>
 * <i>Note: 此代码编译，甚至适用于简单的CGI案例. 没有进行详尽的测试. 请考虑它的质量. 感谢作者的反馈(见下文).</i>
 * </p>
 * <p>
 *
 * <b>Example</b>:<br>
 * 如果这个servlet实例被映射为(使用<code>&lt;web-app&gt;/WEB-INF/web.xml</code>) :
 * </p>
 * <p>
 * <code>
 * &lt;web-app&gt;/cgi-bin/*
 * </code>
 * </p>
 * <p>
 * 然后，以下请求:
 * </p>
 * <p>
 * <code>
 * http://localhost:8080/&lt;web-app&gt;/cgi-bin/dir1/script/pathinfo1
 * </code>
 * </p>
 * <p>
 * 将执行脚本
 * </p>
 * <p>
 * <code>
 * &lt;web-app-root&gt;/WEB-INF/cgi/dir1/script
 * </code>
 * </p>
 * <p>
 * 并将脚本的<code>PATH_INFO</code>设置为<code>/pathinfo1</code>.
 * </p>
 * <p>
 * 推荐:  你所有的CGI脚本都放在<code>&lt;webapp&gt;/WEB-INF/cgi</code>下面.
 * 这将确保您不会意外地将CGI脚本代码暴露出去，你的CGI将干净安置在WEB-INF文件夹中.
 * </p>
 * <p>
 * 上面提到的默认CGI位置. 可以灵活地把CGI放到任何你想的地方，但是:
 * </p>
 * <p>
 *   CGI搜索路径将开始
 *   webAppRootDir + File.separator + cgiPathPrefix
 *   (或者webAppRootDir，如果cgiPathPrefix是null).
 * </p>
 * <p>
 *   cgiPathPrefix 通过设置cgiPathPrefix初始化参数定义
 * </p>
 * <p>
 * <B>CGI 规范</B>:<br> 来自
 * <a href="http://cgi-spec.golux.com">http://cgi-spec.golux.com</a>.
 * A work-in-progress & expired Internet Draft. 
 * 目前不存在描述CGI规范的RFC. 此servlet的行为与上面引用的规范不同, 这里有文件记录, 一个bug,
 * 或规范从Best Community Practice (BCP)引用不同的实例.
 * </p>
 * <p>
 *
 * <b>Canonical metavariables</b>:<br>
 * CGI规范定义了以下元变量:
 * <br>
 * [CGI规范的摘录]
 * <PRE>
 *  AUTH_TYPE
 *  CONTENT_LENGTH
 *  CONTENT_TYPE
 *  GATEWAY_INTERFACE
 *  PATH_INFO
 *  PATH_TRANSLATED
 *  QUERY_STRING
 *  REMOTE_ADDR
 *  REMOTE_HOST
 *  REMOTE_IDENT
 *  REMOTE_USER
 *  REQUEST_METHOD
 *  SCRIPT_NAME
 *  SERVER_NAME
 *  SERVER_PORT
 *  SERVER_PROTOCOL
 *  SERVER_SOFTWARE
 * </PRE>
 * <p>
 * 以协议名称开始的元变量名称(<EM>e.g.</EM>, "HTTP_ACCEPT")在请求标头字段的描述中也是规范的. 
 * 这些字段的数量和含义可能与本规范无关.(参见第 6.1.5 [CGI 规范].)
 * </p>
 * [end excerpt]
 *
 * </p>
 * <h2>实现注意事项</h2>
 * <p>
 *
 * <b>标准的输入处理</b>: 如果脚本接受标准输入,
 * 然后客户端必须在一定的超时时间内开始发送输入, 否则servlet将假定没有输入，并继续运行脚本.
 * 脚本的标准输入将被关闭，客户端的任何其他输入的处理都是未定义的. 很有可能会被忽略. 
 * 如果这种行为变得不受欢迎, 然后这个servlet需要增强处理催生了进程的stdin，stdout和stderr线程(不应该太难).
 * <br>
 * 如果你发现你的CGI脚本正在超时接收输入, 可以设置init参数<code></code> 你的webapps的CGI处理servlet是
 * </p>
 * <p>
 *
 * <b>元变量值</b>: 根据CGI, 实现类可以选择以特定实现类的方式来表示空值或丢失值，但必须定义这种方式.
 * 这个实现总是选择所需的元变量定义, 但是设置值为"" 为所有的元变量的值是null或undefined.
 * PATH_TRANSLATED 是这条规则的唯一例外, 按照CGI规范.
 * </p>
 * <p>
 *
 * <b>NPH -- 非解析报头实现</b>:  这种实现不支持CGI NPH的概念, 其中服务器确保提供给脚本的数据是由客户端提供，而不是服务器.
 * </p>
 * <p>
 * servlet容器（包括Tomcat）的功能是专门用来解析和更改CGI特定变量的, 这样使NPH功能难以支撑.
 * </p>
 * <p>
 * CGI规范规定，兼容的服务器可以支持NPH输出.
 * 它没有规定服务器必须支持NPH输出是完全兼容的. 因此，此实现类保持无条件遵守规范,虽然NPH支持是不存在的.
 * </p>
 * <p>
 * </p>
 * </p>
 * <p>
 * <h3>TODO:</h3>
 * <ul>
 * <li> 支持设置 header (例如, 位置header不工作了)
 * <li> 支持折叠多个header行 (per RFC 2616)
 * <li> 确保POST方法处理不干扰 2.3 Filters
 * <li> 一些header代码重构的核心
 * <li> 确保报头处理保留编码
 * <li> 可能重写CGIRunner.run()?
 * <li> CGIRunner 和 CGIEnvironment可能重构非内部类?
 * <li> CGI标准输入文件的处理, 当没有输入的时候
 * <li> Revisit IOException handling in CGIRunner.run()
 * <li> Better documentation
 * <li> CGIRunner.run()中的 ServletInputStream.available()不需要
 * <li> 验证servlet中的 "." and ".." & cgi PATH_INFO less
 *      draconian
 * <li> [add more to this TODO list]
 * </ul>
 * </p>
 */
public final class CGIServlet extends HttpServlet {

    /* some vars below copied from Craig R. McClanahan's InvokerServlet */

    /** 与web应用程序关联的上下文容器. */
    private ServletContext context = null;

    /** 调试等级. */
    private int debug = 0;

    /**
     *  CGI搜索路径：
     *    webAppRootDir + File.separator + cgiPathPrefix
     *    (或者只有webAppRootDir，如果cgiPathPrefix是null)
     */
    private String cgiPathPrefix = null;

    /** 与脚本一起使用的可执行文件 */
    private String cgiExecutable = "perl";
    
    /** 用于参数的编码 */
    private String parameterEncoding = System.getProperty("file.encoding", "UTF-8");

    /** 用于确保多个线程不尝试扩展同一文件的对象 */
    static Object expandFileLock = new Object();

    /** 要传递给CGI脚本的shell环境变量 */
    static Hashtable shellEnv = new Hashtable();

    /**
     * 设置实例变量.
     *
     * @param config  包含servlet配置和初始化参数的<code>ServletConfig</code>
     *
     * @exception ServletException   如果发生了异常，干扰了servlet的正常操作
     */
    public void init(ServletConfig config) throws ServletException {

        super.init(config);

        // Verify that we were not accessed using the invoker servlet
        String servletName = getServletConfig().getServletName();
        if (servletName == null)
            servletName = "";
        if (servletName.startsWith("org.apache.catalina.INVOKER."))
            throw new UnavailableException
                ("Cannot invoke CGIServlet through the invoker");

        boolean passShellEnvironment = false;
        
        // 从初始化参数设置属性
        String value = null;
        try {
            value = getServletConfig().getInitParameter("debug");
            debug = Integer.parseInt(value);
            cgiPathPrefix =
                getServletConfig().getInitParameter("cgiPathPrefix");
            value = getServletConfig().getInitParameter("passShellEnvironment");
            passShellEnvironment = Boolean.valueOf(value).booleanValue();
        } catch (Throwable t) {
            //NOOP
        }
        log("init: loglevel set to " + debug);

        if (passShellEnvironment) {
            try {
                shellEnv.putAll(getShellEnvironment());
            } catch (IOException ioe) {
                ServletException e = new ServletException(
                        "Unable to read shell environment variables", ioe);
            }
        }

        value = getServletConfig().getInitParameter("executable");
        if (value != null) {
            cgiExecutable = value;
        }

        value = getServletConfig().getInitParameter("parameterEncoding");
        if (value != null) {
            parameterEncoding = value;
        }

        // 确定需要的内部容器资源
        context = config.getServletContext();
    }



    /**
     * 打印出重要的servlet API和容器信息
     *
     * @param  out    ServletOutputStream作为信息的目标
     * @param  req    HttpServletRequest对象，信息来源
     * @param  res    HttpServletResponse对象目前没有使用，但可以提供未来的信息
     *
     * @exception  IOException  如果出现写操作异常
     */
    protected void printServletEnvironment(ServletOutputStream out,
        HttpServletRequest req, HttpServletResponse res) throws IOException {

        // Document the properties from ServletRequest
        out.println("<h1>ServletRequest Properties</h1>");
        out.println("<ul>");
        Enumeration attrs = req.getAttributeNames();
        while (attrs.hasMoreElements()) {
            String attr = (String) attrs.nextElement();
            out.println("<li><b>attribute</b> " + attr + " = " +
                           req.getAttribute(attr));
        }
        out.println("<li><b>characterEncoding</b> = " +
                       req.getCharacterEncoding());
        out.println("<li><b>contentLength</b> = " +
                       req.getContentLength());
        out.println("<li><b>contentType</b> = " +
                       req.getContentType());
        Enumeration locales = req.getLocales();
        while (locales.hasMoreElements()) {
            Locale locale = (Locale) locales.nextElement();
            out.println("<li><b>locale</b> = " + locale);
        }
        Enumeration params = req.getParameterNames();
        while (params.hasMoreElements()) {
            String param = (String) params.nextElement();
            String values[] = req.getParameterValues(param);
            for (int i = 0; i < values.length; i++)
                out.println("<li><b>parameter</b> " + param + " = " +
                               values[i]);
        }
        out.println("<li><b>protocol</b> = " + req.getProtocol());
        out.println("<li><b>remoteAddr</b> = " + req.getRemoteAddr());
        out.println("<li><b>remoteHost</b> = " + req.getRemoteHost());
        out.println("<li><b>scheme</b> = " + req.getScheme());
        out.println("<li><b>secure</b> = " + req.isSecure());
        out.println("<li><b>serverName</b> = " + req.getServerName());
        out.println("<li><b>serverPort</b> = " + req.getServerPort());
        out.println("</ul>");
        out.println("<hr>");

        // Document the properties from HttpServletRequest
        out.println("<h1>HttpServletRequest Properties</h1>");
        out.println("<ul>");
        out.println("<li><b>authType</b> = " + req.getAuthType());
        out.println("<li><b>contextPath</b> = " +
                       req.getContextPath());
        Cookie cookies[] = req.getCookies();
        if (cookies!=null) {
            for (int i = 0; i < cookies.length; i++)
                out.println("<li><b>cookie</b> " + cookies[i].getName() +" = " +cookies[i].getValue());
        }
        Enumeration headers = req.getHeaderNames();
        while (headers.hasMoreElements()) {
            String header = (String) headers.nextElement();
            out.println("<li><b>header</b> " + header + " = " +
                           req.getHeader(header));
        }
        out.println("<li><b>method</b> = " + req.getMethod());
        out.println("<li><a name=\"pathInfo\"><b>pathInfo</b></a> = "
                    + req.getPathInfo());
        out.println("<li><b>pathTranslated</b> = " +
                       req.getPathTranslated());
        out.println("<li><b>queryString</b> = " +
                       req.getQueryString());
        out.println("<li><b>remoteUser</b> = " +
                       req.getRemoteUser());
        out.println("<li><b>requestedSessionId</b> = " +
                       req.getRequestedSessionId());
        out.println("<li><b>requestedSessionIdFromCookie</b> = " +
                       req.isRequestedSessionIdFromCookie());
        out.println("<li><b>requestedSessionIdFromURL</b> = " +
                       req.isRequestedSessionIdFromURL());
        out.println("<li><b>requestedSessionIdValid</b> = " +
                       req.isRequestedSessionIdValid());
        out.println("<li><b>requestURI</b> = " +
                       req.getRequestURI());
        out.println("<li><b>servletPath</b> = " +
                       req.getServletPath());
        out.println("<li><b>userPrincipal</b> = " +
                       req.getUserPrincipal());
        out.println("</ul>");
        out.println("<hr>");

        // Document the servlet request attributes
        out.println("<h1>ServletRequest Attributes</h1>");
        out.println("<ul>");
        attrs = req.getAttributeNames();
        while (attrs.hasMoreElements()) {
            String attr = (String) attrs.nextElement();
            out.println("<li><b>" + attr + "</b> = " +
                           req.getAttribute(attr));
        }
        out.println("</ul>");
        out.println("<hr>");

        // Process the current session (if there is one)
        HttpSession session = req.getSession(false);
        if (session != null) {

            // Document the session properties
            out.println("<h1>HttpSession Properties</h1>");
            out.println("<ul>");
            out.println("<li><b>id</b> = " +
                           session.getId());
            out.println("<li><b>creationTime</b> = " +
                           new Date(session.getCreationTime()));
            out.println("<li><b>lastAccessedTime</b> = " +
                           new Date(session.getLastAccessedTime()));
            out.println("<li><b>maxInactiveInterval</b> = " +
                           session.getMaxInactiveInterval());
            out.println("</ul>");
            out.println("<hr>");

            // Document the session attributes
            out.println("<h1>HttpSession Attributes</h1>");
            out.println("<ul>");
            attrs = session.getAttributeNames();
            while (attrs.hasMoreElements()) {
                String attr = (String) attrs.nextElement();
                out.println("<li><b>" + attr + "</b> = " +
                               session.getAttribute(attr));
            }
            out.println("</ul>");
            out.println("<hr>");

        }

        // Document the servlet configuration properties
        out.println("<h1>ServletConfig Properties</h1>");
        out.println("<ul>");
        out.println("<li><b>servletName</b> = " +
                       getServletConfig().getServletName());
        out.println("</ul>");
        out.println("<hr>");

        // Document the servlet configuration initialization parameters
        out.println("<h1>ServletConfig Initialization Parameters</h1>");
        out.println("<ul>");
        params = getServletConfig().getInitParameterNames();
        while (params.hasMoreElements()) {
            String param = (String) params.nextElement();
            String value = getServletConfig().getInitParameter(param);
            out.println("<li><b>" + param + "</b> = " + value);
        }
        out.println("</ul>");
        out.println("<hr>");

        // Document the servlet context properties
        out.println("<h1>ServletContext Properties</h1>");
        out.println("<ul>");
        out.println("<li><b>majorVersion</b> = " +
                       getServletContext().getMajorVersion());
        out.println("<li><b>minorVersion</b> = " +
                       getServletContext().getMinorVersion());
        out.println("<li><b>realPath('/')</b> = " +
                       getServletContext().getRealPath("/"));
        out.println("<li><b>serverInfo</b> = " +
                       getServletContext().getServerInfo());
        out.println("</ul>");
        out.println("<hr>");

        // Document the servlet context initialization parameters
        out.println("<h1>ServletContext Initialization Parameters</h1>");
        out.println("<ul>");
        params = getServletContext().getInitParameterNames();
        while (params.hasMoreElements()) {
            String param = (String) params.nextElement();
            String value = getServletContext().getInitParameter(param);
            out.println("<li><b>" + param + "</b> = " + value);
        }
        out.println("</ul>");
        out.println("<hr>");

        // Document the servlet context attributes
        out.println("<h1>ServletContext Attributes</h1>");
        out.println("<ul>");
        attrs = getServletContext().getAttributeNames();
        while (attrs.hasMoreElements()) {
            String attr = (String) attrs.nextElement();
            out.println("<li><b>" + attr + "</b> = " +
                           getServletContext().getAttribute(attr));
        }
        out.println("</ul>");
        out.println("<hr>");
    }



    /**
     * 提供CGI网关服务 -- 委托给<code>doGet</code>
     *
     * @param  req   HttpServletRequest passed in by servlet container
     * @param  res   HttpServletResponse passed in by servlet container
     *
     * @exception  ServletException  if a servlet-specific exception occurs
     * @exception  IOException  if a read/write exception occurs
     */
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
        throws IOException, ServletException {
        doGet(req, res);
    }



    /**
     * 提供CGI网关服务
     *
     * @param  req   HttpServletRequest passed in by servlet container
     * @param  res   HttpServletResponse passed in by servlet container
     *
     * @exception  ServletException  if a servlet-specific exception occurs
     * @exception  IOException  if a read/write exception occurs
     */
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException {

        // Verify that we were not accessed using the invoker servlet
        if (req.getAttribute(Globals.INVOKED_ATTR) != null)
            throw new UnavailableException
                ("Cannot invoke CGIServlet through the invoker");

        CGIEnvironment cgiEnv = new CGIEnvironment(req, getServletContext());

        if (cgiEnv.isValid()) {
            CGIRunner cgi = new CGIRunner(cgiEnv.getCommand(),
                                          cgiEnv.getEnvironment(),
                                          cgiEnv.getWorkingDirectory(),
                                          cgiEnv.getParameters());
            //if POST, we need to cgi.setInput
            //REMIND: how does this interact with Servlet API 2.3's Filters?!
            if ("POST".equals(req.getMethod())) {
                cgi.setInput(req.getInputStream());
            }
            cgi.setResponse(res);
            cgi.run();
        }

        if (!cgiEnv.isValid()) {
            res.setStatus(404);
        }
 
        if (debug >= 10) {
            try {
                ServletOutputStream out = res.getOutputStream();
                out.println("<HTML><HEAD><TITLE>$Name:  $</TITLE></HEAD>");
                out.println("<BODY>$Header: /home/cvs/jakarta-tomcat-catalina/catalina/src/share/org/apache/catalina/servlets/CGIServlet.java,v 1.31 2004/11/29 23:00:30 markt Exp $<p>");

                if (cgiEnv.isValid()) {
                    out.println(cgiEnv.toString());
                } else {
                    out.println("<H3>");
                    out.println("CGI script not found or not specified.");
                    out.println("</H3>");
                    out.println("<H4>");
                    out.println("Check the <b>HttpServletRequest ");
                    out.println("<a href=\"#pathInfo\">pathInfo</a></b> ");
                    out.println("property to see if it is what you meant ");
                    out.println("it to be.  You must specify an existant ");
                    out.println("and executable file as part of the ");
                    out.println("path-info.");
                    out.println("</H4>");
                    out.println("<H4>");
                    out.println("For a good discussion of how CGI scripts ");
                    out.println("work and what their environment variables ");
                    out.println("mean, please visit the <a ");
                    out.println("href=\"http://cgi-spec.golux.com\">CGI ");
                    out.println("Specification page</a>.");
                    out.println("</H4>");
                }

                printServletEnvironment(out, req, res);

                out.println("</BODY></HTML>");
            } catch (IOException ignored) {
            }
        }

    }

    /** 用于未来的测试; 现在什么也不做 */
    public static void main(String[] args) {
        System.out.println("$Header: /home/cvs/jakarta-tomcat-catalina/catalina/src/share/org/apache/catalina/servlets/CGIServlet.java,v 1.31 2004/11/29 23:00:30 markt Exp $");
    }

    /**
     * 获取所有shell环境变量. 必须这样做，这是相当丑陋的，因为获取的API在1.4和更早的API中是不可用的.
     *
     * See <a href="http://www.rgagnon.com/javadetails/java-0150.html">从应用程序读取环境变量</a> for 原始来源和文章.
     */
    private Hashtable getShellEnvironment() throws IOException {
        Hashtable envVars = new Hashtable();
        Process p = null;
        Runtime r = Runtime.getRuntime();
        String OS = System.getProperty("os.name").toLowerCase();
        boolean ignoreCase;

        if (OS.indexOf("windows 9") > -1) {
            p = r.exec( "command.com /c set" );
            ignoreCase = true;
        } else if ( (OS.indexOf("nt") > -1)
                || (OS.indexOf("windows 2000") > -1)
                || (OS.indexOf("windows xp") > -1) ) {
            // thanks to JuanFran for the xp fix!
            p = r.exec( "cmd.exe /c set" );
            ignoreCase = true;
        } else {
            // 最后的希望, 假设Unix (thanks to H. Ware for the fix)
            p = r.exec( "env" );
            ignoreCase = false;
        }

        BufferedReader br = new BufferedReader
            ( new InputStreamReader( p.getInputStream() ) );
        String line;
        while( (line = br.readLine()) != null ) {
            int idx = line.indexOf( '=' );
            String key = line.substring( 0, idx );
            String value = line.substring( idx+1 );
            if (ignoreCase) {
                key = key.toUpperCase();
            }
            envVars.put(key, value);
        }
        return envVars;
    }


    /**
     * 封装CGI环境和规则，以从servlet容器和请求信息中派生出该环境.
     */
    protected class CGIEnvironment {


        /** 封闭servlet的上下文  */
        private ServletContext context = null;

        /** 封闭servlet的上下文路径 */
        private String contextPath = null;

        /** 封闭servlet的servlet URI */
        private String servletPath = null;

        /** 当前请求的路径 */
        private String pathInfo = null;

        /** 封闭servlet Web应用程序的真正文件系统目录  */
        private String webAppRootDir = null;

        /** 上下文临时文件夹 - 用于扩展war中的脚本 */
        private File tmpDir = null;

        /** 衍生的CGI环境 */
        private Hashtable env = null;

        /** 要调用的CGI命令 */
        private String command = null;

        /** CGI命令所需的工作目录 */
        private File workingDirectory = null;

        /** CGI命令的查询参数 */
        private ArrayList queryParameters = new ArrayList();

        /** 这个对象是否有效 */
        private boolean valid = false;


        /**
         * 创建一个CGIEnvironment 并派生出必要的环境, 查询参数, 工作目录, CGI命令, etc.
         *
         * @param  req       HttpServletRequest for information provided by
         *                   the Servlet API
         * @param  context   ServletContext for information provided by the
         *                   Servlet API
         */
        protected CGIEnvironment(HttpServletRequest req,
                                 ServletContext context) throws IOException {
            setupFromContext(context);
            setupFromRequest(req);

            Enumeration paramNames = req.getParameterNames();
            while (paramNames != null && paramNames.hasMoreElements()) {
                String param = paramNames.nextElement().toString();
                if (param != null) {
                    String values[] = req.getParameterValues(param);
                    for (int i=0; i < values.length; i++) {
                        String value = URLEncoder.encode(values[i],
                                                         parameterEncoding);
                        NameValuePair nvp = new NameValuePair(param, value);
                        queryParameters.add(nvp);
                    }
                }
            }

            this.valid = setCGIEnvironment(req);

            if (this.valid) {
                workingDirectory = new File(command.substring(0,
                      command.lastIndexOf(File.separator)));
            }
        }



        /**
         * 使用ServletContext设置一些CGI变量
         *
         * @param  context   ServletContext for information provided by the
         *                   Servlet API
         */
        protected void setupFromContext(ServletContext context) {
            this.context = context;
            this.webAppRootDir = context.getRealPath("/");
            this.tmpDir = (File) context.getAttribute(Globals.WORK_DIR_ATTR);
        }



        /**
         * 使用HttpServletRequest设置大多数CGI变量
         *
         * @param  req   HttpServletRequest for information provided by
         *               the Servlet API
         */
        protected void setupFromRequest(HttpServletRequest req) {
            this.contextPath = req.getContextPath();
            this.servletPath = req.getServletPath();
            this.pathInfo = req.getPathInfo();
            // 如果getPathInfo() 返回 null, 必须使用扩展映射
            // 在这种情况下, pathInfo 应该和servletPath一样
            if (this.pathInfo == null) {
                this.pathInfo = this.servletPath;
            }
        }



        /**
         * 解析有关CGI脚本的核心信息.
         *
         * <p>
         * 例如URI:
         * <PRE> /servlet/cgigateway/dir1/realCGIscript/pathinfo1 </PRE>
         * <ul>
         * <LI><b>path</b> = $CATALINA_HOME/mywebapp/dir1/realCGIscript
         * <LI><b>scriptName</b> = /servlet/cgigateway/dir1/realCGIscript
         * <LI><b>cgiName</b> = /dir1/realCGIscript
         * <LI><b>name</b> = realCGIscript
         * </ul>
         * </p>
         * <p>
         * CGI的搜索算法: 搜索下面的真实路径
         *    &lt;my-webapp-root&gt; 并查找getPathTranslated("/")中的第一个非目录, 读取/搜索从左至右.
         *</p>
         *<p>
         *   CGI搜索路径：
         *   webAppRootDir + File.separator + cgiPathPrefix
         *   (只使用webAppRootDir，如果cgiPathPrefix是null).
         *</p>
         *<p>
         *   cgiPathPrefix是通过设置这个servlet的cgiPathPrefix 初始化参数定义的
         *</p>
         *
         * @param pathInfo       String from HttpServletRequest.getPathInfo()
         * @param webAppRootDir  String from context.getRealPath("/")
         * @param contextPath    String as from
         *                       HttpServletRequest.getContextPath()
         * @param servletPath    String as from
         *                       HttpServletRequest.getServletPath()
         * @param cgiPathPrefix  webAppRootDir下面的子目录，Web应用的CGI可以存储; 可以是 null.
         *                       CGI搜索路径：
         *   						webAppRootDir + File.separator + cgiPathPrefix
         *   						(只使用webAppRootDir，如果cgiPathPrefix是null).
         *   						cgiPathPrefix 通过设置servlet的 cgiPathPrefix 初始化参数指定.
         *
         * @return
         * <ul>
         * <li>
         * <code>path</code> -    有效的CGI脚本的完整的文件系统路径,或者null
         * <li>
         * <code>scriptName</code> - CGI变量SCRIPT_NAME; 有效CGI脚本的完整URL路径，或 null
         * <li>
         * <code>cgiName</code> - servlet路径信息片段对应于CGI脚本本身, 或null
         * <li>
         * <code>name</code> -    CGI脚本的简单名称（没有目录）, 或null
         * </ul>
         */
        protected String[] findCGI(String pathInfo, String webAppRootDir,
                                   String contextPath, String servletPath,
                                   String cgiPathPrefix) {
            String path = null;
            String name = null;
            String scriptname = null;
            String cginame = null;

            if ((webAppRootDir != null)
                && (webAppRootDir.lastIndexOf(File.separator) ==
                    (webAppRootDir.length() - 1))) {
                    //strip the trailing "/" from the webAppRootDir
                    webAppRootDir =
                    webAppRootDir.substring(0, (webAppRootDir.length() - 1));
            }

            if (cgiPathPrefix != null) {
                webAppRootDir = webAppRootDir + File.separator
                    + cgiPathPrefix;
            }

            if (debug >= 2) {
                log("findCGI: path=" + pathInfo + ", " + webAppRootDir);
            }

            File currentLocation = new File(webAppRootDir);
            StringTokenizer dirWalker =
            new StringTokenizer(pathInfo, "/");
            if (debug >= 3) {
                log("findCGI: currentLoc=" + currentLocation);
            }
            while (!currentLocation.isFile() && dirWalker.hasMoreElements()) {
                if (debug >= 3) {
                    log("findCGI: currentLoc=" + currentLocation);
                }
                currentLocation = new File(currentLocation,
                                           (String) dirWalker.nextElement());
            }
            if (!currentLocation.isFile()) {
                return new String[] { null, null, null, null };
            } else {
                if (debug >= 2) {
                    log("findCGI: FOUND cgi at " + currentLocation);
                }
                path = currentLocation.getAbsolutePath();
                name = currentLocation.getName();
                cginame =
                currentLocation.getParent().substring(webAppRootDir.length())
                + File.separator
                + name;

                if (".".equals(contextPath)) {
                    scriptname = servletPath + cginame;
                } else {
                    scriptname = contextPath + servletPath + cginame;
                }
            }

            if (debug >= 1) {
                log("findCGI calc: name=" + name + ", path=" + path
                    + ", scriptname=" + scriptname + ", cginame=" + cginame);
            }
            return new String[] { path, scriptname, cginame, name };
        }

        /**
         * 构建提供给CGI脚本的CGI环境; 依赖Servlet API方法和findCGI
         *
         * @param    req request associated with the CGI
         *           invokation
         *
         * @return   true if environment was set OK, false if there
         *           was a problem and no environment was set
         */
        protected boolean setCGIEnvironment(HttpServletRequest req) throws IOException {

            /*
             * This method is slightly ugly; c'est la vie.
             * "You cannot stop [ugliness], you can only hope to contain [it]"
             * (apologies to Marv Albert regarding MJ)
             */

            Hashtable envp = new Hashtable();

            // Add the shell environment variables (if any)
            envp.putAll(shellEnv);

            // Add the CGI environment variables
            String sPathInfoOrig = null;
            String sPathTranslatedOrig = null;
            String sPathInfoCGI = null;
            String sPathTranslatedCGI = null;
            String sCGIFullPath = null;
            String sCGIScriptName = null;
            String sCGIFullName = null;
            String sCGIName = null;
            String[] sCGINames;


            sPathInfoOrig = this.pathInfo;
            sPathInfoOrig = sPathInfoOrig == null ? "" : sPathInfoOrig;

            sPathTranslatedOrig = req.getPathTranslated();
            sPathTranslatedOrig =
                sPathTranslatedOrig == null ? "" : sPathTranslatedOrig;

            if (webAppRootDir == null ) {
                // The app has not been deployed in exploded form
                webAppRootDir = tmpDir.toString();
                expandCGIScript();
            } 
            
            sCGINames = findCGI(sPathInfoOrig,
                                webAppRootDir,
                                contextPath,
                                servletPath,
                                cgiPathPrefix);

            sCGIFullPath = sCGINames[0];
            sCGIScriptName = sCGINames[1];
            sCGIFullName = sCGINames[2];
            sCGIName = sCGINames[3];

            if (sCGIFullPath == null
                || sCGIScriptName == null
                || sCGIFullName == null
                || sCGIName == null) {
                return false;
            }

            envp.put("SERVER_SOFTWARE", "TOMCAT");

            envp.put("SERVER_NAME", nullsToBlanks(req.getServerName()));

            envp.put("GATEWAY_INTERFACE", "CGI/1.1");

            envp.put("SERVER_PROTOCOL", nullsToBlanks(req.getProtocol()));

            int port = req.getServerPort();
            Integer iPort = (port == 0 ? new Integer(-1) : new Integer(port));
            envp.put("SERVER_PORT", iPort.toString());

            envp.put("REQUEST_METHOD", nullsToBlanks(req.getMethod()));



            /*-
             * PATH_INFO should be determined by using sCGIFullName:
             * 1) Let sCGIFullName not end in a "/" (see method findCGI)
             * 2) Let sCGIFullName equal the pathInfo fragment which
             *    corresponds to the actual cgi script.
             * 3) Thus, PATH_INFO = request.getPathInfo().substring(
             *                      sCGIFullName.length())
             *
             * (see method findCGI, where the real work is done)
             *
             */
            if (pathInfo == null
                || (pathInfo.substring(sCGIFullName.length()).length() <= 0)) {
                sPathInfoCGI = "";
            } else {
                sPathInfoCGI = pathInfo.substring(sCGIFullName.length());
            }
            envp.put("PATH_INFO", sPathInfoCGI);


            /*-
             * PATH_TRANSLATED must be determined after PATH_INFO (and the
             * implied real cgi-script) has been taken into account.
             *
             * The following example demonstrates:
             *
             * servlet info   = /servlet/cgigw/dir1/dir2/cgi1/trans1/trans2
             * cgifullpath    = /servlet/cgigw/dir1/dir2/cgi1
             * path_info      = /trans1/trans2
             * webAppRootDir  = servletContext.getRealPath("/")
             *
             * path_translated = servletContext.getRealPath("/trans1/trans2")
             *
             * That is, PATH_TRANSLATED = webAppRootDir + sPathInfoCGI
             * (unless sPathInfoCGI is null or blank, then the CGI
             * specification dictates that the PATH_TRANSLATED metavariable
             * SHOULD NOT be defined.
             *
             */
            if (sPathInfoCGI != null && !("".equals(sPathInfoCGI))) {
                sPathTranslatedCGI = context.getRealPath(sPathInfoCGI);
            } else {
                sPathTranslatedCGI = null;
            }
            if (sPathTranslatedCGI == null || "".equals(sPathTranslatedCGI)) {
                //NOOP
            } else {
                envp.put("PATH_TRANSLATED", nullsToBlanks(sPathTranslatedCGI));
            }


            envp.put("SCRIPT_NAME", nullsToBlanks(sCGIScriptName));

            envp.put("QUERY_STRING", nullsToBlanks(req.getQueryString()));

            envp.put("REMOTE_HOST", nullsToBlanks(req.getRemoteHost()));

            envp.put("REMOTE_ADDR", nullsToBlanks(req.getRemoteAddr()));

            envp.put("AUTH_TYPE", nullsToBlanks(req.getAuthType()));

            envp.put("REMOTE_USER", nullsToBlanks(req.getRemoteUser()));

            envp.put("REMOTE_IDENT", ""); //not necessary for full compliance

            envp.put("CONTENT_TYPE", nullsToBlanks(req.getContentType()));


            /* Note CGI spec says CONTENT_LENGTH must be NULL ("") or undefined
             * if there is no content, so we cannot put 0 or -1 in as per the
             * Servlet API spec.
             */
            int contentLength = req.getContentLength();
            String sContentLength = (contentLength <= 0 ? "" :
                                     (new Integer(contentLength)).toString());
            envp.put("CONTENT_LENGTH", sContentLength);


            Enumeration headers = req.getHeaderNames();
            String header = null;
            while (headers.hasMoreElements()) {
                header = null;
                header = ((String) headers.nextElement()).toUpperCase();
                //REMIND: rewrite multiple headers as if received as single
                //REMIND: change character set
                //REMIND: I forgot what the previous REMIND means
                if ("AUTHORIZATION".equalsIgnoreCase(header) ||
                    "PROXY_AUTHORIZATION".equalsIgnoreCase(header)) {
                    //NOOP per CGI specification section 11.2
                } else {
                    envp.put("HTTP_" + header.replace('-', '_'),
                             req.getHeader(header));
                }
            }

            File fCGIFullPath = new File(sCGIFullPath);
            command = fCGIFullPath.getCanonicalPath();

            envp.put("X_TOMCAT_SCRIPT_PATH", command);  //for kicks

            this.env = envp;

            return true;

        }

        /**
         * 将请求的资源从Web应用程序存档提取到上下文工作目录，以便执行CGI脚本.
         */
        protected void expandCGIScript() {
            StringBuffer srcPath = new StringBuffer();
            StringBuffer destPath = new StringBuffer();
            InputStream is = null;

            // paths depend on mapping
            if (cgiPathPrefix == null ) {
                srcPath.append(pathInfo);
                is = context.getResourceAsStream(srcPath.toString());
                destPath.append(tmpDir);
                destPath.append(pathInfo);
            } else {
                // essentially same search algorithm as findCGI()
                srcPath.append(cgiPathPrefix);
                StringTokenizer pathWalker =
                        new StringTokenizer (pathInfo, "/");
                // start with first element
                while (pathWalker.hasMoreElements() && (is == null)) {
                    srcPath.append("/");
                    srcPath.append(pathWalker.nextElement());
                    is = context.getResourceAsStream(srcPath.toString());
                }
                destPath.append(tmpDir);
                destPath.append("/");
                destPath.append(srcPath);
            }

            if (is == null) {
                // didn't find anything, give up now
                if (debug >= 2) {
                    log("expandCGIScript: source '" + srcPath + "' not found");
                }
                 return;
            }

            File f = new File(destPath.toString());
            if (f.exists()) {
                // Don't need to expand if it already exists
                return;
            } 

            // create directories
            String dirPath = new String (destPath.toString().substring(
                    0,destPath.toString().lastIndexOf("/")));
            File dir = new File(dirPath);
            dir.mkdirs();

            try {
                synchronized (expandFileLock) {
                    // make sure file doesn't exist
                    if (f.exists()) {
                        return;
                    }

                    // create file
                    if (!f.createNewFile()) {
                        return;
                    }
                    FileOutputStream fos = new FileOutputStream(f);

                    // copy data
                    IOTools.flow(is, fos);
                    is.close();
                    fos.close();
                    if (debug >= 2) {
                        log("expandCGIScript: expanded '" + srcPath + "' to '" + destPath + "'");
                    }
                }
            } catch (IOException ioe) {
                // delete in case file is corrupted 
                if (f.exists()) {
                    f.delete();
                }
            }
        }


        public String toString() {
            StringBuffer sb = new StringBuffer();

            sb.append("<TABLE border=2>");

            sb.append("<tr><th colspan=2 bgcolor=grey>");
            sb.append("CGIEnvironment Info</th></tr>");

            sb.append("<tr><td>Debug Level</td><td>");
            sb.append(debug);
            sb.append("</td></tr>");

            sb.append("<tr><td>Validity:</td><td>");
            sb.append(isValid());
            sb.append("</td></tr>");

            if (isValid()) {
                Enumeration envk = env.keys();
                while (envk.hasMoreElements()) {
                    String s = (String) envk.nextElement();
                    sb.append("<tr><td>");
                    sb.append(s);
                    sb.append("</td><td>");
                    sb.append(blanksToString((String) env.get(s),
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

            sb.append("<tr><td colspan=2>Query Params</td></tr>");
            for (int i=0; i < queryParameters.size(); i++) {
                NameValuePair nvp = (NameValuePair) queryParameters.get(i);
                sb.append("<tr><td>");
                sb.append(nvp.getName());
                sb.append("</td><td>");
                sb.append(nvp.getValue());
                sb.append("</td></tr>");
            }
            sb.append("</TABLE><p>end.");
            return sb.toString();
        }



        /**
         * 获取派生命令字符串
         *
         * @return  command string
         */
        protected String getCommand() {
            return command;
        }



        /**
         * 获取派生的CGI工作目录
         *
         * @return  working directory
         */
        protected File getWorkingDirectory() {
            return workingDirectory;
        }



        /**
         * 获取派生的CGI环境
         *
         * @return   CGI environment
         */
        protected Hashtable getEnvironment() {
            return env;
        }



        /**
         * 获取派生的CGI查询参数
         *
         * @return   CGI query parameters
         */
        protected ArrayList getParameters() {
            return queryParameters;
        }



        /**
         * 获取有效状态
         *
         * @return   true if this environment is valid, false
         *           otherwise
         */
        protected boolean isValid() {
            return valid;
        }



        /**
         * 将null转换为空字符串("")
         *
         * @param    s string to be converted if necessary
         * @return   a non-null string, either the original or the empty string
         *           ("") if the original was <code>null</code>
         */
        protected String nullsToBlanks(String s) {
            return nullsToString(s, "");
        }



        /**
         * 将null转换为另一个字符串
         *
         * @param    couldBeNull string to be converted if necessary
         * @param    subForNulls string to return instead of a null string
         * @return   a non-null string, either the original or the substitute
         *           string if the original was <code>null</code>
         */
        protected String nullsToString(String couldBeNull,
                                       String subForNulls) {
            return (couldBeNull == null ? subForNulls : couldBeNull);
        }



        /**
         * 将空白字符串转换为另一个字符串
         *
         * @param    couldBeBlank string to be converted if necessary
         * @param    subForBlanks string to return instead of a blank string
         * @return   a non-null string, either the original or the substitute
         *           string if the original was <code>null</code> or empty ("")
         */
        protected String blanksToString(String couldBeBlank,
                                      String subForBlanks) {
            return (("".equals(couldBeBlank) || couldBeBlank == null)
                    ? subForBlanks
                    : couldBeBlank);
        }
    }


    /**
     * 封装了如何运行CGI脚本的知识, 给定脚本所需的环境和（可选）输入/输出流
     *
     * <p>
     * 暴露<code>run</code>方法用于实际调用CGI
     * </p>
     * <p>
     * CGI环境和设置传递给构造器.
     * </p>
     * <p>
     * 输入输出流可以通过<code>setInput</code>和<code>setResponse</code>方法设置, 分别地.
     * </p>
     */
    protected class CGIRunner {

        /** 要执行的脚本/命令 */
        private String command = null;

        /** 调用CGI脚本时使用的环境 */
        private Hashtable env = null;

        /** 执行CGI脚本时使用的工作目录 */
        private File wd = null;

        /** 要传递给被调用脚本的查询参数  */
        private ArrayList params = null;

        /** 输入要传递给CGI脚本 */
        private InputStream stdin = null;

        /** 用于设置标头和获取输出流的响应对象 */
        private HttpServletResponse response = null;

        /** 该对象是否有足够的信息来run() */
        private boolean readyToRun = false;



        /**
         *  创建一个CGIRunner 并初始化它的环境, 工作目录, 和查询参数.
         *  <BR>
         *  使用<code>setInput</code>和<code>setResponse</code>方法设置输入输出流.
         *
         * @param  command  要执行的命令的字符串完整路径
         * @param  env      Hashtable 所需的脚本环境
         * @param  wd       使用脚本所需的工作目录
         * @param  params   Hashtable使用脚本的查询参数
         */
        protected CGIRunner(String command, Hashtable env, File wd,
                            ArrayList params) {
            this.command = command;
            this.env = env;
            this.wd = wd;
            this.params = params;
            updateReadyStatus();
        }



        /**
         * Checks & sets ready status
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
         * Gets ready status
         *
         * @return   false if not ready (<code>run</code> will throw
         *           an exception), true if ready
         */
        protected boolean isReady() {
            return readyToRun;
        }



        /**
         * 设置HttpServletResponse对象用于设置header和发送输出
         *
         * @param  response   HttpServletResponse to be used
         */
        protected void setResponse(HttpServletResponse response) {
            this.response = response;
            updateReadyStatus();
        }



        /**
         * 设置要传递给调用的CGI脚本的标准输入
         *
         * @param  stdin   InputStream to be used
         */
        protected void setInput(InputStream stdin) {
            this.stdin = stdin;
            updateReadyStatus();
        }



        /**
         * 转换Hashtable成String数组，通过转换每个键值对成一个String,格式为"key=value" (hashkey + "=" + hash.get(hashkey).toString())
         *
         * @param  h   Hashtable to convert
         *
         * @return     converted string array
         *
         * @exception  NullPointerException   if a hash key has a null value
         *
         */
        protected String[] hashToStringArray(Hashtable h)
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
         * 使用所需环境、当前工作目录和输入/输出流执行CGI脚本
         *
         * <p>
         * 实现了以下CGI规范的建议:
         * <UL>
         * <LI> 服务器应该将脚本URI的“查询”组件作为脚本的命令行参数提供给脚本, 如果它不包含任何非编码“=”字符和命令行参数，可以生成一个明确的方式.
         * <LI> See <code>getCGIEnvironment</code> method.
         * <LI> 在适用的情况下，服务器应该在调用脚本之前将当前工作目录设置为脚本所在的目录.
         * <LI> 服务器实现应该为下列情况定义其行为:
         *     <ul>
         *     <LI> <u>允许的字符是</u>:  此实现不允许ASCII NUL或任何字符不能URL编码根据互联网标准;
         *     <LI> <u>路径段中允许的字符</u>: 此实现不允许路径中的非终结符空段 -- IOExceptions may be thrown;
         *     <LI> <u>"<code>.</code>" and "<code>..</code>" 路径</u>:
         *             此实现不允许"<code>.</code>" 和
         *             "<code>..</code>" 包含在路径中, 这样字符会通过IOException异常被抛出;
         *     <LI> <u>实现限制</u>: 除了上述记录外，此实现没有任何限制. 此实现可能受到用于保存此实现的servlet容器的限制.
         *             特别是，所有主要CGI变量值都是直接或间接从容器的servlet API方法的实现派生的.
         *     </ul>
         * </UL>
         * </p>
         *
         * @exception IOException if problems during reading/writing occur
         */
        protected void run() throws IOException {

            /*
             * REMIND:  this method feels too big; should it be re-written?
             */

            if (!isReady()) {
                throw new IOException(this.getClass().getName()
                                      + ": not ready to run.");
            }

            if (debug >= 1 ) {
                log("runCGI(envp=[" + env + "], command=" + command + ")");
            }

            if ((command.indexOf(File.separator + "." + File.separator) >= 0)
                || (command.indexOf(File.separator + "..") >= 0)
                || (command.indexOf(".." + File.separator) >= 0)) {
                throw new IOException(this.getClass().getName()
                                      + "Illegal Character in CGI command "
                                      + "path ('.' or '..') detected.  Not "
                                      + "running CGI [" + command + "].");
            }

            /* original content/structure of this section taken from
             * http://developer.java.sun.com/developer/
             *                               bugParade/bugs/4216884.html
             * with major modifications by Martin Dengler
             */
            Runtime rt = null;
            BufferedReader commandsStdOut = null;
            InputStream cgiOutput = null;
            BufferedReader commandsStdErr = null;
            BufferedOutputStream commandsStdIn = null;
            Process proc = null;
            int bufRead = -1;

            //create query arguments
            StringBuffer cmdAndArgs = new StringBuffer();
            if (command.indexOf(" ") < 0) {
                cmdAndArgs.append(command);
            } else {
                // Spaces used as delimiter, so need to use quotes
                cmdAndArgs.append("\"");
                cmdAndArgs.append(command);
                cmdAndArgs.append("\"");
            }

            for (int i=0; i < params.size(); i++) {
                cmdAndArgs.append(" ");
                NameValuePair nvp = (NameValuePair) params.get(i); 
                String k = nvp.getName();
                String v = nvp.getValue();
                if ((k.indexOf("=") < 0) && (v.indexOf("=") < 0)) {
                    StringBuffer arg = new StringBuffer(k);
                    arg.append("=");
                    arg.append(v);
                    if (arg.toString().indexOf(" ") < 0) {
                        cmdAndArgs.append(arg);
                    } else {
                        // Spaces used as delimiter, so need to use quotes
                        cmdAndArgs.append("\"");
                        cmdAndArgs.append(arg);
                        cmdAndArgs.append("\"");
                    }
                }
            }

            StringBuffer command = new StringBuffer(cgiExecutable);
            command.append(" ");
            command.append(cmdAndArgs.toString());
            cmdAndArgs = command;

            String sContentLength = (String) env.get("CONTENT_LENGTH");
            ByteArrayOutputStream contentStream = null;
            if(!"".equals(sContentLength)) {
                byte[] content = new byte[Integer.parseInt(sContentLength)];

                // Bugzilla 32023
                int lenRead = 0;
                do {
                    int partRead = stdin.read(content,lenRead,content.length-lenRead);
                    lenRead += partRead;
                } while (lenRead > 0 && lenRead < content.length);

                contentStream = new ByteArrayOutputStream(
                        Integer.parseInt(sContentLength));
                if ("POST".equals(env.get("REQUEST_METHOD"))) {
                    String paramStr = getPostInput(params);
                    if (paramStr != null) {
                        byte[] paramBytes = paramStr.getBytes();
                        contentStream.write(paramBytes);

                        int contentLength = paramBytes.length;
                        if (lenRead > 0) {
                            String lineSep = System.getProperty("line.separator");
                            contentStream.write(lineSep.getBytes());
                            contentLength = lineSep.length() + lenRead;
                        }
                        env.put("CONTENT_LENGTH", Integer.toString(contentLength));
                    }
                }

                if (lenRead > 0) {
                    contentStream.write(content, 0, lenRead);
                }
                contentStream.close();
            }

            rt = Runtime.getRuntime();
            proc = rt.exec(cmdAndArgs.toString(), hashToStringArray(env), wd);

            if(contentStream != null) {
                commandsStdIn = new BufferedOutputStream(proc.getOutputStream());
                commandsStdIn.write(contentStream.toByteArray());
                commandsStdIn.flush();
                commandsStdIn.close();
            }

            /* 我们要等待进程退出,  Process.waitFor()是无效的; see
             * http://developer.java.sun.com/developer/
             *                               bugParade/bugs/4223650.html
             */

            boolean isRunning = true;
            commandsStdErr = new BufferedReader
                (new InputStreamReader(proc.getErrorStream()));
            BufferedWriter servletContainerStdout = null;

            try {
                if (response.getOutputStream() != null) {
                    servletContainerStdout =
                        new BufferedWriter(new OutputStreamWriter
                            (response.getOutputStream()));
                }
            } catch (IOException ignored) {
                //NOOP: no output will be written
            }
            final BufferedReader stdErrRdr = commandsStdErr ;

            new Thread() {
                public void run () {
                    sendToLog(stdErrRdr) ;
                } ;
            }.start() ;

            InputStream cgiHeaderStream =
                new HTTPHeaderInputStream(proc.getInputStream());
            BufferedReader cgiHeaderReader =
                new BufferedReader(new InputStreamReader(cgiHeaderStream));
            boolean isBinaryContent = false;
            
            while (isRunning) {
                try {
                    //set headers
                    String line = null;
                    while (((line = cgiHeaderReader.readLine()) != null)
                           && !("".equals(line))) {
                        if (debug >= 2) {
                            log("runCGI: addHeader(\"" + line + "\")");
                        }
                        if (line.startsWith("HTTP")) {
                            //TODO: should set status codes (NPH support)
                            /*
                             * response.setStatus(getStatusCode(line));
                             */
                        } else if (line.indexOf(":") >= 0) {
                            String header =
                                line.substring(0, line.indexOf(":")).trim();
                            String value =
                                line.substring(line.indexOf(":") + 1).trim(); 
                            response.addHeader(header , value);
                            if ((header.toLowerCase().equals("content-type"))
                                && (!value.toLowerCase().startsWith("text"))) {
                                isBinaryContent = true;
                            }
                        } else {
                            log("runCGI: bad header line \"" + line + "\"");
                        }
                    }

                    //write output
                    if (isBinaryContent) {
                        byte[] bBuf = new byte[2048];
                        OutputStream out = response.getOutputStream();
                        cgiOutput = proc.getInputStream();
                        while ((bufRead = cgiOutput.read(bBuf)) != -1) {
                            if (debug >= 4) {
                                log("runCGI: output " + bufRead +
                                    " bytes of binary data");
                            }
                            out.write(bBuf, 0, bufRead);
                        }
                    } else {
                        commandsStdOut = new BufferedReader
                            (new InputStreamReader(proc.getInputStream()));

                        char[] cBuf = new char[1024];
                        try {
                            while ((bufRead = commandsStdOut.read(cBuf)) != -1) {
                                if (servletContainerStdout != null) {
                                    if (debug >= 4) {
                                        log("runCGI: write(\"" +
                                                new String(cBuf, 0, bufRead) + "\")");
                                    }
                                    servletContainerStdout.write(cBuf, 0, bufRead);
                                }
                            }
                        } finally {
                            // Attempt to consume any leftover byte if something bad happens,
                            // such as a socket disconnect on the servlet side; otherwise, the
                            // external process could hang
                            if (bufRead != -1) {
                                while ((bufRead = commandsStdOut.read(cBuf)) != -1) {}
                            }
                        }
    
                        if (servletContainerStdout != null) {
                            servletContainerStdout.flush();
                        }
                    }

                    proc.exitValue(); // Throws exception if alive

                    isRunning = false;

                } catch (IllegalThreadStateException e) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {
                    }
                }
            } //replacement for Process.waitFor()
            // Close the output stream used
            if (isBinaryContent) {
                cgiOutput.close();
            } else {
                commandsStdOut.close();
            }
        }

        private void sendToLog(BufferedReader rdr) {
            String line = null;
            int lineCount = 0 ;
            try {
                while ((line = rdr.readLine()) != null) {
                    log("runCGI (stderr):" +  line) ;
                    lineCount++ ;
                }
            } catch (IOException e) {
                log("sendToLog error", e) ;
            } finally {
                try {
                    rdr.close() ;
                } catch (IOException ce) {
                    log("sendToLog error", ce) ;
                } ;
            } ;
            if ( lineCount > 0 && debug > 2) {
                log("runCGI: " + lineCount + " lines received on stderr") ;
            } ;
        }


        /**
         * 获取输入到POST CGI脚本的字符串
         *
         * @param  params   要传递给CGI脚本的查询参数
         * @return          用作CGI脚本的输入
         */
        protected String getPostInput(ArrayList params) {
            String lineSeparator = System.getProperty("line.separator");
            StringBuffer qs = new StringBuffer("");
            for (int i=0; i < params.size(); i++) {
                NameValuePair nvp = (NameValuePair) this.params.get(i); 
                String k = nvp.getName();
                String v = nvp.getValue();
                if ((k.indexOf("=") < 0) && (v.indexOf("=") < 0)) {
                    qs.append(k);
                    qs.append("=");
                    qs.append(v);
                    qs.append("&");
                }
            }
            if (qs.length() > 0) {
                // Remove last "&"
                qs.setLength(qs.length() - 1);
                return qs.toString();
            } else {
                return null;
            }
        }
    }

    /**
     * 这是一个简单的类，用于存储名称值对.
     * 
     * TODO: 对这个功能有更广泛的要求, 移动这个工具包是值得的.
     */
    protected class NameValuePair {
        private String name;
        private String value;
        
        NameValuePair(String name, String value) {
            this.name = name;
            this.value = value;
        }
        
        protected String getName() {
            return name;
        }
        
        protected String getValue() {
            return value;
        }
    }

    /**
     * 这是一个专门用于读取HTTP头的输入流. 它读取最多，包括两个终止header的空行. 它允许使用字节或字符作为适当的内容读取.
     */
    protected class HTTPHeaderInputStream extends InputStream {
        private static final int STATE_CHARACTER = 0;
        private static final int STATE_FIRST_CR = 1;
        private static final int STATE_FIRST_LF = 2;
        private static final int STATE_SECOND_CR = 3;
        private static final int STATE_HEADER_END = 4;
        
        private InputStream input;
        private int state;
        
        HTTPHeaderInputStream(InputStream theInput) {
            input = theInput;
            state = STATE_CHARACTER;
        }

        /**
         * @see java.io.InputStream#read()
         */
        public int read() throws IOException {
            if (state == STATE_HEADER_END) {
                return -1;
            }

            int i = input.read();

            // Update the state
            // State machine looks like this
            //
            //    -------->--------
            //   |      (CR)       |
            //   |                 |
            //  CR1--->---         |
            //   |        |        |
            //   ^(CR)    |(LF)    |
            //   |        |        |
            // CHAR--->--LF1--->--EOH
            //      (LF)  |  (LF)  |
            //            |(CR)    ^(LF)
            //            |        |
            //          (CR2)-->---
            
            if (i == 10) {
                // LF
                switch(state) {
                    case STATE_CHARACTER:
                        state = STATE_FIRST_LF;
                        break;
                    case STATE_FIRST_CR:
                        state = STATE_FIRST_LF;
                        break;
                    case STATE_FIRST_LF:
                    case STATE_SECOND_CR:
                        state = STATE_HEADER_END;
                        break;
                }

            } else if (i == 13) {
                // CR
                switch(state) {
                    case STATE_CHARACTER:
                        state = STATE_FIRST_CR;
                        break;
                    case STATE_FIRST_CR:
                        state = STATE_HEADER_END;
                        break;
                    case STATE_FIRST_LF:
                        state = STATE_SECOND_CR;
                        break;
                }

            } else {
                state = STATE_CHARACTER;
            }
            
            return i;            
        }
    }

}
