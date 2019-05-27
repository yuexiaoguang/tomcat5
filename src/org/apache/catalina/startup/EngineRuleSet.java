package org.apache.catalina.startup;


import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;


/**
 * <p><strong>RuleSet</strong>用于处理Engine元素的内容. 
 * 这个<code>RuleSet</code>不包括任何嵌套主机或DefaultContext元素的规则, 
 * 应该通过<code>HostRuleSet</code>或<code>ContextRuleSet</code>实例添加.</p>
 */
public class EngineRuleSet extends RuleSetBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * 用于识别元素的匹配模式前缀.
     */
    protected String prefix = null;


    // ------------------------------------------------------------ Constructor


    public EngineRuleSet() {
        this("");
    }


    /**
     * @param prefix 匹配模式规则的前缀(包括尾部斜杠字符)
     */
    public EngineRuleSet(String prefix) {
        super();
        this.namespaceURI = null;
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>添加一组RuleSet中定义的Rule实例到指定的<code>Digester</code>实例, 使用命名空间的URI关联它们.
     * 此方法只应由Digester实例调用.</p>
     *
     * @param digester 应该添加新规则实例的Digester实例
     */
    public void addRuleInstances(Digester digester) {

        digester.addObjectCreate(prefix + "Engine",
                                 "org.apache.catalina.core.StandardEngine",
                                 "className");
        digester.addSetProperties(prefix + "Engine");
        digester.addRule(prefix + "Engine",
                         new LifecycleListenerRule
                         ("org.apache.catalina.startup.EngineConfig",
                          "engineConfigClass"));
        digester.addSetNext(prefix + "Engine",
                            "setContainer",
                            "org.apache.catalina.Container");

        //Cluster configuration start
        digester.addObjectCreate(prefix + "Engine/Cluster",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Engine/Cluster");
        digester.addSetNext(prefix + "Engine/Cluster",
                            "setCluster",
                            "org.apache.catalina.Cluster");
        //Cluster configuration end

        digester.addObjectCreate(prefix + "Engine/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Engine/Listener");
        digester.addSetNext(prefix + "Engine/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        digester.addObjectCreate(prefix + "Engine/Logger",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Engine/Logger");
        digester.addSetNext(prefix + "Engine/Logger",
                            "setLogger",
                            "org.apache.catalina.Logger");

        digester.addObjectCreate(prefix + "Engine/Realm",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Engine/Realm");
        digester.addSetNext(prefix + "Engine/Realm",
                            "setRealm",
                            "org.apache.catalina.Realm");

        digester.addObjectCreate(prefix + "Engine/Valve",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Engine/Valve");
        digester.addSetNext(prefix + "Engine/Valve",
                            "addValve",
                            "org.apache.catalina.Valve");
    }
}
