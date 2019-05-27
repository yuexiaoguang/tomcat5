package org.apache.catalina.valves;


import java.io.IOException;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.ServletException;

import org.apache.catalina.Contained;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Valve;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * <b>Valve</b>接口实现类的基类.
 * 子类必须实现<code>invoke()</code>方法提供所需功能, 也可以实现<code>Lifecycle</code>接口提供配置管理和生命周期支持
 */
public abstract class ValveBase implements Contained, Valve, MBeanRegistration {
    private static Log log = LogFactory.getLog(ValveBase.class);

    //------------------------------------------------------ Instance Variables


    /**
     * The Container whose pipeline this Valve is a component of.
     */
    protected Container container = null;


    /**
     * Container log
     */
    protected Log containerLog = null;


    /**
     * 实现类描述信息. 子类应该重写这个值
     */
    protected static String info = "org.apache.catalina.core.ValveBase/1.0";


    /**
     * 管道中下一个Valve.
     */
    protected Valve next = null;


    /**
     * The string manager for this package.
     */
    protected final static StringManager sm = StringManager.getManager(Constants.Package);


    //-------------------------------------------------------------- Properties


    /**
     * 返回关联的Container.
     */
    public Container getContainer() {
        return (container);
    }


    /**
     * 设置关联的Container.
     *
     * @param container The new associated container
     */
    public void setContainer(Container container) {
        this.container = container;
    }


    /**
     * 返回实现类描述信息.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 返回这个管道中下一个 Valve, 如果这个Valve已经是最后的一个返回<code>null</code>.
     */
    public Valve getNext() {
        return (next);
    }


    /**
     * 设置管道中下一个Valve.
     *
     * @param valve The new next valve
     */
    public void setNext(Valve valve) {
        this.next = valve;
    }


    //---------------------------------------------------------- Public Methods


    /**
     * 执行周期任务, 例如重新加载, etc. 该方法将在该容器的类加载上下文中被调用.
     * 异常将被捕获和记录.
     */
    public void backgroundProcess() {
    }


    /**
     * 这个 Valve实现类的特定逻辑. 查看 Valve 描述了解这个方法的设计模式.
     * <p>
     * 子类必须实现这个方法.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    public abstract void invoke(Request request, Response response)
        throws IOException, ServletException;


    public String toString() {
        StringBuffer sb = new StringBuffer(this.getClass().getName());
        sb.append("[");
        if (container != null)
            sb.append(container.getName());
        sb.append("]");
        return (sb.toString());
    }


    // -------------------- JMX and Registration  --------------------
    protected String domain;
    protected ObjectName oname;
    protected MBeanServer mserver;
    protected ObjectName controller;

    public ObjectName getObjectName() {
        return oname;
    }

    public void setObjectName(ObjectName oname) {
        this.oname = oname;
    }

    public String getDomain() {
        return domain;
    }

    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        oname=name;
        mserver=server;
        domain=name.getDomain();
        return name;
    }

    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() throws Exception {
    }

    public void postDeregister() {
    }

    public ObjectName getController() {
        return controller;
    }

    public void setController(ObjectName controller) {
        this.controller = controller;
    }

    /** 从名称中提取父对象名称
     *
     * @param valveName The valve name
     * @return ObjectName The parent name
     */
    public ObjectName getParentName( ObjectName valveName ) {
        return null;
    }

    public ObjectName createObjectName(String domain, ObjectName parent)
            throws MalformedObjectNameException
    {
        Container container=this.getContainer();
        if( container == null || ! (container instanceof ContainerBase) )
            return null;
        this.containerLog = container.getLogger();
        ContainerBase containerBase=(ContainerBase)container;
        Pipeline pipe=containerBase.getPipeline();
        Valve valves[]=pipe.getValves();

        /* Compute the "parent name" part */
        String parentName="";
        if (container instanceof Engine) {
        } else if (container instanceof Host) {
            parentName=",host=" +container.getName();
        } else if (container instanceof Context) {
                    String path = ((Context)container).getPath();
                    if (path.length() < 1) {
                        path = "/";
                    }
                    Host host = (Host) container.getParent();
                    parentName=",path=" + path + ",host=" +
                            host.getName();
        } else if (container instanceof Wrapper) {
            Context ctx = (Context) container.getParent();
            String path = ctx.getPath();
            if (path.length() < 1) {
                path = "/";
            }
            Host host = (Host) ctx.getParent();
            parentName=",servlet=" + container.getName() +
                    ",path=" + path + ",host=" + host.getName();
        }
        log.debug("valve parent=" + parentName + " " + parent);

        String className=this.getClass().getName();
        int period = className.lastIndexOf('.');
        if (period >= 0)
            className = className.substring(period + 1);

        int seq=0;
        for( int i=0; i<valves.length; i++ ) {
            // Find other valves with the same name
            if (valves[i] == this) {
                break;
            }
            if( valves[i]!=null &&
                    valves[i].getClass() == this.getClass() ) {
                log.debug("Duplicate " + valves[i] + " " + this + " " + container);
                seq++;
            }
        }
        String ext="";
        if( seq > 0 ) {
            ext=",seq=" + seq;
        }

        ObjectName objectName = 
            new ObjectName( domain + ":type=Valve,name=" + className + ext + parentName);
        log.debug("valve objectname = "+objectName);
        return objectName;
    }

    // -------------------- JMX data  --------------------

    public ObjectName getContainerName() {
        if( container== null) return null;
        return ((ContainerBase)container).getJmxName();
    }
}
