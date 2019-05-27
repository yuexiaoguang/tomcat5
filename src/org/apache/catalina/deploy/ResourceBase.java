package org.apache.catalina.deploy;

import java.io.Serializable;
import java.util.Iterator;
import java.util.HashMap;

/**
 * 上下文元素表示
 */
public class ResourceBase implements Serializable {

    // ------------------------------------------------------------- Properties

    /**
     * 上下文元素描述.
     */
    private String description = null;

    public String getDescription() {
        return (this.description);
    }

    public void setDescription(String description) {
        this.description = description;
    }



    /**
     * 上下文元素名称.
     */
    private String name = null;

    public String getName() {
        return (this.name);
    }

    public void setName(String name) {
        this.name = name;
    }


    /**
     * EJB bean 实现类的名称.
     */
    private String type = null;

    public String getType() {
        return (this.type);
    }

    public void setType(String type) {
        this.type = type;
    }


    /**
     * 保存配置属性.
     */
    private HashMap properties = new HashMap();

    /**
     * 返回一个配置属性.
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * 设置一个配置属性.
     */
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /** 
     * 删除一个配置属性.
     */
    public void removeProperty(String name) {
        properties.remove(name);
    }

    /**
     * 列出属性.
     */
    public Iterator listProperties() {
        return properties.keySet().iterator();
    }
    
    // -------------------------------------------------------- Package Methods

    /**
     * 关联的NamingResource.
     */
    protected NamingResources resources = null;

    public NamingResources getNamingResources() {
        return (this.resources);
    }

    void setNamingResources(NamingResources resources) {
        this.resources = resources;
    }
}
