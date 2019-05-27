package org.apache.naming;

/**
 * Naming MBean interface.
 */
public interface NamingServiceMBean {
    
    // -------------------------------------------------------------- Constants
    
    /**
     * 状态常量.
     */
    public static final String[] states = 
    {"Stopped", "Stopping", "Starting", "Started"};
    
    
    public static final int STOPPED  = 0;
    public static final int STOPPING = 1;
    public static final int STARTING = 2;
    public static final int STARTED  = 3;
    
    
    /**
     * 组件名称
     */
    public static final String NAME = "Apache JNDI Naming Service";
    
    
    /**
     * Object name.
     */
    public static final String OBJECT_NAME = ":service=Naming";
    
    
    // ------------------------------------------------------ Interface Methods
    
    
    /**
     * 返回JNDI的组件名称
     */
    public String getName();
    
    
    /**
     * 返回状态
     */
    public int getState();
    
    
    /**
     * 返回状态字符串.
     */
    public String getStateString();
    
    
    /**
     * Start the servlet container.
     */
    public void start() throws Exception;
    
    
    /**
     * Stop the servlet container.
     */
    public void stop();
    
    
    /**
     * Destroy servlet container (if any is running).
     */
    public void destroy();
    
}
