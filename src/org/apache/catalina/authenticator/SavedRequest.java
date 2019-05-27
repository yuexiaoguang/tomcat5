package org.apache.catalina.authenticator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

import javax.servlet.http.Cookie;

import org.apache.tomcat.util.buf.ByteChunk;

/**
 * 从请求中保存关键信息，以便基于表单的身份验证可以在用户身份验证后重现它
 * <p>
 * <b>IMPLEMENTATION NOTE</b> - 假设该对象仅从单个线程的上下文中访问，因此不进行内部集合类的同步
 */
public final class SavedRequest {

    /**
     * 关联的Cookies集合
     */
    private ArrayList cookies = new ArrayList();

    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    public Iterator getCookies() {
        return (cookies.iterator());
    }


    /**
     * 关联的Headers集合. 
     * 每个key是头文件名, 而值是一个ArrayList包含一个或多个Header的实际值.
     */
    private HashMap headers = new HashMap();

    public void addHeader(String name, String value) {
        ArrayList values = (ArrayList) headers.get(name);
        if (values == null) {
            values = new ArrayList();
            headers.put(name, values);
        }
        values.add(value);
    }

    public Iterator getHeaderNames() {
        return (headers.keySet().iterator());
    }

    public Iterator getHeaderValues(String name) {
        ArrayList values = (ArrayList) headers.get(name);
        if (values == null)
            return ((new ArrayList()).iterator());
        else
            return (values.iterator());
    }


    /**
     * 关联的Locales集合
     */
    private ArrayList locales = new ArrayList();

    public void addLocale(Locale locale) {
        locales.add(locale);
    }

    public Iterator getLocales() {
        return (locales.iterator());
    }


    /**
     * 使用的请求方法
     */
    private String method = null;

    public String getMethod() {
        return (this.method);
    }

    public void setMethod(String method) {
        this.method = method;
    }



    /**
     * 请求参数集合. 
     * 每个条目由参数名称映射到对应值的字符串数组
     */
    private HashMap parameters = new HashMap();

    public void addParameter(String name, String values[]) {
        parameters.put(name, values);
    }

    public Iterator getParameterNames() {
        return (parameters.keySet().iterator());
    }

    public String[] getParameterValues(String name) {
        return ((String[]) parameters.get(name));
    }


    /**
     * 关联的查询字符串
     */
    private String queryString = null;

    public String getQueryString() {
        return (this.queryString);
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }


    /**
     * 请求URI
     */
    private String requestURI = null;

    public String getRequestURI() {
        return (this.requestURI);
    }

    public void setRequestURI(String requestURI) {
        this.requestURI = requestURI;
    }

    
    /**
     * 此请求的主体
     */
    private ByteChunk body = null;
    
    public ByteChunk getBody() {
        return (this.body);
    }

    public void setBody(ByteChunk body) {
        this.body = body;
    }
}
