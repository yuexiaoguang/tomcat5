package org.apache.catalina.valves;


import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;


/**
 * <p>Valve实现类，从指定的请求记录感兴趣的内容 (处理之前) 以及相应的Response (处理之后).
 * 它特别适用于调试与标头和cookie相关的问题.</p>
 *
 * <p>这个Valve 可以连接到任何Container, 取决于您希望执行的日志的粒度.</p>
 */
public class RequestDumperValve extends ValveBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * 实现类描述信息.
     */
    private static final String info =
        "org.apache.catalina.valves.RequestDumperValve/1.0";


    /**
     * The StringManager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);


    // ------------------------------------------------------------- Properties


    /**
     * 返回实现类的描述信息.
     */
    public String getInfo() {
        return (info);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 记录感兴趣的请求参数, 执行序列中的下一个Valve, 并记录感兴趣的响应参数.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        Log log = container.getLogger();
        
        // Log pre-service information
        log.info("REQUEST URI       =" + request.getRequestURI());
        log.info("          authType=" + request.getAuthType());
        log.info(" characterEncoding=" + request.getCharacterEncoding());
        log.info("     contentLength=" + request.getContentLength());
        log.info("       contentType=" + request.getContentType());
        log.info("       contextPath=" + request.getContextPath());
        Cookie cookies[] = request.getCookies();
        if (cookies != null) {
            for (int i = 0; i < cookies.length; i++)
                log.info("            cookie=" + cookies[i].getName() + "=" +
                    cookies[i].getValue());
        }
        Enumeration hnames = request.getHeaderNames();
        while (hnames.hasMoreElements()) {
            String hname = (String) hnames.nextElement();
            Enumeration hvalues = request.getHeaders(hname);
            while (hvalues.hasMoreElements()) {
                String hvalue = (String) hvalues.nextElement();
                log.info("            header=" + hname + "=" + hvalue);
            }
        }
        log.info("            locale=" + request.getLocale());
        log.info("            method=" + request.getMethod());
        Enumeration pnames = request.getParameterNames();
        while (pnames.hasMoreElements()) {
            String pname = (String) pnames.nextElement();
            String pvalues[] = request.getParameterValues(pname);
            StringBuffer result = new StringBuffer(pname);
            result.append('=');
            for (int i = 0; i < pvalues.length; i++) {
                if (i > 0)
                    result.append(", ");
                result.append(pvalues[i]);
            }
            log.info("         parameter=" + result.toString());
        }
        log.info("          pathInfo=" + request.getPathInfo());
        log.info("          protocol=" + request.getProtocol());
        log.info("       queryString=" + request.getQueryString());
        log.info("        remoteAddr=" + request.getRemoteAddr());
        log.info("        remoteHost=" + request.getRemoteHost());
        log.info("        remoteUser=" + request.getRemoteUser());
        log.info("requestedSessionId=" + request.getRequestedSessionId());
        log.info("            scheme=" + request.getScheme());
        log.info("        serverName=" + request.getServerName());
        log.info("        serverPort=" + request.getServerPort());
        log.info("       servletPath=" + request.getServletPath());
        log.info("          isSecure=" + request.isSecure());
        log.info("---------------------------------------------------------------");

        // Perform the request
        getNext().invoke(request, response);

        // Log post-service information
        log.info("---------------------------------------------------------------");
        log.info("          authType=" + request.getAuthType());
        log.info("     contentLength=" + response.getContentLength());
        log.info("       contentType=" + response.getContentType());
        Cookie rcookies[] = response.getCookies();
        for (int i = 0; i < rcookies.length; i++) {
            log.info("            cookie=" + rcookies[i].getName() + "=" +
                rcookies[i].getValue() + "; domain=" +
                rcookies[i].getDomain() + "; path=" + rcookies[i].getPath());
        }
        String rhnames[] = response.getHttpHeaderNames();
        for (int i = 0; i < rhnames.length; i++) {
            String rhvalues[] = response.getHeaderValues(rhnames[i]);
            for (int j = 0; j < rhvalues.length; j++)
                log.info("            header=" + rhnames[i] + "=" + rhvalues[j]);
        }
        log.info("           message=" + response.getMessage());
        log.info("        remoteUser=" + request.getRemoteUser());
        log.info("            status=" + response.getStatus());
        log.info("===============================================================");

    }

    public String toString() {
        StringBuffer sb = new StringBuffer("RequestDumperValve[");
        if (container != null)
            sb.append(container.getName());
        sb.append("]");
        return (sb.toString());
    }
}
