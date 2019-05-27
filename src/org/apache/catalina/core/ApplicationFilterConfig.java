package org.apache.catalina.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.util.Enumerator;
import org.apache.tomcat.util.log.SystemLogHandler;

/**
 * <code>javax.servlet.FilterConfig</code>实现类用于管理过滤器实例初始化，在应用第一次启动的时候
 */
final class ApplicationFilterConfig implements FilterConfig, Serializable {

    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( ApplicationFilterConfig.class );
 
    // ----------------------------------------------------------- Constructors


    /**
     * @param context 要关联的context
     * @param filterDef Filter definition for which a FilterConfig is to be
     *  constructed
     *
     * @exception ClassCastException 如果指定的类没有实现<code>javax.servlet.Filter</code>接口
     * @exception ClassNotFoundException 如果找不到过滤器类
     * @exception IllegalAccessException 如果过滤器类不能公开实例化
     * @exception InstantiationException 如果实例化过滤器对象过程中发生异常
     * @exception ServletException 如果过滤器类的init() 方法抛出
     */
    public ApplicationFilterConfig(Context context, FilterDef filterDef)
        throws ClassCastException, ClassNotFoundException,
               IllegalAccessException, InstantiationException,
               ServletException {
        super();
        this.context = context;
        setFilterDef(filterDef);
    }


    // ----------------------------------------------------- Instance Variables


    private Context context = null;


    private transient Filter filter = null;


    private FilterDef filterDef = null;


    // --------------------------------------------------- FilterConfig Methods


    /**
     * 返回配置的过滤器名称
     */
    public String getFilterName() {
        return (filterDef.getFilterName());
    }


    /**
     * 返回指定初始化参数的值, 或<code>null</code>
     *
     * @param name 请求的初始化参数的名称
     */
    public String getInitParameter(String name) {
        Map map = filterDef.getParameterMap();
        if (map == null)
            return (null);
        else
            return ((String) map.get(name));
    }


    /**
     * 返回初始化参数名称
     */
    public Enumeration getInitParameterNames() {
        Map map = filterDef.getParameterMap();
        if (map == null)
            return (new Enumerator(new ArrayList()));
        else
            return (new Enumerator(map.keySet()));
    }


    /**
     * 返回ServletContext.
     */
    public ServletContext getServletContext() {
        return (this.context.getServletContext());
    }


    public String toString() {
        StringBuffer sb = new StringBuffer("ApplicationFilterConfig[");
        sb.append("name=");
        sb.append(filterDef.getFilterName());
        sb.append(", filterClass=");
        sb.append(filterDef.getFilterClass());
        sb.append("]");
        return (sb.toString());
    }


    // -------------------------------------------------------- Package Methods


    /**
     * 返回配置的Filter
     *
     * @exception ClassCastException 如果指定的类没有实现<code>javax.servlet.Filter</code>接口
     * @exception ClassNotFoundException 如果找不到过滤器类
     * @exception IllegalAccessException 如果过滤器类不能公开实例化
     * @exception InstantiationException 如果实例化过滤器对象时发生异常
     * @exception ServletException 如果过滤器的init() 方法抛出
     */
    Filter getFilter() throws ClassCastException, ClassNotFoundException,
        IllegalAccessException, InstantiationException, ServletException {

        // 返回现有的过滤器实例
        if (this.filter != null)
            return (this.filter);

        // 标识将使用的类加载器
        String filterClass = filterDef.getFilterClass();
        ClassLoader classLoader = null;
        if (filterClass.startsWith("org.apache.catalina."))
            classLoader = this.getClass().getClassLoader();
        else
            classLoader = context.getLoader().getClassLoader();

        ClassLoader oldCtxClassLoader =
            Thread.currentThread().getContextClassLoader();

        // 实例化此过滤器的一个新实例并返回它
        Class clazz = classLoader.loadClass(filterClass);
        this.filter = (Filter) clazz.newInstance();
        if (context instanceof StandardContext &&
            ((StandardContext)context).getSwallowOutput()) {
            try {
                SystemLogHandler.startCapture();
                filter.init(this);
            } finally {
                String log = SystemLogHandler.stopCapture();
                if (log != null && log.length() > 0) {
                    getServletContext().log(log);
                }
            }
        } else {
            filter.init(this);
        }
        return (this.filter);
    }


    FilterDef getFilterDef() {
        return (this.filterDef);
    }


    /**
     * 释放过滤器实例
     */
    void release() {
        if (this.filter != null){
             if( System.getSecurityManager() != null) {
                try{
                    SecurityUtil.doAsPrivilege("destroy",
                                               filter); 
                    SecurityUtil.remove(filter);
                } catch(java.lang.Exception ex){                    
                    log.error("ApplicationFilterConfig.doAsPrivilege", ex);
                }
            } else { 
                filter.destroy();
            }
        }
        this.filter = null;
     }


    /**
     * 设置配置的过滤器定义.  初始化响应的过滤器类实例有副作用.
     *
     * @param filterDef The new filter definition
     *
     * @exception ClassCastException 如果指定的类没有实现<code>javax.servlet.Filter</code>接口
     * @exception ClassNotFoundException if the filter class cannot be found
     * @exception IllegalAccessException 如果过滤器类不能公开实例化
     * @exception InstantiationException 如果实例化过滤器对象过程中发生异常
     * @exception ServletException if thrown by the filter's init() method
     */
    void setFilterDef(FilterDef filterDef)
        throws ClassCastException, ClassNotFoundException,
               IllegalAccessException, InstantiationException,
               ServletException {

        this.filterDef = filterDef;
        if (filterDef == null) {

            // 释放以前分配的过滤器实例
            if (this.filter != null){
                 if( System.getSecurityManager() != null) {
                    try{
                        SecurityUtil.doAsPrivilege("destroy",
                                                   filter);  
                        SecurityUtil.remove(filter);
                    } catch(java.lang.Exception ex){    
                        log.error("ApplicationFilterConfig.doAsPrivilege", ex);
                    }
                } else { 
                    filter.destroy();
                }
            }
            this.filter = null;

        } else {
            // 分配一个新的过滤器实例
            Filter filter = getFilter();
        }
    }
}
