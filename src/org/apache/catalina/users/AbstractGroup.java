package org.apache.catalina.users;


import java.util.Iterator;

import org.apache.catalina.Group;
import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;


/**
 * <p>{@link Group}实现类的基类.</p>
 */
public abstract class AbstractGroup implements Group {


    // ----------------------------------------------------- Instance Variables


    /**
     * 这个组的描述信息
     */
    protected String description = null;


    /**
     * 这个组的组名
     */
    protected String groupname = null;


    // ------------------------------------------------------------- Properties


    /**
     * 返回这个组的描述信息.
     */
    public String getDescription() {
        return (this.description);
    }


    /**
     * 设置这个组的描述信息.
     *
     * @param description The new description
     */
    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * 返回这个组的组名, 必须在{@link UserDatabase}范围内唯一.
     */
    public String getGroupname() {
        return (this.groupname);
    }


    /**
     * 设置这个组的组名, 必须在{@link UserDatabase}范围内唯一.
     *
     * @param groupname The new group name
     */
    public void setGroupname(String groupname) {
        this.groupname = groupname;
    }


    /**
     * 返回专门分配给这个组的一组{@link Role}.
     */
    public abstract Iterator getRoles();


    /**
     * 返回定义了这个组的{@link UserDatabase}.
     */
    public abstract UserDatabase getUserDatabase();


    /**
     * 返回一组这个组的成员{@link User}.
     */
    public abstract Iterator getUsers();


    // --------------------------------------------------------- Public Methods


    /**
     * 给这个组添加一个新的{@link Role}.
     *
     * @param role The new role
     */
    public abstract void addRole(Role role);


    /**
     * 指定的{@link Role}是否是这个组的?
     *
     * @param role The role to check
     */
    public abstract boolean isInRole(Role role);


    /**
     * 删除{@link Role}
     *
     * @param role The old role
     */
    public abstract void removeRole(Role role);


    /**
     * 删除这个组的所有{@link Role}
     */
    public abstract void removeRoles();


    // ------------------------------------------------------ Principal Methods


    /**
     * 使principal名称与组名称相同
     */
    public String getName() {
        return (getGroupname());
    }
}
