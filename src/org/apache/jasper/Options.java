package org.apache.jasper;

import java.io.File;
import java.util.Map;

import org.apache.jasper.compiler.JspConfig;
import org.apache.jasper.compiler.TagPluginManager;
import org.apache.jasper.compiler.TldLocationsCache;

/**
 * 用于保存特定于JSP引擎的所有init参数. 
 */
public interface Options {

    /**
     * 返回 true, 如果Jasper发生编译错误而不是运行错误.
     * 初始化错误，如果useBean行为中指定的类属性是无效的.
     */
    public boolean getErrorOnUseBeanInvalidClassAttribute();

    /**
     * 是否保留生成的源?
     */
    public boolean getKeepGenerated();

    /**
     * 返回 true, 如果启用了标记处理程序池, 否则返回false.
     */
    public boolean isPoolingEnabled();

    /**
     * 是否支持HTML映射servlet?
     */
    public boolean getMappedFile();

    /**
     * 错误是发送到客户端，还是抛出到标准错误流?
     */
    public boolean getSendErrorToClient();
 
    /**
     * 应该在编译类中包含调试信息吗?
     */
    public boolean getClassDebugInfo();

    /**
     * 后台编译线程检查间隔, 以秒为单位
     */
    public int getCheckInterval();

    /**
     * 是否在开发模式中使用Jasper?
     */
    public boolean getDevelopment();

    /**
     * 是否阻止JSR45调试的SMAP信息的生成?
     */
    public boolean isSmapSuppressed();

    /**
     * JSR45调试的SMAP信息是否应该转储到一个文件.
     * 不容忽视的是 suppressSmap() 是 true
     */
    public boolean isSmapDumped();

    /**
     * 是否删除指令或操作之间的空格?
     */
    public boolean getTrimSpaces();

    /**
     * 当浏览器是IE时，在插件标签中使用的类ID. 
     */
    public String getIeClassId();

    /**
     * 临时目录?
     */
    public File getScratchDir();

    /**
     * 当编译JSP文件生成的servlet时, 是否使用classpath?
     */
    public String getClassPath();

    /**
     * 使用的编译器.
     */
    public String getCompiler();

    /**
     * 编译器的 VM, e.g. 1.1, 1.2, 1.3, 1.4, or 1.5.
     */
    public String getCompilerTargetVM();

    /**
     * 编译器源 VM, e.g. 1.3, 1.4, or 1.5.
     */
    public String getCompilerSourceVM();   

    /**
     * 用于Web应用程序暴露的各种标签库的TLD位置的缓存.
     * 标签库是直接在web.xml暴露，还是间接地通过部署在jar文件(WEB-INF/lib)中的标签库的TLD中的URI标签暴露.
     *
     * @return web应用的TldLocationsCache
     */
    public TldLocationsCache getTldLocationsCache();

    /**
     * 生成JSP页面servlet的Java平台的编码.
     */
    public String getJavaEncoding();

    /**
     * 告诉Ant JSP 页面是否被编辑
     */
    public boolean getFork();

    /**
     * 在web.xml中指定的JSP的配置信息.  
     */
    public JspConfig getJspConfig();

    /**
     * 是否生成 X-Powered-By 响应头?
     */
    public boolean isXpoweredBy();

    /**
     * Tag Plugin Manager
     */
    public TagPluginManager getTagPluginManager();

    /**
     * 是否是生成字符数组的文本字符串?
     */
    public boolean genStringAsCharArray();
    
    /**
     * 修改测试间隔.
     */
    public int getModificationTestInterval();
    
    /**
     * 缓存是否可用(用于预编译).
     */
    public boolean isCaching();
    
    /**
     * 通过TagLibraryInfoImpl.parseTLD中的parseXMLDocument返回的TreeNode的web应用范围内的缓存,
     * 如果isCaching 返回true.
     * 
     * @return the Map(String uri, TreeNode tld) instance.
     */
    public Map getCache();
    
}
