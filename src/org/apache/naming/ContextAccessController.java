package org.apache.naming;

import java.util.Hashtable;

/**
 * 在JNDI上下文句柄的访问控制.
 */
public class ContextAccessController {

    // -------------------------------------------------------------- Variables

    /**
     * Catalina 不允许写的上下文名称.
     */
    private static Hashtable readOnlyContexts = new Hashtable();


    /**
     * 安全令牌存储库
     */
    private static Hashtable securityTokens = new Hashtable();


    // --------------------------------------------------------- Public Methods


    /**
     * 设置security token. 只能设置一次.
     * 
     * @param name Name of the context
     * @param token Security token
     */
    public static void setSecurityToken(Object name, Object token) {
        if ((!securityTokens.containsKey(name)) && (token != null)) {
            securityTokens.put(name, token);
        }
    }


    /**
     * 删除一个security.
     * 
     * @param name Name of the context
     * @param token Security token
     */
    public static void unsetSecurityToken(Object name, Object token) {
        if (checkSecurityToken(name, token)) {
            securityTokens.remove(name);
        }
    }


    /**
     * 验证提交的security token. 提交的token 必须与存储库中存在的token相等.
     * 如果上下文没有token存在, 返回true.
     * 
     * @param name Name of the context
     * @param token Submitted security token
     */
    public static boolean checkSecurityToken
        (Object name, Object token) {
        Object refToken = securityTokens.get(name);
        if (refToken == null)
            return (true);
        if ((refToken != null) && (refToken.equals(token)))
            return (true);
        return (false);
    }


    /**
     * 允许写入上下文
     * 
     * @param name Name of the context
     * @param token Security token
     */
    public static void setWritable(Object name, Object token) {
        if (checkSecurityToken(name, token))
            readOnlyContexts.remove(name);
    }


    /**
     * 设置上下文是否可写
     * 
     * @param name Name of the context
     */
    public static void setReadOnly(Object name) {
        readOnlyContexts.put(name, name);
    }


    /**
     * 返回上下文是否可写
     * 
     * @param name Name of the context
     */
    public static boolean isWritable(Object name) {
        return !(readOnlyContexts.containsKey(name));
    }
}

