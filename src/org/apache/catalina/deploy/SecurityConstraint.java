package org.apache.catalina.deploy;

import java.io.Serializable;

/**
 * Web应用程序的安全约束元素的表示,在部署描述中使用<code>&lt;security-constraint&gt;</code>元素表示
 * <p>
 * <b>WARNING</b>: 假定该类的实例只在单个线程的上下文中创建和修改, 在实例对其余的应用程序可见之前. 
 * 之后，只需要读取访问权限. 因此，这个类中的任何读写访问都不同步.
 */
public class SecurityConstraint implements Serializable {


    // ----------------------------------------------------------- Constructors

    public SecurityConstraint() {
        super();
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 安全约束的授权约束中包含了“所有角色”通配符吗?
     */
    private boolean allRoles = false;


    /**
     * 是包含在这个安全约束中的授权约束?
     * 这是要区分的情况下，没有角色授权约束要求(表示根本没有直接访问), 与缺乏权威性约束，这意味着没有访问控制检查.
     */
    private boolean authConstraint = false;


    /**
     * 允许访问受此安全约束保护的资源的角色集.
     */
    private String authRoles[] = new String[0];


    /**
     * 受此安全约束保护的Web资源集合集.
     */
    private SecurityCollection collections[] = new SecurityCollection[0];


    /**
     * 此安全约束的显示名称.
     */
    private String displayName = null;


    /**
     * 此安全约束的用户数据约束.
     * 必须是 NONE, INTEGRAL, CONFIDENTIAL.
     */
    private String userConstraint = "NONE";


    // ------------------------------------------------------------- Properties


    /**
     * 是这个身份验证约束中包含的“所有角色”通配符吗?
     */
    public boolean getAllRoles() {
        return (this.allRoles);
    }


    /**
     * 返回此安全约束的授权约束当前标志.
     */
    public boolean getAuthConstraint() {
        return (this.authConstraint);
    }


    /**
     * 设置此安全约束的授权约束当前标志.
     */
    public void setAuthConstraint(boolean authConstraint) {
        this.authConstraint = authConstraint;
    }


    /**
     * 返回此安全约束的显示名称.
     */
    public String getDisplayName() {
        return (this.displayName);
    }


    /**
     * 设置此安全约束的显示名称
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }


    /**
     * 返回此安全约束的用户数据约束
     */
    public String getUserConstraint() {
        return (userConstraint);
    }


    /**
     * 设置此安全约束的用户数据约束
     *
     * @param userConstraint The new user data constraint
     */
    public void setUserConstraint(String userConstraint) {
        if (userConstraint != null)
            this.userConstraint = userConstraint;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加授权角色, 这是一个角色名称，允许访问受此安全约束保护的资源.
     *
     * @param authRole Role name to be added
     */
    public void addAuthRole(String authRole) {

        if (authRole == null)
            return;
        if ("*".equals(authRole)) {
            allRoles = true;
            return;
        }
        String results[] = new String[authRoles.length + 1];
        for (int i = 0; i < authRoles.length; i++)
            results[i] = authRoles[i];
        results[authRoles.length] = authRole;
        authRoles = results;
        authConstraint = true;

    }


    /**
     * 将新的Web资源集合添加到受此安全约束保护的那些Web资源集合中.
     *
     * @param collection The new web resource collection
     */
    public void addCollection(SecurityCollection collection) {
        if (collection == null)
            return;
        SecurityCollection results[] =
            new SecurityCollection[collections.length + 1];
        for (int i = 0; i < collections.length; i++)
            results[i] = collections[i];
        results[collections.length] = collection;
        collections = results;
    }


    /**
     * 返回<code>true</code>，如果指定的角色被允许访问由此安全约束保护的资源.
     *
     * @param role Role name to be checked
     */
    public boolean findAuthRole(String role) {
        if (role == null)
            return (false);
        for (int i = 0; i < authRoles.length; i++) {
            if (role.equals(authRoles[i]))
                return (true);
        }
        return (false);
    }


    /**
     * 返回允许访问受此安全约束保护的资源的角色集. 
     * 如果没有，返回零长度数组(这意味着所有已验证的用户都被允许访问).
     */
    public String[] findAuthRoles() {
        return (authRoles);
    }


    /**
     * 返回指定名称的Web资源集合;或者<code>null</code>.
     *
     * @param name Web resource collection name to return
     */
    public SecurityCollection findCollection(String name) {
        if (name == null)
            return (null);
        for (int i = 0; i < collections.length; i++) {
            if (name.equals(collections[i].getName()))
                return (collections[i]);
        }
        return (null);
    }


    /**
     * 返回受此安全约束保护的所有Web资源集合. 
     * 如果没有, 返回零长度数组.
     */
    public SecurityCollection[] findCollections() {
        return (collections);
    }


    /**
     * 返回<code>true</code>，如果指定的上下文相对URI受此安全约束的保护(和相关的HTTP方法).
     *
     * @param uri 上下文相关URI检查
     * @param method 正在使用请求方法
     */
    public boolean included(String uri, String method) {

        // 没有有效的请求方法，无法匹配
        if (method == null)
            return (false);

        // Check all of the collections included in this constraint
        for (int i = 0; i < collections.length; i++) {
            if (!collections[i].findMethod(method))
                continue;
            String patterns[] = collections[i].findPatterns();
            for (int j = 0; j < patterns.length; j++) {
                if (matchPattern(uri, patterns[j]))
                    return (true);
            }
        }
        // 此约束中包含的集合不匹配此请求
        return (false);
    }


    /**
     * 从允许访问受此安全约束保护的资源的角色集中，删除指定角色.
     *
     * @param authRole Role name to be removed
     */
    public void removeAuthRole(String authRole) {
        if (authRole == null)
            return;
        int n = -1;
        for (int i = 0; i < authRoles.length; i++) {
            if (authRoles[i].equals(authRole)) {
                n = i;
                break;
            }
        }
        if (n >= 0) {
            int j = 0;
            String results[] = new String[authRoles.length - 1];
            for (int i = 0; i < authRoles.length; i++) {
                if (i != n)
                    results[j++] = authRoles[i];
            }
            authRoles = results;
        }
    }


    /**
     * 从受此安全约束保护的Web资源集合中移除指定的Web资源集合
     *
     * @param collection 要删除的Web资源集合
     */
    public void removeCollection(SecurityCollection collection) {

        if (collection == null)
            return;
        int n = -1;
        for (int i = 0; i < collections.length; i++) {
            if (collections[i].equals(collection)) {
                n = i;
                break;
            }
        }
        if (n >= 0) {
            int j = 0;
            SecurityCollection results[] =
                new SecurityCollection[collections.length - 1];
            for (int i = 0; i < collections.length; i++) {
                if (i != n)
                    results[j++] = collections[i];
            }
            collections = results;
        }
    }


    public String toString() {
        StringBuffer sb = new StringBuffer("SecurityConstraint[");
        for (int i = 0; i < collections.length; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(collections[i].getName());
        }
        sb.append("]");
        return (sb.toString());
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 指定的请求路径是否与指定的URL模式匹配?
     * 该方法遵循相同的规则（在同一顺序）作为请求的servlet映射.
     *
     * @param path 要检查的上下文相对请求路径(必须以'/'开头)
     * @param pattern 要比较的URL模式
     */
    private boolean matchPattern(String path, String pattern) {

        // 规范化参数字符串
        if ((path == null) || (path.length() == 0))
            path = "/";
        if ((pattern == null) || (pattern.length() == 0))
            pattern = "/";

        // Check for exact match
        if (path.equals(pattern))
            return (true);

        // Check for path prefix matching
        if (pattern.startsWith("/") && pattern.endsWith("/*")) {
            pattern = pattern.substring(0, pattern.length() - 2);
            if (pattern.length() == 0)
                return (true);  // "/*" is the same as "/"
            if (path.endsWith("/"))
                path = path.substring(0, path.length() - 1);
            while (true) {
                if (pattern.equals(path))
                    return (true);
                int slash = path.lastIndexOf('/');
                if (slash <= 0)
                    break;
                path = path.substring(0, slash);
            }
            return (false);
        }

        // Check for suffix matching
        if (pattern.startsWith("*.")) {
            int slash = path.lastIndexOf('/');
            int period = path.lastIndexOf('.');
            if ((slash >= 0) && (period > slash) &&
                path.endsWith(pattern.substring(1))) {
                return (true);
            }
            return (false);
        }

        // Check for universal mapping
        if (pattern.equals("/"))
            return (true);

        return (false);
    }
}
