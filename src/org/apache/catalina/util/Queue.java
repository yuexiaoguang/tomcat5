package org.apache.catalina.util;

import java.util.Vector;

/**
 * 一个简单的FIFO队列类，当队列为空时调用线程等待，并在不空时通知等待的线程.
 */
public class Queue {
    private Vector vector = new Vector();

    /**
     * 将对象放入队列中
     *
     * @param   object   要添加到队列中的对象
     */
    public synchronized void put(Object object) {
        vector.addElement(object);
        notify();
    }

    /**
     * 从队列中拉出第一个对象. 如果队列是空的，请等待.
     */
    public synchronized Object pull() {
        while (isEmpty())
            try {
                wait();
            } catch (InterruptedException ex) {
            }
        return get();
    }

    /**
     * 从队列中获取第一个对象。如果队列是空的，返回null.
     */
    public synchronized Object get() {
        Object object = peek();
        if (object != null)
            vector.removeElementAt(0);
        return object;
    }

    /**
     * 看看有没有可用的东西
     */
    public Object peek() {
        if (isEmpty())
            return null;
        return vector.elementAt(0);
    }

    /**
     * 队列是空的吗?
     */
    public boolean isEmpty() {
        return vector.isEmpty();
    }

    /**
     * 这个队列中有多少个元素?
     */
    public int size() {
        return vector.size();
    }
}
