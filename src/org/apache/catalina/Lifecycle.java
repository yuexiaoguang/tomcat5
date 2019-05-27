package org.apache.catalina;


/**
 * 组件生命周期方法的通用接口. 
 * 可能是Catalina组件, 但不是必须的, 实现这个接口(以及它们支持的功能的相应接口)为了提供一个一致的机制来启动和停止组件
 */
public interface Lifecycle {

    // ----------------------------------------------------- Manifest Constants

    /**
     * “组件初始化”事件的LifecycleEvent类型
     */
    public static final String INIT_EVENT = "init";


    /**
     * “组件启动”事件的LifecycleEvent类型
     */
    public static final String START_EVENT = "start";


    /**
     * “组件启动之前”事件的LifecycleEvent类型
     */
    public static final String BEFORE_START_EVENT = "before_start";


    /**
     * “组件启动之后”事件的LifecycleEvent类型
     */
    public static final String AFTER_START_EVENT = "after_start";


    /**
     * “组件停止”事件的LifecycleEvent类型
     */
    public static final String STOP_EVENT = "stop";


    /**
     * “组件停止之前”事件的LifecycleEvent类型
     */
    public static final String BEFORE_STOP_EVENT = "before_stop";


    /**
     * “组件停止之后”事件的LifecycleEvent类型
     */
    public static final String AFTER_STOP_EVENT = "after_stop";


    /**
     * “组件销毁”事件的LifecycleEvent类型
     */
    public static final String DESTROY_EVENT = "destroy";


    /**
     * “组件周期”事件的LifecycleEvent类型
     */
    public static final String PERIODIC_EVENT = "periodic";


    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个LifecycleEvent监听器
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener);


    /**
     * 获取lifecycle监听器.如果没有,返回一个零长度的数组
     */
    public LifecycleListener[] findLifecycleListeners();


    /**
     * 移除一个LifecycleEvent监听器
     *
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener);


    /**
     * 这个方法应该在公共方法调用之前调用. 
     * 也将发送START_EVENT类型的LifecycleEvent给所有注册的监听器
     *
     * @exception LifecycleException 如果此组件检测到阻止该组件被使用的致命错误
     */
    public void start() throws LifecycleException;


    /**
     * 结束该组件公共方法的使用. 这个方法应该被最后调用.
     * 也将发送STOP_EVENT类型的LifecycleEvent给所有注册的监听器
     *
     * @exception LifecycleException 如果此组件检测到需要报告的致命错误
     */
    public void stop() throws LifecycleException;


}
