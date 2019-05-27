package org.apache.catalina;

/**
 * 接口定义与特定servlet实例相关的重要事件的监听器, 而不是管理该实例的Wrapper组件
 */
public interface InstanceListener {

    /**
     * 确认指定事件的发生
     *
     * @param event 已经发生的InstanceEvent
     */
    public void instanceEvent(InstanceEvent event);

}
