package org.apache.catalina.core;


import java.io.File;
import java.util.List;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Realm;
import org.apache.catalina.Service;
import org.apache.catalina.realm.JAASRealm;
import org.apache.catalina.util.ServerInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.modeler.Registry;
import org.apache.commons.modeler.modules.MbeansSource;

/**
 * <b>Engine</b>接口的标准实现类.
 * 每个子级容器必须是一个Host实现类，处理该虚拟主机的特定完全限定主机名.
 * 可以直接设置jvmRoute, 或使用System.property <b>jvmRoute</b>.
 */
public class StandardEngine extends ContainerBase implements Engine {

    private static Log log = LogFactory.getLog(StandardEngine.class);

    // ----------------------------------------------------------- Constructors


    public StandardEngine() {
        super();
        pipeline.setBasic(new StandardEngineValve());
        /* 设置 jmvRoute, 使用系统属性 jvmRoute */
        try {
            setJvmRoute(System.getProperty("jvmRoute"));
        } catch(Exception ex) {
        }
        // 默认情况下, engine 将保存重新加载的线程
        backgroundProcessorDelay = 10;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 当没有服务器主机时使用的主机名, 或未知主机, 在请求中指定
     */
    private String defaultHost = null;


    /**
     * 描述信息
     */
    private static final String info =
        "org.apache.catalina.core.StandardEngine/1.0";


    /**
     * 属于这个Engine的<code>Service</code>.
     */
    private Service service = null;

    /** 允许每个引擎直接指定基目录. 及时停止使用catalina.base 属性 - 否则会失去一些灵活性.
     */
    private String baseDir = null;

    /** 可选的配置文件中. 这将在jk和ServerListener中替换 "hacks".
     * MBeans文件不久后支持持久性. 它可能会替换jk2.properties 以及 server.xml.
     * 当然 - 相同的bean可以由外部实体加载和管理 - 就像嵌入应用程序一样 - 它可以使用不同的持久性机制.
     */ 
    private String mbeansFile = null;
    
    /** Mbean通过engine加装.  
     */ 
    private List mbeans;
    

    /**
     * 这个Tomcat实例的 JVM Route ID. 所有路由ID在集群中必须是唯一的.
     */
    private String jvmRouteId;


    // ------------------------------------------------------------- Properties

    /** 如果没有显式配置，则提供默认设置
     *
     * @return 配置的 realm, 或默认的 JAAS realm
     */
    public Realm getRealm() {
        Realm configured=super.getRealm();
        // 如果没有设置 realm - 默认是 JAAS
        // 或者可以在engine、context、host等级中覆盖
        if( configured==null ) {
            configured=new JAASRealm();
            this.setRealm( configured );
        }
        return configured;
    }


    /**
     * 返回默认的主机
     */
    public String getDefaultHost() {
        return (defaultHost);
    }


    /**
     * 设置默认的主机
     *
     * @param host 新的默认的主机
     */
    public void setDefaultHost(String host) {
        String oldDefaultHost = this.defaultHost;
        if (host == null) {
            this.defaultHost = null;
        } else {
            this.defaultHost = host.toLowerCase();
        }
        support.firePropertyChange("defaultHost", oldDefaultHost,
                                   this.defaultHost);
    }
    
    public void setName(String name ) {
        if( domain != null ) {
            // keep name==domain, ignore override
            // 已经注册
            super.setName( domain );
            return;
        }
        // 引擎名称作为域名
        domain=name; // XXX should we set it in init() ? It shouldn't matter
        super.setName( name );
    }


    /**
     * 设置集群范围唯一标识符.
     * 此值仅在负载平衡方案中有用.
     * <p>
     * 此属性在设置后不应更改.
     */
    public void setJvmRoute(String routeId) {
        jvmRouteId = routeId;
    }


    /**
     * 检索群集范围唯一标识符.
     * 此值仅在负载平衡方案中有用.
     */
    public String getJvmRoute() {
        return jvmRouteId;
    }


    /**
     * 返回关联的<code>Service</code>.
     */
    public Service getService() {
        return (this.service);
    }


    /**
     * 设置关联的<code>Service</code>.
     *
     * @param service 这个Engine的service
     */
    public void setService(Service service) {
        this.service = service;
    }

    public String getMbeansFile() {
        return mbeansFile;
    }

    public void setMbeansFile(String mbeansFile) {
        this.mbeansFile = mbeansFile;
    }

    public String getBaseDir() {
        if( baseDir==null ) {
            baseDir=System.getProperty("catalina.base");
        }
        if( baseDir==null ) {
            baseDir=System.getProperty("catalina.home");
        }
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个子级Container, 只有当子级Container是Host实现类时.
     *
     * @param child Child container to be added
     */
    public void addChild(Container child) {
        if (!(child instanceof Host))
            throw new IllegalArgumentException
                (sm.getString("standardEngine.notHost"));
        super.addChild(child);
    }


    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }

    /**
     * 不允许为这个Container设置一个父级Container, 因为Engine应该位于容器Container结构的顶部
     *
     * @param container Proposed parent Container
     */
    public void setParent(Container container) {
        throw new IllegalArgumentException(sm.getString("standardEngine.notParent"));
    }


    private boolean initialized=false;
    
    public void init() {
        if( initialized ) return;
        initialized=true;

        if( oname==null ) {
            // 还没有在 JMX 中注册 - 单机模式
            try {
                if (domain==null) {
                    domain=getName();
                }
                if(log.isDebugEnabled())
                    log.debug( "Register " + domain );
                oname=new ObjectName(domain + ":type=Engine");
                controller=oname;
                Registry.getRegistry(null, null)
                    .registerComponent(this, oname, null);
            } catch( Throwable t ) {
                log.info("Error registering ", t );
            }
        }

        if( mbeansFile == null ) {
            String defaultMBeansFile=getBaseDir() + "/conf/tomcat5-mbeans.xml";
            File f=new File( defaultMBeansFile );
            if( f.exists() ) mbeansFile=f.getAbsolutePath();
        }
        if( mbeansFile != null ) {
            readEngineMbeans();
        }
        if( mbeans != null ) {
            try {
                Registry.getRegistry(null, null).invoke(mbeans, "init", false);
            } catch (Exception e) {
                log.error("Error in init() for " + mbeansFile, e);
            }
        }
        
        // not needed since the following if statement does the same thing the right way
        // remove later after checking
        //if( service==null ) {
        //    try {
        //        ObjectName serviceName=getParentName();        
        //        if( mserver.isRegistered( serviceName )) {
        //            log.info("Registering with the service ");
        //            try {
        //                mserver.invoke( serviceName, "setContainer",
        //                        new Object[] { this },
        //                        new String[] { "org.apache.catalina.Container" } );
        //            } catch( Exception ex ) {
        //               ex.printStackTrace();
        //            }
        //        }
        //    } catch( Exception ex ) {
        //        log.error("Error registering with service ");
        //    }
        //}
        
        if( service==null ) {
            // 一致性...: 可能处于嵌入式模式
            try {
                service=new StandardService();
                service.setContainer( this );
                service.initialize();
            } catch( Throwable t ) {
                log.error(t);
            }
        }
        
    }
    
    public void destroy() throws LifecycleException {
        if( ! initialized ) return;
        initialized=false;
        
        // 如果创建了它, 确保它也销毁了this.stop()的调用
        ((StandardService)service).destroy();

        if( mbeans != null ) {
            try {
                Registry.getRegistry(null, null)
                    .invoke(mbeans, "destroy", false);
            } catch (Exception e) {
                log.error(sm.getString("standardEngine.unregister.mbeans.failed" ,mbeansFile), e);
            }
        }
        // 
        if( mbeans != null ) {
            try {
                for( int i=0; i<mbeans.size() ; i++ ) {
                    Registry.getRegistry(null, null)
                        .unregisterComponent((ObjectName)mbeans.get(i));
                }
            } catch (Exception e) {
                log.error(sm.getString("standardEngine.unregister.mbeans.failed", mbeansFile), e);
            }
        }
        
        // 强制所有的元数据重新加载.
        // 这不会影响现有bean. 应该每个都注册 - 停止使用静态的.
        Registry.getRegistry(null, null).resetMetadata();
        
    }
    
    /**
     * @exception LifecycleException if a startup error occurs
     */
    public void start() throws LifecycleException {
        if( started ) {
            return;
        }
        if( !initialized ) {
            init();
        }

        // 找一个realm - 这可能之前已经配置. 
        // 如果 realm 在上下文之后被添加 - 他将设置它自己.
        if( realm == null ) {
            ObjectName realmName=null;
            try {
                realmName=new ObjectName( domain + ":type=Realm");
                if( mserver.isRegistered(realmName ) ) {
                    mserver.invoke(realmName, "init", 
                            new Object[] {},
                            new String[] {}
                    );            
                }
            } catch( Throwable t ) {
                log.debug("No realm for this engine " + realmName);
            }
        }
            
        // 记录服务器标识信息
        //System.out.println(ServerInfo.getServerInfo());
        if(log.isInfoEnabled())
            log.info( "Starting Servlet Engine: " + ServerInfo.getServerInfo());
        if( mbeans != null ) {
            try {
                Registry.getRegistry(null, null)
                    .invoke(mbeans, "start", false);
            } catch (Exception e) {
                log.error("Error in start() for " + mbeansFile, e);
            }
        }

        // 标准容器启动
        super.start();
    }
    
    public void stop() throws LifecycleException {
        super.stop();
        if( mbeans != null ) {
            try {
                Registry.getRegistry(null, null).invoke(mbeans, "stop", false);
            } catch (Exception e) {
                log.error("Error in stop() for " + mbeansFile, e);
            }
        }
    }


    public String toString() {
        StringBuffer sb = new StringBuffer("StandardEngine[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());
    }


    // ------------------------------------------------------ Protected Methods


    // -------------------- JMX registration  --------------------

    public ObjectName preRegister(MBeanServer server,
                                  ObjectName name) throws Exception {
        super.preRegister(server,name);

        this.setName( name.getDomain());

        return name;
    }

    // FIXME Remove -- not used 
    public ObjectName getParentName() throws MalformedObjectNameException {
        if (getService()==null) {
            return null;
        }
        String name = getService().getName();
        ObjectName serviceName=new ObjectName(domain +
                        ":type=Service,serviceName="+name);
        return serviceName;                
    }
    
    public ObjectName createObjectName(String domain, ObjectName parent)
        throws Exception
    {
        if( log.isDebugEnabled())
            log.debug("Create ObjectName " + domain + " " + parent );
        return new ObjectName( domain + ":type=Engine");
    }

    
    private void readEngineMbeans() {
        try {
            MbeansSource mbeansMB=new MbeansSource();
            File mbeansF=new File( mbeansFile );
            mbeansMB.setSource(mbeansF);
            
            Registry.getRegistry(null, null).registerComponent
                (mbeansMB, domain + ":type=MbeansFile", null);
            mbeansMB.load();
            mbeansMB.init();
            mbeansMB.setRegistry(Registry.getRegistry(null, null));
            mbeans=mbeansMB.getMBeans();
            
        } catch( Throwable t ) {
            log.error( "Error loading " + mbeansFile, t );
        }
        
    }
    
    public String getDomain() {
        if (domain!=null) {
            return domain;
        } else { 
            return getName();
        }
    }
    
    public void setDomain(String domain) {
        this.domain = domain;
    }
}
