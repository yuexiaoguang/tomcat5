package org.apache.catalina.deploy;

import java.io.Serializable;

/**
 * 应用程序资源引用的表示, 在部署描述中使用<code>&lt;res-env-refy&gt;</code>元素表示.
 */
public class ContextResourceEnvRef extends ResourceBase implements Serializable {


    // ------------------------------------------------------------- Properties

    /**
     * 这个环境条目允许应用程序部署描述符重写吗?
     */
    private boolean override = true;

    public boolean getOverride() {
        return (this.override);
    }

    public void setOverride(boolean override) {
        this.override = override;
    }
    
    // --------------------------------------------------------- Public Methods

    public String toString() {
        StringBuffer sb = new StringBuffer("ContextResourceEnvRef[");
        sb.append("name=");
        sb.append(getName());
        if (getType() != null) {
            sb.append(", type=");
            sb.append(getType());
        }
        sb.append(", override=");
        sb.append(override);
        sb.append("]");
        return (sb.toString());
    }
}
