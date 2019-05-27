package org.apache.catalina;


/**
 * <p>解耦接口指定实现类最多关联一个<strong>Container</strong>实例.</p>
 */
public interface Contained {

    //-------------------------------------------------------------- Properties

    /**
     * 返回这个实例关联的<code>Container</code>; 或者<code>null</code>.
     */
    public Container getContainer();


    /**
     * 设置这个实例关联的<code>Container</code>.
     *
     * @param container 设置这个实例关联的Container实例, 或者<code>null</code>解除关联
     *  from any Container
     */
    public void setContainer(Container container);

}
