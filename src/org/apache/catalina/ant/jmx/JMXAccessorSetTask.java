package org.apache.catalina.ant.jmx;


import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.BuildException;


/**
 * 访问<em>JMX</em> JSR 160 MBeans Server. 
 * <ul>
 * <li>获取Mbeans 属性</li>
 * <li>在Ant控制台显示 Get 结果</li>
 * <li>绑定 Get 结果到 Ant 属性</li>
 * </ul>
 * <p>
 * 示例:
 * 设置Mbean Manager 属性 maxActiveSessions.
 * 设置这个属性刷新jmx 连接, 不保存引用
 * <pre>
 *   &lt;jmx:set
 *           host="127.0.0.1"
 *           port="9014"
 *           ref=""
 *           name="Catalina:type=Manager,path="/ClusterTest",host=localhost" 
 *           attribute="maxActiveSessions"
 *           value="100"
 *           type="int"
 *           echo="false"&gt;
 *       /&gt;
 * </pre>
 * </p>
 * These tasks require Ant 1.6 or later interface.
 */
public class JMXAccessorSetTask extends JMXAccessorTask {

    // ----------------------------------------------------- Instance Variables

    private String attribute;
    private String value;
    private String type;
    private boolean convert = false ;
    
    // ----------------------------------------------------- Instance Info

    /**
     * 实现类的描述信息.
     */
    private static final String info = "org.apache.catalina.ant.JMXAccessorSetTask/1.0";

    /**
     * 返回描述信息和相应版本号, 格式为
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
    
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }
    public String getType() {
        return type;
    }
    public void setType(String valueType) {
        this.type = valueType;
    }
    public boolean isConvert() {
        return convert;
    }
    public void setConvert(boolean convert) {
        this.convert = convert;
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
        if ((attribute == null || value == null)) {
            throw new BuildException(
                    "Must specify a 'attribute' and 'value' for set");
        }
        return  jmxSet(jmxServerConnection, getName());
     }

    /**
     * @param jmxServerConnection
     * @param name
     * @throws Exception
     */
    protected String jmxSet(MBeanServerConnection jmxServerConnection,
            String name) throws Exception {
        Object realValue;
        if (type != null) {
            realValue = convertStringToType(value, type);
        } else {
            if (isConvert()) {
                String mType = getMBeanAttributeType(jmxServerConnection, name,
                        attribute);
                realValue = convertStringToType(value, mType);
            } else
                realValue = value;
        }
        jmxServerConnection.setAttribute(new ObjectName(name), new Attribute(
                attribute, realValue));
        return null;
    }
    


    /**
     * Get MBean Attriute from Mbean Server
     * @param jmxServerConnection
     * @param name
     * @param attribute
     * @return The type
     * @throws Exception
     */
    protected String getMBeanAttributeType(
            MBeanServerConnection jmxServerConnection,
            String name,
            String attribute) throws Exception {
        ObjectName oname = new ObjectName(name);
        String mattrType = null;
        MBeanInfo minfo = jmxServerConnection.getMBeanInfo(oname);
        MBeanAttributeInfo attrs[] = minfo.getAttributes();
        if (attrs != null) {
            for (int i = 0; mattrType == null && i < attrs.length; i++) {
                if (attribute.equals(attrs[i].getName()))
                    mattrType = attrs[i].getType();
            }
        }
        return mattrType;
    }
 }
