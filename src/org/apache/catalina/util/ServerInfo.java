package org.apache.catalina.util;


import java.io.InputStream;
import java.util.Properties;


/**
 * 简单实用模块，在集成Tomcat时可以轻松地插入服务器标识符.
 */
public class ServerInfo {


    // ------------------------------------------------------- Static Variables


    /**
     * 识别自己的服务器信息字符串.
     */
    private static String serverInfo = null;

    /**
     * 服务器构建字符串.
     */
    private static String serverBuilt = null;

    /**
     * 服务器版本号.
     */
    private static String serverNumber = null;

    static {

        try {
            InputStream is = ServerInfo.class.getResourceAsStream
                ("/org/apache/catalina/util/ServerInfo.properties");
            Properties props = new Properties();
            props.load(is);
            is.close();
            serverInfo = props.getProperty("server.info");
            serverBuilt = props.getProperty("server.built");
            serverNumber = props.getProperty("server.number");
        } catch (Throwable t) {
            ;
        }
        if (serverInfo == null)
            serverInfo = "Apache Tomcat";
        if (serverBuilt == null)
            serverBuilt = "unknown";
        if (serverNumber == null)
            serverNumber = "5.5.0.0";
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 返回此版本Tomcat的服务器标识.
     */
    public static String getServerInfo() {
        return (serverInfo);
    }

    /**
     * 返回此版本Tomcat的服务器建立时间.
     */
    public static String getServerBuilt() {
        return (serverBuilt);
    }

    /**
     * 返回服务器的版本号.
     */
    public static String getServerNumber() {
        return (serverNumber);
    }

    public static void main(String args[]) {
        System.out.println("Server version: " + getServerInfo());
        System.out.println("Server built:   " + getServerBuilt());
        System.out.println("Server number:  " + getServerNumber());
        System.out.println("OS Name:        " +
                           System.getProperty("os.name"));
        System.out.println("OS Version:     " +
                           System.getProperty("os.version"));
        System.out.println("Architecture:   " +
                           System.getProperty("os.arch"));
        System.out.println("JVM Version:    " +
                           System.getProperty("java.runtime.version"));
        System.out.println("JVM Vendor:     " +
                           System.getProperty("java.vm.vendor"));                        
    }

}
