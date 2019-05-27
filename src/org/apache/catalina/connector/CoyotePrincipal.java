package org.apache.catalina.connector;

import java.security.Principal;

/**
 * <strong>java.security.Principal</strong>通用实现类, 用于表示在协议处理程序级别上进行身份验证的主体.
 */
public class CoyotePrincipal implements Principal {

    // ----------------------------------------------------------- Constructors

    public CoyotePrincipal(String name) {
        this.name = name;
    }

    // ------------------------------------------------------------- Properties

    /**
     * 这个Principal代表的用户的用户名.
     */
    protected String name = null;

    public String getName() {
        return (this.name);
    }

    // --------------------------------------------------------- Public Methods

    public String toString() {
        StringBuffer sb = new StringBuffer("CoyotePrincipal[");
        sb.append(this.name);
        sb.append("]");
        return (sb.toString());
    }
}
