package org.apache.naming;

import java.util.Hashtable;
import javax.naming.NamingException;
import javax.naming.Context;

/**
 * 处理关联 :
 * <ul>
 * <li>Catalina 的 NamingContext上下文名称</li>
 * <li>调用NamingContext的线程</li>
 * </ul>
 */
public class ContextBindings {


    // -------------------------------------------------------------- Variables


    /**
     * 绑定的名字 - 命名上下文. 使用名称作为key.
     */
    private static Hashtable contextNameBindings = new Hashtable();


    /**
     * 绑定线程 - 命名上下文. 使用线程ID作为key.
     */
    private static Hashtable threadBindings = new Hashtable();


    /**
     * 绑定线程 - 名称. 使用线程ID作为key.
     */
    private static Hashtable threadNameBindings = new Hashtable();


    /**
     * 绑定的类装载器 - 命名上下文. 使用CL的ID作为key.
     */
    private static Hashtable clBindings = new Hashtable();


    /**
     * 绑定的类装载器 - 名称. 使用CL的ID作为key.
     */
    private static Hashtable clNameBindings = new Hashtable();


    /**
     * The string manager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);


    // --------------------------------------------------------- Public Methods


    /**
     * 绑定上下文名
     * 
     * @param name 上下文名称
     * @param context 关联命名上下文实例
     */
    public static void bindContext(Object name, Context context) {
        bindContext(name, context, null);
    }


    /**
     * 绑定上下文名
     * 
     * @param name 上下文名称
     * @param context 关联命名上下文实例
     * @param token Security token
     */
    public static void bindContext(Object name, Context context, 
                                   Object token) {
        if (ContextAccessController.checkSecurityToken(name, token))
            contextNameBindings.put(name, context);
    }


    /**
     * 取消绑定上下文名称
     * 
     * @param name Name of the context
     */
    public static void unbindContext(Object name) {
        unbindContext(name, null);
    }


    /**
     * 取消绑定上下文名称
     * 
     * @param name Name of the context
     * @param token Security token
     */
    public static void unbindContext(Object name, Object token) {
        if (ContextAccessController.checkSecurityToken(name, token))
            contextNameBindings.remove(name);
    }


    /**
     * 检索命名上下文
     * 
     * @param name 上下文名称
     */
    static Context getContext(Object name) {
        return (Context) contextNameBindings.get(name);
    }


    /**
     * 将命名上下文绑定到线程
     * 
     * @param name Name of the context
     */
    public static void bindThread(Object name) 
        throws NamingException {
        bindThread(name, null);
    }


    /**
     * 将命名上下文绑定到线程
     * 
     * @param name Name of the context
     * @param token Security token
     */
    public static void bindThread(Object name, Object token) 
        throws NamingException {
        if (ContextAccessController.checkSecurityToken(name, token)) {
            Context context = (Context) contextNameBindings.get(name);
            if (context == null)
                throw new NamingException
                    (sm.getString("contextBindings.unknownContext", name));
            threadBindings.put(Thread.currentThread(), context);
            threadNameBindings.put(Thread.currentThread(), name);
        }
    }


    /**
     * 取消绑定命名上下文到线程
     * 
     * @param name Name of the context
     */
    public static void unbindThread(Object name) {
        unbindThread(name, null);
    }


    /**
     * 取消绑定命名上下文到线程
     * 
     * @param name Name of the context
     * @param token Security token
     */
    public static void unbindThread(Object name, Object token) {
        if (ContextAccessController.checkSecurityToken(name, token)) {
            threadBindings.remove(Thread.currentThread());
            threadNameBindings.remove(Thread.currentThread());
        }
    }


    /**
     * 检索绑定到线程的命名上下文
     */
    public static Context getThread()
        throws NamingException {
        Context context = 
            (Context) threadBindings.get(Thread.currentThread());
        if (context == null)
            throw new NamingException
                (sm.getString("contextBindings.noContextBoundToThread"));
        return context;
    }


    /**
     * 检索绑定到线程的命名上下文名称
     */
    static Object getThreadName()
        throws NamingException {
        Object name = threadNameBindings.get(Thread.currentThread());
        if (name == null)
            throw new NamingException
                (sm.getString("contextBindings.noContextBoundToThread"));
        return name;
    }


    /**
     * 当前线程是否绑定到上下文中
     */
    public static boolean isThreadBound() {
        return (threadBindings.containsKey(Thread.currentThread()));
    }


    /**
     * 将命名上下文绑定到类装入器
     * 
     * @param name Name of the context
     */
    public static void bindClassLoader(Object name) 
        throws NamingException {
        bindClassLoader(name, null);
    }


    /**
     * 将命名上下文绑定到线程
     * 
     * @param name Name of the context
     * @param token Security token
     */
    public static void bindClassLoader(Object name, Object token) 
        throws NamingException {
        bindClassLoader
            (name, token, Thread.currentThread().getContextClassLoader());
    }


    /**
     * 将命名上下文绑定到线程
     * 
     * @param name 上下文名称
     * @param token Security token
     */
    public static void bindClassLoader(Object name, Object token, 
                                       ClassLoader classLoader) 
        throws NamingException {
        if (ContextAccessController.checkSecurityToken(name, token)) {
            Context context = (Context) contextNameBindings.get(name);
            if (context == null)
                throw new NamingException
                    (sm.getString("contextBindings.unknownContext", name));
            clBindings.put(classLoader, context);
            clNameBindings.put(classLoader, name);
        }
    }


    /**
     * 取消绑定命名上下文到类加载器
     * 
     * @param name Name of the context
     */
    public static void unbindClassLoader(Object name) {
        unbindClassLoader(name, null);
    }


    /**
     * 取消绑定命名上下文到类加载器
     * 
     * @param name Name of the context
     * @param token Security token
     */
    public static void unbindClassLoader(Object name, Object token) {
        unbindClassLoader(name, token, 
                          Thread.currentThread().getContextClassLoader());
    }


    /**
     * 取消绑定命名上下文到类加载器.
     * 
     * @param name Name of the context
     * @param token Security token
     */
    public static void unbindClassLoader(Object name, Object token, 
                                         ClassLoader classLoader) {
        if (ContextAccessController.checkSecurityToken(name, token)) {
            Object n = clNameBindings.get(classLoader);
            if ((n==null) || !(n.equals(name))) {
                return;
            }
            clBindings.remove(classLoader);
            clNameBindings.remove(classLoader);
        }
    }


    /**
     * 检索绑定到类装入器的命名上下文
     */
    public static Context getClassLoader()
        throws NamingException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Context context = null;
        do {
            context = (Context) clBindings.get(cl);
            if (context != null) {
                return context;
            }
        } while ((cl = cl.getParent()) != null);
        throw new NamingException
            (sm.getString("contextBindings.noContextBoundToCL"));
    }


    /**
     * 检索绑定到类装入器的命名上下文
     */
    static Object getClassLoaderName()
        throws NamingException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Object name = null;
        do {
            name = clNameBindings.get(cl);
            if (name != null) {
                return name;
            }
        } while ((cl = cl.getParent()) != null);
        throw new NamingException
            (sm.getString("contextBindings.noContextBoundToCL"));
    }


    /**
     * 当前类装入器是否绑定到上下文中
     */
    public static boolean isClassLoaderBound() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        do {
            if (clBindings.containsKey(cl)) {
                return true;
            }
        } while ((cl = cl.getParent()) != null);
        return false;
    }
}
