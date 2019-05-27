package org.apache.catalina.core;

import java.io.IOException;
import java.security.Principal;
import java.security.PrivilegedActionException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.InstanceEvent;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.util.InstanceSupport;
import org.apache.catalina.util.StringManager;

/**
 * <code>javax.servlet.FilterChain</code>实现类，用于管理特定请求的一组过滤器的执行. 
 * 当定义的一组过滤器已经执行的时候，下一个调用<code>doFilter()</code>将执行servlet的<code>service()</code>方法本身
 */
final class ApplicationFilterChain implements FilterChain {

    // -------------------------------------------------------------- Constants

    public static final int INCREMENT = 10;

    // ----------------------------------------------------------- Constructors

    public ApplicationFilterChain() {
        super();
    }

    // ----------------------------------------------------- Instance Variables


    /**
     * Filters.
     */
    private ApplicationFilterConfig[] filters = new ApplicationFilterConfig[0];


    /**
     * 用于维护过滤器链中当前位置的int.
     */
    private int pos = 0;


    /**
     * 给出链中当前过滤器数量的int.
     */
    private int n = 0;


    /**
     * 要由该链执行的servlet实例.
     */
    private Servlet servlet = null;


    /**
     * The string manager for our package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 用于发送"before filter"和"after filter"事件
     */
    private InstanceSupport support = null;

    
    /**
     * 当SecurityManager打开和<code>doFilter</code>调用的时候使用.
     */
    private static Class[] classType = new Class[]{ServletRequest.class, 
                                                   ServletResponse.class,
                                                   FilterChain.class};
                                                   
    /**
     * 当SecurityManager打开和<code>service</code>调用的时候使用.
     */                                                 
    private static Class[] classTypeUsedInService = new Class[]{
                                                         ServletRequest.class,
                                                         ServletResponse.class};

    // ---------------------------------------------------- FilterChain Methods


    /**
     * 执行下一个过滤器, 传递指定的 request和response. 
     * 如果没有下一个过滤器, 执行servlet的<code>service()</code>方法
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果发生servlet异常
     */
    public void doFilter(ServletRequest request, ServletResponse response)
        throws IOException, ServletException {

        if( System.getSecurityManager() != null ) {
            final ServletRequest req = request;
            final ServletResponse res = response;
            try {
                java.security.AccessController.doPrivileged(
                    new java.security.PrivilegedExceptionAction() {
                        public Object run() 
                            throws ServletException, IOException {
                            internalDoFilter(req,res);
                            return null;
                        }
                    }
                );
            } catch( PrivilegedActionException pe) {
                Exception e = pe.getException();
                if (e instanceof ServletException)
                    throw (ServletException) e;
                else if (e instanceof IOException)
                    throw (IOException) e;
                else if (e instanceof RuntimeException)
                    throw (RuntimeException) e;
                else
                    throw new ServletException(e.getMessage(), e);
            }
        } else {
            internalDoFilter(request,response);
        }
    }

    private void internalDoFilter(ServletRequest request, 
                                  ServletResponse response)
        throws IOException, ServletException {

        // 调用下一个过滤器
        if (pos < n) {
            ApplicationFilterConfig filterConfig = filters[pos++];
            Filter filter = null;
            try {
                filter = filterConfig.getFilter();
                support.fireInstanceEvent(InstanceEvent.BEFORE_FILTER_EVENT,
                                          filter, request, response);
                
                if( System.getSecurityManager() != null ) {
                    final ServletRequest req = request;
                    final ServletResponse res = response;
                    Principal principal = 
                        ((HttpServletRequest) req).getUserPrincipal();

                    Object[] args = new Object[]{req, res, this};
                    SecurityUtil.doAsPrivilege
                        ("doFilter", filter, classType, args);
                    
                    args = null;
                } else {  
                    filter.doFilter(request, response, this);
                }

                support.fireInstanceEvent(InstanceEvent.AFTER_FILTER_EVENT,
                                          filter, request, response);
            } catch (IOException e) {
                if (filter != null)
                    support.fireInstanceEvent(InstanceEvent.AFTER_FILTER_EVENT,
                                              filter, request, response, e);
                throw e;
            } catch (ServletException e) {
                if (filter != null)
                    support.fireInstanceEvent(InstanceEvent.AFTER_FILTER_EVENT,
                                              filter, request, response, e);
                throw e;
            } catch (RuntimeException e) {
                if (filter != null)
                    support.fireInstanceEvent(InstanceEvent.AFTER_FILTER_EVENT,
                                              filter, request, response, e);
                throw e;
            } catch (Throwable e) {
                if (filter != null)
                    support.fireInstanceEvent(InstanceEvent.AFTER_FILTER_EVENT,
                                              filter, request, response, e);
                throw new ServletException
                  (sm.getString("filterChain.filter"), e);
            }
            return;
        }

        // 过滤器链结束 -- 调用servlet 实例
        try {
            support.fireInstanceEvent(InstanceEvent.BEFORE_SERVICE_EVENT,
                                      servlet, request, response);
            if ((request instanceof HttpServletRequest) &&
                (response instanceof HttpServletResponse)) {
                    
                if( System.getSecurityManager() != null ) {
                    final ServletRequest req = request;
                    final ServletResponse res = response;
                    Principal principal = 
                        ((HttpServletRequest) req).getUserPrincipal();
                    Object[] args = new Object[]{req, res};
                    SecurityUtil.doAsPrivilege("service",
                                               servlet,
                                               classTypeUsedInService, 
                                               args,
                                               principal);   
                    args = null;
                } else {  
                    servlet.service((HttpServletRequest) request,
                                    (HttpServletResponse) response);
                }
            } else {
                servlet.service(request, response);
            }
            support.fireInstanceEvent(InstanceEvent.AFTER_SERVICE_EVENT,
                                      servlet, request, response);
        } catch (IOException e) {
            support.fireInstanceEvent(InstanceEvent.AFTER_SERVICE_EVENT,
                                      servlet, request, response, e);
            throw e;
        } catch (ServletException e) {
            support.fireInstanceEvent(InstanceEvent.AFTER_SERVICE_EVENT,
                                      servlet, request, response, e);
            throw e;
        } catch (RuntimeException e) {
            support.fireInstanceEvent(InstanceEvent.AFTER_SERVICE_EVENT,
                                      servlet, request, response, e);
            throw e;
        } catch (Throwable e) {
            support.fireInstanceEvent(InstanceEvent.AFTER_SERVICE_EVENT,
                                      servlet, request, response, e);
            throw new ServletException
              (sm.getString("filterChain.servlet"), e);
        }
    }


    // -------------------------------------------------------- Package Methods



    /**
     * 添加一个过滤器到过滤器链中
     *
     * @param filterConfig The FilterConfig for the servlet to be executed
     */
    void addFilter(ApplicationFilterConfig filterConfig) {

        if (n == filters.length) {
            ApplicationFilterConfig[] newFilters =
                new ApplicationFilterConfig[n + INCREMENT];
            System.arraycopy(filters, 0, newFilters, 0, n);
            filters = newFilters;
        }
        filters[n++] = filterConfig;
    }


    /**
     * 释放对该链执行的过滤器和包装器的引用
     */
    void release() {
        n = 0;
        pos = 0;
        servlet = null;
        support = null;
    }


    /**
     * 设置最后执行的servlet
     *
     * @param servlet The Wrapper for the servlet to be executed
     */
    void setServlet(Servlet servlet) {
        this.servlet = servlet;
    }


    /**
     * 用于事件通知
     *
     * @param support The InstanceSupport object for our Wrapper
     */
    void setSupport(InstanceSupport support) {
        this.support = support;
    }
}
