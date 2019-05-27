package org.apache.catalina.startup;


import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleListener;
import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;


/**
 * <p>创建<code>LifecycleListener</code>实例的规则,
 * 并将其与堆栈上的顶级对象关联起来(必须实现<code>LifecycleListener</code>).</p>
 */
public class LifecycleListenerRule extends Rule {


    // ----------------------------------------------------------- Constructors

    /**
     * @param listenerClass 创建的LifecycleListener实现类的默认名称
     * @param attributeName 属性名称，可以覆盖LifecycleListener实现类的名字
     */
    public LifecycleListenerRule(String listenerClass, String attributeName) {
        this.listenerClass = listenerClass;
        this.attributeName = attributeName;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 属性名称，可以覆盖LifecycleListener实现类的名字
     */
    private String attributeName;


    /**
     * <code>LifecycleListener</code>实现类的名字.
     */
    private String listenerClass;


    // --------------------------------------------------------- Public Methods


    /**
     * 处理xml元素的开头.
     *
     * @param attributes 元素的属性
     *
     * @exception Exception if a processing error occurs
     */
    public void begin(String namespace, String name, Attributes attributes) throws Exception {
        // Instantiate a new LifecyleListener implementation object
        String className = listenerClass;
        if (attributeName != null) {
            String value = attributes.getValue(attributeName);
            if (value != null)
                className = value;
        }
        Class clazz = Class.forName(className);
        LifecycleListener listener =
            (LifecycleListener) clazz.newInstance();

        // Add this LifecycleListener to our associated component
        Lifecycle lifecycle = (Lifecycle) digester.peek();
        lifecycle.addLifecycleListener(listener);
    }
}
