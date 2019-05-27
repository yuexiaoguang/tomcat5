package org.apache.catalina.realm;


import java.security.Principal;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.apache.catalina.Container;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.util.digester.Digester;


/**
 * <b>Realm</b>实现类， 读取XML文件以配置有效用户、密码和角色.
 * 文件格式（和默认文件位置）与当前由Tomcat 3支持的文件格式相同.
 * <p>
 * <strong>实现注意</strong>: 假设在应用程序启动时初始化定义的用户（及其角色）的内存集合，再也不会修改.
 * 因此，在访问主体集合时不执行线程同步.
 */
public class MemoryRealm  extends RealmBase {

    private static Log log = LogFactory.getLog(MemoryRealm.class);

    // ----------------------------------------------------- Instance Variables


    /**
     * 关联的Container.
     */
    private Container container = null;


    /**
     * 用于处理内存数据库文件的Digester.
     */
    private static Digester digester = null;


    /**
     * 描述信息
     */
    protected final String info =
        "org.apache.catalina.realm.MemoryRealm/1.0";


    /**
     * 描述信息
     */
    protected static final String name = "MemoryRealm";


    /**
     * 包含数据库信息的XML文件的路径(绝对路径，或者相对于Catalina的当前工作路径)
     */
    private String pathname = "conf/tomcat-users.xml";


    /**
     * 有效Principals集合, 使用用户名作为key.
     */
    private Map principals = new HashMap();


    /**
     * The string manager for this package.
     */
    private static StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 是否已启动?
     */
    private boolean started = false;


    // ------------------------------------------------------------- Properties


    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return info;
    }


    /**
     * 返回包含用户定义的XML文件的路径名.
     */
    public String getPathname() {
        return pathname;
    }


    /**
     * 设置包含用户定义的XML文件的路径名.
     * 如果指定相对路径, 将违反"catalina.base".
     *
     * @param pathname The new pathname
     */
    public void setPathname(String pathname) {
        this.pathname = pathname;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 返回指定用户名和凭据的Principal; 或者<code>null</code>.
     *
     * @param username 要查找Principal的用户名
     * @param credentials 验证这个用户名的Password或其它凭据
     */
    public Principal authenticate(String username, String credentials) {

        GenericPrincipal principal =
            (GenericPrincipal) principals.get(username);

        boolean validated = false;
        if (principal != null) {
            if (hasMessageDigest()) {
                // Hex hashes 不区分大小写
                validated = (digest(credentials)
                             .equalsIgnoreCase(principal.getPassword()));
            } else {
                validated =
                    (digest(credentials).equals(principal.getPassword()));
            }
        }

        if (validated) {
            if (log.isDebugEnabled())
                log.debug(sm.getString("memoryRealm.authenticateSuccess", username));
            return (principal);
        } else {
            if (log.isDebugEnabled())
                log.debug(sm.getString("memoryRealm.authenticateFailure", username));
            return (null);
        }
    }


    // -------------------------------------------------------- Package Methods


    /**
     * 在内存数据库中添加一个新用户.
     *
     * @param username User's username
     * @param password User's password (clear text)
     * @param roles 与此用户关联的逗号分隔的角色集
     */
    void addUser(String username, String password, String roles) {

        // 为这个用户累加角色列表
        ArrayList list = new ArrayList();
        roles += ",";
        while (true) {
            int comma = roles.indexOf(',');
            if (comma < 0)
                break;
            String role = roles.substring(0, comma).trim();
            list.add(role);
            roles = roles.substring(comma + 1);
        }

        // 创建并缓存Principal
        GenericPrincipal principal =
            new GenericPrincipal(this, username, password, list);
        principals.put(username, principal);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 返回一个配置的<code>Digester</code>用于处理XML输入文件, 如果必要的话，创建一个新的.
     */
    protected synchronized Digester getDigester() {
        if (digester == null) {
            digester = new Digester();
            digester.setValidating(false);
            digester.addRuleSet(new MemoryRuleSet());
        }
        return (digester);
    }


    /**
     * 返回实现类名称.
     */
    protected String getName() {
        return (name);
    }


    /**
     * 返回指定用户名关联的密码.
     */
    protected String getPassword(String username) {
        GenericPrincipal principal =
            (GenericPrincipal) principals.get(username);
        if (principal != null) {
            return (principal.getPassword());
        } else {
            return (null);
        }
    }


    /**
     * 返回指定用户名关联的Principal.
     */
    protected Principal getPrincipal(String username) {
        return (Principal) principals.get(username);
    }

    /**
     * 返回这个realm的所有主体.
     *
     * @return The principals, 使用用户名作为key(a String)
     */
    protected Map getPrincipals() {
        return principals;
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * @exception LifecycleException 如果此组件检测到阻止其启动的致命错误
     */
    public synchronized void start() throws LifecycleException {

        // Validate the existence of our database file
        File file = new File(pathname);
        if (!file.isAbsolute())
            file = new File(System.getProperty("catalina.base"), pathname);
        if (!file.exists() || !file.canRead())
            throw new LifecycleException
                (sm.getString("memoryRealm.loadExist",
                              file.getAbsolutePath()));

        // Load the contents of the database file
        if (log.isDebugEnabled())
            log.debug(sm.getString("memoryRealm.loadPath",
                             file.getAbsolutePath()));
        Digester digester = getDigester();
        try {
            synchronized (digester) {
                digester.push(this);
                digester.parse(file);
            }
        } catch (Exception e) {
            throw new LifecycleException("memoryRealm.readXml", e);
        } finally {
            digester.reset();
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
    }
}
