package org.apache.naming;

import javax.naming.Context;
import javax.naming.Reference;

/**
 * 表示事务的引用地址
 */
public class TransactionRef extends Reference {


    // -------------------------------------------------------------- Constants


    /**
     * 此引用的默认工厂
     */
    public static final String DEFAULT_FACTORY = 
        org.apache.naming.factory.Constants.DEFAULT_TRANSACTION_FACTORY;


    // ----------------------------------------------------------- Constructors


    /**
     * Resource Reference.
     */
    public TransactionRef() {
        this(null, null);
    }


    /**
     * Resource Reference.
     *
     * @param factory The factory class
     * @param factoryLocation The factory location
     */
    public TransactionRef(String factory, String factoryLocation) {
        super("javax.transaction.UserTransaction", factory, factoryLocation);
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
