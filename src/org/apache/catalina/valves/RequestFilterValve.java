package org.apache.catalina.valves;


import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.StringManager;
import org.apache.tomcat.util.compat.JdkCompat;

/**
 * Valve实现类, 在比较适当的请求属性的基础上执行筛选(基于您选择配置到Container的管道中的子类来选择)针对该Valve配置的一组正则表达式.
 * <p>
 * 这个valve通过设置<code>allow</code>和<code>deny</code>属性配置以逗号分隔的正则表达式列表，其中将比较适当的请求属性.
 * 评估结果如下:
 * <ul>
 * <li>子类提取要过滤的请求属性, 并调用普通的<code>process()</code>方法.
 * <li>如果任何表达式没有配置, 属性将与每个表达式进行比较. 
 * 		如果找到匹配项, 这个请求将被拒绝，使用 "Forbidden" HTTP响应.</li>
 * <li>如果有允许表达式配置, 属性将与每个表达式进行比较. 如果找到匹配项, 此请求将被允许传递到当前pipeline中的下一个Valve.</li>
 * <li>如果指定了一个或多个否定表达式，但不允许表达式, 允许这个请求通过(因为没有一个否定表达式匹配它).
 * <li>这个请求将被拒绝，使用"Forbidden" HTTP响应.</li>
 * </ul>
 * <p>
 * 这个Valve可以连接到任何Container, 取决于希望执行的筛选的粒度.
 */
public abstract class RequestFilterValve extends ValveBase {

    // ----------------------------------------------------- Class Variables

    /**
     * JDK 兼容支持
     */
    private static final JdkCompat jdkCompat = JdkCompat.getJdkCompat();


    /**
     * 实现类描述信息.
     */
    private static final String info = "org.apache.catalina.valves.RequestFilterValve/1.0";


    /**
     * The StringManager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);


    // ----------------------------------------------------- Instance Variables


    /**
     * 逗号分隔的<code>allow</code>表达式列表.
     */
    protected String allow = null;


    /**
     * 评估的<code>deny</code>正则表达式集合
     */
    protected Pattern allows[] = new Pattern[0];


    /**
     * 评估的<code>deny</code>正则表达式集合.
     */
    protected Pattern denies[] = new Pattern[0];


    /**
     * 逗号分隔的<code>deny</code>表达式列表.
     */
    protected String deny = null;


    // ------------------------------------------------------------- Properties


    /**
     * 返回逗号分隔的<code>allow</code>表达式列表; 或者<code>null</code>.
     */
    public String getAllow() {
        return (this.allow);
    }


    /**
     * 设置逗号分隔的<code>allow</code>表达式列表.
     *
     * @param allow The new set of allow expressions
     */
    public void setAllow(String allow) {
        this.allow = allow;
        allows = precalculate(allow);
    }


    /**
     * 返回逗号分隔的<code>deny</code>表达式列表; 或者<code>null</code>.
     */
    public String getDeny() {
        return (this.deny);
    }


    /**
     * 设置逗号分隔的<code>deny</code>表达式列表.
     *
     * @param deny The new set of deny expressions
     */
    public void setDeny(String deny) {
        this.deny = deny;
        denies = precalculate(deny);
    }


    /**
     * 返回实现类的描述信息.
     */
    public String getInfo() {
        return (info);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 提取所需的请求属性, 传递它到(与指定的请求和响应对象一起)<code>process()</code>方法来执行实际筛选.
     * 这个方法必须由一个具体的子类来实现.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    public abstract void invoke(Request request, Response response)
        throws IOException, ServletException;


    // ------------------------------------------------------ Protected Methods


    /**
     * 返回从指定参数初始化的正则表达式对象数组, 必须是<code>null</code>或逗号分隔的正则表达式模式列表.
     *
     * @param list 逗号分隔的模式列表
     *
     * @exception IllegalArgumentException 如果其中一个模式有无效的语法
     */
    protected Pattern[] precalculate(String list) {

        if (list == null)
            return (new Pattern[0]);
        list = list.trim();
        if (list.length() < 1)
            return (new Pattern[0]);
        list += ",";

        ArrayList reList = new ArrayList();
        while (list.length() > 0) {
            int comma = list.indexOf(',');
            if (comma < 0)
                break;
            String pattern = list.substring(0, comma).trim();
            try {
                reList.add(Pattern.compile(pattern));
            } catch (PatternSyntaxException e) {
                IllegalArgumentException iae = new IllegalArgumentException
                    (sm.getString("requestFilterValve.syntax", pattern));
                jdkCompat.chainException(iae, e);
                throw iae;
            }
            list = list.substring(comma + 1);
        }
        Pattern reArray[] = new Pattern[reList.size()];
        return ((Pattern[]) reList.toArray(reArray));
    }


    /**
     * 执行为这个Valve配置的过滤, 与指定的请求属性匹配.
     *
     * @param property 要过滤的请求属性
     * @param request The servlet request to be processed
     * @param response The servlet response to be processed
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    protected void process(String property,
                           Request request, Response response)
        throws IOException, ServletException {

        // Check the deny patterns, if any
        for (int i = 0; i < denies.length; i++) {
            if (denies[i].matcher(property).matches()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }

        // Check the allow patterns, if any
        for (int i = 0; i < allows.length; i++) {
            if (allows[i].matcher(property).matches()) {
                getNext().invoke(request, response);
                return;
            }
        }

        // Allow if denies specified but not allows
        if ((denies.length > 0) && (allows.length == 0)) {
            getNext().invoke(request, response);
            return;
        }

        // Deny this request
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
