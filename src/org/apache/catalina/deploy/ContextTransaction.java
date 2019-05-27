package org.apache.catalina.deploy;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;

/**
 * 应用程序资源引用的表示, 在部署描述中使用 <code>&lt;res-env-refy&gt;</code>元素表示.
 */
public class ContextTransaction implements Serializable {

    // ------------------------------------------------------------- Properties

    /**
     * 配置的属性
     */
    private HashMap properties = new HashMap();

    /**
     * 返回配置的属性.
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * 设置配置的属性.
     */
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /** 
     * 删除配置的属性.
     */
    public void removeProperty(String name) {
        properties.remove(name);
    }

    /**
     * 列表属性.
     */
    public Iterator listProperties() {
        return properties.keySet().iterator();
    }
    
    
    // --------------------------------------------------------- Public Methods

    public String toString() {
        StringBuffer sb = new StringBuffer("Transaction[");
        sb.append("]");
        return (sb.toString());
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
