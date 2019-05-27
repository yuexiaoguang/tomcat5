package org.apache.catalina;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.security.Principal;
import java.security.cert.X509Certificate;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.deploy.SecurityConstraint;

/**
 * <b>Realm</b> 是用于验证单个用户的底层安全域的只读外观模式，并识别与这些用户相关联的安全角色.
 * 可以在任何Container级别上连接，但通常只连接到Context或更高级别的Container
 */
public interface Realm {

    // ------------------------------------------------------------- Properties

    public Container getContainer();


    public void setContainer(Container container);


    /**
     * 返回描述信息和相应版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo();


    // --------------------------------------------------------- Public Methods

    
    /**
     * 添加属性修改监听器
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener);


    /**
     * 返回Principal ; 如果没有，返回<code>null</code>.
     *
     * @param username 要查找的Principal的用户名
     * @param credentials 用于验证此用户名的密码或其他凭据
     */
    public Principal authenticate(String username, String credentials);


    /**
     * 返回Principal ; 如果没有，返回<code>null</code>.
     *
     * @param username 要查找的Principal的用户名
     * @param credentials 用于验证此用户名的密码或其他凭据
     */
    public Principal authenticate(String username, byte[] credentials);


    /**
     * 返回指定用户名关联的Principal, 使用指定参数和RFC 2069中描述的方法进行匹配; 没有匹配到，返回<code>null</code>.
     *
     * @param username 要查找的Principal的用户名
     * @param digest 客户端提交的摘要
     * @param nonce request使用的唯一的(或者独特的)token
     * @param realm Realm类的名称
     * @param md5a2 第二个MD5摘要用于计算摘要 : MD5(Method + ":" + uri)
     */
    public Principal authenticate(String username, String digest,
                                  String nonce, String nc, String cnonce,
                                  String qop, String realm,
                                  String md5a2);


    /**
     * 返回X509客户端证书的指定链关联的Principal. 如果没有, 返回<code>null</code>.
     *
     * @param certs 客户端证书数组, 数组中的第一个是客户端本身的证书
     */
    public Principal authenticate(X509Certificate certs[]);
    
    
    /**
     * 执行周期任务, 例如重新加载, etc. 这个方法将在这个容器的类加载上下文被调用.
     * 异常将被捕获和记录.
     */
    public void backgroundProcess();


    /**
     * 返回用于防范请求URI的 SecurityConstraints, 或者<code>null</code>.
     *
     * @param request 正在处理的请求
     */
    public SecurityConstraint [] findSecurityConstraints(Request request, Context context);
    
    
    /**
     * 根据指定的授权约束执行访问控制.
     * 返回<code>true</code>如果满足此约束，则处理将继续进行, 否则返回<code>false</code>
     *
     * @param request Request we are processing
     * @param response Response we are creating
     * @param constraint 执行的安全约束
     * @param context 这个类的客户端附加的上下文
     *
     * @exception IOException 如果出现输入/输出错误
     */
    public boolean hasResourcePermission(Request request,
                                         Response response,
                                         SecurityConstraint [] constraint,
                                         Context context)
        throws IOException;
    
    
    /**
     * 返回<code>true</code>如果指定的Principal拥有指定的安全角色, 在这个Realm的上下文中; 否则返回<code>false</code>.
     *
     * @param principal 被验证安全角色的Principal
     * @param role 要验证的安全角色
     */
    public boolean hasRole(Principal principal, String role);

    /**
     * 强制检查这个请求URI是否拥有权限访问受保护的用户数据.
     * 返回<code>true</code>如果该约束未被违反，则处理将继续进行, 或者<code>false</code>如果已经创建了响应
     *
     * @param request Request we are processing
     * @param response Response we are creating
     * @param constraint 检查的安全约束
     *
     * @exception IOException 如果出现输入/输出错误
     */
    public boolean hasUserDataPermission(Request request,
                                         Response response,
                                         SecurityConstraint []constraint)
        throws IOException;
    
    /**
     * 删除属性修改监听器
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener);


}
