package org.apache.catalina.users;


import org.apache.catalina.UserDatabase;


/**
 * <p>Concrete implementation of {@link org.apache.catalina.Role} for the
 * {@link MemoryUserDatabase} implementation of {@link UserDatabase}.</p>
 */
public class MemoryRole extends AbstractRole {

    // ----------------------------------------------------------- Constructors

    /**
     * @param database 拥有这个角色的{@link MemoryUserDatabase}
     * @param rolename 角色名
     * @param description 角色的描述
     */
    MemoryRole(MemoryUserDatabase database, String rolename, String description) {
        super();
        this.database = database;
        setRolename(rolename);
        setDescription(description);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 拥有这个角色的{@link MemoryUserDatabase}
     */
    protected MemoryUserDatabase database = null;


    // ------------------------------------------------------------- Properties


    /**
     * 返回定义这个角色的{@link UserDatabase}
     */
    public UserDatabase getUserDatabase() {
        return (this.database);
    }


    // --------------------------------------------------------- Public Methods


    public String toString() {
        StringBuffer sb = new StringBuffer("<role rolename=\"");
        sb.append(rolename);
        sb.append("\"");
        if (description != null) {
            sb.append(" description=\"");
            sb.append(description);
            sb.append("\"");
        }
        sb.append("/>");
        return (sb.toString());
    }
}
