package org.apache.naming.resources;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Hashtable;

import javax.naming.directory.DirContext;

/**
 * 流处理器JNDI目录上下文
 */
public class DirContextURLStreamHandler extends URLStreamHandler {
    
    // ----------------------------------------------------------- Constructors
    
    
    public DirContextURLStreamHandler() {
    }
    
    
    public DirContextURLStreamHandler(DirContext context) {
        this.context = context;
    }
    
    
    // -------------------------------------------------------------- Variables
    
    
    /**
     * 绑定的类装载器 - 目录上下文. 使用CL id作为key.
     */
    private static Hashtable clBindings = new Hashtable();
    
    
    /**
     * 绑定线程 - 目录上下文. 使用线程ID作为key
     */
    private static Hashtable threadBindings = new Hashtable();
    
    
    // ----------------------------------------------------- Instance Variables
    
    
    /**
     * 目录上下文
     */
    protected DirContext context = null;
    
    // ----------------------------------------------- URLStreamHandler Methods
    
    /**
     * 打开指向所引用对象的连接通过<code>URL</code> 参数
     */
    protected URLConnection openConnection(URL u) 
        throws IOException {
        DirContext currentContext = this.context;
        if (currentContext == null)
            currentContext = get();
        return new DirContextURLConnection(currentContext, u);
    }
    
    
    // ------------------------------------------------------------ URL Methods
    
    
    /**
     * 36534解决方案的一部分, 确保 toString 是正确的.
     */
    protected String toExternalForm(URL u) {
        // pre-compute length of StringBuffer
        int len = u.getProtocol().length() + 1;
        if (u.getPath() != null) {
            len += u.getPath().length();
        }
        if (u.getQuery() != null) {
            len += 1 + u.getQuery().length();
        }
        if (u.getRef() != null) 
            len += 1 + u.getRef().length();
        StringBuffer result = new StringBuffer(len);
        result.append(u.getProtocol());
        result.append(":");
        if (u.getPath() != null) {
            result.append(u.getPath());
        }
        if (u.getQuery() != null) {
            result.append('?');
            result.append(u.getQuery());
        }
        if (u.getRef() != null) {
            result.append("#");
            result.append(u.getRef());
        }
        return result.toString();
    }


    // --------------------------------------------------------- Public Methods
    
    
    /**
     * 设置java.protocol.handler.pkgs系统属性.
     */
    public static void setProtocolHandler() {
        String value = System.getProperty(Constants.PROTOCOL_HANDLER_VARIABLE);
        if (value == null) {
            value = Constants.Package;
            System.setProperty(Constants.PROTOCOL_HANDLER_VARIABLE, value);
        } else if (value.indexOf(Constants.Package) == -1) {
            value += "|" + Constants.Package;
            System.setProperty(Constants.PROTOCOL_HANDLER_VARIABLE, value);
        }
    }
    
    
    /**
     * 返回true, 如果当前线程本身或上下文类装入器被绑定.
     */
    public static boolean isBound() {
        return (clBindings.containsKey
                (Thread.currentThread().getContextClassLoader()))
            || (threadBindings.containsKey(Thread.currentThread()));
    }
    
    
    /**
     * 将目录上下文绑定到类装入器
     */
    public static void bind(DirContext dirContext) {
        ClassLoader currentCL = 
            Thread.currentThread().getContextClassLoader();
        if (currentCL != null)
            clBindings.put(currentCL, dirContext);
    }
    
    
    /**
     * 取消绑定目录上下文类加载器
     */
    public static void unbind() {
        ClassLoader currentCL = 
            Thread.currentThread().getContextClassLoader();
        if (currentCL != null)
            clBindings.remove(currentCL);
    }
    
    
    /**
     * 将目录上下文绑定到线程
     */
    public static void bindThread(DirContext dirContext) {
        threadBindings.put(Thread.currentThread(), dirContext);
    }
    
    
    /**
     * 取消绑定目录上下文到线程.
     */
    public static void unbindThread() {
        threadBindings.remove(Thread.currentThread());
    }
    
    
    /**
     * 获取绑定的上下文
     */
    public static DirContext get() {

        DirContext result = null;

        Thread currentThread = Thread.currentThread();
        ClassLoader currentCL = currentThread.getContextClassLoader();

        // Checking CL binding
        result = (DirContext) clBindings.get(currentCL);
        if (result != null)
            return result;

        // Checking thread biding
        result = (DirContext) threadBindings.get(currentThread);

        // Checking parent CL binding
        currentCL = currentCL.getParent();
        while (currentCL != null) {
            result = (DirContext) clBindings.get(currentCL);
            if (result != null)
                return result;
            currentCL = currentCL.getParent();
        }

        if (result == null)
            throw new IllegalStateException("Illegal class loader binding");

        return result;
    }
    
    
    /**
     * 将目录上下文绑定到类装入器
     */
    public static void bind(ClassLoader cl, DirContext dirContext) {
        clBindings.put(cl, dirContext);
    }
    
    
    /**
     * 取消绑定目录上下文到类装入器
     */
    public static void unbind(ClassLoader cl) {
        clBindings.remove(cl);
    }
    
    
    /**
     * 获取绑定的上下文.
     */
    public static DirContext get(ClassLoader cl) {
        return (DirContext) clBindings.get(cl);
    }
    
    
    /**
     * 获取绑定的上下文.
     */
    public static DirContext get(Thread thread) {
        return (DirContext) threadBindings.get(thread);
    }
}
