package org.apache.catalina.valves;


import java.io.IOException;
import java.io.Writer;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Globals;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.StringManager;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.compat.JdkCompat;

/**
 * <p>Valve实现类输出HTML错误页面.</p>
 *
 * <p>这个Valve 应该附加在Host等级, 如果附加到Context等级，它也可以工作.</p>
 */
public class ErrorReportValve extends ValveBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * 实现类描述信息.
     */
    private static final String info =
        "org.apache.catalina.valves.ErrorReportValve/1.0";


    /**
     * The StringManager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);


    // ------------------------------------------------------------- Properties


    /**
     * 实现类描述信息.
     */
    public String getInfo() {
        return (info);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 执行序列中的下一个Valve. 当执行返回时, 检查响应状态, 输出一个错误报告是必要的.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        // Perform the request
        getNext().invoke(request, response);

        ServletRequest sreq = (ServletRequest) request;
        Throwable throwable =
            (Throwable) sreq.getAttribute(Globals.EXCEPTION_ATTR);

        ServletResponse sresp = (ServletResponse) response;
        if (sresp.isCommitted()) {
            return;
        }

        if (throwable != null) {

            // The response is an error
            response.setError();

            // Reset the response (if possible)
            try {
                sresp.reset();
            } catch (IllegalStateException e) {
                ;
            }

            response.sendError
                (HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

        }

        response.setSuspended(false);

        try {
            report(request, response, throwable);
        } catch (Throwable tt) {
            ;
        }
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 打印出错误报告
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param throwable The exception that occurred (which possibly wraps
     *  a root cause exception
     */
    protected void report(Request request, Response response,
                          Throwable throwable)
        throws IOException {

        // Do nothing on non-HTTP responses
        int statusCode = response.getStatus();

        // Do nothing on a 1xx, 2xx and 3xx status
        // Do nothing if anything has been written already
        if ((statusCode < 400) || (response.getContentCount() > 0))
            return;

        Throwable rootCause = null;

        if (throwable != null) {

            if (throwable instanceof ServletException)
                rootCause = ((ServletException) throwable).getRootCause();

        }

        String message = RequestUtil.filter(response.getMessage());
        if (message == null)
            message = "";

        // 如果没有指定状态码的报告，请不要做任何事
        String report = null;
        try {
            report = sm.getString("http." + statusCode, message);
        } catch (Throwable t) {
            ;
        }
        if (report == null)
            return;

        StringBuffer sb = new StringBuffer();

        sb.append("<html><head><title>");
        sb.append(ServerInfo.getServerInfo()).append(" - ");
        sb.append(sm.getString("errorReportValve.errorReport"));
        sb.append("</title>");
        sb.append("<style><!--");
        sb.append(org.apache.catalina.util.TomcatCSS.TOMCAT_CSS);
        sb.append("--></style> ");
        sb.append("</head><body>");
        sb.append("<h1>");
        sb.append(sm.getString("errorReportValve.statusHeader",
                               "" + statusCode, message)).append("</h1>");
        sb.append("<HR size=\"1\" noshade=\"noshade\">");
        sb.append("<p><b>type</b> ");
        if (throwable != null) {
            sb.append(sm.getString("errorReportValve.exceptionReport"));
        } else {
            sb.append(sm.getString("errorReportValve.statusReport"));
        }
        sb.append("</p>");
        sb.append("<p><b>");
        sb.append(sm.getString("errorReportValve.message"));
        sb.append("</b> <u>");
        sb.append(message).append("</u></p>");
        sb.append("<p><b>");
        sb.append(sm.getString("errorReportValve.description"));
        sb.append("</b> <u>");
        sb.append(report);
        sb.append("</u></p>");

        if (throwable != null) {

            String stackTrace = JdkCompat.getJdkCompat()
                .getPartialServletStackTrace(throwable);
            sb.append("<p><b>");
            sb.append(sm.getString("errorReportValve.exception"));
            sb.append("</b> <pre>");
            sb.append(RequestUtil.filter(stackTrace));
            sb.append("</pre></p>");

            while (rootCause != null) {
                stackTrace = JdkCompat.getJdkCompat()
                    .getPartialServletStackTrace(rootCause);
                sb.append("<p><b>");
                sb.append(sm.getString("errorReportValve.rootCause"));
                sb.append("</b> <pre>");
                sb.append(RequestUtil.filter(stackTrace));
                sb.append("</pre></p>");
                // In case root cause is somehow heavily nested
                try {
                    rootCause = (Throwable)IntrospectionUtils.getProperty
                                                (rootCause, "rootCause");
                } catch (ClassCastException e) {
                    rootCause = null;
                }
            }

            sb.append("<p><b>");
            sb.append(sm.getString("errorReportValve.note"));
            sb.append("</b> <u>");
            sb.append(sm.getString("errorReportValve.rootCauseInLogs",
                                   ServerInfo.getServerInfo()));
            sb.append("</u></p>");

        }

        sb.append("<HR size=\"1\" noshade=\"noshade\">");
        sb.append("<h3>").append(ServerInfo.getServerInfo()).append("</h3>");
        sb.append("</body></html>");

        try {
            try {
                response.setContentType("text/html");
                response.setCharacterEncoding("utf-8");
            } catch (Throwable t) {
                if (container.getLogger().isDebugEnabled())
                    container.getLogger().debug("status.setContentType", t);
            }
            Writer writer = response.getReporter();
            if (writer != null) {
                // If writer is null, it's an indication that the response has
                // been hard committed already, which should never happen
                writer.write(sb.toString());
            }
        } catch (IOException e) {
            ;
        } catch (IllegalStateException e) {
            ;
        }
    }
}
