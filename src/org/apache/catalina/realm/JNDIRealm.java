package org.apache.catalina.realm;

import java.io.IOException;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import javax.naming.Context;
import javax.naming.CommunicationException;
import javax.naming.CompositeName;
import javax.naming.InvalidNameException;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NameParser;
import javax.naming.Name;
import javax.naming.AuthenticationException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.util.Base64;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;

/**
 * <p><strong>Realm</strong>实现类，通过java命名和目录接口（JNDI）API访问目录服务器.
 * 下面对底层目录服务器中的数据结构施加约束:</p>
 * <ul>
 *
 * <li>可以验证的每个用户都由顶层<code>DirContext</code>的单个元素表示，通过<code>connectionURL</code>属性进入.</li>
 *
 * <li>如果不能将套接字连接到<code>connectURL</code>, 将尝试使用<code>alternateURL</code>.</li>
 *
 * <li>每个用户元素都有一个专有名称, 可以通过将当前用户名替换为模式来形成, 使用userPattern 属性配置.</li>
 *
 * <li>另外, 如果<code>userPattern</code>属性未被指定, 可以通过搜索目录上下文来定位一个独特的元素.
 * 		在这种情况下:
 *     <ul>
 *     <li><code>userSearch</code>模式指定用户名替换后的搜索筛选器.</li>
 *     <li><code>userBase</code> 属性可以设置为包含用户的子树的基元素. 如果未指定,搜索基础是顶级上下文.</li>
 *     <li><code>userSubtree</code> 属性可以设置为<code>true</code>, 如果您希望搜索目录上下文的整个子树.
 *     <code>false</code>默认值，将只搜索当前级别.</li>
 *    </ul>
 * </li>
 * 
 * <li>用户可以通过提供用户名和密码绑定到目录来进行身份验证. 当<code>userPassword</code>属性未指定的时候，这个方法将会使用.</li>
 *
 * <li>通过从目录检索属性值并与用户所提供的值进行比较，可以对用户进行身份验证. 当<code>userPassword</code>属性被指定，这个方法将会使用, 
 * 		在这种情况下:
 *     <ul>
 *     <li>此用户的元素必须包含一个<code>userPassword</code>属性.
 *     <li>用户密码属性的值是一个明文字符串, 或通过<code>RealmBase.digest()</code>方法获得的明文字符串
 *     (使用<code>RealmBase</code>支持的标准摘要).
 *     <li>如果所提交的凭据(通过<code>RealmBase.digest()</code>之后)与检索用户密码属性的检索值相等，则认为该用户是经过验证的.</li>
 *     </ul></li>
 *
 * <li>每个用户组被分配一个特定的角色, 可以由一个最高等级的<code>DirContext</code>元素表示，通过<code>connectionURL</code>属性访问.
 * 		此元素具有以下特性:
 *     <ul>
 *     <li>所有可能的组的集合可以通过<code>roleSearch</code>属性配置的搜索模式选择.</li>
 *     <li><code>roleSearch</code>模式选择包含模式替换 "{0}"为名称, 替换 "{1}"为用户名, 验证用户要检索的角色.</li>
 *     <li><code>roleBase</code>属性可以设置为搜索匹配角色的基础的元素. 如果未指定, 将搜索整个上下文.</li>
 *     <li><code>roleSubtree</code> 属性可以设置为<code>true</code>, 如果您希望搜索目录上下文的整个子树.
 *     		默认的<code>false</code>值只搜索当前级别.</li>
 *     <li>元素有一个属性(使用<code>roleName</code>属性配置名称) 包含由该元素表示的角色的名称.</li>
 *     </ul></li>
 *
 * <li>此外，角色可以由用户元素(可以使用<code>userRoleName</code>属性配置)中属性的值表示.</li>
 *
 * <li>注意标准<code>&lt;security-role-ref&gt;</code>元素，在Web应用程序部署描述符中，
 * 		允许应用程序以编程方式引用角色，而不是目录服务器本身所使用的名称.</li>
 * </ul>
 *
 * <p><strong>TODO</strong> - 支持连接池 (包含消息格式对象), 因此<code>authenticate()</code> 不需要同步.</p>
 */
public class JNDIRealm extends RealmBase {


    // ----------------------------------------------------- Instance Variables

    /**
     * 要使用的身份验证类型
     */
    protected String authentication = null;

    /**
     * 服务器的连接用户名
     */
    protected String connectionName = null;


    /**
     * 服务器的连接密码.
     */
    protected String connectionPassword = null;


    /**
     * 服务器的连接URL.
     */
    protected String connectionURL = null;


    /**
     * 链接到目录服务器的目录上下文.
     */
    protected DirContext context = null;


    /**
     * JNDI上下文工厂用来获取InitialContext.
     * 默认情况下，假定LDAP服务器使用标准的JNDI LDAP供应者
     */
    protected String contextFactory = "com.sun.jndi.ldap.LdapCtxFactory";

    
    /**
     * 如何在搜索中取消引用别名.
     */
    protected String derefAliases = null;

    /**
     * 保存环境属性的名称的常量, 指定取消引用别名的方式.
     */
    public final static String DEREF_ALIASES = "java.naming.ldap.derefAliases";

    /**
     * 描述信息.
     */
    protected static final String info =
        "org.apache.catalina.realm.JNDIRealm/1.0";


    /**
     * 描述信息.
     */
    protected static final String name = "JNDIRealm";


    /**
     * 用于与目录服务器通信的协议.
     */
    protected String protocol = null;


    /**
     * 应该如何处理下线?  微软Active Directory无法处理默认情况, 因此，一个针对AD的应用程序必须将下线设置为"follow".
     */
    protected String referrals = null;


    /**
     * 用户搜索的基本元素.
     */
    protected String userBase = "";


    /**
     * 用于搜索用户的消息格式, 使用"{0}" 标记用户名所在的位置.
     */
    protected String userSearch = null;


    /**
     * 当前<code>userSearch</code>关联的MessageFormat对象.
     */
    protected MessageFormat userSearchFormat = null;


    /**
     * 应该搜索整个子树来匹配用户吗?
     */
    protected boolean userSubtree = false;


    /**
     * 用于检索用户密码的属性名称.
     */
    protected String userPassword = null;


    /**
     * 一系列LDAP用户模式或路径, ":"-分隔
     * 这些将用于生成用户的专有名称, 使用"{0}" 标记用户名所在的位置.
     * 类似于userPattern, 但允许对用户进行多个搜索.
     */
    protected String[] userPatternArray = null;


    /**
     * 用于形成用户的专有名称的消息格式, 使用"{0}" 标记用户名所在的位置.  
     */
    protected String userPattern = null;


    /**
     * 当前<code>userPatternArray</code>相关的MessageFormat对象数组.
     */
    protected MessageFormat[] userPatternFormatArray = null;


    /**
     * 用于角色搜索的基本元素.
     */
    protected String roleBase = "";


    /**
     * 当前<code>roleSearch</code>关联的MessageFormat对象.
     */
    protected MessageFormat roleFormat = null;


    /**
     * 用户条目中包含该用户角色的属性的名称
     */
    protected String userRoleName = null;


    /**
     * 包含在别处的角色的属性的名称
     */
    protected String roleName = null;


    /**
     * 用于为用户选择角色的消息格式, 使用"{0}" 标记用户的识别名所在的位置.
     */
    protected String roleSearch = null;


    /**
     * 应该搜索整个子树来匹配成员吗?
     */
    protected boolean roleSubtree = false;

    /**
     * 而另一个URL, 应该连接，如果connectionURL失败.
     */
    protected String alternateURL;

    /**
     * 连接尝试的次数.  如果大于0，则使用备用URL.
     */
    protected int connectionAttempt = 0;

    /**
     * 用于查找和绑定用户的当前用户模式.
     */
    protected int curUserPattern = 0;

    // ------------------------------------------------------------- Properties

    /**
     * 返回要使用的身份验证类型.
     */
    public String getAuthentication() {
        return authentication;
    }

    /**
     * 设置要使用的身份验证类型
     *
     * @param authentication The authentication
     */
    public void setAuthentication(String authentication) {
        this.authentication = authentication;
    }

    /**
     * 返回连接用户名.
     */
    public String getConnectionName() {
        return (this.connectionName);
    }


    /**
     * 设置连接用户名.
     *
     * @param connectionName The new connection username
     */
    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }


    /**
     * 返回连接密码.
     */
    public String getConnectionPassword() {
        return (this.connectionPassword);
    }


    /**
     * 设置连接密码.
     *
     * @param connectionPassword The new connection password
     */
    public void setConnectionPassword(String connectionPassword) {
        this.connectionPassword = connectionPassword;
    }


    /**
     * 返回连接URL.
     */
    public String getConnectionURL() {
        return (this.connectionURL);
    }


    /**
     * 设置连接URL.
     *
     * @param connectionURL The new connection URL
     */
    public void setConnectionURL(String connectionURL) {
        this.connectionURL = connectionURL;
    }


    /**
     * 返回JNDI上下文工厂.
     */
    public String getContextFactory() {
        return (this.contextFactory);
    }


    /**
     * 设置JNDI上下文工厂.
     *
     * @param contextFactory The new context factory
     */
    public void setContextFactory(String contextFactory) {
        this.contextFactory = contextFactory;
    }

    /**
     * 返回derefAliases.
     */
    public java.lang.String getDerefAliases() {
        return derefAliases;
    }  
    
    /**
     * 设置搜索目录时使用的 derefAliases.
     * 
     * @param derefAliases New value of property derefAliases.
     */
    public void setDerefAliases(java.lang.String derefAliases) {
      this.derefAliases = derefAliases;
    }

    /**
     * 返回要使用的协议.
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * 设置协议.
     *
     * @param protocol The new protocol.
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }


    /**
     * 返回当前设置JNDI引用处理.
     */
    public String getReferrals () {
        return referrals;
    }


    /**
     * 如何处理JNDI引用? 忽略, 跟随, 或抛出
     * (see javax.naming.Context.REFERRAL for more information).
     */
    public void setReferrals (String referrals) {
        this.referrals = referrals;
    }


    /**
     * 返回用户搜索的基本元素.
     */
    public String getUserBase() {
        return (this.userBase);
    }


    /**
     * 设置用户搜索的基本元素.
     *
     * @param userBase The new base element
     */
    public void setUserBase(String userBase) {
        this.userBase = userBase;
    }


    /**
     * 返回用于选择用户的消息格式模式.
     */
    public String getUserSearch() {
        return (this.userSearch);
    }


    /**
     * 设置用于选择用户的消息格式模式.
     *
     * @param userSearch The new user search pattern
     */
    public void setUserSearch(String userSearch) {
        this.userSearch = userSearch;
        if (userSearch == null)
            userSearchFormat = null;
        else
            userSearchFormat = new MessageFormat(userSearch);
    }


    /**
     * 返回"search subtree for users"标记.
     */
    public boolean getUserSubtree() {
        return (this.userSubtree);
    }


    /**
     * 设置"search subtree for users"标记.
     *
     * @param userSubtree The new search flag
     */
    public void setUserSubtree(boolean userSubtree) {
        this.userSubtree = userSubtree;
    }


    /**
     * 返回用户角色名称属性名.
     */
    public String getUserRoleName() {
        return userRoleName;
    }


    /**
     * 设置用户角色名称属性名.
     *
     * @param userRoleName The new userRole name attribute name
     */
    public void setUserRoleName(String userRoleName) {
        this.userRoleName = userRoleName;
    }


    /**
     * 返回用于角色搜索的基本元素.
     */
    public String getRoleBase() {
        return (this.roleBase);
    }


    /**
     * 设置用于角色搜索的基本元素
     *
     * @param roleBase The new base element
     */
    public void setRoleBase(String roleBase) {
        this.roleBase = roleBase;
    }


    /**
     * 返回角色名称属性名.
     */
    public String getRoleName() {
        return (this.roleName);
    }


    /**
     * 设置角色名称属性名.
     *
     * @param roleName The new role name attribute name
     */
    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }


    /**
     * 返回用于选择角色的消息格式模式.
     */
    public String getRoleSearch() {
        return (this.roleSearch);
    }


    /**
     * 设置用于选择角色的消息格式模式.
     *
     * @param roleSearch The new role search pattern
     */
    public void setRoleSearch(String roleSearch) {
        this.roleSearch = roleSearch;
        if (roleSearch == null)
            roleFormat = null;
        else
            roleFormat = new MessageFormat(roleSearch);
    }


    /**
     * 返回"search subtree for roles"标记.
     */
    public boolean getRoleSubtree() {
        return (this.roleSubtree);
    }


    /**
     * 设置"search subtree for roles"标记.
     *
     * @param roleSubtree The new search flag
     */
    public void setRoleSubtree(boolean roleSubtree) {
        this.roleSubtree = roleSubtree;
    }


    /**
     * 返回用于检索用户密码的密码属性.
     */
    public String getUserPassword() {
        return (this.userPassword);
    }


    /**
     * 设置用于检索用户密码的密码属性.
     *
     * @param userPassword The new password attribute
     */
    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }


    /**
     * 返回用于选择用户的消息格式模式.
     */
    public String getUserPattern() {
        return (this.userPattern);
    }


    /**
     * 设置用于选择用户的消息格式模式.
     * 这可能是一个简单的模式, 或要尝试的多种模式,用括号分隔. (例如, "cn={0}", 或
     * "(cn={0})(cn={0},o=myorg)" 还支持完整的LDAP搜索字符串,
     * 只支持 "OR", "|" 语法, 因此"(|(cn={0})(cn={0},o=myorg))"是有效的. 复杂搜索字符串 &, 等不支持.
     *
     * @param userPattern The new user pattern
     */
    public void setUserPattern(String userPattern) {
        this.userPattern = userPattern;
        if (userPattern == null)
            userPatternArray = null;
        else {
            userPatternArray = parseUserPatternString(userPattern);
            int len = this.userPatternArray.length;
            userPatternFormatArray = new MessageFormat[len];
            for (int i=0; i < len; i++) {
                userPatternFormatArray[i] =
                    new MessageFormat(userPatternArray[i]);
            }
        }
    }


    public String getAlternateURL() {
        return this.alternateURL;
    }

    public void setAlternateURL(String alternateURL) {
        this.alternateURL = alternateURL;
    }


    // ---------------------------------------------------------- Realm Methods


    /**
     * 返回指定用户名和凭据关联的 Principal; 或者<code>null</code>.
     *
     * 如果JDBC连接有任何错误, 执行查询或返回null的任何操作 (不验证).
     * 此事件也被记录, 连接将被关闭，以便随后的请求将自动重新打开它.
     *
     * @param username 要查找的Principal 用户名
     * @param credentials 验证这个用户名使用的Password 或其它凭据
     */
    public Principal authenticate(String username, String credentials) {

        DirContext context = null;
        Principal principal = null;

        try {

            // 确保有可用的目录上下文
            context = open();

            // 目录上下文将偶尔超时. 再试一次
            try {

                // 如果可能，验证指定的用户名
                principal = authenticate(context, username, credentials);

            } catch (CommunicationException e) {

                // log the exception so we know it's there.
                containerLog.warn(sm.getString("jndiRealm.exception"), e);

                // 关闭连接，以便下次重新打开连接
                if (context != null)
                    close(context);

                // 打开一个新的目录上下文.
                context = open();

                // 再次尝试身份验证
                principal = authenticate(context, username, credentials);
            }

            // 释放这个上下文
            release(context);

            // 返回已经验证的Principal
            return (principal);
        } catch (NamingException e) {

            // Log the problem for posterity
            containerLog.error(sm.getString("jndiRealm.exception"), e);

            // 关闭连接，以便下次重新打开连接
            if (context != null)
                close(context);

            // 返回这个请求的"未认证"
            return (null);
        }
    }

    // ------------------------------------------------------ Protected Methods

    /**
     * 返回指定用户名和凭据关联的 Principal; 或者<code>null</code>.
     *
     * @param context 目录上下文
     * @param username 要查找的Principal 用户名
     * @param credentials 验证这个用户名使用的Password 或其它凭据
     *
     * @exception NamingException 如果出现目录服务器错误
     */
    public synchronized Principal authenticate(DirContext context,
                                               String username,
                                               String credentials)
        throws NamingException {

        if (username == null || username.equals("")
            || credentials == null || credentials.equals(""))
            return (null);

        if (userPatternArray != null) {
            for (curUserPattern = 0;
                 curUserPattern < userPatternFormatArray.length;
                 curUserPattern++) {
                // Retrieve user information
                User user = getUser(context, username);
                if (user != null) {
                    try {
                        // Check the user's credentials
                        if (checkCredentials(context, user, credentials)) {
                            // 搜索其他角色
                            List roles = getRoles(context, user);
                            return (new GenericPrincipal(this,
                                                         username,
                                                         credentials,
                                                         roles));
                        }
                    } catch (InvalidNameException ine) {
                        // Log the problem for posterity
                        containerLog.warn(sm.getString("jndiRealm.exception"), ine);
                        // ignore; 这可能是由于名称不完全符合搜索路径格式, 完全一样-
                        // 合格的名字已经变成搜索路径
                        // 已经包含 cn= 或 vice-versa
                    }
                }
            }
            return null;
        } else {
            // Retrieve user information
            User user = getUser(context, username);
            if (user == null)
                return (null);

            // Check the user's credentials
            if (!checkCredentials(context, user, credentials))
                return (null);

            // 搜索其他角色
            List roles = getRoles(context, user);

            // 创建并返回合适的 Principal
            return (new GenericPrincipal(this, username, credentials, roles));
        }
    }


    /**
     * 返回包含指定用户名的用户信息的用户对象;或者<code>null</code>.
     *
     * 如果指定<code>userPassword</code>配置属性, 该属性的值是从用户的目录项检索的.
     * 如果指定<code>userRoleName</code>配置属性, 该属性的所有值都从目录项中检索.
     *
     * @param context 目录上下文
     * @param username 要查找的用户名
     *
     * @exception NamingException 如果出现目录服务器错误
     */
    protected User getUser(DirContext context, String username)
        throws NamingException {

        User user = null;

        // Get attributes to retrieve from user entry
        ArrayList list = new ArrayList();
        if (userPassword != null)
            list.add(userPassword);
        if (userRoleName != null)
            list.add(userRoleName);
        String[] attrIds = new String[list.size()];
        list.toArray(attrIds);

        // Use pattern or search for user entry
        if (userPatternFormatArray != null) {
            user = getUserByPattern(context, username, attrIds);
        } else {
            user = getUserBySearch(context, username, attrIds);
        }

        return user;
    }


    /**
     * 使用<code>UserPattern</code>配置属性使用指定的用户名定位用户的目录条目并返回用户对象; 或者<code>null</code>.
     *
     * @param context 目录上下文
     * @param username 用户名
     * @param attrIds String[]包含要检索的属性的名称
     *
     * @exception NamingException 如果出现目录服务器错误
     */
    protected User getUserByPattern(DirContext context,
                                              String username,
                                              String[] attrIds)
        throws NamingException {

        if (username == null || userPatternFormatArray[curUserPattern] == null)
            return (null);

        // Form the dn from the user pattern
        String dn = userPatternFormatArray[curUserPattern].format(new String[] { username });

        // Return if no attributes to retrieve
        if (attrIds == null || attrIds.length == 0)
            return new User(username, dn, null, null);

        // Get required attributes from user entry
        Attributes attrs = null;
        try {
            attrs = context.getAttributes(dn, attrIds);
        } catch (NameNotFoundException e) {
            return (null);
        }
        if (attrs == null)
            return (null);

        // Retrieve value of userPassword
        String password = null;
        if (userPassword != null)
            password = getAttributeValue(userPassword, attrs);

        // Retrieve values of userRoleName attribute
        ArrayList roles = null;
        if (userRoleName != null)
            roles = addAttributeValues(userRoleName, attrs, roles);

        return new User(username, dn, password, roles);
    }


    /**
     * 在目录中搜索包含指定用户名的用户信息的用户对象; 或者<code>null</code>.
     *
     * @param context 目录上下文
     * @param username 目录上下文
     * @param attrIds String[]包含要检索的属性的名称
     *
     * @exception NamingException 如果出现目录服务器错误
     */
    protected User getUserBySearch(DirContext context,
                                           String username,
                                           String[] attrIds)
        throws NamingException {

        if (username == null || userSearchFormat == null)
            return (null);

        // Form the search filter
        String filter = userSearchFormat.format(new String[] { username });

        // Set up the search controls
        SearchControls constraints = new SearchControls();

        if (userSubtree) {
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
        }
        else {
            constraints.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        }

        // Specify the attributes to be retrieved
        if (attrIds == null)
            attrIds = new String[0];
        constraints.setReturningAttributes(attrIds);

        NamingEnumeration results =
            context.search(userBase, filter, constraints);


        // Fail if no entries found
        if (results == null || !results.hasMore()) {
            return (null);
        }

        // Get result for the first entry found
        SearchResult result = (SearchResult)results.next();

        // Check no further entries were found
        if (results.hasMore()) {
            if(containerLog.isInfoEnabled())
                containerLog.info("username " + username + " has multiple entries");
            return (null);
        }

        // Get the entry's distinguished name
        NameParser parser = context.getNameParser("");
        Name contextName = parser.parse(context.getNameInNamespace());
        Name baseName = parser.parse(userBase);

        // Bugzilla 32269
        Name entryName = parser.parse(new CompositeName(result.getName()).get(0));

        Name name = contextName.addAll(baseName);
        name = name.addAll(entryName);
        String dn = name.toString();

        if (containerLog.isTraceEnabled())
            containerLog.trace("  entry found for " + username + " with dn " + dn);

        // Get the entry's attributes
        Attributes attrs = result.getAttributes();
        if (attrs == null)
            return null;

        // Retrieve value of userPassword
        String password = null;
        if (userPassword != null)
            password = getAttributeValue(userPassword, attrs);

        // Retrieve values of userRoleName attribute
        ArrayList roles = null;
        if (userRoleName != null)
            roles = addAttributeValues(userRoleName, attrs, roles);

        return new User(username, dn, password, roles);
    }


    /**
     * 检查给定的用户是否可以通过给定的凭据进行身份验证. 
     * 如果指定<code>userPassword</code>配置属性, 先前从目录检索的凭据与用户呈现的显式进行的比较.
     * 否则，通过绑定到用户的目录检查所提交的凭据.
     *
     * @param context 目录上下文
     * @param user 要验证的User
     * @param credentials 用户提交的凭据
     *
     * @exception NamingException 如果出现目录服务器错误
     */
    protected boolean checkCredentials(DirContext context,
                                     User user,
                                     String credentials)
         throws NamingException {

         boolean validated = false;

         if (userPassword == null) {
             validated = bindAsUser(context, user, credentials);
         } else {
             validated = compareCredentials(context, user, credentials);
         }

         if (containerLog.isTraceEnabled()) {
             if (validated) {
                 containerLog.trace(sm.getString("jndiRealm.authenticateSuccess",
                                  user.username));
             } else {
                 containerLog.trace(sm.getString("jndiRealm.authenticateFailure",
                                  user.username));
             }
         }
         return (validated);
     }



    /**
     * 检查用户提交的凭据与从目录检索的凭据是否匹配.
     *
     * @param context 目录上下文
     * @param user 要验证的User
     * @param credentials 用户提交的凭据
     *
     * @exception NamingException 如果出现目录服务器错误
     */
    protected boolean compareCredentials(DirContext context,
                                         User info,
                                         String credentials)
        throws NamingException {

        if (info == null || credentials == null)
            return (false);

        String password = info.password;
        if (password == null)
            return (false);

        // 验证用户指定的凭据
        if (containerLog.isTraceEnabled())
            containerLog.trace("  validating credentials");

        boolean validated = false;
        if (hasMessageDigest()) {
            // iPlanet 支持, 如果值以{SHA1}开头
            // 字符串格式兼容 Base64.encode , 而不是父类的 Hex 编码.
            if (password.startsWith("{SHA}")) {
                /* 同步，由于 super.digest() 正在做同样的事情 */
                synchronized (this) {
                    password = password.substring(5);
                    md.reset();
                    md.update(credentials.getBytes());
                    String digestedPassword =
                        new String(Base64.encode(md.digest()));
                    validated = password.equals(digestedPassword);
                }
            } else if (password.startsWith("{SSHA}")) {
                // Bugzilla 32938
                /* sync since super.digest() does this same thing */
                synchronized (this) {
                    password = password.substring(6);

                    md.reset();
                    md.update(credentials.getBytes());

                    // 解码存储的密码.
                    ByteChunk pwbc = new ByteChunk(password.length());
                    try {
                        pwbc.append(password.getBytes(), 0, password.length());
                    } catch (IOException e) {
                        // Should never happen
                        containerLog.error("Could not append password bytes to chunk: ", e);
                    }

                    CharChunk decoded = new CharChunk();
                    Base64.decode(pwbc, decoded);
                    char[] pwarray = decoded.getBuffer();

                    // 破解密码成 hash and salt.
                    final int saltpos = 20;
                    byte[] hash = new byte[saltpos];
                    for (int i=0; i< hash.length; i++) {
                        hash[i] = (byte) pwarray[i];
                    }

                    byte[] salt = new byte[pwarray.length - saltpos];
                    for (int i=0; i< salt.length; i++)
                        salt[i] = (byte)pwarray[i+saltpos];

                    md.update(salt);
                    byte[] dp = md.digest();

                    validated = Arrays.equals(dp, hash);
                } // End synchronized(this) block
            } else {
                // Hex hashes 不区分大小写
                validated = (digest(credentials).equalsIgnoreCase(password));
            }
        } else
            validated = (digest(credentials).equals(password));
        return (validated);
    }



    /**
     * 检查凭据，通过绑定用户到目录
     *
     * @param context 目录上下文
     * @param user 要验证的用户
     * @param credentials 验证的凭据
     *
     * @exception NamingException 如果出现目录服务器错误
     */
     protected boolean bindAsUser(DirContext context,
                                  User user,
                                  String credentials)
         throws NamingException {
         Attributes attr;

         if (credentials == null || user == null)
             return (false);

         String dn = user.dn;
         if (dn == null)
             return (false);

         // Validate the credentials specified by the user
         if (containerLog.isTraceEnabled()) {
             containerLog.trace("  validating credentials by binding as the user");
        }

        // 设置作为用户绑定的安全环境
        context.addToEnvironment(Context.SECURITY_PRINCIPAL, dn);
        context.addToEnvironment(Context.SECURITY_CREDENTIALS, credentials);

        // 引出LDAP绑定操作
        boolean validated = false;
        try {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace("  binding as "  + dn);
            }
            attr = context.getAttributes("", null);
            validated = true;
        }
        catch (AuthenticationException e) {
            if (containerLog.isTraceEnabled()) {
                containerLog.trace("  bind attempt failed");
            }
        }

        // 恢复原来的安全环境
        if (connectionName != null) {
            context.addToEnvironment(Context.SECURITY_PRINCIPAL,
                                     connectionName);
        } else {
            context.removeFromEnvironment(Context.SECURITY_PRINCIPAL);
        }

        if (connectionPassword != null) {
            context.addToEnvironment(Context.SECURITY_CREDENTIALS,
                                     connectionPassword);
        }
        else {
            context.removeFromEnvironment(Context.SECURITY_CREDENTIALS);
        }

        return (validated);
     }


    /**
     * 返回与给定用户关联的角色列表.
     * 用户目录条目中的任何角色都可以通过目录搜索得到补充. 如果没有与该用户关联的角色，则返回一个零长度列表.
     *
     * @param context 搜索的目录上下文
     * @param user 要检查的用户
     *
     * @exception NamingException 如果出现目录服务器错误
     */
    protected List getRoles(DirContext context, User user)
        throws NamingException {

        if (user == null)
            return (null);

        String dn = user.dn;
        String username = user.username;

        if (dn == null || username == null)
            return (null);

        if (containerLog.isTraceEnabled())
            containerLog.trace("  getRoles(" + dn + ")");

        // 从用户条目检索的角色开始
        ArrayList list = user.roles;
        if (list == null) {
            list = new ArrayList();
        }

        // Are we configured to do role searches?
        if ((roleFormat == null) || (roleName == null))
            return (list);

        // 为适当的搜索设置参数
        String filter = roleFormat.format(new String[] { doRFC2254Encoding(dn), username });
        SearchControls controls = new SearchControls();
        if (roleSubtree)
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        else
            controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        controls.setReturningAttributes(new String[] {roleName});

        // 执行配置的搜索并处理结果
        NamingEnumeration results =
            context.search(roleBase, filter, controls);
        if (results == null)
            return (list);  // Should never happen, but just in case ...
        while (results.hasMore()) {
            SearchResult result = (SearchResult) results.next();
            Attributes attrs = result.getAttributes();
            if (attrs == null)
                continue;
            list = addAttributeValues(roleName, attrs, list);
        }


        if (containerLog.isTraceEnabled()) {
            if (list != null) {
                containerLog.trace("  Returning " + list.size() + " roles");
                for (int i=0; i<list.size(); i++)
                    containerLog.trace(  "  Found role " + list.get(i));
            } else {
                containerLog.trace("  getRoles about to return null ");
            }
        }
        return (list);
    }


    /**
     * 返回指定属性的值
     *
     * @param attrId 属性名
     * @param attrs 包含所需值的属性
     *
     * @exception NamingException 如果出现目录服务器错误
     */
    private String getAttributeValue(String attrId, Attributes attrs)
        throws NamingException {

        if (containerLog.isTraceEnabled())
            containerLog.trace("  retrieving attribute " + attrId);

        if (attrId == null || attrs == null)
            return null;

        Attribute attr = attrs.get(attrId);
        if (attr == null)
            return (null);
        Object value = attr.get();
        if (value == null)
            return (null);
        String valueString = null;
        if (value instanceof byte[])
            valueString = new String((byte[]) value);
        else
            valueString = value.toString();

        return valueString;
    }



    /**
     * 将指定属性的值添加到列表中
     *
     * @param attrId 属性名称
     * @param attrs 包含新值的属性
     * @param values ArrayList 包含迄今发现的值
     *
     * @exception NamingException 如果出现目录服务器错误
     */
    private ArrayList addAttributeValues(String attrId,
                                         Attributes attrs,
                                         ArrayList values)
        throws NamingException{

        if (containerLog.isTraceEnabled())
            containerLog.trace("  retrieving values for attribute " + attrId);
        if (attrId == null || attrs == null)
            return values;
        if (values == null)
            values = new ArrayList();
        Attribute attr = attrs.get(attrId);
        if (attr == null)
            return (values);
        NamingEnumeration e = attr.getAll();
        while(e.hasMore()) {
            String value = (String)e.next();
            values.add(value);
        }
        return values;
    }


    /**
     * 关闭任何与目录服务器的打开连接.
     *
     * @param context 要关闭的目录上下文
     */
    protected void close(DirContext context) {

        // Do nothing if there is no opened connection
        if (context == null)
            return;

        // Close our opened connection
        try {
            if (containerLog.isDebugEnabled())
                containerLog.debug("Closing directory context");
            context.close();
        } catch (NamingException e) {
            containerLog.error(sm.getString("jndiRealm.close"), e);
        }
        this.context = null;

    }


    /**
     * 返回实现类的名称.
     */
    protected String getName() {
        return (name);
    }


    /**
     * 返回指定用户名的密码.
     */
    protected String getPassword(String username) {
        return (null);
    }


    /**
     * 返回给定用户名的Principal.
     */
    protected Principal getPrincipal(String username) {
        return (null);
    }



    /**
     * 打开并返回一个连接到配置的目录服务器.
     *
     * @exception NamingException 如果出现目录服务器错误
     */
    protected DirContext open() throws NamingException {

        // Do nothing if there is a directory server connection already open
        if (context != null)
            return (context);

        try {

            // 确保有可用的目录上下文
            context = new InitialDirContext(getDirectoryContextEnvironment());

        } catch (Exception e) {

            connectionAttempt = 1;

            // log the first exception.
            containerLog.warn(sm.getString("jndiRealm.exception"), e);

            // 尝试连接到备用URL
            context = new InitialDirContext(getDirectoryContextEnvironment());

        } finally {
            // 重置它，以防连接超时.
            // the primary may come back.
            connectionAttempt = 0;
        }
        return (context);
    }

    /**
     * 创建目录上下文配置.
     *
     * @return java.util.Hashtable 目录上下文的配置.
     */
    protected Hashtable getDirectoryContextEnvironment() {

        Hashtable env = new Hashtable();

        // Configure our directory context environment.
        if (containerLog.isDebugEnabled() && connectionAttempt == 0)
            containerLog.debug("Connecting to URL " + connectionURL);
        else if (containerLog.isDebugEnabled() && connectionAttempt > 0)
            containerLog.debug("Connecting to URL " + alternateURL);
        env.put(Context.INITIAL_CONTEXT_FACTORY, contextFactory);
        if (connectionName != null)
            env.put(Context.SECURITY_PRINCIPAL, connectionName);
        if (connectionPassword != null)
            env.put(Context.SECURITY_CREDENTIALS, connectionPassword);
        if (connectionURL != null && connectionAttempt == 0)
            env.put(Context.PROVIDER_URL, connectionURL);
        else if (alternateURL != null && connectionAttempt > 0)
            env.put(Context.PROVIDER_URL, alternateURL);
        if (authentication != null)
            env.put(Context.SECURITY_AUTHENTICATION, authentication);
        if (protocol != null)
            env.put(Context.SECURITY_PROTOCOL, protocol);
        if (referrals != null)
            env.put(Context.REFERRAL, referrals);
        if (derefAliases != null)
            env.put(JNDIRealm.DEREF_ALIASES, derefAliases);

        return env;

    }


    /**
     * 释放连接，这样它可以循环使用.
     *
     * @param context The directory context to release
     */
    protected void release(DirContext context) {
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * @exception LifecycleException 如果此组件检测到阻止其启动的致命错误
     */
    public void start() throws LifecycleException {
        // Validate that we can open our connection
        try {
            open();
        } catch (NamingException e) {
            throw new LifecycleException(sm.getString("jndiRealm.open"), e);
        }

        // Perform normal superclass initialization
        super.start();
    }


    /**
     * @exception LifecycleException 如果此组件检测到需要报告的致命错误
     */
    public void stop() throws LifecycleException {

        // Perform normal superclass finalization
        super.stop();

        // Close any open directory server connection
        close(this.context);
    }

    /**
     * 给定包含用户位置的LDAP模式的字符串 (在一个伪LDAP搜索字符串格式中用圆括号分隔开 -
     * "(location1)(location2)", 返回这些路径的数组. 真正的 LDAP 还支持搜索字符串(只支持 "|" "OR" 类型).
     *
     * @param userPatternString - 由括号包围的字符串LDAP搜索路径
     */
    protected String[] parseUserPatternString(String userPatternString) {

        if (userPatternString != null) {
            ArrayList pathList = new ArrayList();
            int startParenLoc = userPatternString.indexOf('(');
            if (startParenLoc == -1) {
                // no parens here; return whole thing
                return new String[] {userPatternString};
            }
            int startingPoint = 0;
            while (startParenLoc > -1) {
                int endParenLoc = 0;
                // 剔除括号和包含整个声明的括号(在有效的LDAP搜索字符串的情况下: (|(something)(somethingelse))
                while ( (userPatternString.charAt(startParenLoc + 1) == '|') ||
                        (startParenLoc != 0 && userPatternString.charAt(startParenLoc - 1) == '\\') ) {
                    startParenLoc = userPatternString.indexOf("(", startParenLoc+1);
                }
                endParenLoc = userPatternString.indexOf(")", startParenLoc+1);
                // 剔除了结束括号
                while (userPatternString.charAt(endParenLoc - 1) == '\\') {
                    endParenLoc = userPatternString.indexOf(")", endParenLoc+1);
                }
                String nextPathPart = userPatternString.substring
                    (startParenLoc+1, endParenLoc);
                pathList.add(nextPathPart);
                startingPoint = endParenLoc+1;
                startParenLoc = userPatternString.indexOf('(', startingPoint);
            }
            return (String[])pathList.toArray(new String[] {});
        }
        return null;
    }


    /**
     * 给定LDAP搜索字符串, 根据RFC 2254规则返回具有某些字符的字符串.
     * 字符映射如下所示:
     *     char ->  Replacement
     *    ---------------------------
     *     *  -> \2a
     *     (  -> \28
     *     )  -> \29
     *     \  -> \5c
     *     \0 -> \00
     * @param inString 根据RFC 2254准则要转义的字符串
     * @return String 转义/编码结果
     */
    protected String doRFC2254Encoding(String inString) {
        StringBuffer buf = new StringBuffer(inString.length());
        for (int i = 0; i < inString.length(); i++) {
            char c = inString.charAt(i);
            switch (c) {
                case '\\':
                    buf.append("\\5c");
                    break;
                case '*':
                    buf.append("\\2a");
                    break;
                case '(':
                    buf.append("\\28");
                    break;
                case ')':
                    buf.append("\\29");
                    break;
                case '\0':
                    buf.append("\\00");
                    break;
                default:
                    buf.append(c);
                    break;
            }
        }
        return buf.toString();
    }
}

// ------------------------------------------------------ Private Classes

/**
 * 表示一个User的私有类
 */
class User {
    String username = null;
    String dn = null;
    String password = null;
    ArrayList roles = null;


    User(String username, String dn, String password, ArrayList roles) {
        this.username = username;
        this.dn = dn;
        this.password = password;
        this.roles = roles;
    }

}
