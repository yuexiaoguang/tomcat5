package org.apache.catalina.realm;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.naming.Context;

import org.apache.catalina.Group;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Realm;
import org.apache.catalina.Role;
import org.apache.catalina.ServerFactory;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.util.StringManager;

/**
 * <p>{@link Realm}实现类，基于一个{@link UserDatabase}实现类变得可用，通过这个Catalina实例的全局JNDI资源配置.
 * 设置<code>resourceName</code>参数为全局JNDI资源名称，为配置的<code>UserDatabase</code>实例.</p>
 */
public class UserDatabaseRealm extends RealmBase {

    // ----------------------------------------------------- Instance Variables

    /**
     * 用于验证用户和相关角色的<code>UserDatabase</code>.
     */
    protected UserDatabase database = null;


    /**
     * 描述信息
     */
    protected final String info =
        "org.apache.catalina.realm.UserDatabaseRealm/1.0";


    /**
     * 描述信息
     */
    protected static final String name = "UserDatabaseRealm";


    /**
     * 将使用的<code>UserDatabase</code>资源的全局JNDI名称.
     */
    protected String resourceName = "UserDatabase";


    /**
     * The string manager for this package.
     */
    private static StringManager sm = StringManager.getManager(Constants.Package);


    // ------------------------------------------------------------- Properties


    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return info;
    }


    /**
     * 返回将使用的<code>UserDatabase</code>资源的全局JNDI名称.
     */
    public String getResourceName() {
        return resourceName;
    }


    /**
     * 设置将使用的<code>UserDatabase</code>资源的全局JNDI名称.
     *
     * @param resourceName 全局 JNDI 名称
     */
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 返回<code>true</code>如果指定的Principal具有指定的安全角色, 在这个 Realm上下文中; 否则返回<code>false</code>.
     * 这个实现返回<code>true</code>, 如果<code>User</code>有这个角色, 或者用户所在的<code>Group</code>有这个角色. 
     *
     * @param principal 要验证角色的Principal
     * @param role 要验证的安全角色
     */
    public boolean hasRole(Principal principal, String role) {
        if( principal instanceof GenericPrincipal) {
            GenericPrincipal gp = (GenericPrincipal)principal;
            if(gp.getUserPrincipal() instanceof User) {
                principal = gp.getUserPrincipal();
            }
        }
        if(! (principal instanceof User) ) {
            //在SSO和混合Realm玩得很好
            return super.hasRole(principal, role);
        }
        if("*".equals(role)) {
            return true;
        } else if(role == null) {
            return false;
        }
        User user = (User)principal;
        Role dbrole = database.findRole(role);
        if(dbrole == null) {
            return false; 
        }
        if(user.isInRole(dbrole)) {
            return true;
        }
        Iterator groups = user.getGroups();
        while(groups.hasNext()) {
            Group group = (Group)groups.next();
            if(group.isInRole(dbrole)) {
                return true;
            }
        }
        return false;
    }
		
    // ------------------------------------------------------ Protected Methods


    /**
     * 返回实现类的名称.
     */
    protected String getName() {
        return (name);
    }


    /**
     * 返回指定用户名的密码.
     */
    protected String getPassword(String username) {
        User user = database.findUser(username);
        if (user == null) {
            return null;
        } 
        return (user.getPassword());
    }


    /**
     * 返回指定用户名的Principal.
     */
    protected Principal getPrincipal(String username) {

        User user = database.findUser(username);
        if(user == null) {
            return null;
        }

        List roles = new ArrayList();
        Iterator uroles = user.getRoles();
        while(uroles.hasNext()) {
            Role role = (Role)uroles.next();
            roles.add(role.getName());
        }
        Iterator groups = user.getGroups();
        while(groups.hasNext()) {
            Group group = (Group)groups.next();
            uroles = user.getRoles();
            while(uroles.hasNext()) {
                Role role = (Role)uroles.next();
                roles.add(role.getName());
            }
        }
        return new GenericPrincipal(this, username, user.getPassword(), roles, user);
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * @exception LifecycleException 如果此组件检测到阻止其启动的致命错误
     */
    public synchronized void start() throws LifecycleException {

        try {
            StandardServer server = (StandardServer) ServerFactory.getServer();
            Context context = server.getGlobalNamingContext();
            database = (UserDatabase) context.lookup(resourceName);
        } catch (Throwable e) {
            containerLog.error(sm.getString("userDatabaseRealm.lookup",
                                            resourceName),
                               e);
            database = null;
        }
        if (database == null) {
            throw new LifecycleException
                (sm.getString("userDatabaseRealm.noDatabase", resourceName));
        }
        // Perform normal superclass initialization
        super.start();
    }


    /**
     * @exception LifecycleException 如果此组件检测到需要报告的致命错误
     */
    public synchronized void stop() throws LifecycleException {

        // Perform normal superclass finalization
        super.stop();

        // 释放对数据库的引用
        database = null;
    }
}
