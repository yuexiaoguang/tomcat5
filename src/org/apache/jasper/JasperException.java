package org.apache.jasper;

/**
 * JSP 引擎所有异常的基类. 
 */
public class JasperException extends javax.servlet.ServletException {
    
    public JasperException(String reason) {
    	super(reason);
    }

    public JasperException (String reason, Throwable exception) {
    	super(reason, exception);
    }

    public JasperException (Throwable exception) {
    	super(exception);
    }
}
