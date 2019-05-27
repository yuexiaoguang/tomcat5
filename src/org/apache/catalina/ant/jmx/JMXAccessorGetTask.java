package org.apache.catalina.ant.jmx;


import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.BuildException;


/**
 * 访问<em>JMX</em> JSR 160 MBeans Server. 
 * <ul>
 * <li>获取 Mbeans 属性</li>
 * <li>在Ant控制台显示 Get 结果</li>
 * <li>绑定 Get 结果到 Ant 属性</li>
 * </ul>
 * <p>
 * 示例:
 * <br/>
 * 获取 Mbean IDataSender 属性 nrOfRequests 并创建新的Ant属性<em>IDataSender.9025.nrOfRequests</em> 
 * <pre>
 *   &lt;jmx:get
 *           ref="jmx.server"
 *           name="Catalina:type=IDataSender,host=localhost,senderAddress=192.168.1.2,senderPort=9025" 
 *           attribute="nrOfRequests"
 *           resultproperty="IDataSender.9025.nrOfRequests"
 *           echo="false"&gt;
 *       /&gt;
 * </pre>
 * </p>
 * These tasks require Ant 1.6 or later interface.
 */
public class JMXAccessorGetTask extends JMXAccessorTask {

    // ----------------------------------------------------- Instance Variables

    private String attribute;

    // ----------------------------------------------------- Instance Info

    /**
     * 实现类的描述信息
     */
    private static final String info = "org.apache.catalina.ant.JMXAccessorGetTask/1.0";

    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }

    // ------------------------------------------------------------- Properties
    
    public String getAttribute() {
        return attribute;
    }
    
    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }
    
  
    // ------------------------------------------------------ protected Methods
    
    /**
     * 根据所配置的属性执行指定的命令.
     * 完成任务后，输入流将关闭, 不论他是否成功执行.
     * 
     * @exception BuildException if an error occurs
     */
    public String jmxExecute(MBeanServerConnection jmxServerConnection)
        throws Exception {

        if (getName() == null) {
            throw new BuildException("Must specify a 'name'");
        }
        if ((attribute == null)) {
            throw new BuildException(
                    "Must specify a 'attribute' for get");
        }
        return  jmxGet(jmxServerConnection, getName());
     }


    /**
     * @param jmxServerConnection
     * @param name
     * @return 给定命名属性的值
     * @throws Exception
     */
    protected String jmxGet(MBeanServerConnection jmxServerConnection,String name) throws Exception {
        String error = null;
        if(isEcho()) {
            handleOutput("MBean " + name + " get attribute " + attribute );
        }
        Object result = jmxServerConnection.getAttribute(
                new ObjectName(name), attribute);
        if (result != null) {
            echoResult(attribute,result);
            createProperty(result);
        } else
            error = "Attribute " + attribute + " is empty";
        return error;
    }
}
