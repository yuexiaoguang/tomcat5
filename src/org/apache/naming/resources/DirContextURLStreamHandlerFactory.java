package org.apache.naming.resources;

import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;

/**
 * 流处理器的JNDI目录上下文工厂
 */
public class DirContextURLStreamHandlerFactory implements URLStreamHandlerFactory {
    
    // ----------------------------------------------------------- Constructors
    
    public DirContextURLStreamHandlerFactory() {
    }
    
    // ---------------------------------------- URLStreamHandlerFactory Methods
    
    /**
     * 创建一个指定协议的URLStreamHandler实例.
     * 如果协议不是<code>jndi</code>, 返回null.
     * 
     * @param protocol 协议(在这里必须是"jndi")
     * @return jndi协议的URLStreamHandler, 或者null
     */
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (protocol.equals("jndi")) {
            return new DirContextURLStreamHandler();
        } else {
            return null;
        }
    }
}
