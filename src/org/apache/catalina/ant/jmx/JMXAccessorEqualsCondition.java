package org.apache.catalina.ant.jmx;

import java.io.IOException;
import java.net.MalformedURLException;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.ProjectComponent;
import org.apache.tools.ant.taskdefs.condition.Condition;

/**
 * 定义
 * <pre> 
 *   &lt;path id="catalina_ant">
 *       &lt;fileset dir="${catalina.home}/server/lib">
 *           &lt;include name="catalina-ant.jar"/>
 *           &lt;include name="catalina-ant-jmx.jar"/>
 *       &lt;/fileset>
 *   &lt;/path>
 *
 *   &lt;typedef
 *       name="jmxEquals"
 *       classname="org.apache.catalina.ant.jmx.JMXAccessorEqualsCondition"
 *       classpathref="catalina_ant"/>
 * </pre>
 * 
 * 用法: 等待开始备份节点
 * <pre>
 *     &lt;target name="wait"&gt;
 *        &lt;waitfor maxwait="${maxwait}" maxwaitunit="second" timeoutproperty="server.timeout" &gt;
 *           &lt;and&gt;
 *               &lt;socket server="${server.name}" port="${server.port}"/&gt;
 *               &lt;http url="${url}"/&gt;
 *               &lt;jmxEquals 
 *                   host="localhost" port="9014" username="controlRole" password="tomcat"
 *                   name="Catalina:type=IDataSender,host=localhost,senderAddress=192.168.111.1,senderPort=9025"
 *                   attribute="connected" value="true"
 *               /&gt;
 *           &lt;/and&gt;
 *       &lt;/waitfor&gt;
 *       &lt;fail if="server.timeout" message="Server ${url} don't answer inside ${maxwait} sec" /&gt;
 *       &lt;echo message="Server ${url} alive" /&gt;
 *   &lt;/target&gt;
 *
 * </pre>
 */
public class JMXAccessorEqualsCondition  extends ProjectComponent  implements Condition {

    // ----------------------------------------------------- Instance Variables

    private String url = null;
    private String host = "localhost";
    private String port = "8050";
    private String password = null;
    private String username = null;
    private String name = null;
    private String attribute;
    private String value;
    private String ref = "jmx.server" ;
    // ----------------------------------------------------- Instance Info

    /**
     * 实现类描述信息
     */
    private static final String info = "org.apache.catalina.ant.JMXAccessorEqualsCondition/1.1";

    /**
     * 返回描述信息和相应版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }
    // ----------------------------------------------------- Properties

    public String getAttribute() {
        return attribute;
    }
    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }
    public String getHost() {
        return host;
    }
    public void setHost(String host) {
        this.host = host;
    }
    public String getName() {
        return name;
    }
    public void setName(String objectName) {
        this.name = objectName;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public String getPort() {
        return port;
    }
    public void setPort(String port) {
        this.port = port;
    }
    public String getUrl() {
        return url;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }

    public String getRef() {
        return ref;
    }
    public void setRef(String refId) {
        this.ref = refId;
    }
    
    protected MBeanServerConnection getJMXConnection()
            throws MalformedURLException, IOException {
        return JMXAccessorTask.accessJMXConnection(
                getProject(),
                getUrl(), getHost(),
                getPort(), getUsername(), getPassword(), ref);
    }

    protected String accessJMXValue() {
        try {
            Object result = getJMXConnection().getAttribute(
                    new ObjectName(name), attribute);
            if(result != null)
                return result.toString();
        } catch (Exception e) {
            // 忽略访问或连接打开错误
        }
        return null;
    }

    // 此方法评估条件
    public boolean eval() {
        if (value == null) {
            throw new BuildException("value attribute is not set");
        }
        if ((name == null || attribute == null)) {
            throw new BuildException(
                    "Must specify a 'attribute', name for equals condition");
        }
        //FIXME check url or host/parameter
        String jmxValue = accessJMXValue();
        if(jmxValue != null)
            return jmxValue.equals(value);
        return false;
    }
}

