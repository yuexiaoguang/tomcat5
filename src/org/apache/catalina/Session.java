package org.apache.catalina;


import java.security.Principal;
import java.util.Iterator;

import javax.servlet.http.HttpSession;


/**
 * <b>Session</b> 是Catalina内部外观模式，为<code>HttpSession</code>，
 * 用于维护Web应用程序特定用户请求之间的状态信息
 */
public interface Session {

    // ----------------------------------------------------- Manifest Constants

    /**
     * 创建session的时候的SessionEvent事件类型
     */
    public static final String SESSION_CREATED_EVENT = "createSession";


    /**
     * 销毁session的时候的SessionEvent事件类型
     */
    public static final String SESSION_DESTROYED_EVENT = "destroySession";


    /**
     * 激活session的时候的SessionEvent事件类型
     */
    public static final String SESSION_ACTIVATED_EVENT = "activateSession";


    /**
     * 休眠session的时候的SessionEvent事件类型
     */
    public static final String SESSION_PASSIVATED_EVENT = "passivateSession";


    // ------------------------------------------------------------- Properties


    /**
     * 返回用于验证缓存的Principal的验证类型
     */
    public String getAuthType();


    /**
     * 设置用于验证缓存的Principal的验证类型
     *
     * @param authType 缓存的验证类型
     */
    public void setAuthType(String authType);


    /**
     * 返回会话的创建时间
     */
    public long getCreationTime();


    /**
     * 设置会话的创建时间. 当现有会话实例重用时，Manager调用此方法.
     *
     * @param time The new creation time
     */
    public void setCreationTime(long time);


    public String getId();


    public String getIdInternal();


    public void setId(String id);


    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;描述&gt;/&lt;版本号&gt;</code>.
     */
    public String getInfo();


    /**
     * 返回客户端发送请求的最后一次时间, 从午夜起的毫秒数, January 1, 1970
     * GMT.  应用程序所采取的操作, 例如获取或设置与会话相关联的值, 不影响访问时间
     */
    public long getLastAccessedTime();


    /**
     * 返回有效的Manager
     */
    public Manager getManager();


    /**
     * 设置有效的Manager
     *
     * @param manager The new Manager
     */
    public void setManager(Manager manager);


    /**
     * 返回两次请求最大时间间隔, 单位是秒, 在servlet容器关闭session之前. 
     * 负值表示session永远不会超时
     */
    public int getMaxInactiveInterval();


    /**
     * 设置两次请求最大时间间隔, 单位是秒, 在servlet容器关闭session之前. 
     * 负值表示session永远不会超时
     *
     * @param interval The new maximum interval
     */
    public void setMaxInactiveInterval(int interval);


    public void setNew(boolean isNew);


    /**
     * 返回关联的已验证的Principal.
     * 这提供了一个<code>Authenticator</code> 使用先前缓存的已验证的Principal, 避免潜在的大代价的
     * <code>Realm.authenticate()</code>调用，在每个请求中. 
     * 如果没有当前关联的Principal, 返回<code>null</code>.
     */
    public Principal getPrincipal();


    /**
     * 设置关联的已验证的Principal.
     * 这提供了一个<code>Authenticator</code> 使用先前缓存的已验证的Principal, 避免潜在的大代价的
     * <code>Realm.authenticate()</code>调用，在每个请求中. 
     * 如果没有当前关联的Principal, 返回<code>null</code>.
     *
     * @param principal The new Principal, or <code>null</code> if none
     */
    public void setPrincipal(Principal principal);


    /**
     * 返回包装的<code>HttpSession</code>
     */
    public HttpSession getSession();


    public void setValid(boolean isValid);


    public boolean isValid();


    // --------------------------------------------------------- Public Methods


    /**
     * 更新此session的访问时间信息. 
     * 这个方法应该被上下文调用，当一个请求到来的时候 ，即使应用程序不引用它
     */
    public void access();


    /**
     * 添加一个session事件监听器
     */
    public void addSessionListener(SessionListener listener);


    /**
     * 结束会话访问
     */
    public void endAccess();


    /**
     * 执行使会话无效的内部处理, 如果会话已经过期，则不触发异常.
     */
    public void expire();


    /**
     * 将指定名称绑定的对象返回给此会话的内部注释, 或者<code>null</code>
     *
     * @param name 注释名称
     */
    public Object getNote(String name);


    /**
     * 返回所有注释的名称
     */
    public Iterator getNoteNames();


    /**
     * 释放所有对象引用，初始化实例变量，以准备重用这个对象
     */
    public void recycle();


    /**
     * 移除指定名称关联的所有注释
     *
     * @param name Name of the note to be removed
     */
    public void removeNote(String name);


    /**
     * 移除一个session事件监听器
     */
    public void removeSessionListener(SessionListener listener);


    /**
     * 设置内部注释
     *
     * @param name 绑定的名称
     * @param value 绑定的对象
     */
    public void setNote(String name, Object value);


}
