package org.apache.catalina.users;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Iterator;
import org.apache.catalina.Group;
import org.apache.catalina.Role;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.ObjectCreationFactory;
import org.xml.sax.Attributes;


/**
 * <p>加载所有定义的用户, 组, 和角色到内存数据结构中, 并为其使用指定的XML文件持久存储.</p>
 */
public class MemoryUserDatabase implements UserDatabase {


    private static Log log = LogFactory.getLog(MemoryUserDatabase.class);

    // ----------------------------------------------------------- Constructors


    public MemoryUserDatabase() {
        super();
    }


    /**
     * @param id 此用户数据库的唯一全局标识符
     */
    public MemoryUserDatabase(String id) {
        super();
        this.id = id;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 一组{@link Group}, 使用组名作为key.
     */
    protected HashMap groups = new HashMap();


    /**
     * 此用户数据库的唯一全局标识符.
     */
    protected String id = null;


    /**
     * 将保存持久信息的XML文件的相对(to <code>catalina.base</code>)或绝对路径名.
     */
    protected String pathname = "conf/tomcat-users.xml";


    /**
     * 重命名的时候，保存旧的信息的相对或绝对路径名.
     */
    protected String pathnameOld = pathname + ".old";


    /**
     * 在重命名之前编写新的信息的文件的相对或绝对路径名.
     */
    protected String pathnameNew = pathname + ".new";


    /**
     * 用户数据库是否是只读的.
     */
    protected boolean readonly = false;

    /**
     * 数据库中的一组{@link Role}, 角色名作为key.
     */
    protected HashMap roles = new HashMap();


    /**
     * The string manager for this package.
     */
    private static StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 数据库中的一组{@link User}, 用户名作为key.
     */
    protected HashMap users = new HashMap();


    // ------------------------------------------------------------- Properties


    /**
     * 返回用户数据库中定义的所有 {@link Group}
     */
    public Iterator getGroups() {
        synchronized (groups) {
            return (groups.values().iterator());
        }
    }


    /**
     * 返回此用户数据库的唯一全局标识符.
     */
    public String getId() {
        return (this.id);
    }


    /**
     * 返回永久存储文件的相对或绝对路径名.
     */
    public String getPathname() {
        return (this.pathname);
    }


    /**
     * 设置永久存储文件的相对或绝对路径名.
     *
     * @param pathname The new pathname
     */
    public void setPathname(String pathname) {
        this.pathname = pathname;
        this.pathnameOld = pathname + ".old";
        this.pathnameNew = pathname + ".new";
    }


    /**
     * 用户数据库是否是只读的.
     */
    public boolean getReadonly() {
        return (this.readonly);
    }


    /**
     * 设置用户数据库是否是只读的.
     */
    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }


    /**
     * 返回用户数据库中定义的所有{@link Role}.
     */
    public Iterator getRoles() {
        synchronized (roles) {
            return (roles.values().iterator());
        }
    }


    /**
     * 返回用户数据库中定义的所有{@link User}.
     */
    public Iterator getUsers() {
        synchronized (users) {
            return (users.values().iterator());
        }
    }



    // --------------------------------------------------------- Public Methods


    /**
     * 结束对这个用户数据库的访问.
     *
     * @exception Exception if any exception is thrown during closing
     */
    public void close() throws Exception {
        save();

        synchronized (groups) {
            synchronized (users) {
                users.clear();
                groups.clear();
            }
        }
    }


    /**
     * 创建并返回一个{@link Group}
     *
     * @param groupname 新组的组名 (必须唯一)
     * @param description 这个组的描述
     */
    public Group createGroup(String groupname, String description) {

        MemoryGroup group = new MemoryGroup(this, groupname, description);
        synchronized (groups) {
            groups.put(group.getGroupname(), group);
        }
        return (group);
    }


    /**
     * 创建并返回一个{@link Role}.
     *
     * @param rolename 新组的角色名(必须唯一)
     * @param description 这个组的描述
     */
    public Role createRole(String rolename, String description) {

        MemoryRole role = new MemoryRole(this, rolename, description);
        synchronized (roles) {
            roles.put(role.getRolename(), role);
        }
        return (role);
    }


    /**
     * 创建并返回一个{@link User}
     *
     * @param username 新用户的登录用户名(必须唯一)
     * @param password 新用户的登录密码
     * @param fullName 新用户的全名
     */
    public User createUser(String username, String password,
                           String fullName) {

        MemoryUser user = new MemoryUser(this, username, password, fullName);
        synchronized (users) {
            users.put(user.getUsername(), user);
        }
        return (user);
    }


    /**
     * 返回指定名称的{@link Group};或者<code>null</code>.
     *
     * @param groupname 返回的组的名称
     */
    public Group findGroup(String groupname) {
        synchronized (groups) {
            return ((Group) groups.get(groupname));
        }
    }


    /**
     * 返回指定名称的{@link Role};或者<code>null</code>.
     *
     * @param rolename 要返回的角色的名称
     */
    public Role findRole(String rolename) {
        synchronized (roles) {
            return ((Role) roles.get(rolename));
        }
    }


    /**
     * 返回指定名称的{@link User};或者<code>null</code>.
     *
     * @param username 要返回的用户的名称
     */
    public User findUser(String username) {
        synchronized (users) {
            return ((User) users.get(username));
        }
    }


    /**
     * 初始化对这个用户数据库的访问
     *
     * @exception Exception if any exception is thrown during opening
     */
    public void open() throws Exception {
        synchronized (groups) {
            synchronized (users) {

                // 删除任何以前的组和用户
                users.clear();
                groups.clear();
                roles.clear();

                // 为XML输入文件构造一个读取器(if it exists)
                File file = new File(pathname);
                if (!file.isAbsolute()) {
                    file = new File(System.getProperty("catalina.base"),
                                    pathname);
                }
                if (!file.exists()) {
                    return;
                }
                FileInputStream fis = new FileInputStream(file);

                // 构建一个digester来读取XML输入文件
                Digester digester = new Digester();
                digester.addFactoryCreate
                    ("tomcat-users/group",
                     new MemoryGroupCreationFactory(this));
                digester.addFactoryCreate
                    ("tomcat-users/role",
                     new MemoryRoleCreationFactory(this));
                digester.addFactoryCreate
                    ("tomcat-users/user",
                     new MemoryUserCreationFactory(this));

                // 解析XML输入文件以加载此数据库
                try {
                    digester.parse(fis);
                    fis.close();
                } catch (Exception e) {
                    try {
                        fis.close();
                    } catch (Throwable t) {
                        ;
                    }
                    throw e;
                }
            }
        }
    }


    /**
     * 删除指定的{@link Group}.
     *
     * @param group The group to be removed
     */
    public void removeGroup(Group group) {
        synchronized (groups) {
            Iterator users = getUsers();
            while (users.hasNext()) {
                User user = (User) users.next();
                user.removeGroup(group);
            }
            groups.remove(group.getGroupname());
        }
    }


    /**
     * 删除指定的{@link Role}
     *
     * @param role The role to be removed
     */
    public void removeRole(Role role) {

        synchronized (roles) {
            Iterator groups = getGroups();
            while (groups.hasNext()) {
                Group group = (Group) groups.next();
                group.removeRole(role);
            }
            Iterator users = getUsers();
            while (users.hasNext()) {
                User user = (User) users.next();
                user.removeRole(role);
            }
            roles.remove(role.getRolename());
        }
    }


    /**
     * 删除指定的{@link User}
     *
     * @param user The user to be removed
     */
    public void removeUser(User user) {
        synchronized (users) {
            users.remove(user.getUsername());
        }
    }


    /**
     * 检查将此用户数据库保存到持久存储位置的权限
     */
    public boolean isWriteable() {
        File file = new File(pathname);
        if (!file.isAbsolute()) {
            file = new File(System.getProperty("catalina.base"),
                            pathname);
        }
        File dir = file.getParentFile();
        return dir.exists() && dir.isDirectory() && dir.canWrite();
    }


    /**
     * 将任何更新信息保存到此用户数据库的持久存储位置.
     *
     * @exception Exception if any exception is thrown during saving
     */
    public void save() throws Exception {

        if (getReadonly()) {
            return;
        }

        if (!isWriteable()) {
            log.warn(sm.getString("memoryUserDatabase.notPersistable"));
            return;
        }

        // 将内容写入临时文件
        File fileNew = new File(pathnameNew);
        if (!fileNew.isAbsolute()) {
            fileNew =
                new File(System.getProperty("catalina.base"), pathnameNew);
        }
        PrintWriter writer = null;
        try {

            // Configure our PrintWriter
            FileOutputStream fos = new FileOutputStream(fileNew);
            OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF8");
            writer = new PrintWriter(osw);

            // Print the file prolog
            writer.println("<?xml version='1.0' encoding='utf-8'?>");
            writer.println("<tomcat-users>");

            // Print entries for each defined role, group, and user
            Iterator values = null;
            values = getRoles();
            while (values.hasNext()) {
                writer.print("  ");
                writer.println(values.next());
            }
            values = getGroups();
            while (values.hasNext()) {
                writer.print("  ");
                writer.println(values.next());
            }
            values = getUsers();
            while (values.hasNext()) {
                writer.print("  ");
                writer.println(values.next());
            }

            // Print the file epilog
            writer.println("</tomcat-users>");

            // Check for errors that occurred while printing
            if (writer.checkError()) {
                writer.close();
                fileNew.delete();
                throw new IOException
                    (sm.getString("memoryUserDatabase.writeException",
                                  fileNew.getAbsolutePath()));
            }
            writer.close();
        } catch (IOException e) {
            if (writer != null) {
                writer.close();
            }
            fileNew.delete();
            throw e;
        }

        // 执行所需的重命名, 永久保存这个文件
        File fileOld = new File(pathnameOld);
        if (!fileOld.isAbsolute()) {
            fileOld =
                new File(System.getProperty("catalina.base"), pathnameOld);
        }
        fileOld.delete();
        File fileOrig = new File(pathname);
        if (!fileOrig.isAbsolute()) {
            fileOrig =
                new File(System.getProperty("catalina.base"), pathname);
        }
        if (fileOrig.exists()) {
            fileOld.delete();
            if (!fileOrig.renameTo(fileOld)) {
                throw new IOException
                    (sm.getString("memoryUserDatabase.renameOld",
                                  fileOld.getAbsolutePath()));
            }
        }
        if (!fileNew.renameTo(fileOrig)) {
            if (fileOld.exists()) {
                fileOld.renameTo(fileOrig);
            }
            throw new IOException
                (sm.getString("memoryUserDatabase.renameNew",
                              fileOrig.getAbsolutePath()));
        }
        fileOld.delete();

    }


    public String toString() {
        StringBuffer sb = new StringBuffer("MemoryUserDatabase[id=");
        sb.append(this.id);
        sb.append(",pathname=");
        sb.append(pathname);
        sb.append(",groupCount=");
        sb.append(this.groups.size());
        sb.append(",roleCount=");
        sb.append(this.roles.size());
        sb.append(",userCount=");
        sb.append(this.users.size());
        sb.append("]");
        return (sb.toString());
    }


    // -------------------------------------------------------- Package Methods


    StringManager getStringManager() {
        return (sm);
    }
}



/**
 * 组实例的Digester对象创建工厂
 */
class MemoryGroupCreationFactory implements ObjectCreationFactory {

    public MemoryGroupCreationFactory(MemoryUserDatabase database) {
        this.database = database;
    }

    public Object createObject(Attributes attributes) {
        String groupname = attributes.getValue("groupname");
        if (groupname == null) {
            groupname = attributes.getValue("name");
        }
        String description = attributes.getValue("description");
        String roles = attributes.getValue("roles");
        Group group = database.createGroup(groupname, description);
        if (roles != null) {
            while (roles.length() > 0) {
                String rolename = null;
                int comma = roles.indexOf(',');
                if (comma >= 0) {
                    rolename = roles.substring(0, comma).trim();
                    roles = roles.substring(comma + 1);
                } else {
                    rolename = roles.trim();
                    roles = "";
                }
                if (rolename.length() > 0) {
                    Role role = database.findRole(rolename);
                    if (role == null) {
                        role = database.createRole(rolename, null);
                    }
                    group.addRole(role);
                }
            }
        }
        return (group);
    }

    private MemoryUserDatabase database = null;

    private Digester digester = null;

    public Digester getDigester() {
        return (this.digester);
    }

    public void setDigester(Digester digester) {
        this.digester = digester;
    }
}


/**
 * 角色实例的Digester对象创建工厂
 */
class MemoryRoleCreationFactory implements ObjectCreationFactory {

    public MemoryRoleCreationFactory(MemoryUserDatabase database) {
        this.database = database;
    }

    public Object createObject(Attributes attributes) {
        String rolename = attributes.getValue("rolename");
        if (rolename == null) {
            rolename = attributes.getValue("name");
        }
        String description = attributes.getValue("description");
        Role role = database.createRole(rolename, description);
        return (role);
    }

    private MemoryUserDatabase database = null;

    private Digester digester = null;

    public Digester getDigester() {
        return (this.digester);
    }

    public void setDigester(Digester digester) {
        this.digester = digester;
    }

}


/**
 * 用户实例的Digester对象创建工厂.
 */
class MemoryUserCreationFactory implements ObjectCreationFactory {

    public MemoryUserCreationFactory(MemoryUserDatabase database) {
        this.database = database;
    }

    public Object createObject(Attributes attributes) {
        String username = attributes.getValue("username");
        if (username == null) {
            username = attributes.getValue("name");
        }
        String password = attributes.getValue("password");
        String fullName = attributes.getValue("fullName");
        if (fullName == null) {
            fullName = attributes.getValue("fullname");
        }
        String groups = attributes.getValue("groups");
        String roles = attributes.getValue("roles");
        User user = database.createUser(username, password, fullName);
        if (groups != null) {
            while (groups.length() > 0) {
                String groupname = null;
                int comma = groups.indexOf(',');
                if (comma >= 0) {
                    groupname = groups.substring(0, comma).trim();
                    groups = groups.substring(comma + 1);
                } else {
                    groupname = groups.trim();
                    groups = "";
                }
                if (groupname.length() > 0) {
                    Group group = database.findGroup(groupname);
                    if (group == null) {
                        group = database.createGroup(groupname, null);
                    }
                    user.addGroup(group);
                }
            }
        }
        if (roles != null) {
            while (roles.length() > 0) {
                String rolename = null;
                int comma = roles.indexOf(',');
                if (comma >= 0) {
                    rolename = roles.substring(0, comma).trim();
                    roles = roles.substring(comma + 1);
                } else {
                    rolename = roles.trim();
                    roles = "";
                }
                if (rolename.length() > 0) {
                    Role role = database.findRole(rolename);
                    if (role == null) {
                        role = database.createRole(rolename, null);
                    }
                    user.addRole(role);
                }
            }
        }
        return (user);
    }

    private MemoryUserDatabase database = null;

    private Digester digester = null;

    public Digester getDigester() {
        return (this.digester);
    }

    public void setDigester(Digester digester) {
        this.digester = digester;
    }
}
