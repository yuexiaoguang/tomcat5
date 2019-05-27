package org.apache.naming.resources;

import java.util.Enumeration;
import java.util.Vector;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

/**
 * Naming enumeration implementation.
 */
public class RecyclableNamingEnumeration implements NamingEnumeration {

    // ----------------------------------------------------------- Constructors

    public RecyclableNamingEnumeration(Vector entries) {
        this.entries = entries;
        recycle();
    }

    // -------------------------------------------------------------- Variables

    /**
     * Entries.
     */
    protected Vector entries;


    /**
     * Underlying enumeration.
     */
    protected Enumeration enumeration;


    // --------------------------------------------------------- Public Methods


    /**
     * 检索枚举中的下一个元素
     */
    public Object next()
        throws NamingException {
        return nextElement();
    }


    /**
     * 确定枚举中是否有更多元素
     */
    public boolean hasMore()
        throws NamingException {
        return enumeration.hasMoreElements();
    }


    public void close()
        throws NamingException {
    }


    public boolean hasMoreElements() {
        return enumeration.hasMoreElements();
    }


    public Object nextElement() {
        return enumeration.nextElement();
    }


    // -------------------------------------------------------- Package Methods


    /**
     * 回收
     */
    void recycle() {
    	enumeration = entries.elements();
    }
}

