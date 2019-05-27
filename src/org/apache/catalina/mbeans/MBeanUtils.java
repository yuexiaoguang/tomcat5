package org.apache.catalina.mbeans;

import java.util.Hashtable;

import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.modelmbean.ModelMBean;

import org.apache.catalina.Contained;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Group;
import org.apache.catalina.Host;
import org.apache.catalina.Loader;
import org.apache.catalina.Manager;
import org.apache.catalina.Realm;
import org.apache.catalina.Role;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.deploy.ContextEnvironment;
import org.apache.catalina.deploy.ContextResource;
import org.apache.catalina.deploy.ContextResourceLink;
import org.apache.catalina.deploy.NamingResources;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.modeler.ManagedBean;
import org.apache.commons.modeler.Registry;
import org.apache.tomcat.util.IntrospectionUtils;

/**
 * 服务器端MBeans实现类的公用方法
 */
public class MBeanUtils {
    private static Log log = LogFactory.getLog(MBeanUtils.class);

    // ------------------------------------------------------- Static Variables

    /**
     * <code>createManagedBean()</code>使用的常规规则的异常集合.
     * 每对的第一个元素是类名，第二个元素是托管bean名.
     */
    private static String exceptions[][] = {
        { "org.apache.ajp.tomcat4.Ajp13Connector",
          "Ajp13Connector" },
        { "org.apache.coyote.tomcat4.Ajp13Connector",
          "CoyoteConnector" },
        { "org.apache.catalina.users.JDBCGroup",
          "Group" },
        { "org.apache.catalina.users.JDBCRole",
          "Role" },
        { "org.apache.catalina.users.JDBCUser",
          "User" },
        { "org.apache.catalina.users.MemoryGroup",
          "Group" },
        { "org.apache.catalina.users.MemoryRole",
          "Role" },
        { "org.apache.catalina.users.MemoryUser",
          "User" },
    };


    /**
     * 管理bean的配置信息注册表
     */
    private static Registry registry = createRegistry();


    /**
     * The <code>MBeanServer</code> for this application.
     */
    private static MBeanServer mserver = createServer();


    // --------------------------------------------------------- Static Methods

    /**
     * 创建并返回这个Catalina组件相应的<code>ManagedBean</code>名称.
     *
     * @param component 用于创建名称的组件
     */
    static String createManagedName(Object component) {

        // 处理标准规则的异常
        String className = component.getClass().getName();
        for (int i = 0; i < exceptions.length; i++) {
            if (className.equals(exceptions[i][0])) {
                return (exceptions[i][1]);
            }
        }

        // 执行标准转换
        int period = className.lastIndexOf('.');
        if (period >= 0)
            className = className.substring(period + 1);
        return (className);
    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>Connector</code>对象.
     *
     * @param connector The Connector to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(Connector connector)
        throws Exception {

        String mname = createManagedName(connector);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(connector);
        ObjectName oname = createObjectName(domain, connector);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>Context</code>对象.
     *
     * @param context The Context to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(Context context)
        throws Exception {

        String mname = createManagedName(context);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(context);
        ObjectName oname = createObjectName(domain, context);
        if( mserver.isRegistered(oname)) {
            log.debug("Already registered " + oname);
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }

    
    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>ContextEnvironment</code>对象.
     *
     * @param environment The ContextEnvironment to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(ContextEnvironment environment)
        throws Exception {

        String mname = createManagedName(environment);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(environment);
        ObjectName oname = createObjectName(domain, environment);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>ContextResource</code>对象.
     *
     * @param resource The ContextResource to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(ContextResource resource)
        throws Exception {

        String mname = createManagedName(resource);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(resource);
        ObjectName oname = createObjectName(domain, resource);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>ContextResourceLink</code>对象.
     *
     * @param resourceLink The ContextResourceLink to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(ContextResourceLink resourceLink)
        throws Exception {

        String mname = createManagedName(resourceLink);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(resourceLink);
        ObjectName oname = createObjectName(domain, resourceLink);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }    
 
    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>Engine</code>对象.
     *
     * @param engine The Engine to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(Engine engine)
        throws Exception {

        String mname = createManagedName(engine);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(engine);
        ObjectName oname = createObjectName(domain, engine);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>Group</code>对象.
     *
     * @param group The Group to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(Group group)
        throws Exception {

        String mname = createManagedName(group);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(group);
        ObjectName oname = createObjectName(domain, group);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);

    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>Host</code>对象.
     *
     * @param host The Host to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(Host host)
        throws Exception {

        String mname = createManagedName(host);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(host);
        ObjectName oname = createObjectName(domain, host);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>Loader</code>对象.
     *
     * @param loader The Loader to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(Loader loader)
        throws Exception {

        String mname = createManagedName(loader);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(loader);
        ObjectName oname = createObjectName(domain, loader);
        if( mserver.isRegistered( oname ))  {
            // side effect: stop it
            mserver.unregisterMBean( oname );
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);

    }

    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>Manager</code>对象.
     *
     * @param manager The Manager to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(Manager manager)
        throws Exception {

        String mname = createManagedName(manager);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(manager);
        ObjectName oname = createObjectName(domain, manager);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>MBeanFactory</code>对象.
     *
     * @param factory The MBeanFactory to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(MBeanFactory factory)
        throws Exception {

        String mname = createManagedName(factory);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(factory);
        ObjectName oname = createObjectName(domain, factory);
        if( mserver.isRegistered(oname )) {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>NamingResources</code>对象.
     *
     * @param resource The NamingResources to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(NamingResources resource)
        throws Exception {

        String mname = createManagedName(resource);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(resource);
        ObjectName oname = createObjectName(domain, resource);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }

    
    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>Realm</code>对象.
     *
     * @param realm The Realm to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(Realm realm)
        throws Exception {

        String mname = createManagedName(realm);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(realm);
        ObjectName oname = createObjectName(domain, realm);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>Role</code>对象.
     *
     * @param role The Role to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(Role role)
        throws Exception {

        String mname = createManagedName(role);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(role);
        ObjectName oname = createObjectName(domain, role);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>Server</code>对象.
     *
     * @param server The Server to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(Server server)
        throws Exception {

        String mname = createManagedName(server);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(server);
        ObjectName oname = createObjectName(domain, server);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>Service</code>对象.
     *
     * @param service The Service to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(Service service)
        throws Exception {

        String mname = createManagedName(service);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(service);
        ObjectName oname = createObjectName(domain, service);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>User</code>对象.
     *
     * @param user The User to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(User user)
        throws Exception {

        String mname = createManagedName(user);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(user);
        ObjectName oname = createObjectName(domain, user);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>UserDatabase</code>对象.
     *
     * @param userDatabase The UserDatabase to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(UserDatabase userDatabase)
        throws Exception {

        String mname = createManagedName(userDatabase);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(userDatabase);
        ObjectName oname = createObjectName(domain, userDatabase);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }


    /**
     * 创建, 注册, 并返回一个MBean，为这个<code>Valve</code>对象.
     *
     * @param valve The Valve to be managed
     *
     * @exception Exception 如果无法创建或注册MBean
     */
    static ModelMBean createMBean(Valve valve)
        throws Exception {

        String mname = createManagedName(valve);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            Exception e = new Exception("ManagedBean is not found with "+mname);
            throw new MBeanException(e);
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ModelMBean mbean = managed.createMBean(valve);
        ObjectName oname = createObjectName(domain, valve);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
        mserver.registerMBean(mbean, oname);
        return (mbean);
    }

    /**
     * 为这个<code>Connector</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param connector 要被命名的Connector
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                        Connector connector)
        throws MalformedObjectNameException {

        ObjectName name = null;
        if (connector.getClass().getName().indexOf("CoyoteConnector") >= 0 ) {
            try {
                String address = (String)
                    IntrospectionUtils.getProperty(connector, "address");
                Integer port = (Integer)
                    IntrospectionUtils.getProperty(connector, "port");
                Service service = connector.getService();
                String serviceName = null;
                if (service != null)
                    serviceName = service.getName();
                StringBuffer sb = new StringBuffer(domain);
                sb.append(":type=Connector");
                sb.append(",port=" + port);
                if ((address != null) && (address.length()>0)) {
                    sb.append(",address=" + address);
                }
                name = new ObjectName(sb.toString());
                return (name);
            } catch (Exception e) {
                throw new MalformedObjectNameException
                    ("Cannot create object name for " + connector+e);
            }
        } else {
            throw new MalformedObjectNameException
                ("Cannot create object name for " + connector);
        }
    }


    /**
     * 为这个<code>Context</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param context The Context to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              Context context)
        throws MalformedObjectNameException {

        ObjectName name = null;
        Host host = (Host)context.getParent();
        Service service = ((Engine)host.getParent()).getService();
        String path = context.getPath();
        if (path.length() < 1)
            path = "/";
        // FIXME 
        name = new ObjectName(domain + ":j2eeType=WebModule,name=//" +
                              host.getName()+ path +
                              ",J2EEApplication=none,J2EEServer=none");
    
        return (name);
    }

    
    /**
     * 为这个<code>Service</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param environment The ContextEnvironment to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    public static ObjectName createObjectName(String domain,
                                              ContextEnvironment environment)
        throws MalformedObjectNameException {

        ObjectName name = null;
        Object container = 
                environment.getNamingResources().getContainer();
        if (container instanceof Server) {
            name = new ObjectName(domain + ":type=Environment" + 
                        ",resourcetype=Global,name=" + environment.getName());
        } else if (container instanceof Context) {        
            String path = ((Context)container).getPath();
            if (path.length() < 1)
                path = "/";
            Host host = (Host) ((Context)container).getParent();
            Engine engine = (Engine) host.getParent();
            Service service = engine.getService();
            name = new ObjectName(domain + ":type=Environment" + 
                        ",resourcetype=Context,path=" + path + 
                        ",host=" + host.getName() +
                        ",name=" + environment.getName());
        }        
        return (name);
    }
    
    
    /**
     * 为这个<code>ContextResource</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param resource The ContextResource to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    public static ObjectName createObjectName(String domain,
                                              ContextResource resource)
        throws MalformedObjectNameException {

        ObjectName name = null;
        String quotedResourceName = ObjectName.quote(resource.getName());
        Object container = 
                resource.getNamingResources().getContainer();
        if (container instanceof Server) {        
            name = new ObjectName(domain + ":type=Resource" +
                        ",resourcetype=Global,class=" + resource.getType() + 
                        ",name=" + quotedResourceName);
        } else if (container instanceof Context) {                    
            String path = ((Context)container).getPath();
            if (path.length() < 1)
                path = "/";
            Host host = (Host) ((Context)container).getParent();
            Engine engine = (Engine) host.getParent();
            Service service = engine.getService();
            name = new ObjectName(domain + ":type=Resource" +
                        ",resourcetype=Context,path=" + path + 
                        ",host=" + host.getName() +
                        ",class=" + resource.getType() +
                        ",name=" + quotedResourceName);
        }
        return (name);
    }
  
    
     /**
     * 为这个<code>ContextResourceLink</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param resourceLink The ContextResourceLink to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    public static ObjectName createObjectName(String domain,
                                              ContextResourceLink resourceLink)
        throws MalformedObjectNameException {

        ObjectName name = null;
        String quotedResourceLinkName
                = ObjectName.quote(resourceLink.getName());        
        Object container = 
                resourceLink.getNamingResources().getContainer();
        if (container instanceof Server) {        
            name = new ObjectName(domain + ":type=ResourceLink" +
                        ",resourcetype=Global" + 
                        ",name=" + quotedResourceLinkName);
        } else if (container instanceof Context) {                    
            String path = ((Context)container).getPath();
            if (path.length() < 1)
                path = "/";
            Host host = (Host) ((Context)container).getParent();
            Engine engine = (Engine) host.getParent();
            Service service = engine.getService();
            name = new ObjectName(domain + ":type=ResourceLink" +
                        ",resourcetype=Context,path=" + path + 
                        ",host=" + host.getName() +
                        ",name=" + quotedResourceLinkName);
        }
        return (name);
    }
    
    
 
    /**
     * 为这个<code>Engine</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param engine The Engine to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              Engine engine)
        throws MalformedObjectNameException {

        ObjectName name = null;
        name = new ObjectName(domain + ":type=Engine");
        return (name);
    }


    /**
     * 为这个<code>Group</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param group The Group to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              Group group)
        throws MalformedObjectNameException {

        ObjectName name = null;
        name = new ObjectName(domain + ":type=Group,groupname=" +
                              ObjectName.quote(group.getGroupname()) +
                              ",database=" + group.getUserDatabase().getId());
        return (name);
    }


    /**
     * 为这个<code>Host</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param host The Host to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              Host host)
        throws MalformedObjectNameException {

        ObjectName name = null;
        Engine engine = (Engine)host.getParent();
        Service service = engine.getService();
        name = new ObjectName(domain + ":type=Host,host=" +
                              host.getName());
        return (name);
    }


    /**
     * 为这个<code>Loader</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param loader The Loader to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              Loader loader)
        throws MalformedObjectNameException {

        ObjectName name = null;
        Container container = loader.getContainer();

        if (container instanceof Engine) {
            Service service = ((Engine)container).getService();
            name = new ObjectName(domain + ":type=Loader");
        } else if (container instanceof Host) {
            Engine engine = (Engine) container.getParent();
            Service service = engine.getService();
            name = new ObjectName(domain + ":type=Loader,host=" +
                              container.getName());
        } else if (container instanceof Context) {
            String path = ((Context)container).getPath();
            if (path.length() < 1) {
                path = "/";
            }
            Host host = (Host) container.getParent();
            Engine engine = (Engine) host.getParent();
            Service service = engine.getService();
            name = new ObjectName(domain + ":type=Loader,path=" + path +
                              ",host=" + host.getName());
        }
        return (name);
    }


    /**
     * 为这个<code>Manager</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param manager The Manager to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              Manager manager)
        throws MalformedObjectNameException {

        ObjectName name = null;
        Container container = manager.getContainer();

        if (container instanceof Engine) {
            Service service = ((Engine)container).getService();
            name = new ObjectName(domain + ":type=Manager");
        } else if (container instanceof Host) {
            Engine engine = (Engine) container.getParent();
            Service service = engine.getService();
            name = new ObjectName(domain + ":type=Manager,host=" +
                              container.getName());
        } else if (container instanceof Context) {
            String path = ((Context)container).getPath();
            if (path.length() < 1) {
                path = "/";
            }
            Host host = (Host) container.getParent();
            Engine engine = (Engine) host.getParent();
            Service service = engine.getService();
            name = new ObjectName(domain + ":type=Manager,path=" + path +
                              ",host=" + host.getName());
        }
        return (name);
    }
    
    
    /**
     * 为这个<code>Server</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param resources The NamingResources to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              NamingResources resources)
        throws MalformedObjectNameException {

        ObjectName name = null;
        Object container = resources.getContainer();        
        if (container instanceof Server) {        
            name = new ObjectName(domain + ":type=NamingResources" + 
                        ",resourcetype=Global");
        } else if (container instanceof Context) {        
            String path = ((Context)container).getPath();
            if (path.length() < 1)
                path = "/";
            Host host = (Host) ((Context)container).getParent();
            Engine engine = (Engine) host.getParent();
            Service service = engine.getService();
            name = new ObjectName(domain + ":type=NamingResources" + 
                        ",resourcetype=Context,path=" + path + 
                        ",host=" + host.getName());
        }
        return (name);
    }


    /**
     * 为这个<code>MBeanFactory</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param factory The MBeanFactory to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              MBeanFactory factory)
        throws MalformedObjectNameException {

        ObjectName name = new ObjectName(domain + ":type=MBeanFactory");
        return (name);
    }

    
    /**
     * 为这个<code>Realm</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param realm The Realm to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              Realm realm)
        throws MalformedObjectNameException {

        ObjectName name = null;
        Container container = realm.getContainer();

        if (container instanceof Engine) {
            Service service = ((Engine)container).getService();
            name = new ObjectName(domain + ":type=Realm");
        } else if (container instanceof Host) {
            Engine engine = (Engine) container.getParent();
            Service service = engine.getService();
            name = new ObjectName(domain + ":type=Realm,host=" +
                              container.getName());
        } else if (container instanceof Context) {
            String path = ((Context)container).getPath();
            if (path.length() < 1) {
                path = "/";
            }
            Host host = (Host) container.getParent();
            Engine engine = (Engine) host.getParent();
            Service service = engine.getService();
            name = new ObjectName(domain + ":type=Realm,path=" + path +
                              ",host=" + host.getName());
        }
        return (name);
    }


    /**
     * 为这个<code>Role</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param role The Role to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              Role role)
        throws MalformedObjectNameException {

        ObjectName name = null;
        name = new ObjectName(domain + ":type=Role,rolename=" +
                              role.getRolename() + ",database=" +
                              role.getUserDatabase().getId());
        return (name);
    }


    /**
     * 为这个<code>Server</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param server The Server to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              Server server)
        throws MalformedObjectNameException {

        ObjectName name = null;
        name = new ObjectName(domain + ":type=Server");
        return (name);
    }


    /**
     * 为这个<code>Service</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param service The Service to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              Service service)
        throws MalformedObjectNameException {

        ObjectName name = null;
        name = new ObjectName(domain + ":type=Service,serviceName=" + 
                            service.getName());
        return (name);
    }


    /**
     * 为这个<code>User</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param user The User to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              User user)
        throws MalformedObjectNameException {

        ObjectName name = null;
        name = new ObjectName(domain + ":type=User,username=" +
                              ObjectName.quote(user.getUsername())
                              + ",database=" + user.getUserDatabase().getId());
        return (name);
    }


    /**
     * 为这个<code>UserDatabase</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param userDatabase The UserDatabase to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                              UserDatabase userDatabase)
        throws MalformedObjectNameException {

        ObjectName name = null;
        name = new ObjectName(domain + ":type=UserDatabase,database=" +
                              userDatabase.getId());
        return (name);
    }


    /**
     * 为这个<code>Valve</code>对象创建一个<code>ObjectName</code>.
     *
     * @param domain 要创建此名称的域名
     * @param valve The Valve to be named
     *
     * @exception MalformedObjectNameException 如果不能创建名称
     */
    static ObjectName createObjectName(String domain,
                                       Valve valve)
        throws MalformedObjectNameException {
        if( valve instanceof ValveBase ) {
            ObjectName name=((ValveBase)valve).getObjectName();
            if( name != null )
                return name;
        }

        ObjectName name = null;
        Container container = null;
        String className=valve.getClass().getName();
        int period = className.lastIndexOf('.');
        if (period >= 0)
            className = className.substring(period + 1);
        if( valve instanceof Contained ) {
            container = ((Contained)valve).getContainer();
        }
        if( container == null ) {
            throw new MalformedObjectNameException(
                               "Cannot create mbean for non-contained valve " +
                               valve);
        }        
        if (container instanceof Engine) {
            Service service = ((Engine)container).getService();
            String local="";
            int seq = getSeq(local);
            String ext="";
            if( seq > 0 ) {
                ext=",seq=" + seq;
            }
            name = new ObjectName(domain + ":type=Valve,name=" + className + 
                                    ext + local );
        } else if (container instanceof Host) {
            Service service = ((Engine)container.getParent()).getService();
            String local=",host=" +container.getName();
            int seq = getSeq(local);
            String ext="";
            if( seq > 0 ) {
                ext=",seq=" + seq;
            }
            name = new ObjectName(domain + ":type=Valve,name=" + className + 
                                    ext + local );
        } else if (container instanceof Context) {
            String path = ((Context)container).getPath();
            if (path.length() < 1) {
                path = "/";
            }
            Host host = (Host) container.getParent();
            Service service = ((Engine) host.getParent()).getService();
            String local=",path=" + path + ",host=" +
                    host.getName();
            int seq = getSeq(local);
            String ext="";
            if( seq > 0 ) {
                ext=",seq=" + seq;
            }
            name = new ObjectName(domain + ":type=Valve,name=" + className + 
                                    ext + local );
        }
        return (name);
    }

    static Hashtable seq=new Hashtable();
    static int getSeq( String key ) {
        int i[]=(int [])seq.get( key );
        if (i == null ) {
            i=new int[1];
            i[0]=0;
            seq.put( key, i);
        } else {
            i[0]++;
        }
        return i[0];
    }

    /**
     * 创建, 配置（如果有必要）并返回管理对象描述的注册表.
     */
    public synchronized static Registry createRegistry() {

        if (registry == null) {
            registry = Registry.getRegistry(null, null);
            ClassLoader cl=ServerLifecycleListener.class.getClassLoader();

            registry.loadDescriptors("org.apache.catalina.mbeans",  cl);
            registry.loadDescriptors("org.apache.catalina.authenticator", cl);
            registry.loadDescriptors("org.apache.catalina.core", cl);
            registry.loadDescriptors("org.apache.catalina", cl);
            registry.loadDescriptors("org.apache.catalina.deploy", cl);
            registry.loadDescriptors("org.apache.catalina.loader", cl);
            registry.loadDescriptors("org.apache.catalina.realm", cl);
            registry.loadDescriptors("org.apache.catalina.session", cl);
            registry.loadDescriptors("org.apache.catalina.startup", cl);
            registry.loadDescriptors("org.apache.catalina.users", cl);
            registry.loadDescriptors("org.apache.catalina.cluster", cl);
            registry.loadDescriptors("org.apache.catalina.connector", cl);
            registry.loadDescriptors("org.apache.catalina.valves",  cl);
        }
        return (registry);
    }


    /**
     * 创建, 配置（如果需要）并返回<code>MBeanServer</code>，我们将注册<code>DynamicMBean</code>实现类到该MBeanServer中.
     */
    public synchronized static MBeanServer createServer() {

        if (mserver == null) {
            try {
                mserver = Registry.getRegistry(null, null).getMBeanServer();
            } catch (Throwable t) {
                t.printStackTrace(System.out);
                System.exit(1);
            }
        }
        return (mserver);
    }


    /**
     * 为这个<code>Connector</code>对象注销MBean.
     *
     * @param connector The Connector to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(Connector connector, Service service)
        throws Exception {

        connector.setService(service);
        String mname = createManagedName(connector);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, connector);
        connector.setService(null);
        if( mserver.isRegistered( oname ))  {
            mserver.unregisterMBean(oname);
        }
    }


    /**
     * 为这个<code>Context</code>对象注销MBean.
     *
     * @param context The Context to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(Context context)
        throws Exception {

        String mname = createManagedName(context);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, context);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);
    }

    
    /**
     * 为这个<code>ContextEnvironment</code>对象注销MBean.
     *
     * @param environment The ContextEnvironment to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(ContextEnvironment environment)
        throws Exception {

        String mname = createManagedName(environment);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, environment);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);

    }
    
    
    /**
     * 为这个<code>ContextResource</code>对象注销MBean.
     *
     * @param resource The ContextResource to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(ContextResource resource)
        throws Exception {

        String mname = createManagedName(resource);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, resource);
        if( mserver.isRegistered(oname ))
            mserver.unregisterMBean(oname);
    }
     
    
    /**
     * 为这个<code>ContextResourceLink</code>对象注销MBean.
     *
     * @param resourceLink The ContextResourceLink to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(ContextResourceLink resourceLink)
        throws Exception {

        String mname = createManagedName(resourceLink);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, resourceLink);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);
    }   
    
    /**
     * 为这个<code>Engine</code>对象注销MBean.
     *
     * @param engine The Engine to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(Engine engine)
        throws Exception {

        String mname = createManagedName(engine);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, engine);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);
    }


    /**
     * 为这个<code>Group</code>对象注销MBean.
     *
     * @param group The Group to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(Group group)
        throws Exception {

        String mname = createManagedName(group);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, group);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);
    }


    /**
     * 为这个<code>Host</code>对象注销MBean.
     *
     * @param host The Host to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(Host host)
        throws Exception {

        String mname = createManagedName(host);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, host);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);

    }


    /**
     * 为这个<code>Loader</code>对象注销MBean.
     *
     * @param loader The Loader to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(Loader loader)
        throws Exception {

        String mname = createManagedName(loader);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, loader);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);
    }


    /**
     * Deregister the MBean for this
     * <code>Manager</code> object.
     *
     * @param manager The Manager to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(Manager manager)
        throws Exception {

        String mname = createManagedName(manager);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, manager);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);

    }
    
    
   /**
     * 为这个<code>NamingResources</code>对象注销MBean.
     *
     * @param resources The NamingResources to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(NamingResources resources)
        throws Exception {

        String mname = createManagedName(resources);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, resources);
       if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);

    }
    
    
    /**
     * 为这个<code>Realm</code>对象注销MBean.
     *
     * @param realm The Realm to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(Realm realm)
        throws Exception {

        String mname = createManagedName(realm);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, realm);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);
    }


    /**
     * 为这个<code>Role</code>对象注销MBean.
     *
     * @param role The Role to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(Role role) throws Exception {

        String mname = createManagedName(role);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, role);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);
    }


    /**
     * 为这个<code>Server</code>对象注销MBean.
     *
     * @param server The Server to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(Server server)
        throws Exception {

        String mname = createManagedName(server);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, server);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);
    }


    /**
     * 为这个<code>Service</code>对象注销MBean.
     *
     * @param service The Service to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(Service service)
        throws Exception {

        String mname = createManagedName(service);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, service);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);
    }


    /**
     * 为这个<code>User</code>对象注销MBean.
     *
     * @param user The User to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(User user)
        throws Exception {

        String mname = createManagedName(user);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, user);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);
    }


    /**
     * 为这个<code>UserDatabase</code>对象注销MBean.
     *
     * @param userDatabase The UserDatabase to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(UserDatabase userDatabase)
        throws Exception {

        String mname = createManagedName(userDatabase);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, userDatabase);
        if( mserver.isRegistered(oname) )
            mserver.unregisterMBean(oname);
    }


    /**
     * 为这个<code>Valve</code>对象注销MBean.
     *
     * @param valve The Valve to be managed
     *
     * @exception Exception 如果MBean不能注销
     */
    static void destroyMBean(Valve valve, Container container)
        throws Exception {

        ((Contained)valve).setContainer(container);
        String mname = createManagedName(valve);
        ManagedBean managed = registry.findManagedBean(mname);
        if (managed == null) {
            return;
        }
        String domain = managed.getDomain();
        if (domain == null)
            domain = mserver.getDefaultDomain();
        ObjectName oname = createObjectName(domain, valve);
        try {
            ((Contained)valve).setContainer(null);
        } catch (Throwable t) {
        ;
        }
        if( mserver.isRegistered(oname) ) {
            mserver.unregisterMBean(oname);
        }
    }
}
