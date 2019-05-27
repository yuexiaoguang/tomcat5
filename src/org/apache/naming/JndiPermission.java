package org.apache.naming;

import java.security.BasicPermission;

/**
 * 基于文件资源的JNDI名称的Java SecurityManager Permission类
 * <p>
 * 权限名称是一个完整的或部分的JNDI资源名称.
 * 一个 * 可以在名称的结尾使用, 用来匹配以名称开头的所有命名资源. 没有行动.</p>
 * <p>
 * 例如，授予权限读取所有JNDI基于文件的资源:
 * <li> permission org.apache.naming.JndiPermission "*";</li>
 * </p>
 */
public final class JndiPermission extends BasicPermission {

    // ----------------------------------------------------------- Constructors

    /**
     * @param name - JNDI资源路径名
     */
    public JndiPermission(String name) {
        super(name);
    }

    /**
     * @param String - JNDI 资源路径名
     * @param String - JNDI 行动(没有定义)
     */
    public JndiPermission(String name, String actions) {
        super(name,actions);
    }

}
