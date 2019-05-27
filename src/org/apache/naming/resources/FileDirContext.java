package org.apache.naming.resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;

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
 * Filesystem Directory Context implementation helper class.
 */
public class FileDirContext extends BaseDirContext {

    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( FileDirContext.class );

    // -------------------------------------------------------------- Constants


    protected static final int BUFFER_SIZE = 2048;


    // ----------------------------------------------------------- Constructors

    public FileDirContext() {
        super();
    }

    public FileDirContext(Hashtable env) {
        super(env);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 文档基础路径
     */
    protected File base = null;


    /**
     * 基础的绝对标准文件名.
     */
    protected String absoluteBase = null;


    /**
     * 案例的敏感性
     */
    protected boolean caseSensitive = true;


    /**
     * 是否允许连接.
     */
    protected boolean allowLinking = false;


    // ------------------------------------------------------------- Properties


    /**
     * 设置文档根目录
     * 
     * @param docBase 新文档根
     * 
     * @exception IllegalArgumentException 如果该实现不支持指定的值
     * @exception IllegalArgumentException 如果创建了一个格式错误的URL
     */
    public void setDocBase(String docBase) {

    // 验证文档根的格式
    if (docBase == null)
        throw new IllegalArgumentException
        (sm.getString("resources.null"));

    // 计算引用该文档基目录的文件对象
    base = new File(docBase);
        try {
            base = base.getCanonicalFile();
        } catch (IOException e) {
            // Ignore
        }

    // 验证文档库是一个现有目录
    if (!base.exists() || !base.isDirectory() || !base.canRead())
        throw new IllegalArgumentException(sm.getString("fileResources.base", docBase));
        this.absoluteBase = base.getAbsolutePath();
        super.setDocBase(docBase);
    }


    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }


    public boolean isCaseSensitive() {
        return caseSensitive;
    }


    /**
     * 设置允许的链接
     */
    public void setAllowLinking(boolean allowLinking) {
        this.allowLinking = allowLinking;
    }


    /**
     * 是否允许连接
     */
    public boolean getAllowLinking() {
        return allowLinking;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 释放分配给此目录上下文的任何资源
     */
    public void release() {
        caseSensitive = true;
        allowLinking = false;
        absoluteBase = null;
        base = null;
        super.release();
    }


    // -------------------------------------------------------- Context Methods


    /**
     * 检索命名对象
     * 
     * @param name 要查找的对象的名称
     * @return 绑定到名称的对象
     * @exception NamingException 如果遇到命名异常
     */
    public Object lookup(String name)
        throws NamingException {
        Object result = null;
        File file = file(name);

        if (file == null)
            throw new NamingException
                (sm.getString("resources.notFound", name));

        if (file.isDirectory()) {
            FileDirContext tempContext = new FileDirContext(env);
            tempContext.setDocBase(file.getPath());
            tempContext.setAllowLinking(getAllowLinking());
            tempContext.setCaseSensitive(isCaseSensitive());
            result = tempContext;
        } else {
            result = new FileResource(file);
        }
        return result;
    }


    /**
     * 取消绑定命名对象.从目标上下文中删除名称中的终端原子名称--除了名称的最终原子部分以外的所有名称.
     * <p>
     * 此方法是幂等的. 即使在目标上下文中没有绑定终端原子名称，它也会成功,
     * 但是抛出NameNotFoundException ,如果任何中间上下文不存在. 
     * 
     * @param name 绑定的名称; 不能是空的
     * @exception NameNotFoundException 如果中间上下文不存在
     * @exception NamingException 如果遇到命名异常
     */
    public void unbind(String name) throws NamingException {
        File file = file(name);

        if (file == null)
            throw new NamingException(sm.getString("resources.notFound", name));

        if (!file.delete())
            throw new NamingException(sm.getString("resources.unbindFailed", name));
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

        File file = file(oldName);

        if (file == null)
            throw new NamingException(sm.getString("resources.notFound", oldName));

        File newFile = new File(base, newName);

        file.renameTo(newFile);
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
    public NamingEnumeration list(String name) throws NamingException {
        File file = file(name);

        if (file == null)
            throw new NamingException
                (sm.getString("resources.notFound", name));

        return new NamingContextEnumeration(list(file).iterator());
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

        File file = file(name);

        if (file == null)
            throw new NamingException
                (sm.getString("resources.notFound", name));

        return new NamingContextBindingsEnumeration(list(file).iterator());
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
    public void destroySubcontext(String name)
        throws NamingException {
        unbind(name);
    }


    /**
     * 检索命名对象, 以下链接，除了名称的终端原子组件.如果绑定到名称的对象不是链接, 返回对象本身.
     * 
     * @param name 要查找的对象的名称
     * @return 绑定到名称的对象, 不遵循终端链接
     * @exception NamingException 如果遇到命名异常
     */
    public Object lookupLink(String name) throws NamingException {
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

        // Building attribute list
        File file = file(name);

        if (file == null)
            throw new NamingException
                (sm.getString("resources.notFound", name));

        return new FileResourceAttributes(file);
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
    }


    /**
     * 使用一个有序的修改列表, 修改与命名对象相关联的属性. 修改按指定的顺序执行.
     * 每个修改指定一个修改操作代码和一个用于操作的属性. 在可能的情况下, 修改自动执行.
     * 
     * @param name 将更新其属性的对象的名称
     * @param mods 要执行的有序序列; 不能是null
     * @exception AttributeModificationException 如果修改不能成功完成
     * @exception NamingException 如果遇到命名异常
     */
    public void modifyAttributes(String name, ModificationItem[] mods)
        throws NamingException {
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

        // Note: No custom attributes allowed
        File file = new File(base, name);
        if (file.exists())
            throw new NameAlreadyBoundException
                (sm.getString("resources.alreadyBound", name));

        rebind(name, obj, attrs);
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

        // Note: No custom attributes allowed
        // Check obj type
        File file = new File(base, name);

        InputStream is = null;
        if (obj instanceof Resource) {
            try {
                is = ((Resource) obj).streamContent();
            } catch (IOException e) {
            }
        } else if (obj instanceof InputStream) {
            is = (InputStream) obj;
        } else if (obj instanceof DirContext) {
            if (file.exists()) {
                if (!file.delete())
                    throw new NamingException
                        (sm.getString("resources.bindFailed", name));
            }
            if (!file.mkdir())
                throw new NamingException
                    (sm.getString("resources.bindFailed", name));
        }
        if (is == null)
            throw new NamingException
                (sm.getString("resources.bindFailed", name));

        // Open os

        try {
            FileOutputStream os = null;
            byte buffer[] = new byte[BUFFER_SIZE];
            int len = -1;
            try {
                os = new FileOutputStream(file);
                while (true) {
                    len = is.read(buffer);
                    if (len == -1)
                        break;
                    os.write(buffer, 0, len);
                }
            } finally {
                if (os != null)
                    os.close();
                is.close();
            }
        } catch (IOException e) {
            throw new NamingException
                (sm.getString("resources.bindFailed", e));
        }
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

        File file = new File(base, name);
        if (file.exists())
            throw new NameAlreadyBoundException
                (sm.getString("resources.alreadyBound", name));
        if (!file.mkdir())
            throw new NamingException
                (sm.getString("resources.bindFailed", name));
        return (DirContext) lookup(name);
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
        return null;
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
        return null;
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
        return null;
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
        return null;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 返回上下文相对路径, 以"/"开头, 表示指定路径的规范版本, 在".." 和 "."元素被解析之后.
     * 如果指定的路径试图超出当前上下文的边界(即太多的".."路径元素), 返回<code>null</code>.
     *
     * @param path 标准化路径
     */
    protected String normalize(String path) {

	    String normalized = path;
	
	    // 规范的斜线，必要时加上斜杠
	    if (File.separatorChar == '\\' && normalized.indexOf('\\') >= 0)
	        normalized = normalized.replace('\\', '/');
	    if (!normalized.startsWith("/"))
	        normalized = "/" + normalized;
	
	    // 在标准化路径中解析"//"
	    while (true) {
	        int index = normalized.indexOf("//");
	        if (index < 0)
	        break;
	        normalized = normalized.substring(0, index) +
	        normalized.substring(index + 1);
	    }
	
	    // 解析"/./"
	    while (true) {
	        int index = normalized.indexOf("/./");
	        if (index < 0)
	        break;
	        normalized = normalized.substring(0, index) +
	        normalized.substring(index + 2);
	    }
	
	    // 解析"/../"
	    while (true) {
	        int index = normalized.indexOf("/../");
	        if (index < 0)
	        break;
	        if (index == 0)
	        return (null);  // Trying to go outside our context
	        int index2 = normalized.lastIndexOf('/', index - 1);
	        normalized = normalized.substring(0, index2) +
	        normalized.substring(index + 3);
	    }
	    // 返回已完成的标准化路径
	    return (normalized);
    }


    /**
     * 返回指定的标准化上下文相对路径的文件对象，如果它存在并可读的话. 或者返回<code>null</code>.
     *
     * @param name 规范上下文相关路径(与领先 '/')
     */
    protected File file(String name) {

        File file = new File(base, name);
        if (file.exists() && file.canRead()) {

        	if (allowLinking)
        		return file;
        	
            // 检查此文件是否属于我们的根路径
            String canPath = null;
            try {
                canPath = file.getCanonicalPath();
            } catch (IOException e) {
            }
            if (canPath == null)
                return null;

            // 检查是否在Web应用程序根目录之外
            if (!canPath.startsWith(absoluteBase)) {
                return null;
            }

            // Case sensitivity check
            if (caseSensitive) {
                String fileAbsPath = file.getAbsolutePath();
                if (fileAbsPath.endsWith("."))
                    fileAbsPath = fileAbsPath + "/";
                String absPath = normalize(fileAbsPath);
                if (canPath != null)
                    canPath = normalize(canPath);
                if ((absoluteBase.length() < absPath.length())
                    && (absoluteBase.length() < canPath.length())) {
                    absPath = absPath.substring(absoluteBase.length() + 1);
                    if ((canPath == null) || (absPath == null))
                        return null;
                    if (absPath.equals(""))
                        absPath = "/";
                    canPath = canPath.substring(absoluteBase.length() + 1);
                    if (canPath.equals(""))
                        canPath = "/";
                    if (!canPath.equals(absPath))
                        return null;
                }
            }
        } else {
            return null;
        }
        return file;
    }


    /**
     * 列出集合中的资源
     *
     * @param file Collection
     * @return Vector containg NamingEntry objects
     */
    protected ArrayList list(File file) {

        ArrayList entries = new ArrayList();
        if (!file.isDirectory())
            return entries;
        String[] names = file.list();
        if (names==null) {
            /* Some IO error occurred such as bad file permissions.
               Prevent a NPE with Arrays.sort(names) */
            log.warn(sm.getString("fileResources.listingNull",
                                  file.getAbsolutePath()));
            return entries;
        }

        Arrays.sort(names);             // Sort alphabetically
        if (names == null)
            return entries;
        NamingEntry entry = null;

        for (int i = 0; i < names.length; i++) {

            File currentFile = new File(file, names[i]);
            Object object = null;
            if (currentFile.isDirectory()) {
                FileDirContext tempContext = new FileDirContext(env);
                tempContext.setDocBase(file.getPath());
                tempContext.setAllowLinking(getAllowLinking());
                tempContext.setCaseSensitive(isCaseSensitive());
                object = tempContext;
            } else {
                object = new FileResource(currentFile);
            }
            entry = new NamingEntry(names[i], object, NamingEntry.ENTRY);
            entries.add(entry);
        }
        return entries;
    }


    // ----------------------------------------------- FileResource Inner Class


    /**
     * 指定的资源实现类避免立即打开IputStream为文件(会把文件锁上).
     */
    protected class FileResource extends Resource {


        // -------------------------------------------------------- Constructor


        public FileResource(File file) {
            this.file = file;
        }


        // --------------------------------------------------- Member Variables


        /**
         * 关联的文件对象.
         */
        protected File file;


        /**
         * 文件大小
         */
        protected long length = -1L;


        // --------------------------------------------------- Resource Methods


        /**
         * 内容访问器.
         *
         * @return InputStream
         */
        public InputStream streamContent()
            throws IOException {
            if (binaryContent == null) {
                inputStream = new FileInputStream(file);
            }
            return super.streamContent();
        }
    }


    // ------------------------------------- FileResourceAttributes Inner Class


    /**
     * 指定的资源属性实现类延迟读取(为了加快简单的检查，比如检查最后修改日期).
     */
    protected class FileResourceAttributes extends ResourceAttributes {


        // -------------------------------------------------------- Constructor


        public FileResourceAttributes(File file) {
            this.file = file;
        }

        // --------------------------------------------------- Member Variables


        protected File file;


        protected boolean accessed = false;


        protected String canonicalPath = null;


        // ----------------------------------------- ResourceAttributes Methods


        /**
         * Is collection.
         */
        public boolean isCollection() {
            if (!accessed) {
                collection = file.isDirectory();
                accessed = true;
            }
            return super.isCollection();
        }


        /**
         * 获取内容大小
         *
         * @return content length value
         */
        public long getContentLength() {
            if (contentLength != -1L)
                return contentLength;
            contentLength = file.length();
            return contentLength;
        }


        /**
         * 获取创建时间戳
         *
         * @return creation time value
         */
        public long getCreation() {
            if (creation != -1L)
                return creation;
            creation = file.lastModified();
            return creation;
        }


        /**
         * 获取创建日期
         *
         * @return Creation date value
         */
        public Date getCreationDate() {
            if (creation == -1L) {
                creation = file.lastModified();
            }
            return super.getCreationDate();
        }


        /**
         * 获取最后修改时间戳
         *
         * @return lastModified time value
         */
        public long getLastModified() {
            if (lastModified != -1L)
                return lastModified;
            lastModified = file.lastModified();
            return lastModified;
        }


        /**
         * 获取最后修改日期
         *
         * @return LastModified date value
         */
        public Date getLastModifiedDate() {
            if (lastModified == -1L) {
                lastModified = file.lastModified();
            }
            return super.getLastModifiedDate();
        }


        /**
         * 获取名称
         *
         * @return Name value
         */
        public String getName() {
            if (name == null)
                name = file.getName();
            return name;
        }


        /**
         * 获取资源类型
         *
         * @return String resource type
         */
        public String getResourceType() {
            if (!accessed) {
                collection = file.isDirectory();
                accessed = true;
            }
            return super.getResourceType();
        }

        
        /**
         * 获取规范路径.
         * 
         * @return String 文件的规范路径
         */
        public String getCanonicalPath() {
            if (canonicalPath == null) {
                try {
                    canonicalPath = file.getCanonicalPath();
                } catch (IOException e) {
                    // Ignore
                }
            }
            return canonicalPath;
        }
    }
}

