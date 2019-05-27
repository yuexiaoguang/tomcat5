package org.apache.catalina.realm;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import org.apache.catalina.Realm;

/**
 * <strong>java.security.Principal</strong>通用实现类，对于<code>Realm</code>实现类是有用的.
 */
public class GenericPrincipal implements Principal {

    // ----------------------------------------------------------- Constructors

    /**
     * @param realm The Realm that owns this Principal
     * @param name 由该Principal表示的用户的用户名
     * @param password 用于验证此用户的密码
     */
    public GenericPrincipal(Realm realm, String name, String password) {
        this(realm, name, password, null);
    }


    /**
     * @param realm The Realm that owns this principal
     * @param name 由该Principal表示的用户的用户名
     * @param password 用于验证此用户的密码
     * @param roles 由本用户拥有的角色列表(必须是 String)
     */
    public GenericPrincipal(Realm realm, String name, String password,
                            List roles) {
        this(realm, name, password, roles, null);
    }

    /**
     * @param realm The Realm that owns this principal
     * @param name 由该Principal表示的用户的用户名
     * @param password 用于验证此用户的密码
     * @param roles 由本用户拥有的角色列表(必须是 String)
     * @param userPrincipal - 调用getUserPrincipal返回的主题; 如果是null, 将会返回
     */
    public GenericPrincipal(Realm realm, String name, String password,
                            List roles, Principal userPrincipal) {

        super();
        this.realm = realm;
        this.name = name;
        this.password = password;
        this.userPrincipal = userPrincipal;
        if (roles != null) {
            this.roles = new String[roles.size()];
            this.roles = (String[]) roles.toArray(this.roles);
            if (this.roles.length > 0)
                Arrays.sort(this.roles);
        }
    }


    // ------------------------------------------------------------- Properties


    /**
     * 由该Principal表示的用户的用户名
     */
    protected String name = null;

    public String getName() {
        return (this.name);
    }


    /**
     * 用于验证此用户的密码.
     */
    protected String password = null;

    public String getPassword() {
        return (this.password);
    }


    /**
     * 关联的Realm.
     */
    protected Realm realm = null;

    public Realm getRealm() {
        return (this.realm);
    }

    void setRealm( Realm realm ) {
        this.realm=realm;
    }


    /**
     * 用户拥有的角色列表.
     */
    protected String roles[] = new String[0];

    public String[] getRoles() {
        return (this.roles);
    }


    /**
     * 要暴露在应用程序中的身份验证Principal.
     */
    protected Principal userPrincipal = null;

    public Principal getUserPrincipal() {
        if (userPrincipal != null) {
            return userPrincipal;
        } else {
            return this;
        }
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 这个Principal表示的用户是否具有指定的角色?
     *
     * @param role 待测角色
     */
    public boolean hasRole(String role) {

        if("*".equals(role)) // Special 2.4 role meaning everyone
            return true;
        if (role == null)
            return (false);
        return (Arrays.binarySearch(roles, role) >= 0);

    }

    public String toString() {
        StringBuffer sb = new StringBuffer("GenericPrincipal[");
        sb.append(this.name);
        sb.append("(");
        for( int i=0;i<roles.length; i++ ) {
            sb.append( roles[i]).append(",");
        }
        sb.append(")]");
        return (sb.toString());
    }
}
