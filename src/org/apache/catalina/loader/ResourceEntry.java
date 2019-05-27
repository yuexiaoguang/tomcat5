package org.apache.catalina.loader;

import java.net.URL;
import java.security.cert.Certificate;
import java.util.jar.Manifest;

/**
 * Resource entry.
 */
public class ResourceEntry {

    /**
     * 加载此类时, 源文件的“最后修改”时间，自纪元以来的毫秒内.
     */
    public long lastModified = -1;

    /**
     * 资源的二进制内容
     */
    public byte[] binaryContent = null;


    /**
     * 加载的类
     */
    public Class loadedClass = null;


    /**
     * 加载对象的URL资源.
     */
    public URL source = null;


    /**
     * 在对象被加载代码库的URL.
     */
    public URL codeBase = null;


    /**
     * Manifest (如果资源是从JAR加载的).
     */
    public Manifest manifest = null;


    /**
     * Certificates (如果资源是从JAR加载的).
     */
    public Certificate[] certificates = null;

}
