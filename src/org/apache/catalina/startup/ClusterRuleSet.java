package org.apache.catalina.startup;


import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;


/**
 * <p><strong>RuleSet</strong>, 用于处理Cluster元素的内容.</p>
 */
public class ClusterRuleSet extends RuleSetBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * 用于识别元素的匹配模式前缀.
     */
    protected String prefix = null;


    // ------------------------------------------------------------ Constructor

    public ClusterRuleSet() {
        this("");
    }


    /**
     * @param prefix 匹配模式规则的前缀(包括尾部斜杠字符)
     */
    public ClusterRuleSet(String prefix) {
        super();
        this.namespaceURI = null;
        this.prefix = prefix;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>添加一组RuleSet中定义的Rule 实例到指定的<code>Digester</code>实例, 将它们与命名空间URI 关联.
     * 这个方法只能通过Digester 实例调用.</p>
     *
     * @param digester Digester instance to which the new Rule instances
     *  should be added.
     */
    public void addRuleInstances(Digester digester) {
        //Cluster 配置开始
        digester.addObjectCreate(prefix + "Membership",
                                 null, // 必须在元素中指定
                                 "className");
        digester.addSetProperties(prefix + "Membership");
        digester.addSetNext(prefix + "Membership",
                            "setMembershipService",
                            "org.apache.catalina.cluster.MembershipService");
        
        digester.addObjectCreate(prefix + "Sender",
                                 null, // 必须在元素中指定
                                 "className");
        digester.addSetProperties(prefix + "Sender");
        digester.addSetNext(prefix + "Sender",
                            "setClusterSender",
                            "org.apache.catalina.cluster.ClusterSender");

        digester.addObjectCreate(prefix + "Receiver",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Receiver");
        digester.addSetNext(prefix + "Receiver",
                            "setClusterReceiver",
                            "org.apache.catalina.cluster.ClusterReceiver");

        digester.addObjectCreate(prefix + "Valve",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Valve");
        digester.addSetNext(prefix + "Valve",
                            "addValve",
                            "org.apache.catalina.Valve");
        
        digester.addObjectCreate(prefix + "Deployer",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Deployer");
        digester.addSetNext(prefix + "Deployer",
                            "setClusterDeployer",
                            "org.apache.catalina.cluster.ClusterDeployer");
        
        digester.addObjectCreate(prefix + "Listener",
                null, // MUST be specified in the element
                "className");
        digester.addSetProperties(prefix + "Listener");
        digester.addSetNext(prefix + "Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");
        
        digester.addObjectCreate(prefix + "ClusterListener",
                null, // MUST be specified in the element
                "className");
        digester.addSetProperties(prefix + "ClusterListener");
        digester.addSetNext(prefix + "ClusterListener",
                            "addClusterListener",
                            "org.apache.catalina.cluster.MessageListener");
    }
}
