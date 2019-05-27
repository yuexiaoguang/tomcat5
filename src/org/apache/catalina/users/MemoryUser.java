package org.apache.catalina.users;


import java.util.ArrayList;
import java.util.Iterator;

import org.apache.catalina.Group;
import org.apache.catalina.Role;
import org.apache.catalina.UserDatabase;
import org.apache.catalina.util.RequestUtil;

/**
 * <p>Concrete implementation of {@link org.apache.catalina.User} for the
 * {@link MemoryUserDatabase} implementation of {@link UserDatabase}.</p>
 */
public class MemoryUser extends AbstractUser {


    // ----------------------------------------------------------- Constructors


    /**
     * @param database 拥有这个用户的{@link MemoryUserDatabase}
     * @param username 新用户的用户名
     * @param password 新用户的登录密码
     * @param fullName 新用户的全名
     */
    MemoryUser(MemoryUserDatabase database, String username, String password, String fullName) {
        super();
        this.database = database;
        setUsername(username);
        setPassword(password);
        setFullName(fullName);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 拥有这个用户的{@link MemoryUserDatabase}.
     */
    protected MemoryUserDatabase database = null;


    /**
     * 包含这个用户的一组{@link Group}.
     */
    protected ArrayList groups = new ArrayList();


    /**
     * 用户拥有的一组{@link Role}.
     */
    protected ArrayList roles = new ArrayList();


    // ------------------------------------------------------------- Properties


    /**
     * 返回包含这个用户的一组{@link Group}.
     */
    public Iterator getGroups() {
        synchronized (groups) {
            return (groups.iterator());
        }
    }


    /**
     * 返回用户拥有的所有{@link Role}.
     */
    public Iterator getRoles() {
        synchronized (roles) {
            return (roles.iterator());
        }
    }


    /**
     * 返回定义这个用户的{@link UserDatabase}.
     */
    public UserDatabase getUserDatabase() {
        return (this.database);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个{@link Group}.
     *
     * @param group The new group
     */
    public void addGroup(Group group) {
        synchronized (groups) {
            if (!groups.contains(group)) {
                groups.add(group);
            }
        }
    }


    /**
     * 添加一个{@link Role}.
     *
     * @param role The new role
     */
    public void addRole(Role role) {
        synchronized (roles) {
            if (!roles.contains(role)) {
                roles.add(role);
            }
        }
    }


    /**
     * 是否包含此用户?
     *
     * @param group The group to check
     */
    public boolean isInGroup(Group group) {
        synchronized (groups) {
            return (groups.contains(group));
        }
    }


    /**
     * 这个用户是否拥有指定的{@link Role}?
     * 这个方法不检查从{@link Group} 继承下来的角色.
     *
     * @param role The role to check
     */
    public boolean isInRole(Role role) {
        synchronized (roles) {
            return (roles.contains(role));
        }
    }


    /**
     * 删除{@link Group}.
     *
     * @param group The old group
     */
    public void removeGroup(Group group) {
        synchronized (groups) {
            groups.remove(group);
        }
    }


    /**
     * 删除所有的{@link Group}
     */
    public void removeGroups() {
        synchronized (groups) {
            groups.clear();
        }
    }


    /**
     * 删除一个{@link Role}.
     *
     * @param role The old role
     */
    public void removeRole(Role role) {
        synchronized (roles) {
            roles.remove(role);
        }
    }


    /**
     * 删除所有的{@link Role}.
     */
    public void removeRoles() {
        synchronized (roles) {
            roles.clear();
        }
    }


    public String toString() {
        StringBuffer sb = new StringBuffer("<user username=\"");
        sb.append(RequestUtil.filter(username));
        sb.append("\" password=\"");
        sb.append(RequestUtil.filter(password));
        sb.append("\"");
        if (fullName != null) {
            sb.append(" fullName=\"");
            sb.append(RequestUtil.filter(fullName));
            sb.append("\"");
        }
        synchronized (groups) {
            if (groups.size() > 0) {
                sb.append(" groups=\"");
                int n = 0;
                Iterator values = groups.iterator();
                while (values.hasNext()) {
                    if (n > 0) {
                        sb.append(',');
                    }
                    n++;
                    sb.append(RequestUtil.filter(((Group) values.next()).getGroupname()));
                }
                sb.append("\"");
            }
        }
        synchronized (roles) {
            if (roles.size() > 0) {
                sb.append(" roles=\"");
                int n = 0;
                Iterator values = roles.iterator();
                while (values.hasNext()) {
                    if (n > 0) {
                        sb.append(',');
                    }
                    n++;
                    sb.append(RequestUtil.filter(((Role) values.next()).getRolename()));
                }
                sb.append("\"");
            }
        }
        sb.append("/>");
        return (sb.toString());
    }
}
