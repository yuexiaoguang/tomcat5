package org.apache.catalina.core;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Host;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.valves.ValveBase;

/**
 * Valve，实现了默认基本行为，为<code>StandardEngine</code>容器实现类.
 * <p>
 * <b>使用约束</b>: 只有在处理HTTP请求时，这种实现才可能有用.
 */
final class StandardEngineValve extends ValveBase {

    // ----------------------------------------------------- Instance Variables

    /**
     * 描述信息
     */
    private static final String info =
        "org.apache.catalina.core.StandardEngineValve/1.0";


    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);

    // ------------------------------------------------------------- Properties

    /**
     * 返回描述信息
     */
    public String getInfo() {
        return (info);
    }

    // --------------------------------------------------------- Public Methods

    /**
     * 选择合适的子级Host处理这个请求,基于请求的服务器名. 
     * 如果找不到匹配的主机, 返回一个合适的HTTP错误.
     *
     * @param request Request to be processed
     * @param response Response to be produced
     * @param valveContext Valve上下文用于重定向下一个Valve
     *
     * @exception IOException 如果发生输入/输出错误
     * @exception ServletException 如果发生servlet错误
     */
    public final void invoke(Request request, Response response) throws IOException, ServletException {

        // 选择要用于此请求的主机
        Host host = request.getHost();
        if (host == null) {
            response.sendError
                (HttpServletResponse.SC_BAD_REQUEST,
                 sm.getString("standardEngine.noHost", 
                              request.getServerName()));
            return;
        }

        // 让这个主机处理这个请求
        host.getPipeline().getFirst().invoke(request, response);
    }
}
