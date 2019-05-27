package org.apache.catalina.users;


import org.apache.catalina.Role;
import org.apache.catalina.UserDatabase;


/**
 * <p>{@link Role}实现类的基类.</p>
 */
public abstract class AbstractRole implements Role {


    // ----------------------------------------------------- Instance Variables


    /**
     * 这个Role的描述信息.
     */
    protected String description = null;


    /**
     * 这个Role的角色名
     */
    protected String rolename = null;


    // ------------------------------------------------------------- Properties


    /**
     * 返回这个Role的描述信息.
     */
    public String getDescription() {
        return (this.description);
    }


    /**
     * 设置这个Role的描述信息.
     *
     * @param description The new description
     */
    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * 返回这个Role的角色名, 在{@link UserDatabase}范围内必须唯一.
     */
    public String getRolename() {
        return (this.rolename);
    }


    /**
     * 设置这个Role的角色名, 在{@link UserDatabase}范围内必须唯一.
     *
     * @param rolename The new role name
     */
    public void setRolename(String rolename) {
        this.rolename = rolename;
    }


    /**
     * 返回定义这个Role的{@link UserDatabase}.
     */
    public abstract UserDatabase getUserDatabase();


    // ------------------------------------------------------ Principal Methods


    /**
     * 让principal的名字和角色名字相同.
     */
    public String getName() {
        return (getRolename());
    }
}
