package org.apache.jasper.servlet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.SingleThreadModel;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.tagext.TagInfo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.Options;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.Localizer;
import org.apache.jasper.runtime.JspSourceDependent;

/**
 * JSP 引擎(a.k.a Jasper).
 *
 * servlet 容器负责提供一个URLClassLoader , 用于Web应用程序上下文Jasper. Jasper 将为它的ServletContext类加载器获取Tomcat ServletContext 属性,
 * 如果失败, 使用父类加载器.
 * 在其他情况下, 它必须是一个 URLClassLoader.
 */
public class JspServletWrapper {

    // Logger
    private Log log = LogFactory.getLog(JspServletWrapper.class);

    private Servlet theServlet;
    private String jspUri;
    private Class servletClass;
    private Class tagHandlerClass;
    private JspCompilationContext ctxt;
    private long available = 0L;
    private ServletConfig config;
    private Options options;
    private boolean firstTime = true;
    private boolean reload = true;
    private boolean isTagFile;
    private int tripCount;
    private JasperException compileException;
    private long servletClassLastModifiedTime;
    private long lastModificationTest = 0L;

    /*
     * 用于JSP 页面.
     */
    JspServletWrapper(ServletConfig config, Options options, String jspUri,
                      boolean isErrorPage, JspRuntimeContext rctxt)
            throws JasperException {

    	this.isTagFile = false;
        this.config = config;
        this.options = options;
        this.jspUri = jspUri;
        ctxt = new JspCompilationContext(jspUri, isErrorPage, options,
					 config.getServletContext(),
					 this, rctxt);
    }

    /*
     * 用于tag 文件.
     */
    public JspServletWrapper(ServletContext servletContext,
			     Options options,
			     String tagFilePath,
			     TagInfo tagInfo,
			     JspRuntimeContext rctxt,
			     URL tagFileJarUrl)
	    throws JasperException {

	this.isTagFile = true;
        this.config = null;	// not used
        this.options = options;
	this.jspUri = tagFilePath;
	this.tripCount = 0;
        ctxt = new JspCompilationContext(jspUri, tagInfo, options,
					 servletContext, this, rctxt,
					 tagFileJarUrl);
    }

    public JspCompilationContext getJspEngineContext() {
        return ctxt;
    }

    public void setReload(boolean reload) {
        this.reload = reload;
    }

    public Servlet getServlet()
        throws ServletException, IOException, FileNotFoundException
    {
        if (reload) {
            synchronized (this) {
                // 同步在JSW使不同的页面同时加载, 但不是同一个页面.
                if (reload) {
                    // 这是为了维护原始协议.
                    destroy();
                    
                    try {
                        servletClass = ctxt.load();
                        theServlet = (Servlet) servletClass.newInstance();
                    } catch( IllegalAccessException ex1 ) {
                        throw new JasperException( ex1 );
                    } catch( InstantiationException ex ) {
                        throw new JasperException( ex );
                    }
                    
                    theServlet.init(config);

                    if (!firstTime) {
                        ctxt.getRuntimeContext().incrementJspReloadCount();
                    }

                    reload = false;
                }
            }    
        }
        return theServlet;
    }

    public ServletContext getServletContext() {
        return config.getServletContext();
    }

    /**
     * 设置编译异常.
     *
     * @param je The compilation exception
     */
    public void setCompilationException(JasperException je) {
        this.compileException = je;
    }

    /**
     * 设置servlet类文件的最后修改时间.
     *
     * @param lastModified servlet类文件的最后修改时间
     */
    public void setServletClassLastModifiedTime(long lastModified) {
        if (this.servletClassLastModifiedTime < lastModified) {
            synchronized (this) {
                if (this.servletClassLastModifiedTime < lastModified) {
                    this.servletClassLastModifiedTime = lastModified;
                    reload = true;
                }
            }
        }
    }

    /**
     * 编译并加载标签文件
     */
    public Class loadTagFile() throws JasperException {

        try {
            if (ctxt.isRemoved()) {
                throw new FileNotFoundException(jspUri);
            }
            if (options.getDevelopment() || firstTime ) {
                synchronized (this) {
                    firstTime = false;
                    ctxt.compile();
                }
            } else {
                if (compileException != null) {
                    throw compileException;
                }
            }

            if (reload) {
                tagHandlerClass = ctxt.load();
            }
        } catch (FileNotFoundException ex) {
            throw new JasperException(ex);
		}
	
		return tagHandlerClass;
    }

    /**
     * 编译并加载标签文件的原型. 当编译带有循环依赖关系的标签文件时，这是必需的. 一个原型（骨架）与其他标签文件的其他无依赖性生成和编译.
     */
    public Class loadTagFilePrototype() throws JasperException {

		ctxt.setPrototypeMode(true);
		try {
		    return loadTagFile();
		} finally {
		    ctxt.setPrototypeMode(false);
		}
    }

    /**
     * 获取当前页面具有源依赖项的文件列表.
     */
    public java.util.List getDependants() {
		try {
		    Object target;
		    if (isTagFile) {
                if (reload) {
                    tagHandlerClass = ctxt.load();
                }
                target = tagHandlerClass.newInstance();
		    } else {
		    	target = getServlet();
		    }
		    if (target != null && target instanceof JspSourceDependent) {
	            return ((java.util.List) ((JspSourceDependent) target).getDependants());
		    }
		} catch (Throwable ex) {
		}
		return null;
    }

    public boolean isTagFile() {
    	return this.isTagFile;
    }

    public int incTripCount() {
    	return tripCount++;
    }

    public int decTripCount() {
    	return tripCount--;
    }

    public void service(HttpServletRequest request, 
                        HttpServletResponse response,
                        boolean precompile)
	    throws ServletException, IOException, FileNotFoundException {
        try {

            if (ctxt.isRemoved()) {
                throw new FileNotFoundException(jspUri);
            }

            if ((available > 0L) && (available < Long.MAX_VALUE)) {
                response.setDateHeader("Retry-After", available);
                response.sendError
                    (HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                     Localizer.getMessage("jsp.error.unavailable"));
            }

            /*
             * (1) 编译
             */
            if (options.getDevelopment() || firstTime ) {
                synchronized (this) {
                    firstTime = false;

                    // 以下设置重新加载为 true
                    ctxt.compile();
                }
            } else {
                if (compileException != null) {
                    // 抛出缓存编译异常
                    throw compileException;
                }
            }

            /*
             * (2) (Re)加载servlet类文件
             */
            getServlet();

            // 如果一个页面只被预编译, 返回.
            if (precompile) {
                return;
            }

            /*
             * (3) 服务请求
             */
            if (theServlet instanceof SingleThreadModel) {
               // 在包装上同步, 以便在服务前确定页面的新鲜度
               synchronized (this) {
                   theServlet.service(request, response);
                }
            } else {
                theServlet.service(request, response);
            }

        } catch (UnavailableException ex) {
            String includeRequestUri = (String)
                request.getAttribute("javax.servlet.include.request_uri");
            if (includeRequestUri != null) {
                // 这个文件是包含的. 抛出一个异常作为一个 response.sendError() 将被servlet引擎忽略.
                throw ex;
            } else {
                int unavailableSeconds = ex.getUnavailableSeconds();
                if (unavailableSeconds <= 0) {
                    unavailableSeconds = 60;        // Arbitrary default
                }
                available = System.currentTimeMillis() +
                    (unavailableSeconds * 1000L);
                response.sendError
                    (HttpServletResponse.SC_SERVICE_UNAVAILABLE, 
                     ex.getMessage());
            }
        } catch (FileNotFoundException ex) {
            ctxt.incrementRemoved();
            String includeRequestUri = (String)
                request.getAttribute("javax.servlet.include.request_uri");
            if (includeRequestUri != null) {
                // 包含此文件. 抛出一个异常作为一个 response.sendError() 将被servlet引擎忽略.
                throw new ServletException(ex);
            } else {
                try {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, 
                                      ex.getMessage());
                } catch (IllegalStateException ise) {
                    log.error(Localizer.getMessage("jsp.error.file.not.found",
						   ex.getMessage()),
			      ex);
                }
            }
        } catch (ServletException ex) {
	    throw ex;
        } catch (IOException ex) {
            throw ex;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new JasperException(ex);
        }
    }

    public void destroy() {
        if (theServlet != null) {
            theServlet.destroy();
        }
    }

    public long getLastModificationTest() {
        return lastModificationTest;
    }
    
    public void setLastModificationTest(long lastModificationTest) {
        this.lastModificationTest = lastModificationTest;
    }
}
