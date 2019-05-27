package org.apache.catalina.valves;


import java.io.IOException;
import java.util.concurrent.Semaphore;

import javax.servlet.ServletException;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;


/**
 * <p>Valve的实现类限制了并发.</p>
 *
 * <p>这个Valve 可能附加到任何 Container, 取决于希望执行的并发控制的粒度.</p>
 */
public class SemaphoreValve extends ValveBase implements Lifecycle {


    // ----------------------------------------------------- Instance Variables


    /**
     * 实现类的描述信息.
     */
    private static final String info =
        "org.apache.catalina.valves.SemaphoreValve/1.0";


    /**
     * The string manager for this package.
     */
    private StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * Semaphore.
     */
    protected Semaphore semaphore = null;
    

    /**
     * 生命周期事件支持.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * 是否已启动?
     */
    private boolean started = false;


    // ------------------------------------------------------------- Properties

    
    /**
     * 信号量的并发级别.
     */
    protected int concurrency = 10;
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
    

    /**
     * 信号量的公平性.
     */
    protected boolean fairness = false;
    public boolean getFairness() { return fairness; }
    public void setFairness(boolean fairness) { this.fairness = fairness; }
    

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
     * 获取关联的所有生命周期事件监听器.
     * 如果没有, 返回零长度数组.
     */
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }


    /**
     * 删除生命周期事件监听器.
     *
     * @param listener The listener to add
     */
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }


    /**
     * 这个方法应该在<code>configure()</code>方法之后调用, 在其他方法之前调用.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    public void start() throws LifecycleException {
        // Validate and update our current component state
        if (started)
            throw new LifecycleException
                (sm.getString("semaphoreValve.alreadyStarted"));
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        semaphore = new Semaphore(concurrency, fairness);
    }


    /**
     * 这个方法应该最后一个调用.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public void stop() throws LifecycleException {
        // Validate and update our current component state
        if (!started)
            throw new LifecycleException
                (sm.getString("semaphoreValve.notStarted"));
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        semaphore = null;
    }

    
    // --------------------------------------------------------- Public Methods


    /**
     * 返回实现类的描述信息.
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 使用信号量对请求执行并发控制.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException {
        try {
            semaphore.acquireUninterruptibly();
            // Perform the request
            getNext().invoke(request, response);
        } finally {
            semaphore.release();
        }
    }
}
