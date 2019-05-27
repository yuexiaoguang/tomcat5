package org.apache.catalina.session;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Iterator;
import javax.servlet.ServletContext;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Loader;
import org.apache.catalina.Session;
import org.apache.catalina.util.CustomObjectInputStream;
import org.apache.catalina.util.LifecycleSupport;

import org.apache.catalina.security.SecurityUtil;

/**
 * <b>Manager</b>接口的标准实现类， 在重新启动时提供简单会话持久性(例如，当整个服务器关闭并重新启动时, 或者当一个特定的Web应用程序加载).
 * <p>
 * <b>实现注意</b>: 会话存储和重新加载的正确行为取决于外部调用这个类的<code>start()</code>
 * 和<code>stop()</code>方法在正确的时间.
 */
public class StandardManager extends ManagerBase implements Lifecycle, PropertyChangeListener {

    // ---------------------------------------------------- Security Classes
    private class PrivilegedDoLoad
        implements PrivilegedExceptionAction {

        PrivilegedDoLoad() {
        }

        public Object run() throws Exception{
           doLoad();
           return null;
        }
    }

    private class PrivilegedDoUnload
        implements PrivilegedExceptionAction {

        PrivilegedDoUnload() {
        }

        public Object run() throws Exception{
            doUnload();
            return null;
        }

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 实现类描述信息
     */
    protected static final String info = "StandardManager/1.0";


    /**
     * 生命周期事件支持
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * 允许活动会话的最大数目, 或 -1 没有限制.
     */
    protected int maxActiveSessions = -1;


    /**
     * 这个Manager实现类的名称 (用于记录日志).
     */
    protected static String name = "StandardManager";


    /**
     * 当停止活动会话时保存的活动磁盘文件的路径名, 开始时加载这些会话.
     * <code>null</code>值表示不需要持久性.
     * 如果路径名是相对的, 它将根据上下文提供的临时工作目录解决, 通过<code>javax.servlet.context.tempdir</code>上下文属性可用.
     */
    protected String pathname = "SESSIONS.ser";


    /**
     * 是否已启动?
     */
    protected boolean started = false;


    /**
     * 由于maxActiveSessions的限制创建失败的会话数量.
     */
    protected int rejectedSessions = 0;


    /**
     * 会话过期期间的处理时间.
     */
    protected long processingTime = 0;


    // ------------------------------------------------------------- Properties


    /**
     * 设置关联的Container.
     * 如果是一个Context (通常情况), 监听会话超时属性的更改.
     *
     * @param container The associated Container
     */
    public void setContainer(Container container) {
        // De-register from the old Container (if any)
        if ((this.container != null) && (this.container instanceof Context))
            ((Context) this.container).removePropertyChangeListener(this);

        // Default processing provided by our superclass
        super.setContainer(container);

        // Register with the new Container (if any)
        if ((this.container != null) && (this.container instanceof Context)) {
            setMaxInactiveInterval
                ( ((Context) this.container).getSessionTimeout()*60 );
            ((Context) this.container).addPropertyChangeListener(this);
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
     * 返回允许的活动会话的最大数目, 或者 -1 不做限制.
     */
    public int getMaxActiveSessions() {
        return (this.maxActiveSessions);
    }


    /** 由于maxActiveSessions的限制创建失败的会话数量.
     *
     * @return The count
     */
    public int getRejectedSessions() {
        return rejectedSessions;
    }


    public void setRejectedSessions(int rejectedSessions) {
        this.rejectedSessions = rejectedSessions;
    }


    /**
     * 设置允许的活动会话的最大数目, 或者 -1 不做限制.
     *
     * @param max The new maximum number of sessions
     */
    public void setMaxActiveSessions(int max) {
        int oldMaxActiveSessions = this.maxActiveSessions;
        this.maxActiveSessions = max;
        support.firePropertyChange("maxActiveSessions",
                                   new Integer(oldMaxActiveSessions),
                                   new Integer(this.maxActiveSessions));
    }


    /**
     * 返回这个Manager实现类的名称
     */
    public String getName() {
        return (name);
    }


    /**
     * 返回会话持久的路径.
     */
    public String getPathname() {
        return (this.pathname);
    }


    /**
     * 设置会话持久性路径来指定值.
     * 如果不需要持久性支持, 设置路径名为 <code>null</code>.
     *
     * @param pathname New session persistence pathname
     */
    public void setPathname(String pathname) {
        String oldPathname = this.pathname;
        this.pathname = pathname;
        support.firePropertyChange("pathname", oldPathname, this.pathname);
    }


    // --------------------------------------------------------- Public Methods

    /**
     * 创建一个新的会话对象, 基于此Manager属性指定的默认设置.
     * 此方法将分配会话id, 可以通过会话的getId()方法获取. 如果不能创建一个新的会话对象, 返回<code>null</code>.
     *
     * @exception IllegalStateException 如果不能创建新会话
     */
    public Session createSession(String sessionId) {
        if ((maxActiveSessions >= 0) &&
            (sessions.size() >= maxActiveSessions)) {
            rejectedSessions++;
            throw new IllegalStateException
                (sm.getString("standardManager.createSession.ise"));
        }
        return (super.createSession(sessionId));
    }


    /**
     * 将以前卸载的当前活动会话加载到适当的持久化机制. 
     * 如果不支持持久化, 这个方法不做任何事情就返回.
     *
     * @exception ClassNotFoundException 如果在重新加载期间找不到序列化类
     * @exception IOException if an input/output error occurs
     */
    public void load() throws ClassNotFoundException, IOException {
        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                AccessController.doPrivileged( new PrivilegedDoLoad() );
            } catch (PrivilegedActionException ex){
                Exception exception = ex.getException();
                if (exception instanceof ClassNotFoundException){
                    throw (ClassNotFoundException)exception;
                } else if (exception instanceof IOException){
                    throw (IOException)exception;
                }
                if (log.isDebugEnabled())
                    log.debug("Unreported exception in load() "
                        + exception);
            }
        } else {
            doLoad();
        }
    }


    /**
     * 将以前卸载的当前活动会话加载到适当的持久化机制. 
     * 如果不支持持久化, 这个方法不做任何事情就返回.
     *
     * @exception ClassNotFoundException 如果在重新加载期间找不到序列化类
     * @exception IOException if an input/output error occurs
     */
    protected void doLoad() throws ClassNotFoundException, IOException {
        if (log.isDebugEnabled())
            log.debug("Start: Loading persisted sessions");

        // 初始化内部数据结构
        sessions.clear();

        // 打开一个输入流到指定的路径
        File file = file();
        if (file == null)
            return;
        if (log.isDebugEnabled())
            log.debug(sm.getString("standardManager.loading", pathname));
        FileInputStream fis = null;
        ObjectInputStream ois = null;
        Loader loader = null;
        ClassLoader classLoader = null;
        try {
            fis = new FileInputStream(file.getAbsolutePath());
            BufferedInputStream bis = new BufferedInputStream(fis);
            if (container != null)
                loader = container.getLoader();
            if (loader != null)
                classLoader = loader.getClassLoader();
            if (classLoader != null) {
                if (log.isDebugEnabled())
                    log.debug("Creating custom object input stream for class loader ");
                ois = new CustomObjectInputStream(bis, classLoader);
            } else {
                if (log.isDebugEnabled())
                    log.debug("Creating standard object input stream");
                ois = new ObjectInputStream(bis);
            }
        } catch (FileNotFoundException e) {
            if (log.isDebugEnabled())
                log.debug("No persisted data file found");
            return;
        } catch (IOException e) {
            log.error(sm.getString("standardManager.loading.ioe", e), e);
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException f) {
                    ;
                }
                ois = null;
            }
            throw e;
        }

        // 加载以前卸载的活动会话
        synchronized (sessions) {
            try {
                Integer count = (Integer) ois.readObject();
                int n = count.intValue();
                if (log.isDebugEnabled())
                    log.debug("Loading " + n + " persisted sessions");
                for (int i = 0; i < n; i++) {
                    StandardSession session = getNewSession();
                    session.readObjectData(ois);
                    session.setManager(this);
                    sessions.put(session.getIdInternal(), session);
                    session.activate();
                    session.endAccess();
                }
            } catch (ClassNotFoundException e) {
              log.error(sm.getString("standardManager.loading.cnfe", e), e);
                if (ois != null) {
                    try {
                        ois.close();
                    } catch (IOException f) {
                        ;
                    }
                    ois = null;
                }
                throw e;
            } catch (IOException e) {
              log.error(sm.getString("standardManager.loading.ioe", e), e);
                if (ois != null) {
                    try {
                        ois.close();
                    } catch (IOException f) {
                        ;
                    }
                    ois = null;
                }
                throw e;
            } finally {
                // Close the input stream
                try {
                    if (ois != null)
                        ois.close();
                } catch (IOException f) {
                    // ignored
                }

                // Delete the persistent storage file
                if (file != null && file.exists() )
                    file.delete();
            }
        }

        if (log.isDebugEnabled())
            log.debug("Finish: Loading persisted sessions");
    }


    /**
     * 在适当的持久性机制中保存当前活动的会话. 
     * 如果不支持持久性, 这个方法不做任何事情就返回.
     *
     * @exception IOException if an input/output error occurs
     */
    public void unload() throws IOException {
        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                AccessController.doPrivileged( new PrivilegedDoUnload() );
            } catch (PrivilegedActionException ex){
                Exception exception = ex.getException();
                if (exception instanceof IOException){
                    throw (IOException)exception;
                }
                if (log.isDebugEnabled())
                    log.debug("Unreported exception in unLoad() "
                        + exception);
            }
        } else {
            doUnload();
        }
    }


    /**
     * 在适当的持久性机制中保存当前活动的会话. 
     * 如果不支持持久性, 这个方法不做任何事情就返回.
     *
     * @exception IOException if an input/output error occurs
     */
    protected void doUnload() throws IOException {

        if (log.isDebugEnabled())
            log.debug("Unloading persisted sessions");

        // 打开输出流到指定的路径
        File file = file();
        if (file == null)
            return;
        if (log.isDebugEnabled())
            log.debug(sm.getString("standardManager.unloading", pathname));
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fos = new FileOutputStream(file.getAbsolutePath());
            oos = new ObjectOutputStream(new BufferedOutputStream(fos));
        } catch (IOException e) {
            log.error(sm.getString("standardManager.unloading.ioe", e), e);
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException f) {
                    ;
                }
                oos = null;
            }
            throw e;
        }

        // 写出活动会话的数量, 详情后面
        ArrayList list = new ArrayList();
        synchronized (sessions) {
            if (log.isDebugEnabled())
                log.debug("Unloading " + sessions.size() + " sessions");
            try {
                oos.writeObject(new Integer(sessions.size()));
                Iterator elements = sessions.values().iterator();
                while (elements.hasNext()) {
                    StandardSession session =
                        (StandardSession) elements.next();
                    list.add(session);
                    ((StandardSession) session).passivate();
                    session.writeObjectData(oos);
                }
            } catch (IOException e) {
                log.error(sm.getString("standardManager.unloading.ioe", e), e);
                if (oos != null) {
                    try {
                        oos.close();
                    } catch (IOException f) {
                        ;
                    }
                    oos = null;
                }
                throw e;
            }
        }

        // Flush and close the output stream
        try {
            oos.flush();
            oos.close();
            oos = null;
        } catch (IOException e) {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException f) {
                    ;
                }
                oos = null;
            }
            throw e;
        }

        // 终止刚才写入的所有会话
        if (log.isDebugEnabled())
            log.debug("Expiring " + list.size() + " persisted sessions");
        Iterator expires = list.iterator();
        while (expires.hasNext()) {
            StandardSession session = (StandardSession) expires.next();
            try {
                session.expire(false);
            } catch (Throwable t) {
                ;
            } finally {
                session.recycle();
            }
        }

        if (log.isDebugEnabled())
            log.debug("Unloading complete");

    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * 添加生命周期事件监听器.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }


    /**
     * 获取所有生命周期事件监听器. 或者返回一个零长度数组.
     */
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }


    /**
     * 移除生命周期事件监听器.
     *
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }

    /**
     * 这个方法应该在<code>configure()</code>方法之后调用, 并在其他方法之前调用.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    public void start() throws LifecycleException {

        if( ! initialized )
            init();

        // Validate and update our current component state
        if (started) {
            return;
        }
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        // Force initialization of the random number generator
        if (log.isDebugEnabled())
            log.debug("Force random number initialization starting");
        String dummy = generateSessionId();
        if (log.isDebugEnabled())
            log.debug("Force random number initialization completed");

        // Load unloaded sessions, if any
        try {
            load();
        } catch (Throwable t) {
            log.error(sm.getString("standardManager.managerLoad"), t);
        }

    }


    /**
     * 这个方法应该最后一个调用.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public void stop() throws LifecycleException {

        if (log.isDebugEnabled())
            log.debug("Stopping");

        // Validate and update our current component state
        if (!started)
            throw new LifecycleException
                (sm.getString("standardManager.notStarted"));
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        // Write out sessions
        try {
            unload();
        } catch (Throwable t) {
            log.error(sm.getString("standardManager.managerUnload"), t);
        }

        // Expire all active sessions
        Session sessions[] = findSessions();
        for (int i = 0; i < sessions.length; i++) {
            StandardSession session = (StandardSession) sessions[i];
            try {
                if (session.isValid()) {
                    session.expire();
                }
            } catch (Throwable t) {
                ;
            } finally {
                // 如果对会话对象的引用保存在某个共享字段中，则可以防止内存泄漏
                session.recycle();
            }
        }

        // 如果重新启动，需要一个新的随机数生成器
        this.random = null;

        if( initialized ) {
            destroy();
        }
    }


    // ----------------------------------------- PropertyChangeListener Methods


    /**
     * 从关联的Context处理属性修改事件.
     *
     * @param event 发生的属性修改事件
     */
    public void propertyChange(PropertyChangeEvent event) {

        // Validate the source of this event
        if (!(event.getSource() instanceof Context))
            return;
        Context context = (Context) event.getSource();

        // Process a relevant property change
        if (event.getPropertyName().equals("sessionTimeout")) {
            try {
                setMaxInactiveInterval
                    ( ((Integer) event.getNewValue()).intValue()*60 );
            } catch (NumberFormatException e) {
                log.error(sm.getString("standardManager.sessionTimeout",
                                 event.getNewValue().toString()));
            }
        }
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 持久化文件的路径名.
     */
    protected File file() {

        if ((pathname == null) || (pathname.length() == 0))
            return (null);
        File file = new File(pathname);
        if (!file.isAbsolute()) {
            if (container instanceof Context) {
                ServletContext servletContext =
                    ((Context) container).getServletContext();
                File tempdir = (File)
                    servletContext.getAttribute(Globals.WORK_DIR_ATTR);
                if (tempdir != null)
                    file = new File(tempdir, pathname);
            }
        }
//        if (!file.isAbsolute())
//            return (null);
        return (file);
    }
}
