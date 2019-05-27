package org.apache.catalina.session;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Manager;
import org.apache.catalina.Store;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;

/**
 * Store接口的抽象实现类提供了大多数Store所需的功能.
 */
public abstract class StoreBase implements Lifecycle, Store {

    // ----------------------------------------------------- Instance Variables

    /**
     * 实现类描述信息.
     */
    protected static String info = "StoreBase/1.0";

    /**
     * 这个Store注册名称, 用于记录日志.
     */
    protected static String storeName = "StoreBase";

    /**
     * 是否已启动?
     */
    protected boolean started = false;

    /**
     * 生命周期事件支持.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);

    /**
     * 属性修改支持.
     */
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);

    /**
     * The string manager for this package.
     */
    protected StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * JDBCStore关联的Manager.
     */
    protected Manager manager;

    // ------------------------------------------------------------- Properties

    /**
     * 返回这个Store的信息.
     */
    public String getInfo() {
        return(info);
    }


    /**
     * 返回这个Store的名称, 用于记录日志.
     */
    public String getStoreName() {
        return(storeName);
    }


    /**
     * 设置关联的Manager.
     *
     * @param manager The newly associated Manager
     */
    public void setManager(Manager manager) {
        Manager oldManager = this.manager;
        this.manager = manager;
        support.firePropertyChange("manager", oldManager, this.manager);
    }

    /**
     * 返回关联的Manager.
     */
    public Manager getManager() {
        return(this.manager);
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 添加生命周期事件监听器.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }


    /**
     * 获取所有生命周期事件监听器.
     * 如果没有找到, 返回零长度数组.
     */
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }


    /**
     * 移除生命周期事件监听器.
     *
     * @param listener The listener to add
     */
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }

    /**
     * 添加属性修改监听器.
     *
     * @param listener a value of type 'PropertyChangeListener'
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }

    /**
     * 移除属性修改监听器
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        support.removePropertyChangeListener(listener);
    }

    // --------------------------------------------------------- Protected Methods

    /**
     * 由后台线程调用，以检查保存在存储中的会话是否过期.
     * 如果是这样，则终止Session并将其从Store中删除.
     */
    public void processExpires() {
        long timeNow = System.currentTimeMillis();
        String[] keys = null;

         if(!started) {
            return;
        }

        try {
            keys = keys();
        } catch (IOException e) {
            manager.getContainer().getLogger().error("Error getting keys", e);
            return;
        }
        if (manager.getContainer().getLogger().isDebugEnabled()) {
            manager.getContainer().getLogger().debug(getStoreName()+ ": processExpires check number of " + keys.length + " sessions" );
        }
    
        for (int i = 0; i < keys.length; i++) {
            try {
                StandardSession session = (StandardSession) load(keys[i]);
                if (session == null) {
                    continue;
                }
                if (session.isValid()) {
                    continue;
                }
                if (manager.getContainer().getLogger().isDebugEnabled()) {
                    manager.getContainer().getLogger().debug(getStoreName()+ ": processExpires expire store session " + keys[i] );
                }
                if ( ( (PersistentManagerBase) manager).isLoaded( keys[i] )) {
                    // recycle old backup session
                    session.recycle();
                } else {
                    // expire swapped out session
                    session.expire();
                }
                remove(session.getIdInternal());
            } catch (Exception e) {
                manager.getContainer().getLogger().error("Session: "+keys[i]+"; ", e);
                try {
                    remove(keys[i]);
                } catch (IOException e2) {
                    manager.getContainer().getLogger().error("Error removing key", e2);
                }
            }
        }
    }


    // --------------------------------------------------------- Thread Methods


    /**
     * 这个方法在<code>configure()</code>方法之后调用, 在其他方法之前调用.
     *
     * @exception LifecycleException 如果此组件检测到防止使用该组件的致命错误
     */
    public void start() throws LifecycleException {
        // Validate and update our current component state
        if (started)
            throw new LifecycleException
                (sm.getString(getStoreName()+".alreadyStarted"));
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

    }


    /**
     * 这个方法应该最后一个调用.
     *
     * @exception LifecycleException 如果此组件检测到需要报告的致命错误
     */
    public void stop() throws LifecycleException {
        // Validate and update our current component state
        if (!started)
            throw new LifecycleException
                (sm.getString(getStoreName()+".notStarted"));
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;
    }
}
