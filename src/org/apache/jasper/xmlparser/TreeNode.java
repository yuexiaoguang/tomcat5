package org.apache.jasper.xmlparser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;


/**
 * Node的简单实现类从一个XML文档解析的 Document Object Model (DOM).
 * 这个类用来表示一个 DOM 树, 因此 XML 解析器的<code>org.w3c.dom</code>实现对其余的Jasper不可见.
 * <p>
 * <strong>WARNING</strong> - 新树的初始化, 或者现有的编辑, 不是线程安全的，这样的访问必须同步.
 */
public class TreeNode {

    // ----------------------------------------------------------- Constructors

    /**
     * @param name 这个节点的名称
     */
    public TreeNode(String name) {
        this(name, null);
    }


    /**
     * @param name 这个节点的名称
     * @param parent 这个节点的父节点
     */
    public TreeNode(String name, TreeNode parent) {
        super();
        this.name = name;
        this.parent = parent;
        if (this.parent != null)
            this.parent.addChild(this);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 这个节点的属性, 属性名作为key, 仅在需要时实例化.
     */
    protected HashMap attributes = null;


    /**
     * 与此节点关联的正文文本.
     */
    protected String body = null;


    /**
     * 这个节点的子节点, 仅在需要时实例化.
     */
    protected ArrayList children = null;


    /**
     * 此节点的名称.
     */
    protected String name = null;


    /**
     * 这个节点的父节点.
     */
    protected TreeNode parent = null;


    // --------------------------------------------------------- Public Methods


    /**
     * 添加一个属性, 替换相同名称的现有属性.
     *
     * @param name 要添加的属性名
     * @param value 新的属性值
     */
    public void addAttribute(String name, String value) {
        if (attributes == null)
            attributes = new HashMap();
        attributes.put(name, value);
    }


    /**
     * 添加一个新的子节点.
     *
     * @param node 新的子节点
     */
    public void addChild(TreeNode node) {
        if (children == null)
            children = new ArrayList();
        children.add(node);
    }


    /**
     * 如果指定的节点属性存在，则返回它的值, 或者<code>null</code>.
     *
     * @param name 请求属性的名称
     */
    public String findAttribute(String name) {
        if (attributes == null)
            return (null);
        else
            return ((String) attributes.get(name));
    }


    /**
     * 返回此节点属性名称的迭代器. 如果没有属性, 返回一个空的Iterator.
     */
    public Iterator findAttributes() {
        if (attributes == null)
            return (Collections.EMPTY_LIST.iterator());
        else
            return (attributes.keySet().iterator());
    }


    /**
     * 返回此节点的指定名称的第一个子节点; 或者<code>null</code>.
     *
     * @param name 想要的子元素的名称
     */
    public TreeNode findChild(String name) {
        if (children == null)
            return (null);
        Iterator items = children.iterator();
        while (items.hasNext()) {
            TreeNode item = (TreeNode) items.next();
            if (name.equals(item.getName()))
                return (item);
        }
        return (null);
    }


    /**
     * 返回这个节点的所有子节点的Iterator. 如果没有, 返回一个空的Iterator.
     */
    public Iterator findChildren() {
        if (children == null)
            return (Collections.EMPTY_LIST.iterator());
        else
            return (children.iterator());
    }


    /**
     * 返回指定名称的节点的所有子节点的Iterator. 如果没有, 返回一个空的Iterator.
     *
     * @param name 用于选择子节点的名称
     */
    public Iterator findChildren(String name) {
        if (children == null)
            return (Collections.EMPTY_LIST.iterator());

        ArrayList results = new ArrayList();
        Iterator items = children.iterator();
        while (items.hasNext()) {
            TreeNode item = (TreeNode) items.next();
            if (name.equals(item.getName()))
                results.add(item);
        }
        return (results.iterator());
    }


    /**
     * 返回与此节点关联的正文文本.
     */
    public String getBody() {
        return (this.body);
    }


    /**
     * 返回此节点的名称.
     */
    public String getName() {
        return (this.name);
    }


    /**
     * 删除指定属性名的任何现有值.
     *
     * @param name 要删除的属性名称
     */
    public void removeAttribute(String name) {
        if (attributes != null)
            attributes.remove(name);
    }


    /**
     * 从这个节点删除子节点.
     *
     * @param node 要删除的子节点
     */
    public void removeNode(TreeNode node) {
        if (children != null)
            children.remove(node);
    }


    /**
     * 设置与此节点关联的正文文本.
     *
     * @param body 正文文本
     */
    public void setBody(String body) {
        this.body = body;
    }


    public String toString() {
        StringBuffer sb = new StringBuffer();
        toString(sb, 0, this);
        return (sb.toString());
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 追加到指定的StringBuffer这个节点的字符表示形式, 用指定的缩进量.
     *
     * @param sb 要追加到的StringBuffer
     * @param indent 字符缩进数
     * @param node 要打印的TreeNode
     */
    protected void toString(StringBuffer sb, int indent,
                            TreeNode node) {
        int indent2 = indent + 2;

        // 重建打开节点
        for (int i = 0; i < indent; i++)
            sb.append(' ');
        sb.append('<');
        sb.append(node.getName());
        Iterator names = node.findAttributes();
        while (names.hasNext()) {
            sb.append(' ');
            String name = (String) names.next();
            sb.append(name);
            sb.append("=\"");
            String value = node.findAttribute(name);
            sb.append(value);
            sb.append("\"");
        }
        sb.append(">\n");

        // 重建这个节点的正文
        String body = node.getBody();
        if ((body != null) && (body.length() > 0)) {
            for (int i = 0; i < indent2; i++)
                sb.append(' ');
            sb.append(body);
            sb.append("\n");
        }

        // 用额外的缩进重建子节点
        Iterator children = node.findChildren();
        while (children.hasNext()) {
            TreeNode child = (TreeNode) children.next();
            toString(sb, indent2, child);
        }

        // 重建一个关闭节点标记
        for (int i = 0; i < indent; i++)
            sb.append(' ');
        sb.append("</");
        sb.append(node.getName());
        sb.append(">\n");
    }
}
