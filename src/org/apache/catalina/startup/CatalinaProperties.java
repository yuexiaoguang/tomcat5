package org.apache.catalina.startup;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;


/**
 * 读取引导Catalina 配置的工具类.
 */
public class CatalinaProperties {


    // ------------------------------------------------------- Static Variables

    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( CatalinaProperties.class );

    private static Properties properties = null;


    static {
        loadProperties();
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 返回指定的属性值.
     */
    public static String getProperty(String name) {
        return properties.getProperty(name);
    }


    /**
     * 返回指定的属性值.
     */
    public static String getProperty(String name, String defaultValue) {
        return properties.getProperty(name, defaultValue);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 加载属性.
     */
    private static void loadProperties() {

        InputStream is = null;
        Throwable error = null;

        try {
            String configUrl = getConfigUrl();
            if (configUrl != null) {
                is = (new URL(configUrl)).openStream();
            }
        } catch (Throwable t) {
            // Ignore
        }

        if (is == null) {
            try {
                File home = new File(getCatalinaBase());
                File conf = new File(home, "conf");
                File properties = new File(conf, "catalina.properties");
                is = new FileInputStream(properties);
            } catch (Throwable t) {
                // Ignore
            }
        }

        if (is == null) {
            try {
                is = CatalinaProperties.class.getResourceAsStream
                    ("/org/apache/catalina/startup/catalina.properties");
            } catch (Throwable t) {
                // Ignore
            }
        }

        if (is != null) {
            try {
                properties = new Properties();
                properties.load(is);
                is.close();
            } catch (Throwable t) {
                error = t;
            }
        }

        if ((is == null) || (error != null)) {
            // Do something
            log.warn("Failed to load catalina.properties", error);
        }

        // 将属性注册为系统属性
        Enumeration enumeration = properties.propertyNames();
        while (enumeration.hasMoreElements()) {
            String name = (String) enumeration.nextElement();
            String value = properties.getProperty(name);
            if (value != null) {
                System.setProperty(name, value);
            }
        }
    }


    /**
     * 获取catalina.home 系统变量的值.
     */
    private static String getCatalinaHome() {
        return System.getProperty("catalina.home", System.getProperty("user.dir"));
    }
    
    
    /**
     * 获取catalina.base 系统变量的值.
     */
    private static String getCatalinaBase() {
        return System.getProperty("catalina.base", getCatalinaHome());
    }


    /**
     * 获取配置的 URL的值.
     */
    private static String getConfigUrl() {
        return System.getProperty("catalina.config");
    }
}
