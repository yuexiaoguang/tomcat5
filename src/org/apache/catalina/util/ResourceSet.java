package org.apache.catalina.util;

import java.util.Collection;
import java.util.HashSet;

/**
 * <strong>HashSet</strong>扩展实现类，包含一个<code>locked</code>属性.
 * 该类可用于安全地将资源路径集公开给用户类，而无需克隆它们，以避免修改. 当第一次创建, <code>ResourceMap</code>未锁定.
 */
public final class ResourceSet extends HashSet {

    // ----------------------------------------------------------- Constructors

    public ResourceSet() {
        super();
    }


    /**
     * @param initialCapacity 初始容量
     */
    public ResourceSet(int initialCapacity) {
        super(initialCapacity);
    }


    /**
     * @param initialCapacity 初始容量
     * @param loadFactor 负荷系数
     */
    public ResourceSet(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }


    /**
     * @param coll 要复制内容的集合
     */
    public ResourceSet(Collection coll) {
        super(coll);
    }


    // ------------------------------------------------------------- Properties


    /**
     * 当前锁定状态
     */
    private boolean locked = false;


    /**
     * 返回当前锁定状态.
     */
    public boolean isLocked() {
        return (this.locked);
    }


    /**
     * 设置当前锁定状态.
     *
     * @param locked The new locked state
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
    }


    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager("org.apache.catalina.util");


    // --------------------------------------------------------- Public Methods


    /**
     * 如果该元素尚未呈现，则将其添加到该集合中.
     * 返回<code>true</code>如果已经添加了元素.
     *
     * @param o 要添加的对象
     *
     * @exception IllegalStateException if this ResourceSet is locked
     */
    public boolean add(Object o) {
        if (locked)
            throw new IllegalStateException(sm.getString("resourceSet.locked"));
        return (super.add(o));
    }


    /**
     * 从这个集合中删除所有元素.
     *
     * @exception IllegalStateException if this ResourceSet is locked
     */
    public void clear() {
        if (locked)
            throw new IllegalStateException
              (sm.getString("resourceSet.locked"));
        super.clear();
    }


    /**
     * 如果该集合存在，则从该集合中移除给定元素.
     * 返回<code>true</code>如果元素被移除.
     *
     * @param o 要删除的对象
     *
     * @exception IllegalStateException if this ResourceSet is locked
     */
    public boolean remove(Object o) {
        if (locked)
            throw new IllegalStateException
              (sm.getString("resourceSet.locked"));
        return (super.remove(o));
    }
}
