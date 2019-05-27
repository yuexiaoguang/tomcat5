package org.apache.naming;

import java.util.Iterator;

import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

/**
 * 命名枚举实现
 */
public class NamingContextEnumeration implements NamingEnumeration {


    // ----------------------------------------------------------- Constructors


    public NamingContextEnumeration(Iterator entries) {
    	iterator = entries;
    }


    // -------------------------------------------------------------- Variables


    /**
     * Underlying enumeration.
     */
    protected Iterator iterator;


    // --------------------------------------------------------- Public Methods


    /**
     * 检索枚举中的下一个元素
     */
    public Object next()
        throws NamingException {
        return nextElement();
    }


    /**
     * 确定枚举中是否有更多元素.
     */
    public boolean hasMore()
        throws NamingException {
        return iterator.hasNext();
    }


    /**
     * 关闭这个枚举.
     */
    public void close()
        throws NamingException {
    }


    public boolean hasMoreElements() {
        return iterator.hasNext();
    }

    public Object nextElement() {
        NamingEntry entry = (NamingEntry) iterator.next();
        return new NameClassPair(entry.name, entry.value.getClass().getName());
    }
}

