package org.apache.catalina.core;


import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.util.buf.MessageBytes;

/**
 * 实现<code>StandardContext</code>容器实现类的默认基本行为.
 * <p>
 * <b>使用约束</b>: 只有在处理HTTP请求时，这种实现才可能有用.
 */
final class StandardContextValve extends ValveBase {

    // ----------------------------------------------------- Instance Variables

    /**
     * 描述信息
     */
    private static final String info =
        "org.apache.catalina.core.StandardContextValve/1.0";


    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);


    private static Log log = LogFactory.getLog(StandardContextValve.class);

    
    private StandardContext context = null;
    

    // ------------------------------------------------------------- Properties


    /**
     * 返回描述信息.
     */
    public String getInfo() {
        return (info);
    }


    // --------------------------------------------------------- Public Methods


    public void setContainer(Container container) {
        super.setContainer(container);
        context = (StandardContext) container;
    }

    
    /**
     * 选择合适的子级Wrapper处理这个请求, 基于指定的请求URI. 
     * 如果没有匹配的Wrapper, 返回一个适当的HTTP错误.
     *
     * @param request Request to be processed
     * @param response Response to be produced
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果发生servlet错误
     */
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {

        // 禁止任何直接访问WEB-INF或META-INF文件夹下的资源
        MessageBytes requestPathMB = request.getRequestPathMB();
        if ((requestPathMB.startsWithIgnoreCase("/META-INF/", 0))
            || (requestPathMB.equalsIgnoreCase("/META-INF"))
            || (requestPathMB.startsWithIgnoreCase("/WEB-INF/", 0))
            || (requestPathMB.equalsIgnoreCase("/WEB-INF"))) {
            String requestURI = request.getDecodedRequestURI();
            notFound(requestURI, response);
            return;
        }

        // Wait if we are reloading
        while (context.getPaused()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                ;
            }
        }

        // Select the Wrapper to be used for this Request
        Wrapper wrapper = request.getWrapper();
        if (wrapper == null) {
            String requestURI = request.getDecodedRequestURI();
            notFound(requestURI, response);
            return;
        }

        // Normal request processing
        Object instances[] = context.getApplicationEventListeners();

        ServletRequestEvent event = null;

        if ((instances != null) 
                && (instances.length > 0)) {
            event = new ServletRequestEvent
                (((StandardContext) container).getServletContext(), 
                 request.getRequest());
            // create pre-service event
            for (int i = 0; i < instances.length; i++) {
                if (instances[i] == null)
                    continue;
                if (!(instances[i] instanceof ServletRequestListener))
                    continue;
                ServletRequestListener listener =
                    (ServletRequestListener) instances[i];
                try {
                    listener.requestInitialized(event);
                } catch (Throwable t) {
                    container.getLogger().error(sm.getString("requestListenerValve.requestInit",
                                     instances[i].getClass().getName()), t);
                    ServletRequest sreq = request.getRequest();
                    sreq.setAttribute(Globals.EXCEPTION_ATTR,t);
                    return;
                }
            }
        }

        wrapper.getPipeline().getFirst().invoke(request, response);

        if ((instances !=null ) &&
                (instances.length > 0)) {
            // create post-service event
            for (int i = 0; i < instances.length; i++) {
                if (instances[i] == null)
                    continue;
                if (!(instances[i] instanceof ServletRequestListener))
                    continue;
                ServletRequestListener listener =
                    (ServletRequestListener) instances[i];
                try {
                    listener.requestDestroyed(event);
                } catch (Throwable t) {
                    container.getLogger().error(sm.getString("requestListenerValve.requestDestroy",
                                     instances[i].getClass().getName()), t);
                    ServletRequest sreq = request.getRequest();
                    sreq.setAttribute(Globals.EXCEPTION_ATTR,t);
                }
            }
        }
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 报告指定资源的“错误请求”错误. 
     * FIXME: 我们确实应该使用这个Web应用程序的错误报告设置, 但目前该代码在包装器级别运行，而不是在上下文级别运行.
     *
     * @param requestURI 请求资源的请求URI
     * @param response 创建的响应
     */
    private void badRequest(String requestURI, HttpServletResponse response) {
        try {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, requestURI);
        } catch (IllegalStateException e) {
            ;
        } catch (IOException e) {
            ;
        }
    }
    
    
    /**
     * 报告指定资源的“禁止”错误. 
     *
     * @param requestURI 请求资源的请求URI
     * @param response 创建的响应
     */
    private void forbidden(String requestURI, HttpServletResponse response) {
        try {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, requestURI);
        } catch (IllegalStateException e) {
            ;
        } catch (IOException e) {
            ;
        }
    }


    /**
     * 报告指定资源的“未找到”错误. 
     * FIXME: 我们确实应该使用这个Web应用程序的错误报告设置, 但目前该代码在包装器级别运行，而不是在上下文级别运行.
     *
     * @param requestURI 请求资源的请求URI
     * @param response 创建的响应
     */
    private void notFound(String requestURI, HttpServletResponse response) {
        try {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, requestURI);
        } catch (IllegalStateException e) {
            ;
        } catch (IOException e) {
            ;
        }
    }
}
