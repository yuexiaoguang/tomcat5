package org.apache.catalina.core;

import java.io.IOException;
import java.util.Locale;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.catalina.util.StringManager;

/**
 * 包装一个<code>javax.servlet.http.HttpServletResponse</code>
 * 转换一个应用响应对象(这可能是传递给servlet的原始消息, 或者可能基于 2.3
 * <code>javax.servlet.http.HttpServletResponseWrapper</code>)
 * 回到一个内部的<code>org.apache.catalina.HttpResponse</code>.
 * <p>
 * <strong>WARNING</strong>: 
 * 由于java不支持多重继承, <code>ApplicationResponse</code>中所有的逻辑在<code>ApplicationHttpResponse</code>中是重复的.
 * 确保在进行更改时保持这两个类同步!
 */
class ApplicationHttpResponse extends HttpServletResponseWrapper {

    // ----------------------------------------------------------- Constructors

    /**
     * @param response The servlet response being wrapped
     */
    public ApplicationHttpResponse(HttpServletResponse response) {
        this(response, false);
    }


    /**
     * @param response The servlet response being wrapped
     * @param included <code>true</code>如果这个响应被<code>RequestDispatcher.include()</code>处理
     */
    public ApplicationHttpResponse(HttpServletResponse response,
                                   boolean included) {
        super(response);
        setIncluded(included);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 包装的响应是<code>include()</code>调用的主题?
     */
    protected boolean included = false;


    /**
     * 描述信息
     */
    protected static final String info =
        "org.apache.catalina.core.ApplicationHttpResponse/1.0";


    /**
     * The string manager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);


    // ------------------------------------------------ ServletResponse Methods


    /**
     * 不允许调用included响应的<code>reset()</code>
     *
     * @exception IllegalStateException 如果响应已经提交
     */
    public void reset() {
        // If already committed, the wrapped response will throw ISE
        if (!included || getResponse().isCommitted())
            getResponse().reset();
    }


    /**
     * 不允许调用included响应的<code>setContentLength()</code>
     *
     * @param len 内容长度
     */
    public void setContentLength(int len) {
        if (!included)
            getResponse().setContentLength(len);
    }


    /**
     * 不允许调用included响应的<code>setContentType()</code>
     *
     * @param type The new content type
     */
    public void setContentType(String type) {
        if (!included)
            getResponse().setContentType(type);
    }


    /**
     * 不允许调用included响应的<code>setLocale()</code>
     *
     * @param loc The new locale
     */
    public void setLocale(Locale loc) {
        if (!included)
            getResponse().setLocale(loc);
    }


    /**
     * 忽略included响应中的<code>setBufferSize()</code>调用.
     *
     * @param size 缓冲区的大小
     */
    public void setBufferSize(int size) {
        if (!included)
            getResponse().setBufferSize(size);
    }


    // -------------------------------------------- HttpServletResponse Methods


    /**
     * 不允许调用included响应的<code>addCookie()</code>
     *
     * @param cookie The new cookie
     */
    public void addCookie(Cookie cookie) {
        if (!included)
            ((HttpServletResponse) getResponse()).addCookie(cookie);
    }


    /**
     * 不允许调用included响应的<code>addDateHeader()</code>
     *
     * @param name The new header name
     * @param value The new header value
     */
    public void addDateHeader(String name, long value) {
        if (!included)
            ((HttpServletResponse) getResponse()).addDateHeader(name, value);
    }


    /**
     * 不允许调用included响应的<code>addHeader()</code>
     *
     * @param name The new header name
     * @param value The new header value
     */
    public void addHeader(String name, String value) {
        if (!included)
            ((HttpServletResponse) getResponse()).addHeader(name, value);
    }


    /**
     * 不允许调用included响应的<code>addIntHeader()</code>
     *
     * @param name The new header name
     * @param value The new header value
     */
    public void addIntHeader(String name, int value) {
        if (!included)
            ((HttpServletResponse) getResponse()).addIntHeader(name, value);
    }


    /**
     * 不允许调用included响应的<code>sendError()</code>
     *
     * @param sc 状态码
     *
     * @exception IOException if an input/output error occurs
     */
    public void sendError(int sc) throws IOException {
        if (!included)
            ((HttpServletResponse) getResponse()).sendError(sc);
    }


    /**
     * 不允许调用included响应的<code>sendError()</code>.
     *
     * @param sc The new status code
     * @param msg The new message
     *
     * @exception IOException if an input/output error occurs
     */
    public void sendError(int sc, String msg) throws IOException {
        if (!included)
            ((HttpServletResponse) getResponse()).sendError(sc, msg);
    }


    /**
     * 不允许调用included响应的<code>sendRedirect()</code>.
     *
     * @param location The new location
     *
     * @exception IOException if an input/output error occurs
     */
    public void sendRedirect(String location) throws IOException {

        if (!included)
            ((HttpServletResponse) getResponse()).sendRedirect(location);

    }


    /**
     * 不允许调用included响应的<code>setDateHeader()</code>.
     *
     * @param name The new header name
     * @param value The new header value
     */
    public void setDateHeader(String name, long value) {

        if (!included)
            ((HttpServletResponse) getResponse()).setDateHeader(name, value);

    }


    /**
     * 不允许调用included响应的<code>setHeader()</code>.
     *
     * @param name The new header name
     * @param value The new header value
     */
    public void setHeader(String name, String value) {

        if (!included)
            ((HttpServletResponse) getResponse()).setHeader(name, value);

    }


    /**
     * 不允许调用included响应的<code>setIntHeader()</code>.
     *
     * @param name The new header name
     * @param value The new header value
     */
    public void setIntHeader(String name, int value) {

        if (!included)
            ((HttpServletResponse) getResponse()).setIntHeader(name, value);

    }


    /**
     * 不允许调用included响应的<code>setStatus()</code>.
     *
     * @param sc The new status code
     */
    public void setStatus(int sc) {

        if (!included)
            ((HttpServletResponse) getResponse()).setStatus(sc);

    }


    /**
     * 不允许调用included响应的<code>setStatus()</code>.
     *
     * @param sc The new status code
     * @param msg The new message
     */
    public void setStatus(int sc, String msg) {

        if (!included)
            ((HttpServletResponse) getResponse()).setStatus(sc, msg);

    }


    // -------------------------------------------------------- Package Methods


    /**
     * 返回描述信息
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 返回这个响应的included 标记.
     */
    boolean isIncluded() {
        return (this.included);
    }


    /**
     * 设置这个响应的included 标记.
     *
     * @param included The new included flag
     */
    void setIncluded(boolean included) {
        this.included = included;
    }


    /**
     * 设置要包装的响应
     *
     * @param response The new wrapped response
     */
    void setResponse(HttpServletResponse response) {
        super.setResponse(response);
    }
}
