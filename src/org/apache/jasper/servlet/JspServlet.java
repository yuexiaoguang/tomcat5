package org.apache.jasper.servlet;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.jasper.Constants;
import org.apache.jasper.EmbeddedServletOptions;
import org.apache.jasper.Options;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.Localizer;

/**
 * JSP 引擎(a.k.a Jasper).
 *
 * servlet容器负责提供一个URLClassLoader, 对于 web 应用上下文Jasper使用的.
 * Jasper 为它的ServletContext类加载器尝试获取Tomcat ServletContext属性, 如果失败, 它使用父类加载器.
 * 其它情况下, 它必须是一个 URLClassLoader.
 */
public class JspServlet extends HttpServlet {

    private Log log = LogFactory.getLog(JspServlet.class);

    private ServletContext context;
    private ServletConfig config;
    private Options options;
    private JspRuntimeContext rctxt;


    /*
     * 初始化JspServlet.
     */
    public void init(ServletConfig config) throws ServletException {
        
        super.init(config);
        this.config = config;
        this.context = config.getServletContext();
        
        // 初始化JSP 运行上下文
        // 验证自定义 Options 实现
        String engineOptionsName = 
            config.getInitParameter("engineOptionsClass");
        if (engineOptionsName != null) {
            // 初始化声明的 Options 实现
            try {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                Class engineOptionsClass = loader.loadClass(engineOptionsName);
                Class[] ctorSig = { ServletConfig.class, ServletContext.class };
                Constructor ctor = engineOptionsClass.getConstructor(ctorSig);
                Object[] args = { config, context };
                options = (Options) ctor.newInstance(args);
            } catch (Throwable e) {
                // 需要本地化.
                log.warn("Failed to load engineOptionsClass", e);
                // 使用的默认的 Options 实现
                options = new EmbeddedServletOptions(config, context);
            }
        } else {
            // 使用的默认的 Options 实现
            options = new EmbeddedServletOptions(config, context);
        }
        rctxt = new JspRuntimeContext(context, options);
        
        if (log.isDebugEnabled()) {
            log.debug(Localizer.getMessage("jsp.message.scratch.dir.is",
                    options.getScratchDir().toString()));
            log.debug(Localizer.getMessage("jsp.message.dont.modify.servlets"));
        }
    }


    /**
     * 返回存在JspServletWrapper的JSP的数量, 即, JSP加载进这个JspServlet关联的Web应用的数量.
     *
     * <p>此信息可用于监视目的.
     *
     * @return JSP加载进这个JspServlet关联的Web应用的数量.
     */
    public int getJspCount() {
        return this.rctxt.getJspCount();
    }


    /**
     * 重置 JSP 重新加载计数.
     *
     * @param count 重置JSP重新加载计数器的值
     */
    public void setJspReloadCount(int count) {
        this.rctxt.setJspReloadCount(count);
    }


    /**
     * 获取已加载JSP的数量.
     *
     * <p>此信息可用于监视目的.
     *
     * @return 已加载JSP的数量.
     */
    public int getJspReloadCount() {
        return this.rctxt.getJspReloadCount();
    }


    /**
     * <p>找一个在JSP 1.2规范的Section 8.4.2描述的<em>precompilation request</em>.
     * <strong>WARNING</strong> - 不能使用<code>request.getParameter()</code>, 因为这将触发解析所有请求参数,
     * 而且不给servlet一个首先调用<code>request.setCharacterEncoding()</code>的机会.</p>
     *
     * @param request 处理的servlet请求
     *
     * @exception ServletException 如果指定名称的<code>jsp_precompile</code>参数值无效
     */
    boolean preCompile(HttpServletRequest request) throws ServletException {

        String queryString = request.getQueryString();
        if (queryString == null) {
            return (false);
        }
        int start = queryString.indexOf(Constants.PRECOMPILE);
        if (start < 0) {
            return (false);
        }
        queryString = queryString.substring(start + Constants.PRECOMPILE.length());
        if (queryString.length() == 0) {
            return (true);             // ?jsp_precompile
        }
        if (queryString.startsWith("&")) {
            return (true);             // ?jsp_precompile&foo=bar...
        }
        if (!queryString.startsWith("=")) {
            return (false);            // 其他名称或值的一部分
        }
        int limit = queryString.length();
        int ampersand = queryString.indexOf("&");
        if (ampersand > 0) {
            limit = ampersand;
        }
        String value = queryString.substring(1, limit);
        if (value.equals("true")) {
            return (true);             // ?jsp_precompile=true
        } else if (value.equals("false")) {
		    // 规范中说明如果jsp_precompile=false, 请求不应传递到JSP页面; 实现这一点最简单的方法是将标志设置为 true, 预编译页面.
		    // 这仍然符合规范, 因为预编译的要求可以忽略.
            return (true);             // ?jsp_precompile=false
        } else {
            throw new ServletException("Cannot have request parameter " +
                                       Constants.PRECOMPILE + " set to " +
                                       value);
        }
    }
    

    public void service (HttpServletRequest request, 
    			 HttpServletResponse response)
                throws ServletException, IOException {

        String jspUri = null;

        String jspFile = (String) request.getAttribute(Constants.JSP_FILE);
        if (jspFile != null) {
            // JSP 通过<servlet>声明中的<jsp-file>指定
            jspUri = jspFile;
        } else {
            /*
             * 检查所请求的JSP是否已成为RequestDispatcher.include()的目标
             */
            jspUri = (String) request.getAttribute(Constants.INC_SERVLET_PATH);
            if (jspUri != null) {
                /*
                 * 请求的JSP 是RequestDispatcher.include()的目标.
                 * 它的路径是从相关的 javax.servlet.include.* 请求属性中收集的
                 */
                String pathInfo = (String) request.getAttribute("javax.servlet.include.path_info");
                if (pathInfo != null) {
                    jspUri += pathInfo;
                }
            } else {
                /*
                 * 请求的JSP 不是RequestDispatcher.include()的目标.
                 * 从请求的 getServletPath() 和 getPathInfo()重建路径
                 */
                jspUri = request.getServletPath();
                String pathInfo = request.getPathInfo();
                if (pathInfo != null) {
                    jspUri += pathInfo;
                }
            }
        }

        if (log.isDebugEnabled()) {	    
            log.debug("JspEngine --> " + jspUri);
            log.debug("\t     ServletPath: " + request.getServletPath());
            log.debug("\t        PathInfo: " + request.getPathInfo());
            log.debug("\t        RealPath: " + context.getRealPath(jspUri));
            log.debug("\t      RequestURI: " + request.getRequestURI());
            log.debug("\t     QueryString: " + request.getQueryString());
            log.debug("\t  Request Params: ");
            Enumeration e = request.getParameterNames();
            while (e.hasMoreElements()) {
                String name = (String) e.nextElement();
                log.debug("\t\t " + name + " = " + request.getParameter(name));
            }
        }

        try {
            boolean precompile = preCompile(request);
            serviceJspFile(request, response, jspUri, null, precompile);
        } catch (RuntimeException e) {
            throw e;
        } catch (ServletException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        } catch (Throwable e) {
            throw new ServletException(e);
        }

    }

    public void destroy() {
        if (log.isDebugEnabled()) {
            log.debug("JspServlet.destroy()");
        }
        rctxt.destroy();
    }


    // -------------------------------------------------------- Private Methods

    private void serviceJspFile(HttpServletRequest request,
                                HttpServletResponse response, String jspUri,
                                Throwable exception, boolean precompile)
        throws ServletException, IOException {

        JspServletWrapper wrapper = (JspServletWrapper) rctxt.getWrapper(jspUri);
        if (wrapper == null) {
            synchronized(this) {
                wrapper = (JspServletWrapper) rctxt.getWrapper(jspUri);
                if (wrapper == null) {
                    // 检查请求的JSP 页面是否存在, 避免创建不必要的目录和文件.
                    if (null == context.getResource(jspUri)) {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND, jspUri);
                        return;
                    }
                    boolean isErrorPage = exception != null;
                    wrapper = new JspServletWrapper(config, options, jspUri,
                                                    isErrorPage, rctxt);
                    rctxt.addWrapper(jspUri,wrapper);
                }
            }
        }
        wrapper.service(request, response, precompile);
    }
}
