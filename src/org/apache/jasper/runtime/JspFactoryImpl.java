package org.apache.jasper.runtime;

import java.security.AccessController;
import java.security.PrivilegedAction;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.jsp.JspFactory;
import javax.servlet.jsp.JspApplicationContext;
import javax.servlet.jsp.JspEngineInfo;
import javax.servlet.jsp.PageContext;

import org.apache.jasper.util.SimplePool;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * JspFactory实现类.
 */
public class JspFactoryImpl extends JspFactory {

    // Logger
    private Log log = LogFactory.getLog(JspFactoryImpl.class);

    private static final String SPEC_VERSION = "2.0";
    private static final boolean USE_POOL = true;

    private SimplePool pool = new SimplePool( 100 );
    
    public PageContext getPageContext(Servlet servlet,
				      ServletRequest request,
                                      ServletResponse response,
                                      String errorPageURL,                    
                                      boolean needsSession,
				      int bufferSize,
                                      boolean autoflush) {

		if( System.getSecurityManager() != null ) {
		    PrivilegedGetPageContext dp = new PrivilegedGetPageContext(
			(JspFactoryImpl)this, servlet, request, response, errorPageURL,
	                needsSession, bufferSize, autoflush);
		    return (PageContext)AccessController.doPrivileged(dp);
		} else {
		    return internalGetPageContext(servlet, request, response,
						  errorPageURL, needsSession,
						  bufferSize, autoflush);
		}
    }

    public void releasePageContext(PageContext pc) {
		if( pc == null )
		    return;
        if( System.getSecurityManager() != null ) {
            PrivilegedReleasePageContext dp = new PrivilegedReleasePageContext(
                (JspFactoryImpl)this,pc);
            AccessController.doPrivileged(dp);
        } else {
            internalReleasePageContext(pc);
        }
    }

    public JspEngineInfo getEngineInfo() {
        return new JspEngineInfo() {
			public String getSpecificationVersion() {
			    return SPEC_VERSION;
			}
	    };
    }

    private PageContext internalGetPageContext(Servlet servlet,
					       ServletRequest request,
					       ServletResponse response, 
					       String errorPageURL, 
					       boolean needsSession,
					       int bufferSize, 
					       boolean autoflush) {
        try {
		    PageContext pc;
		    if( USE_POOL ) {
                pc = (PageContext) pool.get();
				if( pc == null ) {
				    pc= new PageContextImpl(this);
				}
		    } else {
		    	pc = new PageContextImpl(this);
		    }
		    pc.initialize(servlet, request, response, errorPageURL, 
	                          needsSession, bufferSize, autoflush);
            return pc;
        } catch (Throwable ex) {
            /* FIXME: need to do something reasonable here!! */
            log.fatal("Exception initializing page context", ex);
            return null;
        }
    }

    private void internalReleasePageContext(PageContext pc) {
        pc.release();
		if (USE_POOL && (pc instanceof PageContextImpl)) {
		    pool.put( pc );
		}
    }

    private class PrivilegedGetPageContext implements PrivilegedAction {

		private JspFactoryImpl factory;
		private Servlet servlet;
		private ServletRequest request;
		private ServletResponse response;
		private String errorPageURL;
		private boolean needsSession;
		private int bufferSize;
		private boolean autoflush;
	
		PrivilegedGetPageContext(JspFactoryImpl factory,
					 Servlet servlet,
					 ServletRequest request,
					 ServletResponse response,
					 String errorPageURL,
					 boolean needsSession,
					 int bufferSize,
					 boolean autoflush) {
		    this.factory = factory;
		    this.servlet = servlet;
		    this.request = request;
		    this.response = response;
		    this.errorPageURL = errorPageURL;
		    this.needsSession = needsSession;
		    this.bufferSize = bufferSize;
		    this.autoflush = autoflush;
		}
	 
		public Object run() {
		    return factory.internalGetPageContext(servlet,
							  request,
							  response,
							  errorPageURL,
							  needsSession,
							  bufferSize,
							  autoflush);
		}
    }

    private class PrivilegedReleasePageContext implements PrivilegedAction {

        private JspFactoryImpl factory;
        private PageContext pageContext;

        PrivilegedReleasePageContext(JspFactoryImpl factory,
				     PageContext pageContext) {
            this.factory = factory;
            this.pageContext = pageContext;
        }

        public Object run() {
            factory.internalReleasePageContext(pageContext);
            return null;
        }
    }
/***************自己加的*************/
	@Override
	public JspApplicationContext getJspApplicationContext(ServletContext arg0) {
		// TODO Auto-generated method stub
		return null;
	}
/***************自己加的*************/
}
