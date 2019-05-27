package org.apache.catalina.users;


import java.util.ArrayList;
import java.util.Iterator;

import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;


/**
 * <p>Concrete implementation of {@link org.apache.catalina.Group} for the
 * {@link MemoryUserDatabase} implementation of {@link UserDatabase}.</p>
 */
public class MemoryGroup extends AbstractGroup {


    // ----------------------------------------------------------- Constructors


    /**
     * @param database 拥有这个组的{@link MemoryUserDatabase}
     * @param groupname 这个组的组名
     * @param description 这个组的描述
     */
    MemoryGroup(MemoryUserDatabase database, String groupname, String description) {
        super();
        this.database = database;
        setGroupname(groupname);
        setDescription(description);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 这个组所属的{@link MemoryUserDatabase}.
     */
    protected MemoryUserDatabase database = null;


    /**
     * 关联的一组{@link Role}.
     */
    protected ArrayList roles = new ArrayList();


    // ------------------------------------------------------------- Properties


    /**
     * 返回这个组拥有的一组{@link Role}.
     */
    public Iterator getRoles() {
        synchronized (roles) {
            return (roles.iterator());
        }
    }


    /**
     * 返回定义这个Group的{@link UserDatabase}.
     */
    public UserDatabase getUserDatabase() {
        return (this.database);
    }


    /**
     * 返回这个组包含的一组{@link User}.
     */
    public Iterator getUsers() {
        ArrayList results = new ArrayList();
        Iterator users = database.getUsers();
        while (users.hasNext()) {
            MemoryUser user = (MemoryUser) users.next();
            if (user.isInGroup(this)) {
                results.add(user);
            }
        }
        return (results.iterator());
    }


    // --------------------------------------------------------- Public Methods


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
     * 这个组是否拥有指定的{@link Role}?
     *
     * @param role The role to check
     */
    public boolean isInRole(Role role) {
        synchronized (roles) {
            return (roles.contains(role));
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
        StringBuffer sb = new StringBuffer("<group groupname=\"");
        sb.append(groupname);
        sb.append("\"");
        if (description != null) {
            sb.append(" description=\"");
            sb.append(description);
            sb.append("\"");
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
                    sb.append((String) ((Role) values.next()).getRolename());
                }
                sb.append("\"");
            }
        }
        sb.append("/>");
        return (sb.toString());
    }
}
