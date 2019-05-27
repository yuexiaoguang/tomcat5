package org.apache.catalina;


import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;


/**
 * <b>Wrapper</b>是一个Container，它表示Web应用程序的部署描述中的单个servlet定义. 
 * 它提供了一个方便的机制来使用拦截器，该拦截器将每个请求看作此定义所表示的servlet的一个请求.
 * <p>
 * Wrapper的实现类负责管理servlet生命周期, 包括在适当的时候调用init()和
 * destroy()方法, 此外，也关心SingleThreadModel声明的关于servlet类本身的实体.
 * <p>
 * 附加到Wrapper上的父级Container通常是一个Context实现类, 表示servlet执行的上下文（以及Web应用程序）.
 * <p>
 * 子级Containers不允许在Wrapper实现,因此<code>addChild()</code> 方法应该抛出
 * <code>IllegalArgumentException</code>.
 */
public interface Wrapper extends Container {

    // ------------------------------------------------------------- Properties

    /**
     * 返回这个servlet的可用date/time, 以毫秒为单位. 
     * 如果date/time在将来, 这个servlet的任何请求都将返回一个 SC_SERVICE_UNAVAILABLE错误. 
     * 如果是零, servlet当前可用. 
     * 值等于Long.MAX_VALUE，将意味着永久不可用
     */
    public long getAvailable();


    /**
     * 设置这个servlet的可用date/time, 以毫秒为单位. 
     * 如果date/time在将来, 这个servlet的任何请求都将返回一个 SC_SERVICE_UNAVAILABLE错误. 
     * 如果是零, servlet当前可用. 
     * 值等于Long.MAX_VALUE，将意味着永久不可用
     *
     * @param available The new available date/time
     */
    public void setAvailable(long available);


    /**
     * 返回JSP文件的上下文相对URI
     */
    public String getJspFile();


    /**
     * 设置JSP文件的上下文相对URI
     *
     * @param jspFile JSP file URI
     */
    public void setJspFile(String jspFile);


    /**
     * 返回load-on-startup属性表示的调用顺序值 (负值意味着加载时第一个调用).
     */
    public int getLoadOnStartup();


    /**
     * 设置load-on-startup属性表示的调用顺序值 (负值意味着加载时第一个调用).
     *
     * @param value New load-on-startup value
     */
    public void setLoadOnStartup(int value);


    /**
     * 返回run-as的ID
     */
    public String getRunAs();


    /**
     * 设置run-as的ID
     *
     * @param runAs New run-as identity value
     */
    public void setRunAs(String runAs);


    /**
     * 返回完全限定的servlet类名
     */
    public String getServletClass();


    /**
     * 设置完全限定的servlet类名
     *
     * @param servletClass Servlet class name
     */
    public void setServletClass(String servletClass);


    /**
     * 获取底层servlet支持的方法的名称.
     *
     * 这是响应底层的servlet处理的选项请求方法中包含的一组相同的方法.
     *
     * @return 基础servlet支持的方法的名称数组
     */
    public String[] getServletMethods() throws ServletException;


    /**
     * 这个servlet当前是否可用
     */
    public boolean isUnavailable();


    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个servlet初始化参数
     *
     * @param name 要添加的初始化参数的名称
     * @param value 此初始化参数的值
     */
    public void addInitParameter(String name, String value);


    /**
     * 添加关注的InstanceEvent监听器.
     *
     * @param listener The new listener
     */
    public void addInstanceListener(InstanceListener listener);


    /**
     * 添加这个Wrapper关联的映射.
     * 
     * @param mapping The new wrapper mapping
     */
    public void addMapping(String mapping);


    /**
     * 向记录集添加一个新的安全角色引用记录
     *
     * @param name 在此servlet中使用的角色名称
     * @param link 在Web应用程序中使用的角色名称
     */
    public void addSecurityReference(String name, String link);


    /**
     * 分配一个Servlet初始化实例，已经准备好<code>service()</code>方法被调用. 
     * 如果servlet类没有实现<code>SingleThreadModel</code>, 初始化的实例将立即返回. 
     * 如果servlet类实现了<code>SingleThreadModel</code>, Wrapper实现类必须保证此实例未重新分配,
     * 直到它被调用自身的<code>deallocate()</code>方法释放.
     *
     * @exception ServletException 如果servlet的init() 方法抛出异常
     * @exception ServletException 加载错误发生
     */
    public Servlet allocate() throws ServletException;


    /**
     * 将先前分配的servlet返回到可用实例池中. 
     * 如果servlet类没有实现SingleThreadModel,不会采取任何动作
     *
     * @param servlet The servlet to be returned
     *
     * @exception ServletException 如果一个释放错误发生
     */
    public void deallocate(Servlet servlet) throws ServletException;


    /**
     * 返回指定的初始化参数值; 或者<code>null</code>.
     *
     * @param name 请求的初始化参数的名称
     */
    public String findInitParameter(String name);


    /**
     * 返回所有定义的初始化参数名称数组
     */
    public String[] findInitParameters();


    /**
     * 返回这个wrapper关联的所有映射.
     */
    public String[] findMappings();


    /**
     * 为指定的安全角色引用名称返回安全角色链接; 或者<code>null</code>.
     *
     * @param name 安全角色引用名称
     */
    public String findSecurityReference(String name);


    /**
     * 返回关联的安全角色引用数组; 或者一个零长度的数组.
     */
    public String[] findSecurityReferences();


    /**
     * 增加监视时使用的错误计数值.
     */
    public void incrementErrorCount();


    /**
     * 加载并初始化当前servlet, 如果一个初始化实例也没有. 
     * 这可以使用，例如,在服务器启动的时候，加载在部署描述中标记的servlet
     *
     * @exception ServletException 如果servlet init()方法抛出一个异常
     * @exception ServletException 如果出现其他加载问题
     */
    public void load() throws ServletException;


    /**
     * 移除指定的初始化参数
     *
     * @param name 要移除的初始化参数的名称
     */
    public void removeInitParameter(String name);


    /**
     * 移除不再关注的InstanceEvent监听器
     *
     * @param listener The listener to remove
     */
    public void removeInstanceListener(InstanceListener listener);


    /**
     * 移除这个wrapper关联的映射.
     *
     * @param mapping The pattern to remove
     */
    public void removeMapping(String mapping);


    /**
     * 移除指定名称的安全角色引用
     *
     * @param name Security role used within this servlet to be removed
     */
    public void removeSecurityReference(String name);


    /**
     * 处理一个UnavailableException, 标记此servlet在指定的时间内不可用
     *
     * @param unavailable 发生的异常, 或者<code>null</code>将此servlet标记为永久不可用
     */
    public void unavailable(UnavailableException unavailable);


    /**
     * 卸载此servlet的所有初始化实例, 在调用每个实例的<code>destroy()</code>方法之后. 
     * 例如，在关闭整个servlet引擎之前，或者从Loader加载与Loader的存储库相关联的所有类之前，都可以使用它
     *
     * @exception ServletException 如果发生卸载错误
     */
    public void unload() throws ServletException;


}
