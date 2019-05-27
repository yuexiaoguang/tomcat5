package org.apache.catalina.deploy;

import java.io.Serializable;

/**
 * Web应用程序资源引用的表示, 在部署描述中使用<code>&lt;resource-ref&gt;</code>元素表示
 */
public class ContextResource extends ResourceBase implements Serializable {


    // ------------------------------------------------------------- Properties


    /**
     * 此资源的授权要求
     * (<code>Application</code>或<code>Container</code>).
     */
    private String auth = null;

    public String getAuth() {
        return (this.auth);
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    /**
     * 资源工厂的共享范围 (<code>Shareable</code>或<code>Unshareable</code>).
     */
    private String scope = "Shareable";

    public String getScope() {
        return (this.scope);
    }

    public void setScope(String scope) {
        this.scope = scope;
    }


    // --------------------------------------------------------- Public Methods


    public String toString() {
        StringBuffer sb = new StringBuffer("ContextResource[");
        sb.append("name=");
        sb.append(getName());
        if (getDescription() != null) {
            sb.append(", description=");
            sb.append(getDescription());
        }
        if (getType() != null) {
            sb.append(", type=");
            sb.append(getType());
        }
        if (auth != null) {
            sb.append(", auth=");
            sb.append(auth);
        }
        if (scope != null) {
            sb.append(", scope=");
            sb.append(scope);
        }
        sb.append("]");
        return (sb.toString());
    }
}
