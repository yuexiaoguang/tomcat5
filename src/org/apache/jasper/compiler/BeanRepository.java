package org.apache.jasper.compiler;


import java.util.Vector;
import java.util.Hashtable;

import org.apache.jasper.JasperException;

/**
 * {page, request, session, application}-范围的 bean的存储库
 */
class BeanRepository {

    private Vector sessionBeans;
    private Vector pageBeans;
    private Vector appBeans;
    private Vector requestBeans;
    private Hashtable beanTypes;
    private ClassLoader loader;
    private ErrorDispatcher errDispatcher;

    /*
     * Constructor.
     */    
    public BeanRepository(ClassLoader loader, ErrorDispatcher err) {

        this.loader = loader;
	this.errDispatcher = err;

	sessionBeans = new Vector(11);
	pageBeans = new Vector(11);
	appBeans = new Vector(11);
	requestBeans = new Vector(11);
	beanTypes = new Hashtable();
    }
        
    public void addBean(Node.UseBean n, String s, String type, String scope)
	    throws JasperException {

	if (scope == null || scope.equals("page")) {
	    pageBeans.addElement(s);	
	} else if (scope.equals("request")) {
	    requestBeans.addElement(s);
	} else if (scope.equals("session")) {
	    sessionBeans.addElement(s);
	} else if (scope.equals("application")) {
	    appBeans.addElement(s);
	} else {
	    errDispatcher.jspError(n, "jsp.error.usebean.badScope");
	}
	
	putBeanType(s, type);
    }
            
    public Class getBeanType(String bean) throws JasperException {
	Class clazz = null;
	try {
	    clazz = loader.loadClass ((String)beanTypes.get(bean));
	} catch (ClassNotFoundException ex) {
	    throw new JasperException (ex);
	}
	return clazz;
    }
      
    public boolean checkVariable (String bean) {
	// XXX 不知道这是不是正确的方法.
	// 在pageContext 完成之后，这个将修改.
	return (checkPageBean(bean) || checkSessionBean(bean) ||
		checkRequestBean(bean) || checkApplicationBean(bean));
    }


    private void putBeanType(String bean, String type) {
	beanTypes.put (bean, type);
    }

    private boolean checkPageBean (String s) {
	return pageBeans.contains (s);
    }

    private boolean checkRequestBean (String s) {
	return requestBeans.contains (s);
    }

    private boolean checkSessionBean (String s) {
	return sessionBeans.contains (s);
    }

    private boolean checkApplicationBean (String s) {
	return appBeans.contains (s);
    }
}
