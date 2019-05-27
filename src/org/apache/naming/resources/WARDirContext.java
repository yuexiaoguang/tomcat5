package org.apache.naming.resources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.naming.CompositeName;
import javax.naming.Name;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NameNotFoundException;
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

import org.apache.naming.NamingContextBindingsEnumeration;
import org.apache.naming.NamingContextEnumeration;
import org.apache.naming.NamingEntry;

/**
 * WAR Directory Context implementation.
 */
public class WARDirContext extends BaseDirContext {

    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( WARDirContext.class );
    
    // ----------------------------------------------------------- Constructors


    public WARDirContext() {
        super();
    }


    public WARDirContext(Hashtable env) {
        super(env);
    }


    protected WARDirContext(ZipFile base, Entry entries) {
        this.base = base;
        this.entries = entries;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The WAR file.
     */
    protected ZipFile base = null;


    /**
     * WAR entries.
     */
    protected Entry entries = null;


    // ------------------------------------------------------------- Properties


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
		    throw new IllegalArgumentException(sm.getString("resources.null"));
		if (!(docBase.endsWith(".war")))
		    throw new IllegalArgumentException(sm.getString("warResources.notWar"));

		// 生成File对象引用此文档基目录
		File base = new File(docBase);

		// 验证文档根目录是否存在
		if (!base.exists() || !base.canRead() || base.isDirectory())
	    	throw new IllegalArgumentException(sm.getString("warResources.invalidWar", docBase));
        try {
            this.base = new ZipFile(base);
        } catch (Exception e) {
        	throw new IllegalArgumentException(sm.getString("warResources.invalidWar", e.getMessage()));
        }
        super.setDocBase(docBase);

        loadEntries();
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 释放分配给此目录上下文的任何资源.
     */
    public void release() {
        entries = null;
        if (base != null) {
            try {
                base.close();
            } catch (IOException e) {
                log.warn
                    ("Exception closing WAR File " + base.getName(), e);
            }
        }
        base = null;
        super.release();
    }


    // -------------------------------------------------------- Context Methods


    /**
     * 检索命名对象.
     * 
     * @param name 要查找的对象的名称
     * @return 绑定到名称的对象
     * @exception NamingException 如果遇到命名异常
     */
    public Object lookup(String name) throws NamingException {
        return lookup(new CompositeName(name));
    }


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
        if (name.isEmpty())
            return this;
        Entry entry = treeLookup(name);
        if (entry == null)
            throw new NamingException
                (sm.getString("resources.notFound", name));
        ZipEntry zipEntry = entry.getEntry();
        if (zipEntry.isDirectory())
            return new WARDirContext(base, entry);
        else
            return new WARResource(entry.getEntry());
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
    public void unbind(String name)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * 将新名称绑定到对象, 并取消绑定旧名称. 两个名称都与此上下文相关. 与旧名称相关联的任何属性都与新名称关联. 
     * 旧名称的中间上下文没有改变.
     * 
     * @param oldName 现有绑定的名称; 不能为空
     * @param newName 新绑定的名称; 不能为空
     * @exception NameAlreadyBoundException 如果newName已绑定
     * @exception NamingException 如果遇到命名异常
     */
    public void rename(String oldName, String newName) throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * 枚举命名上下文中绑定的名称, 连同绑定到它们的对象的类名. 不包括任何子上下文的内容.
     * <p>
     * 如果在该上下文中添加或删除绑定, 它对先前返回的枚举的影响是未定义的.
     * 
     * @param name 要列出的上下文的名称
     * @return 在此上下文中绑定的名称和类名的枚举. 每个枚举元素的类型是NameClassPair.
     * @exception NamingException 如果遇到命名异常
     */
    public NamingEnumeration list(String name)
        throws NamingException {
        return list(new CompositeName(name));
    }


    /**
     * 枚举命名上下文中绑定的名称, 连同绑定到它们的对象的类名. 不包括任何子上下文的内容.
     * <p>
     * 如果在该上下文中添加或删除绑定, 它对先前返回的枚举的影响是未定义的.
     * 
     * @param name 要列出的上下文的名称
     * @return 在此上下文中绑定的名称和类名的枚举. 每个枚举元素的类型是NameClassPair.
     * @exception NamingException 如果遇到命名异常
     */
    public NamingEnumeration list(Name name)
        throws NamingException {
        if (name.isEmpty())
            return new NamingContextEnumeration(list(entries).iterator());
        Entry entry = treeLookup(name);
        if (entry == null)
            throw new NamingException
                (sm.getString("resources.notFound", name));
        return new NamingContextEnumeration(list(entry).iterator());
    }


    /**
     * 枚举命名上下文中绑定的名称, 连同绑定到它们的对象的类名. 不包括任何子上下文的内容.
     * <p>
     * 如果在该上下文中添加或删除绑定, 它对先前返回的枚举的影响是未定义的.
     * 
     * @param name 要列出的上下文的名称
     * @return 在此上下文中绑定的枚举. 每个枚举元素的类型是Binding.
     * @exception NamingException 如果遇到命名异常
     */
    public NamingEnumeration listBindings(String name)
        throws NamingException {
        return listBindings(new CompositeName(name));
    }


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
        if (name.isEmpty())
            return new NamingContextBindingsEnumeration(list(entries).iterator());
        Entry entry = treeLookup(name);
        if (entry == null)
            throw new NamingException
                (sm.getString("resources.notFound", name));
        return new NamingContextBindingsEnumeration(list(entry).iterator());
    }


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
    public void destroySubcontext(String name) throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * 检索命名对象, 以下链接，除了名称的终端原子组件.如果绑定到名称的对象不是链接, 返回对象本身.
     * 
     * @param name 要查找的对象的名称
     * @return 绑定到名称的对象, 不遵循终端链接
     * @exception NamingException 如果遇到命名异常
     */
    public Object lookupLink(String name)
        throws NamingException {
        // Note : Links are not supported
        return lookup(name);
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
    public String getNameInNamespace()
        throws NamingException {
        return docBase;
    }


    // ----------------------------------------------------- DirContext Methods


    /**
     * 检索与命名对象关联的选定属性
     * 查看类描述, 相关属性模型, 属性类型名称, 和操作属性.
     * 
     * @return 请求的属性; never null
     * @param name 从中检索属性的对象的名称
     * @param attrIds 要检索的属性的标识符. null表示应检索所有属性; 空数组表示不应检索任何内容
     * @exception NamingException 如果遇到命名异常
     */
    public Attributes getAttributes(String name, String[] attrIds)
        throws NamingException {
        return getAttributes(new CompositeName(name), attrIds);
    }


    /**
     * 检索与命名对象关联的选定属性
     * 
     * @return 请求的属性; never null
     * @param name 从中检索属性的对象的名称
     * @param attrIds 要检索的属性的标识符. null表示应检索所有属性; 空数组表示不应检索任何内容
     * @exception NamingException 如果遇到命名异常
     */
    public Attributes getAttributes(Name name, String[] attrIds)
        throws NamingException {
        
        Entry entry = null;
        if (name.isEmpty())
            entry = entries;
        else
            entry = treeLookup(name);
        if (entry == null)
            throw new NamingException
                (sm.getString("resources.notFound", name));
        
        ZipEntry zipEntry = entry.getEntry();

        ResourceAttributes attrs = new ResourceAttributes();
        attrs.setCreationDate(new Date(zipEntry.getTime()));
        attrs.setName(entry.getName());
        if (!zipEntry.isDirectory())
            attrs.setResourceType("");
        attrs.setContentLength(zipEntry.getSize());
        attrs.setLastModified(zipEntry.getTime());
        
        return attrs;
        
    }


    /**
     * 修改与命名对象相关联的属性. 修改的顺序没有指定. 在可能的情况下, 修改自动执行.
     * 
     * @param name 将更新其属性的对象的名称
     * @param mod_op 修改操作, 其中之一: ADD_ATTRIBUTE, REPLACE_ATTRIBUTE, REMOVE_ATTRIBUTE
     * @param attrs 用于修改的属性; 不能是null
     * @exception AttributeModificationException 如果修改不能成功完成
     * @exception NamingException 如果遇到命名异常
     */
    public void modifyAttributes(String name, int mod_op, Attributes attrs)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * 修改与命名对象相关联的属性. 修改的顺序没有指定. 在可能的情况下, 修改自动执行.
     * 
     * @param name 将更新其属性的对象的名称
     * @param mod_op 修改操作, 其中之一: ADD_ATTRIBUTE, REPLACE_ATTRIBUTE, REMOVE_ATTRIBUTE
     * @param attrs 用于修改的属性; 不能是null
     * @exception AttributeModificationException 如果修改不能成功完成
     * @exception NamingException 如果遇到命名异常
     */
    public void modifyAttributes(String name, ModificationItem[] mods)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


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
    public void bind(String name, Object obj, Attributes attrs)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


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
    public void rebind(String name, Object obj, Attributes attrs)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


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
    public DirContext createSubcontext(String name, Attributes attrs)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


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
    public DirContext getSchema(String name)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * 检索包含命名对象的类定义的架构对象的上下文.
     * 
     * @param name 要检索对象类定义的对象的名称
     * @return 包含命名对象的类定义的DirContext; never null
     * @exception OperationNotSupportedException 如果模式不支持
     * @exception NamingException 如果遇到命名异常
     */
    public DirContext getSchemaClassDefinition(String name)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * 在单个上下文中搜索包含指定属性集的对象, 并检索选定的属性. 搜索使用默认SearchControls 设置.
     * 
     * @param name 要搜索的上下文的名称
     * @param matchingAttributes 要搜索的属性. 如果是空或null, 返回目标上下文中的所有对象.
     * @param attributesToReturn 返回的属性. null表示将返回所有属性; 空数组表示不返回任何一个数组.
     * @return 一个非空的枚举SearchResult对象. 每个SearchResult包含通过attributesToReturn和相应对象的名称标识的属性, 命名为与命名名称相关的上下文.
     * @exception NamingException 如果遇到命名异常
     */
    public NamingEnumeration search(String name, Attributes matchingAttributes,
                                    String[] attributesToReturn)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    /**
     * 在单个上下文中搜索包含指定属性集的对象. 此方法返回此类对象的所有属性. 
     * 它相当于提供 null作为 atributesToReturn 参数给方法search(Name, Attributes, String[]).
     * 
     * @param name 要搜索的上下文的名称
     * @param matchingAttributes 要搜索的属性. 如果是空或null, 返回目标上下文中的所有对象.
     * @return 一个非空的枚举SearchResult对象. 每个SearchResult包含通过attributesToReturn和相应对象的名称标识的属性, 命名为与命名名称相关的上下文.
     * @exception NamingException 如果遇到命名异常
     */
    public NamingEnumeration search(String name, Attributes matchingAttributes)
        throws NamingException {
        throw new OperationNotSupportedException();
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
    public NamingEnumeration search(String name, String filter, 
                                    SearchControls cons)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


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
    public NamingEnumeration search(String name, String filterExpr, 
                                    Object[] filterArgs, SearchControls cons)
        throws NamingException {
        throw new OperationNotSupportedException();
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 规范从ZIP读取的项的名称.
     */
    protected String normalize(ZipEntry entry) {

        String result = "/" + entry.getName();
        if (entry.isDirectory()) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }


    /**
     * 构造一个包含在WAR文件中的条目的树.
     */
    protected void loadEntries() {
        try {
            Enumeration entryList = base.entries();
            entries = new Entry("/", new ZipEntry("/"));
            
            while (entryList.hasMoreElements()) {
                
                ZipEntry entry = (ZipEntry) entryList.nextElement();
                String name = normalize(entry);
                int pos = name.lastIndexOf('/');
                // 检查父条目是否存在, 如果不存在, 创建.
                // 这为WAR文件修复了一个bug, 不要为目录记录单独的ZIP条目.
                int currentPos = -1;
                int lastPos = 0;
                while ((currentPos = name.indexOf('/', lastPos)) != -1) {
                    Name parentName = new CompositeName(name.substring(0, lastPos));
                    Name childName = new CompositeName(name.substring(0, currentPos));
                    String entryName = name.substring(lastPos, currentPos);
                    // 应该通过循环在最后一个周期创建父级
                    Entry parent = treeLookup(parentName);
                    Entry child = treeLookup(childName);
                    if (child == null) {
                        // 为丢失条目创建一个新条目并取消首位的 '/'字符, 通过规范的方法连接, 并添加'/' 字符到目录的结尾
                        String zipName = name.substring(1, currentPos) + "/";
                        child = new Entry(entryName, new ZipEntry(zipName));
                        if (parent != null)
                            parent.addChild(child);
                    }
                    // Increment lastPos
                    lastPos = currentPos + 1;
                }
                String entryName = name.substring(pos + 1, name.length());
                Name compositeName = new CompositeName(name.substring(0, pos));
                Entry parent = treeLookup(compositeName);
                Entry child = new Entry(entryName, entry);
                if (parent != null)
                    parent.addChild(child);
            }
        } catch (Exception e) {
        }
    }


    /**
     * 进入树查找.
     */
    protected Entry treeLookup(Name name) {
        if (name.isEmpty())
            return entries;
        Entry currentEntry = entries;
        for (int i = 0; i < name.size(); i++) {
            if (name.get(i).length() == 0)
                continue;
            currentEntry = currentEntry.getChild(name.get(i));
            if (currentEntry == null)
                return null;
        }
        return currentEntry;
    }


    /**
     * 列出子元素.
     */
    protected ArrayList list(Entry entry) {
        
        ArrayList entries = new ArrayList();
        Entry[] children = entry.getChildren();
        Arrays.sort(children);
        NamingEntry namingEntry = null;
        
        for (int i = 0; i < children.length; i++) {
            ZipEntry current = children[i].getEntry();
            Object object = null;
            if (current.isDirectory()) {
                object = new WARDirContext(base, children[i]);
            } else {
                object = new WARResource(current);
            }
            namingEntry = new NamingEntry
                (children[i].getName(), object, NamingEntry.ENTRY);
            entries.add(namingEntry);
        }
        return entries;
    }


    // ---------------------------------------------------- Entries Inner Class


    /**
     * 条目结构.
     */
    protected class Entry implements Comparable {

        // -------------------------------------------------------- Constructor
        
        public Entry(String name, ZipEntry entry) {
            this.name = name;
            this.entry = entry;
        }
        
        // --------------------------------------------------- Member Variables
        
        protected String name = null;
        
        protected ZipEntry entry = null;
        
        protected Entry children[] = new Entry[0];
        
        // ----------------------------------------------------- Public Methods
        
        
        public int compareTo(Object o) {
            if (!(o instanceof Entry))
                return (+1);
            return (name.compareTo(((Entry) o).getName()));
        }

        public ZipEntry getEntry() {
            return entry;
        }
        
        
        public String getName() {
            return name;
        }
        
        
        public void addChild(Entry entry) {
            Entry[] newChildren = new Entry[children.length + 1];
            for (int i = 0; i < children.length; i++)
                newChildren[i] = children[i];
            newChildren[children.length] = entry;
            children = newChildren;
        }


        public Entry[] getChildren() {
            return children;
        }


        public Entry getChild(String name) {
            for (int i = 0; i < children.length; i++) {
                if (children[i].name.equals(name)) {
                    return children[i];
                }
            }
            return null;
        }
    }


    // ------------------------------------------------ WARResource Inner Class


    /**
     * 这个指定的资源实现类避免立即打开IputStream为WAR.
     */
    protected class WARResource extends Resource {
        
        // -------------------------------------------------------- Constructor
        
        public WARResource(ZipEntry entry) {
            this.entry = entry;
        }
        
        // --------------------------------------------------- Member Variables
        
        protected ZipEntry entry;
        
        // ----------------------------------------------------- Public Methods
        
        /**
         * 内容访问器
         * 
         * @return InputStream
         */
        public InputStream streamContent()
            throws IOException {
            try {
                if (binaryContent == null) {
                    inputStream = base.getInputStream(entry);
                }
            } catch (ZipException e) {
                throw new IOException(e.getMessage());
            }
            return super.streamContent();
        }
    }
}

