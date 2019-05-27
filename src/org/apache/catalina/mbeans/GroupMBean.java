package org.apache.catalina.mbeans;


import java.util.ArrayList;
import java.util.Iterator;

import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.RuntimeOperationsException;

import org.apache.catalina.Group;
import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.commons.modeler.BaseModelMBean;
import org.apache.commons.modeler.ManagedBean;
import org.apache.commons.modeler.Registry;
import org.apache.tomcat.util.compat.JdkCompat;

/**
 * <p>A <strong>ModelMBean</strong> implementation for the
 * <code>org.apache.catalina.Group</code> component.</p>
 */
public class GroupMBean extends BaseModelMBean {

    // ----------------------------------------------------------- Constructors

    /**
     * @exception MBeanException 如果对象的初始化器抛出异常
     * @exception RuntimeOperationsException 如果发生IllegalArgumentException
     */
    public GroupMBean() throws MBeanException, RuntimeOperationsException {
        super();
    }


    // ----------------------------------------------------- Class Variables


    /**
     * JDK 兼容支持
     */
    private static final JdkCompat jdkCompat = JdkCompat.getJdkCompat();


    // ----------------------------------------------------- Instance Variables


    /**
     * 管理bean的配置信息注册表.
     */
    protected Registry registry = MBeanUtils.createRegistry();


    /**
     * 注册的<code>MBeanServer</code>.
     */
    protected MBeanServer mserver = MBeanUtils.createServer();


    /**
     * 描述这个MBean的<code>ManagedBean</code>.
     */
    protected ManagedBean managed = registry.findManagedBean("Group");


    // ------------------------------------------------------------- Attributes


    /**
     * 返回这个组的所有授权角色的MBean的名字.
     */
    public String[] getRoles() {

        Group group = (Group) this.resource;
        ArrayList results = new ArrayList();
        Iterator roles = group.getRoles();
        while (roles.hasNext()) {
            Role role = null;
            try {
                role = (Role) roles.next();
                ObjectName oname =
                    MBeanUtils.createObjectName(managed.getDomain(), role);
                results.add(oname.toString());
            } catch (MalformedObjectNameException e) {
                IllegalArgumentException iae = new IllegalArgumentException
                    ("Cannot create object name for role " + role);
                jdkCompat.chainException(iae, e);
                throw iae;
            }
        }
        return ((String[]) results.toArray(new String[results.size()]));
    }


    /**
     * 返回这个组所有成员用户的MBean名称.
     */
    public String[] getUsers() {

        Group group = (Group) this.resource;
        ArrayList results = new ArrayList();
        Iterator users = group.getUsers();
        while (users.hasNext()) {
            User user = null;
            try {
                user = (User) users.next();
                ObjectName oname =
                    MBeanUtils.createObjectName(managed.getDomain(), user);
                results.add(oname.toString());
            } catch (MalformedObjectNameException e) {
                IllegalArgumentException iae = new IllegalArgumentException
                    ("Cannot create object name for user " + user);
                jdkCompat.chainException(iae, e);
                throw iae;
            }
        }
        return ((String[]) results.toArray(new String[results.size()]));
    }


    // ------------------------------------------------------------- Operations


    /**
     * 添加一个新的{@link Role}到组中.
     *
     * @param rolename Role name of the new role
     */
    public void addRole(String rolename) {
        Group group = (Group) this.resource;
        if (group == null) {
            return;
        }
        Role role = group.getUserDatabase().findRole(rolename);
        if (role == null) {
            throw new IllegalArgumentException
                ("Invalid role name '" + rolename + "'");
        }
        group.addRole(role);
    }


    /**
     * 从这个组中移除一个{@link Role}
     *
     * @param rolename Role name of the old role
     */
    public void removeRole(String rolename) {
        Group group = (Group) this.resource;
        if (group == null) {
            return;
        }
        Role role = group.getUserDatabase().findRole(rolename);
        if (role == null) {
            throw new IllegalArgumentException
                ("Invalid role name '" + rolename + "'");
        }
        group.removeRole(role);
    }
}
