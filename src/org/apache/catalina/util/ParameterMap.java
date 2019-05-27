package org.apache.catalina.util;

import java.util.HashMap;
import java.util.Map;

/**
 * <strong>HashMap</strong>的扩展实现，包含一个<code>locked</code>属性.
 * 这个类可以用来安全地暴露Catalina内部参数映射对象到用户类，为了避免修改，不必克隆它们.
 * 当第一次创建, <code>ParmaeterMap</code>实例未锁定.
 */
public final class ParameterMap extends HashMap {


    // ----------------------------------------------------------- Constructors

    public ParameterMap() {
        super();
    }


    /**
     * @param initialCapacity 初始容量
     */
    public ParameterMap(int initialCapacity) {
        super(initialCapacity);
    }


    /**
     * @param initialCapacity 初始容量
     * @param loadFactor 荷载系数
     */
    public ParameterMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
    }


    /**
     * @param map Map whose contents are dupliated in the new map
     */
    public ParameterMap(Map map) {
        super(map);
    }


    // ------------------------------------------------------------- Properties


    /**
     * 这个参数map的当前锁定状态.
     */
    private boolean locked = false;


    /**
     * 返回这个参数map的当前锁定状态.
     */
    public boolean isLocked() {
        return (this.locked);
    }


    /**
     * 设置这个参数map的当前锁定状态.
     *
     * @param locked 新锁定状态
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
    }


    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager("org.apache.catalina.util");


    // --------------------------------------------------------- Public Methods



    /**
     * 清空这个map.
     *
     * @exception IllegalStateException if this map is currently locked
     */
    public void clear() {
        if (locked)
            throw new IllegalStateException
                (sm.getString("parameterMap.locked"));
        super.clear();
    }


    /**
     * @param key Key with which the specified value is to be associated
     * @param value Value to be associated with the specified key
     *
     * @return The previous value associated with the specified key, or
     *  <code>null</code> if there was no mapping for key
     *
     * @exception IllegalStateException if this map is currently locked
     */
    public Object put(Object key, Object value) {
        if (locked)
            throw new IllegalStateException
                (sm.getString("parameterMap.locked"));
        return (super.put(key, value));
    }


    /**
     * @param map Mappings to be stored into this map
     *
     * @exception IllegalStateException if this map is currently locked
     */
    public void putAll(Map map) {
        if (locked)
            throw new IllegalStateException
                (sm.getString("parameterMap.locked"));
        super.putAll(map);
    }


    /**
     * @param key Key whose mapping is to be removed from the map
     *
     * @return The previous value associated with the specified key, or
     *  <code>null</code> if there was no mapping for that key
     *
     * @exception IllegalStateException if this map is currently locked
     */
    public Object remove(Object key) {
        if (locked)
            throw new IllegalStateException
                (sm.getString("parameterMap.locked"));
        return (super.remove(key));
    }
}
