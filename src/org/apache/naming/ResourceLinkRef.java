package org.apache.naming;

import javax.naming.Context;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

/**
 * 表示资源的引用地址
 */
public class ResourceLinkRef extends Reference {


    // -------------------------------------------------------------- Constants


    /**
     * 此引用的默认工厂
     */
    public static final String DEFAULT_FACTORY = 
        org.apache.naming.factory.Constants.DEFAULT_RESOURCE_LINK_FACTORY;


    /**
     * 描述地址类型
     */
    public static final String GLOBALNAME = "globalName";


    // ----------------------------------------------------------- Constructors


    /**
     * ResourceLink Reference.
     * 
     * @param resourceClass Resource class
     * @param globalName Global name
     */
    public ResourceLinkRef(String resourceClass, String globalName) {
        this(resourceClass, globalName, null, null);
    }


    /**
     * ResourceLink Reference.
     * 
     * @param resourceClass Resource class
     * @param globalName Global name
     */
    public ResourceLinkRef(String resourceClass, String globalName, 
                           String factory, String factoryLocation) {
        super(resourceClass, factory, factoryLocation);
        StringRefAddr refAddr = null;
        if (globalName != null) {
            refAddr = new StringRefAddr(GLOBALNAME, globalName);
            add(refAddr);
        }
    }


    // ------------------------------------------------------ Reference Methods


    /**
     * 检索引用的对象的工厂的类名
     */
    public String getFactoryClassName() {
        String factory = super.getFactoryClassName();
        if (factory != null) {
            return factory;
        } else {
            factory = System.getProperty(Context.OBJECT_FACTORIES);
            if (factory != null) {
                return null;
            } else {
                return DEFAULT_FACTORY;
            }
        }
    }
}
