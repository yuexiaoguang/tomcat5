package org.apache.catalina.util;


import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;


/**
 * 帮助触发LifecycleEvent 通知注册的 LifecycleListener.
 */
public final class LifecycleSupport {


    // ----------------------------------------------------------- Constructors


    /**
     * @param lifecycle 生命周期组件将是触发的事件源
     */
    public LifecycleSupport(Lifecycle lifecycle) {
        super();
        this.lifecycle = lifecycle;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 生命周期组件将是触发的事件源.
     */
    private Lifecycle lifecycle = null;


    /**
     * 一组注册的LifecycleListener
     */
    private LifecycleListener listeners[] = new LifecycleListener[0];


    // --------------------------------------------------------- Public Methods


    /**
     * 添加声明周期事件监听器.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {

      synchronized (listeners) {
          LifecycleListener results[] =
            new LifecycleListener[listeners.length + 1];
          for (int i = 0; i < listeners.length; i++)
              results[i] = listeners[i];
          results[listeners.length] = listener;
          listeners = results;
      }

    }


    /**
     * 获取所有生命周期事件监听器.
     * 如果没有, 返回零长度数组.
     */
    public LifecycleListener[] findLifecycleListeners() {
        return listeners;
    }


    /**
     * 通知所有生命周期事件监听器， 这个Container发生了一个修改事件.
     * 默认实现使用调用线程同步执行此通知.
     *
     * @param type Event type
     * @param data Event data
     */
    public void fireLifecycleEvent(String type, Object data) {
        LifecycleEvent event = new LifecycleEvent(lifecycle, type, data);
        LifecycleListener interested[] = null;
        synchronized (listeners) {
            interested = (LifecycleListener[]) listeners.clone();
        }
        for (int i = 0; i < interested.length; i++)
            interested[i].lifecycleEvent(event);
    }


    /**
     * 删除生命周期事件监听器.
     *
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener) {
        synchronized (listeners) {
            int n = -1;
            for (int i = 0; i < listeners.length; i++) {
                if (listeners[i] == listener) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;
            LifecycleListener results[] =
              new LifecycleListener[listeners.length - 1];
            int j = 0;
            for (int i = 0; i < listeners.length; i++) {
                if (i != n)
                    results[j++] = listeners[i];
            }
            listeners = results;
        }
    }
}
