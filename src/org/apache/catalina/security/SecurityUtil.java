package org.apache.catalina.security;


import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.catalina.Globals;
import org.apache.catalina.util.StringManager;

/**
 * 这个工具类关联了一个<code>Subject</code> 到当前<code>AccessControlContext</code>.
 * 当使用<code>SecurityManager</code>的时候, * 容器将总是将所调用的线程与一个只包含请求的Servlet/Filter主体的AccessControlContext关联 *.
 *
 * 这个类使用反射来调用方法.
 */
public final class SecurityUtil{
    
    private final static int INIT= 0;
    private final static int SERVICE = 1;
    private final static int DOFILTER = 1;
    private final static int DESTROY = 2;
    
    private final static String INIT_METHOD = "init";
    private final static String DOFILTER_METHOD = "doFilter";
    private final static String SERVICE_METHOD = "service";
    private final static String DESTROY_METHOD = "destroy";
   
    /**
     * 缓存正在创建的每个对象的方法.
     */
    private static HashMap objectCache = new HashMap();
        
    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( SecurityUtil.class );
    
    private static String PACKAGE = "org.apache.catalina.security";
    
    private static boolean packageDefinitionEnabled =  
         (System.getProperty("package.definition") == null && 
           System.getProperty("package.access")  == null) ? false : true;
    
    /**
     * The string resources for this package.
     */
    private static final StringManager sm = StringManager.getManager(PACKAGE);    
    
    
    /**
     * 执行工作作为一个特殊的</code>Subject</code>. 这里将使用<code>null</code> subject. 
     *
     * @param methodName 应用安全约束的方法
     * @param targetObject 调用此方法的<code>Servlet</code>
     */
    public static void doAsPrivilege(final String methodName, 
                                     final Servlet targetObject) throws java.lang.Exception{
         doAsPrivilege(methodName, targetObject, null, null, null);                                
    }

    
    /**
     * 执行工作作为一个特殊的</code>Subject</code>. 这里将使用<code>null</code> subject. 
     *
     * @param methodName 应用安全约束的方法
     * @param targetObject 调用此方法的<code>Servlet</code>
     * @param targetType <code>Class</code>数组用于初始化一个<code>Method</code>对象.
     * @param targetArguments <code>Object</code>数组包含运行时参数实例
     */
    public static void doAsPrivilege(final String methodName, 
                                     final Servlet targetObject, 
                                     final Class[] targetType,
                                     final Object[] targetArguments) throws java.lang.Exception{    

         doAsPrivilege(methodName, 
                       targetObject, 
                       targetType, 
                       targetArguments, 
                       null);                                
    }
    
    
    /**
     * 执行工作作为一个特殊的</code>Subject</code>. 这里将使用<code>null</code> subject. 
     *
     * @param methodName 应用安全约束的方法
     * @param targetObject 调用此方法的<code>Servlet</code>
     * @param targetType <code>Class</code>数组用于初始化一个<code>Method</code>对象.
     * @param targetArguments <code>Object</code>数组包含运行时参数实例
     * @param principal 安全特权应用的<code>Principal</code>
     */    
    public static void doAsPrivilege(final String methodName, 
                                     final Servlet targetObject, 
                                     final Class[] targetType,
                                     final Object[] targetArguments,
                                     Principal principal) 
        throws java.lang.Exception{

        Method method = null;
        Method[] methodsCache = null;
        if(objectCache.containsKey(targetObject)){
            methodsCache = (Method[])objectCache.get(targetObject);
            method = findMethod(methodsCache, methodName);
            if (method == null){
                method = createMethodAndCacheIt(methodsCache,
                                                methodName,
                                                targetObject,
                                                targetType);
            }
        } else {
            method = createMethodAndCacheIt(methodsCache,
                                            methodName,
                                            targetObject,
                                            targetType);                     
        }

        execute(method, targetObject, targetArguments, principal);
    }
 
    
    /**
     * 执行工作作为一个特殊的</code>Subject</code>. 这里将使用<code>null</code> subject. 
     *
     * @param methodName 应用安全约束的方法
     * @param targetObject 调用此方法的<code>Filter</code>
     */    
    public static void doAsPrivilege(final String methodName, 
                                     final Filter targetObject) 
        throws java.lang.Exception{

         doAsPrivilege(methodName, targetObject, null, null);                                
    }
 
    
    /**
     * 执行工作作为一个特殊的</code>Subject</code>. 这里将使用<code>null</code> subject. 
     *
     * @param methodName 应用安全约束的方法
     * @param targetObject 调用此方法的<code>Filter</code>
     * @param targetType <code>Class</code>数组用于初始化一个<code>Method</code>对象.
     * @param targetArguments <code>Object</code>数组包含运行时参数实例
     */    
    public static void doAsPrivilege(final String methodName, 
                                     final Filter targetObject, 
                                     final Class[] targetType,
                                     final Object[] targetArguments) 
        throws java.lang.Exception{
        Method method = null;

        Method[] methodsCache = null;
        if(objectCache.containsKey(targetObject)){
            methodsCache = (Method[])objectCache.get(targetObject);
            method = findMethod(methodsCache, methodName);
            if (method == null){
                method = createMethodAndCacheIt(methodsCache,
                                                methodName,
                                                targetObject,
                                                targetType);
            }
        } else {
            method = createMethodAndCacheIt(methodsCache,
                                            methodName,
                                            targetObject,
                                            targetType);                     
        }

        execute(method, targetObject, targetArguments, null);
    }
    
    
    /**
     * 执行工作作为一个特殊的</code>Subject</code>. 这里将使用<code>null</code> subject.
     *
     * @param methodName 应用安全约束的方法
     * @param targetObject 调用此方法的<code>Filter</code>
     * @param targetArguments <code>Object</code>数组包含运行时参数实例
     * @param principal 安全特权应用的<code>Principal</code>
     */    
    private static void execute(final Method method,
                                final Object targetObject, 
                                final Object[] targetArguments,
                                Principal principal) 
        throws java.lang.Exception{
       
        try{   
            Subject subject = null;
            PrivilegedExceptionAction pea = new PrivilegedExceptionAction(){
                    public Object run() throws Exception{
                       method.invoke(targetObject, targetArguments);
                       return null;
                    }
            };

            // 第一个参数总是请求对象
            if (targetArguments != null 
                    && targetArguments[0] instanceof HttpServletRequest){
                HttpServletRequest request = 
                    (HttpServletRequest)targetArguments[0];

                boolean hasSubject = false;
                HttpSession session = request.getSession(false);
                if (session != null){
                    subject = 
                        (Subject)session.getAttribute(Globals.SUBJECT_ATTR);
                    hasSubject = (subject != null);
                }

                if (subject == null){
                    subject = new Subject();
                    
                    if (principal != null){
                        subject.getPrincipals().add(principal);
                    }
                }

                if (session != null && !hasSubject) {
                    session.setAttribute(Globals.SUBJECT_ATTR, subject);
                }
            }

            Subject.doAsPrivileged(subject, pea, null);       
       } catch( PrivilegedActionException pe) {
            Throwable e = ((InvocationTargetException)pe.getException())
                                .getTargetException();
            
            if (log.isDebugEnabled()){
                log.debug(sm.getString("SecurityUtil.doAsPrivilege"), e); 
            }
            
            if (e instanceof UnavailableException)
                throw (UnavailableException) e;
            else if (e instanceof ServletException)
                throw (ServletException) e;
            else if (e instanceof IOException)
                throw (IOException) e;
            else if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            else
                throw new ServletException(e.getMessage(), e);
        }  
    }
    
    
    /**
     * 找到存储在缓存中的方法.
     * @param methodsCache 用于存储方法实例的缓存
     * @param methodName 应用安全约束的方法
     * @return 方法实例, null
     */
    private static Method findMethod(Method[] methodsCache,
                                     String methodName){
        if (methodName.equalsIgnoreCase(INIT_METHOD) 
                && methodsCache[INIT] != null){
            return methodsCache[INIT];
        } else if (methodName.equalsIgnoreCase(DESTROY_METHOD) 
                && methodsCache[DESTROY] != null){
            return methodsCache[DESTROY];            
        } else if (methodName.equalsIgnoreCase(SERVICE_METHOD) 
                && methodsCache[SERVICE] != null){
            return methodsCache[SERVICE];
        } else if (methodName.equalsIgnoreCase(DOFILTER_METHOD) 
                && methodsCache[DOFILTER] != null){
            return methodsCache[DOFILTER];          
        } 
        return null;
    }
    
    
    /**
     * 创建方法并将其缓存以便进一步重用.
     * @param methodsCache 用于存储方法实例的缓存
     * @param methodName 应用安全约束的方法
     * @param targetObject 调用此方法的<code>Filter</code>
     * @param targetType <code>Class</code>数组用于初始化一个<code>Method</code>对象.
     * @return the method instance.
     */
    private static Method createMethodAndCacheIt(Method[] methodsCache,
                                                 String methodName,
                                                 Object targetObject,
                                                 Class[] targetType) 
            throws Exception{
        
        if ( methodsCache == null){
            methodsCache = new Method[3];
        }               
                
        Method method = 
            targetObject.getClass().getMethod(methodName, targetType); 

        if (methodName.equalsIgnoreCase(INIT_METHOD)){
            methodsCache[INIT] = method;
        } else if (methodName.equalsIgnoreCase(DESTROY_METHOD)){
            methodsCache[DESTROY] = method;
        } else if (methodName.equalsIgnoreCase(SERVICE_METHOD)){
            methodsCache[SERVICE] = method;
        } else if (methodName.equalsIgnoreCase(DOFILTER_METHOD)){
            methodsCache[DOFILTER] = method;
        } 
         
        objectCache.put(targetObject, methodsCache );
                                           
        return method;
    }

    
    /**
     * 从缓存中删除对象.
     *
     * @param cachedObject 要移除的对象
     */
    public static void remove(Object cachedObject){
        objectCache.remove(cachedObject);
    }
    
    
    /**
     * 返回<code>SecurityManager</code>是否可用, 只有当Security可用以及包保护机制启用的时候.
     */
    public static boolean isPackageProtectionEnabled(){
        if (packageDefinitionEnabled && System.getSecurityManager() !=  null){
            return true;
        }
        return false;
    }
}
