package org.apache.catalina.startup;

import java.lang.reflect.Constructor;

import org.apache.catalina.Container;
import org.apache.catalina.Loader;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.Rule;
import org.apache.tomcat.util.digester.RuleSetBase;
import org.xml.sax.Attributes;

/**
 * <p><strong>RuleSet</strong>用于处理上下文或DefaultContext定义的元素的内容. 
 * 启用分析 DefaultContext, 一定要指定一个前缀，以 "/Default"结尾.</p>
 */
public class ContextRuleSet extends RuleSetBase {

    // ----------------------------------------------------- Instance Variables

    /**
     * 用于识别元素的匹配模式前缀.
     */
    protected String prefix = null;


    /**
     * 是否创建上下文.
     */
    protected boolean create = true;


    // ------------------------------------------------------------ Constructor


    public ContextRuleSet() {
        this("");
    }


    /**
     * @param prefix 匹配模式规则的前缀 (包括尾部斜杠字符)
     */
    public ContextRuleSet(String prefix) {
        super();
        this.namespaceURI = null;
        this.prefix = prefix;
    }


    /**
     * @param prefix 匹配模式规则的前缀 (包括尾部斜杠字符)
     */
    public ContextRuleSet(String prefix, boolean create) {
        super();
        this.namespaceURI = null;
        this.prefix = prefix;
        this.create = create;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>添加RuleSet中定义的Rule实例集合到指定的<code>Digester</code>实例, 将它们与命名空间URI相关联.
     * 此方法只应由Digester实例调用.</p>
     *
     * @param digester 应该添加新规则实例的Digester实例.
     */
    public void addRuleInstances(Digester digester) {

        if (create) {
            digester.addObjectCreate(prefix + "Context",
                    "org.apache.catalina.core.StandardContext", "className");
            digester.addSetProperties(prefix + "Context");
        } else {
            digester.addRule(prefix + "Context", new SetContextPropertiesRule());
        }

        if (create) {
            digester.addRule(prefix + "Context",
                             new LifecycleListenerRule
                                 ("org.apache.catalina.startup.ContextConfig",
                                  "configClass"));
            digester.addSetNext(prefix + "Context",
                                "addChild",
                                "org.apache.catalina.Container");
        }
        digester.addCallMethod(prefix + "Context/InstanceListener",
                               "addInstanceListener", 0);

        digester.addObjectCreate(prefix + "Context/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Context/Listener");
        digester.addSetNext(prefix + "Context/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        digester.addRule(prefix + "Context/Loader",
                         new CreateLoaderRule
                             ("org.apache.catalina.loader.WebappLoader",
                              "className"));
        digester.addSetProperties(prefix + "Context/Loader");
        digester.addSetNext(prefix + "Context/Loader",
                            "setLoader",
                            "org.apache.catalina.Loader");

        digester.addObjectCreate(prefix + "Context/Manager",
                                 "org.apache.catalina.session.StandardManager",
                                 "className");
        digester.addSetProperties(prefix + "Context/Manager");
        digester.addSetNext(prefix + "Context/Manager",
                            "setManager",
                            "org.apache.catalina.Manager");

        digester.addObjectCreate(prefix + "Context/Manager/Store",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Context/Manager/Store");
        digester.addSetNext(prefix + "Context/Manager/Store",
                            "setStore",
                            "org.apache.catalina.Store");

        digester.addObjectCreate(prefix + "Context/Parameter",
                                 "org.apache.catalina.deploy.ApplicationParameter");
        digester.addSetProperties(prefix + "Context/Parameter");
        digester.addSetNext(prefix + "Context/Parameter",
                            "addApplicationParameter",
                            "org.apache.catalina.deploy.ApplicationParameter");

        digester.addObjectCreate(prefix + "Context/Realm",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Context/Realm");
        digester.addSetNext(prefix + "Context/Realm",
                            "setRealm",
                            "org.apache.catalina.Realm");

        digester.addObjectCreate(prefix + "Context/Resources",
                                 "org.apache.naming.resources.FileDirContext",
                                 "className");
        digester.addSetProperties(prefix + "Context/Resources");
        digester.addSetNext(prefix + "Context/Resources",
                            "setResources",
                            "javax.naming.directory.DirContext");

        digester.addObjectCreate(prefix + "Context/ResourceLink",
                "org.apache.catalina.deploy.ContextResourceLink");
        digester.addSetProperties(prefix + "Context/ResourceLink");
        digester.addRule(prefix + "Context/ResourceLink",
                new SetNextNamingRule("addResourceLink",
                        "org.apache.catalina.deploy.ContextResourceLink"));

        digester.addObjectCreate(prefix + "Context/Valve",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Context/Valve");
        digester.addSetNext(prefix + "Context/Valve",
                            "addValve",
                            "org.apache.catalina.Valve");

        digester.addCallMethod(prefix + "Context/WatchedResource",
                               "addWatchedResource", 0);

        digester.addCallMethod(prefix + "Context/WrapperLifecycle",
                               "addWrapperLifecycle", 0);

        digester.addCallMethod(prefix + "Context/WrapperListener",
                               "addWrapperListener", 0);
    }
}


// ----------------------------------------------------------- Private Classes


/**
 * 创建一个新的<code>Loader</code>实例规则, 使用与堆栈上的顶级对象关联的父类装入器(必须是<code>Container</code>), 然后把它推到堆栈上.
 */
final class CreateLoaderRule extends Rule {

    public CreateLoaderRule(String loaderClass, String attributeName) {
        this.loaderClass = loaderClass;
        this.attributeName = attributeName;
    }

    private String attributeName;

    private String loaderClass;

    public void begin(String namespace, String name, Attributes attributes)
        throws Exception {

        // 查找所需的父类装入器
        ClassLoader parentClassLoader = null;
        Object ojb = digester.peek();
        if (ojb instanceof Container) {
            parentClassLoader = ((Container)ojb).getParentClassLoader();
        }

        // 实例化一个新的加载器实现对象
        String className = loaderClass;
        if (attributeName != null) {
            String value = attributes.getValue(attributeName);
            if (value != null)
                className = value;
        }
        Class clazz = Class.forName(className);
        Class types[] = { ClassLoader.class };
        Object args[] = { parentClassLoader };
        Constructor constructor = clazz.getDeclaredConstructor(types);
        Loader loader = (Loader) constructor.newInstance(args);

        // 将新加载程序推到堆栈上
        digester.push(loader);
        if (digester.getLogger().isDebugEnabled())
            digester.getLogger().debug("new " + loader.getClass().getName());
    }

    public void end(String namespace, String name) throws Exception {
        Loader loader = (Loader) digester.pop();
        if (digester.getLogger().isDebugEnabled())
            digester.getLogger().debug("pop " + loader.getClass().getName());
    }
}
