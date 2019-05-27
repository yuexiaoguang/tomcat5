package org.apache.catalina.mbeans;

import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.RuntimeOperationsException;

import org.apache.catalina.Valve;
import org.apache.catalina.core.StandardHost;
import org.apache.commons.modeler.BaseModelMBean;
import org.apache.commons.modeler.ManagedBean;
import org.apache.commons.modeler.Registry;

/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.catalina.core.StandardHost</code> component.</p>
 */
public class StandardHostMBean extends BaseModelMBean {

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
    public StandardHostMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }

    // ------------------------------------------------------------- Operations

   /**
     * 添加映射到该主机的别名
     *
     * @param alias 要添加的别名
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    public void addAlias(String alias)
        throws Exception {
        StandardHost host = (StandardHost) this.resource;
        host.addAlias(alias);
    }


   /**
     * 返回此主机的别名集合
     *
     * @exception Exception 如果一个MBean不能创建或注册
     */
    public String [] findAliases()
        throws Exception {
        StandardHost host = (StandardHost) this.resource;
        return host.findAliases();
    }


   /**
     * 返回这个Host关联的Valves的MBean名称
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    public String [] getValves()
        throws Exception {

        Registry registry = MBeanUtils.createRegistry();
        StandardHost host = (StandardHost) this.resource;
        String mname = MBeanUtils.createManagedName(host);
        ManagedBean managed = registry.findManagedBean(mname);
        String domain = null;
        if (managed != null) {
            domain = managed.getDomain();
        }
        if (domain == null)
            domain = mserver.getDefaultDomain();
        Valve [] valves = host.getValves();
        String [] mbeanNames = new String[valves.length];
        for (int i = 0; i < valves.length; i++) {
            mbeanNames[i] =
                MBeanUtils.createObjectName(domain, valves[i]).toString();
        }
        return mbeanNames;
    }


   /**
     * 移除指定的别名
     *
     * @param alias 要删除的别名
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    public void removeAlias(String alias)
        throws Exception {
        StandardHost host = (StandardHost) this.resource;
        host.removeAlias(alias);
    }
}
