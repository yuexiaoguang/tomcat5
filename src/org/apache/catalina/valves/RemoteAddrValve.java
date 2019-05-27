package org.apache.catalina.valves;


import java.io.IOException;
import javax.servlet.ServletException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;


/**
 * <code>RequestFilterValve</code>实现类, 基于远程客户端IP地址的字符串的过滤器.
 */
public final class RemoteAddrValve extends RequestFilterValve {


    // ----------------------------------------------------- Instance Variables


    /**
     * 实现类的描述信息
     */
    private static final String info =
        "org.apache.catalina.valves.RemoteAddrValve/1.0";


    // ------------------------------------------------------------- Properties


    /**
     * 返回实现类描述信息
     */
    public String getInfo() {
        return (info);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 提取所需的请求属性, 传递它到(与指定的请求和响应对象一起)<code>process()</code>方法来执行实际筛选.
     * 这个方法必须由一个具体的子类来实现.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException {
        process(request.getRequest().getRemoteAddr(), request, response);
    }
}
