package org.apache.catalina.ant.jmx;


import java.util.Iterator;
import java.util.Set;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.BuildException;


/**
 * 查询 Mbeans. 
 * <ul>
 * <li>打开不存在的JSR 160 rmi jmx 连接</li>
 * <li>获取所有 Mbeans 属性</li>
 * <li>只获取 Query Mbeans ObjectNames</li>
 * <li>在Ant控制台显示查询结果</li>
 * <li>绑定查询结果到Ant 属性</li>
 * </ul>
 * <br/>
 * 查询一组Mbeans.
 * <pre>
 *   &lt;jmxQuery
 *           host="127.0.0.1"
 *           port="9014"
 *           name="Catalina:type=Manager,* 
 *           resultproperty="manager" /&gt;
 * </pre>
 * 设置<em>attributebinding="true"</em> 您也可以从结果对象获得所有属性.
 * <br>
 * These tasks require Ant 1.6 or later interface.
 */
public class JMXAccessorQueryTask extends JMXAccessorTask {

    // ----------------------------------------------------- Instance Variables

    private boolean attributebinding = false;

    // ----------------------------------------------------- Instance Info

    /**
     * 实现类描述信息
     */
    private static final String info = "org.apache.catalina.ant.JMXAccessorQueryTask/1.0";

    /**
     * 返回描述信息和相应版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }

    // ------------------------------------------------------------- Properties
    
    public boolean isAttributebinding() {
        return attributebinding;
    }
    public void setAttributebinding(boolean attributeBinding) {
        this.attributebinding = attributeBinding;
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
        return jmxQuery(jmxServerConnection, getName());
    }

       
    /**
     * 使用相同的域名和属性调用Mbean 服务器.
     * 设置<em>attributebindung=true</em> 您可以将所有已找到对象的属性保存为Ant属性
     * 
     * @param jmxServerConnection
     * @param qry
     * @return The query result
     */
    protected String jmxQuery(MBeanServerConnection jmxServerConnection,
            String qry) {
        String isError = null;
        Set names = null;
        String resultproperty = getResultproperty();
        try {
            names = jmxServerConnection.queryNames(new ObjectName(qry), null);
            if (resultproperty != null) {
                setProperty(resultproperty + ".Length",Integer.toString(names.size()));
            }
        } catch (Exception e) {
            if (isEcho())
                handleErrorOutput(e.getMessage());
            return "Can't query mbeans " + qry;
        }

        if (resultproperty != null) {
            Iterator it = names.iterator();
            int oindex = 0;
            String pname = null;
            while (it.hasNext()) {
                ObjectName oname = (ObjectName) it.next();
                pname = resultproperty + "." + Integer.toString(oindex) + ".";
                oindex++;
                    setProperty(pname + "Name", oname.toString());
                    if (isAttributebinding()) {
                        bindAttributes(jmxServerConnection, resultproperty, pname, oname);
                
                    }
                }
        }
        return isError;
    }

    /**
     * @param jmxServerConnection
     * @param resultproperty
     * @param pname
     * @param oname
     */
    protected void bindAttributes(MBeanServerConnection jmxServerConnection, String resultproperty, String pname, ObjectName oname) {
        if (jmxServerConnection != null  && resultproperty != null 
            && pname != null && oname != null ) {
            try {
                MBeanInfo minfo = jmxServerConnection.getMBeanInfo(oname);
                String code = minfo.getClassName();
                if ("org.apache.commons.modeler.BaseModelMBean".equals(code)) {
                    code = (String) jmxServerConnection.getAttribute(oname,
                            "modelerType");
                }
                MBeanAttributeInfo attrs[] = minfo.getAttributes();
                Object value = null;

                for (int i = 0; i < attrs.length; i++) {
                    if (!attrs[i].isReadable())
                        continue;
                    String attName = attrs[i].getName();
                    if (attName.indexOf("=") >= 0 || attName.indexOf(":") >= 0
                            || attName.indexOf(" ") >= 0) {
                        continue;
                    }

                    try {
                        value = jmxServerConnection
                                .getAttribute(oname, attName);
                    } catch (Throwable t) {
                        if (isEcho())
                            handleErrorOutput("Error getting attribute "
                                    + oname + " " + pname + attName + " "
                                    + t.toString());
                        continue;
                    }
                    if (value == null)
                        continue;
                    if ("modelerType".equals(attName))
                        continue;
                    createProperty(pname + attName, value);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
