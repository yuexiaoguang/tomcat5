package org.apache.catalina.core;

import java.lang.reflect.Method;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <code>LifecycleListener</code>实现类将初始化和销毁 APR.
 */
public class AprLifecycleListener implements LifecycleListener {

    private static Log log = LogFactory.getLog(AprLifecycleListener.class);

    /**
     * The string manager for this package.
     */
    protected StringManager sm = StringManager.getManager(Constants.Package);

    
    // -------------------------------------------------------------- Constants


    protected static final int REQUIRED_MAJOR = 1;
    protected static final int REQUIRED_MINOR = 1;
    protected static final int REQUIRED_PATCH = 0;


    // ---------------------------------------------- LifecycleListener Methods


    /**
     * 启动和关闭事件的主要入口点.
     *
     * @param event 发生的事件
     */
    public void lifecycleEvent(LifecycleEvent event) {

        if (Lifecycle.INIT_EVENT.equals(event.getType())) {
            int major = 0;
            int minor = 0;
            int patch = 0;
            try {
                String methodName = "initialize";
                Class paramTypes[] = new Class[1];
                paramTypes[0] = String.class;
                Object paramValues[] = new Object[1];
                paramValues[0] = null;
                Class clazz = Class.forName("org.apache.tomcat.jni.Library");
                Method method = clazz.getMethod(methodName, paramTypes);
                method.invoke(null, paramValues);
                major = clazz.getField("TCN_MAJOR_VERSION").getInt(null);
                minor = clazz.getField("TCN_MINOR_VERSION").getInt(null);
                patch = clazz.getField("TCN_PATCH_VERSION").getInt(null);
            } catch (Throwable t) {
                if (!log.isDebugEnabled()) {
                    log.info(sm.getString("aprListener.aprInit", 
                            System.getProperty("java.library.path")));
                } else {
                    log.debug(sm.getString("aprListener.aprInit", 
                            System.getProperty("java.library.path")), t);
                }
                return;
            }
            if ((major != REQUIRED_MAJOR) || (minor != REQUIRED_MINOR)
                    || (patch < REQUIRED_PATCH)) {
                log.error(sm.getString("aprListener.tcnInvalid", major + "." 
                        + minor + "." + patch, REQUIRED_MAJOR + "." 
                        + REQUIRED_MINOR + "." + REQUIRED_PATCH));
            }
        } else if (Lifecycle.AFTER_STOP_EVENT.equals(event.getType())) {
            try {
                String methodName = "terminate";
                Method method = Class.forName("org.apache.tomcat.jni.Library")
                    .getMethod(methodName, null);
                method.invoke(null, null);
            } catch (Throwable t) {
                if (!log.isDebugEnabled()) {
                    log.info(sm.getString("aprListener.aprDestroy"));
                } else {
                    log.debug(sm.getString("aprListener.aprDestroy"), t);
                }
            }
        }
    }
}
