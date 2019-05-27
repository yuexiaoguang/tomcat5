package org.apache.catalina;


import java.security.Principal;
import java.util.Iterator;


/**
 * <p>{@link UserDatabase}中的一组{@link User}的抽象实现. 组中的每个成员用户都将获得分配给组的角色</p>
 */
public interface Group extends Principal {

    // ------------------------------------------------------------- Properties

    /**
     * 返回组的描述信息
     */
    public String getDescription();


    /**
     * 设置组的描述信息
     *
     * @param description The new description
     */
    public void setDescription(String description);


    /**
     * 返回组名，在{@link UserDatabase}范围内必须唯一
     */
    public String getGroupname();


    /**
     * 设置组名，在{@link UserDatabase}范围内必须唯一
     *
     * @param groupname 新组名
     */
    public void setGroupname(String groupname);


    /**
     * 返回分配给这个组的角色集合
     */
    public Iterator getRoles();


    /**
     * 返回组的{@link UserDatabase}
     */
    public UserDatabase getUserDatabase();


    /**
     * 返回组中的成员{@link User}集合
     */
    public Iterator getUsers();


    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个新的{@link Role} 给这个组
     *
     * @param role 新角色
     */
    public void addRole(Role role);


    /**
     * 是不是指定给这个组的{@link Role}?
     *
     * @param role The role to check
     */
    public boolean isInRole(Role role);


    /**
     * 移除{@link Role}
     *
     * @param role The old role
     */
    public void removeRole(Role role);


    /**
     * 移除所有{@link Role}
     */
    public void removeRoles();

}
