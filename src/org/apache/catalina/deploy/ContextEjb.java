package org.apache.catalina.deploy;

import java.io.Serializable;

/**
 * Web应用程序的EJB资源引用的表示, 在部署描述符中使用<code>&lt;ejb-ref&gt;</code>元素表示.
 */
public class ContextEjb extends ResourceBase implements Serializable {

    // ------------------------------------------------------------- Properties

    /**
     * EJB home实现类的名称.
     */
    private String home = null;

    public String getHome() {
        return (this.home);
    }

    public void setHome(String home) {
        this.home = home;
    }


    /**
     * J2EE EJB定义的链接.
     */
    private String link = null;

    public String getLink() {
        return (this.link);
    }

    public void setLink(String link) {
        this.link = link;
    }

    /**
     * EJB远程实现类的名称.
     */
    private String remote = null;

    public String getRemote() {
        return (this.remote);
    }

    public void setRemote(String remote) {
        this.remote = remote;
    }
    
    // --------------------------------------------------------- Public Methods

    public String toString() {
        StringBuffer sb = new StringBuffer("ContextEjb[");
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
        if (remote != null) {
            sb.append(", remote=");
            sb.append(remote);
        }
        if (link != null) {
            sb.append(", link=");
            sb.append(link);
        }
        sb.append("]");
        return (sb.toString());
    }
}
