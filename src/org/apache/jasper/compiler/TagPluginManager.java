package org.apache.jasper.compiler;

import java.util.*;
import java.io.*;
import javax.servlet.ServletContext;

import org.apache.jasper.JasperException;
import org.apache.jasper.xmlparser.ParserUtils;
import org.apache.jasper.xmlparser.TreeNode;
import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;

/**
 * 管理标签插件优化.
 */
public class TagPluginManager {

    private static final String TAG_PLUGINS_XML = "/WEB-INF/tagPlugins.xml";
    private static final String TAG_PLUGINS_ROOT_ELEM = "tag-plugins";

    private boolean initialized = false;
    private HashMap tagPlugins = null;
    private ServletContext ctxt;
    private PageInfo pageInfo;

    public TagPluginManager(ServletContext ctxt) {
    	this.ctxt = ctxt;
    }

    public void apply(Node.Nodes page, ErrorDispatcher err, PageInfo pageInfo)
	    throws JasperException {

		init(err);
		if (tagPlugins == null || tagPlugins.size() == 0) {
		    return;
		}
	
		this.pageInfo = pageInfo;
	        page.visit(new Node.Visitor() {
	            public void visit(Node.CustomTag n) throws JasperException {
	                invokePlugin(n);
	                visitBody(n);
	            }
        });
    }
 
    private void init(ErrorDispatcher err) throws JasperException {
		if (initialized)
		    return;
	
		InputStream is = ctxt.getResourceAsStream(TAG_PLUGINS_XML);
		if (is == null)
		    return;
	
		TreeNode root = (new ParserUtils()).parseXMLDocument(TAG_PLUGINS_XML, is);
		if (root == null) {
		    return;
		}
	
		if (!TAG_PLUGINS_ROOT_ELEM.equals(root.getName())) {
		    err.jspError("jsp.error.plugin.wrongRootElement", TAG_PLUGINS_XML,
				 TAG_PLUGINS_ROOT_ELEM);
		}
	
		tagPlugins = new HashMap();
		Iterator pluginList = root.findChildren("tag-plugin");
		while (pluginList.hasNext()) {
		    TreeNode pluginNode = (TreeNode) pluginList.next();
            TreeNode tagClassNode = pluginNode.findChild("tag-class");
		    if (tagClassNode == null) {
				// Error
				return;
		    }
		    String tagClass = tagClassNode.getBody().trim();
		    TreeNode pluginClassNode = pluginNode.findChild("plugin-class");
		    if (pluginClassNode == null) {
				// Error
				return;
		    }
	
		    String pluginClassStr = pluginClassNode.getBody();
		    TagPlugin tagPlugin = null;
		    try {
				Class pluginClass = Class.forName(pluginClassStr);
				tagPlugin = (TagPlugin) pluginClass.newInstance();
		    } catch (Exception e) {
		    	throw new JasperException(e);
		    }
		    if (tagPlugin == null) {
		    	return;
		    }
		    tagPlugins.put(tagClass, tagPlugin);
		}
		initialized = true;
    }

    /**
     * 为给定的自定义标签调用标签插件, 如果自定义标签的标签处理程序存在一个插件.
     *
     * 给定的自定义标签节点将由插件操作.
     */
    private void invokePlugin(Node.CustomTag n) {
		TagPlugin tagPlugin = (TagPlugin) tagPlugins.get(n.getTagHandlerClass().getName());
		if (tagPlugin == null) {
		    return;
		}
	
		TagPluginContext tagPluginContext = new TagPluginContextImpl(n, pageInfo);
		n.setTagPluginContext(tagPluginContext);
		tagPlugin.doTag(tagPluginContext);
    }

    static class TagPluginContextImpl implements TagPluginContext {
		private Node.CustomTag node;
		private Node.Nodes curNodes;
		private PageInfo pageInfo;
		private HashMap pluginAttributes;
	
		TagPluginContextImpl(Node.CustomTag n, PageInfo pageInfo) {
		    this.node = n;
		    this.pageInfo = pageInfo;
		    curNodes = new Node.Nodes();
		    n.setAtETag(curNodes);
		    curNodes = new Node.Nodes();
		    n.setAtSTag(curNodes);
		    n.setUseTagPlugin(true);
		    pluginAttributes = new HashMap();
		}
	
		public TagPluginContext getParentContext() {
		    Node parent = node.getParent();
		    if (! (parent instanceof Node.CustomTag)) {
		    	return null;
		    }
		    return ((Node.CustomTag) parent).getTagPluginContext();
		}
	
		public void setPluginAttribute(String key, Object value) {
		    pluginAttributes.put(key, value);
		}
	
		public Object getPluginAttribute(String key) {
		    return pluginAttributes.get(key);
		}
	
		public boolean isScriptless() {
		    return node.getChildInfo().isScriptless();
		}
	
		public boolean isConstantAttribute(String attribute) {
		    Node.JspAttribute attr = getNodeAttribute(attribute);
		    if (attr == null)
		    	return false;
		    return attr.isLiteral();
		}
	
		public String getConstantAttribute(String attribute) {
		    Node.JspAttribute attr = getNodeAttribute(attribute);
            if (attr == null)
            	return null;
		    return attr.getValue();
		}
	
		public boolean isAttributeSpecified(String attribute) {
		    return getNodeAttribute(attribute) != null;
		}
	
		public String getTemporaryVariableName() {
		    return JspUtil.nextTemporaryVariableName();
		}
	
		public void generateImport(String imp) {
		    pageInfo.addImport(imp);
		}
	
		public void generateDeclaration(String id, String text) {
		    if (pageInfo.isPluginDeclared(id)) {
		    	return;
		    }
		    curNodes.add(new Node.Declaration(text, node.getStart(), null));
		}
	
		public void generateJavaSource(String sourceCode) {
		    curNodes.add(new Node.Scriptlet(sourceCode, node.getStart(), null));
		}
	
		public void generateAttribute(String attributeName) {
		    curNodes.add(new Node.AttributeGenerator(node.getStart(),
							     attributeName, node));
		}
	
		public void dontUseTagPlugin() {
		    node.setUseTagPlugin(false);
		}
	
		public void generateBody() {
		    // 既然我们会生成主体, 这真是一个nop, 
		    // 除了这个事实, 它让插件按正确的顺序生成Java源码(w.r.t the body).
		    curNodes = node.getAtETag();
		}
	
		private Node.JspAttribute getNodeAttribute(String attribute) {
		    Node.JspAttribute[] attrs = node.getJspAttributes();
		    for (int i=0; attrs != null && i < attrs.length; i++) {
				if (attrs[i].getName().equals(attribute)) {
				    return attrs[i];
				}
		    }
		    return null;
		}
    }
}
