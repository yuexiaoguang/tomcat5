package org.apache.catalina.mbeans;

import java.io.File;
import java.util.Vector;

import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.RuntimeOperationsException;

import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Server;
import org.apache.catalina.ServerFactory;
import org.apache.catalina.Service;
import org.apache.catalina.Valve;
import org.apache.catalina.authenticator.SingleSignOn;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.core.StandardService;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.realm.DataSourceRealm;
import org.apache.catalina.realm.JDBCRealm;
import org.apache.catalina.realm.JNDIRealm;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.realm.UserDatabaseRealm;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.HostConfig;
import org.apache.catalina.valves.AccessLogValve;
import org.apache.catalina.valves.RemoteAddrValve;
import org.apache.catalina.valves.RemoteHostValve;
import org.apache.catalina.valves.RequestDumperValve;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.modeler.BaseModelMBean;
import org.apache.commons.modeler.Registry;

/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.catalina.core.StandardServer</code> component.</p>
 */
public class MBeanFactory extends BaseModelMBean {

    private static org.apache.commons.logging.Log log = 
        org.apache.commons.logging.LogFactory.getLog(MBeanFactory.class);

    /**
     * 应用的<code>MBeanServer</code>.
     */
    private static MBeanServer mserver = MBeanUtils.createServer();

    /**
     * 管理bean的配置信息注册表.
     */
    private static Registry registry = MBeanUtils.createRegistry();


    // ----------------------------------------------------------- Constructors


    /**
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception RuntimeOperationsException 如果发生IllegalArgumentException
     */
    public MBeanFactory() throws MBeanException, RuntimeOperationsException {
        super();
    }

    // ------------------------------------------------------------- Operations

    /**
     * 返回指定bean类型的管理bean定义
     *
     * @param type MBean type
     */
    public String findObjectName(String type) {
        if (type.equals("org.apache.catalina.core.StandardContext")) {
            return "StandardContext";
        } else if (type.equals("org.apache.catalina.core.StandardEngine")) {
            return "Engine";
        } else if (type.equals("org.apache.catalina.core.StandardHost")) {
            return "Host";
        } else {
            return null;
        }
    }


    /**
     * 提取路径字符串时删除冗余代码的简便方法
     *
     * @param t path string
     * @return empty string if t==null || t.equals("/")
     */
    private final String getPathStr(String t) {
        if (t == null || t.equals("/")) {
            return "";
        }
        return t;
    }
    
   /**
     * 获取指定父级的ObjectName对应的父级 ContainerBase
     */
    private ContainerBase getParentContainerFromParent(ObjectName pname) 
        throws Exception {
        
        String type = pname.getKeyProperty("type");
        String j2eeType = pname.getKeyProperty("j2eeType");
        Service service = getService(pname);
        StandardEngine engine = (StandardEngine) service.getContainer();
        if ((j2eeType!=null) && (j2eeType.equals("WebModule"))) {
            String name = pname.getKeyProperty("name");
            name = name.substring(2);
            int i = name.indexOf("/");
            String hostName = name.substring(0,i);
            String path = name.substring(i);
            Host host = (Host) engine.findChild(hostName);
            String pathStr = getPathStr(path);
            StandardContext context = (StandardContext)host.findChild(pathStr);
            return context;
        } else if (type != null) {
            if (type.equals("Engine")) {
                return engine;
            } else if (type.equals("Host")) {
                String hostName = pname.getKeyProperty("host");
                StandardHost host = (StandardHost) engine.findChild(hostName);
                return host;
            }
        }
        return null;
        
    }


    /**
     * 获取指定子级的ObjectName对应的父级 ContainerBase
     */    
    private ContainerBase getParentContainerFromChild(ObjectName oname) 
        throws Exception {
        
        String hostName = oname.getKeyProperty("host");
        String path = oname.getKeyProperty("path");
        Service service = getService(oname);
        StandardEngine engine = (StandardEngine) service.getContainer();
        if (hostName == null) {             
            // child's container is Engine
            return engine;
        } else if (path == null) {      
            // child's container is Host
            StandardHost host = (StandardHost) engine.findChild(hostName);
            return host;
        } else {                
            // child's container is Context
            StandardHost host = (StandardHost) engine.findChild(hostName);
            path = getPathStr(path);
            StandardContext context = (StandardContext) host.findChild(path);
            return context;
        }
    }

    
    private Service getService(ObjectName oname) throws Exception {
    
        String domain = oname.getDomain();
        Server server = ServerFactory.getServer();
        Service[] services = server.findServices();
        StandardService service = null;
        for (int i = 0; i < services.length; i++) {
            service = (StandardService) services[i];
            if (domain.equals(service.getObjectName().getDomain())) {
                break;
            }
        }
        if (!service.getObjectName().getDomain().equals(domain)) {
            throw new Exception("Service with the domain is not found");
        }        
        return service;
    }
    
    
    /**
     * Create a new AccessLoggerValve.
     *
     * @param parent 关联的父级组件的MBean名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createAccessLoggerValve(String parent)
        throws Exception {

        ObjectName pname = new ObjectName(parent);
        // Create a new AccessLogValve instance
        AccessLogValve accessLogger = new AccessLogValve();
        ContainerBase containerBase = getParentContainerFromParent(pname);
        // 将新实例添加到其父组件
        containerBase.addValve(accessLogger);
        ObjectName oname = accessLogger.getObjectName();
        return (oname.toString());
    }
        

    /**
     * Create a new AjpConnector
     *
     * @param parent 关联的父级组件的MBean名称
     * @param address 要绑定的IP地址
     * @param port TCP端口号
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createAjpConnector(String parent, String address, int port)
        throws Exception {

        return createConnector(parent, address, port, true, false);
    }
    
    /**
     * Create a new DataSource Realm.
     *
     * @param parent 关联的父级组件的MBean名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createDataSourceRealm(String parent, String dataSourceName, 
        String roleNameCol, String userCredCol, String userNameCol, 
        String userRoleTable, String userTable) throws Exception {

        // Create a new DataSourceRealm instance
        DataSourceRealm realm = new DataSourceRealm();
        realm.setDataSourceName(dataSourceName);
        realm.setRoleNameCol(roleNameCol);
        realm.setUserCredCol(userCredCol);
        realm.setUserNameCol(userNameCol);
        realm.setUserRoleTable(userRoleTable);
        realm.setUserTable(userTable);

        // 将新实例添加到其父组件
        ObjectName pname = new ObjectName(parent);
        ContainerBase containerBase = getParentContainerFromParent(pname);
        // 将新实例添加到其父组件
        containerBase.setRealm(realm);
        // 返回相应的 MBean 名称
        ObjectName oname = realm.getObjectName();
        if (oname != null) {
            return (oname.toString());
        } else {
            return null;
        }   
    }

    /**
     * Create a new HttpConnector
     *
     * @param parent 关联的父级组件的MBean名称
     * @param address 要绑定的IP地址
     * @param port TCP端口号
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createHttpConnector(String parent, String address, int port)
        throws Exception {
	return createConnector(parent, address, port, false, false);
    }

    /**
     * Create a new Connector
     *
     * @param parent 关联的父级组件的MBean名称
     * @param address 要绑定的IP地址
     * @param port TCP端口号
     * @param isAjp 创建一个AJP/1.3连接器
     * @param isSSL 创建安全连接器
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    private String createConnector(String parent, String address, int port, boolean isAjp, boolean isSSL)
        throws Exception {
        Connector retobj = new Connector();
        if ((address!=null) && (address.length()>0)) {
            retobj.setProperty("address", address);
        }
        // 设置端口号
        retobj.setPort(port);
        // 设置协议
        retobj.setProtocol(isAjp ? "AJP/1.3" : "HTTP/1.1");
        // Set SSL
        retobj.setSecure(isSSL);
        retobj.setScheme(isSSL ? "https" : "http");
        // 将新实例添加到其父组件
        // FIX ME - addConnector will fail
        ObjectName pname = new ObjectName(parent);
        Service service = getService(pname);
        service.addConnector(retobj);
        
        // 返回相应的 MBean 名称
        ObjectName coname = retobj.getObjectName();
        
        return (coname.toString());
    }


    /**
     * Create a new HttpsConnector
     *
     * @param parent 关联的父级组件的MBean名称
     * @param address 要绑定的IP地址
     * @param port TCP端口号
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createHttpsConnector(String parent, String address, int port)
        throws Exception {
        return createConnector(parent, address, port, false, true);
    }

    /**
     * Create a new JDBC Realm.
     *
     * @param parent 关联的父级组件的MBean名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createJDBCRealm(String parent, String driverName, 
    	String connectionName, String connectionPassword, String connectionURL)
        throws Exception {

        // Create a new JDBCRealm instance
        JDBCRealm realm = new JDBCRealm();
        realm.setDriverName(driverName);
        realm.setConnectionName(connectionName);
        realm.setConnectionPassword(connectionPassword);
        realm.setConnectionURL(connectionURL);

        // 将新实例添加到其父组件
        ObjectName pname = new ObjectName(parent);
        ContainerBase containerBase = getParentContainerFromParent(pname);
        // 将新实例添加到其父组件
        containerBase.setRealm(realm);
        // 返回相应的 MBean 名称
        ObjectName oname = realm.getObjectName();

        if (oname != null) {
            return (oname.toString());
        } else {
            return null;
        }   
    }


    /**
     * Create a new JNDI Realm.
     *
     * @param parent 关联的父级组件的MBean名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createJNDIRealm(String parent) throws Exception {

         // Create a new JNDIRealm instance
        JNDIRealm realm = new JNDIRealm();

        // 将新实例添加到其父组件
        ObjectName pname = new ObjectName(parent);
        ContainerBase containerBase = getParentContainerFromParent(pname);
        // 将新实例添加到其父组件
        containerBase.setRealm(realm);
        // 返回相应的 MBean 名称
        ObjectName oname = realm.getObjectName();

        if (oname != null) {
            return (oname.toString());
        } else {
            return null;
        }   
    }


    /**
     * Create a new Memory Realm.
     *
     * @param parent 关联的父级组件的MBean名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createMemoryRealm(String parent) throws Exception {

         // Create a new MemoryRealm instance
        MemoryRealm realm = new MemoryRealm();

        // 将新实例添加到其父组件
        ObjectName pname = new ObjectName(parent);
        ContainerBase containerBase = getParentContainerFromParent(pname);
        // 将新实例添加到其父组件
        containerBase.setRealm(realm);
        // 返回相应的 MBean 名称
        ObjectName oname = realm.getObjectName();
        if (oname != null) {
            return (oname.toString());
        } else {
            return null;
        }   
    }


    /**
     * Create a new Remote Address Filter Valve.
     *
     * @param parent 关联的父级组件的MBean名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createRemoteAddrValve(String parent) throws Exception {

        // Create a new RemoteAddrValve instance
        RemoteAddrValve valve = new RemoteAddrValve();

        // 将新实例添加到其父组件
        ObjectName pname = new ObjectName(parent);
        ContainerBase containerBase = getParentContainerFromParent(pname);
        containerBase.addValve(valve);
        ObjectName oname = valve.getObjectName();
        return (oname.toString());
    }


     /**
     * Create a new Remote Host Filter Valve.
     *
     * @param parent 关联的父级组件的MBean名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createRemoteHostValve(String parent) throws Exception {

        // Create a new RemoteHostValve instance
        RemoteHostValve valve = new RemoteHostValve();

        // 将新实例添加到其父组件
        ObjectName pname = new ObjectName(parent);
        ContainerBase containerBase = getParentContainerFromParent(pname);
        containerBase.addValve(valve);
        ObjectName oname = valve.getObjectName();
        return (oname.toString());
    }


    /**
     * Create a new Request Dumper Valve.
     *
     * @param parent 关联的父级组件的MBean名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createRequestDumperValve(String parent) throws Exception {
        // Create a new RequestDumperValve instance
        RequestDumperValve valve = new RequestDumperValve();

        // 将新实例添加到其父组件
        ObjectName pname = new ObjectName(parent);
        ContainerBase containerBase = getParentContainerFromParent(pname);
        containerBase.addValve(valve);
        ObjectName oname = valve.getObjectName();
        return (oname.toString());
    }


    /**
     * Create a new Single Sign On Valve.
     *
     * @param parent 关联的父级组件的MBean名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createSingleSignOn(String parent) throws Exception {
        // Create a new SingleSignOn instance
        SingleSignOn valve = new SingleSignOn();

        // 将新实例添加到其父组件
        ObjectName pname = new ObjectName(parent);
        ContainerBase containerBase = getParentContainerFromParent(pname);
        containerBase.addValve(valve);
        ObjectName oname = valve.getObjectName();
        return (oname.toString());
    }
    
    
   /**
     * Create a new StandardContext.
     *
     * @param parent 关联的父级组件的MBean名称
     * @param path 此上下文的上下文路径
     * @param docBase 这个上下文的文档基目录(or WAR)
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createStandardContext(String parent, 
                                        String path,
                                        String docBase) throws Exception {
                                            
        // XXX 向后兼容性. 一旦管理员支持删除它
        return createStandardContext(parent,path,docBase,false,false,false,false);                                  
    }

    /**
     * 给定上下文路径, 获取配置文件名.
     */
    private String getConfigFile(String path) {
        String basename = null;
        if (path.equals("")) {
            basename = "ROOT";
        } else {
            basename = path.substring(1).replace('/', '#');
        }
        return (basename);
    }

   /**
     * Create a new StandardContext.
     *
     * @param parent 关联的父级组件的MBean名称
     * @param path 此上下文的上下文路径
     * @param docBase 这个上下文的文档基目录(or WAR)
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createStandardContext(String parent, 
                                        String path,
                                        String docBase,
                                        boolean xmlValidation,
                                        boolean xmlNamespaceAware,
                                        boolean tldValidation,
                                        boolean tldNamespaceAware)
        throws Exception {

        // Create a new StandardContext instance
        StandardContext context = new StandardContext();
        path = getPathStr(path);
        context.setPath(path);
        context.setDocBase(docBase);
        context.setXmlValidation(xmlValidation);
        context.setXmlNamespaceAware(xmlNamespaceAware);
        context.setTldValidation(tldValidation);
        context.setTldNamespaceAware(tldNamespaceAware);
        
        ContextConfig contextConfig = new ContextConfig();
        context.addLifecycleListener(contextConfig);

        // 将新实例添加到其父组件
        ObjectName pname = new ObjectName(parent);
        ObjectName deployer = new ObjectName(pname.getDomain()+
                                             ":type=Deployer,host="+
                                             pname.getKeyProperty("host"));
        if(mserver.isRegistered(deployer)) {
            String contextPath = context.getPath();
            mserver.invoke(deployer, "addServiced",
                           new Object [] {contextPath},
                           new String [] {"java.lang.String"});
            String configPath = (String)mserver.getAttribute(deployer,
                                                             "configBaseName");
            String baseName = getConfigFile(contextPath);
            File configFile = new File(new File(configPath), baseName+".xml");
            context.setConfigFile(configFile.getAbsolutePath());
            mserver.invoke(deployer, "manageApp",
                           new Object[] {context},
                           new String[] {"org.apache.catalina.Context"});
            mserver.invoke(deployer, "removeServiced",
                           new Object [] {contextPath},
                           new String [] {"java.lang.String"});
        } else {
            log.warn("Deployer not found for "+pname.getKeyProperty("host"));
            Service service = getService(pname);
            Engine engine = (Engine) service.getContainer();
            Host host = (Host) engine.findChild(pname.getKeyProperty("host"));
            host.addChild(context);
        }

        // 返回相应的 MBean 名称
        ObjectName oname = context.getJmxName();
        return (oname.toString());
    }


   /**
     * Create a new StandardEngine.
     *
     * @param parent 关联的父级组件的MBean名称
     * @param engineName 这个Engine的唯一名称
     * @param defaultHost 这个Engine的默认主机名
     * @param serviceName 这个Service的唯一名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public Vector createStandardEngineService(String parent, 
            String engineName, String defaultHost, String serviceName)
        throws Exception {

        // Create a new StandardService instance
        StandardService service = new StandardService();
        service.setName(serviceName);
        // Create a new StandardEngine instance
        StandardEngine engine = new StandardEngine();
        engine.setName(engineName);
        engine.setDefaultHost(defaultHost);
        // 需要设置引擎，然后将其添加到服务器，以设置域名
        service.setContainer(engine);
        // 将新实例添加到其父组件
        Server server = ServerFactory.getServer();
        server.addService(service);
        Vector onames = new Vector();
        // FIXME service & engine.getObjectName
        //ObjectName oname = engine.getObjectName();
        ObjectName oname = 
            MBeanUtils.createObjectName(engineName, engine);
        onames.add(0, oname);
        //oname = service.getObjectName();
        oname = 
            MBeanUtils.createObjectName(engineName, service);
        onames.add(1, oname);
        return (onames);
    }


    /**
     * Create a new StandardHost.
     *
     * @param parent 关联的父级组件的MBean名称
     * @param name 此主机的唯一名称
     * @param appBase 应用程序基目录名
     * @param autoDeploy 是否自动部署?
     * @param deployOnStartup 是否在服务器启动时部署?
     * @param deployXML 是否部署上下文XML配置文件属性?
     * @param unpackWARs 自动部署的时候，是否解压 WAR?
     * @param xmlNamespaceAware 是否打开/关闭XML命名空间感知功能?
     * @param xmlValidation 是否打开/关闭XML验证功能?        
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createStandardHost(String parent, String name,
                                     String appBase,
                                     boolean autoDeploy,
                                     boolean deployOnStartup,
                                     boolean deployXML,                                       
                                     boolean unpackWARs,
                                     boolean xmlNamespaceAware,
                                     boolean xmlValidation)
        throws Exception {

        // Create a new StandardHost instance
        StandardHost host = new StandardHost();
        host.setName(name);
        host.setAppBase(appBase);
        host.setAutoDeploy(autoDeploy);
        host.setDeployOnStartup(deployOnStartup);
        host.setDeployXML(deployXML);
        host.setUnpackWARs(unpackWARs);
        host.setXmlNamespaceAware(xmlNamespaceAware);
        host.setXmlValidation(xmlValidation);
	
        // add HostConfig for active reloading
        HostConfig hostConfig = new HostConfig();
        host.addLifecycleListener(hostConfig);

        // 将新实例添加到其父组件
        ObjectName pname = new ObjectName(parent);
        Service service = getService(pname);
        Engine engine = (Engine) service.getContainer();
        engine.addChild(host);

        // 返回相应的 MBean 名称
        return (host.getObjectName().toString());
    }


    /**
     * Create a new StandardManager.
     *
     * @param parent 关联的父级组件的MBean名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createStandardManager(String parent)
        throws Exception {

        // Create a new StandardManager instance
        StandardManager manager = new StandardManager();

        // 将新实例添加到其父组件
        ObjectName pname = new ObjectName(parent);
        ContainerBase containerBase = getParentContainerFromParent(pname);
        if (containerBase != null) {
            containerBase.setManager(manager);
        } 
        ObjectName oname = manager.getObjectName();
        if (oname != null) {
            return (oname.toString());
        } else {
            return null;
        }
    }


    /**
     * Create a new StandardService.
     *
     * @param parent 关联的父级组件的MBean名称
     * @param name Unique name of this StandardService
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createStandardService(String parent, String name, String domain)
        throws Exception {

        // Create a new StandardService instance
        StandardService service = new StandardService();
        service.setName(name);

        // 将新实例添加到其父组件
        Server server = ServerFactory.getServer();
        server.addService(service);

        // 返回相应的 MBean 名称
        return (service.getObjectName().toString());
    }



    /**
     * Create a new  UserDatabaseRealm.
     *
     * @param parent 关联的父级组件的MBean名称
     * @param resourceName 相关UserDatabase的全局JNDI资源名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createUserDatabaseRealm(String parent, String resourceName)
        throws Exception {

         // Create a new UserDatabaseRealm instance
        UserDatabaseRealm realm = new UserDatabaseRealm();
        realm.setResourceName(resourceName);
        
        // 将新实例添加到其父组件
        ObjectName pname = new ObjectName(parent);
        ContainerBase containerBase = getParentContainerFromParent(pname);
        // 将新实例添加到其父组件
        containerBase.setRealm(realm);
        // 返回相应的 MBean 名称
        ObjectName oname = realm.getObjectName();
        // FIXME getObjectName() returns null
        //ObjectName oname = 
        //    MBeanUtils.createObjectName(pname.getDomain(), realm);
        if (oname != null) {
            return (oname.toString());
        } else {
            return null;
        }   
    }


    /**
     * Create a new Web Application Loader.
     *
     * @param parent 关联的父级组件的MBean名称
     *
     * @exception Exception 如果一个MBean不能被创建或注册
     */
    public String createWebappLoader(String parent)
        throws Exception {

        // Create a new WebappLoader instance
        WebappLoader loader = new WebappLoader();

        // 将新实例添加到其父组件
        ObjectName pname = new ObjectName(parent);
        ContainerBase containerBase = getParentContainerFromParent(pname);
        if (containerBase != null) {
            containerBase.setLoader(loader);
        } 
        // FIXME add Loader.getObjectName
        //ObjectName oname = loader.getObjectName();
        ObjectName oname = 
            MBeanUtils.createObjectName(pname.getDomain(), loader);
        return (oname.toString());
    }


    /**
     * Remove an existing Connector.
     *
     * @param name 要删除的组件的MBean名称
     *
     * @exception Exception 如果不能删除组件
     */
    public void removeConnector(String name) throws Exception {

        // 获取要删除的组件的引用
        ObjectName oname = new ObjectName(name);
        Server server = ServerFactory.getServer();
        Service service = getService(oname);
        String port = oname.getKeyProperty("port");
        //String address = oname.getKeyProperty("address");

        Connector conns[] = (Connector[]) service.findConnectors();

        for (int i = 0; i < conns.length; i++) {
            String connAddress = String.valueOf(conns[i].getProperty("address"));
            String connPort = ""+conns[i].getPort();

            // if (((address.equals("null")) &&
            if ((connAddress==null) && port.equals(connPort)) {
                service.removeConnector(conns[i]);
                conns[i].destroy();
                break;
            }
            // } else if (address.equals(connAddress))
            if (port.equals(connPort)) {
                // 从它的父组件上删除这个组件
                service.removeConnector(conns[i]);
                conns[i].destroy();
                break;
            }
        }

    }


    /**
     * Remove an existing Context.
     *
     * @param contextName 要删除的组件的MBean名称
     *
     * @exception Exception 如果不能删除组件
     */
    public void removeContext(String contextName) throws Exception {

        // 获取要删除的组件的引用
        ObjectName oname = new ObjectName(contextName);
        String domain = oname.getDomain();
        StandardService service = (StandardService) getService(oname);

        Engine engine = (Engine) service.getContainer();
        String name = oname.getKeyProperty("name");
        name = name.substring(2);
        int i = name.indexOf("/");
        String hostName = name.substring(0,i);
        String path = name.substring(i);
        ObjectName deployer = new ObjectName(domain+":type=Deployer,host="+
                                             hostName);
        String pathStr = getPathStr(path);
        if(mserver.isRegistered(deployer)) {
            mserver.invoke(deployer,"addServiced",
                           new Object[]{pathStr},
                           new String[] {"java.lang.String"});
            mserver.invoke(deployer,"unmanageApp",
                           new Object[] {pathStr},
                           new String[] {"java.lang.String"});
            mserver.invoke(deployer,"removeServiced",
                           new Object[] {pathStr},
                           new String[] {"java.lang.String"});
        } else {
            log.warn("Deployer not found for "+hostName);
            Host host = (Host) engine.findChild(hostName);
            Context context = (Context) host.findChild(pathStr);
            // 从它的父组件上删除这个组件
            host.removeChild(context);
            if(context instanceof StandardContext)
            try {
                ((StandardContext)context).destroy();
            } catch (Exception e) {
                log.warn("Error during context [" + context.getName() + "] destroy ", e);
           }
   
        }

    }


    /**
     * Remove an existing Host.
     *
     * @param name 要删除的组件的MBean名称
     *
     * @exception Exception 如果不能删除组件
     */
    public void removeHost(String name) throws Exception {

        // 获取要删除的组件的引用
        ObjectName oname = new ObjectName(name);
        String hostName = oname.getKeyProperty("host");
        Service service = getService(oname);
        Engine engine = (Engine) service.getContainer();
        Host host = (Host) engine.findChild(hostName);

        // 从它的父组件上删除这个组件
        if(host!=null) {
            if(host instanceof StandardHost)
                ((StandardHost)host).destroy();
            else
                engine.removeChild(host);
        }

    }


    /**
     * Remove an existing Loader.
     *
     * @param name 要删除的组件的MBean名称
     *
     * @exception Exception 如果不能删除组件
     */
    public void removeLoader(String name) throws Exception {
        ObjectName oname = new ObjectName(name);
        // 获取要删除的组件的引用
        ContainerBase container = getParentContainerFromChild(oname);    
        container.setLoader(null);
    }


    /**
     * Remove an existing Manager.
     *
     * @param name 要删除的组件的MBean名称
     *
     * @exception Exception 如果不能删除组件
     */
    public void removeManager(String name) throws Exception {
        ObjectName oname = new ObjectName(name);
        // 获取要删除的组件的引用
        ContainerBase container = getParentContainerFromChild(oname);    
        container.setManager(null);
    }


    /**
     * Remove an existing Realm.
     *
     * @param name 要删除的组件的MBean名称
     *
     * @exception Exception 如果不能删除组件
     */
    public void removeRealm(String name) throws Exception {
        ObjectName oname = new ObjectName(name);
        // 获取要删除的组件的引用
        ContainerBase container = getParentContainerFromChild(oname); 
        container.setRealm(null);
    }


    /**
     * Remove an existing Service.
     *
     * @param name 要删除的组件的MBean名称
     *
     * @exception Exception 如果不能删除组件
     */
    public void removeService(String name) throws Exception {

        // 获取要删除的组件的引用
        ObjectName oname = new ObjectName(name);
        String serviceName = oname.getKeyProperty("serviceName");
        Server server = ServerFactory.getServer();
        Service service = server.findService(serviceName);

        // 从它的父组件上删除这个组件
        server.removeService(service);
    }


    /**
     * Remove an existing Valve.
     *
     * @param name 要删除的组件的MBean名称
     *
     * @exception Exception 如果不能删除组件
     */
    public void removeValve(String name) throws Exception {

        // 获取要删除的组件的引用
        ObjectName oname = new ObjectName(name);
        ContainerBase container = getParentContainerFromChild(oname);
        String sequence = oname.getKeyProperty("seq");
        Valve[] valves = (Valve[])container.getValves();
        for (int i = 0; i < valves.length; i++) {
            ObjectName voname = ((ValveBase) valves[i]).getObjectName();
            if (voname.equals(oname)) {
                container.removeValve(valves[i]);
            }
        }
    }
}

