package org.apache.catalina.ant.jmx;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.BuildException;

/**
 * 注销一个 MBean 在<em>JMX</em> JSR 160 MBeans Server. 
 * <ul>
 * <li>注销 Mbeans</li>
 * </ul>
 * <p>
 * 示例:
 * <br>
 * 注销一个现有的 Mbean 在 jmx.server 连接 
 * <pre>
 *   &lt;jmx:unregister
 *           ref="jmx.server"
 *           name="Catalina:type=MBeanFactory" /&gt;
 * </pre>
 * </p>
 * <p>
 * <b>WARNING</b>不是所有的Tomcat MBeans 可以成功的远程注销. mbean的注销不会从父类删除 valves, realm, ...
 * 使用 MBeanFactory 的操作删除 valves 和 realms.
 * </p>
 * These tasks require Ant 1.6 or later interface.
 */
public class JMXAccessorUnregisterTask extends JMXAccessorTask {

    // ----------------------------------------------------- Instance Info

    /**
     * 实现类描述信息
     */
    private static final String info = "org.apache.catalina.ant.JMXAccessorUnregisterTask/1.0";

    /**
     * 返回描述信息和相应版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }
    // ------------------------------------------------------ protected Methods
    
    /**
     * 根据所配置的属性执行指定的命令.
     * 完成任务后，输入流将被关闭，无论它是否成功执行.
     * 
     * @exception Exception if an error occurs
     */
    public String jmxExecute(MBeanServerConnection jmxServerConnection)
        throws Exception {

        if (getName() == null) {
            throw new BuildException("Must specify a 'name'");
        }
        return  jmxUuregister(jmxServerConnection, getName());
     }


    /**
     * 注销 Mbean
     * @param jmxServerConnection
     * @param name
     * @return 给定属性的值
     * @throws Exception
     */
    protected String jmxUuregister(MBeanServerConnection jmxServerConnection,String name) throws Exception {
        String error = null;
        if(isEcho()) {
            handleOutput("Unregister MBean " + name  );
        }
        jmxServerConnection.unregisterMBean(
                new ObjectName(name));
        return error;
    }

}
