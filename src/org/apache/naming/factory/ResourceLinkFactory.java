package org.apache.naming.factory;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.spi.ObjectFactory;

import org.apache.naming.ResourceLinkRef;


/**
 * <p>Object factory, 资源引用.</p>
 */
public class ResourceLinkFactory implements ObjectFactory {

    // ------------------------------------------------------- Static Variables

    /**
     * 全局命名上下文.
     */
    private static Context globalContext = null;


    // --------------------------------------------------------- Public Methods


    /**
     * 设置全局命名上下文(note: 只能使用一次).
     * 
     * @param newGlobalContext 新的全局上下文值
     */
    public static void setGlobalContext(Context newGlobalContext) {
        if (globalContext != null)
            return;
        globalContext = newGlobalContext;
    }


    // -------------------------------------------------- ObjectFactory Methods


    /**
     * Create a new DataSource instance.
     * 
     * @param obj 描述DataSource的引用对象
     */
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                    Hashtable environment)
        throws NamingException {
        
        if (!(obj instanceof ResourceLinkRef))
            return null;

        // 是否可以处理这个请求?
        Reference ref = (Reference) obj;

        String type = ref.getClassName();

        // 读取全局引用地址
        String globalName = null;
        RefAddr refAddr = ref.get(ResourceLinkRef.GLOBALNAME);
        if (refAddr != null) {
            globalName = refAddr.getContent().toString();
            Object result = null;
            result = globalContext.lookup(globalName);
            // FIXME: Check type
            return result;
        }
        return (null);
    }
}
