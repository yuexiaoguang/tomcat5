package org.apache.catalina;

import java.beans.PropertyChangeListener;
import java.io.IOException;

/**
 * <b>Manager</b>管理特定Container相关的Session池. 
 * 不同的Manager实现 可能会有增加的功能，例如会话数据的持久化存储、分布式Web应用程序迁移会话
 * <p>
 * 了一个<code>Manager</code>实现类能成功操作<code>Context</code>实现类,
 * 必须遵守下列限制条件:
 * <ul>
 * <li>必须实现<code>Lifecycle</code>因此Context可以指示重新启动是必需的
 * <li>同一个<code>Manager</code>实例必须在调用<code>start()</code>方法之后，调用<code>stop()</code>方法
 * </ul>
 */
public interface Manager {

    // ------------------------------------------------------------- Properties

    public Container getContainer();


    public void setContainer(Container container);


    /**
     * 返回session是否可分配的标志
     */
    public boolean getDistributable();


    /**
     * 设置session是否可分配的标志.  
     * 如果这个标志被设置，这个manager关联的添加到session所有用户数据对象必须实现Serializable接口
     *
     * @param distributable The new distributable flag
     */
    public void setDistributable(boolean distributable);


    /**
     * 返回这个Manager实现类的描述信息，以及相关版本号, 格式为
     * <code>&lt;描述&gt;/&lt;版本&gt;</code>.
     */
    public String getInfo();


    /**
     * 返回此Manager创建的Session的默认最大非活动时间间隔（秒）
     */
    public int getMaxInactiveInterval();


    /**
     * 设置此Manager创建的Session的默认最大非活动时间间隔（秒）
     *
     * @param interval The new default value
     */
    public void setMaxInactiveInterval(int interval);


    /**
     * 获取这个Manager创建的Session的id长度.
     *
     * @return The session id length
     */
    public int getSessionIdLength();


    /**
     * 设置这个Manager创建的Session的id长度.
     *
     * @param idLength The session id length
     */
    public void setSessionIdLength(int idLength);


    /** 
     * 返回这个Manager创建的Session的总数
     */
    public int getSessionCounter();


    /** 
     * 设置这个Manager创建的Session的总数
     *
     * @param sessionCounter 创建的Session的总数.
     */
    public void setSessionCounter(int sessionCounter);


    /**
     * 获取同时处于活动状态的最大会话数.
     */
    public int getMaxActive();


    /**
     * 设置同时处于活动状态的最大会话数.
     *
     * @param maxActive 同时处于活动状态的最大会话数
     */
    public void setMaxActive(int maxActive);


    /** 
     * 获取当前活动会话的数目.
     */
    public int getActiveSessions();


    /**
     * 获取已过期的会话的数目.
     */
    public int getExpiredSessions();


    /**
     * 设置已过期的会话的数目.
     *
     * @param expiredSessions Number of sessions that have expired
     */
    public void setExpiredSessions(int expiredSessions);


    /**
     * 获取未创建的会话数量，因为已达到活动会话的最大数目.
     */
    public int getRejectedSessions();


    /**
     * 设置未创建的会话数量，因为已达到活动会话的最大数目.
     *
     * @param rejectedSessions 拒绝的会话数量
     */
    public void setRejectedSessions(int rejectedSessions);


    /**
     * 获取过期会话存活的最长时间（秒）.
     */
    public int getSessionMaxAliveTime();


    /**
     * 设置过期会话存活的最长时间（秒）.
     *
     * @param sessionMaxAliveTime 过期会话存活的最长时间（秒）
     */
    public void setSessionMaxAliveTime(int sessionMaxAliveTime);


    /**
     * 获取已过期会话存活的平均时间（以秒为单位）.
     */
    public int getSessionAverageAliveTime();


    /**
     * 设置已过期会话存活的平均时间（以秒为单位）.
     *
     * @param sessionAverageAliveTime Average time (in seconds) that expired
     * sessions had been alive.
     */
    public void setSessionAverageAliveTime(int sessionAverageAliveTime);


    // --------------------------------------------------------- Public Methods


    /**
     * 将此Session添加到此Manager的活动Session集合中
     *
     * @param session 要添加的会话
     */
    public void add(Session session);


    /**
     * 添加一个属性修改监听器
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener);


    /**
     * 从池中获取一个session，或者创建一个新的
     * PersistentManager不需要创建session数据，因为它从Store中读取
     */                                                                         
    public Session createEmptySession();


    /**
     * 创建一个新的session对象，基于Manager指定properties中的默认配置. 
     * 该方法将指定Session的ID, 可以通过session的getId()方法获取. 
     * 如果新的session不能被创建，返回<code>null</code>.
     * 
     * @exception IllegalStateException 如果不能创建新session
     * @deprecated
     */
    public Session createSession();


    /**
     * 创建一个新的session对象，基于Manager指定properties中的默认配置. 
     * 如果新的session不能被创建，返回<code>null</code>.
     * 
     * @param sessionId 用于创建新会话的会话ID; 如果是<code>null</code>, 此方法将分配会话id, 可以通过session的getId()方法获取
     * @exception IllegalStateException 如果不能创建新session
     */
    public Session createSession(String sessionId);


    /**
     * 返回指定ID的session; 如果没有，返回<code>null</code>.
     *
     * @param id The session id for the session to be returned
     *
     * @exception IllegalStateException 如果新session不能实例化
     * @exception IOException 如果在处理此请求时出现输入/输出错误
     */
    public Session findSession(String id) throws IOException;


    /**
     * 返回与此Manager关联的活动Session集合.
     * 如果这个Manager没有活动的Session，将返回零长度的数组
     */
    public Session[] findSessions();


    /**
     * 将以前卸载的当前活动session加载到适当的持久化机制.
     * 如果不支持持久化，则此方法不执行任何操作就返回
     *
     * @exception ClassNotFoundException 如果在重新加载期间找不到序列化类
     * @exception IOException if an input/output error occurs
     */
    public void load() throws ClassNotFoundException, IOException;


    /**
     * 从活动的session中移除一个session
     *
     * @param session Session to be removed
     */
    public void remove(Session session);


    /**
     * 移除一个属性修改监听器
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener);


    /**
     * 在适当的持久性机制中保存当前活动的session. 
     * 如果不支持持久化，则此方法不执行任何操作
     *
     * @exception IOException if an input/output error occurs
     */
    public void unload() throws IOException;
    
     /**
      * 此方法将定期由上下文/容器调用，并允许Manager实现执行周期任务的方法, 例如到期的会话等.
      */
     public void backgroundProcess();

}
