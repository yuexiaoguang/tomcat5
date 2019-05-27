package org.apache.catalina.mbeans;

import java.util.ArrayList;

import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.RuntimeOperationsException;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.deploy.ContextEnvironment;
import org.apache.catalina.deploy.ContextResource;
import org.apache.catalina.deploy.ContextResourceLink;
import org.apache.catalina.deploy.NamingResources;
import org.apache.commons.modeler.BaseModelMBean;
import org.apache.commons.modeler.ManagedBean;
import org.apache.commons.modeler.Registry;
import org.apache.tomcat.util.compat.JdkCompat;

/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.catalina.core.StandardContext</code> component.</p>
 */
public class StandardContextMBean extends BaseModelMBean {


    // ----------------------------------------------------------- Constructors


    /**
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception RuntimeOperationsException if an IllegalArgumentException
     *  occurs
     */
    public StandardContextMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }
    

    // ----------------------------------------------------- Class Variables


    /**
     * JDK 兼容支持
     */
    private static final JdkCompat jdkCompat = JdkCompat.getJdkCompat();


    // ----------------------------------------------------- Instance Variables
    
    
    /**
     * 管理bean的配置信息注册表.
     */
    protected Registry registry = MBeanUtils.createRegistry();

    /**
     * 描述这个MBean的<code>ManagedBean</code>信息.
     */
    protected ManagedBean managed =
        registry.findManagedBean("StandardContext");

    
    // ------------------------------------------------------------- Attributes

    
    /**
     * 返回与此Web应用程序相关联的命名资源
     */
    private NamingResources getNamingResources() {
        return ((StandardContext)this.resource).getNamingResources();
    }
    
    public void reload() {
        ((StandardContext)this.resource).reload();
    }
    
    
    /**
     * 返回此Web应用程序定义的环境条目的MBean名字集合
     */
    public String[] getEnvironments() {
        ContextEnvironment[] envs = getNamingResources().findEnvironments();
        ArrayList results = new ArrayList();
        for (int i = 0; i < envs.length; i++) {
            try {
                ObjectName oname =
                    MBeanUtils.createObjectName(managed.getDomain(), envs[i]);
                results.add(oname.toString());
            } catch (MalformedObjectNameException e) {
                IllegalArgumentException iae = new IllegalArgumentException
                    ("Cannot create object name for environment " + envs[i]);
                jdkCompat.chainException(iae, e);
                throw iae;
            }
        }
        return ((String[]) results.toArray(new String[results.size()]));
    }
    
    
    /**
     * 返回此Web应用程序定义的资源引用的MBean名字集合.
     */
    public String[] getResources() {
        
        ContextResource[] resources = getNamingResources().findResources();
        ArrayList results = new ArrayList();
        for (int i = 0; i < resources.length; i++) {
            try {
                ObjectName oname =
                    MBeanUtils.createObjectName(managed.getDomain(), resources[i]);
                results.add(oname.toString());
            } catch (MalformedObjectNameException e) {
                IllegalArgumentException iae = new IllegalArgumentException
                    ("Cannot create object name for resource " + resources[i]);
                jdkCompat.chainException(iae, e);
                throw iae;
            }
        }
        return ((String[]) results.toArray(new String[results.size()]));
    }

      
    /**
     * 返回此Web应用程序定义的资源链接的MBean名字集合
     */
    public String[] getResourceLinks() {
        
        ContextResourceLink[] links = getNamingResources().findResourceLinks();
        ArrayList results = new ArrayList();
        for (int i = 0; i < links.length; i++) {
            try {
                ObjectName oname =
                    MBeanUtils.createObjectName(managed.getDomain(), links[i]);
                results.add(oname.toString());
            } catch (MalformedObjectNameException e) {
                IllegalArgumentException iae = new IllegalArgumentException
                    ("Cannot create object name for resource " + links[i]);
                jdkCompat.chainException(iae, e);
                throw iae;
            }
        }
        return ((String[]) results.toArray(new String[results.size()]));
    }


    /**
     * 返回与此Web应用程序相关的命名资源.
     */
    public javax.naming.directory.DirContext getStaticResources() {
        return ((StandardContext)this.resource).getResources();
    }


    public String[] getWelcomeFiles() {
        return ((StandardContext)this.resource).findWelcomeFiles();
    }


    // ------------------------------------------------------------- Operations


    /**
     * 为这个Web应用程序添加一个环境条目.
     *
     * @param envName New environment entry name
     */
    public String addEnvironment(String envName, String type) 
        throws MalformedObjectNameException {

        NamingResources nresources = getNamingResources();
        if (nresources == null) {
            return null;
        }
        ContextEnvironment env = nresources.findEnvironment(envName);
        if (env != null) {
            throw new IllegalArgumentException
                ("Invalid environment name - already exists '" + envName + "'");
        }
        env = new ContextEnvironment();
        env.setName(envName);
        env.setType(type);
        nresources.addEnvironment(env);
        
        // Return the corresponding MBean name
        ManagedBean managed = registry.findManagedBean("ContextEnvironment");
        ObjectName oname =
            MBeanUtils.createObjectName(managed.getDomain(), env);
        return (oname.toString());
    }

    
    /**
     * 为这个Web应用程序添加资源引用.
     *
     * @param resourceName New resource reference name
     */
    public String addResource(String resourceName, String type) 
        throws MalformedObjectNameException {
        
        NamingResources nresources = getNamingResources();
        if (nresources == null) {
            return null;
        }
        ContextResource resource = nresources.findResource(resourceName);
        if (resource != null) {
            throw new IllegalArgumentException
                ("Invalid resource name - already exists'" + resourceName + "'");
        }
        resource = new ContextResource();
        resource.setName(resourceName);
        resource.setType(type);
        nresources.addResource(resource);
        
        // Return the corresponding MBean name
        ManagedBean managed = registry.findManagedBean("ContextResource");
        ObjectName oname =
            MBeanUtils.createObjectName(managed.getDomain(), resource);
        return (oname.toString());
    }

    
    /**
     * 为这个Web应用程序添加一个资源链接.
     *
     * @param resourceLinkName New resource link name
     */
    public String addResourceLink(String resourceLinkName, String global, 
                String name, String type) throws MalformedObjectNameException {
        
        NamingResources nresources = getNamingResources();
        if (nresources == null) {
            return null;
        }
        ContextResourceLink resourceLink = 
                                nresources.findResourceLink(resourceLinkName);
        if (resourceLink != null) {
            throw new IllegalArgumentException
                ("Invalid resource link name - already exists'" + 
                                                        resourceLinkName + "'");
        }
        resourceLink = new ContextResourceLink();
        resourceLink.setGlobal(global);
        resourceLink.setName(resourceLinkName);
        resourceLink.setType(type);
        nresources.addResourceLink(resourceLink);
        
        // Return the corresponding MBean name
        ManagedBean managed = registry.findManagedBean("ContextResourceLink");
        ObjectName oname =
            MBeanUtils.createObjectName(managed.getDomain(), resourceLink);
        return (oname.toString());
    }    
    
    
    /**
     * 删除指定名称的任何环境条目.
     *
     * @param envName Name of the environment entry to remove
     */
    public void removeEnvironment(String envName) {

        NamingResources nresources = getNamingResources();
        if (nresources == null) {
            return;
        }
        ContextEnvironment env = nresources.findEnvironment(envName);
        if (env == null) {
            throw new IllegalArgumentException
                ("Invalid environment name '" + envName + "'");
        }
        nresources.removeEnvironment(envName);
    }
    
    
    /**
     * 删除指定名称的任何资源引用.
     *
     * @param resourceName Name of the resource reference to remove
     */
    public void removeResource(String resourceName) {

        resourceName = ObjectName.unquote(resourceName);
        NamingResources nresources = getNamingResources();
        if (nresources == null) {
            return;
        }
        ContextResource resource = nresources.findResource(resourceName);
        if (resource == null) {
            throw new IllegalArgumentException
                ("Invalid resource name '" + resourceName + "'");
        }
        nresources.removeResource(resourceName);
    }
    
    
    /**
     * 删除指定名称的任何资源链接.
     *
     * @param resourceLinkName Name of the resource reference to remove
     */
    public void removeResourceLink(String resourceLinkName) {

        resourceLinkName = ObjectName.unquote(resourceLinkName);
        NamingResources nresources = getNamingResources();
        if (nresources == null) {
            return;
        }
        ContextResourceLink resource = nresources.findResourceLink(resourceLinkName);
        if (resource == null) {
            throw new IllegalArgumentException
                ("Invalid resource name '" + resourceLinkName + "'");
        }
        nresources.removeResourceLink(resourceLinkName);
    }
}
