package org.apache.catalina.core;

import java.util.ArrayList;

import javax.management.ObjectName;

import org.apache.catalina.Contained;
import org.apache.catalina.Container;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Valve;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;
import org.apache.catalina.valves.ValveBase;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.modeler.Registry;

/**
 * <b>Pipeline</b>的标准实现类，将执行一系列 Valves，已配置为按顺序调用. 
 * 此实现可用于任何类型的Container.
 *
 * <b>实施预警</b> - 此实现假定没有调用<code>addValve()</code>或<code>removeValve</code>是允许的，
 * 当一个请求正在处理的时候. 否则，就需要一个维护每个线程状态的机制.
 */
public class StandardPipeline implements Pipeline, Contained, Lifecycle {

    private static Log log = LogFactory.getLog(StandardPipeline.class);

    // ----------------------------------------------------------- Constructors

    public StandardPipeline() {
        this(null);
    }


    /**
     * @param container The container we should be associated with
     */
    public StandardPipeline(Container container) {
        super();
        setContainer(container);
    }

    // ----------------------------------------------------- Instance Variables

    /**
     * 关联的基础Valve.
     */
    protected Valve basic = null;


    /**
     * 关联的Container.
     */
    protected Container container = null;


    /**
     * 描述信息
     */
    protected String info = "org.apache.catalina.core.StandardPipeline/1.0";


    /**
     * 生命周期事件支持
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * The string manager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 该组件是否已经启动?
     */
    protected boolean started = false;


    /**
     * 关联的第一个valve.
     */
    protected Valve first = null;


    // --------------------------------------------------------- Public Methods


    /**
     * 返回描述信息
     */
    public String getInfo() {
        return (this.info);
    }


    // ------------------------------------------------------ Contained Methods


    /**
     * 返回关联的Container.
     */
    public Container getContainer() {
        return (this.container);
    }


    /**
     * 设置关联的Container.
     *
     * @param container The new associated container
     */
    public void setContainer(Container container) {
        this.container = container;
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * 添加一个生命周期事件监听器
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }


    /**
     * 返回关联的所有生命周期监听器.
     * 如果没有，返回一个零长度数组.
     */
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }


    /**
     * 移除一个生命周期事件监听器
     *
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }

    /**
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents it from being started
     */
    public synchronized void start() throws LifecycleException {

        // Validate and update our current component state
        if (started)
            throw new LifecycleException
                (sm.getString("standardPipeline.alreadyStarted"));

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_START_EVENT, null);

        started = true;

        // Start the Valves in our pipeline (including the basic), if any
        Valve current = first;
        if (current == null) {
        	current = basic;
        }
        while (current != null) {
            if (current instanceof Lifecycle)
                ((Lifecycle) current).start();
            registerValve(current);
        	current = current.getNext();
        }

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(START_EVENT, null);

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(AFTER_START_EVENT, null);

    }


    /**
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public synchronized void stop() throws LifecycleException {

        // Validate and update our current component state
        if (!started)
            throw new LifecycleException
                (sm.getString("standardPipeline.notStarted"));

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(BEFORE_STOP_EVENT, null);

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        // Stop the Valves in our pipeline (including the basic), if any
        Valve current = first;
        if (current == null) {
        	current = basic;
        }
        while (current != null) {
            if (current instanceof Lifecycle)
                ((Lifecycle) current).stop();
            unregisterValve(current);
        	current = current.getNext();
        }

        // Notify our interested LifecycleListeners
        lifecycle.fireLifecycleEvent(AFTER_STOP_EVENT, null);
    }

    private void registerValve(Valve valve) {

        if( valve instanceof ValveBase &&
                ((ValveBase)valve).getObjectName()==null ) {
            try {
                
                String domain=((ContainerBase)container).getDomain();
                if( container instanceof StandardContext ) {
                    domain=((StandardContext)container).getEngineName();
                }
                if( container instanceof StandardWrapper) {
                    Container ctx=((StandardWrapper)container).getParent();
                    domain=((StandardContext)ctx).getEngineName();
                }
                ObjectName vname=((ValveBase)valve).createObjectName(
                        domain,
                        ((ContainerBase)container).getJmxName());
                if( vname != null ) {
                    ((ValveBase)valve).setObjectName(vname);
                    Registry.getRegistry(null, null).registerComponent
                        (valve, vname, valve.getClass().getName());
                    ((ValveBase)valve).setController
                        (((ContainerBase)container).getJmxName());
                }
            } catch( Throwable t ) {
                log.info( "Can't register valve " + valve , t );
            }
        }
    }
    
    private void unregisterValve(Valve valve) {
        if( valve instanceof ValveBase ) {
            try {
                ValveBase vb=(ValveBase)valve;
                if( vb.getController()!=null &&
                        vb.getController() == 
                        ((ContainerBase)container).getJmxName() ) {
                    
                    ObjectName vname=vb.getObjectName();
                    Registry.getRegistry(null, null).getMBeanServer()
                        .unregisterMBean(vname);
                    ((ValveBase)valve).setObjectName(null);
                }
            } catch( Throwable t ) {
                log.info( "Can't unregister valve " + valve , t );
            }
        }
    }    

    // ------------------------------------------------------- Pipeline Methods


    /**
     * <p>返回设置的基础Valve.
     */
    public Valve getBasic() {
        return (this.basic);
    }


    /**
     * <p>设置基础Valve.  
     * 设置基础Valve之前,Valve的<code>setContainer()</code>将被调用, 如果它实现<code>Contained</code>, 
     * 以拥有的Container作为参数. 
     * 方法可能抛出一个<code>IllegalArgumentException</code>，
     * 如果这个Valve不能关联到这个Container,或者
     * <code>IllegalStateException</code>，如果它已经关联到另外一个Container.</p>
     *
     * @param valve Valve to be distinguished as the basic Valve
     */
    public void setBasic(Valve valve) {

        // Change components if necessary
        Valve oldBasic = this.basic;
        if (oldBasic == valve)
            return;

        // Stop the old component if necessary
        if (oldBasic != null) {
            if (started && (oldBasic instanceof Lifecycle)) {
                try {
                    ((Lifecycle) oldBasic).stop();
                } catch (LifecycleException e) {
                    log.error("StandardPipeline.setBasic: stop", e);
                }
            }
            if (oldBasic instanceof Contained) {
                try {
                    ((Contained) oldBasic).setContainer(null);
                } catch (Throwable t) {
                    ;
                }
            }
        }

        // Start the new component if necessary
        if (valve == null)
            return;
        if (valve instanceof Contained) {
            ((Contained) valve).setContainer(this.container);
        }
        if (valve instanceof Lifecycle) {
            try {
                ((Lifecycle) valve).start();
            } catch (LifecycleException e) {
                log.error("StandardPipeline.setBasic: start", e);
                return;
            }
        }

        // Update the pipeline
        Valve current = first;
        while (current != null) {
        	if (current.getNext() == oldBasic) {
        		current.setNext(valve);
        		break;
        	}
        	current = current.getNext();
        }
        this.basic = valve;
    }


    /**
     * <p>添加一个Valve到pipeline的结尾. 
     * 在添加Valve之前, Valve的<code>setContainer()</code>方法将被调用, 如果它实现了
     * <code>Contained</code>接口, 并将Container作为一个参数.
     * 该方法可能抛出一个<code>IllegalArgumentException</code>, 如果这个Valve不能关联到Container, 
     * 或者<code>IllegalStateException</code>，如果它已经关联到另外一个Container.</p>
     *
     * @param valve Valve to be added
     *
     * @exception IllegalArgumentException 如果这个Container不能接受指定的Valve
     * @exception IllegalArgumentException 如果指定的Valve拒绝关联到这个Container
     * @exception IllegalStateException 如果指定的Valve 已经关联到另外一个Container
     */
    public void addValve(Valve valve) {
    
        // Validate that we can add this Valve
        if (valve instanceof Contained)
            ((Contained) valve).setContainer(this.container);

        // Start the new component if necessary
        if (started) {
            if (valve instanceof Lifecycle) {
                try {
                    ((Lifecycle) valve).start();
                } catch (LifecycleException e) {
                    log.error("StandardPipeline.addValve: start: ", e);
                }
            }
            // Register the newly added valve
            registerValve(valve);
        }

        // Add this Valve to the set associated with this Pipeline
        if (first == null) {
        	first = valve;
        	valve.setNext(basic);
        } else {
            Valve current = first;
            while (current != null) {
				if (current.getNext() == basic) {
					current.setNext(valve);
					valve.setNext(basic);
					break;
				}
				current = current.getNext();
			}
        }
    }


    /**
     * 返回关联的pipeline中Valve的集合, 包括基础Valve. 
     * 如果没有, 返回一个零长度数组.
     */
    public Valve[] getValves() {

    	ArrayList valveList = new ArrayList();
        Valve current = first;
        if (current == null) {
        	current = basic;
        }
        while (current != null) {
        	valveList.add(current);
        	current = current.getNext();
        }
        return ((Valve[]) valveList.toArray(new Valve[0]));
    }

    public ObjectName[] getValveObjectNames() {

    	ArrayList valveList = new ArrayList();
        Valve current = first;
        if (current == null) {
        	current = basic;
        }
        while (current != null) {
        	if (current instanceof ValveBase) {
        		valveList.add(((ValveBase) current).getObjectName());
        	}
        	current = current.getNext();
        }
        return ((ObjectName[]) valveList.toArray(new ObjectName[0]));
    }

    /**
     * 从pipeline中移除指定的Valve; 否则，什么都不做.
     * 如果Valve被找到并被移除, Valve的<code>setContainer(null)</code>方法将被调用，如果它实现了<code>Contained</code>.
     *
     * @param valve Valve to be removed
     */
    public void removeValve(Valve valve) {

        Valve current;
        if(first == valve) {
            first = first.getNext();
            current = null;
        } else {
            current = first;
        }
        while (current != null) {
            if (current.getNext() == valve) {
                current.setNext(valve.getNext());
                break;
            }
            current = current.getNext();
        }

        if (valve instanceof Contained)
            ((Contained) valve).setContainer(null);

        // Stop this valve if necessary
        if (started) {
            if (valve instanceof Lifecycle) {
                try {
                    ((Lifecycle) valve).stop();
                } catch (LifecycleException e) {
                    log.error("StandardPipeline.removeValve: stop: ", e);
                }
            }
            // Unregister the removed valave
            unregisterValve(valve);
        }
    }


    public Valve getFirst() {
        if (first != null) {
            return first;
        } else {
            return basic;
        }
    }
}
