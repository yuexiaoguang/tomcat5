package org.apache.catalina;


import java.util.EventObject;


/**
 * 通知容器上监听器有重要更改的一般事件
 */
public final class ContainerEvent extends EventObject {


    /**
     * 发生此事件的容器
     */
    private Container container = null;


    /**
     * 与此事件相关联的事件数据
     */
    private Object data = null;


    /**
     * 此实例表示的事件类型
     */
    private String type = null;


    /**
     * @param container 发生此事件的容器
     * @param type 事件类型
     * @param data 事件数据
     */
    public ContainerEvent(Container container, String type, Object data) {
        super(container);
        this.container = container;
        this.type = type;
        this.data = data;
    }


    /**
     * 返回当前事件的事件数据
     */
    public Object getData() {
        return (this.data);
    }


    /**
     * 返回发生当前事件的容器
     */
    public Container getContainer() {

        return (this.container);

    }


    /**
     * 返回事件的类型
     */
    public String getType() {
        return (this.type);
    }


    public String toString() {
        return ("ContainerEvent['" + getContainer() + "','" +
                getType() + "','" + getData() + "']");
    }
}
