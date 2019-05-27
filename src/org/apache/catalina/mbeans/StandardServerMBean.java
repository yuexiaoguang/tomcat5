package org.apache.catalina.mbeans;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.RuntimeOperationsException;
import org.apache.catalina.Server;
import org.apache.catalina.ServerFactory;
import org.apache.catalina.core.StandardServer;
import org.apache.commons.modeler.BaseModelMBean;

/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.catalina.core.StandardServer</code> component.</p>
 */
public class StandardServerMBean extends BaseModelMBean {


    // ------------------------------------------------------- Static Variables


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
    public StandardServerMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }

    // ------------------------------------------------------------- Operations

    /**
     * 写入整个<code>Server</code>的配置信息到server.xml配置文件中
     *
     * @exception InstanceNotFoundException 如果找不到托管资源对象
     * @exception MBeanException 如果初始化对象抛出异常，则不支持持久化
     * @exception RuntimeOperationsException 如果持久化机制报告异常
     */
    public synchronized void store() throws InstanceNotFoundException,
        MBeanException, RuntimeOperationsException {

        Server server = ServerFactory.getServer();
        if (server instanceof StandardServer) {
            try {
                ((StandardServer) server).storeConfig();
            } catch (Exception e) {
                throw new MBeanException(e, "Error updating conf/server.xml");
            }
        }
    }
}
