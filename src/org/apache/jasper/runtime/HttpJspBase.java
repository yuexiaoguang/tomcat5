package org.apache.jasper.runtime;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.HttpJspPage;
import javax.servlet.jsp.JspFactory;

import org.apache.jasper.compiler.Localizer;

/**
 * 所有JSP生成的 servlet地父类.
 */
public abstract class HttpJspBase extends HttpServlet implements HttpJspPage {
    
    static {
        if( JspFactory.getDefaultFactory() == null ) {
            JspFactoryImpl factory = new JspFactoryImpl();
            if( System.getSecurityManager() != null ) {
                String basePackage = "org.apache.jasper.";
                try {
                    factory.getClass().getClassLoader().loadClass( basePackage +
                                                                   "runtime.JspFactoryImpl$PrivilegedGetPageContext");
                    factory.getClass().getClassLoader().loadClass( basePackage +
                                                                   "runtime.JspFactoryImpl$PrivilegedReleasePageContext");
                    factory.getClass().getClassLoader().loadClass( basePackage +
                                                                   "runtime.JspRuntimeLibrary");
                    factory.getClass().getClassLoader().loadClass( basePackage +
                                                                   "runtime.JspRuntimeLibrary$PrivilegedIntrospectHelper");
                    factory.getClass().getClassLoader().loadClass( basePackage +
                                                                   "runtime.ServletResponseWrapperInclude");
                    factory.getClass().getClassLoader().loadClass( basePackage +
                                                                   "servlet.JspServletWrapper");
                } catch (ClassNotFoundException ex) {
                    org.apache.commons.logging.LogFactory.getLog( HttpJspBase.class )
                        .error("Jasper JspRuntimeContext preload of class failed: " +
                                       ex.getMessage(), ex);
                }
            }
            JspFactory.setDefaultFactory(factory);
        }
    }

    protected HttpJspBase() {
    }

    public final void init(ServletConfig config) throws ServletException {
        super.init(config);
        jspInit();
        _jspInit();
    }
    
    public String getServletInfo() {
    	return Localizer.getMessage("jsp.engine.info");
    }

    public final void destroy() {
		jspDestroy();
		_jspDestroy();
    }

    /**
     * Entry point into service.
     */
    public final void service(HttpServletRequest request, HttpServletResponse response) 
    		throws ServletException, IOException {
        _jspService(request, response);
    }
    
    public void jspInit() {
    }

    public void _jspInit() {
    }

    public void jspDestroy() {
    }

    protected void _jspDestroy() {
    }

    public abstract void _jspService(HttpServletRequest request, 
				     HttpServletResponse response) 
	throws ServletException, IOException;
}
