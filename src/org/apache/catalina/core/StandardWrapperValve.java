package org.apache.catalina.core;


import java.io.IOException;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.log.SystemLogHandler;

/**
 * 实现Valve的默认基本行为
 */
final class StandardWrapperValve extends ValveBase {

    private static Log log = LogFactory.getLog(StandardWrapperValve.class);

    // ----------------------------------------------------- Instance Variables


    //  一些JMX统计. 这个vavle 被关联到StandardWrapper.
    // We exponse the StandardWrapper as JMX ( j2eeType=Servlet ).这里的字段用于性能
    private volatile long processingTime;
    private volatile long maxTime;
    private volatile long minTime = Long.MAX_VALUE;
    private volatile int requestCount;
    private volatile int errorCount;


    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);


    // --------------------------------------------------------- Public Methods


    /**
     * 调用正在管理的servlet, 遵守servlet lifecycle和SingleThreadModel支持的有关规则
     *
     * @param request Request to be processed
     * @param response Response to be produced
     * @param valveContext Valve上下文用于转发到下一个Valve
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果出现servlet错误
     */
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {

        // 初始化可能需要的局部变量
        boolean unavailable = false;
        Throwable throwable = null;
        // This should be a Request attribute...
        long t1=System.currentTimeMillis();
        requestCount++;
        StandardWrapper wrapper = (StandardWrapper) getContainer();
        Servlet servlet = null;
        Context context = (Context) wrapper.getParent();
        
        // 检查应用程序是否被标记为不可用
        if (!context.getAvailable()) {
        	response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           sm.getString("standardContext.isUnavailable"));
            unavailable = true;
        }

        // 检查servlet是否被标记为不可用
        if (!unavailable && wrapper.isUnavailable()) {
            container.getLogger().info(sm.getString("standardWrapper.isUnavailable",
                    wrapper.getName()));
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE)) {
                response.setDateHeader("Retry-After", available);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        sm.getString("standardWrapper.isUnavailable",
                                wrapper.getName()));
            } else if (available == Long.MAX_VALUE) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        sm.getString("standardWrapper.notFound",
                                wrapper.getName()));
            }
            unavailable = true;
        }

        // 分配servlet实例来处理此请求
        try {
            if (!unavailable) {
                servlet = wrapper.allocate();
            }
        } catch (UnavailableException e) {
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE)) {
            	response.setDateHeader("Retry-After", available);
            	response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           sm.getString("standardWrapper.isUnavailable",
                                        wrapper.getName()));
            } else if (available == Long.MAX_VALUE) {
            	response.sendError(HttpServletResponse.SC_NOT_FOUND,
                           sm.getString("standardWrapper.notFound",
                                        wrapper.getName()));
            }
        } catch (ServletException e) {
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), StandardWrapper.getRootCause(e));
            throwable = e;
            exception(request, response, e);
            servlet = null;
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
            servlet = null;
        }

        // Acknowlege the request
        try {
            response.sendAcknowledgement();
        } catch (IOException e) {
        	request.removeAttribute(Globals.JSP_FILE_ATTR);
            container.getLogger().warn(sm.getString("standardWrapper.acknowledgeException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.acknowledgeException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
            servlet = null;
        }
        MessageBytes requestPathMB = null;
        if (request != null) {
            requestPathMB = request.getRequestPathMB();
        }
        request.setAttribute
            (ApplicationFilterFactory.DISPATCHER_TYPE_ATTR,
             ApplicationFilterFactory.REQUEST_INTEGER);
        request.setAttribute
            (ApplicationFilterFactory.DISPATCHER_REQUEST_PATH_ATTR,
             requestPathMB);
        // 为这个请求创建过滤器链
        ApplicationFilterFactory factory =
            ApplicationFilterFactory.getInstance();
        ApplicationFilterChain filterChain =
            factory.createFilterChain(request, wrapper, servlet);

        // 调用这个请求的过滤器链
        // NOTE: 也调用servlet的 service() 方法
        try {
            String jspFile = wrapper.getJspFile();
            if (jspFile != null)
            	request.setAttribute(Globals.JSP_FILE_ATTR, jspFile);
            else
            	request.removeAttribute(Globals.JSP_FILE_ATTR);
            if ((servlet != null) && (filterChain != null)) {

                // Swallow output if needed
                if (context.getSwallowOutput()) {
                    try {
                        SystemLogHandler.startCapture();
                        filterChain.doFilter(request.getRequest(), 
                                response.getResponse());
                    } finally {
                        String log = SystemLogHandler.stopCapture();
                        if (log != null && log.length() > 0) {
                            context.getLogger().info(log);
                        }
                    }
                } else {
                    filterChain.doFilter
                        (request.getRequest(), response.getResponse());
                }

            }
            request.removeAttribute(Globals.JSP_FILE_ATTR);
        } catch (ClientAbortException e) {
        	request.removeAttribute(Globals.JSP_FILE_ATTR);
            throwable = e;
            exception(request, response, e);
        } catch (IOException e) {
        	request.removeAttribute(Globals.JSP_FILE_ATTR);
            container.getLogger().warn(sm.getString("standardWrapper.serviceException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
        } catch (UnavailableException e) {
        	request.removeAttribute(Globals.JSP_FILE_ATTR);
            container.getLogger().warn(sm.getString("standardWrapper.serviceException",
                             wrapper.getName()), e);
            //            throwable = e;
            //            exception(request, response, e);
            wrapper.unavailable(e);
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE)) {
                response.setDateHeader("Retry-After", available);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           sm.getString("standardWrapper.isUnavailable",
                                        wrapper.getName()));
            } else if (available == Long.MAX_VALUE) {
            	response.sendError(HttpServletResponse.SC_NOT_FOUND,
                            sm.getString("standardWrapper.notFound",
                                        wrapper.getName()));
            }
            // Do not save exception in 'throwable', because we
            // do not want to do exception(request, response, e) processing
        } catch (ServletException e) {
        	request.removeAttribute(Globals.JSP_FILE_ATTR);
            Throwable rootCause = StandardWrapper.getRootCause(e);
            if (!(rootCause instanceof ClientAbortException)) {
                container.getLogger().error(sm.getString("standardWrapper.serviceException",
                                 wrapper.getName()), rootCause);
            }
            throwable = e;
            exception(request, response, e);
        } catch (Throwable e) {
            request.removeAttribute(Globals.JSP_FILE_ATTR);
            container.getLogger().error(sm.getString("standardWrapper.serviceException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
        }

        // 释放过滤器链
        try {
            if (filterChain != null)
                filterChain.release();
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.releaseFilters",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }

        // 释放分配的servlet实例
        try {
            if (servlet != null) {
                wrapper.deallocate(servlet);
            }
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.deallocateException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }

        // 如果这个servlet被标记为永久不可用, 卸载它并释放这个实例
        try {
            if ((servlet != null) &&
                (wrapper.getAvailable() == Long.MAX_VALUE)) {
                wrapper.unload();
            }
        } catch (Throwable e) {
            container.getLogger().error(sm.getString("standardWrapper.unloadException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }
        long t2=System.currentTimeMillis();

        long time=t2-t1;
        processingTime += time;
        if( time > maxTime) maxTime=time;
        if( time < minTime) minTime=time;
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 处理请求的过程中遇到的ServletException.  在生成异常报告期间发生的任何异常都记录并转发.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param exception 发生的异常(可能导致一个根异常
     */
    private void exception(Request request, Response response, Throwable exception) {
    	request.setAttribute(Globals.EXCEPTION_ATTR, exception);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    public long getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }

    public long getMaxTime() {
        return maxTime;
    }

    public void setMaxTime(long maxTime) {
        this.maxTime = maxTime;
    }

    public long getMinTime() {
        return minTime;
    }

    public void setMinTime(long minTime) {
        this.minTime = minTime;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(int requestCount) {
        this.requestCount = requestCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    // Don't register in JMX
    public ObjectName createObjectName(String domain, ObjectName parent)
            throws MalformedObjectNameException {
        return null;
    }
}
