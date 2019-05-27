package org.apache.catalina.util;

import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 适配器类, 包装一个<code>Enumeration</code> 集合对象<code>Iterator</code>, 因此，现有的API返回的枚举可以轻松运行最新的集合.
 */
public final class Enumerator implements Enumeration {


    // ----------------------------------------------------------- Constructors


    /**
     * @param collection 应枚举其值的集合
     */
    public Enumerator(Collection collection) {
        this(collection.iterator());
    }


    /**
     * @param collection 应枚举其值的集合
     * @param clone true to clone iterator
     */
    public Enumerator(Collection collection, boolean clone) {
        this(collection.iterator(), clone);
    }


    /**
     * @param iterator Iterator to be wrapped
     */
    public Enumerator(Iterator iterator) {
        super();
        this.iterator = iterator;
    }


    /**
     * @param iterator Iterator to be wrapped
     * @param clone true to clone iterator
     */
    public Enumerator(Iterator iterator, boolean clone) {
        super();
        if (!clone) {
            this.iterator = iterator;
        } else {
            List list = new ArrayList();
            while (iterator.hasNext()) {
                list.add(iterator.next());
            }
            this.iterator = list.iterator();   
        }
    }


    /**
     * @param map Map whose values should be enumerated
     */
    public Enumerator(Map map) {
        this(map.values().iterator());
    }


    /**
     * @param map Map whose values should be enumerated
     * @param clone true to clone iterator
     */
    public Enumerator(Map map, boolean clone) {
        this(map.values().iterator(), clone);
    }


    // ----------------------------------------------------- Instance Variables

    private Iterator iterator = null;


    // --------------------------------------------------------- Public Methods

    /**
     * 测试此枚举是否包含更多元素.
     *
     * @return <code>true</code>当且仅当该枚举对象包含至少一个要提供的元素时, 否则<code>false</code>
     */
    public boolean hasMoreElements() {
        return (iterator.hasNext());
    }


    /**
     * 返回此枚举的下一个元素，如果该枚举有至少一个元素.
     *
     * @return the next element of this enumeration
     *
     * @exception NoSuchElementException if no more elements exist
     */
    public Object nextElement() throws NoSuchElementException {
        return (iterator.next());
    }
}
