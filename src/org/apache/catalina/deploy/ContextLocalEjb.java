package org.apache.catalina.deploy;

import java.io.Serializable;


/**
 * Web应用程序的本地EJB资源引用的表示, 在部署描述中使用<code>&lt;ejb-local-ref&gt;</code>元素表示.
 */
public class ContextLocalEjb extends ResourceBase implements Serializable {


    // ------------------------------------------------------------- Properties

    /**
     * EJB home实现类的名称
     */
    private String home = null;

    public String getHome() {
        return (this.home);
    }

    public void setHome(String home) {
        this.home = home;
    }


    /**
     * J2EE EJB 定义的链接.
     */
    private String link = null;

    public String getLink() {
        return (this.link);
    }

    public void setLink(String link) {
        this.link = link;
    }


    /**
     * EJB本地实现类的名称
     */
    private String local = null;

    public String getLocal() {
        return (this.local);
    }

    public void setLocal(String local) {
        this.local = local;
    }

    
    // --------------------------------------------------------- Public Methods


    public String toString() {
        StringBuffer sb = new StringBuffer("ContextLocalEjb[");
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
        if (home != null) {
            sb.append(", home=");
            sb.append(home);
        }
        if (link != null) {
            sb.append(", link=");
            sb.append(link);
        }
        if (local != null) {
            sb.append(", local=");
            sb.append(local);
        }
        sb.append("]");
        return (sb.toString());
    }
}
