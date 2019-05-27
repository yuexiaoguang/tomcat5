package org.apache.catalina.deploy;

import java.io.Serializable;

/**
 * 在服务器配置文件中配置的上下文初始化参数的表示, 而不是应用程序部署描述符.
 * 这对于建立默认值是很方便的 (可以配置为允许应用程序重写)不必修改应用程序部署描述符本身.
 */
public class ApplicationParameter implements Serializable {

    // ------------------------------------------------------------- Properties

    /**
     * 此环境项的描述.
     */
    private String description = null;

    public String getDescription() {
        return (this.description);
    }

    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * 此应用程序参数的名称.
     */
    private String name = null;

    public String getName() {
        return (this.name);
    }

    public void setName(String name) {
        this.name = name;
    }


    /**
     * 这个应用程序参数允许应用程序部署描述符重写吗?
     */
    private boolean override = true;

    public boolean getOverride() {
        return (this.override);
    }

    public void setOverride(boolean override) {
        this.override = override;
    }


    /**
     * 此应用程序参数的值
     */
    private String value = null;

    public String getValue() {
        return (this.value);
    }

    public void setValue(String value) {
        this.value = value;
    }

    // --------------------------------------------------------- Public Methods


    public String toString() {
        StringBuffer sb = new StringBuffer("ApplicationParameter[");
        sb.append("name=");
        sb.append(name);
        if (description != null) {
            sb.append(", description=");
            sb.append(description);
        }
        sb.append(", value=");
        sb.append(value);
        sb.append(", override=");
        sb.append(override);
        sb.append("]");
        return (sb.toString());
    }
}
