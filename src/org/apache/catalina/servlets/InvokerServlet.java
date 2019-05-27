package org.apache.catalina.servlets;


import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.util.StringManager;


/**
 * 大多数Web应用程序调用这个默认servlet, 用于请求中没有在Web应用程序部署描述符中注册的Servlet.
 */
public final class InvokerServlet extends HttpServlet implements ContainerServlet {


    // ----------------------------------------------------- Instance Variables


    /**
     * 与web应用程序关联的上下文容器.
     */
    private Context context = null;


    /**
     * 调试等级
     */
    private int debug = 0;


    /**
     * The string manager for this package.
     */
    private static StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * The Wrapper container associated with this servlet.
     */
    private Wrapper wrapper = null;


    // ----------------------------------------------- ContainerServlet Methods


    public Wrapper getWrapper() {
        return (this.wrapper);
    }


    public void setWrapper(Wrapper wrapper) {
        this.wrapper = wrapper;
        if (wrapper == null)
            context = null;
        else
            context = (Context) wrapper.getParent();
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 销毁这个servlet.
     */
    public void destroy() {
    }


    /**
     * 处理指定资源的GET请求.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
        throws IOException, ServletException {

        serveRequest(request, response);

    }


    /**
     * 处理指定资源的HEAD请求.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    public void doHead(HttpServletRequest request,
                       HttpServletResponse response)
        throws IOException, ServletException {
        serveRequest(request, response);
    }


    /**
     * 处理指定资源的POST请求.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    public void doPost(HttpServletRequest request,
                       HttpServletResponse response)
        throws IOException, ServletException {
        serveRequest(request, response);
    }


    /**
     * 初始化这个servlet.
     */
    public void init() throws ServletException {

        // Ensure that our ContainerServlet properties have been set
        if ((wrapper == null) || (context == null))
            throw new UnavailableException
                (sm.getString("invokerServlet.noWrapper"));

        // Set our properties from the initialization parameters
        String value = null;
        try {
            value = getServletConfig().getInitParameter("debug");
            debug = Integer.parseInt(value);
        } catch (Throwable t) {
            ;
        }
        if (debug >= 1)
            log("init: Associated with Context '" + context.getPath() + "'");

    }



    // -------------------------------------------------------- Private Methods


    /**
     * 为指定的请求服务，创建相应的响应.
     * 第一次请求特定servlet类之后, 它将直接服务(像任何注册的servlet) 因为它将在我们关联的上下文中注册和映射.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    public void serveRequest(HttpServletRequest request,
                             HttpServletResponse response)
        throws IOException, ServletException {

        // 不允许调用servlet通过指定调度
        if (request.getAttribute(Globals.NAMED_DISPATCHER_ATTR) != null)
            throw new ServletException
                (sm.getString("invokerServlet.notNamed"));

        // 标识输入参数和"included"状态
        String inRequestURI = null;
        String inServletPath = null;
        String inPathInfo = null;
        boolean included =
            (request.getAttribute(Globals.INCLUDE_REQUEST_URI_ATTR) != null);

        if (included) {
            inRequestURI =
                (String) request.getAttribute(Globals.INCLUDE_REQUEST_URI_ATTR);
            inServletPath =
                (String) request.getAttribute(Globals.INCLUDE_SERVLET_PATH_ATTR);
            inPathInfo =
                (String) request.getAttribute(Globals.INCLUDE_PATH_INFO_ATTR);
        } else {
            inRequestURI = request.getRequestURI();
            inServletPath = request.getServletPath();
            inPathInfo = request.getPathInfo();
        }
        if (debug >= 1) {
            log("included='" + included + "', requestURI='" +
                inRequestURI + "'");
            log("  servletPath='" + inServletPath + "', pathInfo='" +
                inPathInfo + "'");
        }

        // Make sure a servlet name or class name was specified
        if (inPathInfo == null) {
            if (debug >= 1)
                log("Invalid pathInfo '" + inPathInfo + "'");
            if (included)
                throw new ServletException
                    (sm.getString("invokerServlet.invalidPath", inRequestURI));
            else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                   inRequestURI);
                return;
            }
        }

        // Identify the outgoing servlet name or class, and outgoing path info
        String pathInfo = inPathInfo;
        String servletClass = pathInfo.substring(1);
        int slash = servletClass.indexOf('/');
        //        if (debug >= 2)
        //            log("  Calculating with servletClass='" + servletClass +
        //                "', pathInfo='" + pathInfo + "', slash=" + slash);
        if (slash >= 0) {
            pathInfo = servletClass.substring(slash);
            servletClass = servletClass.substring(0, slash);
        } else {
            pathInfo = "";
        }

        if (servletClass.startsWith("org.apache.catalina")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                               inRequestURI);
            return;
        }

        if (debug >= 1)
            log("Processing servlet '" + servletClass +
                "' with path info '" + pathInfo + "'");
        String name = "org.apache.catalina.INVOKER." + servletClass;
        String pattern = inServletPath + "/" + servletClass + "/*";
        Wrapper wrapper = null;

        // 同步以避免竞争, 当多个请求试图同时初始化同一servlet时
        synchronized (this) {

            // Are we referencing an existing servlet class or name?
            wrapper = (Wrapper) context.findChild(servletClass);
            if (wrapper == null)
                wrapper = (Wrapper) context.findChild(name);
            if (wrapper != null) {
                String actualServletClass = wrapper.getServletClass();
                if ((actualServletClass != null)
                    && (actualServletClass.startsWith
                        ("org.apache.catalina"))) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                       inRequestURI);
                    return;
                }
                if (debug >= 1)
                    log("Using wrapper for servlet '" +
                        wrapper.getName() + "' with mapping '" +
                        pattern + "'");
                context.addServletMapping(pattern, wrapper.getName());
            }

            // No, 为指定的servlet类创建一个新的包装器
            else {

                if (debug >= 1)
                    log("Creating wrapper for '" + servletClass +
                        "' with mapping '" + pattern + "'");

                try {
                    wrapper = context.createWrapper();
                    wrapper.setName(name);
                    wrapper.setLoadOnStartup(1);
                    wrapper.setServletClass(servletClass);
                    context.addChild(wrapper);
                    context.addServletMapping(pattern, name);
                } catch (Throwable t) {
                    log(sm.getString("invokerServlet.cannotCreate",
                                     inRequestURI), t);
                    context.removeServletMapping(pattern);
                    context.removeChild(wrapper);
                    if (included)
                        throw new ServletException
                            (sm.getString("invokerServlet.cannotCreate",
                                          inRequestURI), t);
                    else {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                           inRequestURI);
                        return;
                    }
                }
            }
        }

        // Create a request wrapper to pass on to the invoked servlet
        InvokerHttpRequest wrequest =
            new InvokerHttpRequest(request);
        wrequest.setRequestURI(inRequestURI);
        StringBuffer sb = new StringBuffer(inServletPath);
        sb.append("/");
        sb.append(servletClass);
        wrequest.setServletPath(sb.toString());
        if ((pathInfo == null) || (pathInfo.length() < 1)) {
            wrequest.setPathInfo(null);
            wrequest.setPathTranslated(null);
        } else {
            wrequest.setPathInfo(pathInfo);
            wrequest.setPathTranslated
                (getServletContext().getRealPath(pathInfo));
        }

        // 分配servlet实例来执行此请求
        Servlet instance = null;
        try {
            //            if (debug >= 2)
            //                log("  Allocating servlet instance");
            instance = wrapper.allocate();
        } catch (ServletException e) {
            log(sm.getString("invokerServlet.allocate", inRequestURI), e);
            context.removeServletMapping(pattern);
            context.removeChild(wrapper);
            Throwable rootCause = e.getRootCause();
            if (rootCause == null)
                rootCause = e;
            if (rootCause instanceof ClassNotFoundException) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                   inRequestURI);
                return;
            } else if (rootCause instanceof IOException) {
                throw (IOException) rootCause;
            } else if (rootCause instanceof RuntimeException) {
                throw (RuntimeException) rootCause;
            } else if (rootCause instanceof ServletException) {
                throw (ServletException) rootCause;
            } else {
                throw new ServletException
                    (sm.getString("invokerServlet.allocate", inRequestURI),
                     rootCause);
            }
        } catch (Throwable e) {
            log(sm.getString("invokerServlet.allocate", inRequestURI), e);
            context.removeServletMapping(pattern);
            context.removeChild(wrapper);
            throw new ServletException
                (sm.getString("invokerServlet.allocate", inRequestURI), e);
        }

        // After loading the wrapper, restore some of the fields when including
        if (included) {
            wrequest.setRequestURI(request.getRequestURI());
            wrequest.setPathInfo(request.getPathInfo());
            wrequest.setServletPath(request.getServletPath());
        }

        // Invoke the service() method of the allocated servlet
        try {
            String jspFile = wrapper.getJspFile();
            if (jspFile != null)
                request.setAttribute(Globals.JSP_FILE_ATTR, jspFile);
            else
                request.removeAttribute(Globals.JSP_FILE_ATTR);
            request.setAttribute(Globals.INVOKED_ATTR,
                                 request.getServletPath());
            //            if (debug >= 2)
            //                log("  Calling service() method, jspFile=" +
            //                    jspFile);
            instance.service(wrequest, response);
            request.removeAttribute(Globals.INVOKED_ATTR);
            request.removeAttribute(Globals.JSP_FILE_ATTR);
        } catch (IOException e) {
            //            if (debug >= 2)
            //                log("  service() method IOException", e);
            request.removeAttribute(Globals.INVOKED_ATTR);
            request.removeAttribute(Globals.JSP_FILE_ATTR);
            try {
                wrapper.deallocate(instance);
            } catch (Throwable f) {
                ;
            }
            throw e;
        } catch (UnavailableException e) {
            //            if (debug >= 2)
            //                log("  service() method UnavailableException", e);
            context.removeServletMapping(pattern);
            request.removeAttribute(Globals.INVOKED_ATTR);
            request.removeAttribute(Globals.JSP_FILE_ATTR);
            try {
                wrapper.deallocate(instance);
            } catch (Throwable f) {
                ;
            }
            throw e;
        } catch (ServletException e) {
            //            if (debug >= 2)
            //                log("  service() method ServletException", e);
            request.removeAttribute(Globals.INVOKED_ATTR);
            request.removeAttribute(Globals.JSP_FILE_ATTR);
            try {
                wrapper.deallocate(instance);
            } catch (Throwable f) {
                ;
            }
            throw e;
        } catch (RuntimeException e) {
            //            if (debug >= 2)
            //                log("  service() method RuntimeException", e);
            request.removeAttribute(Globals.INVOKED_ATTR);
            request.removeAttribute(Globals.JSP_FILE_ATTR);
            try {
                wrapper.deallocate(instance);
            } catch (Throwable f) {
                ;
            }
            throw e;
        } catch (Throwable e) {
            //            if (debug >= 2)
            //                log("  service() method Throwable", e);
            request.removeAttribute(Globals.INVOKED_ATTR);
            request.removeAttribute(Globals.JSP_FILE_ATTR);
            try {
                wrapper.deallocate(instance);
            } catch (Throwable f) {
                ;
            }
            throw new ServletException("Invoker service() exception", e);
        }

        // 释放分配的servlet实例
        try {
            //            if (debug >= 2)
            //                log("  deallocate servlet instance");
            wrapper.deallocate(instance);
        } catch (ServletException e) {
            log(sm.getString("invokerServlet.deallocate", inRequestURI), e);
            throw e;
        } catch (Throwable e) {
            log(sm.getString("invokerServlet.deallocate", inRequestURI), e);
            throw new ServletException
                (sm.getString("invokerServlet.deallocate", inRequestURI), e);
        }
    }
}
