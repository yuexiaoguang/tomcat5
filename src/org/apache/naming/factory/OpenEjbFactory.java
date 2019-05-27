package org.apache.naming.factory;

import org.apache.naming.EjbRef;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.Reference;
import javax.naming.RefAddr;
import javax.naming.spi.ObjectFactory;
import java.util.Hashtable;
import java.util.Properties;

/**
 * EJB对象工厂.
 */
public class OpenEjbFactory implements ObjectFactory {

    // -------------------------------------------------------------- Constants

    protected static final String DEFAULT_OPENEJB_FACTORY = 
        "org.openejb.client.LocalInitialContextFactory";


    // -------------------------------------------------- ObjectFactory Methods


    /**
     * 使用OpenEJB创建一个 EJB 实例.
     * 
     * @param obj 描述DataSource的引用对象
     */
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                    Hashtable environment) throws Exception {

        Object beanObj = null;

        if (obj instanceof EjbRef) {
            Reference ref = (Reference) obj;

            String factory = DEFAULT_OPENEJB_FACTORY;
            RefAddr factoryRefAddr = ref.get("openejb.factory");
            if (factoryRefAddr != null) {
                // 检索 OpenEJB 工厂
                factory = factoryRefAddr.getContent().toString();
            }

            Properties env = new Properties();
            env.put(Context.INITIAL_CONTEXT_FACTORY, factory);

            RefAddr linkRefAddr = ref.get("openejb.link");
            if (linkRefAddr != null) {
                String ejbLink = linkRefAddr.getContent().toString();
                beanObj = (new InitialContext(env)).lookup(ejbLink);
            }
        }
        return beanObj;
    }
}
