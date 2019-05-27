package org.apache.jasper.compiler;

import java.util.*;
import javax.servlet.jsp.tagext.FunctionInfo;
import org.apache.jasper.JasperException;

/**
 * 生成页面中的EL表达式生成函数映射.
 * 而不是全局映射器, 映射用于每个调用到EL计算, 从而避免前缀重叠和重新定义问题.
 */
public class ELFunctionMapper {
    static private int currFunc = 0;
    private ErrorDispatcher err;
    StringBuffer ds;  // 包含初始化函数映射代码.
    StringBuffer ss;  // 包含的功能映射声明.

    /**
     * 创建在JSP页面中所有的EL表达式的函数映射.
     *
     * @param compiler 当前编译器, 主要用于访问错误调度器.
     * @param page 当前编译单元.
     */
    public static void map(Compiler compiler, Node.Nodes page) throws JasperException {

		currFunc = 0;
		ELFunctionMapper map = new ELFunctionMapper();
		map.err = compiler.getErrorDispatcher();
		map.ds = new StringBuffer();
		map.ss = new StringBuffer();
	
		page.visit(map.new ELFunctionVisitor());
	
		// 将声明追加到根节点
		String ds = map.ds.toString();
		if (ds.length() > 0) {
		    Node root = page.getRoot();
		    new Node.Declaration(map.ss.toString(), null, root);
		    new Node.Declaration("static {\n" + ds + "}\n", null, root);
		}
    }

    /**
     * 页面访问者. 允许EL扫描功能的地方, 如果发现功能映射被创建.
     */
    class ELFunctionVisitor extends Node.Visitor {
	
		/**
		 * 使用全局名称映射来重用函数映射.
		 * The key used is prefix:function:uri.
		 */
		private HashMap gMap = new HashMap();
	
		public void visit(Node.ParamAction n) throws JasperException {
		    doMap(n.getValue());
		    visitBody(n);
		}
	
		public void visit(Node.IncludeAction n) throws JasperException {
		    doMap(n.getPage());
		    visitBody(n);
		}
	
		public void visit(Node.ForwardAction n) throws JasperException {
		    doMap(n.getPage());
		    visitBody(n);
		}
	
        public void visit(Node.SetProperty n) throws JasperException {
		    doMap(n.getValue());
		    visitBody(n);
		}
	
        public void visit(Node.UseBean n) throws JasperException {
		    doMap(n.getBeanName());
		    visitBody(n);
		}
	
        public void visit(Node.PlugIn n) throws JasperException {
		    doMap(n.getHeight());
		    doMap(n.getWidth());
		    visitBody(n);
		}
	
        public void visit(Node.JspElement n) throws JasperException {
	
		    Node.JspAttribute[] attrs = n.getJspAttributes();
		    for (int i = 0; attrs != null && i < attrs.length; i++) {
		    	doMap(attrs[i]);
		    }
		    doMap(n.getNameAttribute());
		    visitBody(n);
		}
	
        public void visit(Node.UninterpretedTag n) throws JasperException {
	
		    Node.JspAttribute[] attrs = n.getJspAttributes();
		    for (int i = 0; attrs != null && i < attrs.length; i++) {
		    	doMap(attrs[i]);
		    }
		    visitBody(n);
		}
	
        public void visit(Node.CustomTag n) throws JasperException {
		    Node.JspAttribute[] attrs = n.getJspAttributes();
		    for (int i = 0; attrs != null && i < attrs.length; i++) {
		    	doMap(attrs[i]);
		    }
		    visitBody(n);
		}
	
        public void visit(Node.ELExpression n) throws JasperException {
		    doMap(n.getEL());
		}
	
		private void doMap(Node.JspAttribute attr) 
			throws JasperException {
		    if (attr != null) {
		    	doMap(attr.getEL());
		    }
		}
	
        /**
         * 创建函数映射, from ELNodes
         */
		private void doMap(ELNode.Nodes el) throws JasperException {
	
            // 只关心 ELNode的函数
		    class Fvisitor extends ELNode.Visitor {
				ArrayList funcs = new ArrayList();
				HashMap keyMap = new HashMap();
				public void visit(ELNode.Function n) throws JasperException {
				    String key = n.getPrefix() + ":" + n.getName();
				    if (! keyMap.containsKey(key)) {
						keyMap.put(key,"");
						funcs.add(n);
				    }
				}
		    }
	
		    if (el == null) {
		    	return;
		    }
	
		    //首先定位这个EL中的所有唯一函数
		    Fvisitor fv = new Fvisitor();
		    el.visit(fv);
		    ArrayList functions = fv.funcs;
	
		    if (functions.size() == 0) {
		    	return;
		    }
	
		    // 如果可能的话重用以前的map
		    String decName = matchMap(functions);
		    if (decName != null) {
				el.setMapName(decName);
				return;
		    }
		
		    // 静态生成映射声明
		    decName = getMapName();
		    ss.append("static private org.apache.jasper.runtime.ProtectedFunctionMapper " + decName + ";\n");
	
		    ds.append("  " + decName + "= ");
		    ds.append("org.apache.jasper.runtime.ProtectedFunctionMapper");
	
		    // 特殊情况，如果在 map中只有一个函数
		    String funcMethod = null;
		    if (functions.size() == 1) {
		    	funcMethod = ".getMapForFunction";
		    } else {
				ds.append(".getInstance();\n");
				funcMethod = "  " + decName + ".mapFunction";
		    }
	
            // Setup arguments for either getMapForFunction or mapFunction
		    for (int i = 0; i < functions.size(); i++) {
				ELNode.Function f = (ELNode.Function)functions.get(i);
				FunctionInfo funcInfo = f.getFunctionInfo();
				String key = f.getPrefix()+ ":" + f.getName();
				ds.append(funcMethod + "(\"" + key + "\", " +
					funcInfo.getFunctionClass() + ".class, " +
					'\"' + f.getMethodName() + "\", " +
					"new Class[] {");
				String params[] = f.getParameters();
				for (int k = 0; k < params.length; k++) {
				    if (k != 0) {
					ds.append(", ");
				    }
				    int iArray = params[k].indexOf('[');
				    if (iArray < 0) {
					ds.append(params[k] + ".class");
				    }
				    else {
					String baseType = params[k].substring(0, iArray);
					ds.append("java.lang.reflect.Array.newInstance(");
					ds.append(baseType);
					ds.append(".class,");
		
					// 计算数组维数
					int aCount = 0;
					for (int jj = iArray; jj < params[k].length(); jj++ ) {
					    if (params[k].charAt(jj) == '[') {
						aCount++;
					    }
					}
					if (aCount == 1) {
					    ds.append("0).getClass()");
					} else {
					    ds.append("new int[" + aCount + "]).getClass()");
					}
				    }
				}
				ds.append("});\n");
				// 将当前名称放入全局函数映射中
				gMap.put(f.getPrefix() + ':' + f.getName() + ':' + f.getUri(), decName);
		    }
		    el.setMapName(decName);
		}
	
        /**
         * 为EL找到函数映射器的名称.  如果可能的话，重用以前生成的.
         * @param functions ELNode.Function实例集合, 表示一个EL中的功能
         * @return 一个以前生成的可以由EL使用的函数映射器名称; 或者null
         */
		private String matchMap(ArrayList functions) {
	
		    String mapName = null;
		    for (int i = 0; i < functions.size(); i++) {
				ELNode.Function f = (ELNode.Function)functions.get(i);
				String temName = (String) gMap.get(f.getPrefix() + ':' +
							f.getName() + ':' + f.getUri());
				if (temName == null) {
				    return null;
				}
				if (mapName == null) {
				    mapName = temName;
				} else if (!temName.equals(mapName)) {
				    // 如果之前的匹配不是全部, 然后不匹配.
				    return null;
				}
		    }
		    return mapName;
		}
	
        /*
         * @return 函数映射器的唯一名称.
         */
		private String getMapName() {
		    return "_jspx_fnmap_" + currFunc++;
		}
    }
}

