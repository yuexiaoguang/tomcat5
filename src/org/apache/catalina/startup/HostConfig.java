package org.apache.catalina.startup;


import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.util.StringManager;
import org.apache.commons.modeler.Registry;
import org.apache.tomcat.util.digester.Digester;


/**
 * 开启<b>Host</b>的事件监听器，配置Host的属性, 及其相关的上下文.
 */
public class HostConfig implements LifecycleListener {
    
    protected static org.apache.commons.logging.Log log=
         org.apache.commons.logging.LogFactory.getLog( HostConfig.class );

    // ----------------------------------------------------- Instance Variables


    /**
     * App base.
     */
    protected File appBase = null;


    /**
     * Config base.
     */
    protected File configBase = null;


    /**
     * 使用的Context配置类的类名.
     */
    protected String configClass = "org.apache.catalina.startup.ContextConfig";


    /**
     * 使用的Context实现类的类名.
     */
    protected String contextClass = "org.apache.catalina.core.StandardContext";


    /**
     * 关联的Host
     */
    protected Host host = null;

    
    /**
     * JMX ObjectName
     */
    protected ObjectName oname = null;
    

    /**
     * The string resources for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 应该部署xml上下文配置文件吗?
     */
    protected boolean deployXML = false;


    /**
     * 是否解压 WAR 文件，当在<code>appBase</code>目录自动部署应用的时候?
     */
    protected boolean unpackWARs = false;


    /**
     * 部署的应用
     */
    protected HashMap deployed = new HashMap();

    
    /**
     * 正在使用的应用程序列表, 目前不应该是deployed/undeployed/redeployed.
     */
    protected ArrayList serviced = new ArrayList();
    

    /**
     * 用于打开/关闭XML验证的属性值
     */
    protected boolean xmlValidation = false;


    /**
     * 用来打开/关闭XML命名空间感知的属性值.
     */
    protected boolean xmlNamespaceAware = false;


    /**
     * 用于解析上下文描述符的<code>Digester</code>实例.
     */
    protected static Digester digester = createDigester();


    // ------------------------------------------------------------- Properties


    /**
     * 返回上下文配置类名.
     */
    public String getConfigClass() {
        return (this.configClass);
    }


    /**
     * 设置上下文配置类名
     *
     * @param configClass The new Context configuration class name.
     */
    public void setConfigClass(String configClass) {
        this.configClass = configClass;
    }


    /**
     * 返回上下文实现类名
     */
    public String getContextClass() {
        return (this.contextClass);
    }


    /**
     * 设置上下文实现类名
     *
     * @param contextClass The new Context implementation class name.
     */
    public void setContextClass(String contextClass) {
        this.contextClass = contextClass;
    }


    /**
     * 返回部署XML配置文件标志.
     */
    public boolean isDeployXML() {
        return (this.deployXML);
    }


    /**
     * 设置部署XML配置文件标志.
     *
     * @param deployXML The new deploy XML flag
     */
    public void setDeployXML(boolean deployXML) {
        this.deployXML= deployXML;
    }


    /**
     * 是否解压 WAR 文件.
     */
    public boolean isUnpackWARs() {
        return (this.unpackWARs);
    }


    /**
     * 是否解压 WAR 文件.
     *
     * @param unpackWARs The new unpack WARs flag
     */
    public void setUnpackWARs(boolean unpackWARs) {
        this.unpackWARs = unpackWARs;
    }
    
    
     /**
     * 设置当解析xml实例的时候是否启用XML解析器的验证功能.
     * 
     * @param xmlValidation true 启用XML实例验证
     */
    public void setXmlValidation(boolean xmlValidation){
        this.xmlValidation = xmlValidation;
    }

    /**
     * 获取 server.xml <host> 属性的 xmlValidation.
     */
    public boolean getXmlValidation(){
        return xmlValidation;
    }

    /**
     * 获取 server.xml <host> 属性的 xmlNamespaceAware.
     */
    public boolean getXmlNamespaceAware(){
        return xmlNamespaceAware;
    }


    /**
     * 设置当解析xml实例的时候是否启用XML解析器的命名空间感知功能.
     * 
     * @param xmlNamespaceAware true to enable namespace awareness
     */
    public void setXmlNamespaceAware(boolean xmlNamespaceAware){
        this.xmlNamespaceAware=xmlNamespaceAware;
    }    


    // --------------------------------------------------------- Public Methods


    /**
     * 处理关联的Host的START事件.
     *
     * @param event The lifecycle event that has occurred
     */
    public void lifecycleEvent(LifecycleEvent event) {

        if (event.getType().equals(Lifecycle.PERIODIC_EVENT))
            check();

        // Identify the host we are associated with
        try {
            host = (Host) event.getLifecycle();
            if (host instanceof StandardHost) {
                setDeployXML(((StandardHost) host).isDeployXML());
                setUnpackWARs(((StandardHost) host).isUnpackWARs());
                setXmlNamespaceAware(((StandardHost) host).getXmlNamespaceAware());
                setXmlValidation(((StandardHost) host).getXmlValidation());
            }
        } catch (ClassCastException e) {
            log.error(sm.getString("hostConfig.cce", event.getLifecycle()), e);
            return;
        }

        // Process the event that has occurred
        if (event.getType().equals(Lifecycle.START_EVENT))
            start();
        else if (event.getType().equals(Lifecycle.STOP_EVENT))
            stop();

    }

    
    /**
     * 向列表中添加应用程序服务.
     */
    public synchronized void addServiced(String name) {
        serviced.add(name);
    }
    
    
    /**
     * 是否包含应用程序?
     * @return state of the application
     */
    public synchronized boolean isServiced(String name) {
        return (serviced.contains(name));
    }
    

    /**
     * 从列表中删除应用程序服务.
     */
    public synchronized void removeServiced(String name) {
        serviced.remove(name);
    }

    
    /**
     * 获取应用程序部署的时间戳.
     * 
     * @return 0L 如果没有部署该名称的应用程序
     */
    public long getDeploymentTime(String name) {
    	DeployedApplication app = (DeployedApplication) deployed.get(name);
    	if (app == null) {
    		return 0L;
    	} else {
    		return app.timestamp;
    	}
    }
    
    
    // ------------------------------------------------------ Protected Methods

    
    /**
     * 创建将用于解析上下文配置文件的digester.
     */
    protected static Digester createDigester() {
        Digester digester = new Digester();
        digester.setValidating(false);
        // 添加对象创建规则
        digester.addObjectCreate("Context", "org.apache.catalina.core.StandardContext",
            "className");
        // 在该对象上设置属性 (设置额外属性并不重要)
        digester.addSetProperties("Context");
        return (digester);
    }
    

    /**
     * 返回Host的表示"应用根目录"的文件对象.
     */
    protected File appBase() {
        if (appBase != null) {
            return appBase;
        }

        File file = new File(host.getAppBase());
        if (!file.isAbsolute())
            file = new File(System.getProperty("catalina.base"),
                            host.getAppBase());
        try {
            appBase = file.getCanonicalFile();
        } catch (IOException e) {
            appBase = file;
        }
        return (appBase);
    }


    /**
     * 关联的Host的配置根目录
     */
    protected File configBase() {

        if (configBase != null) {
            return configBase;
        }

        File file = new File(System.getProperty("catalina.base"), "conf");
        Container parent = host.getParent();
        if ((parent != null) && (parent instanceof Engine)) {
            file = new File(file, parent.getName());
        }
        file = new File(file, host.getName());
        try {
            configBase = file.getCanonicalFile();
        } catch (IOException e) {
            configBase = file;
        }
        return (configBase);
    }

    /**
     * 获取configBase的名称.
     * 用于使用 JMX 管理.
     */
    public String getConfigBaseName() {
        return configBase().getAbsolutePath();
    }

    /**
     * 获取指定路径的配置文件名称.
     */
    protected String getConfigFile(String path) {
        String basename = null;
        if (path.equals("")) {
            basename = "ROOT";
        } else {
            basename = path.substring(1).replace('/', '#');
        }
        return (basename);
    }

    
    /**
     * 获取指定上下文路径的配置文件名称.
     */
    protected String getDocBase(String path) {
        String basename = null;
        if (path.equals("")) {
            basename = "ROOT";
        } else {
            basename = path.substring(1);
        }
        return (basename);
    }

    
    /**
     * 部署所有在“应用根目录”找到的目录或 WAR 文件.
     */
    protected void deployApps() {

        File appBase = appBase();
        File configBase = configBase();
        // Deploy XML descriptors from configBase
        deployDescriptors(configBase, configBase.list());
        // Deploy WARs, and loop if additional descriptors are found
        deployWARs(appBase, appBase.list());
        // Deploy expanded folders
        deployDirectories(appBase, appBase.list());
    }


    /**
     * 部署所有在“应用根目录”找到的目录或 WAR 文件.
     */
    protected void deployApps(String name) {
        File appBase = appBase();
        File configBase = configBase();
        String baseName = getConfigFile(name);
        String docBase = getConfigFile(name);
        
        // Deploy XML descriptors from configBase
        File xml = new File(configBase, baseName + ".xml");
        if (xml.exists())
            deployDescriptor(name, xml, baseName + ".xml");
        // Deploy WARs, and loop if additional descriptors are found
        File war = new File(appBase, docBase + ".war");
        if (war.exists())
            deployWAR(name, war, docBase + ".war");
        // Deploy expanded folders
        File dir = new File(appBase, docBase);
        if (dir.exists())
            deployDirectory(name, dir, docBase);
    }


    /**
     * 部署xml上下文描述符
     */
    protected void deployDescriptors(File configBase, String[] files) {

        if (files == null)
            return;
        
        for (int i = 0; i < files.length; i++) {

            if (files[i].equalsIgnoreCase("META-INF"))
                continue;
            if (files[i].equalsIgnoreCase("WEB-INF"))
                continue;
            File contextXml = new File(configBase, files[i]);
            if (files[i].toLowerCase().endsWith(".xml")) {

                // 计算上下文路径并确保它是唯一的
                String nameTmp = files[i].substring(0, files[i].length() - 4);
                String contextPath = "/" + nameTmp.replace('#', '/');
                if (nameTmp.equals("ROOT")) {
                    contextPath = "";
                }

                if (isServiced(contextPath))
                    continue;
                
                String file = files[i];

                deployDescriptor(contextPath, contextXml, file);
            }
        }
    }


    /**
     * @param contextPath
     * @param contextXml
     * @param file
     */
    protected void deployDescriptor(String contextPath, File contextXml, String file) {
        if (deploymentExists(contextPath)) {
            return;
        }
        
        DeployedApplication deployedApp = new DeployedApplication(contextPath);

        // 假设这是一个配置描述符并部署它
        if(log.isDebugEnabled()) {
            log.debug(sm.getString("hostConfig.deployDescriptor", file));
        }

        Context context = null;
        try {
            synchronized (digester) {
                try {
                    context = (Context) digester.parse(contextXml);
                } finally {
                    digester.reset();
                }
            }
            if (context instanceof Lifecycle) {
                Class clazz = Class.forName(host.getConfigClass());
                LifecycleListener listener =
                    (LifecycleListener) clazz.newInstance();
                ((Lifecycle) context).addLifecycleListener(listener);
            }
            context.setConfigFile(contextXml.getAbsolutePath());
            context.setPath(contextPath);
            // 添加相关的docBase 到部署的列表，如果它是一个 WAR
            boolean isWar = false;
            boolean isExternal = false;
            if (context.getDocBase() != null) {
                File docBase = new File(context.getDocBase());
                if (!docBase.isAbsolute()) {
                    docBase = new File(appBase(), context.getDocBase());
                }
                // 如果是外部 docBase, 首先注册 .xml
                if (!docBase.getCanonicalPath().startsWith(appBase().getAbsolutePath())) {
                    isExternal = true;
                    deployedApp.redeployResources.put
                        (contextXml.getAbsolutePath(), new Long(contextXml.lastModified()));
                    deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                        new Long(docBase.lastModified()));
                    if (docBase.getAbsolutePath().toLowerCase().endsWith(".war")) {
                        isWar = true;
                    }
                } else {
                    log.warn(sm.getString("hostConfig.deployDescriptor.localDocBaseSpecified",
                             docBase));
                    // Ignore specified docBase
                    context.setDocBase(null);
                }
            }
            host.addChild(context);
            // Get paths for WAR and expanded WAR in appBase
            String name = null;
            String path = context.getPath();
            if (path.equals("")) {
                name = "ROOT";
            } else {
                if (path.startsWith("/")) {
                    name = path.substring(1);
                } else {
                    name = path;
                }
            }
            File expandedDocBase = new File(name);
            File warDocBase = new File(name + ".war");
            if (!expandedDocBase.isAbsolute()) {
                expandedDocBase = new File(appBase(), name);
                warDocBase = new File(appBase(), name + ".war");
            }
            // 添加解压后的 WAR 和所有受监视的资源
            if (isWar && unpackWARs) {
                deployedApp.redeployResources.put(expandedDocBase.getAbsolutePath(),
                        new Long(expandedDocBase.lastModified()));
                deployedApp.redeployResources.put
                    (contextXml.getAbsolutePath(), new Long(contextXml.lastModified()));
                addWatchedResources(deployedApp, expandedDocBase.getAbsolutePath(), context);
            } else {
                // 查找存在的匹配的 war 和扩展文件夹
                if (warDocBase.exists()) {
                    deployedApp.redeployResources.put(warDocBase.getAbsolutePath(),
                            new Long(warDocBase.lastModified()));
                }
                if (expandedDocBase.exists()) {
                    deployedApp.redeployResources.put(expandedDocBase.getAbsolutePath(),
                            new Long(expandedDocBase.lastModified()));
                    addWatchedResources(deployedApp, 
                            expandedDocBase.getAbsolutePath(), context);
                } else {
                    addWatchedResources(deployedApp, null, context);
                }
                // 添加上下文 XML 到文件列表, 上下文列表可能触发一个 redeployment
                if (!isExternal) {
                    deployedApp.redeployResources.put
                        (contextXml.getAbsolutePath(), new Long(contextXml.lastModified()));
                }
            }
        } catch (Throwable t) {
            log.error(sm.getString("hostConfig.deployDescriptor.error",
                                   file), t);
        }

        if (context != null && host.findChild(context.getName()) != null) {
            deployed.put(contextPath, deployedApp);
        }
    }


    /**
     * 部署 WAR 文件.
     */
    protected void deployWARs(File appBase, String[] files) {
        
        if (files == null)
            return;
        
        boolean checkAdditionalDeployments = false;
        
        for (int i = 0; i < files.length; i++) {
            
            if (files[i].equalsIgnoreCase("META-INF"))
                continue;
            if (files[i].equalsIgnoreCase("WEB-INF"))
                continue;
            File dir = new File(appBase, files[i]);
            if (files[i].toLowerCase().endsWith(".war")) {
                
                // 计算上下文路径并确保它是唯一的
                String contextPath = "/" + files[i];
                int period = contextPath.lastIndexOf(".");
                if (period >= 0)
                    contextPath = contextPath.substring(0, period);
                if (contextPath.equals("/ROOT"))
                    contextPath = "";
                
                if (isServiced(contextPath))
                    continue;
                
                String file = files[i];
                
                deployWAR(contextPath, dir, file);
            }
        }
    }


    /**
     * @param contextPath
     * @param dir
     * @param file
     */
    protected void deployWAR(String contextPath, File dir, String file) {
        
        if (deploymentExists(contextPath))
            return;
        
        // 检查嵌套 /META-INF/context.xml
        JarFile jar = null;
        JarEntry entry = null;
        InputStream istream = null;
        BufferedOutputStream ostream = null;
        File xml = new File
            (configBase, file.substring(0, file.lastIndexOf(".")) + ".xml");
        if (deployXML && !xml.exists()) {
            try {
                jar = new JarFile(dir);
                entry = jar.getJarEntry(Constants.ApplicationContextXml);
                if (entry != null) {
                    istream = jar.getInputStream(entry);
                    
                    configBase.mkdirs();
                    
                    ostream =
                        new BufferedOutputStream
                        (new FileOutputStream(xml), 1024);
                    byte buffer[] = new byte[1024];
                    while (true) {
                        int n = istream.read(buffer);
                        if (n < 0) {
                            break;
                        }
                        ostream.write(buffer, 0, n);
                    }
                    ostream.flush();
                    ostream.close();
                    ostream = null;
                    istream.close();
                    istream = null;
                    entry = null;
                    jar.close();
                    jar = null;
                }
            } catch (Exception e) {
                // Ignore and continue
                if (ostream != null) {
                    try {
                        ostream.close();
                    } catch (Throwable t) {
                        ;
                    }
                    ostream = null;
                }
                if (istream != null) {
                    try {
                        istream.close();
                    } catch (Throwable t) {
                        ;
                    }
                    istream = null;
                }
            } finally {
                entry = null;
                if (jar != null) {
                    try {
                        jar.close();
                    } catch (Throwable t) {
                        ;
                    }
                    jar = null;
                }
            }
        }
        
        DeployedApplication deployedApp = new DeployedApplication(contextPath);
        
        // 部署 WAR 文件中的背影
        if(log.isInfoEnabled()) 
            log.info(sm.getString("hostConfig.deployJar", file));

        // Populate redeploy resources with the WAR file
        deployedApp.redeployResources.put(dir.getAbsolutePath(), new Long(dir.lastModified()));

        try {
            Context context = (Context) Class.forName(contextClass).newInstance();
            if (context instanceof Lifecycle) {
                Class clazz = Class.forName(host.getConfigClass());
                LifecycleListener listener =
                    (LifecycleListener) clazz.newInstance();
                ((Lifecycle) context).addLifecycleListener(listener);
            }
            context.setPath(contextPath);
            context.setDocBase(file);
            if (xml.exists()) {
                context.setConfigFile(xml.getAbsolutePath());
                deployedApp.redeployResources.put
                    (xml.getAbsolutePath(), new Long(xml.lastModified()));
            }
            host.addChild(context);
            // 如果正在解压 WARs, docBase 在启动上下文后会发生变异
            if (unpackWARs && (context.getDocBase() != null)) {
                String name = null;
                String path = context.getPath();
                if (path.equals("")) {
                    name = "ROOT";
                } else {
                    if (path.startsWith("/")) {
                        name = path.substring(1);
                    } else {
                        name = path;
                    }
                }
                File docBase = new File(name);
                if (!docBase.isAbsolute()) {
                    docBase = new File(appBase(), name);
                }
                deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                        new Long(docBase.lastModified()));
                addWatchedResources(deployedApp, docBase.getAbsolutePath(), context);
            } else {
                addWatchedResources(deployedApp, null, context);
            }
        } catch (Throwable t) {
            log.error(sm.getString("hostConfig.deployJar.error", file), t);
        }
        
        deployed.put(contextPath, deployedApp);
    }


    /**
     * 部署目录.
     */
    protected void deployDirectories(File appBase, String[] files) {

        if (files == null)
            return;
        
        for (int i = 0; i < files.length; i++) {

            if (files[i].equalsIgnoreCase("META-INF"))
                continue;
            if (files[i].equalsIgnoreCase("WEB-INF"))
                continue;
            File dir = new File(appBase, files[i]);
            if (dir.isDirectory()) {

                // 确保有一个应用程序配置目录
                // 这是必要的, 如果Context appBase 与Web服务器文档根相同, 确保只有Web应用程序被部署而不是Web空间的目录.
                File webInf = new File(dir, "/WEB-INF");
                if (!webInf.exists() || !webInf.isDirectory() ||
                    !webInf.canRead())
                    continue;

                // 计算上下文路径并确保它是唯一的
                String contextPath = "/" + files[i];
                if (files[i].equals("ROOT"))
                    contextPath = "";

                if (isServiced(contextPath))
                    continue;

                String file = files[i];
                
                deployDirectory(contextPath, dir, file);
            }
        }
    }

    
    /**
     * @param contextPath
     * @param dir
     * @param file
     */
    protected void deployDirectory(String contextPath, File dir, String file) {
        DeployedApplication deployedApp = new DeployedApplication(contextPath);
        
        if (deploymentExists(contextPath))
            return;

        // 在这个目录中部署应用程序
        if( log.isDebugEnabled() ) 
            log.debug(sm.getString("hostConfig.deployDir", file));
        try {
            Context context = (Context) Class.forName(contextClass).newInstance();
            if (context instanceof Lifecycle) {
                Class clazz = Class.forName(host.getConfigClass());
                LifecycleListener listener =
                    (LifecycleListener) clazz.newInstance();
                ((Lifecycle) context).addLifecycleListener(listener);
            }
            context.setPath(contextPath);
            context.setDocBase(file);
            File configFile = new File(dir, Constants.ApplicationContextXml);
            if (deployXML) {
                context.setConfigFile(configFile.getAbsolutePath());
            }
            host.addChild(context);
            deployedApp.redeployResources.put(dir.getAbsolutePath(),
                    new Long(dir.lastModified()));
            if (deployXML) {
                deployedApp.redeployResources.put(configFile.getAbsolutePath(),
                        new Long(configFile.lastModified()));
            }
            addWatchedResources(deployedApp, dir.getAbsolutePath(), context);
        } catch (Throwable t) {
            log.error(sm.getString("hostConfig.deployDir.error", file), t);
        }

        deployed.put(contextPath, deployedApp);
    }

    
    /**
     * 检查一个程序是否已经部署在该主机.
     * 
     * @param contextPath of the context which will be checked
     */
    protected boolean deploymentExists(String contextPath) {
        return (deployed.containsKey(contextPath) || (host.findChild(contextPath) != null));
    }
    

    /**
     * 将受监视的资源添加到指定的上下文中.
     * @param app HostConfig deployed app
     * @param docBase web app docBase
     * @param context web application context
     */
    protected void addWatchedResources(DeployedApplication app, String docBase, Context context) {
        // FIXME: 有特点的想法. 添加对模式的支持(ex: WEB-INF/*, WEB-INF/*.xml), 只会检查是否至少一个资源比app.timestamp更新
        File docBaseFile = null;
        if (docBase != null) {
            docBaseFile = new File(docBase);
            if (!docBaseFile.isAbsolute()) {
                docBaseFile = new File(appBase(), docBase);
            }
        }
        String[] watchedResources = context.findWatchedResources();
        for (int i = 0; i < watchedResources.length; i++) {
            File resource = new File(watchedResources[i]);
            if (!resource.isAbsolute()) {
                if (docBase != null) {
                    resource = new File(docBaseFile, watchedResources[i]);
                } else {
                    continue;
                }
            }
            app.reloadResources.put(resource.getAbsolutePath(), 
                    new Long(resource.lastModified()));
        }
    }
    

    /**
     * 检查资源以进行重新部署和重新加载.
     */
    protected synchronized void checkResources(DeployedApplication app) {
        String[] resources = (String[]) app.redeployResources.keySet().toArray(new String[0]);
        for (int i = 0; i < resources.length; i++) {
            File resource = new File(resources[i]);
            if (log.isDebugEnabled())
                log.debug("Checking context[" + app.name + "] redeploy resource " + resource);
            if (resource.exists()) {
                long lastModified = ((Long) app.redeployResources.get(resources[i])).longValue();
                if ((!resource.isDirectory()) && resource.lastModified() > lastModified) {
                    // Undeploy application
                    if (log.isInfoEnabled())
                        log.info(sm.getString("hostConfig.undeploy", app.name));
                    ContainerBase context = (ContainerBase) host.findChild(app.name);
                    host.removeChild(context);
                    try {
                        context.destroy();
                    } catch (Exception e) {
                        log.warn(sm.getString
                                 ("hostConfig.context.destroy", app.name), e);
                    }
                    // 删除其他重新调配资源
                    for (int j = i + 1; j < resources.length; j++) {
                        try {
                            File current = new File(resources[j]);
                            current = current.getCanonicalFile();
                            if ((current.getAbsolutePath().startsWith(appBase().getAbsolutePath()))
                                    || (current.getAbsolutePath().startsWith(configBase().getAbsolutePath()))) {
                                if (log.isDebugEnabled())
                                    log.debug("Delete " + current);
                                ExpandWar.delete(current);
                            }
                        } catch (IOException e) {
                            log.warn(sm.getString
                                    ("hostConfig.canonicalizing", app.name), e);
                        }
                    }
                    deployed.remove(app.name);
                    return;
                }
            } else {
                long lastModified = ((Long) app.redeployResources.get(resources[i])).longValue();
                if (lastModified == 0L) {
                    continue;
                }
                // Undeploy application
                if (log.isInfoEnabled())
                    log.info(sm.getString("hostConfig.undeploy", app.name));
                ContainerBase context = (ContainerBase) host.findChild(app.name);
                host.removeChild(context);
                try {
                    context.destroy();
                } catch (Exception e) {
                    log.warn(sm.getString
                             ("hostConfig.context.destroy", app.name), e);
                }
                // 删除所有重新调配资源
                for (int j = i + 1; j < resources.length; j++) {
                    try {
                        File current = new File(resources[j]);
                        current = current.getCanonicalFile();
                        if ((current.getAbsolutePath().startsWith(appBase().getAbsolutePath()))
                            || (current.getAbsolutePath().startsWith(configBase().getAbsolutePath()))) {
                            if (log.isDebugEnabled())
                                log.debug("Delete " + current);
                            ExpandWar.delete(current);
                        }
                    } catch (IOException e) {
                        log.warn(sm.getString
                                ("hostConfig.canonicalizing", app.name), e);
                    }
                }
                // 删除重新加载资源 (删除剩余 .xml 描述符)
                String[] resources2 = (String[]) app.reloadResources.keySet().toArray(new String[0]);
                for (int j = 0; j < resources2.length; j++) {
                    try {
                        File current = new File(resources2[j]);
                        current = current.getCanonicalFile();
                        if ((current.getAbsolutePath().startsWith(appBase().getAbsolutePath()))
                            || ((current.getAbsolutePath().startsWith(configBase().getAbsolutePath())
                                 && (current.getAbsolutePath().endsWith(".xml"))))) {
                            if (log.isDebugEnabled())
                                log.debug("Delete " + current);
                            ExpandWar.delete(current);
                        }
                    } catch (IOException e) {
                        log.warn(sm.getString
                                ("hostConfig.canonicalizing", app.name), e);
                    }
                }
                deployed.remove(app.name);
                return;
            }
        }
        resources = (String[]) app.reloadResources.keySet().toArray(new String[0]);
        for (int i = 0; i < resources.length; i++) {
            File resource = new File(resources[i]);
            if (log.isDebugEnabled())
                log.debug("Checking context[" + app.name + "] reload resource " + resource);
            long lastModified = ((Long) app.reloadResources.get(resources[i])).longValue();
            if ((!resource.exists() && lastModified != 0L) 
                || (resource.lastModified() != lastModified)) {
                // Reload application
                if(log.isInfoEnabled())
                    log.info(sm.getString("hostConfig.reload", app.name));
                Container context = host.findChild(app.name);
                try {
                    ((Lifecycle) context).stop();
                } catch (Exception e) {
                    log.warn(sm.getString
                             ("hostConfig.context.restart", app.name), e);
                }
                // 如果没有启动上下文 (例如在 web.xml中有错误) 还是要试着开始
                try {
                    ((Lifecycle) context).start();
                } catch (Exception e) {
                    log.warn(sm.getString
                             ("hostConfig.context.restart", app.name), e);
                }
                // Update times
                app.reloadResources.put(resources[i], new Long(resource.lastModified()));
                app.timestamp = System.currentTimeMillis();
                return;
            }
        }
    }
    
    
    /**
     * 处理一个"start" 事件
     */
    public void start() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("hostConfig.start"));

        try {
            ObjectName hostON = new ObjectName(host.getObjectName());
            oname = new ObjectName
                (hostON.getDomain() + ":type=Deployer,host=" + host.getName());
            Registry.getRegistry(null, null).registerComponent
                (this, oname, this.getClass().getName());
        } catch (Exception e) {
            log.error(sm.getString("hostConfig.jmx.register", oname), e);
        }

        if (host.getDeployOnStartup())
            deployApps();
    }


    /**
     * 处理一个 "stop"事件
     */
    public void stop() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("hostConfig.stop"));

        undeployApps();

        if (oname != null) {
            try {
                Registry.getRegistry(null, null).unregisterComponent(oname);
            } catch (Exception e) {
                log.error(sm.getString("hostConfig.jmx.unregister", oname), e);
            }
        }
        oname = null;
        appBase = null;
        configBase = null;
    }


    /**
     * 卸载所有已部署的应用程序.
     */
    protected void undeployApps() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("hostConfig.undeploying"));

        // 卸载所有已经部署的上下文
        DeployedApplication[] apps = 
            (DeployedApplication[]) deployed.values().toArray(new DeployedApplication[0]);
        for (int i = 0; i < apps.length; i++) {
            host.removeChild(host.findChild(apps[i].name));
        }
        deployed.clear();
    }


    /**
     * 检查所有应用程序状态.
     */
    protected void check() {

        if (host.getAutoDeploy()) {
            // 检查资源修改以触发重新部署
            DeployedApplication[] apps = 
                (DeployedApplication[]) deployed.values().toArray(new DeployedApplication[0]);
            for (int i = 0; i < apps.length; i++) {
                if (!isServiced(apps[i].name))
                    checkResources(apps[i]);
            }
            // 热部署应用
            deployApps();
        }
    }

    
    /**
     * 检查一个特定的应用现状, 用于管理应用程序的东西.
     */
    public void check(String name) {
        DeployedApplication app = (DeployedApplication) deployed.get(name);
        if (app != null) {
            checkResources(app);
        } else {
            deployApps(name);
        }
    }

    /**
     * 添加一个由我们管理的新上下文.
     * 管理应用的入口点, 和其他JMX Context 控制器.
     */
    public void manageApp(Context context)  {    

        String contextPath = context.getPath();
        
        if (deployed.containsKey(contextPath))
            return;

        DeployedApplication deployedApp = new DeployedApplication(contextPath);
        
        // 添加相关docBase 到部署的列表，如果它是一个 WAR
        boolean isWar = false;
        if (context.getDocBase() != null) {
            File docBase = new File(context.getDocBase());
            if (!docBase.isAbsolute()) {
                docBase = new File(appBase(), context.getDocBase());
            }
            deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                                          new Long(docBase.lastModified()));
            if (docBase.getAbsolutePath().toLowerCase().endsWith(".war")) {
                isWar = true;
            }
        }
        host.addChild(context);
        // 添加解压后的 WAR 和所有受监视的资源
        if (isWar && unpackWARs) {
            String name = null;
            String path = context.getPath();
            if (path.equals("")) {
                name = "ROOT";
            } else {
                if (path.startsWith("/")) {
                    name = path.substring(1);
                } else {
                    name = path;
                }
            }
            File docBase = new File(name);
            if (!docBase.isAbsolute()) {
                docBase = new File(appBase(), name);
            }
            deployedApp.redeployResources.put(docBase.getAbsolutePath(),
                        new Long(docBase.lastModified()));
            addWatchedResources(deployedApp, docBase.getAbsolutePath(), context);
        } else {
            addWatchedResources(deployedApp, null, context);
        }
        deployed.put(contextPath, deployedApp);
    }

    /**
     * 从我们的控制中删除Web应用程序.
     * 管理应用的入口点, 和其他JMX Context 控制器.
     */
    public void unmanageApp(String contextPath) {
        if(isServiced(contextPath)) {
            deployed.remove(contextPath);
            host.removeChild(host.findChild(contextPath));
        }
    }

    // ----------------------------------------------------- Instance Variables


    /**
     * 此类表示已部署应用程序的状态, 以及监控资源.
     */
    protected class DeployedApplication {
    	public DeployedApplication(String name) {
    		this.name = name;
    	}
    	
    	/**
    	 * 应用上下文路径. 断言是(host.getChild(name) != null).
    	 */
    	public String name;
    	
    	/**
    	 * 对指定（静态）资源的任何修改都将导致应用程序的重新部署. 如果指定的任何资源被移除, 应用将被卸载.
    	 * 通常包含类似context.xml文件的资源, 一个压缩后的 WAR 路径.
         * 该值是最后一次修改时间.
    	 */
    	public LinkedHashMap redeployResources = new LinkedHashMap();

    	/**
    	 * 对指定（静态）资源的任何修改都会导致应用程序的重新加载. 通常包含类似应用web.xml文件的资源, 但可以配置为包含附加描述符.
         * 该值是最后一次修改时间.
    	 */
    	public HashMap reloadResources = new HashMap();

    	/**
    	 * 应用程序上一次提供服务的时间戳.
    	 */
    	public long timestamp = System.currentTimeMillis();
    }

}
