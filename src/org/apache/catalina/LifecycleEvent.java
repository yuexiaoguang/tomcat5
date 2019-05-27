package org.apache.catalina;

import java.util.EventObject;

/**
 * 一般事件，通知监听器实现了Lifecycle接口的组件发生修改。
 * 特别是，这将是有用的, 这些事件取代 Tomcat 3.x中的ContextInterceptor概念
 */
public final class LifecycleEvent extends EventObject {

    // ----------------------------------------------------------- Constructors

    /**
     * @param lifecycle 发生事件的Lifecycle接口实现类
     * @param type 事件类型(必须)
     */
    public LifecycleEvent(Lifecycle lifecycle, String type) {
        this(lifecycle, type, null);
    }


    /**
     * @param lifecycle 发生事件的Lifecycle接口实现类
     * @param type 事件类型(必须)
     * @param data 事件数据(可选)
     */
    public LifecycleEvent(Lifecycle lifecycle, String type, Object data) {
        super(lifecycle);
        this.lifecycle = lifecycle;
        this.type = type;
        this.data = data;
    }


    // ----------------------------------------------------- Instance Variables


    private Object data = null;


    private Lifecycle lifecycle = null;


    private String type = null;

    // ------------------------------------------------------------- Properties

    public Object getData() {
        return (this.data);
    }


    public Lifecycle getLifecycle() {
        return (this.lifecycle);
    }


    public String getType() {
        return (this.type);
    }
}
