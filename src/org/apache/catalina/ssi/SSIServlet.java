package org.apache.catalina.ssi;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.catalina.Globals;
/**
 * 在网页中处理SSI请求的Servlet. 映射到一个路径在web.xml.
 */
public class SSIServlet extends HttpServlet {
    /** 调试等级 . */
    protected int debug = 0;
    /** 输出应该缓冲吗. */
    protected boolean buffered = false;
    /** 到期时间, 秒. */
    protected Long expires = null;
    /** 虚拟路径, 可以是webapp相对路径 */
    protected boolean isVirtualWebappRelative = false;
    /** 输入编码. 如果未指定, 使用平台默认的 */
    protected String inputEncoding = null;
    /** 输出编码. 如果未指定, 使用平台默认的 */
    protected String outputEncoding = "UTF-8";


    //----------------- Public methods.
    /**
     * 初始化这个servlet.
     * 
     * @exception ServletException if an error occurs
     */
    public void init() throws ServletException {
        String value = null;
        try {
            value = getServletConfig().getInitParameter("debug");
            debug = Integer.parseInt(value);
        } catch (Throwable t) {
            ;
        }
        try {
            value = getServletConfig().getInitParameter(
                    "isVirtualWebappRelative");
            isVirtualWebappRelative = Integer.parseInt(value) > 0?true:false;
        } catch (Throwable t) {
            ;
        }
        try {
            value = getServletConfig().getInitParameter("expires");
            expires = Long.valueOf(value);
        } catch (NumberFormatException e) {
            expires = null;
            log("Invalid format for expires initParam; expected integer (seconds)");
        } catch (Throwable t) {
            ;
        }
        try {
            value = getServletConfig().getInitParameter("buffered");
            buffered = Integer.parseInt(value) > 0?true:false;
        } catch (Throwable t) {
            ;
        }
        try {
            inputEncoding = getServletConfig().getInitParameter("inputEncoding");
        } catch (Throwable t) {
            ;
        }
        try {
            value = getServletConfig().getInitParameter("outputEncoding");
            if (value != null) {
                outputEncoding = value;
            }
        } catch (Throwable t) {
            ;
        }
        if (debug > 0)
            log("SSIServlet.init() SSI invoker started with 'debug'=" + debug);
    }


    /**
     * 处理并转发GET请求
     * 
     * @param req a value of type 'HttpServletRequest'
     * @param res a value of type 'HttpServletResponse'
     * @exception IOException if an error occurs
     * @exception ServletException if an error occurs
     */
    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {
        if (debug > 0) log("SSIServlet.doGet()");
        requestHandler(req, res);
    }


    /**
     * 处理并转发POST请求
     * 
     * @param req a value of type 'HttpServletRequest'
     * @param res a value of type 'HttpServletResponse'
     * @exception IOException if an error occurs
     * @exception ServletException if an error occurs
     */
    public void doPost(HttpServletRequest req, HttpServletResponse res)
            throws IOException, ServletException {
        if (debug > 0) log("SSIServlet.doPost()");
        requestHandler(req, res);
    }


    /**
     * 处理请求并找到正确的SSI命令.
     * 
     * @param req a value of type 'HttpServletRequest'
     * @param res a value of type 'HttpServletResponse'
     */
    protected void requestHandler(HttpServletRequest req,
            HttpServletResponse res) throws IOException, ServletException {
        ServletContext servletContext = getServletContext();
        String path = SSIServletRequestUtil.getRelativePath(req);
        if (debug > 0)
            log("SSIServlet.requestHandler()\n" + "Serving "
                    + (buffered?"buffered ":"unbuffered ") + "resource '"
                    + path + "'");
        // 不包括任何 /WEB-INF 和 /META-INF 子目录中的资源
        // ("toUpperCase()" 避免Windows系统上的问题)
        if (path == null || path.toUpperCase().startsWith("/WEB-INF")
                || path.toUpperCase().startsWith("/META-INF")) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND, path);
            log("Can't serve file: " + path);
            return;
        }
        URL resource = servletContext.getResource(path);
        if (resource == null) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND, path);
            log("Can't find file: " + path);
            return;
        }
        String resourceMimeType = servletContext.getMimeType(path);
        if (resourceMimeType == null) {
            resourceMimeType = "text/html";
        }
        res.setContentType(resourceMimeType + ";charset=" + outputEncoding);
        if (expires != null) {
            res.setDateHeader("Expires", (new java.util.Date()).getTime()
                    + expires.longValue() * 1000);
        }
        req.setAttribute(Globals.SSI_FLAG_ATTR, "true");
        processSSI(req, res, resource);
    }


    protected void processSSI(HttpServletRequest req, HttpServletResponse res,
            URL resource) throws IOException {
        SSIExternalResolver ssiExternalResolver =
            new SSIServletExternalResolver(getServletContext(), req, res,
                    isVirtualWebappRelative, debug, inputEncoding);
        SSIProcessor ssiProcessor = new SSIProcessor(ssiExternalResolver,
                debug);
        PrintWriter printWriter = null;
        StringWriter stringWriter = null;
        if (buffered) {
            stringWriter = new StringWriter();
            printWriter = new PrintWriter(stringWriter);
        } else {
            printWriter = res.getWriter();
        }

        URLConnection resourceInfo = resource.openConnection();
        InputStream resourceInputStream = resourceInfo.getInputStream();
        String encoding = resourceInfo.getContentEncoding();
        if (encoding == null) {
            encoding = inputEncoding;
        }
        InputStreamReader isr;
        if (encoding == null) {
            isr = new InputStreamReader(resourceInputStream);
        } else {
            isr = new InputStreamReader(resourceInputStream, encoding);
        }
        BufferedReader bufferedReader = new BufferedReader(isr);

        long lastModified = ssiProcessor.process(bufferedReader,
                resourceInfo.getLastModified(), printWriter);
        if (lastModified > 0) {
            res.setDateHeader("last-modified", lastModified);
        }
        if (buffered) {
            printWriter.flush();
            String text = stringWriter.toString();
            res.getWriter().write(text);
        }
    }
}