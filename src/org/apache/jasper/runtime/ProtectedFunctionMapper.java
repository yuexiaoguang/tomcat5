package org.apache.jasper.runtime;

import java.util.HashMap;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;
import java.lang.reflect.Method;
import javax.servlet.jsp.el.FunctionMapper;

import org.apache.jasper.security.SecurityUtil;

/**
 * EL 函数和它们的Java方法相当. 让 Method 对象是 protected, 因此JSP 页面不能间接做反射.
 */
public final class ProtectedFunctionMapper implements FunctionMapper {

    /** 
     * 映射"prefix:name"到 java.lang.Method 对象.
     */
    private HashMap fnmap = null;

    /**
     * 如果map中只有一个函数, 就是这个 Method.
     */
    private Method theMethod = null;

    private ProtectedFunctionMapper() {
    }

    /**
     * 生成的Servlet 和 Tag Handler 实现调用这个方法检索 ProtectedFunctionMapper的实例.
     * 这是必要的，因为生成的代码没有访问此包中类的实例的权限.
     *
     * @return 一个新的受保护函数映射器.
     */
    public static ProtectedFunctionMapper getInstance() {
        ProtectedFunctionMapper funcMapper;
		if (SecurityUtil.isPackageProtectionEnabled()) {
		    funcMapper = (ProtectedFunctionMapper)AccessController.doPrivileged(
			new PrivilegedAction() {
				public Object run() {
				    return new ProtectedFunctionMapper();
				}
		    });
		} else {
		    funcMapper = new ProtectedFunctionMapper();
		}
		funcMapper.fnmap = new java.util.HashMap();
		return funcMapper;
    }

    /**
     * 映射给定的EL 函数前缀和名称到给定的Java 方法.
     *
     * @param fnQName EL函数名称(包括前缀)
     * @param c 包含java方法的类
     * @param methodName java方法的名称
     * @param args Java 方法的参数
     * @throws RuntimeException 如果没有找到给定签名的方法.
     */
    public void mapFunction(String fnQName, final Class c,
			    final String methodName, final Class[] args ) {
    	java.lang.reflect.Method method;
        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                method = (java.lang.reflect.Method)AccessController.doPrivileged(new PrivilegedExceptionAction(){

                    public Object run() throws Exception{
                        return c.getDeclaredMethod(methodName, args);
                    }                
                });      
            } catch (PrivilegedActionException ex){
                throw new RuntimeException(
                    "Invalid function mapping - no such method: "
		    + ex.getException().getMessage());               
            }
        } else {
             try {
                method = c.getDeclaredMethod(methodName, args);
            } catch( NoSuchMethodException e ) {
                throw new RuntimeException(
                    "Invalid function mapping - no such method: "
                    		+ e.getMessage());
            }
        }
        this.fnmap.put(fnQName, method );
    }

    /**
     * 创建这个类的一个实例, 并保存指定EL函数前缀和名称对应的 Method. 此方法用于EL表达式中只有一个函数时的情况.
     *
     * @param fnQName EL函数限定名(包括前缀)
     * @param c 包含java方法的类
     * @param methodName java方法的名称
     * @param args java方法的参数
     * @throws RuntimeException 如果没有找到给定签名的方法.
     */
    public static ProtectedFunctionMapper getMapForFunction(
		String fnQName, final Class c, final String methodName, final Class[] args ) {
    	
        java.lang.reflect.Method method;
        ProtectedFunctionMapper funcMapper;
        if (SecurityUtil.isPackageProtectionEnabled()){
            funcMapper = (ProtectedFunctionMapper)AccessController.doPrivileged(
                new PrivilegedAction(){
                public Object run() {
                    return new ProtectedFunctionMapper();
                }
            });

            try{
                method = (java.lang.reflect.Method)AccessController.doPrivileged(new PrivilegedExceptionAction(){

                    public Object run() throws Exception{
                        return c.getDeclaredMethod(methodName, args);
                    }
                });
            } catch (PrivilegedActionException ex){
                throw new RuntimeException(
                    "Invalid function mapping - no such method: "
                    + ex.getException().getMessage());
            }
        } else {
        	funcMapper = new ProtectedFunctionMapper();
             try {
                method = c.getDeclaredMethod(methodName, args);
            } catch( NoSuchMethodException e ) {
                throw new RuntimeException(
                    "Invalid function mapping - no such method: "
                    + e.getMessage());
            }
        }
        funcMapper.theMethod = method;
        return funcMapper;
    }

    /**
     * 将指定的本地名称和前缀解析为 Java.lang.Method.
     * 返回null, 如果找不到前缀和本地名.
     * 
     * @param prefix 函数的前缀
     * @param localName 函数名称
     * @return 映射的方法. 或Null.
     **/
    public Method resolveFunction(String prefix, String localName) {
        if (this.fnmap != null) {
            return (Method) this.fnmap.get(prefix + ":" + localName);
        }
        return theMethod;
    }
}

