package org.apache.naming;

import javax.naming.Context;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;
import javax.management.MBeanServer;
import javax.management.MBeanRegistration;
import javax.management.AttributeChangeNotification;
import javax.management.Notification;

/**
 * Implementation of the NamingService JMX MBean.
 */
public final class NamingService extends NotificationBroadcasterSupport
    implements NamingServiceMBean, MBeanRegistration {
    
    
    // ----------------------------------------------------- Instance Variables
    
    
    /**
     * Status of the Slide domain.
     */
    private int state = STOPPED;
    
    
    /**
     * 通知序列号
     */
    private long sequenceNumber = 0;
    
    
    /**
     * 旧URL包值.
     */
    private String oldUrlValue = "";
    
    
    /**
     * 旧初始上下文值
     */
    private String oldIcValue = "";
    
    
    // ---------------------------------------------- MBeanRegistration Methods
    
    
    public ObjectName preRegister(MBeanServer server, ObjectName name)
        throws Exception {
        return new ObjectName(OBJECT_NAME);
    }
    
    
    public void postRegister(Boolean registrationDone) {
        if (!registrationDone.booleanValue())
            destroy();
    }
    
    
    public void preDeregister()
        throws Exception {
    }
    
    
    public void postDeregister() {
        destroy();
    }
    
    
    // ----------------------------------------------------- SlideMBean Methods
    
    
    /**
     * 返回Catalina组件名称
     */
    public String getName() {
        return NAME;
    }
    
    
    /**
     * 返回状态
     */
    public int getState() {
        return state;
    }
    
    
    /**
     * 返回状态字符串
     */
    public String getStateString() {
        return states[state];
    }
    
    
    /**
     * Start the servlet container.
     */
    public void start()
        throws Exception {
        
        Notification notification = null;
        
        if (state != STOPPED)
            return;
        
        state = STARTING;
        
        // Notifying the MBEan server that we're starting
        notification = new AttributeChangeNotification
            (this, sequenceNumber++, System.currentTimeMillis(), 
             "Starting " + NAME, "State", "java.lang.Integer", 
             new Integer(STOPPED), new Integer(STARTING));
        sendNotification(notification);
        
        try {
            
            String value = "org.apache.naming";
            String oldValue = System.getProperty(Context.URL_PKG_PREFIXES);
            if (oldValue != null) {
                oldUrlValue = oldValue;
                value = oldValue + ":" + value;
            }
            System.setProperty(Context.URL_PKG_PREFIXES, value);
            
            oldValue = System.getProperty(Context.INITIAL_CONTEXT_FACTORY);
            if ((oldValue != null) && (oldValue.length() > 0)) {
                oldIcValue = oldValue;
            } else {
                System.setProperty(Context.INITIAL_CONTEXT_FACTORY,
                                   Constants.Package 
                                   + ".java.javaURLContextFactory");
            }
            
        } catch (Throwable t) {
            state = STOPPED;
            notification = new AttributeChangeNotification
                (this, sequenceNumber++, System.currentTimeMillis(), 
                 "Stopped " + NAME, "State", "java.lang.Integer", 
                 new Integer(STARTING), new Integer(STOPPED));
            sendNotification(notification);
        }
        
        state = STARTED;
        notification = new AttributeChangeNotification
            (this, sequenceNumber++, System.currentTimeMillis(), 
             "Started " + NAME, "State", "java.lang.Integer", 
             new Integer(STARTING), new Integer(STARTED));
        sendNotification(notification);
        
    }
    
    
    /**
     * Stop the servlet container.
     */
    public void stop() {
        
        Notification notification = null;
        
        if (state != STARTED)
            return;
        
        state = STOPPING;
        
        notification = new AttributeChangeNotification
            (this, sequenceNumber++, System.currentTimeMillis(), 
             "Stopping " + NAME, "State", "java.lang.Integer", 
             new Integer(STARTED), new Integer(STOPPING));
        sendNotification(notification);
        
        try {
            System.setProperty(Context.URL_PKG_PREFIXES, oldUrlValue);
            System.setProperty(Context.INITIAL_CONTEXT_FACTORY, oldIcValue);
        } catch (Throwable t) {
            // FIXME
            t.printStackTrace();
        }
        
        state = STOPPED;
        
        notification = new AttributeChangeNotification
            (this, sequenceNumber++, System.currentTimeMillis(), 
             "Stopped " + NAME, "State", "java.lang.Integer", 
             new Integer(STOPPING), new Integer(STOPPED));
        sendNotification(notification);
    }
    
    
    /**
     * Destroy servlet container (if any is running).
     */
    public void destroy() {
        if (getState() != STOPPED)
            stop();
    }
}
