package org.apache.catalina.deploy;

import org.apache.catalina.util.RequestUtil;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;

/**
 * Web应用程序安全约束的Web资源集合表示, 在部署描述中使用<code>&lt;web-resource-collection&gt;</code>元素表示
 * <p>
 * <b>WARNING</b>:  假定该类的实例只在单个线程的上下文中创建和修改, 在实例对其余的应用程序可见之前.
 * 之后，只需要读取访问权限. 因此，这个类中的任何读写访问都不同步.
 */
public class SecurityCollection implements Serializable {

    private static Log log = LogFactory.getLog(SecurityCollection.class);

    // ----------------------------------------------------------- Constructors

    public SecurityCollection() {
        this(null, null);
    }


    /**
     * @param name 此安全集合的名称
     */
    public SecurityCollection(String name) {
        this(name, null);
    }


    /**
     * @param name 此安全集合的名称
     * @param description 此安全集合的描述
     */
    public SecurityCollection(String name, String description) {
        super();
        setName(name);
        setDescription(description);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 此Web资源集合的描述
     */
    private String description = null;


    /**
     * 此Web资源集合所涵盖的HTTP方法.
     */
    private String methods[] = new String[0];


    /**
     * 此Web资源集合的名称.
     */
    private String name = null;


    /**
     * 此安全集合保护的URL模式.
     */
    private String patterns[] = new String[0];


    // ------------------------------------------------------------- Properties


    /**
     * 返回此Web资源集合的描述.
     */
    public String getDescription() {
        return (this.description);
    }


    /**
     * 设置此Web资源集合的描述
     *
     * @param description The new description
     */
    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * 返回此Web资源集合的名称
     */
    public String getName() {
        return (this.name);
    }


    /**
     * 设置此Web资源集合的名称
     *
     * @param name The new name
     */
    public void setName(String name) {
        this.name = name;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 添加HTTP请求方法作为Web资源集合的一部分.
     */
    public void addMethod(String method) {
        if (method == null)
            return;
        String results[] = new String[methods.length + 1];
        for (int i = 0; i < methods.length; i++)
            results[i] = methods[i];
        results[methods.length] = method;
        methods = results;
    }


    /**
     * 添加URL模式作为Web资源集合的一部分
     */
    public void addPattern(String pattern) {
        if (pattern == null)
            return;

        // Bugzilla 34805: add friendly warning.
        if(pattern.endsWith("*")) {
          if (pattern.charAt(pattern.length()-1) != '/') {
            if (log.isDebugEnabled()) {
              log.warn("Suspicious url pattern: \"" + pattern + "\"" +
                       " - see http://java.sun.com/aboutJava/communityprocess/first/jsr053/servlet23_PFD.pdf" +
                       "  section 11.2" );
            }
          }
        }

        pattern = RequestUtil.URLDecode(pattern);
        String results[] = new String[patterns.length + 1];
        for (int i = 0; i < patterns.length; i++) {
            results[i] = patterns[i];
        }
        results[patterns.length] = pattern;
        patterns = results;
    }


    /**
     * 返回<code>true</code>，如果指定的HTTP请求方法是此Web资源集合的一部分.
     *
     * @param method 请求检查方法
     */
    public boolean findMethod(String method) {
        if (methods.length == 0)
            return (true);
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].equals(method))
                return (true);
        }
        return (false);
    }


    /**
     * 返回HTTP请求方法集, 是这个Web资源集合的一部分; 如果包含所有请求方法，则为零长度数组
     */
    public String[] findMethods() {
        return (methods);
    }


    /**
     * 是此Web资源集合的指定模式部分?
     *
     * @param pattern Pattern to be compared
     */
    public boolean findPattern(String pattern) {
        for (int i = 0; i < patterns.length; i++) {
            if (patterns[i].equals(pattern))
                return (true);
        }
        return (false);
    }


    /**
     * 返回属于此Web资源集合的URL模式集.
     * 如果没有, 返回零长度数组.
     */
    public String[] findPatterns() {
        return (patterns);
    }


    /**
     * 删除指定的HTTP请求方法，来自于此Web资源集合的一部分.
     *
     * @param method Request method to be removed
     */
    public void removeMethod(String method) {

        if (method == null)
            return;
        int n = -1;
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].equals(method)) {
                n = i;
                break;
            }
        }
        if (n >= 0) {
            int j = 0;
            String results[] = new String[methods.length - 1];
            for (int i = 0; i < methods.length; i++) {
                if (i != n)
                    results[j++] = methods[i];
            }
            methods = results;
        }
    }


    /**
     * 从Web资源集合的一部分删除指定的URL模式.
     *
     * @param pattern Pattern to be removed
     */
    public void removePattern(String pattern) {
        if (pattern == null)
            return;
        int n = -1;
        for (int i = 0; i < patterns.length; i++) {
            if (patterns[i].equals(pattern)) {
                n = i;
                break;
            }
        }
        if (n >= 0) {
            int j = 0;
            String results[] = new String[patterns.length - 1];
            for (int i = 0; i < patterns.length; i++) {
                if (i != n)
                    results[j++] = patterns[i];
            }
            patterns = results;
        }
    }


    public String toString() {
        StringBuffer sb = new StringBuffer("SecurityCollection[");
        sb.append(name);
        if (description != null) {
            sb.append(", ");
            sb.append(description);
        }
        sb.append("]");
        return (sb.toString());
    }
}
