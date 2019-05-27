package org.apache.catalina;

/**
 * 定义重要容器生成事件监听器的接口。
 * "容器启动" 和 "容器关闭"事件通常是 LifecycleEvents, 不会是 ContainerEvents.
 */
public interface ContainerListener {

    /**
     * 确认指定事件的发生
     *
     * @param event 已发生的ContainerEvent
     */
    public void containerEvent(ContainerEvent event);

}
