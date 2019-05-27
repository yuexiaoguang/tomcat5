package org.apache.naming.factory;

import java.util.Hashtable;
import javax.naming.Name;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.RefAddr;
import javax.naming.spi.ObjectFactory;
import org.apache.naming.EjbRef;

/**
 * 用EJB对象工厂.
 */
public class EjbFactory implements ObjectFactory {


    /**
     * 创建一个新的EJB实例
     * 
     * @param obj 描述DataSource的引用对象
     */
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                    Hashtable environment) throws Exception {
        
        if (obj instanceof EjbRef) {
            Reference ref = (Reference) obj;

            // If ejb-link has been specified, resolving the link using JNDI
            RefAddr linkRefAddr = ref.get(EjbRef.LINK);
            if (linkRefAddr != null) {
                // Retrieving the EJB link
                String ejbLink = linkRefAddr.getContent().toString();
                Object beanObj = (new InitialContext()).lookup(ejbLink);
                // 加载home接口并检查bean是否正确实现指定的home接口
                /*
                String homeClassName = ref.getClassName();
                try {
                    Class home = Class.forName(homeClassName);
                    if (home.isInstance(beanObj)) {
                        System.out.println("Bean of type " 
                                           + beanObj.getClass().getName() 
                                           + " implements home interface " 
                                           + home.getName());
                    } else {
                        System.out.println("Bean of type " 
                                           + beanObj.getClass().getName() 
                                           + " doesn't implement home interface " 
                                           + home.getName());
                        throw new NamingException
                            ("Bean of type " + beanObj.getClass().getName() 
                             + " doesn't implement home interface " 
                             + home.getName());
                    }
                } catch (ClassNotFoundException e) {
                    System.out.println("Couldn't load home interface "
                                       + homeClassName);
                }
                */
                return beanObj;
            }
            
            ObjectFactory factory = null;
            RefAddr factoryRefAddr = ref.get(Constants.FACTORY);
            if (factoryRefAddr != null) {
                // Using the specified factory
                String factoryClassName = 
                    factoryRefAddr.getContent().toString();
                // Loading factory
                ClassLoader tcl = 
                    Thread.currentThread().getContextClassLoader();
                Class factoryClass = null;
                if (tcl != null) {
                    try {
                        factoryClass = tcl.loadClass(factoryClassName);
                    } catch(ClassNotFoundException e) {
                    }
                } else {
                    try {
                        factoryClass = Class.forName(factoryClassName);
                    } catch(ClassNotFoundException e) {
                    }
                }
                if (factoryClass != null) {
                    try {
                        factory = (ObjectFactory) factoryClass.newInstance();
                    } catch(Throwable t) {
                    }
                }
            } else {
                String javaxEjbFactoryClassName = System.getProperty("javax.ejb.Factory",
                                       Constants.OPENEJB_EJB_FACTORY);
                try {
                    factory = (ObjectFactory)
                        Class.forName(javaxEjbFactoryClassName).newInstance();
                } catch(Throwable t) {
                }
            }
            if (factory != null) {
                return factory.getObjectInstance
                    (obj, name, nameCtx, environment);
            } else {
                throw new NamingException("Cannot create resource instance");
            }
        }
        return null;
    }
}

