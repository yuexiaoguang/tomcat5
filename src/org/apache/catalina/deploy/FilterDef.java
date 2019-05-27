package org.apache.catalina.deploy;

import java.util.HashMap;
import java.util.Map;
import java.io.Serializable;

/**
 * Web应用程序过滤器定义的表示, 在部署描述中使用<code>&lt;filter&gt;</code>元素表示
 */
public class FilterDef implements Serializable {


    // ------------------------------------------------------------- Properties


    /**
     * 过滤器的描述
     */
    private String description = null;

    public String getDescription() {
        return (this.description);
    }

    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * 此过滤器的显示名称
     */
    private String displayName = null;

    public String getDisplayName() {
        return (this.displayName);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }


    /**
     * 实现这种过滤器的java类的完全限定名.
     */
    private String filterClass = null;

    public String getFilterClass() {
        return (this.filterClass);
    }

    public void setFilterClass(String filterClass) {
        this.filterClass = filterClass;
    }


    /**
     * 过滤器的名称,在特定Web应用程序定义的过滤器中，它必须是唯一的.
     */
    private String filterName = null;

    public String getFilterName() {
        return (this.filterName);
    }

    public void setFilterName(String filterName) {
        this.filterName = filterName;
    }


    /**
     * 与此过滤器关联的大图标.
     */
    private String largeIcon = null;

    public String getLargeIcon() {
        return (this.largeIcon);
    }

    public void setLargeIcon(String largeIcon) {
        this.largeIcon = largeIcon;
    }


    /**
     * 此过滤器的初始化参数集, 参数名作为key.
     */
    private Map parameters = new HashMap();

    public Map getParameterMap() {
        return (this.parameters);
    }


    /**
     * 与此过滤器相关联的小图标.
     */
    private String smallIcon = null;

    public String getSmallIcon() {
        return (this.smallIcon);
    }

    public void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 向参数集添加初始化参数.
     *
     * @param name The initialization parameter name
     * @param value The initialization parameter value
     */
    public void addInitParameter(String name, String value) {
        parameters.put(name, value);
    }


    public String toString() {
        StringBuffer sb = new StringBuffer("FilterDef[");
        sb.append("filterName=");
        sb.append(this.filterName);
        sb.append(", filterClass=");
        sb.append(this.filterClass);
        sb.append("]");
        return (sb.toString());
    }
}
