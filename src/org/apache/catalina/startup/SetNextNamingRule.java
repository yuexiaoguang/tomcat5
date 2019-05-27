package org.apache.catalina.startup;

import org.apache.catalina.Context;
import org.apache.catalina.deploy.NamingResources;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Rule;


/**
 * <p>Rule实现类调用(top-1) (parent)对象上的方法, 将top对象（子）作为参数传递.
 * 它通常用于建立亲子关系.</p>
 *
 * <p>此规则现在支持默认的更灵活的方法匹配.
 * 这可能会打破（一些）1.1.1或更早的代码.
 * </p> 
 */
public class SetNextNamingRule extends Rule {


    // ----------------------------------------------------------- Constructors

    
    /**
     * @param methodName 父对象上调用的方法名
     * @param paramType 父方法上的参数类型
     *  (如果希望使用原始类型, 指定相应的Java封装类型, 例如<code>java.lang.Boolean</code>对应于<code>boolean</code>)
     */
    public SetNextNamingRule(String methodName,
                       String paramType) {
        this.methodName = methodName;
        this.paramType = paramType;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 父对象上调用的方法名.
     */
    protected String methodName = null;


    /**
     * 预期的方法的参数类型的java类的名称.
     */
    protected String paramType = null;


    // --------------------------------------------------------- Public Methods


    /**
     * 处理此元素的结尾.
     */
    public void end() throws Exception {

        // 标识要使用的对象
        Object child = digester.peek(0);
        Object parent = digester.peek(1);

        NamingResources namingResources = null;
        if (parent instanceof Context) {
            namingResources = ((Context) parent).getNamingResources();
        } else {
            namingResources = (NamingResources) parent;
        }
        
        // 调用指定的方法
        IntrospectionUtils.callMethod1(namingResources, methodName,
                child, paramType, digester.getClassLoader());

    }

    public String toString() {
        StringBuffer sb = new StringBuffer("SetNextRule[");
        sb.append("methodName=");
        sb.append(methodName);
        sb.append(", paramType=");
        sb.append(paramType);
        sb.append("]");
        return (sb.toString());
    }
}
