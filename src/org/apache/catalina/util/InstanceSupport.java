package org.apache.catalina.util;


import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import org.apache.catalina.InstanceEvent;
import org.apache.catalina.InstanceListener;
import org.apache.catalina.Wrapper;


/**
 * 帮助触发InstanceEvent 通知注册的InstanceListener.
 */
public final class InstanceSupport {


    // ----------------------------------------------------------- Constructors


    /**
     * @param wrapper The component that will be the source
     *  of events that we fire
     */
    public InstanceSupport(Wrapper wrapper) {
        super();
        this.wrapper = wrapper;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 注册的一组InstanceListener.
     */
    private InstanceListener listeners[] = new InstanceListener[0];


    /**
     * 将要触发的实例事件的源组件.
     */
    private Wrapper wrapper = null;


    // ------------------------------------------------------------- Properties


    /**
     * 返回关联的Wrapper.
     */
    public Wrapper getWrapper() {
        return (this.wrapper);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个生命周期事件监听器.
     *
     * @param listener The listener to add
     */
    public void addInstanceListener(InstanceListener listener) {

      synchronized (listeners) {
          InstanceListener results[] =
            new InstanceListener[listeners.length + 1];
          for (int i = 0; i < listeners.length; i++)
              results[i] = listeners[i];
          results[listeners.length] = listener;
          listeners = results;
      }
    }


    /**
     * 通知所有生命周期事件监听器，这个Container发生了修改事件.
     * 默认实现使用调用线程同步执行此通知.
     *
     * @param type 事件类型
     * @param filter 此事件的相关过滤器
     */
    public void fireInstanceEvent(String type, Filter filter) {

        if (listeners.length == 0)
            return;

        InstanceEvent event = new InstanceEvent(wrapper, filter, type);
        InstanceListener interested[] = null;
        synchronized (listeners) {
            interested = (InstanceListener[]) listeners.clone();
        }
        for (int i = 0; i < interested.length; i++)
            interested[i].instanceEvent(event);

    }


    /**
     * 通知所有生命周期事件监听器，这个Container发生了修改事件.
     * 默认实现使用调用线程同步执行此通知.
     *
     * @param type Event type
     * @param filter 此事件的相关过滤器
     * @param exception Exception that occurred
     */
    public void fireInstanceEvent(String type, Filter filter,
                                  Throwable exception) {

        if (listeners.length == 0)
            return;

        InstanceEvent event = new InstanceEvent(wrapper, filter, type,
                                                exception);
        InstanceListener interested[] = null;
        synchronized (listeners) {
            interested = (InstanceListener[]) listeners.clone();
        }
        for (int i = 0; i < interested.length; i++)
            interested[i].instanceEvent(event);
    }


    /**
     * 通知所有生命周期事件监听器，这个Container发生了修改事件.
     * 默认实现使用调用线程同步执行此通知.
     *
     * @param type Event type
     * @param filter 此事件的相关过滤器
     * @param request The servlet request we are processing
     * @param response The servlet response we are processing
     */
    public void fireInstanceEvent(String type, Filter filter,
                                  ServletRequest request,
                                  ServletResponse response) {

        if (listeners.length == 0)
            return;

        InstanceEvent event = new InstanceEvent(wrapper, filter, type,
                                                request, response);
        InstanceListener interested[] = null;
        synchronized (listeners) {
            interested = (InstanceListener[]) listeners.clone();
        }
        for (int i = 0; i < interested.length; i++)
            interested[i].instanceEvent(event);
    }


    /**
     * 通知所有生命周期事件监听器，这个Container发生了修改事件.
     * 默认实现使用调用线程同步执行此通知.
     *
     * @param type Event type
     * @param filter 此事件的相关过滤器
     * @param request The servlet request we are processing
     * @param response The servlet response we are processing
     * @param exception Exception that occurred
     */
    public void fireInstanceEvent(String type, Filter filter,
                                  ServletRequest request,
                                  ServletResponse response,
                                  Throwable exception) {

        if (listeners.length == 0)
            return;

        InstanceEvent event = new InstanceEvent(wrapper, filter, type,
                                                request, response, exception);
        InstanceListener interested[] = null;
        synchronized (listeners) {
            interested = (InstanceListener[]) listeners.clone();
        }
        for (int i = 0; i < interested.length; i++)
            interested[i].instanceEvent(event);

    }


    /**
     * 通知所有生命周期事件监听器，这个Container发生了修改事件.
     * 默认实现使用调用线程同步执行此通知.
     *
     * @param type Event type
     * @param servlet 此事件的相关servlet
     */
    public void fireInstanceEvent(String type, Servlet servlet) {

        if (listeners.length == 0)
            return;

        InstanceEvent event = new InstanceEvent(wrapper, servlet, type);
        InstanceListener interested[] = null;
        synchronized (listeners) {
            interested = (InstanceListener[]) listeners.clone();
        }
        for (int i = 0; i < interested.length; i++)
            interested[i].instanceEvent(event);

    }


    /**
     * 通知所有生命周期事件监听器，这个Container发生了修改事件.
     * 默认实现使用调用线程同步执行此通知.
     *
     * @param type Event type
     * @param servlet 此事件的相关servlet
     * @param exception Exception that occurred
     */
    public void fireInstanceEvent(String type, Servlet servlet,
                                  Throwable exception) {

        if (listeners.length == 0)
            return;

        InstanceEvent event = new InstanceEvent(wrapper, servlet, type,
                                                exception);
        InstanceListener interested[] = null;
        synchronized (listeners) {
            interested = (InstanceListener[]) listeners.clone();
        }
        for (int i = 0; i < interested.length; i++)
            interested[i].instanceEvent(event);
    }


    /**
     * 通知所有生命周期事件监听器，这个Container发生了修改事件.
     * 默认实现使用调用线程同步执行此通知.
     *
     * @param type Event type
     * @param servlet 此事件的相关servlet
     * @param request The servlet request we are processing
     * @param response The servlet response we are processing
     */
    public void fireInstanceEvent(String type, Servlet servlet,
                                  ServletRequest request,
                                  ServletResponse response) {

        if (listeners.length == 0)
            return;

        InstanceEvent event = new InstanceEvent(wrapper, servlet, type,
                                                request, response);
        InstanceListener interested[] = null;
        synchronized (listeners) {
            interested = (InstanceListener[]) listeners.clone();
        }
        for (int i = 0; i < interested.length; i++)
            interested[i].instanceEvent(event);

    }


    /**
     * 通知所有生命周期事件监听器，这个Container发生了修改事件.
     * 默认实现使用调用线程同步执行此通知.
     *
     * @param type Event type
     * @param servlet 此事件的相关servlet
     * @param request The servlet request we are processing
     * @param response The servlet response we are processing
     * @param exception Exception that occurred
     */
    public void fireInstanceEvent(String type, Servlet servlet,
                                  ServletRequest request,
                                  ServletResponse response,
                                  Throwable exception) {

        if (listeners.length == 0)
            return;

        InstanceEvent event = new InstanceEvent(wrapper, servlet, type,
                                                request, response, exception);
        InstanceListener interested[] = null;
        synchronized (listeners) {
            interested = (InstanceListener[]) listeners.clone();
        }
        for (int i = 0; i < interested.length; i++)
            interested[i].instanceEvent(event);
    }


    /**
     * 删除生命周期事件监听器
     *
     * @param listener The listener to remove
     */
    public void removeInstanceListener(InstanceListener listener) {

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
            InstanceListener results[] =
              new InstanceListener[listeners.length - 1];
            int j = 0;
            for (int i = 0; i < listeners.length; i++) {
                if (i != n)
                    results[j++] = listeners[i];
            }
            listeners = results;
        }
    }
}
