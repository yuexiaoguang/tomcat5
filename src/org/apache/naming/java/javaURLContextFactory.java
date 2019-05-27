package org.apache.naming.java;

import java.util.Hashtable;
import javax.naming.Name;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.spi.ObjectFactory;
import javax.naming.spi.InitialContextFactory;
import org.apache.naming.SelectorContext;
import org.apache.naming.NamingContext;
import org.apache.naming.ContextBindings;

/**
 * "java:"命名空间上下文工厂.
 * <p>
 * <b>重要注意事项</b> : 这个工厂必须关联"java" URL前缀, 可以通过 :
 * <ul>
 * <li>添加一个
 * java.naming.factory.url.pkgs=org.apache.catalina.util.naming 属性到JNDI 属性文件</li>
 * <li>设置一个环境变量命名为Context.URL_PKG_PREFIXES, 其值包括org.apache.catalina.util.naming包名称. 
 * 关于这个的更多细节可以在JNDI文档中找到 : 
 * {@link javax.naming.spi.NamingManager#getURLContext(java.lang.String, java.util.Hashtable)}.</li>
 * </ul>
 */
public class javaURLContextFactory implements ObjectFactory, InitialContextFactory {


    // -------------------------------------------------------------- Constants


    public static final String MAIN = "initialContext";


    /**
     * 初始上下文
     */
    protected static Context initialContext = null;


    // -------------------------------------------------- ObjectFactory Methods


    /**
     * 创建一个新的 Context的实例
     */
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                    Hashtable environment)
        throws NamingException {
        if ((ContextBindings.isThreadBound()) || 
            (ContextBindings.isClassLoaderBound())) {
            return new SelectorContext(environment);
        } else {
            return null;
        }
    }


    /**
     * 获取一个新的初始上下文(可写的).
     */
    public Context getInitialContext(Hashtable environment)
        throws NamingException {
        if (ContextBindings.isThreadBound() || 
            (ContextBindings.isClassLoaderBound())) {
            // 将请求重定向到绑定的初始上下文
            return new SelectorContext(environment, true);
        } else {
            // 如果线程未绑定, 返回共享的可写上下文
            if (initialContext == null)
                initialContext = new NamingContext(environment, MAIN);
            return initialContext;
        }
    }
}
