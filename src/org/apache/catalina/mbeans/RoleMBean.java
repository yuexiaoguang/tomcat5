package org.apache.catalina.mbeans;

import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.RuntimeOperationsException;

import org.apache.commons.modeler.BaseModelMBean;
import org.apache.commons.modeler.ManagedBean;
import org.apache.commons.modeler.Registry;

/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.catalina.Role</code> component.</p>
 */
public class RoleMBean extends BaseModelMBean {


    // ----------------------------------------------------------- Constructors


    /**
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception RuntimeOperationsException 如果发生IllegalArgumentException
     */
    public RoleMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 管理bean的配置信息注册表.
     */
    protected Registry registry = MBeanUtils.createRegistry();


    /**
     * 注册的<code>MBeanServer</code>.
     */
    protected MBeanServer mserver = MBeanUtils.createServer();


    /**
     * 描述这个MBean的<code>ManagedBean</code>.
     */
    protected ManagedBean managed = registry.findManagedBean("Role");

}
