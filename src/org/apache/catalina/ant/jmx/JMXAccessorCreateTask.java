package org.apache.catalina.ant.jmx;

import java.util.ArrayList;
import java.util.List;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.apache.tools.ant.BuildException;

/**
 * 在<em>JMX</em> JSR 160 MBeans 服务器上创建新的MBean. 
 * <ul>
 * <li>创建 Mbeans</li>
 * <li>创建有参数的 Mbeans</li>
 * <li>创建远程地使用不同类加载器的 Mbeans</li>
 * </ul>
 * <p>
 * 示例:
 * <br/>
 * 使用 jmx.server连接创建Mbean
 * <pre>
 *   &lt;jmx:create
 *           ref="jmx.server"
 *           name="Catalina:type=MBeanFactory"
 *           className="org.apache.catalina.mbeans.MBeanFactory"
 *           classLoader="Catalina:type=ServerClassLoader,name=server"&gt;
 *            &lt;Arg value="org.apache.catalina.mbeans.MBeanFactory" /&gt;
 *   &lt;/jmxCreate/&gt;
 * </pre>
 * </p>
 * <p>
 * <b>WARNING</b>不是所有的Tomcat MBeans都可以远程创建和注册通过它们的父级!
 * 请使用 MBeanFactory 生成 valves 和 realms.
 * </p>
 * 这些任务必须Ant 1.6 或更新的接口.
 */
public class JMXAccessorCreateTask extends JMXAccessorTask {
    // ----------------------------------------------------- Instance Variables

    private String className;
    private String classLoader;
    private List args=new ArrayList();

    // ----------------------------------------------------- Instance Info

    /**
     * 实现类描述信息
     */
    private static final String info = "org.apache.catalina.ant.JMXAccessorCreateTask/1.0";

    /**
     * 返回描述信息和相应版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }

    // ------------------------------------------------------------- Properties

    public String getClassLoader() {
        return classLoader;
    }
    
    public void setClassLoader(String classLoaderName) {
        this.classLoader = classLoaderName;
    }
    
    public String getClassName() {
        return className;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    
    public void addArg(Arg arg ) {
        args.add(arg);
    }

    public List getArgs() {
        return args;
    }
    public void setArgs(List args) {
        this.args = args;
    }

    // ------------------------------------------------------ protected Methods
    
    /**
     * 根据所配置的属性执行指定的命令.
     * 完成任务后，输入流将关闭, 不管它是否成功执行.
     * 
     * @exception Exception if an error occurs
     */
    public String jmxExecute(MBeanServerConnection jmxServerConnection)
        throws Exception {

        if (getName() == null) {
            throw new BuildException("Must specify a 'name'");
        }
        if ((className == null)) {
            throw new BuildException(
                    "Must specify a 'className' for get");
        }
        return jmxCreate(jmxServerConnection, getName());
     }
    
    /**
     * 创建新的 Mbean ,当从 ClassLoader Objectname设置的时候
     * @param jmxServerConnection
     * @param name
     * @return The value of the given named attribute
     * @throws Exception
     */
    protected String jmxCreate(MBeanServerConnection jmxServerConnection,
            String name) throws Exception {
        String error = null;
        Object argsA[] = null;
        String sigA[] = null;
        if (args != null) {
           argsA = new Object[ args.size()];
           sigA = new String[args.size()];
           for( int i=0; i<args.size(); i++ ) {
               Arg arg=(Arg)args.get(i);
               if( arg.type==null) {
                   arg.type="java.lang.String";
                   sigA[i]=arg.getType();
                   argsA[i]=arg.getValue();
               } else {
                   sigA[i]=arg.getType();
                   argsA[i]=convertStringToType(arg.getValue(),arg.getType());
               }                
           }
        }
        if (classLoader != null && !"".equals(classLoader)) {
            if (isEcho()) {
                handleOutput("create MBean " + name + " from class "
                        + className + " with classLoader " + classLoader);
            }
            if(args == null)
                jmxServerConnection.createMBean(className, new ObjectName(name), new ObjectName(classLoader));
            else
                jmxServerConnection.createMBean(className, new ObjectName(name), new ObjectName(classLoader),argsA,sigA);
                
        } else {
            if (isEcho()) {
                handleOutput("create MBean " + name + " from class "
                        + className);
            }
            if(args == null)
                jmxServerConnection.createMBean(className, new ObjectName(name));
            else
                jmxServerConnection.createMBean(className, new ObjectName(name),argsA,sigA);
        }
        return error;
    }
}
