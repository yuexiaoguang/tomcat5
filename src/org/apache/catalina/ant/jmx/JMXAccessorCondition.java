package org.apache.catalina.ant.jmx;

import java.io.IOException;
import java.net.MalformedURLException;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.ProjectComponent;
import org.apache.tools.ant.taskdefs.condition.Condition;

/**
 * <b>定义</b>:
 * <pre> 
 *   &lt;path id="catalina_ant">
 *       &lt;fileset dir="${catalina.home}/server/lib">
 *           &lt;include name="catalina-ant.jar"/>
 *           &lt;include name="catalina-ant-jmx.jar"/>
 *       &lt;/fileset>
 *   &lt;/path>
 *
 *   &lt;typedef
 *       name="jmxCondition"
 *       classname="org.apache.catalina.ant.jmx.JMXAccessorCondition"
 *       classpathref="catalina_ant"/>
 *   &lt;taskdef
 *       name="jmxOpen"
 *       classname="org.apache.catalina.ant.jmx.JMXAccessorTask"
 *       classpathref="catalina_ant"/>
 * </pre>
 * 
 * <b>使用</b>: 等待开始备份节点
 * <pre>
 *     &lt;target name="wait"&gt;
 *       &lt;jmxOpen
 *               host="${jmx.host}" port="${jmx.port}" username="${jmx.username}" password="${jmx.password}" /&gt;
 *        &lt;waitfor maxwait="${maxwait}" maxwaitunit="second" timeoutproperty="server.timeout" &gt;
 *           &lt;and&gt;
 *               &lt;socket server="${server.name}" port="${server.port}"/&gt;
 *               &lt;http url="${url}"/&gt;
 *               &lt;jmxCondition
 *                   name="Catalina:type=IDataSender,host=localhost,senderAddress=192.168.111.1,senderPort=9025"
 *                   operation="==" 
 *                   attribute="connected" value="true"
 *               /&gt;
 *               &lt;jmxCondition
 *                   operation="&amp;lt;"
 *                   name="Catalina:j2eeType=WebModule,name=//${tomcat.application.host}${tomcat.application.path},J2EEApplication=none,J2EEServer=none"
 *                   attribute="startupTime" value="250"
 *               /&gt;
 *           &lt;/and&gt;
 *       &lt;/waitfor&gt;
 *       &lt;fail if="server.timeout" message="Server ${url} don't answer inside ${maxwait} sec" /&gt;
 *       &lt;echo message="Server ${url} alive" /&gt;
 *   &lt;/target&gt;
 *
 * </pre>
 * 允许运行的JMX属性和参考值:
 * <ul>
 * <li>==  equals</li>
 * <li>!=  not equals</li>
 * <li>&gt; greater than (&amp;gt;)</li>
 * <li>&gt;= greater than or equals (&amp;gt;=)</li>
 * <li>&lt; lesser than (&amp;lt;)</li>
 * <li>&lt;= lesser than or equals (&amp;lt;=)</li>
 * </ul> 
 * <b>NOTE</b>: 对于数值表达式，必须设置类型，并使用XML实体作为操作.<br/>
 * 目前支持的类型<em>long</em> 和 <em>double</em>.
 */
public class JMXAccessorCondition extends ProjectComponent implements Condition {

    // ----------------------------------------------------- Instance Variables

    private String url = null;
    private String host = "localhost";
    private String port = "8050";
    private String password = null;
    private String username = null;
    private String name = null;
    private String attribute;
    private String value;
    private String operation = "==" ;
    private String type = "long" ;
    private String ref = "jmx.server";
    private String unlessCondition;
    private String ifCondition;
     
    // ----------------------------------------------------- Instance Info

    /**
     * 此实现的描述信息
     */
    private static final String info = "org.apache.catalina.ant.JMXAccessorCondition/1.1";

    /**
     * 返回描述信息和相应版本号, 格式为
     * <code>&lt;描述&gt;/&lt;版本号&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }
    // ----------------------------------------------------- Properties

    public String getOperation() {
        return operation;
    }
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
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
    public String getIf() {
        return ifCondition;
    }
    /**
     * 只有在当前项目中存在给定名称的属性时才执行.
     * 
     * @param c 属性名
     */
    public void setIf(String c) {
        ifCondition = c;
    }
    public String getUnless() {
        return unlessCondition;
    }
 
    /**
     * 只有在当前项目中不存在给定名称的属性时才执行
     * 
     * @param c 属性名
     */
    public void setUnless(String c) {
        unlessCondition = c;
    }

    /**
     * 获取JMXConnection (默认查看从jmxOpen任务引用的<em>jmx.server</em>项目)
     * @return active JMXConnection
     * @throws MalformedURLException
     * @throws IOException
     */
    protected MBeanServerConnection getJMXConnection()
            throws MalformedURLException, IOException {
        return JMXAccessorTask.accessJMXConnection(
                getProject(),
                getUrl(), getHost(),
                getPort(), getUsername(), getPassword(), ref);
    }

    /**
     * 获取MBeans 属性的值 
     * @return The value
     */
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

    /**
     * 测试if条件
     * @return true 如果没有if条件, 或命名属性存在
     */
    protected boolean testIfCondition() {
        if (ifCondition == null || "".equals(ifCondition)) {
            return true;
        }
        return getProject().getProperty(ifCondition) != null;
    }

    /**
     * 测试除非条件
     * @return true 如果没有除非条件, 或者有一个命名的属性，但是它不存在
     */
    protected boolean testUnlessCondition() {
        if (unlessCondition == null || "".equals(unlessCondition)) {
            return true;
        }
        return getProject().getProperty(unlessCondition) == null;
    }

    /**
     * 此方法评估条件
     * 如果支持条件 ">,>=,<,<=" 以及<code>long</code> 和 <code>double</code>.
     * @return expression <em>jmxValue</em> <em>operation</em> <em>value</em>
     */
    public boolean eval() {
        if (operation == null) {
            throw new BuildException("operation attribute is not set");
        }
        if (value == null) {
            throw new BuildException("value attribute is not set");
        }
        if ((name == null || attribute == null)) {
            throw new BuildException(
                    "Must specify a 'attribute', name for equals condition");
        }
        if (testIfCondition() && testUnlessCondition()) {
            String jmxValue = accessJMXValue();
            if (jmxValue != null) {
                String op = getOperation();
                if ("==".equals(op)) {
                    return jmxValue.equals(value);
                } else if ("!=".equals(op)) {
                    return !jmxValue.equals(value);
                } else {
                    if ("long".equals(type)) {
                        long jvalue = Long.parseLong(jmxValue);
                        long lvalue = Long.parseLong(value);
                        if (">".equals(op)) {
                            return jvalue > lvalue;
                        } else if (">=".equals(op)) {
                            return jvalue >= lvalue;
                        } else if ("<".equals(op)) {
                            return jvalue < lvalue;
                        } else if ("<=".equals(op)) {
                            return jvalue <= lvalue;
                        }
                    } else if ("double".equals(type)) {
                        double jvalue = Double.parseDouble(jmxValue);
                        double dvalue = Double.parseDouble(value);
                        if (">".equals(op)) {
                            return jvalue > dvalue;
                        } else if (">=".equals(op)) {
                            return jvalue >= dvalue;
                        } else if ("<".equals(op)) {
                            return jvalue < dvalue;
                        } else if ("<=".equals(op)) {
                            return jvalue <= dvalue;
                        }
                    }
                }
            }
            return false;
        }
        return true;
    }
 }

