package org.apache.naming;

import java.util.Iterator;

import javax.naming.Binding;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

/**
 * 命名枚举实现.
 */
public class NamingContextBindingsEnumeration implements NamingEnumeration {


    public NamingContextBindingsEnumeration(Iterator entries) {
    	iterator = entries;
    }


    /**
     * Underlying enumeration.
     */
    protected Iterator iterator;


    /**
     * 检索枚举中的下一个元素.
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
        return new Binding(entry.name, entry.value.getClass().getName(), 
                           entry.value, true);
    }
}
