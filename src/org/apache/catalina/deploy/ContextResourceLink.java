package org.apache.catalina.deploy;

import java.io.Serializable;


/**
 * Web应用程序资源链接的表示,在部署描述中使用<code>&lt;ResourceLink&gt;</code>元素表示
 */
public class ContextResourceLink extends ResourceBase implements Serializable {

    // ------------------------------------------------------------- Properties

   /**
     * 此资源的全局名称.
     */
    private String global = null;

    public String getGlobal() {
        return (this.global);
    }

    public void setGlobal(String global) {
        this.global = global;
    }

    // --------------------------------------------------------- Public Methods

    public String toString() {
        StringBuffer sb = new StringBuffer("ContextResourceLink[");
        sb.append("name=");
        sb.append(getName());
        if (getType() != null) {
            sb.append(", type=");
            sb.append(getType());
        }
        if (getGlobal() != null) {
            sb.append(", global=");
            sb.append(getGlobal());
        }
        sb.append("]");
        return (sb.toString());
    }
}
