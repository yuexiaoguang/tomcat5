package org.apache.catalina.startup;


import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;


/**
 * <p>处理标记库描述符资源的内容.</p>
 */
public class TldRuleSet extends RuleSetBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * 用于识别元素的匹配模式前缀.
     */
    protected String prefix = null;


    // ------------------------------------------------------------ Constructor


    public TldRuleSet() {
        this("");
    }


    /**
     * @param prefix 匹配模式规则的前缀(包括尾部斜杠字符)
     */
    public TldRuleSet(String prefix) {
        super();
        this.namespaceURI = null;
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>添加RuleSet中定义的一组Rule实例到指定的<code>Digester</code>实例, 将他们与命名空间URI关联起来.
     * 这个方法只能被Digester实例调用.</p>
     *
     * @param digester Digester instance to which the new Rule instances
     *  should be added.
     */
    public void addRuleInstances(Digester digester) {
        digester.addCallMethod(prefix + "taglib/listener/listener-class",
                               "addApplicationListener", 0);
    }
}
