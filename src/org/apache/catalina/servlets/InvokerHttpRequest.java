package org.apache.catalina.servlets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * 封装<code>javax.servlet.http.HttpServletRequest</code>，当<code>InvokerServlet</code> 处理调用servlet的初始请求.
 * 后续请求将直接映射到servlet, 因为已经创建了一个新的servlet映射.
 */
class InvokerHttpRequest extends HttpServletRequestWrapper {


    // ----------------------------------------------------------- Constructors


    /**
     * @param request The servlet request being wrapped
     */
    public InvokerHttpRequest(HttpServletRequest request) {
        super(request);
        this.pathInfo = request.getPathInfo();
        this.pathTranslated = request.getPathTranslated();
        this.requestURI = request.getRequestURI();
        this.servletPath = request.getServletPath();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 描述信息
     */
    protected static final String info =
        "org.apache.catalina.servlets.InvokerHttpRequest/1.0";


    /**
     * 请求的路径信息.
     */
    protected String pathInfo = null;


    /**
     * 此请求的翻译路径信息.
     */
    protected String pathTranslated = null;


    /**
     * 请求URI.
     */
    protected String requestURI = null;


    /**
     * servlet路径.
     */
    protected String servletPath = null;


    // --------------------------------------------- HttpServletRequest Methods


    /**
     * 重写包装的request的<code>getPathInfo()</code>方法.
     */
    public String getPathInfo() {
        return (this.pathInfo);
    }


    /**
     * 重写包装的request的<code>getPathTranslated()</code>方法.
     */
    public String getPathTranslated() {
        return (this.pathTranslated);
    }


    /**
     * 重写包装的request的<code>getRequestURI()</code>方法.
     */
    public String getRequestURI() {
        return (this.requestURI);
    }


    /**
     * 重写包装的request的<code>getServletPath()</code>方法.
     */
    public String getServletPath() {
        return (this.servletPath);
    }


    // -------------------------------------------------------- Package Methods



    /**
     * 返回描述信息.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 设置描述信息
     *
     * @param pathInfo The new path info
     */
    void setPathInfo(String pathInfo) {
        this.pathInfo = pathInfo;
    }


    /**
     * 设置此请求的翻译路径信息.
     *
     * @param pathTranslated The new translated path info
     */
    void setPathTranslated(String pathTranslated) {
        this.pathTranslated = pathTranslated;
    }


    /**
     * 设置请求URI.
     *
     * @param requestURI The new request URI
     */
    void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }


    /**
     * 设置servlet路径.
     *
     * @param servletPath The new servlet path
     */
    void setServletPath(String servletPath) {
        this.servletPath = servletPath;
    }
}
