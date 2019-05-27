package org.apache.jasper.compiler;

import java.util.*;
import javax.servlet.jsp.tagext.FunctionInfo;
import org.apache.jasper.JasperException;

/**
 * 这个类定义EL表达式的内部表示
 *
 * 它目前只定义函数. 可以扩展它来定义EL表达式的所有组件, 如果需要.
 */
abstract class ELNode {

    abstract public void accept(Visitor v) throws JasperException;

    /**
     * 表示EL表达式: ${ 和 }中的所有东西.
     */
    public static class Root extends ELNode {

		private ELNode.Nodes expr;
	
		Root(ELNode.Nodes expr) {
		    this.expr = expr;
		}
	
		public void accept(Visitor v) throws JasperException {
		    v.visit(this);
		}
	
		public ELNode.Nodes getExpression() {
		    return expr;
		}
    }

    /**
     * 表示EL表达式之外的文本.
     */
    public static class Text extends ELNode {

		private String text;
	
		Text(String text) {
		    this.text = text;
		}
	
		public void accept(Visitor v) throws JasperException {
		    v.visit(this);
		}
	
		public String getText() {
		    return text;
		}
    }

    /**
     * 表示EL表达式中的任何内容, 其他功能, 包括函数参数等
     */
    public static class ELText extends ELNode {

		private String text;
	
		ELText(String text) {
		    this.text = text;
		}
	
		public void accept(Visitor v) throws JasperException {
		    v.visit(this);
		}
	
		public String getText() {
		    return text;
		}
    }

    /**
     * 代表一个函数
     * 目前只包含前缀和函数名, 但不是它的参数.
     */
    public static class Function extends ELNode {
		private String prefix;
		private String name;
		private String uri;
		private FunctionInfo functionInfo;
		private String methodName;
		private String[] parameters;
	
		Function(String prefix, String name) {
		    this.prefix = prefix;
		    this.name = name;
		}
	
		public void accept(Visitor v) throws JasperException {
		    v.visit(this);
		}
	
		public String getPrefix() {
		    return prefix;
		}
	
		public String getName() {
		    return name;
		}
	
		public void setUri(String uri) {
		    this.uri = uri;
		}
	
		public String getUri() {
		    return uri;
		}
	
		public void setFunctionInfo(FunctionInfo f) {
		    this.functionInfo = f;
		}
	
		public FunctionInfo getFunctionInfo() {
		    return functionInfo;
		}
	
		public void setMethodName(String methodName) {
		    this.methodName = methodName;
		}
	
		public String getMethodName() {
		    return methodName;
		}
	
		public void setParameters(String[] parameters) {
		    this.parameters = parameters;
		}
	
		public String[] getParameters() {
		    return parameters;
		}
    }

    /**
     * ELNode的有序列表.
     */
    public static class Nodes {

		/* 用于为EL表达式中的函数创建映射的名称, 和生成器交互.
		 */
		String mapName = null;	// 与EL相关联的功能map
		private List list;
	
		public Nodes() {
		    list = new ArrayList();
		}
	
		public void add(ELNode en) {
		    list.add(en);
		}
	
		/**
		 * 使用提供的访问者访问列表中的节点
		 * @param v 使用的访问者
		 */
		public void visit(Visitor v) throws JasperException {
		    Iterator iter = list.iterator();
		    while (iter.hasNext()) {
			ELNode n = (ELNode) iter.next();
			n.accept(v);
		    }
		}
	
		public Iterator iterator() {
		    return list.iterator();
		}
	
		public boolean isEmpty() {
		    return list.size() == 0;
		}
	
		/**
		 * @return true 如果表达式包含一个 ${...}
		 */
		public boolean containsEL() {
		    Iterator iter = list.iterator();
		    while (iter.hasNext()) {
				ELNode n = (ELNode) iter.next();
				if (n instanceof Root) {
				    return true;
				}
		    }
		    return false;
		}
	
		public void setMapName(String name) {
		    this.mapName = name;
		}
	
		public String getMapName() {
		    return mapName;
		}
    }

    /*
     * 用于遍历ELNode
     */
    public static class Visitor {

		public void visit(Root n) throws JasperException {
		    n.getExpression().visit(this);
		}
	
		public void visit(Function n) throws JasperException {
		}
	
		public void visit(Text n) throws JasperException {
		}
	
		public void visit(ELText n) throws JasperException {
		}
    }
}
