package org.apache.catalina.core;


import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Valve;
import org.apache.catalina.startup.HostConfig;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.modeler.Registry;


/**
 * <b>Host</b>接口的标准实现类. 
 * 每个子容器必须是Context实现类， 处理指向特定Web应用程序的请求.
 */
public class StandardHost extends ContainerBase implements Host {
    /* Why do we implement deployer and delegate to deployer ??? */

    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( StandardHost.class );
    
    // ----------------------------------------------------------- Constructors

    public StandardHost() {
        super();
        pipeline.setBasic(new StandardHostValve());
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 此主机的别名集合
     */
    private String[] aliases = new String[0];


    /**
     * 此主机的应用程序根目录.
     */
    private String appBase = ".";


    /**
     * 此主机的自动部署标志.
     */
    private boolean autoDeploy = true;


    /**
     * 默认的上下文配置类的类名, 用于部署web应用程序
     */
    private String configClass = "org.apache.catalina.startup.ContextConfig";


    /**
     * 默认的Context实现类类名, 用于部署web应用程序
     */
    private String contextClass = "org.apache.catalina.core.StandardContext";


    /**
     * 此主机在启动部署标志.
     */
    private boolean deployOnStartup = true;


    /**
     * 部署 Context XML 配置文件标志.
     */
    private boolean deployXML = true;


    /**
     * 部署Web应用程序默认错误报告实现类的类名.
     */
    private String errorReportValveClass = "org.apache.catalina.valves.ErrorReportValve";

    /**
     * errorReportValve的对象名.
     */
    private ObjectName errorReportValveObjectName = null;

    /**
     * 实现类描述信息.
     */
    private static final String info = "org.apache.catalina.core.StandardHost/1.0";


    /**
     * 此主机的动态部署标志
     */
    private boolean liveDeploy = true;


    /**
     * 是否解压 WAR.
     */
    private boolean unpackWARs = true;


    /**
     * 应用程序工作目录.
     */
    private String workDir = null;


    /**
     * 用于打开/关闭XML验证的属性值
     */
     private boolean xmlValidation = false;


    /**
     * 用来打开/关闭XML命名空间感知的属性值.
     */
     private boolean xmlNamespaceAware = false;


    // ------------------------------------------------------------- Properties


    /**
     * 返回此主机的应用程序根目录. 
     * 这可以是绝对路径，相对路径, 或一个URL.
     */
    public String getAppBase() {
        return (this.appBase);
    }


    /**
     * 设置此主机的应用程序根目录.
     * 这可以是绝对路径，相对路径, 或一个URL.
     *
     * @param appBase The new application root
     */
    public void setAppBase(String appBase) {
        String oldAppBase = this.appBase;
        this.appBase = appBase;
        support.firePropertyChange("appBase", oldAppBase, this.appBase);
    }


    /**
     * 返回自动部署标志的值. 
     * 如果属实，这表明该主机的子webapps应该自动部署，在启动时
     */
    public boolean getAutoDeploy() {
        return (this.autoDeploy);
    }


    /**
     * 为该主机设置自动部署标志值.
     * 
     * @param autoDeploy The new auto deploy flag
     */
    public void setAutoDeploy(boolean autoDeploy) {
        boolean oldAutoDeploy = this.autoDeploy;
        this.autoDeploy = autoDeploy;
        support.firePropertyChange("autoDeploy", oldAutoDeploy, this.autoDeploy);
    }


    /**
     * 返回上下文配置类的类名.
     */
    public String getConfigClass() {
        return (this.configClass);
    }


    /**
     * 设置上下文配置类的类名.
     *
     * @param configClass The new context configuration class
     */
    public void setConfigClass(String configClass) {
        String oldConfigClass = this.configClass;
        this.configClass = configClass;
        support.firePropertyChange("configClass", oldConfigClass, this.configClass);
    }


    /**
     * 返回Context实现类的类名.
     */
    public String getContextClass() {
        return (this.contextClass);
    }


    /**
     * 设置Context实现类的类名.
     *
     * @param contextClass The new context implementation class
     */
    public void setContextClass(String contextClass) {
        String oldContextClass = this.contextClass;
        this.contextClass = contextClass;
        support.firePropertyChange("contextClass", oldContextClass, this.contextClass);
    }


    /**
     * 此主机启动部署标志. 如果是true, 这个主机的子应用应该自动发现和部署在启动的时候.
     */
    public boolean getDeployOnStartup() {
        return (this.deployOnStartup);
    }


    /**
     * 此主机启动部署标志.
     * 
     * @param deployOnStartup The new deploy on startup flag
     */
    public void setDeployOnStartup(boolean deployOnStartup) {
        boolean oldDeployOnStartup = this.deployOnStartup;
        this.deployOnStartup = deployOnStartup;
        support.firePropertyChange("deployOnStartup", oldDeployOnStartup, this.deployOnStartup);
    }


    /**
     * 返回部署XML Context配置文件标记.
     */
    public boolean isDeployXML() {
        return (deployXML);
    }


    /**
     * 设置部署XML Context配置文件标记.
     */
    public void setDeployXML(boolean deployXML) {
        this.deployXML = deployXML;
    }


    /**
     * 返回活动部署标志的值. 
     * 如果为true，则表示应启动查找Web应用程序上下文文件, WAR文件的后台线程, 或未打开的目录被插入到
     * <code>appBase</code>目录, 并部署新的.
     */
    public boolean getLiveDeploy() {
        return (this.autoDeploy);
    }


    /**
     * 设置活动部署标志的值.
     * 
     * @param liveDeploy The new live deploy flag
     */
    public void setLiveDeploy(boolean liveDeploy) {
        setAutoDeploy(liveDeploy);
    }


    /**
     * 部署Web应用程序默认错误报告实现类的类名.
     */
    public String getErrorReportValveClass() {
        return (this.errorReportValveClass);
    }


    /**
     * 部署Web应用程序默认错误报告实现类的类名.
     *
     * @param errorReportValveClass 错误报告 valve 类
     */
    public void setErrorReportValveClass(String errorReportValveClass) {
        String oldErrorReportValveClassClass = this.errorReportValveClass;
        this.errorReportValveClass = errorReportValveClass;
        support.firePropertyChange("errorReportValveClass",
                                   oldErrorReportValveClassClass, 
                                   this.errorReportValveClass);
    }


    /**
     * 返回此容器表示的虚拟主机的规范的、完全限定的名称.
     */
    public String getName() {
        return (name);
    }


    /**
     * 设置此容器表示的虚拟主机的规范的、完全限定的名称.
     *
     * @param name 虚拟主机名
     *
     * @exception IllegalArgumentException 如果名称是null
     */
    public void setName(String name) {
        if (name == null)
            throw new IllegalArgumentException
                (sm.getString("standardHost.nullName"));

        name = name.toLowerCase();      // 内部所有名称都是小写字母

        String oldName = this.name;
        this.name = name;
        support.firePropertyChange("name", oldName, this.name);
    }


    /**
     * 返回是否解压 WAR
     */
    public boolean isUnpackWARs() {
        return (unpackWARs);
    }


    /**
     * 设置是否解压 WAR
     */
    public void setUnpackWARs(boolean unpackWARs) {
        this.unpackWARs = unpackWARs;
    }

     /**
     * 设置解析XML实例时使用的XML解析器的验证功能.
     * @param xmlValidation true启用XML实例验证
     */
    public void setXmlValidation(boolean xmlValidation){
        this.xmlValidation = xmlValidation;
    }

    /**
     * 获取server.xml <host> 属性的 xmlValidation.
     * @return true 如果验证可用.
     */
    public boolean getXmlValidation(){
        return xmlValidation;
    }

    /**
     * 获取server.xml <host>属性的 xmlNamespaceAware.
     * @return true 如果启用命名空间感知.
     */
    public boolean getXmlNamespaceAware(){
        return xmlNamespaceAware;
    }


    /**
     * 设置解析XML实例时使用的XML解析器的命名空间感知功能.
     * @param xmlNamespaceAware true 启用命名空间感知功能
     */
    public void setXmlNamespaceAware(boolean xmlNamespaceAware){
        this.xmlNamespaceAware=xmlNamespaceAware;
    }    
    
    /**
     * 主机工作基础目录.
     */
    public String getWorkDir() {
        return (workDir);
    }


    /**
     * 主机工作基础目录.
     */
    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加映射到同一主机的别名.
     *
     * @param alias 要添加的别名
     */
    public void addAlias(String alias) {

        alias = alias.toLowerCase();

        // 跳过重复的别名
        for (int i = 0; i < aliases.length; i++) {
            if (aliases[i].equals(alias))
                return;
        }

        // 将此别名添加到列表中
        String newAliases[] = new String[aliases.length + 1];
        for (int i = 0; i < aliases.length; i++)
            newAliases[i] = aliases[i];
        newAliases[aliases.length] = alias;

        aliases = newAliases;

        // 通知感兴趣的监听器
        fireContainerEvent(ADD_ALIAS_EVENT, alias);
    }


    /**
     * 添加一个子级Container, 只有当实现了Context接口.
     *
     * @param child Child container to be added
     */
    public void addChild(Container child) {
        if (!(child instanceof Context))
            throw new IllegalArgumentException(sm.getString("standardHost.notContext"));
        super.addChild(child);
    }


    /**
     * 返回此主机的别名集. 
     * 如果没有，返回零长度数组.
     */
    public String[] findAliases() {
        return (this.aliases);
    }


    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 返回Context，将被用于处理指定的 主机相对请求URI; 否则返回<code>null</code>.
     *
     * @param uri Request URI to be mapped
     */
    public Context map(String uri) {

        if (log.isDebugEnabled())
            log.debug("Mapping request URI '" + uri + "'");
        if (uri == null)
            return (null);

        // 匹配尽可能长的上下文路径前缀
        if (log.isTraceEnabled())
            log.trace("  Trying the longest context path prefix");
        Context context = null;
        String mapuri = uri;
        while (true) {
            context = (Context) findChild(mapuri);
            if (context != null)
                break;
            int slash = mapuri.lastIndexOf('/');
            if (slash < 0)
                break;
            mapuri = mapuri.substring(0, slash);
        }

        // 如果没有匹配的Context, 选择默认的Context
        if (context == null) {
            if (log.isTraceEnabled())
                log.trace("  Trying the default context");
            context = (Context) findChild("");
        }

        // 如果没有选择上下文，请进行投诉
        if (context == null) {
            log.error(sm.getString("standardHost.mappingError", uri));
            return (null);
        }

        // 返回映射的Context
        if (log.isDebugEnabled())
            log.debug(" Mapped to context '" + context.getPath() + "'");
        return (context);
    }


    /**
     * 从该主机的别名中删除指定的别名.
     *
     * @param alias Alias name to be removed
     */
    public void removeAlias(String alias) {

        alias = alias.toLowerCase();

        synchronized (aliases) {

            // Make sure this alias is currently present
            int n = -1;
            for (int i = 0; i < aliases.length; i++) {
                if (aliases[i].equals(alias)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified alias
            int j = 0;
            String results[] = new String[aliases.length - 1];
            for (int i = 0; i < aliases.length; i++) {
                if (i != n)
                    results[j++] = aliases[i];
            }
            aliases = results;
        }
        // Inform interested listeners
        fireContainerEvent(REMOVE_ALIAS_EVENT, alias);
    }


    public String toString() {
        StringBuffer sb = new StringBuffer();
        if (getParent() != null) {
            sb.append(getParent().toString());
            sb.append(".");
        }
        sb.append("StandardHost[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());
    }


    /**
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    public synchronized void start() throws LifecycleException {
        if( started ) {
            return;
        }
        if( ! initialized )
            init();

        // 查找一个 realm - 之前已经设置. 
        // 如果 realm在上下文之后添加 - 它将设置自己.
        if( realm == null ) {
            ObjectName realmName=null;
            try {
                realmName=new ObjectName( domain + ":type=Realm,host=" + getName());
                if( mserver.isRegistered(realmName ) ) {
                    mserver.invoke(realmName, "init", 
                            new Object[] {},
                            new String[] {}
                    );            
                }
            } catch( Throwable t ) {
                log.debug("No realm for this host " + realmName);
            }
        }
            
        // Set error report valve
        if ((errorReportValveClass != null)
            && (!errorReportValveClass.equals(""))) {
            try {
                boolean found = false;
                if(errorReportValveObjectName != null) {
                    ObjectName[] names = 
                        ((StandardPipeline)pipeline).getValveObjectNames();
                    for (int i=0; !found && i<names.length; i++)
                        if(errorReportValveObjectName.equals(names[i]))
                            found = true ;
                    }
                    if(!found) {          	
                        Valve valve = (Valve) Class.forName(errorReportValveClass)
                        .newInstance();
                        addValve(valve);
                        errorReportValveObjectName = ((ValveBase)valve).getObjectName() ;
                    }
            } catch (Throwable t) {
                log.error(sm.getString
                    ("standardHost.invalidErrorReportValveClass", 
                     errorReportValveClass));
            }
        }
        if(log.isInfoEnabled()) {
            if (xmlValidation)
                log.info( sm.getString("standardHost.validationEnabled"));
            else
                log.info( sm.getString("standardHost.validationDisabled"));
        }
        super.start();
    }


    // -------------------- JMX  --------------------
    /**
      * 返回Valve的 MBean 名称
      *
      * @exception Exception 如果无法创建或注册MBean
      */
     public String [] getValveNames() throws Exception {
         Valve [] valves = this.getValves();
         String [] mbeanNames = new String[valves.length];
         for (int i = 0; i < valves.length; i++) {
             if( valves[i] == null ) continue;
             if( ((ValveBase)valves[i]).getObjectName() == null ) continue;
             mbeanNames[i] = ((ValveBase)valves[i]).getObjectName().toString();
         }
         return mbeanNames;
     }

    public String[] getAliases() {
        return aliases;
    }

    private boolean initialized=false;
    
    public void init() {
        if( initialized ) return;
        initialized=true;
        
        // 已经注册
        if( getParent() == null ) {
            try {
                // Register with the Engine
                ObjectName serviceName=new ObjectName(domain + 
                                        ":type=Engine");

                HostConfig deployer = new HostConfig();
                addLifecycleListener(deployer);                
                if( mserver.isRegistered( serviceName )) {
                    if(log.isDebugEnabled())
                        log.debug("Registering "+ serviceName +" with the Engine");
                    mserver.invoke( serviceName, "addChild",
                            new Object[] { this },
                            new String[] { "org.apache.catalina.Container" } );
                }
            } catch( Exception ex ) {
                log.error("Host registering failed!",ex);
            }
        }
        
        if( oname==null ) {
            // not registered in JMX yet - standalone mode
            try {
                StandardEngine engine=(StandardEngine)parent;
                domain=engine.getName();
                if(log.isDebugEnabled())
                    log.debug( "Register host " + getName() + " with domain "+ domain );
                oname=new ObjectName(domain + ":type=Host,host=" +
                        this.getName());
                controller = oname;
                Registry.getRegistry(null, null)
                    .registerComponent(this, oname, null);
            } catch( Throwable t ) {
                log.error("Host registering failed!", t );
            }
        }
    }

    public void destroy() throws Exception {
        // 销毁子容器
        Container children[] = findChildren();
        super.destroy();
        for (int i = 0; i < children.length; i++) {
            if(children[i] instanceof StandardContext)
                ((StandardContext)children[i]).destroy();
        }
      
    }
    
    public ObjectName preRegister(MBeanServer server, ObjectName oname ) throws Exception {
        ObjectName res=super.preRegister(server, oname);
        String name=oname.getKeyProperty("host");
        if( name != null )
            setName( name );
        return res;        
    }
    
    public ObjectName createObjectName(String domain, ObjectName parent) throws Exception {
        if( log.isDebugEnabled())
            log.debug("Create ObjectName " + domain + " " + parent );
        return new ObjectName( domain + ":type=Host,host=" + getName());
    }
}
