package org.apache.catalina;

import java.util.EventObject;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * 一般事件，通知监听器指定Servlet实例相关的重大事件, 或者一个指定的Filter实例,
 * 与管理它的Wrapper组件相反.
 */
public final class InstanceEvent extends EventObject {

    // ----------------------------------------------------- Manifest Constants

    /**
     * 关于<code>init()</code>方法被调用的事件说明
     */
    public static final String BEFORE_INIT_EVENT = "beforeInit";


    /**
     * <code>init()</code>方法返回的事件说明
     */
    public static final String AFTER_INIT_EVENT = "afterInit";


    /**
     * <code>service()</code>方法调用前的事件标志.
     */
    public static final String BEFORE_SERVICE_EVENT = "beforeService";


    /**
     * <code>service()</code>方法调用后的事件标志
     */
    public static final String AFTER_SERVICE_EVENT = "afterService";


    /**
     * <code>destroy</code>方法调用前的事件标志
     */
    public static final String BEFORE_DESTROY_EVENT = "beforeDestroy";


    /**
     * <code>destroy()</code>方法返回的事件标志
     */
    public static final String AFTER_DESTROY_EVENT = "afterDestroy";


    /**
     * servlet的<code>service()</code>方法被调用前的 事件标志.
     */
    public static final String BEFORE_DISPATCH_EVENT = "beforeDispatch";


    /**
     * servlet的<code>service()</code>方法 通过请求调度程序返回的  事件标志.
     */
    public static final String AFTER_DISPATCH_EVENT = "afterDispatch";


    /**
     * Filter的<code>doFilter()</code>方法即将被调用的事件标志.
     */
    public static final String BEFORE_FILTER_EVENT = "beforeFilter";


    /**
     * Filter的<code>doFilter()</code>方法返回的事件标志.
     */
    public static final String AFTER_FILTER_EVENT = "afterFilter";


    // ----------------------------------------------------------- Constructors


    /**
     * @param wrapper 管理servlet实例的Wrapper
     * @param filter Filter实例
     * @param type Event 类型(必须)
     */
    public InstanceEvent(Wrapper wrapper, Filter filter, String type) {
      super(wrapper);
      this.wrapper = wrapper;
      this.filter = filter;
      this.servlet = null;
      this.type = type;
    }


    /**
     * @param wrapper 管理servlet实例的Wrapper
     * @param filter Filter实例
     * @param type Event 类型(必须)
     * @param exception Exception that occurred
     */
    public InstanceEvent(Wrapper wrapper, Filter filter, String type, Throwable exception) {
      super(wrapper);
      this.wrapper = wrapper;
      this.filter = filter;
      this.servlet = null;
      this.type = type;
      this.exception = exception;
    }


    /**
     * @param wrapper 管理servlet实例的Wrapper
     * @param filter Filter实例
     * @param type Event 类型(必须)
     * @param request 正在处理的Servlet请求
     * @param response 正在处理的Servlet响应
     */
    public InstanceEvent(Wrapper wrapper, Filter filter, String type,
                         ServletRequest request, ServletResponse response) {
      super(wrapper);
      this.wrapper = wrapper;
      this.filter = filter;
      this.servlet = null;
      this.type = type;
      this.request = request;
      this.response = response;
    }


    /**
     * @param wrapper 管理servlet实例的Wrapper
     * @param filter Filter实例
     * @param type Event 类型(必须)
     * @param request 正在处理的Servlet请求
     * @param response 正在处理的Servlet响应
     * @param exception Exception that occurred
     */
    public InstanceEvent(Wrapper wrapper, Filter filter, String type,
                         ServletRequest request, ServletResponse response,
                         Throwable exception) {
      super(wrapper);
      this.wrapper = wrapper;
      this.filter = filter;
      this.servlet = null;
      this.type = type;
      this.request = request;
      this.response = response;
      this.exception = exception;
    }


    /**
     *  @param wrapper 管理servlet实例的Wrapper
     * @param servlet Servlet 实例
     * @param type Event 类型(必须)
     */
    public InstanceEvent(Wrapper wrapper, Servlet servlet, String type) {
      super(wrapper);
      this.wrapper = wrapper;
      this.filter = null;
      this.servlet = servlet;
      this.type = type;
    }


    /**
     * @param wrapper 管理servlet实例的Wrapper
     * @param servlet Servlet 实例
     * @param type Event 类型(必须)
     * @param exception Exception that occurred
     */
    public InstanceEvent(Wrapper wrapper, Servlet servlet, String type,
                         Throwable exception) {
      super(wrapper);
      this.wrapper = wrapper;
      this.filter = null;
      this.servlet = servlet;
      this.type = type;
      this.exception = exception;
    }


    /**
     *  @param wrapper 管理servlet实例的Wrapper
     * @param servlet Servlet 实例
     * @param type Event 类型(必须)
     * @param request 正在处理的Servlet请求
     * @param response 正在处理的Servlet响应
     */
    public InstanceEvent(Wrapper wrapper, Servlet servlet, String type,
                         ServletRequest request, ServletResponse response) {
      super(wrapper);
      this.wrapper = wrapper;
      this.filter = null;
      this.servlet = servlet;
      this.type = type;
      this.request = request;
      this.response = response;
    }


    /**
     * @param wrapper 管理servlet实例的Wrapper
     * @param servlet Servlet 实例
     * @param type Event 类型(必须)
     * @param request 正在处理的Servlet请求
     * @param response 正在处理的Servlet响应
     * @param exception 发生的异常Exception
     */
    public InstanceEvent(Wrapper wrapper, Servlet servlet, String type,
                         ServletRequest request, ServletResponse response,
                         Throwable exception) {
      super(wrapper);
      this.wrapper = wrapper;
      this.filter = null;
      this.servlet = servlet;
      this.type = type;
      this.request = request;
      this.response = response;
      this.exception = exception;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 由当前事件报告的处理期间抛出的异常 (AFTER_INIT_EVENT, AFTER_SERVICE_EVENT, 
     * AFTER_DESTROY_EVENT, AFTER_DISPATCH_EVENT, 和AFTER_FILTER_EVENT only).
     */
    private Throwable exception = null;


    /**
     * Filter实例(BEFORE_FILTER_EVENT, AFTER_FILTER_EVENT only).
     */
    private Filter filter = null;


    /**
     * 正在处理的servlet请求(BEFORE_FILTER_EVENT,
     * AFTER_FILTER_EVENT, BEFORE_SERVICE_EVENT, AFTER_SERVICE_EVENT).
     */
    private ServletRequest request = null;


    /**
     * 被处理的servlet响应 (BEFORE_FILTER_EVENT,
     * AFTER_FILTER_EVENT, BEFORE_SERVICE_EVENT, and AFTER_SERVICE_EVENT).
     */
    private ServletResponse response = null;


    /**
     * Servlet实例 (不存在 BEFORE_FILTER_EVENT 或 AFTER_FILTER_EVENT 事件).
     */
    private Servlet servlet = null;


    /**
     * 此实例的事件类型
     */
    private String type = null;


    /**
     * 管理servlet实例的Wrapper
     */
    private Wrapper wrapper = null;


    // ------------------------------------------------------------- Properties


    /**
     * 返回由该事件报告的处理过程中发生的异常
     */
    public Throwable getException() {
        return (this.exception);
    }


    /**
     * 返回filter实例
     */
    public Filter getFilter() {
        return (this.filter);
    }


    /**
     * 返回servlet请求
     */
    public ServletRequest getRequest() {
        return (this.request);
    }


    /**
     * 返回servlet响应
     */
    public ServletResponse getResponse() {
        return (this.response);
    }


    /**
     * 当前事件发生的时候，返回servlet实例
     */
    public Servlet getServlet() {
        return (this.servlet);
    }


    /**
     * 返回当前事件的事件类型
     */
    public String getType() {
        return (this.type);
    }


    /**
     * 当前事件发生的时候，返回管理servlet实例的Wrapper
     */
    public Wrapper getWrapper() {
        return (this.wrapper);
    }
}
