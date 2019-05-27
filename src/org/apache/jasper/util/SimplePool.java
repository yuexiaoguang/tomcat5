package org.apache.jasper.util;

/**
 * 简单对象池. 基于ThreadPool 和其它的类
 *
 * 池将忽略溢出，如果空返回null.
 */
public final class SimplePool  {

    private static final int DEFAULT_SIZE=16;

    /*
     * 线程保存的位置.
     */
    private Object pool[];

    private int max;
    private int current=-1;

    private Object lock;
    
    public SimplePool() {
		this.max=DEFAULT_SIZE;
		this.pool=new Object[max];
		this.lock=new Object();
    }
    
    public SimplePool(int max) {
		this.max=max;
		this.pool=new Object[max];
		this.lock=new Object();
    }

    /**
     * 将给定对象添加到池中, 如果池满了，什么也不做
     */
    public void put(Object o) {
		synchronized( lock ) {
		    if( current < (max-1) ) {
				current += 1;
				pool[current] = o;
            }
		}
    }

    /**
     * 从池中获取对象, null 如果池是空的.
     */
    public Object get() {
		Object item = null;
		synchronized( lock ) {
		    if( current >= 0 ) {
			item = pool[current];
			current -= 1;
		    }
		}
		return item;
    }

    /**
     * 返回池的大小
     */
    public int getMax() {
    	return max;
    }
}
