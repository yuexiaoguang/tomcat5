package org.apache.catalina.core;

import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.deploy.FilterMap;

/**
 * 过滤器创建和缓存以及过滤器链创建的工厂.
 */
public final class ApplicationFilterFactory {

    // -------------------------------------------------------------- Constants

    public static final int ERROR = 1;
    public static final Integer ERROR_INTEGER = new Integer(ERROR);
    public static final int FORWARD = 2;
    public static final Integer FORWARD_INTEGER = new Integer(FORWARD);
    public static final int INCLUDE = 4;
    public static final Integer INCLUDE_INTEGER = new Integer(INCLUDE);
    public static final int REQUEST = 8;
    public static final Integer REQUEST_INTEGER = new Integer(REQUEST);

    public static final String DISPATCHER_TYPE_ATTR = Globals.DISPATCHER_TYPE_ATTR;
    public static final String DISPATCHER_REQUEST_PATH_ATTR = Globals.DISPATCHER_REQUEST_PATH_ATTR;

    private static final SecurityManager securityManager = System.getSecurityManager();

    private static ApplicationFilterFactory factory = null;;


    // ----------------------------------------------------------- Constructors

    private ApplicationFilterFactory() {
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 返回fqctory 实例.
     */
    public static ApplicationFilterFactory getInstance() {
        if (factory == null) {
            factory = new ApplicationFilterFactory();
        }
        return factory;
    }


    /**
     * 创建并返回一个FilterChain 实现类, 它将包装指定servlet实例的执行.
     * 如果不执行过滤器链, 返回<code>null</code>.
     *
     * @param request 正在处理的servlet请求
     * @param servlet 要包装的servlet实例
     */
    public ApplicationFilterChain createFilterChain
        (ServletRequest request, Wrapper wrapper, Servlet servlet) {

        // 获取调度器类型
        int dispatcher = -1; 
        if (request.getAttribute(DISPATCHER_TYPE_ATTR) != null) {
            Integer dispatcherInt = 
                (Integer) request.getAttribute(DISPATCHER_TYPE_ATTR);
            dispatcher = dispatcherInt.intValue();
        }
        String requestPath = null;
        Object attribute = request.getAttribute(DISPATCHER_REQUEST_PATH_ATTR);
        
        if (attribute != null){
            requestPath = attribute.toString();
        }
        
        HttpServletRequest hreq = null;
        if (request instanceof HttpServletRequest) 
            hreq = (HttpServletRequest)request;
        // 如果没有servlet来执行, 返回 null
        if (servlet == null)
            return (null);

        // 创建和初始化过滤器链对象
        ApplicationFilterChain filterChain = null;
        if ((securityManager == null) && (request instanceof Request)) {
            Request req = (Request) request;
            filterChain = (ApplicationFilterChain) req.getFilterChain();
            if (filterChain == null) {
                filterChain = new ApplicationFilterChain();
                req.setFilterChain(filterChain);
            }
        } else {
            // Security: Do not recycle
            filterChain = new ApplicationFilterChain();
        }

        filterChain.setServlet(servlet);

        filterChain.setSupport
            (((StandardWrapper)wrapper).getInstanceSupport());

        // 获取此上下文的过滤器映射
        StandardContext context = (StandardContext) wrapper.getParent();
        FilterMap filterMaps[] = context.findFilterMaps();

        // 如果没有过滤器链
        if ((filterMaps == null) || (filterMaps.length == 0))
            return (filterChain);

        // 获取匹配过滤器映射所需的信息
        String servletName = wrapper.getName();

        int n = 0;

        // 将相关的路径映射过滤器添加到此过滤器链中
        for (int i = 0; i < filterMaps.length; i++) {
            if (!matchDispatcher(filterMaps[i] ,dispatcher)) {
                continue;
            }
            if (!matchFiltersURL(filterMaps[i], requestPath))
                continue;
            ApplicationFilterConfig filterConfig = (ApplicationFilterConfig)
                context.findFilterConfig(filterMaps[i].getFilterName());
            if (filterConfig == null) {
                ;       // FIXME - log configuration problem
                continue;
            }
            filterChain.addFilter(filterConfig);
            n++;
        }

        // 添加与servlet名称匹配的过滤器
        for (int i = 0; i < filterMaps.length; i++) {
            if (!matchDispatcher(filterMaps[i] ,dispatcher)) {
                continue;
            }
            if (!matchFiltersServlet(filterMaps[i], servletName))
                continue;
            ApplicationFilterConfig filterConfig = (ApplicationFilterConfig)
                context.findFilterConfig(filterMaps[i].getFilterName());
            if (filterConfig == null) {
                ;       // FIXME - log configuration problem
                continue;
            }
            filterChain.addFilter(filterConfig);
            n++;
        }

        // 返回已完成的过滤器链
        return (filterChain);
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 返回<code>true</code>如果上下文相对请求路径与指定过滤器映射的要求相匹配;
     * 否则返回<code>null</code>.
     *
     * @param filterMap 正在检查的过滤器映射
     * @param requestPath 此请求的上下文相对请求路径
     */
    private boolean matchFiltersURL(FilterMap filterMap, String requestPath) {

        if (requestPath == null)
            return (false);

        // 上下文相关请求路径上的匹配
        String testPath = filterMap.getURLPattern();
        if (testPath == null)
            return (false);

        // Case 1 - Exact Match
        if (testPath.equals(requestPath))
            return (true);

        // Case 2 - Path Match ("/.../*")
        if (testPath.equals("/*"))
            return (true);
        if (testPath.endsWith("/*")) {
            if (testPath.regionMatches(0, requestPath, 0, 
                                       testPath.length() - 2)) {
                if (requestPath.length() == (testPath.length() - 2)) {
                    return (true);
                } else if ('/' == requestPath.charAt(testPath.length() - 2)) {
                    return (true);
                }
            }
            return (false);
        }

        // Case 3 - Extension Match
        if (testPath.startsWith("*.")) {
            int slash = requestPath.lastIndexOf('/');
            int period = requestPath.lastIndexOf('.');
            if ((slash >= 0) && (period > slash) 
                && (period != requestPath.length() - 1)
                && ((requestPath.length() - period) 
                    == (testPath.length() - 1))) {
                return (testPath.regionMatches(2, requestPath, period + 1,
                                               testPath.length() - 2));
            }
        }

        // Case 4 - "Default" Match
        return (false); // NOTE - Not relevant for selecting filters
    }


    /**
     * 返回<code>true</code> 如果指定的servlet名称与指定过滤器映射的要求相匹配;否则返回<code>false</code>.
     *
     * @param filterMap Filter mapping being checked
     * @param servletName Servlet name being checked
     */
    private boolean matchFiltersServlet(FilterMap filterMap, 
                                        String servletName) {
        if (servletName == null) {
            return (false);
        } else {
            if (servletName.equals(filterMap.getServletName())) {
                return (true);
            } else {
                return false;
            }
        }
    }


    /**
     * 如果调度器类型匹配FilterMap中指定的调度器类型
     */
    private boolean matchDispatcher(FilterMap filterMap, int dispatcher) {
        switch (dispatcher) {
            case FORWARD : {
                if (filterMap.getDispatcherMapping() == FilterMap.FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.FORWARD_ERROR ||
                    filterMap.getDispatcherMapping() == FilterMap.INCLUDE_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.INCLUDE_ERROR_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_FORWARD_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_FORWARD_INCLUDE) {
                        return true;
                }
                break;
            }
            case INCLUDE : {
                if (filterMap.getDispatcherMapping() == FilterMap.INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.INCLUDE_ERROR ||
                    filterMap.getDispatcherMapping() == FilterMap.INCLUDE_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.INCLUDE_ERROR_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_FORWARD_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_FORWARD_INCLUDE) {
                        return true;
                }
                break;
            }
            case REQUEST : {
                if (filterMap.getDispatcherMapping() == FilterMap.REQUEST ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_FORWARD_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_FORWARD_INCLUDE) {
                        return true;
                }
                break;
            }
            case ERROR : {
                if (filterMap.getDispatcherMapping() == FilterMap.ERROR ||
                    filterMap.getDispatcherMapping() == FilterMap.FORWARD_ERROR || 
                    filterMap.getDispatcherMapping() == FilterMap.INCLUDE_ERROR || 
                    filterMap.getDispatcherMapping() == FilterMap.INCLUDE_ERROR_FORWARD || 
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_FORWARD ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_FORWARD_INCLUDE ||
                    filterMap.getDispatcherMapping() == FilterMap.REQUEST_ERROR_INCLUDE) {
                        return true;
                }
                break;
            }
        }
        return false;
    }
}
