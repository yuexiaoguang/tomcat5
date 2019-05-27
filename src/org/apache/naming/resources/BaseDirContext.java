package org.apache.naming.resources;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NameNotFoundException;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NotContextException;
import javax.naming.OperationNotSupportedException;
import javax.naming.directory.AttributeModificationException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidAttributesException;
import javax.naming.directory.InvalidSearchControlsException;
import javax.naming.directory.InvalidSearchFilterException;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;

import org.apache.naming.NameParserImpl;
import org.apache.naming.StringManager;

/**
 * Directory Context实现类帮助类.
 */
public abstract class BaseDirContext implements DirContext {


    // ----------------------------------------------------------- Constructors


    /**
     * 构建基本目录上下文
     */
    public BaseDirContext() {
        this.env = new Hashtable();
    }


    /**
     * 使用给定的环境构建基本目录上下文
     */
    public BaseDirContext(Hashtable env) {
        this.env = env;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 文档基础路径
     */
    protected String docBase = null;


    /**
     * 环境.
     */
    protected Hashtable env;


    /**
     * The string manager for this package.
     */
    protected StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 此上下文的名称分析器
     */
    protected final NameParser nameParser = new NameParserImpl();


    /**
     * 是否缓存.
     */
    protected boolean cached = true;


    /**
     * Cache TTL.
     */
    protected int cacheTTL = 5000; // 5s


    /**
     * 缓存资源最大大小.
     */
    protected int cacheMaxSize = 10240; // 10 MB


    // ------------------------------------------------------------- Properties


    /**
     * 返回此组件的文档根目录
     */
    public String getDocBase() {
        return (this.docBase);
    }


    /**
     * 设置此组件的文档根目录
     *
     * @param docBase The new document root
     *
     * @exception IllegalArgumentException 如果该实现不支持指定的值
     * @exception IllegalArgumentException 如果这会创建一个格式错误的URL
     */
    public void setDocBase(String docBase) {
        // 验证建议文档根的格式
        if (docBase == null)
            throw new IllegalArgumentException
                (sm.getString("resources.null"));

        // 更改文档根属性
        this.docBase = docBase;
    }


    /**
     * 设置是否缓存
     */
    public void setCached(boolean cached) {
        this.cached = cached;
    }


    /**
     * 是否缓存 ?
     */
    public boolean isCached() {
        return cached;
    }


    /**
     * Set cache TTL.
     */
    public void setCacheTTL(int cacheTTL) {
        this.cacheTTL = cacheTTL;
    }


    /**
     * Get cache TTL.
     */
    public int getCacheTTL() {
        return cacheTTL;
    }


    /**
     * 缓存资源最大大小 KB.
     */
    public int getCacheMaxSize() {
        return cacheMaxSize;
    }


    /**
     * 缓存资源最大大小 KB.
     */
    public void setCacheMaxSize(int cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 为该目录上下文分配资源
     */
    public void allocate() {
    }


    /**
     * 释放分配给此目录上下文的任何资源
     */
    public void release() {
    }


    // -------------------------------------------------------- Context Methods


    /**
     * 检索命名对象.
     * 如果名称是空的, 返回此上下文的新实例(它表示与此上下文相同的命名上下文,但它的环境可以独立修改，可以并发访问).
     * 
     * @param name 要查找的对象的名称
     * @return 绑定到名称的对象
     * @exception NamingException 如果遇到命名异常
     */
    public Object lookup(Name name)
        throws NamingException {
        return lookup(name.toString());
    }


    /**
     * 检索命名对象.
     * 
     * @param name 要查找的对象的名称
     * @return 绑定到名称的对象
     * @exception NamingException 如果遇到命名异常
     */
    public abstract Object lookup(String name)
        throws NamingException;


    /**
     * 将名称绑定到对象. 所有中间上下文和目标上下文必须存在(除了名称的最终原子组件以外的所有名称) 
     * 
     * @param name 绑定的名称; 可能是空
     * @param obj 要绑定的对象; 可能是null
     * @exception NameAlreadyBoundException 如果名称已绑定
     * @exception InvalidAttributesException 如果对象没有提供所有强制属性
     * @exception NamingException if a naming exception is encountered
     */
    public void bind(Name name, Object obj)
        throws NamingException {
        bind(name.toString(), obj);
    }


    /**
     * 将名称绑定到对象
     * 
     * @param name 绑定的名称; 可能是空
     * @param obj 要绑定的对象; 可能是null
     * @exception NameAlreadyBoundException 如果名称已绑定
     * @exception InvalidAttributesException 如果对象没有提供所有强制属性
     * @exception NamingException if a naming exception is encountered
     */
    public void bind(String name, Object obj)
        throws NamingException {
        bind(name, obj, null);
    }


    /**
     * 将名称绑定到对象, 覆盖任何现有的绑定.
     * 所有中间上下文和目标上下文必须已经存在 (除了名称的最终原子组件以外的所有名称)
     * <p>
     * 如果对象是一个DirContext, 与该名称相关联的任何现有属性都替换为该对象的属性. 
     * 否则, 与名称相关联的任何现有属性都保持不变
     * 
     * @param name 绑定的名称; 可能是空
     * @param obj 要绑定的对象; 可能是null
     * @exception InvalidAttributesException 如果对象没有提供所有强制属性
     * @exception NamingException if a naming exception is encountered
     */
    public void rebind(Name name, Object obj) throws NamingException {
        rebind(name.toString(), obj);
    }


    /**
     * 将名称绑定到对象, 覆盖任何现有的绑定.
     * 
     * @param name 绑定的名称; 可能是空
     * @param obj 要绑定的对象; 可能是null
     * @exception InvalidAttributesException 如果对象没有提供所有强制属性
     * @exception NamingException 如果遇到命名异常
     */
    public void rebind(String name, Object obj) throws NamingException {
        rebind(name, obj, null);
    }


    /**
     * 取消绑定命名对象. 从目标上下文中删除名称中的终端原子名称--除了名称的最终原子部分以外的所有名称.
     * <p>
     * 此方法是幂等的. 即使在目标上下文中没有绑定终端原子名称，它也会成功,
     * 但是抛出NameNotFoundException ,如果任何中间上下文不存在. 
     * 
     * @param name 绑定的名称; 可能是空
     * @exception NameNotFoundException 如果中间上下文不存在
     * @exception NamingException 如果遇到命名异常
     */
    public void unbind(Name name)
        throws NamingException {
        unbind(name.toString());
    }


    /**
     * 取消绑定命名对象.
     * 
     * @param name 绑定的名称; 可能是空
     * @exception NameNotFoundException 如果中间上下文不存在
     * @exception NamingException 如果遇到命名异常
     */
    public abstract void unbind(String name) throws NamingException;


    /**
     * 将新名称绑定到对象, 并取消绑定旧名称. 两个名称都与此上下文相关. 与旧名称相关联的任何属性都与新名称关联. 
     * 旧名称的中间上下文没有改变.
     * 
     * @param oldName 现有绑定的名称; 不能为空
     * @param newName 新绑定的名称; 不能为空
     * @exception NameAlreadyBoundException 如果newName已绑定
     * @exception NamingException 如果遇到命名异常
     */
    public void rename(Name oldName, Name newName) throws NamingException {
        rename(oldName.toString(), newName.toString());
    }


    /**
     * 将新名称绑定到对象, 并取消绑定旧名称.
     * 
     * @param oldName 现有绑定的名称; 不能为空
     * @param newName 新绑定的名称; 不能为空
     * @exception NameAlreadyBoundException 如果newName已绑定
     * @exception NamingException 如果遇到命名异常
     */
    public abstract void rename(String oldName, String newName)
        throws NamingException;


    /**
     * 枚举命名上下文中绑定的名称, 连同绑定到它们的对象的类名. 不包括任何子上下文的内容.
     * <p>
     * 如果在该上下文中添加或删除绑定, 它对先前返回的枚举的影响是未定义的.
     * 
     * @param name 要列出的上下文的名称
     * @return 在此上下文中绑定的名称和类名的枚举. 每个枚举元素的类型是NameClassPair.
     * @exception NamingException 如果遇到命名异常
     */
    public NamingEnumeration list(Name name) throws NamingException {
        return list(name.toString());
    }


    /**
     * 枚举命名上下文中绑定的名称, 连同绑定到它们的对象的类名.
     * 
     * @param name 要列出的上下文的名称
     * @return 在此上下文中绑定的名称和类名的枚举. 每个枚举元素的类型是NameClassPair.
     * @exception NamingException 如果遇到命名异常
     */
    public abstract NamingEnumeration list(String name)
        throws NamingException;


    /**
     * 枚举命名上下文中绑定的名称, 连同绑定到它们的对象的类名. 不包括任何子上下文的内容.
     * <p>
     * 如果在该上下文中添加或删除绑定, 它对先前返回的枚举的影响是未定义的.
     * 
     * @param name 要列出的上下文的名称
     * @return 在此上下文中绑定的枚举. 每个枚举元素的类型是Binding.
     * @exception NamingException 如果遇到命名异常
     */
    public NamingEnumeration listBindings(Name name)
        throws NamingException {
        return listBindings(name.toString());
    }


    /**
     * 枚举命名上下文中绑定的名称, 连同绑定到它们的对象.
     * 
     * @param name 要列出的上下文的名称
     * @return 在此上下文中绑定的枚举. 每个枚举元素的类型是Binding.
     * @exception NamingException 如果遇到命名异常
     */
    public abstract NamingEnumeration listBindings(String name)
        throws NamingException;


    /**
     * 销毁指定的上下文并将其从命名空间中删除. 与名称相关联的任何属性也被移除. 中间上下文不会被销毁.
     * <p>
     * 此方法是幂等的. 即使在目标上下文中没有绑定终端原子名称，它也会成功, 但是会抛出NameNotFoundException, 如果任何中间上下文不存在. 
     * 
     * 在联合命名系统中, 一个命名系统的上下文可以绑定到另一个名称中. 随后可以在外来的上下文中使用复合名称查找和执行操作.
     * 但是, 销毁上下文的时候使用这个复合名称将失败并抛出NotContextException, 因为外来的上下文不是绑定的上下文的 "subcontext".
     * 相反, 使用 unbind() 删除绑定的外来上下文. 销毁外来的上下文需要执行外来上下文的"native"命名系统的上下文的 destroySubcontext()方法.
     * 
     * @param name 要销毁的上下文的名称; 不能是空
     * @exception NameNotFoundException 如果中间上下文不存在
     * @exception NotContextException 如果名称被绑定，但不命名上下文, 或者不命名适当类型的上下文
     */
    public void destroySubcontext(Name name) throws NamingException {
        destroySubcontext(name.toString());
    }


    /**
     * 销毁指定的上下文并将其从命名空间中删除. 与名称相关联的任何属性也被移除. 中间上下文不会被销毁.
     * <p>
     * 此方法是幂等的. 即使在目标上下文中没有绑定终端原子名称，它也会成功, 但是会抛出NameNotFoundException, 如果任何中间上下文不存在. 
     * 
     * @param name 要销毁的上下文的名称; 不能是空
     * @exception NameNotFoundException 如果中间上下文不存在
     * @exception NotContextException 如果名称被绑定，但不命名上下文, 或者不命名适当类型的上下文
     */
    public abstract void destroySubcontext(String name)
        throws NamingException;


    /**
     * 创建并绑定新上下文. 使用给定名称创建新上下文并将其绑定到目标上下文中(除了名称的最终原子组件以外的所有名称).
     * 所有中间上下文和目标上下文必须已经存在.
     * 
     * @param name 要创建的上下文的名称; 不能是空
     * @return 新创建的上下文
     * @exception NameAlreadyBoundException 如果名称已绑定
     * @exception InvalidAttributesException 如果子上下文的创建需要属性的强制性规范
     * @exception NamingException 如果遇到命名异常
     */
    public Context createSubcontext(Name name)
        throws NamingException {
        return createSubcontext(name.toString());
    }


    /**
     * 创建并绑定新上下文.
     * 
     * @param name 要创建的上下文的名称; 不能是空
     * @return 新创建的上下文
     * @exception NameAlreadyBoundException 如果名称已绑定
     * @exception InvalidAttributesException 如果子上下文的创建需要属性的强制性规范
     * @exception NamingException 如果遇到命名异常
     */
    public Context createSubcontext(String name)
        throws NamingException {
        return createSubcontext(name, null);
    }


    /**
     * 检索命名对象, 以下链接，除了名称的终端原子组件.如果绑定到名称的对象不是链接, 返回对象本身.
     * 
     * @param name 要查找的对象的名称
     * @return 绑定到名称的对象, 不遵循终端链接
     * @exception NamingException 如果遇到命名异常
     */
    public Object lookupLink(Name name)
        throws NamingException {
        return lookupLink(name.toString());
    }


    /**
     * 检索命名对象, 以下链接，除了名称的终端原子组件.
     * 
     * @param name 要查找的对象的名称
     * @return 绑定到名称的对象, 不遵循终端链接
     * @exception NamingException 如果遇到命名异常
     */
    public abstract Object lookupLink(String name)
        throws NamingException;


    /**
     * 检索与命名上下文关联的解析器. 在名称空间联合中, 不同的命名系统将解析的名称不同.
     * 此方法允许应用程序获得一个解析器，使用特定命名系统的命名约定将名称解析为它们的原子组件. 在任何单一命名系统中, 
     * 此方法返回的NameParser对象必须相等(使用equals() 比较).
     * 
     * @param name 获取解析器的上下文的名称
     * @return 可以将复合名称解析为原子组件的名称解析器
     * @exception NamingException 如果遇到命名异常
     */
    public NameParser getNameParser(Name name)
        throws NamingException {
        return new NameParserImpl();
    }


    /**
     * 检索与命名上下文关联的解析器
     * 
     * @param name 获取解析器的上下文的名称
     * @return 可以将复合名称解析为原子组件的名称解析器
     * @exception NamingException 如果遇到命名异常
     */
    public NameParser getNameParser(String name)
        throws NamingException {
        return new NameParserImpl();
    }


    /**
     * 用与此上下文相关的名称组成此上下文的名称.
     * <p>
     * 给出与此上下文相关的名称(name), 这个上下文的名称（前缀）相对于它的祖先之一, 此方法使用与命名系统相关的语法来返回两个名称的联合.
     * 也就是说, 如果名称命名一个与此上下文相关的对象, 结果是相同对象的名称, 但相对于祖先的上下文. 名称不能为 null.
     * 
     * @param name 与此上下文相关的名称
     * @param prefix 此上下文相对于其祖先之一的名称
     * @return 前缀和名称的构成
     * @exception NamingException 如果遇到命名异常
     */
    public Name composeName(Name name, Name prefix)
        throws NamingException {
        prefix = (Name) prefix.clone();
        return prefix.addAll(name);
    }


    /**
     * 用与此上下文相关的名称组成此上下文的名称.
     * 
     * @param name 与此上下文相关的名称
     * @param prefix 此上下文相对于其祖先之一的名称
     * @return 前缀和名称的构成
     * @exception NamingException 如果遇到命名异常
     */
    public String composeName(String name, String prefix)
        throws NamingException {
        return prefix + "/" + name;
    }


    /**
     * 将新的环境属性添加到此上下文的环境中.
     * 如果属性已经存在, 它的值被覆盖.
     * 
     * @param propName 要添加的环境属性的名称; 不能是null
     * @param propVal 要添加的属性的值; 不能是 null
     * @exception NamingException 如果遇到命名异常
     */
    public Object addToEnvironment(String propName, Object propVal)
        throws NamingException {
        return env.put(propName, propVal);
    }


    /**
     * 从此上下文环境中移除环境属性. 
     * 
     * @param propName 要删除的环境属性的名称;不能是null
     * @exception NamingException 如果遇到命名异常
     */
    public Object removeFromEnvironment(String propName)
        throws NamingException {
        return env.remove(propName);
    }


    /**
     * 检索此上下文中有效的环境. 有关环境属性的详细信息，请参见类描述. 
     * 调用者不应对返回的对象做任何更改: 它们对上下文的影响是未定义的. 此上下文的环境可以使用 addToEnvironment() 和 removeFromEnvironment().
     * 
     * @return 这个上下文的环境; never null
     * @exception NamingException 如果遇到命名异常
     */
    public Hashtable getEnvironment()
        throws NamingException {
        return env;
    }


    /**
     * 关闭这个上下文. 此方法立即释放此上下文的资源, 而不是等待垃圾收集器自动释放它们.
     * 此方法是幂等的: 在已经关闭的上下文中调用它没有效果. 不允许在封闭上下文中调用任何其他方法, 并导致未定义的行为.
     * 
     * @exception NamingException 如果遇到命名异常
     */
    public void close()
        throws NamingException {
        env.clear();
    }


    /**
     * 在自己的命名空间中检索此上下文的全名
     * <p>
     * 许多命名服务都有一个 "full name"对于各自名称空间中的对象.
     * 例如, LDAP条目有一个专有名称, DNS记录具有完全限定名. 此方法允许客户端应用程序检索此名称. 
     * 此方法返回的字符串不是一个JNDI复合名称, 而且不应直接传递到上下文的方法. 在命名系统中，全名这个概念没有意义, 
     * OperationNotSupportedException 将被抛出.
     * 
     * @return 在自己的命名空间中的此上下文的名称; never null
     * @exception OperationNotSupportedException 如果命名系统没有全名的概念
     * @exception NamingException 如果遇到命名异常
     */
    public abstract String getNameInNamespace()
        throws NamingException;


    // ----------------------------------------------------- DirContext Methods


    /**
     * 检索与命名对象相关联的所有属性
     * 
     * @return 与名称相关联的属性集合. 如果名称没有属性，则返回空属性集合; never null.
     * @param name 从中检索属性的对象的名称
     * @exception NamingException 如果遇到命名异常
     */
    public Attributes getAttributes(Name name)
        throws NamingException {
        return getAttributes(name.toString());
    }


    /**
     * 检索与命名对象相关联的所有属性
     * 
     * @return 与名称相关联的属性集合
     * @param name 从中检索属性的对象的名称
     * @exception NamingException 如果遇到命名异常
     */
    public Attributes getAttributes(String name)
        throws NamingException {
        return getAttributes(name, null);
    }


    /**
     * 检索与命名对象关联的选定属性
     * 查看类描述, 相关属性模型, 属性类型名称, 和操作属性.
     * 
     * @return 请求的属性; never null
     * @param name 从中检索属性的对象的名称
     * @param attrIds 要检索的属性的标识符. null表示应检索所有属性; 空数组表示不应检索任何内容
     * @exception NamingException 如果遇到命名异常
     */
    public Attributes getAttributes(Name name, String[] attrIds)
        throws NamingException {
        return getAttributes(name.toString(), attrIds);
    }
    
    
    /**
     * 检索与命名对象关联的选定属性
     * 
     * @return 请求的属性; never null
     * @param name 从中检索属性的对象的名称
     * @param attrIds 要检索的属性的标识符. null表示应检索所有属性; 空数组表示不应检索任何内容
     * @exception NamingException 如果遇到命名异常
     */
    public abstract Attributes getAttributes(String name, String[] attrIds)
        throws NamingException;


    /**
     * 修改与命名对象相关联的属性. 修改的顺序没有指定. 在可能的情况下, 修改自动执行.
     * 
     * @param name 将更新其属性的对象的名称
     * @param mod_op 修改操作, 其中之一: ADD_ATTRIBUTE, REPLACE_ATTRIBUTE, REMOVE_ATTRIBUTE
     * @param attrs 用于修改的属性; 不能是null
     * @exception AttributeModificationException 如果修改不能成功完成
     * @exception NamingException 如果遇到命名异常
     */
    public void modifyAttributes(Name name, int mod_op, Attributes attrs)
        throws NamingException {
        modifyAttributes(name.toString(), mod_op, attrs);
    }


    /**
     * 修改与命名对象相关联的属性.
     * 
     * @param name 将更新其属性的对象的名称
     * @param mod_op 修改操作, 其中之一: ADD_ATTRIBUTE, REPLACE_ATTRIBUTE, REMOVE_ATTRIBUTE
     * @param attrs 用于修改的属性; 不能是null
     * @exception AttributeModificationException 如果修改不能成功完成
     * @exception NamingException 如果遇到命名异常
     */
    public abstract void modifyAttributes
        (String name, int mod_op, Attributes attrs)
        throws NamingException;


    /**
     * 使用一个有序的修改列表, 修改与命名对象相关联的属性. 修改按指定的顺序执行.
     * 每个修改指定一个修改操作代码和一个用于操作的属性. 在可能的情况下, 修改自动执行.
     * 
     * @param name 将更新其属性的对象的名称
     * @param mods 要执行的有序序列; 不能是null
     * @exception AttributeModificationException 如果修改不能成功完成
     * @exception NamingException 如果遇到命名异常
     */
    public void modifyAttributes(Name name, ModificationItem[] mods)
        throws NamingException {
        modifyAttributes(name.toString(), mods);
    }


    /**
     * 使用一个有序的修改列表, 修改与命名对象相关联的属性.
     * 
     * @param name 将更新其属性的对象的名称
     * @param mods 要执行的有序序列; 不能是null
     * @exception AttributeModificationException 如果修改不能成功完成
     * @exception NamingException 如果遇到命名异常
     */
    public abstract void modifyAttributes(String name, ModificationItem[] mods)
        throws NamingException;


    /**
     * 将名称绑定到对象, 连同相关属性.
     * 如果attrs是 null, 由此产生的结合将拥有obj相关的属性, 如果 obj 是一个 DirContext, 否则就没有属性.
     * 如果 attrs non-null, 由此产生的结合将把 attrs 作为它自己的属性; 任何obj相关的属性将忽略.
     * 
     * @param name 绑定的名称; 不能是空的
     * @param obj 绑定的对象; 可能是null
     * @param attrs 与绑定相关联的属性
     * @exception NameAlreadyBoundException 如果名称已绑定
     * @exception InvalidAttributesException 如果不支持绑定的"mandatory"属性
     * @exception NamingException 如果遇到命名异常
     */
    public void bind(Name name, Object obj, Attributes attrs)
        throws NamingException {
        bind(name.toString(), obj, attrs);
    }


    /**
     * 将名称绑定到对象, 连同相关属性.
     * 
     * @param name 绑定的名称; 不能是空的
     * @param obj 绑定的对象; 可能是null
     * @param attrs 与绑定相关联的属性
     * @exception NameAlreadyBoundException 如果名称已绑定
     * @exception InvalidAttributesException 如果不支持绑定的"mandatory"属性
     * @exception NamingException 如果遇到命名异常
     */
    public abstract void bind(String name, Object obj, Attributes attrs)
        throws NamingException;


    /**
     * 将名称绑定到对象, 连同相关属性, 覆盖存在的绑定.
     * 如果attrs 是 null 以及 obj 是一个 DirContext, 将使用obj的属性.
     * 如果attrs 是 null 以及 obj 不是一个 DirContext, 与已在目录中绑定的对象相关联的任何现有属性保持不变. 如果attrs non-null, 
     * 与已经绑定到目录中的对象相关联的任何现有属性将被删除，attrs将和命名的对象相关联. 如果obj 是一个 DirContext 以及 attrs 是 non-null, obj的属性将忽略.
     * 
     * @param name 绑定的名称; 不能是空的
     * @param obj 绑定的对象; 可能是null
     * @param attrs 与绑定相关联的属性
     * @exception InvalidAttributesException 如果不支持绑定的"mandatory"属性
     * @exception NamingException 如果遇到命名异常
     */
    public void rebind(Name name, Object obj, Attributes attrs)
        throws NamingException {
        rebind(name.toString(), obj, attrs);
    }


    /**
     * 将名称绑定到对象, 连同相关属性, 覆盖存在的绑定.
     * 
     * @param name 绑定的名称; 不能是空的
     * @param obj 绑定的对象; 可能是null
     * @param attrs 与绑定相关联的属性
     * @exception InvalidAttributesException 如果不支持绑定的"mandatory"属性
     * @exception NamingException 如果遇到命名异常
     */
    public abstract void rebind(String name, Object obj, Attributes attrs)
        throws NamingException;


    /**
     * 创建并绑定新上下文, 连同相关属性. 
     * 此方法创建具有给定名称的新子上下文, 在目标上下文中绑定它(除了名称的最终原子组件以外的所有名称),
     * 并将提供的属性与新创建的对象关联起来. 所有中间和目标上下文必须已经存在. 如果属性是null, 这个方法相当于
     * Context.createSubcontext().
     * 
     * @param name 要创建的上下文的名称; 不能是空的
     * @param attrs 与新创建的上下文关联的属性
     * @return 新创建的上下文
     * @exception NameAlreadyBoundException 如果名称已绑定
     * @exception InvalidAttributesException 如果属性不包含创建所需的所有强制属性
     * @exception NamingException 如果遇到命名异常
     */
    public DirContext createSubcontext(Name name, Attributes attrs)
        throws NamingException {
        return createSubcontext(name.toString(), attrs);
    }


    /**
     * 创建并绑定新上下文, 连同相关属性. 
     * 
     * @param name 要创建的上下文的名称; 不能是空的
     * @param attrs 与新创建的上下文关联的属性
     * @return 新创建的上下文
     * @exception NameAlreadyBoundException 如果名称已绑定
     * @exception InvalidAttributesException 如果属性不包含创建所需的所有强制属性
     * @exception NamingException 如果遇到命名异常
     */
    public abstract DirContext createSubcontext(String name, Attributes attrs)
        throws NamingException;


    /**
     * 检索与命名对象关联的架构.
     * 模式描述有关命名空间的结构和存储在其中的属性的规则. 模式指定哪些类型的对象可以添加到目录中，以及它们可以添加到哪里; 
     * 对象可以具有哪些强制属性和可选属性. 对模式的支持范围是特定于目录的.
     * 
     * @param name 要检索其模式的对象的名称
     * @return 与上下文关联的模式; never null
     * @exception OperationNotSupportedException 如果模式不支持
     * @exception NamingException 如果遇到命名异常
     */
    public DirContext getSchema(Name name) throws NamingException {
        return getSchema(name.toString());
    }


    /**
     * 检索与命名对象关联的架构
     * 
     * @param name 要检索其模式的对象的名称
     * @return 与上下文关联的模式; never null
     * @exception OperationNotSupportedException 如果模式不支持
     * @exception NamingException 如果遇到命名异常
     */
    public abstract DirContext getSchema(String name)
        throws NamingException;


    /**
     * 检索包含命名对象的类定义的架构对象的上下文.
     * 
     * @param name 要检索对象类定义的对象的名称
     * @return 包含命名对象的类定义的DirContext; never null
     * @exception OperationNotSupportedException 如果模式不支持
     * @exception NamingException 如果遇到命名异常
     */
    public DirContext getSchemaClassDefinition(Name name)
        throws NamingException {
        return getSchemaClassDefinition(name.toString());
    }


    /**
     * 检索包含命名对象的类定义的架构对象的上下文.
     * 
     * @param name 要检索对象类定义的对象的名称
     * @return 包含命名对象的类定义的DirContext; never null
     * @exception OperationNotSupportedException 如果模式不支持
     * @exception NamingException 如果遇到命名异常
     */
    public abstract DirContext getSchemaClassDefinition(String name)
        throws NamingException;


    /**
     * 在单个上下文中搜索包含指定属性集的对象, 并检索选定的属性. 搜索使用默认SearchControls 设置.
     * 
     * @param name 要搜索的上下文的名称
     * @param matchingAttributes 要搜索的属性. 如果是空或null, 返回目标上下文中的所有对象.
     * @param attributesToReturn 返回的属性. null表示将返回所有属性; 空数组表示不返回任何一个数组.
     * @return 一个非空的枚举SearchResult对象. 每个SearchResult包含通过attributesToReturn和相应对象的名称标识的属性, 命名为与命名名称相关的上下文.
     * @exception NamingException 如果遇到命名异常
     */
    public NamingEnumeration search(Name name, Attributes matchingAttributes,
                                    String[] attributesToReturn)
        throws NamingException {
        return search(name.toString(), matchingAttributes, attributesToReturn);
    }


    /**
     * 在单个上下文中搜索包含指定属性集的对象, 并检索选定的属性.
     * 
     * @param name 要搜索的上下文的名称
     * @param matchingAttributes 要搜索的属性. 如果是空或null, 返回目标上下文中的所有对象.
     * @param attributesToReturn 返回的属性. null表示将返回所有属性; 空数组表示不返回任何一个数组.
     * @return 一个非空的枚举SearchResult对象. 每个SearchResult包含通过attributesToReturn和相应对象的名称标识的属性, 命名为与命名名称相关的上下文.
     * @exception NamingException 如果遇到命名异常
     */
    public abstract NamingEnumeration search(String name, Attributes matchingAttributes,
         String[] attributesToReturn)
        throws NamingException;


    /**
     * 在单个上下文中搜索包含指定属性集的对象. 此方法返回此类对象的所有属性. 
     * 它相当于提供 null作为 atributesToReturn 参数给方法search(Name, Attributes, String[]).
     * 
     * @param name 要搜索的上下文的名称
     * @param matchingAttributes 要搜索的属性. 如果是空或null, 返回目标上下文中的所有对象.
     * @return 一个非空的枚举SearchResult对象. 每个SearchResult包含通过attributesToReturn和相应对象的名称标识的属性, 命名为与命名名称相关的上下文.
     * @exception NamingException 如果遇到命名异常
     */
    public NamingEnumeration search(Name name, Attributes matchingAttributes)
        throws NamingException {
        return search(name.toString(), matchingAttributes);
    }


    /**
     * 在单个上下文中搜索包含指定属性集的对象.
     * 
     * @param name 要搜索的上下文的名称
     * @param matchingAttributes 要搜索的属性. 如果是空或null, 返回目标上下文中的所有对象.
     * @return 一个非空的枚举SearchResult对象. 每个SearchResult包含通过attributesToReturn和相应对象的名称标识的属性, 命名为与命名名称相关的上下文.
     * @exception NamingException 如果遇到命名异常
     */
    public abstract NamingEnumeration search(String name, Attributes matchingAttributes)
        throws NamingException;


    /**
     * 在指定的上下文或对象中搜索满足给定搜索过滤器的条目. 执行搜索控件指定的搜索.
     * 
     * @param name 要搜索的上下文或对象的名称
     * @param filter 用于搜索的过滤器表达式; 不能是null
     * @param cons 控制搜索的搜索控件. 如果是null, 使用默认搜索控件(相当于(new SearchControls())).
     * @return 满足过滤器的对象的SearchResults的枚举; never null
     * @exception InvalidSearchFilterException 如果指定的搜索过滤器不受底层目录的支持或理解
     * @exception InvalidSearchControlsException 如果搜索控件包含无效设置
     * @exception NamingException 如果遇到命名异常
     */
    public NamingEnumeration search(Name name, String filter, SearchControls cons)
        throws NamingException {
        return search(name.toString(), filter, cons);
    }


    /**
     * 在指定的上下文或对象中搜索满足给定搜索过滤器的条目. 执行搜索控件指定的搜索.
     * 
     * @param name 要搜索的上下文或对象的名称
     * @param filter 用于搜索的过滤器表达式; 不能是null
     * @param cons 控制搜索的搜索控件. 如果是null, 使用默认搜索控件(相当于(new SearchControls())).
     * @return 满足过滤器的对象的SearchResults的枚举; never null
     * @exception InvalidSearchFilterException 如果指定的搜索过滤器不受底层目录的支持或理解
     * @exception InvalidSearchControlsException 如果搜索控件包含无效设置
     * @exception NamingException 如果遇到命名异常
     */
    public abstract NamingEnumeration search(String name, String filter, 
                                             SearchControls cons)
        throws NamingException;


    /**
     * 在指定的上下文或对象中搜索满足给定搜索过滤器的条目. 执行搜索控件指定的搜索.
     * 
     * @param name 要搜索的上下文或对象的名称
     * @param filterExpr 用于搜索的过滤器表达式. 表达式可能包含"{i}"(一个非负整数)表单变量. 不能是 null.
     * @param filterArgs 参数数组代替filterExpr中的变量. filterArgs[i]的值 将替换掉每个"{i}". 如果是null, 相当于空数组.
     * @param cons 控制搜索的搜索控件. 如果是null, 使用默认搜索控件(相当于(new SearchControls())).
     * @return 满足过滤器的对象的SearchResults枚举; never null
     * @exception ArrayIndexOutOfBoundsException 如果filterExpr 中的{i}表达式超出filterArgs数组边界
     * @exception InvalidSearchControlsException 如果cons包含无效设置
     * @exception InvalidSearchFilterException 如果filterExpr 加上filterArgs 是无效的搜索过滤器
     * @exception NamingException 如果遇到命名异常
     */
    public NamingEnumeration search(Name name, String filterExpr, 
                                    Object[] filterArgs, SearchControls cons)
        throws NamingException {
        return search(name.toString(), filterExpr, filterArgs, cons);
    }


    /**
     * 在指定的上下文或对象中搜索满足给定搜索过滤器的条目. 执行搜索控件指定的搜索.
     * 
     * @param name 要搜索的上下文或对象的名称
     * @param filterExpr 用于搜索的过滤器表达式.表达式可能包含"{i}"(一个非负整数)表单变量. 不能是 null.
     * @param filterArgs 参数数组代替filterExpr中的变量. filterArgs[i]的值 将替换掉每个"{i}". 如果是null, 相当于空数组.
     * @param cons 控制搜索的搜索控件. 如果是null, 使用默认搜索控件(相当于(new SearchControls())).
     * @return 满足过滤器的对象的SearchResults枚举; never null
     * @exception ArrayIndexOutOfBoundsException 如果filterExpr 中的{i}表达式超出filterArgs数组边界
     * @exception InvalidSearchControlsException 如果cons包含无效设置
     * @exception InvalidSearchFilterException 如果filterExpr 加上filterArgs 是无效的搜索过滤器
     * @exception NamingException 如果遇到命名异常
     */
    public abstract NamingEnumeration search(String name, String filterExpr, 
         Object[] filterArgs, SearchControls cons) throws NamingException;

}

