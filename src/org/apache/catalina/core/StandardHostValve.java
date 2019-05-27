package org.apache.catalina.core;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.deploy.ErrorPage;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 实现了Valve的默认基本行为.
 * <p>
 * <b>使用约束</b>: 只有在处理HTTP请求时，这种实现才可能有用.
 */
final class StandardHostValve extends ValveBase {

    private static Log log = LogFactory.getLog(StandardHostValve.class);

    // ----------------------------------------------------- Instance Variables

    /**
     * 描述信息
     */
    private static final String info = "org.apache.catalina.core.StandardHostValve/1.0";


    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);

    // ------------------------------------------------------------- Properties

    /**
     * 返回描述信息.
     */
    public String getInfo() {
        return (info);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 选择合适的子级Context处理这个请求, 基于指定的请求URI.
     * 如果找不到匹配的上下文，返回一个适当的HTTP错误.
     *
     * @param request Request to be processed
     * @param response Response to be produced
     * @param valveContext Valve上下文用于重定向到下一个Valve
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {

        // 选择要用于此请求的上下文
        Context context = request.getContext();
        if (context == null) {
            response.sendError
                (HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                 sm.getString("standardHost.noContext"));
            return;
        }

        // 绑定上下文CL 到当前线程
        if( context.getLoader() != null ) {
            // 未启动 - 它应该首先检查可用性
            // 这最终将转移到 Engine, 这是通用的.
            Thread.currentThread().setContextClassLoader
                    (context.getLoader().getClassLoader());
        }

        // Ask this Context to process this request
        context.getPipeline().getFirst().invoke(request, response);

        // 错误页面处理
        response.setSuspended(false);

        Throwable t = (Throwable) request.getAttribute(Globals.EXCEPTION_ATTR);

        if (t != null) {
            throwable(request, response, t);
        } else {
            status(request, response);
        }

        // Restore the context classloader
        Thread.currentThread().setContextClassLoader(StandardHostValve.class.getClassLoader());
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 在处理指定的请求期间，处理指定的遇到的异常. 在生成异常报告期间发生的任何异常都记录并重定向.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param throwable 发生的异常(可能导致一个根异常
     */
    protected void throwable(Request request, Response response, Throwable throwable) {
        Context context = request.getContext();
        if (context == null)
            return;

        Throwable realError = throwable;

        if (realError instanceof ServletException) {
            realError = ((ServletException) realError).getRootCause();
            if (realError == null) {
                realError = throwable;
            }
        }

        // 如果这是从客户端中止的请求，只需记录并返回
        if (realError instanceof ClientAbortException ) {
            if (log.isDebugEnabled()) {
                log.debug
                    (sm.getString("standardHost.clientAbort",
                        realError.getCause().getMessage()));
            }
            return;
        }

        ErrorPage errorPage = findErrorPage(context, throwable);
        if ((errorPage == null) && (realError != throwable)) {
            errorPage = findErrorPage(context, realError);
        }

        if (errorPage != null) {
            response.setAppCommitted(false);
            request.setAttribute
                (ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR,
                 errorPage.getLocation());
            request.setAttribute(ApplicationFilterFactory.DISPATCHER_TYPE_ATTR,
                              new Integer(ApplicationFilterFactory.ERROR));
            request.setAttribute
                (Globals.STATUS_CODE_ATTR,
                 new Integer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR));
            request.setAttribute(Globals.ERROR_MESSAGE_ATTR,
                              throwable.getMessage());
            request.setAttribute(Globals.EXCEPTION_ATTR,
                              realError);
            Wrapper wrapper = request.getWrapper();
            if (wrapper != null)
                request.setAttribute(Globals.SERVLET_NAME_ATTR,
                                  wrapper.getName());
            request.setAttribute(Globals.EXCEPTION_PAGE_ATTR,
                                 request.getRequestURI());
            request.setAttribute(Globals.EXCEPTION_TYPE_ATTR,
                              realError.getClass());
            if (custom(request, response, errorPage)) {
                try {
                    response.flushBuffer();
                } catch (IOException e) {
                    container.getLogger().warn("Exception Processing " + errorPage, e);
                }
            }
        } else {
            // 对于请求处理期间抛出的异常，尚未定义自定义错误页.
            // 检查是否指定了错误代码500的错误页，如果指定了,将该页发送回作为响应.
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            // 响应是一个错误
            response.setError();
            status(request, response);
        }
    }


    /**
     * 处理生成的HTTP状态码（和相应的消息）, 在处理指定请求期间. 在生成异常报告期间发生的任何异常都记录并重定向.
     *
     * @param request The request being processed
     * @param response The response being generated
     */
    protected void status(Request request, Response response) {

        int statusCode = response.getStatus();

        // 处理此状态码的自定义错误页面
        Context context = request.getContext();
        if (context == null)
            return;

        /* 只有当isError() 被设置，才会查找错误页面.
         * isError() 被设置，当 response.sendError() 被调用的时候. 这允许自定义错误页面不依赖于默认的web.xml.
         */
        if (!response.isError())
            return;

        ErrorPage errorPage = context.findErrorPage(statusCode);
        if (errorPage != null) {
            response.setAppCommitted(false);
            request.setAttribute(Globals.STATUS_CODE_ATTR,
                              new Integer(statusCode));

            String message = RequestUtil.filter(response.getMessage());
            if (message == null)
                message = "";
            request.setAttribute(Globals.ERROR_MESSAGE_ATTR, message);
            request.setAttribute
                (ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR,
                 errorPage.getLocation());
            request.setAttribute(ApplicationFilterFactory.DISPATCHER_TYPE_ATTR,
                              new Integer(ApplicationFilterFactory.ERROR));


            Wrapper wrapper = request.getWrapper();
            if (wrapper != null)
                request.setAttribute(Globals.SERVLET_NAME_ATTR,
                                  wrapper.getName());
            request.setAttribute(Globals.EXCEPTION_PAGE_ATTR,
                                 request.getRequestURI());
            if (custom(request, response, errorPage)) {
                try {
                    response.flushBuffer();
                } catch (IOException e) {
                    container.getLogger().warn("Exception Processing " + errorPage, e);
                }
            }
        }
    }


    /**
     * 找到并返回指定异常类的ErrorPage实例, 或最近的父类的 ErrorPage实例. 如果没有找到 ErrorPage实例, 返回<code>null</code>.
     *
     * @param context 要搜索的上下文
     * @param exception 要查找的ErrorPage的异常
     */
    protected static ErrorPage findErrorPage(Context context, Throwable exception) {

        if (exception == null)
            return (null);
        Class clazz = exception.getClass();
        String name = clazz.getName();
        while (!"java.lang.Object".equals(clazz)) {
            ErrorPage errorPage = context.findErrorPage(name);
            if (errorPage != null)
                return (errorPage);
            clazz = clazz.getSuperclass();
            if (clazz == null)
                break;
            name = clazz.getName();
        }
        return (null);
    }


    /**
     * 处理一个HTTP状态码或Java异常通过转发控制指定errorPage对象中的位置.
     * 假定调用方已经记录了要转发到该页面的任何请求属性.
     * 返回<code>true</code>如果成功地使用指定的错误页面位置, 或<code>false</code> 如果应该呈现默认错误报告.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param errorPage 遵守的errorPage指令
     */
    protected boolean custom(Request request, Response response,
                             ErrorPage errorPage) {

        if (container.getLogger().isDebugEnabled())
            container.getLogger().debug("Processing " + errorPage);

        request.setPathInfo(errorPage.getLocation());

        try {

            // 如果可能，重置响应(else IllegalStateException)
            //hres.reset();
            // 重置响应(保存真正的错误代码和消息)
            Integer statusCodeObj =
                (Integer) request.getAttribute(Globals.STATUS_CODE_ATTR);
            int statusCode = statusCodeObj.intValue();
            String message =
                (String) request.getAttribute(Globals.ERROR_MESSAGE_ATTR);
            response.reset(statusCode, message);

            // 将控制转发到指定位置
            ServletContext servletContext =
                request.getContext().getServletContext();
            RequestDispatcher rd =
                servletContext.getRequestDispatcher(errorPage.getLocation());
            rd.forward(request.getRequest(), response.getResponse());

            // 如果重定向, 响应再次暂停
            response.setSuspended(false);

            // 表明我们已经成功地处理了这个自定义页面
            return (true);

        } catch (Throwable t) {
            // 报告未能处理此自定义页面
            container.getLogger().error("Exception Processing " + errorPage, t);
            return (false);
        }
    }
}
