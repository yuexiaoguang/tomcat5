package org.apache.catalina;


/**
 * <b>ContainerServlet</b>是一个servlet, 用来访问 Catalina 内部功能, 从Catalina类加载器加载， 而不是Web应用程序类装入器.  
 * 每当该servlet的新实例被投入服务时，必须由容器调用属性setter方法
 */
public interface ContainerServlet {

    // ------------------------------------------------------------- Properties

    /**
     * 返回与此servlet关联的Wrapper
     */
    public Wrapper getWrapper();


    /**
     * 设置与此servlet关联的Wrapper
     *
     * @param wrapper The new associated Wrapper
     */
    public void setWrapper(Wrapper wrapper);


}
