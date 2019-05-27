package org.apache.catalina.mbeans;

import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.RuntimeOperationsException;

import org.apache.commons.modeler.BaseModelMBean;

/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.catalina.core.StandardService</code> component.</p>
 */
public class StandardServiceMBean extends BaseModelMBean {

    /**
     * The <code>MBeanServer</code> for this application.
     */
    private static MBeanServer mserver = MBeanUtils.createServer();
    
    // ----------------------------------------------------------- Constructors


    /**
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception RuntimeOperationsException if an IllegalArgumentException
     *  occurs
     */
    public StandardServiceMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }
}
