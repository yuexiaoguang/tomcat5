package org.apache.catalina.mbeans;


import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.RuntimeOperationsException;

import org.apache.commons.modeler.BaseModelMBean;


/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.catalina.core.StandardEngine</code> component.</p>
 */
public class StandardEngineMBean extends BaseModelMBean {

    /**
     * 这个应用的<code>MBeanServer</code>.
     */
    private static MBeanServer mserver = MBeanUtils.createServer();
    
    // ----------------------------------------------------------- Constructors


    /**
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception RuntimeOperationsException if an IllegalArgumentException
     *  occurs
     */
    public StandardEngineMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }

}
