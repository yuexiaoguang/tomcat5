package org.apache.jasper.security;

/**
 * 安全相关的操作工具类.
 */
public final class SecurityUtil{
    
    private static boolean packageDefinitionEnabled =  
         System.getProperty("package.definition") == null ? false : true;
    
    /**
     * 返回<code>SecurityManager</code> 只有当 Security 是启用的, 并且启用了包保护机制.
     */
    public static boolean isPackageProtectionEnabled(){
        if (packageDefinitionEnabled && System.getSecurityManager() !=  null){
            return true;
        }
        return false;
    }
}
