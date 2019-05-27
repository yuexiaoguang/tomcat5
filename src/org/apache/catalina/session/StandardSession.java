package org.apache.catalina.session;


import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.SessionEvent;
import org.apache.catalina.SessionListener;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.util.Enumerator;
import org.apache.catalina.util.StringManager;

/**
 * <b>Session</b>接口标准实现类.
 * 这个对象是可序列化的, 因此，它可以存储在持久存储或转移到一个不同的虚拟机可分配会话支持.
 * <p>
 * <b>实现注意</b>: 这个类的实例表示内部（会话）和应用层（HttpSession）的会话视图.
 * 但是, 因为类本身没有被声明为public, <code>org.apache.catalina.session</code>包之外的类不能使用此实例HTTPSession视图返回到会话视图.
 * <p>
 * <b>实现注意</b>: 如果将字段添加到该类, 必须确保在读/写对象方法中进行了这些操作，这样就可以正确地序列化这个类.
 */
public class StandardSession implements HttpSession, Session, Serializable {

    // ----------------------------------------------------------- Constructors

    /**
     * @param manager The manager with which this Session is associated
     */
    public StandardSession(Manager manager) {
        super();
        this.manager = manager;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 类型数组.
     */
    protected static final String EMPTY_ARRAY[] = new String[0];


    /**
     * 虚拟属性值序列化，当 <code>writeObject()</code>抛出NotSerializableException异常.
     */
    protected static final String NOT_SERIALIZED = "___NOT_SERIALIZABLE_EXCEPTION___";


    /**
     * 用户数据属性集合.
     */
    protected Map attributes = new Hashtable();


    /**
     * 用于验证缓存Principal的身份验证类型. 
     * NOTE: 此值不包含在该对象的序列化版本中.
     */
    protected transient String authType = null;


    /**
     * <code>org.apache.catalina.core.StandardContext</code>的<code>fireContainerEvent()</code> 方法的反射,
     * 如果Context实现类是这个类的. 
     * 该值在需要时第一次动态计算, 或者在会话重新加载之后 (自从它被声明为transient).
     */
    protected transient Method containerEventMethod = null;


    /**
     * <code>fireContainerEvent</code>方法的方法签名.
     */
    protected static final Class containerEventTypes[] = { String.class, Object.class };


    /**
     * 创建会话的时间, 午夜以来的毫秒,
     * January 1, 1970 GMT.
     */
    protected long creationTime = 0L;


    /**
     * 不允许保存的属性名称集合.
     */
    private static final String[] excludedAttributes = {
        Globals.SUBJECT_ATTR
    };


    /**
     * 目前正在处理的会话过期, 所以绕过某些 IllegalStateException 测试. 
     * NOTE: 此值不包含在该对象的序列化版本中.
     */
    protected transient boolean expiring = false;


    /**
     * 这个session的外观模式.
     * NOTE: 此值不包含在该对象的序列化版本中.
     */
    protected transient StandardSessionFacade facade = null;


    /**
     * 这个Session的会话标识符.
     */
    protected String id = null;


    /**
     * 这个Session实现类的描述信息
     */
    protected static final String info = "StandardSession/1.0";


    /**
     * 此会话的最后一次访问时间.
     */
    protected long lastAccessedTime = creationTime;


    /**
     * 会话事件监听器.
     */
    protected transient ArrayList listeners = new ArrayList();


    /**
     * 关联的Manager
     */
    protected transient Manager manager = null;


    /**
     * 最大时间间隔, in seconds, 在servlet容器可能使该会话无效之前，客户端请求之间. 
     * 负值表示会话不应该超时.
     */
    protected int maxInactiveInterval = -1;


    /**
     * 这个会话是不是新的.
     */
    protected boolean isNew = false;


    /**
     * 此会话有效与否.
     */
    protected boolean isValid = false;

    
    /**
     * 内部注释.  <b>IMPLEMENTATION NOTE:</b> 这个对象不是保存和恢复整个会话序列!
     */
    protected transient Map notes = new Hashtable();


    /**
     * 认证过的 Principal.
     * <b>IMPLEMENTATION NOTE:</b> 这个对象不是保存和恢复整个会话序列!
     */
    protected transient Principal principal = null;


    /**
     * The string manager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * HTTP会话上下文.
     */
    protected static HttpSessionContext sessionContext = null;


    /**
     * 属性修改支持. 
     * NOTE: 此值不包含在该对象的序列化版本中.
     */
    protected transient PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * 这个会话的当前访问时间.
     */
    protected long thisAccessedTime = creationTime;


    /**
     * 此会话的访问计数.
     */
    protected transient int accessCount = 0;


    // ----------------------------------------------------- Session Properties


    /**
     * 返回用于验证缓存Principal的身份验证类型.
     */
    public String getAuthType() {
        return (this.authType);
    }


    /**
     * 设置用于验证缓存Principal的身份验证类型.
     *
     * @param authType 缓存的验证类型
     */
    public void setAuthType(String authType) {
        String oldAuthType = this.authType;
        this.authType = authType;
        support.firePropertyChange("authType", oldAuthType, this.authType);
    }


    /**
     * 设置此会话的创建时间. 
     * 当现有会话实例被重用时, 这个方法被Manager调用.
     *
     * @param time The new creation time
     */
    public void setCreationTime(long time) {
        this.creationTime = time;
        this.lastAccessedTime = time;
        this.thisAccessedTime = time;
    }


    /**
     * 返回会话标识符.
     */
    public String getId() {
        if ( !isValid() ) {
            throw new IllegalStateException
            (sm.getString("standardSession.getId.ise"));
        }
        return (this.id);
    }


    /**
     * 返回会话标识符.
     */
    public String getIdInternal() {
        return (this.id);
    }


    /**
     * 设置会话标识符.
     *
     * @param id The new session identifier
     */
    public void setId(String id) {
        if ((this.id != null) && (manager != null))
            manager.remove(this);

        this.id = id;

        if (manager != null)
            manager.add(this);
        tellNew();
    }


    /**
     * 通知监听器有关新会话的情况.
     */
    public void tellNew() {

        // Notify interested session event listeners
        fireSessionEvent(Session.SESSION_CREATED_EVENT, null);

        // Notify interested application event listeners
        Context context = (Context) manager.getContainer();
        Object listeners[] = context.getApplicationLifecycleListeners();
        if (listeners != null) {
            HttpSessionEvent event =
                new HttpSessionEvent(getSession());
            for (int i = 0; i < listeners.length; i++) {
                if (!(listeners[i] instanceof HttpSessionListener))
                    continue;
                HttpSessionListener listener =
                    (HttpSessionListener) listeners[i];
                try {
                    fireContainerEvent(context,
                                       "beforeSessionCreated",
                                       listener);
                    listener.sessionCreated(event);
                    fireContainerEvent(context,
                                       "afterSessionCreated",
                                       listener);
                } catch (Throwable t) {
                    try {
                        fireContainerEvent(context,
                                           "afterSessionCreated",
                                           listener);
                    } catch (Exception e) {
                        ;
                    }
                    manager.getContainer().getLogger().error
                        (sm.getString("standardSession.sessionEvent"), t);
                }
            }
        }
    }


    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 返回客户端发送请求的最后一次时间, 从午夜起的毫秒数, January 1, 1970
     * GMT.  应用程序所采取的操作, 比如获取或设置一个值, 不影响访问时间.
     */
    public long getLastAccessedTime() {
         if ( !isValid() ) {
             throw new IllegalStateException
                 (sm.getString("standardSession.getLastAccessedTime.ise"));
         }
         return (this.lastAccessedTime);
    }


    /**
     * 返回其中会话有效的Manager.
     */
    public Manager getManager() {
        return (this.manager);
    }


    /**
     * 设置其中会话有效的Manager.
     *
     * @param manager The new Manager
     */
    public void setManager(Manager manager) {
        this.manager = manager;
    }


    /**
     * 返回最大时间间隔, in seconds, 在servlet容器将使会话无效之前，客户端请求之间.
     * 负值表示会话不应该超时.
     */
    public int getMaxInactiveInterval() {
        return (this.maxInactiveInterval);
    }


    /**
     * 设置最大时间间隔, in seconds, 在servlet容器将使会话无效之前，客户端请求之间.
     * 负值表示会话不应该超时.
     *
     * @param interval The new maximum interval
     */
    public void setMaxInactiveInterval(int interval) {
        this.maxInactiveInterval = interval;
        if (isValid && interval == 0) {
            expire();
        }
    }


    /**
     * Set the <code>isNew</code> flag for this session.
     *
     * @param isNew The new value for the <code>isNew</code> flag
     */
    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }


    /**
     * 返回已认证的Principal.
     * 提供了一个<code>Authenticator</code>缓存先前已验证过的Principal的方法, 
     * 避免潜在的每个请求的 <code>Realm.authenticate()</code>调用.
     * 如果没有关联的Principal, 返回<code>null</code>.
     */
    public Principal getPrincipal() {
        return (this.principal);
    }


    /**
     * 设置已认证的Principal.
     * 提供了一个<code>Authenticator</code>缓存先前已验证过的Principal的方法, 
     * 避免潜在的每个请求的 <code>Realm.authenticate()</code>调用.
     * 如果没有关联的Principal, 返回<code>null</code>.
     *
     * @param principal The new Principal, or <code>null</code> if none
     */
    public void setPrincipal(Principal principal) {
        Principal oldPrincipal = this.principal;
        this.principal = principal;
        support.firePropertyChange("principal", oldPrincipal, this.principal);
    }


    /**
     * 返回代理的<code>HttpSession</code>.
     */
    public HttpSession getSession() {

        if (facade == null){
            if (SecurityUtil.isPackageProtectionEnabled()){
                final StandardSession fsession = this;
                facade = (StandardSessionFacade)AccessController.doPrivileged(new PrivilegedAction(){
                    public Object run(){
                        return new StandardSessionFacade(fsession);
                    }
                });
            } else {
                facade = new StandardSessionFacade(this);
            }
        }
        return (facade);
    }


    /**
     * Return the <code>isValid</code> flag for this session.
     */
    public boolean isValid() {
        if (this.expiring) {
            return true;
        }

        if (!this.isValid ) {
            return false;
        }

        if (accessCount > 0) {
            return true;
        }

        if (maxInactiveInterval >= 0) { 
            long timeNow = System.currentTimeMillis();
            int timeIdle = (int) ((timeNow - thisAccessedTime) / 1000L);
            if (timeIdle >= maxInactiveInterval) {
                expire(true);
            }
        }
        return (this.isValid);
    }


    /**
     * Set the <code>isValid</code> flag for this session.
     *
     * @param isValid The new value for the <code>isValid</code> flag
     */
    public void setValid(boolean isValid) {
        this.isValid = isValid;
    }


    // ------------------------------------------------- Session Public Methods


    /**
     * 更新访问的时间信息. 当一个请求进入某个特定会话时，该方法应该由上下文调用, 即使应用程序不引用它.
     */
    public void access() {
        this.lastAccessedTime = this.thisAccessedTime;
        this.thisAccessedTime = System.currentTimeMillis();

        evaluateIfValid();
        accessCount++;
    }


    /**
     * 结束访问
     */
    public void endAccess() {
        isNew = false;
        accessCount--;
    }


    /**
     * 添加会话事件监听器.
     */
    public void addSessionListener(SessionListener listener) {
        listeners.add(listener);
    }


    /**
     * 执行使会话无效的内部处理, 如果会话已经过期，则不触发异常.
     */
    public void expire() {
        expire(true);
    }


    /**
     * 执行使会话无效的内部处理, 如果会话已经过期，则不触发异常.
     *
     * @param notify 应该通知监听器这个会话的死亡?
     */
    public void expire(boolean notify) {

        // 标记会话 "being expired"
        if (expiring)
            return;

        synchronized (this) {

            if (manager == null)
                return;

            expiring = true;
        
            // 通知相关应用事件监听器
            // FIXME - Assumes we call listeners in reverse order
            Context context = (Context) manager.getContainer();
            Object listeners[] = context.getApplicationLifecycleListeners();
            if (notify && (listeners != null)) {
                HttpSessionEvent event =
                    new HttpSessionEvent(getSession());
                for (int i = 0; i < listeners.length; i++) {
                    int j = (listeners.length - 1) - i;
                    if (!(listeners[j] instanceof HttpSessionListener))
                        continue;
                    HttpSessionListener listener =
                        (HttpSessionListener) listeners[j];
                    try {
                        fireContainerEvent(context,
                                           "beforeSessionDestroyed",
                                           listener);
                        listener.sessionDestroyed(event);
                        fireContainerEvent(context,
                                           "afterSessionDestroyed",
                                           listener);
                    } catch (Throwable t) {
                        try {
                            fireContainerEvent(context,
                                               "afterSessionDestroyed",
                                               listener);
                        } catch (Exception e) {
                            ;
                        }
                        manager.getContainer().getLogger().error
                            (sm.getString("standardSession.sessionEvent"), t);
                    }
                }
            }
            accessCount = 0;
            setValid(false);

            /*
             * 计算这个会话存活了多长时间, 并相应地更新会话管理器的相关属性
             */
            long timeNow = System.currentTimeMillis();
            int timeAlive = (int) ((timeNow - creationTime)/1000);
            synchronized (manager) {
                if (timeAlive > manager.getSessionMaxAliveTime()) {
                    manager.setSessionMaxAliveTime(timeAlive);
                }
                int numExpired = manager.getExpiredSessions();
                numExpired++;
                manager.setExpiredSessions(numExpired);
                int average = manager.getSessionAverageAliveTime();
                average = ((average * (numExpired-1)) + timeAlive)/numExpired;
                manager.setSessionAverageAliveTime(average);
            }

            // Remove this session from our manager's active sessions
            manager.remove(this);

            // 通知相关会话事件监听器
            if (notify) {
                fireSessionEvent(Session.SESSION_DESTROYED_EVENT, null);
            }

            // 会话已经失效
            expiring = false;

            // 解绑关联的对象
            String keys[] = keys();
            for (int i = 0; i < keys.length; i++)
                removeAttributeInternal(keys[i], notify);
        }
    }


    /**
     * 执行所需的钝化.
     */
    public void passivate() {

        // 通知相关会话事件监听器
        fireSessionEvent(Session.SESSION_PASSIVATED_EVENT, null);

        // Notify ActivationListeners
        HttpSessionEvent event = null;
        String keys[] = keys();
        for (int i = 0; i < keys.length; i++) {
            Object attribute = attributes.get(keys[i]);
            if (attribute instanceof HttpSessionActivationListener) {
                if (event == null)
                    event = new HttpSessionEvent(getSession());
                try {
                    ((HttpSessionActivationListener)attribute)
                        .sessionWillPassivate(event);
                } catch (Throwable t) {
                    manager.getContainer().getLogger().error
                        (sm.getString("standardSession.attributeEvent"), t);
                }
            }
        }
    }


    /**
     * 执行激活此会话所需的内部处理.
     */
    public void activate() {

        // 通知相关会话事件监听器
        fireSessionEvent(Session.SESSION_ACTIVATED_EVENT, null);

        // Notify ActivationListeners
        HttpSessionEvent event = null;
        String keys[] = keys();
        for (int i = 0; i < keys.length; i++) {
            Object attribute = attributes.get(keys[i]);
            if (attribute instanceof HttpSessionActivationListener) {
                if (event == null)
                    event = new HttpSessionEvent(getSession());
                try {
                    ((HttpSessionActivationListener)attribute)
                        .sessionDidActivate(event);
                } catch (Throwable t) {
                    manager.getContainer().getLogger().error
                        (sm.getString("standardSession.attributeEvent"), t);
                }
            }
        }
    }


    /**
     * 将指定名称绑定的对象返回给此会话的内部注释, 或者<code>null</code>.
     *
     * @param name Name of the note to be returned
     */
    public Object getNote(String name) {
        return (notes.get(name));
    }


    /**
     * 返回此会话存在的所有Notes绑定的字符串名称的迭代器.
     */
    public Iterator getNoteNames() {
        return (notes.keySet().iterator());
    }


    /**
     * 释放所有对象引用, 初始化实例变量, 准备重用这个对象.
     */
    public void recycle() {
        // 重置关联的实际变量
        attributes.clear();
        setAuthType(null);
        creationTime = 0L;
        expiring = false;
        id = null;
        lastAccessedTime = 0L;
        maxInactiveInterval = -1;
        accessCount = 0;
        notes.clear();
        setPrincipal(null);
        isNew = false;
        isValid = false;
        manager = null;
    }


    /**
     * 删除在内部注释中绑定到指定名称的任何对象.
     *
     * @param name Name of the note to be removed
     */
    public void removeNote(String name) {
        notes.remove(name);
    }


    /**
     * 移除会话事件监听器.
     */
    public void removeSessionListener(SessionListener listener) {
        listeners.remove(listener);
    }


    /**
     * 将对象绑定到内部注释中指定的名称, 替换此名称的任何现有绑定.
     *
     * @param name Name to which the object should be bound
     * @param value Object to be bound to the specified name
     */
    public void setNote(String name, Object value) {
        notes.put(name, value);
    }


    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("StandardSession[");
        sb.append(id);
        sb.append("]");
        return (sb.toString());
    }


    // ------------------------------------------------ Session Package Methods


    /**
     * 从指定的对象输入流中读取该会话对象的内容的序列化版本, StandardSession本身已序列化.
     *
     * @param stream The object input stream to read from
     *
     * @exception ClassNotFoundException if an unknown class is specified
     * @exception IOException if an input/output error occurs
     */
    public void readObjectData(ObjectInputStream stream)
        throws ClassNotFoundException, IOException {
        readObject(stream);
    }


    /**
     * 将该会话对象的内容的序列化版本写入指定的对象输出流, StandardSession本身已序列化.
     *
     * @param stream The object output stream to write to
     *
     * @exception IOException if an input/output error occurs
     */
    public void writeObjectData(ObjectOutputStream stream)
        throws IOException {
        writeObject(stream);
    }


    // ------------------------------------------------- HttpSession Properties


    /**
     * 返回此会话创建时的时间, 午夜以来的毫秒, January 1, 1970 GMT.
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public long getCreationTime() {

        if (!isValid())
            throw new IllegalStateException
                (sm.getString("standardSession.getCreationTime.ise"));

        return (this.creationTime);
    }


    /**
     * 返回所属的ServletContext.
     */
    public ServletContext getServletContext() {

        if (manager == null)
            return (null);
        Context context = (Context) manager.getContainer();
        if (context == null)
            return (null);
        else
            return (context.getServletContext());
    }


    /**
     * 返回关联的会话上下文.
     *
     * @deprecated As of Version 2.1, this method is deprecated and has no
     *  replacement.  It will be removed in a future version of the
     *  Java Servlet API.
     */
    public HttpSessionContext getSessionContext() {
        if (sessionContext == null)
            sessionContext = new StandardSessionContext();
        return (sessionContext);
    }


    // ----------------------------------------------HttpSession Public Methods


    /**
     * 返回指定名称的属性或<code>null</code>.
     *
     * @param name Name of the attribute to be returned
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public Object getAttribute(String name) {
        if (!isValid())
            throw new IllegalStateException
                (sm.getString("standardSession.getAttribute.ise"));

        return (attributes.get(name));
    }


    /**
     * 返回所有属性的名称的枚举.
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public Enumeration getAttributeNames() {

        if (!isValid())
            throw new IllegalStateException
                (sm.getString("standardSession.getAttributeNames.ise"));

        return (new Enumerator(attributes.keySet(), true));
    }


    /**
     * 返回指定名称的值, 或<code>null</code>.
     *
     * @param name Name of the value to be returned
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     *
     * @deprecated As of Version 2.2, this method is replaced by
     *  <code>getAttribute()</code>
     */
    public Object getValue(String name) {
        return (getAttribute(name));
    }


    /**
     * 返回所有属性的名称. 如果没有, 返回零长度数组.
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     *
     * @deprecated As of Version 2.2, this method is replaced by
     *  <code>getAttributeNames()</code>
     */
    public String[] getValueNames() {

        if (!isValid())
            throw new IllegalStateException
                (sm.getString("standardSession.getValueNames.ise"));

        return (keys());

    }


    /**
     * 使会话无效并解绑所有对象.
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public void invalidate() {

        if (!isValid())
            throw new IllegalStateException
                (sm.getString("standardSession.invalidate.ise"));

        // Cause this session to expire
        expire();
    }


    /**
     * 返回<code>true</code>，如果客户端还不知道会话, 或者如果客户端选择不加入会话.
     * 例如, 如果服务器只使用基于cookie的会话, 客户端禁用了cookie的使用, 然后每个请求都会有一个会话.
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public boolean isNew() {
        if (!isValid())
            throw new IllegalStateException
                (sm.getString("standardSession.isNew.ise"));

        return (this.isNew);
    }



    /**
     * 设置属性
     * <p>
     * After this method executes, and if the object implements
     * <code>HttpSessionBindingListener</code>, the container calls
     * <code>valueBound()</code> on the object.
     *
     * @param name Name to which the object is bound, cannot be null
     * @param value Object to be bound, cannot be null
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     *
     * @deprecated As of Version 2.2, this method is replaced by
     *  <code>setAttribute()</code>
     */
    public void putValue(String name, Object value) {
        setAttribute(name, value);
    }


    /**
     * 删除指定名称的属性. 如果属性不存在, 什么都不做.
     * <p>
     * 这个方法执行之后, 如果对象实现了
     * <code>HttpSessionBindingListener</code>, 容器将调用这个对象的
     * <code>valueUnbound()</code>方法.
     *
     * @param name Name of the object to remove from this session.
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public void removeAttribute(String name) {
        removeAttribute(name, true);
    }


    /**
     * 删除指定名称的属性. 如果属性不存在, 什么都不做.
     * <p>
     * 这个方法执行之后, 如果对象实现了
     * <code>HttpSessionBindingListener</code>, 容器将调用这个对象的
     * <code>valueUnbound()</code>方法.
     *
     * @param name Name of the object to remove from this session.
     * @param notify 是否通知内部监听器?
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public void removeAttribute(String name, boolean notify) {

        // 验证当前状态
        if (!isValid())
            throw new IllegalStateException
                (sm.getString("standardSession.removeAttribute.ise"));

        removeAttributeInternal(name, notify);
    }


    /**
     * 移除指定名称的属性. 如果属性不存在, 什么都不做.
     * <p>
     * 这个方法执行之后, 如果对象实现了
     * <code>HttpSessionBindingListener</code>, 容器将调用这个对象的
     * <code>valueUnbound()</code>方法.
     *
     * @param name Name of the object to remove from this session.
     *
     * @exception IllegalStateException 如果在无效会话上调用此方法
     *
     * @deprecated As of Version 2.2, this method is replaced by
     *  <code>removeAttribute()</code>
     */
    public void removeValue(String name) {
        removeAttribute(name);
    }


    /**
     * 设置指定名称的值. 
     * <p>
     * 这个方法执行之后, 如果对象实现了
     * <code>HttpSessionBindingListener</code>, 容器将调用这个对象的
     * <code>valueBound()</code>方法.
     *
     * @param name Name to which the object is bound, cannot be null
     * @param value Object to be bound, cannot be null
     *
     * @exception IllegalArgumentException 如果尝试添加一个非可序列化的对象在一个可分配的环境中.
     * @exception IllegalStateException 如果在无效会话上调用此方法
     */
    public void setAttribute(String name, Object value) {

        // Name cannot be null
        if (name == null)
            throw new IllegalArgumentException
                (sm.getString("standardSession.setAttribute.namenull"));

        // Null value is the same as removeAttribute()
        if (value == null) {
            removeAttribute(name);
            return;
        }

        // Validate our current state
        if (!isValid())
            throw new IllegalStateException
                (sm.getString("standardSession.setAttribute.ise"));
        if ((manager != null) && manager.getDistributable() &&
          !(value instanceof Serializable))
            throw new IllegalArgumentException
                (sm.getString("standardSession.setAttribute.iae"));

        // Construct an event with the new value
        HttpSessionBindingEvent event = null;

        // Call the valueBound() method if necessary
        if (value instanceof HttpSessionBindingListener) {
            // 如果是相同的值不要调用通知
            Object oldValue = attributes.get(name);
            if (value != oldValue) {
                event = new HttpSessionBindingEvent(getSession(), name, value);
                try {
                    ((HttpSessionBindingListener) value).valueBound(event);
                } catch (Throwable t){
                    manager.getContainer().getLogger().error
                    (sm.getString("standardSession.bindingEvent"), t); 
                }
            }
        }

        // 替换或添加
        Object unbound = attributes.put(name, value);

        // Call the valueUnbound() method if necessary
        if ((unbound != null) && (unbound != value) &&
            (unbound instanceof HttpSessionBindingListener)) {
            try {
                ((HttpSessionBindingListener) unbound).valueUnbound
                    (new HttpSessionBindingEvent(getSession(), name));
            } catch (Throwable t) {
                manager.getContainer().getLogger().error
                    (sm.getString("standardSession.bindingEvent"), t);
            }
        }

        // 通知感兴趣的应用事件监听器
        Context context = (Context) manager.getContainer();
        Object listeners[] = context.getApplicationEventListeners();
        if (listeners == null)
            return;
        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof HttpSessionAttributeListener))
                continue;
            HttpSessionAttributeListener listener =
                (HttpSessionAttributeListener) listeners[i];
            try {
                if (unbound != null) {
                    fireContainerEvent(context,
                                       "beforeSessionAttributeReplaced",
                                       listener);
                    if (event == null) {
                        event = new HttpSessionBindingEvent
                            (getSession(), name, unbound);
                    }
                    listener.attributeReplaced(event);
                    fireContainerEvent(context,
                                       "afterSessionAttributeReplaced",
                                       listener);
                } else {
                    fireContainerEvent(context,
                                       "beforeSessionAttributeAdded",
                                       listener);
                    if (event == null) {
                        event = new HttpSessionBindingEvent
                            (getSession(), name, value);
                    }
                    listener.attributeAdded(event);
                    fireContainerEvent(context,
                                       "afterSessionAttributeAdded",
                                       listener);
                }
            } catch (Throwable t) {
                try {
                    if (unbound != null) {
                        fireContainerEvent(context,
                                           "afterSessionAttributeReplaced",
                                           listener);
                    } else {
                        fireContainerEvent(context,
                                           "afterSessionAttributeAdded",
                                           listener);
                    }
                } catch (Exception e) {
                    ;
                }
                manager.getContainer().getLogger().error
                    (sm.getString("standardSession.attributeEvent"), t);
            }
        }
    }


    // ------------------------------------------ HttpSession Protected Methods


    /**
     * 从指定的对象输入流中读取此会话对象的序列化版本.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>: 此方法没有恢复对所属Manager的引用 , 必须明确设置.
     *
     * @param stream 要从中读取的输入流
     *
     * @exception ClassNotFoundException 如果指定了未知类
     * @exception IOException 如果发生输入/输出错误
     */
    private void readObject(ObjectInputStream stream)
        throws ClassNotFoundException, IOException {

        // 反序列化scalar 实例变量(except Manager)
        authType = null;        // Transient only
        creationTime = ((Long) stream.readObject()).longValue();
        lastAccessedTime = ((Long) stream.readObject()).longValue();
        maxInactiveInterval = ((Integer) stream.readObject()).intValue();
        isNew = ((Boolean) stream.readObject()).booleanValue();
        isValid = ((Boolean) stream.readObject()).booleanValue();
        thisAccessedTime = ((Long) stream.readObject()).longValue();
        principal = null;        // Transient only
        //        setId((String) stream.readObject());
        id = (String) stream.readObject();
        if (manager.getContainer().getLogger().isDebugEnabled())
            manager.getContainer().getLogger().debug
                ("readObject() loading session " + id);

        // Deserialize the attribute count and attribute values
        if (attributes == null)
            attributes = new Hashtable();
        int n = ((Integer) stream.readObject()).intValue();
        boolean isValidSave = isValid;
        isValid = true;
        for (int i = 0; i < n; i++) {
            String name = (String) stream.readObject();
            Object value = (Object) stream.readObject();
            if ((value instanceof String) && (value.equals(NOT_SERIALIZED)))
                continue;
            if (manager.getContainer().getLogger().isDebugEnabled())
                manager.getContainer().getLogger().debug("  loading attribute '" + name +
                    "' with value '" + value + "'");
            attributes.put(name, value);
        }
        isValid = isValidSave;

        if (listeners == null) {
            listeners = new ArrayList();
        }

        if (notes == null) {
            notes = new Hashtable();
        }
    }


    /**
     * 将这个会话对象的序列化版本写入指定的对象输出流.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>: 所属Manager不会存储在这个会话的序列化表示中. 
     * 调用<code>readObject()</code>方法之后, 必须显式地设置关联的Manager .
     * <p>
     * <b>IMPLEMENTATION NOTE</b>: 任何属性，不可序列化将从会话中解绑, 适当的行动，如果它实现了HttpSessionBindingListener. 
     * 如果您不想要任何这样的属性, 确保<code>distributable</code>属性被设置为<code>true</code>.
     *
     * @param stream 要写入的输出流
     *
     * @exception IOException 如果发生输入/输出错误
     */
    private void writeObject(ObjectOutputStream stream) throws IOException {

        // 写入scalar 实例变量(except Manager)
        stream.writeObject(new Long(creationTime));
        stream.writeObject(new Long(lastAccessedTime));
        stream.writeObject(new Integer(maxInactiveInterval));
        stream.writeObject(new Boolean(isNew));
        stream.writeObject(new Boolean(isValid));
        stream.writeObject(new Long(thisAccessedTime));
        stream.writeObject(id);
        if (manager.getContainer().getLogger().isDebugEnabled())
            manager.getContainer().getLogger().debug
                ("writeObject() storing session " + id);

        // Accumulate the names of serializable and non-serializable attributes
        String keys[] = keys();
        ArrayList saveNames = new ArrayList();
        ArrayList saveValues = new ArrayList();
        for (int i = 0; i < keys.length; i++) {
            Object value = attributes.get(keys[i]);
            if (value == null)
                continue;
            else if ( (value instanceof Serializable) 
                    && (!exclude(keys[i]) )) {
                saveNames.add(keys[i]);
                saveValues.add(value);
            } else {
                removeAttributeInternal(keys[i], true);
            }
        }

        // 序列化属性计数和Serializable属性
        int n = saveNames.size();
        stream.writeObject(new Integer(n));
        for (int i = 0; i < n; i++) {
            stream.writeObject((String) saveNames.get(i));
            try {
                stream.writeObject(saveValues.get(i));
                if (manager.getContainer().getLogger().isDebugEnabled())
                    manager.getContainer().getLogger().debug
                        ("  storing attribute '" + saveNames.get(i) +
                        "' with value '" + saveValues.get(i) + "'");
            } catch (NotSerializableException e) {
                manager.getContainer().getLogger().warn
                    (sm.getString("standardSession.notSerializable",
                     saveNames.get(i), id), e);
                stream.writeObject(NOT_SERIALIZED);
                if (manager.getContainer().getLogger().isDebugEnabled())
                    manager.getContainer().getLogger().debug
                       ("  storing attribute '" + saveNames.get(i) +
                        "' with value NOT_SERIALIZED");
            }
        }
    }


    /**
     * 排除不能序列化的属性.
     * @param name 属性名称
     */
    protected boolean exclude(String name){
        for (int i = 0; i < excludedAttributes.length; i++) {
            if (name.equalsIgnoreCase(excludedAttributes[i]))
                return true;
        }
        return false;
    }


    protected void evaluateIfValid() {
        /*
	     * 如果此会话已过期或即将到期或将永不过期，则返回
	     */
        if (!this.isValid || expiring || maxInactiveInterval < 0)
            return;

        isValid();
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 触发容器事件，如果Context实现类是
     * <code>org.apache.catalina.core.StandardContext</code>.
     *
     * @param context Context for which to fire events
     * @param type Event type
     * @param data Event data
     *
     * @exception Exception occurred during event firing
     */
    protected void fireContainerEvent(Context context,
                                    String type, Object data)
        throws Exception {

        if (!"org.apache.catalina.core.StandardContext".equals
            (context.getClass().getName())) {
            return; // Container events are not supported
        }
        // NOTE:  Race condition is harmless, so do not synchronize
        if (containerEventMethod == null) {
            containerEventMethod =
                context.getClass().getMethod("fireContainerEvent",
                                             containerEventTypes);
        }
        Object containerEventParams[] = new Object[2];
        containerEventParams[0] = type;
        containerEventParams[1] = data;
        containerEventMethod.invoke(context, containerEventParams);
    }
                                      


    /**
     * 通知所有会话事件监听器这个会话发生了一个特殊事件.
     * 默认实现使用调用线程同步执行此通知.
     *
     * @param type Event type
     * @param data Event data
     */
    public void fireSessionEvent(String type, Object data) {
        if (listeners.size() < 1)
            return;
        SessionEvent event = new SessionEvent(this, type, data);
        SessionListener list[] = new SessionListener[0];
        synchronized (listeners) {
            list = (SessionListener[]) listeners.toArray(list);
        }

        for (int i = 0; i < list.length; i++){
            ((SessionListener) list[i]).sessionEvent(event);
        }
    }


    /**
     * 将所有当前定义的会话属性的名称作为字符串数组返回.
     * 如果没有, 返回零长度数组.
     */
    protected String[] keys() {
        return ((String[]) attributes.keySet().toArray(EMPTY_ARRAY));
    }


    /**
     * 从该会话中删除具有指定名称的对象.  如果没有对应的对象, 什么都不做.
     * <p>
     * 这个方法执行之后, 如果对象实现了<code>HttpSessionBindingListener</code>, 容器调用对象上的<code>valueUnbound()</code>方法.
     *
     * @param name 要移除的对象的名称.
     * @param notify 是否通知监听器?
     */
    protected void removeAttributeInternal(String name, boolean notify) {

        // 删除属性
        Object value = attributes.remove(name);

        // Do we need to do valueUnbound() and attributeRemoved() notification?
        if (!notify || (value == null)) {
            return;
        }

        // Call the valueUnbound() method if necessary
        HttpSessionBindingEvent event = null;
        if (value instanceof HttpSessionBindingListener) {
            event = new HttpSessionBindingEvent(getSession(), name, value);
            ((HttpSessionBindingListener) value).valueUnbound(event);
        }

        // 通知相关应用事件监听器
        Context context = (Context) manager.getContainer();
        Object listeners[] = context.getApplicationEventListeners();
        if (listeners == null)
            return;
        for (int i = 0; i < listeners.length; i++) {
            if (!(listeners[i] instanceof HttpSessionAttributeListener))
                continue;
            HttpSessionAttributeListener listener =
                (HttpSessionAttributeListener) listeners[i];
            try {
                fireContainerEvent(context,
                                   "beforeSessionAttributeRemoved",
                                   listener);
                if (event == null) {
                    event = new HttpSessionBindingEvent
                        (getSession(), name, value);
                }
                listener.attributeRemoved(event);
                fireContainerEvent(context,
                                   "afterSessionAttributeRemoved",
                                   listener);
            } catch (Throwable t) {
                try {
                    fireContainerEvent(context,
                                       "afterSessionAttributeRemoved",
                                       listener);
                } catch (Exception e) {
                    ;
                }
                manager.getContainer().getLogger().error
                    (sm.getString("standardSession.attributeEvent"), t);
            }
        }
    }
}


// ------------------------------------------------------------ Protected Class


/**
 * @deprecated As of Java Servlet API 2.1 with no replacement.  The
 *  interface will be removed in a future version of this API.
 */
final class StandardSessionContext implements HttpSessionContext {


    protected HashMap dummy = new HashMap();

    /**
     * 返回在此上下文中定义的所有会话的会话标识符.
     *
     * @deprecated As of Java Servlet API 2.1 with no replacement.
     *  This method must return an empty <code>Enumeration</code>
     *  and will be removed in a future version of the API.
     */
    public Enumeration getIds() {
        return (new Enumerator(dummy));
    }


    /**
     * 返回指定ID的<code>HttpSession</code>.
     *
     * @param id Session identifier for which to look up a session
     *
     * @deprecated As of Java Servlet API 2.1 with no replacement.
     *  This method must return null and will be removed in a
     *  future version of the API.
     */
    public HttpSession getSession(String id) {
        return (null);
    }
}
