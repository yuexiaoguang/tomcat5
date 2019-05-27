package org.apache.catalina.util;

import java.io.File;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.StringTokenizer;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;


/**
 * 封装 CGI 处理的环境和规则, 从servlet容器和请求信息中获取该环境.
 */
public class CGIProcessEnvironment extends ProcessEnvironment {
    
    
    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( CGIProcessEnvironment.class );
    
    /** CGI命令的查询参数  */
    private Hashtable queryParameters = null;

    /**
     *  CGI 搜索路径将从
     *  webAppRootDir + File.separator + cgiPathPrefix
     *  (或只有webAppRootDir, 如果cgiPathPrefix 是null)
     */
    private String cgiPathPrefix = null;


    /**
     * 创建一个ProcessEnvironment 并获取必要的环境, 工作目录, 命令, 等.
     * CGI路径前缀被初始化为"" (空字符串).
     *
     * @param  req       用于servlet API提供信息的HttpServletRequest
     * @param  context   用于servlet API提供信息的ServletContext
     */
    public CGIProcessEnvironment(HttpServletRequest req,
        ServletContext context) {
            this(req, context, "");
    }



    /**
     * 创建一个ProcessEnvironment 并获取必要的环境, 工作目录, 命令, 等.
     * @param req             HttpServletRequest for information provided by
     *                        the Servlet API
     * @param context         ServletContext for information provided by
     *                        the Servlet API
     * @param cgiPathPrefix   Web应用的CGI可以存储的webAppRootDir子目录; 可以是null 或 "".
     */
    public CGIProcessEnvironment(HttpServletRequest req,
        ServletContext context, String cgiPathPrefix) {
            this(req, context, cgiPathPrefix, 0);
    }



    /**
     * 创建一个ProcessEnvironment 并获取必要的环境, 工作目录, 命令, 等.
     * @param req             HttpServletRequest for information provided by
     *                        the Servlet API
     * @param context         ServletContext for information provided by
     *                        the Servlet API
     * @param  debug          int debug level (0 == none, 6 == lots)
     */
    public CGIProcessEnvironment(HttpServletRequest req,
        ServletContext context, int debug) {
            this(req, context, "", 0);
    }




    /**
     * 创建一个ProcessEnvironment 并获取必要的环境, 工作目录, 命令, 等.
     * @param req             HttpServletRequest for information provided by
     *                        the Servlet API
     * @param context         ServletContext for information provided by
     *                        the Servlet API
     * @param cgiPathPrefix   Web应用的CGI可以存储的webAppRootDir子目录; 可以是null 或 "".
     * @param  debug          int debug level (0 == none, 6 == lots)
     */
    public CGIProcessEnvironment(HttpServletRequest req,
        ServletContext context, String cgiPathPrefix, int debug) {
            super(req, context, debug);
            this.cgiPathPrefix = cgiPathPrefix;
            queryParameters = new Hashtable();
            Enumeration paramNames = req.getParameterNames();
            while (paramNames != null && paramNames.hasMoreElements()) {
                String param = paramNames.nextElement().toString();
                if (param != null) {
                    queryParameters.put(param,
                        URLEncoder.encode(req.getParameter(param)));
                }
            }
            this.valid = deriveProcessEnvironment(req);
    }



    /**
     * 构建要提供给被调用的CGI脚本的CGI环境; 依赖Servlet API方法和findCGI
     * @param    HttpServletRequest 与CGI调用相关的请求
     * @return   true : 如果环境设置为 OK, false : 如果有问题，没有环境被设置
     */
    protected boolean deriveProcessEnvironment(HttpServletRequest req) {
        /*
         * This method is slightly ugly; c'est la vie.
         * "You cannot stop [ugliness], you can only hope to contain [it]"
         * (apologies to Marv Albert regarding MJ)
         */
        Hashtable envp;
        super.deriveProcessEnvironment(req);
        envp = getEnvironment();

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
        sPathTranslatedOrig = sPathTranslatedOrig == null ? "" :
            sPathTranslatedOrig;
            sCGINames =
                findCGI(sPathInfoOrig, getWebAppRootDir(), getContextPath(),
                getServletPath(), cgiPathPrefix);
        sCGIFullPath = sCGINames[0];
        sCGIScriptName = sCGINames[1];
        sCGIFullName = sCGINames[2];
        sCGIName = sCGINames[3];
        if (sCGIFullPath == null || sCGIScriptName == null
            || sCGIFullName == null || sCGIName == null) {
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

        if (pathInfo == null ||
            (pathInfo.substring(sCGIFullName.length()).length() <= 0)) {
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
            sPathTranslatedCGI = getContext().getRealPath(sPathInfoCGI);
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
        String sContentLength = (contentLength <= 0 ? "" : (
            new Integer(contentLength)).toString());
        envp.put("CONTENT_LENGTH", sContentLength);
        Enumeration headers = req.getHeaderNames();
        String header = null;
        while (headers.hasMoreElements()) {
            header = null;
            header = ((String)headers.nextElement()).toUpperCase();
            //REMIND: rewrite multiple headers as if received as single
            //REMIND: change character set
            //REMIND: I forgot what the previous REMIND means
            if ("AUTHORIZATION".equalsIgnoreCase(header)
                || "PROXY_AUTHORIZATION".equalsIgnoreCase(header)) {
                    //NOOP per CGI specification section 11.2
            } else if ("HOST".equalsIgnoreCase(header)) {
                String host = req.getHeader(header);
                envp.put("HTTP_" + header.replace('-', '_'),
                    host.substring(0, host.indexOf(":")));
            } else {
                envp.put("HTTP_" + header.replace('-', '_'),
                    req.getHeader(header));
            }
        }
        command = sCGIFullPath;
        workingDirectory = new File(command.substring(0,
            command.lastIndexOf(File.separator)));
        envp.put("X_TOMCAT_COMMAND_PATH", command); //for kicks
        this.setEnvironment(envp);
        return true;
    }


    /**
     * 解析有关CGI脚本的核心信息. 
     * <p> Example URI:
     * <PRE> /servlet/cgigateway/dir1/realCGIscript/pathinfo1 </PRE> <ul>
     * <LI><b>path</b> = $CATALINA_HOME/mywebapp/dir1/realCGIscript
     * <LI><b>scriptName</b> = /servlet/cgigateway/dir1/realCGIscript</LI>
     * <LI><b>cgiName</b> = /dir1/realCGIscript
     * <LI><b>name</b> = realCGIscript
     * </ul>
     * </p>
     * <p>
     * CGI的搜索算法: 搜索&lt;my-webapp-root&gt下面的真实路径; 并在getPathTranslated("/")找到第一个非目录, 从左到右reading/searching.
     * </p>
     * <p>
     * CGI搜索路径将从下面开始
     * webAppRootDir + File.separator + cgiPathPrefix (或者只有webAppRootDir, 如果cgiPathPrefix是null).
     * </p>
     * <p>
     * cgiPathPrefix通常通过调用servlet设置为servlet的cgiPathPrefix 初始化参数
     * </p>
     *
     * @param pathInfo       String from HttpServletRequest.getPathInfo()
     * @param webAppRootDir  String from context.getRealPath("/")
     * @param contextPath    String as from HttpServletRequest.getContextPath()
     * @param servletPath    String as from HttpServletRequest.getServletPath()
     * @param cgiPathPrefix  Web应用的CGI可以存储的webAppRootDir子目录; 可以是null 或 "".
     * @return
     * <ul> <li> <code>path</code>  -    有效CGI脚本的完整的文件系统路径，或NULL如果没有CGI被找到
     * <li> <code>scriptName</code> -    SCRIPT_NAME的CGI 变量; 有效CGI脚本的URL，或NULL如果没有CGI被找到
     * <li> <code>cgiName</code>    -    对应于CGI脚本本身的Servlet路径片段，或NULL如果没有找到
     * <li> <code>name</code>       -    CGI脚本的简单的名字(没有目录),或NULL如果没有CGI被找到
     * </ul>
     */
    protected String[] findCGI(String pathInfo, String webAppRootDir,
        String contextPath, String servletPath, String cgiPathPrefix) {
            String path = null;
            String name = null;
            String scriptname = null;
            String cginame = null;
            if ((webAppRootDir != null)
                && (webAppRootDir.lastIndexOf("/")
                == (webAppRootDir.length() - 1))) {
                    //strip the trailing "/" from the webAppRootDir
                    webAppRootDir =
                        webAppRootDir.substring(0,
                        (webAppRootDir.length() - 1));
            }
            if (cgiPathPrefix != null) {
                webAppRootDir = webAppRootDir + File.separator
                    + cgiPathPrefix;
            }
            
            if (log.isDebugEnabled()) {
                log.debug("findCGI: start = [" + webAppRootDir
                    + "], pathInfo = [" + pathInfo + "] ");
            }
            File currentLocation = new File(webAppRootDir);
            StringTokenizer dirWalker = new StringTokenizer(pathInfo, "/");
            while (!currentLocation.isFile() && dirWalker.hasMoreElements()) {
                currentLocation = new
                    File(currentLocation, (String) dirWalker.nextElement());
                if (log.isDebugEnabled())  {
                    log.debug("findCGI: traversing to [" + currentLocation + "]");
                }
            }
            if (!currentLocation.isFile()) {
                return new String[] { null, null, null, null };
            } else {
                if (log.isDebugEnabled())  {
                    log.debug("findCGI: FOUND cgi at [" + currentLocation + "]");
                }
                path = currentLocation.getAbsolutePath();
                name = currentLocation.getName();
                cginame = currentLocation.getParent()
                    .substring(webAppRootDir.length())
                    + File.separator + name;
                    if (".".equals(contextPath)) {
                        scriptname = servletPath + cginame;
                } else {
                    scriptname = contextPath + servletPath + cginame;
                }
            }
            if (log.isDebugEnabled())  {
                log.debug("findCGI calc: name=" + name + ", path=" + path
                    + ", scriptname=" + scriptname + ", cginame=" + cginame);
            }
            return new String[] { path, scriptname, cginame, name };
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
        sb.append("<tr><td colspan=2>Query Params</td></tr>");
        Enumeration paramk = queryParameters.keys();
        while (paramk.hasMoreElements()) {
            String s = paramk.nextElement().toString();
            sb.append("<tr><td>");
            sb.append(s);
            sb.append("</td><td>");
            sb.append(queryParameters.get(s).toString());
            sb.append("</td></tr>");
        }

        sb.append("</TABLE><p>end.");
        return sb.toString();
    }


    /**
     * 获取进程派生的查询参数
     * @return process' query parameters
     */
    public Hashtable getParameters() {
        return queryParameters;
    }
}
