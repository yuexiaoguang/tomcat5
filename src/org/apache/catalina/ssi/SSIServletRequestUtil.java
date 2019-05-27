package org.apache.catalina.ssi;


import javax.servlet.http.HttpServletRequest;
import org.apache.catalina.util.RequestUtil;

public class SSIServletRequestUtil {
    /**
     * 返回与此servlet关联的相对路径.
     * 从DefaultServlet.java中拿. 也许这应该放进  org.apache.catalina.util somewhere?  似乎会被广泛使用.
     * 
     * @param request
     *            The servlet request we are processing
     */
    public static String getRelativePath(HttpServletRequest request) {
        // Are we being processed by a RequestDispatcher.include()?
        if (request.getAttribute("javax.servlet.include.request_uri") != null) {
            String result = (String)request
                    .getAttribute("javax.servlet.include.path_info");
            if (result == null)
                result = (String)request
                        .getAttribute("javax.servlet.include.servlet_path");
            if ((result == null) || (result.equals(""))) result = "/";
            return (result);
        }
        // No, extract the desired path directly from the request
        String result = request.getPathInfo();
        if (result == null) {
            result = request.getServletPath();
        }
        if ((result == null) || (result.equals(""))) {
            result = "/";
        }
        return normalize(result);
    }


    /**
     * 返回上下文相对路径, 以"/"开头, 表示指定路径的规范版本， 在".." 和 "." 节点被解析之后.
     * 如果指定的路径试图超出当前上下文的边界(i.e. 很多".."路径元素存在), 返回<code>null</code>.
     *
     * 这个正常化应该和 DefaultServlet.normalize是一样的, 这几乎是和RequestUtil.normalize一样的
     * (见下面的源代码). 我们需要所有这些重复吗?
     * 
     * @param path Path to be normalized
     */
    public static String normalize(String path) {
        if (path == null) return null;
        String normalized = path;
        //Why doesn't RequestUtil do this??
        // Normalize the slashes and add leading slash if necessary
        if (normalized.indexOf('\\') >= 0)
            normalized = normalized.replace('\\', '/');
        normalized = RequestUtil.normalize(path);
        return normalized;
    }
}