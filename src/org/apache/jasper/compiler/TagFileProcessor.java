package org.apache.jasper.compiler;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.HashMap;

import javax.servlet.jsp.tagext.TagAttributeInfo;
import javax.servlet.jsp.tagext.TagExtraInfo;
import javax.servlet.jsp.tagext.TagFileInfo;
import javax.servlet.jsp.tagext.TagInfo;
import javax.servlet.jsp.tagext.TagLibraryInfo;
import javax.servlet.jsp.tagext.TagVariableInfo;
import javax.servlet.jsp.tagext.VariableInfo;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.servlet.JspServletWrapper;
import org.apache.jasper.runtime.JspSourceDependent;

/**
 * 1. 处理并提取标签文件中的指令信息.
 * 2. 编译并加载JSP文件中使用的标签文件.
 */
class TagFileProcessor {

    private Vector tempVector;

    /**
     * 访问标签文件
     */
    private static class TagFileDirectiveVisitor extends Node.Visitor {

        private static final JspUtil.ValidAttribute[] tagDirectiveAttrs = {
            new JspUtil.ValidAttribute("display-name"),
            new JspUtil.ValidAttribute("body-content"),
            new JspUtil.ValidAttribute("dynamic-attributes"),
            new JspUtil.ValidAttribute("small-icon"),
            new JspUtil.ValidAttribute("large-icon"),
            new JspUtil.ValidAttribute("description"),
            new JspUtil.ValidAttribute("example"),
            new JspUtil.ValidAttribute("pageEncoding"),
            new JspUtil.ValidAttribute("language"),
            new JspUtil.ValidAttribute("import"),
            new JspUtil.ValidAttribute("isELIgnored") };

        private static final JspUtil.ValidAttribute[] attributeDirectiveAttrs = {
            new JspUtil.ValidAttribute("name", true),
            new JspUtil.ValidAttribute("required"),
            new JspUtil.ValidAttribute("fragment"),
            new JspUtil.ValidAttribute("rtexprvalue"),
            new JspUtil.ValidAttribute("type"),
            new JspUtil.ValidAttribute("description")
        };

        private static final JspUtil.ValidAttribute[] variableDirectiveAttrs = {
            new JspUtil.ValidAttribute("name-given"),
            new JspUtil.ValidAttribute("name-from-attribute"),
            new JspUtil.ValidAttribute("alias"),
            new JspUtil.ValidAttribute("variable-class"),
            new JspUtil.ValidAttribute("scope"),
            new JspUtil.ValidAttribute("declare"),
            new JspUtil.ValidAttribute("description")
        };

        private ErrorDispatcher err;
        private TagLibraryInfo tagLibInfo;

        private String name = null;
        private String path = null;
        private TagExtraInfo tei = null;
        private String bodycontent = null;
        private String description = null;
        private String displayName = null;
        private String smallIcon = null;
        private String largeIcon = null;
        private String dynamicAttrsMapName;
        private String example = null;
        
        private Vector attributeVector;
        private Vector variableVector;

        private static final String ATTR_NAME =
            "the name attribute of the attribute directive";
        private static final String VAR_NAME_GIVEN =
            "the name-given attribute of the variable directive";
        private static final String VAR_NAME_FROM =
            "the name-from-attribute attribute of the variable directive";
        private static final String VAR_ALIAS =
            "the alias attribute of the variable directive";
        private static final String TAG_DYNAMIC =
            "the dynamic-attributes attribute of the tag directive";
        private HashMap nameTable = new HashMap();
        private HashMap nameFromTable = new HashMap();

        public TagFileDirectiveVisitor(Compiler compiler,
                                       TagLibraryInfo tagLibInfo,
                                       String name,
                                       String path) {
            err = compiler.getErrorDispatcher();
            this.tagLibInfo = tagLibInfo;
            this.name = name;
            this.path = path;
            attributeVector = new Vector();
            variableVector = new Vector();
        }

        public void visit(Node.TagDirective n) throws JasperException {

            JspUtil.checkAttributes("Tag directive", n, tagDirectiveAttrs,
                                    err);

            bodycontent = checkConflict(n, bodycontent, "body-content");
            if (bodycontent != null &&
                    !bodycontent.equalsIgnoreCase(TagInfo.BODY_CONTENT_EMPTY) &&
                    !bodycontent.equalsIgnoreCase(TagInfo.BODY_CONTENT_TAG_DEPENDENT) &&
                    !bodycontent.equalsIgnoreCase(TagInfo.BODY_CONTENT_SCRIPTLESS)) {
                err.jspError(n, "jsp.error.tagdirective.badbodycontent",
                             bodycontent);
            }
            dynamicAttrsMapName = checkConflict(n, dynamicAttrsMapName,
                                                "dynamic-attributes");
            if (dynamicAttrsMapName != null) {
                checkUniqueName(dynamicAttrsMapName, TAG_DYNAMIC, n);
            }
            smallIcon = checkConflict(n, smallIcon, "small-icon");
            largeIcon = checkConflict(n, largeIcon, "large-icon");
            description = checkConflict(n, description, "description");
            displayName = checkConflict(n, displayName, "display-name");
            example = checkConflict(n, example, "example");
        }

        private String checkConflict(Node n, String oldAttrValue, String attr)
                throws JasperException {

            String result = oldAttrValue;
            String attrValue = n.getAttributeValue(attr);
            if (attrValue != null) {
                if (oldAttrValue != null && !oldAttrValue.equals(attrValue)) {
                    err.jspError(n, "jsp.error.tag.conflict.attr", attr,
                                 oldAttrValue, attrValue);
                }
                result = attrValue;
            }
            return result;
        }
            

        public void visit(Node.AttributeDirective n) throws JasperException {

            JspUtil.checkAttributes("Attribute directive", n,
                                    attributeDirectiveAttrs, err);

            String attrName = n.getAttributeValue("name");
            boolean required = JspUtil.booleanValue(
                                        n.getAttributeValue("required"));
            boolean rtexprvalue = true;
            String rtexprvalueString = n.getAttributeValue("rtexprvalue");
            if (rtexprvalueString != null) {
                rtexprvalue = JspUtil.booleanValue( rtexprvalueString );
            }
            boolean fragment = JspUtil.booleanValue(
                                        n.getAttributeValue("fragment"));
            String type = n.getAttributeValue("type");
            if (fragment) {
                // 类型固定于"JspFragment", 如果指定，则必须发生翻译错误.
                if (type != null) {
                    err.jspError(n, "jsp.error.fragmentwithtype");
                }
                // rtexprvalue 固定于"true", 如果指定，则必须发生翻译错误.
                rtexprvalue = true;
                if( rtexprvalueString != null ) {
                    err.jspError(n, "jsp.error.frgmentwithrtexprvalue" );
                }
            } else {
                if (type == null)
                    type = "java.lang.String";
            }

            TagAttributeInfo tagAttributeInfo =
                    new TagAttributeInfo(attrName, required, type, rtexprvalue,
                                         fragment);
            attributeVector.addElement(tagAttributeInfo);
            checkUniqueName(attrName, ATTR_NAME, n, tagAttributeInfo);
        }

        public void visit(Node.VariableDirective n) throws JasperException {

            JspUtil.checkAttributes("Variable directive", n,
                                    variableDirectiveAttrs, err);

            String nameGiven = n.getAttributeValue("name-given");
            String nameFromAttribute = n.getAttributeValue("name-from-attribute");
            if (nameGiven == null && nameFromAttribute == null) {
                err.jspError("jsp.error.variable.either.name");
            }

            if (nameGiven != null && nameFromAttribute != null) {
                err.jspError("jsp.error.variable.both.name");
            }

            String alias = n.getAttributeValue("alias");
            if (nameFromAttribute != null && alias == null ||
                nameFromAttribute == null && alias != null) {
                err.jspError("jsp.error.variable.alias");
            }

            String className = n.getAttributeValue("variable-class");
            if (className == null)
                className = "java.lang.String";

            String declareStr = n.getAttributeValue("declare");
            boolean declare = true;
            if (declareStr != null)
                declare = JspUtil.booleanValue(declareStr);

            int scope = VariableInfo.NESTED;
            String scopeStr = n.getAttributeValue("scope");
            if (scopeStr != null) {
                if ("NESTED".equals(scopeStr)) {
                    // Already the default
                } else if ("AT_BEGIN".equals(scopeStr)) {
                    scope = VariableInfo.AT_BEGIN;
                } else if ("AT_END".equals(scopeStr)) {
                    scope = VariableInfo.AT_END;
                }
            }

            if (nameFromAttribute != null) {
                /*
				 * 已经指定别名. 使用'nameGiven'保存别名的值, 使用'nameFromAttribute'保存属性的值, 有别名的属性的值表示变量的名称(在调用时间)
				 */
                nameGiven = alias;
                checkUniqueName(nameFromAttribute, VAR_NAME_FROM, n);
                checkUniqueName(alias, VAR_ALIAS, n);
            }
            else {
                // 指定的名称
                checkUniqueName(nameGiven, VAR_NAME_GIVEN, n);
            }
                
            variableVector.addElement(new TagVariableInfo(
                                                nameGiven,
                                                nameFromAttribute,
                                                className,
                                                declare,
                                                scope));
        }

        /*
         * 返回一组对应于属性指令的属性.
         */
        public Vector getAttributesVector() {
            return attributeVector;
        }

        /*
         * 返回一组对应于变量指令的变量.
         */        
        public Vector getVariablesVector() {
            return variableVector;
        }

		/*
		 * 返回动态属性标签指令属性的值.
		 */
		public String getDynamicAttributesMapName() {
		    return dynamicAttrsMapName;
		}

        public TagInfo getTagInfo() throws JasperException {

            if (name == null) {
                // XXX Get it from tag file name
            }

            if (bodycontent == null) {
                bodycontent = TagInfo.BODY_CONTENT_SCRIPTLESS;
            }

            String tagClassName = JspUtil.getTagHandlerClassName(path, err);

            TagVariableInfo[] tagVariableInfos
                = new TagVariableInfo[variableVector.size()];
            variableVector.copyInto(tagVariableInfos);

            TagAttributeInfo[] tagAttributeInfo
                = new TagAttributeInfo[attributeVector.size()];
            attributeVector.copyInto(tagAttributeInfo);

            return new JasperTagInfo(name,
			       tagClassName,
			       bodycontent,
			       description,
			       tagLibInfo,
			       tei,
			       tagAttributeInfo,
			       displayName,
			       smallIcon,
			       largeIcon,
			       tagVariableInfos,
			       dynamicAttrsMapName);
        }

        static class NameEntry {
            private String type;
            private Node node;
            private TagAttributeInfo attr;

            NameEntry(String type, Node node, TagAttributeInfo attr) {
                this.type = type;
                this.node = node;
                this.attr = attr;
            }

            String getType() { return type;}
            Node getNode() { return node; }
            TagAttributeInfo getTagAttributeInfo() { return attr; }
        }

        /**
         * 如果指令属性中指定的名称在这个翻译单元中不是唯一的，则报告翻译错误.
         *
         * 下列属性的值必须是唯一的.
         *   1. 属性指令的'name'属性
         *   2. 属性指令的'name-given'属性
         *   3. 变量指令的'alias'属性
         *   4. 标签指令的'dynamic-attributes'
         * 除了'dynamic-attributes' 必须有相同的值, 当它出现在多个标记指令中时.
         *
         * 同样, 变量指令的'name-from'属性不能具有与另一个变量指令相同的值.
         */
        private void checkUniqueName(String name, String type, Node n)
                throws JasperException {
            checkUniqueName(name, type, n, null);
        }

        private void checkUniqueName(String name, String type, Node n,
                                     TagAttributeInfo attr)
                throws JasperException {

            HashMap table = (type == VAR_NAME_FROM)? nameFromTable: nameTable;
            NameEntry nameEntry = (NameEntry) table.get(name);
            if (nameEntry != null) {
                if (type != TAG_DYNAMIC || nameEntry.getType() != TAG_DYNAMIC) {
                    int line = nameEntry.getNode().getStart().getLineNumber();
                    err.jspError(n, "jsp.error.tagfile.nameNotUnique",
                         type, nameEntry.getType(), Integer.toString(line));
                }
            } else {
                table.put(name, new NameEntry(type, n, attr));
            }
        }

        /**
         * 在节点的访问之后进行miscellean检查.
         */
        void postCheck() throws JasperException {
            // 检查 var.name-from-attributes 具有有效值.
	    Iterator iter = nameFromTable.keySet().iterator();
            while (iter.hasNext()) {
                String nameFrom = (String) iter.next();
                NameEntry nameEntry = (NameEntry) nameTable.get(nameFrom);
                NameEntry nameFromEntry =
                    (NameEntry) nameFromTable.get(nameFrom);
                Node nameFromNode = nameFromEntry.getNode();
                if (nameEntry == null) {
                    err.jspError(nameFromNode,
                                 "jsp.error.tagfile.nameFrom.noAttribute",
                                 nameFrom);
                } else {
                    Node node = nameEntry.getNode();
                    TagAttributeInfo tagAttr = nameEntry.getTagAttributeInfo();
                    if (! "java.lang.String".equals(tagAttr.getTypeName())
                            || ! tagAttr.isRequired()
                            || tagAttr.canBeRequestTime()){
                        err.jspError(nameFromNode,
                            "jsp.error.tagfile.nameFrom.badAttribute",
                            nameFrom,
                            Integer.toString(node.getStart().getLineNumber()));
                     }
                }
            }
        }
    }

    /**
     * 解析标签文件, 并收集其中包含的指令的信息. 该方法用于获取标签文件上的信息, 当它表示的处理程序被引用时.
     * 标签文件不在这里编译.
     *
     * @param pc 编译中使用的当前ParserController
     * @param name 在TLD指定的标签名称
     * @param tagfile 标签文件的路径
     * @param tagLibInfo 这个TagInfo关联的TagLibraryInfo 对象
     * @return 从标签文件中的指令中组合的 TagInfo对象.
     */
    public static TagInfo parseTagFileDirectives(ParserController pc,
						 String name,
						 String path,
						 TagLibraryInfo tagLibInfo)
                        throws JasperException {

        ErrorDispatcher err = pc.getCompiler().getErrorDispatcher();

        Node.Nodes page = null;
        try {
            page = pc.parseTagFileDirectives(path);
        } catch (FileNotFoundException e) {
            err.jspError("jsp.error.file.not.found", path);
        } catch (IOException e) {
            err.jspError("jsp.error.file.not.found", path);
        }

        TagFileDirectiveVisitor tagFileVisitor
            = new TagFileDirectiveVisitor(pc.getCompiler(), tagLibInfo, name,
                                          path);
        page.visit(tagFileVisitor);
        tagFileVisitor.postCheck();

        return tagFileVisitor.getTagInfo();
    }

    /**
     * 编译并加载一个标签文件.
     */
    private Class loadTagFile(Compiler compiler,
                              String tagFilePath, TagInfo tagInfo,
                              PageInfo parentPageInfo)
        throws JasperException {

        JspCompilationContext ctxt = compiler.getCompilationContext();
        JspRuntimeContext rctxt = ctxt.getRuntimeContext();
        JspServletWrapper wrapper =
                (JspServletWrapper) rctxt.getWrapper(tagFilePath);

        synchronized(rctxt) {
            if (wrapper == null) {
                wrapper = new JspServletWrapper(ctxt.getServletContext(),
                                                ctxt.getOptions(),
                                                tagFilePath,
                                                tagInfo,
                                                ctxt.getRuntimeContext(),
                                                (URL) ctxt.getTagFileJarUrls().get(tagFilePath));
                    rctxt.addWrapper(tagFilePath,wrapper);

		// 使用相同的classloader 和 classpath 编译标签文件
		wrapper.getJspEngineContext().setClassLoader(
				(URLClassLoader) ctxt.getClassLoader());
		wrapper.getJspEngineContext().setClassPath(ctxt.getClassPath());
            }
            else {
                // 确保 JspCompilationContext 获取最新的TagInfo为标签文件. 上一次标签文件扫描时创建TagInfo实例, 并且标签文件从那时起可能已经被修改.
                wrapper.getJspEngineContext().setTagInfo(tagInfo);
            }

            Class tagClazz;
            int tripCount = wrapper.incTripCount();
            try {
                if (tripCount > 0) {
                    // 当tripCount比零大, 存在循环依赖项. circularily依赖标签文件以原型模式编译, 避免无限递归.
                    JspServletWrapper tempWrapper
                        = new JspServletWrapper(ctxt.getServletContext(),
                                                ctxt.getOptions(),
                                                tagFilePath,
                                                tagInfo,
                                                ctxt.getRuntimeContext(),
                                                (URL) ctxt.getTagFileJarUrls().get(tagFilePath));
                    tagClazz = tempWrapper.loadTagFilePrototype();
                    tempVector.add(
                               tempWrapper.getJspEngineContext().getCompiler());
                } else {
                    tagClazz = wrapper.loadTagFile();
                }
            } finally {
                wrapper.decTripCount();
            }
        
            // 添加这个标签文件的依赖到它的父级的依赖列表. 只能从标签实例获得唯一可靠的依赖性信息.
            try {
                Object tagIns = tagClazz.newInstance();
                if (tagIns instanceof JspSourceDependent) {
                    Iterator iter = 
                        ((List) ((JspSourceDependent) tagIns).getDependants()).iterator();
                    while (iter.hasNext()) {
                        parentPageInfo.addDependant((String)iter.next());
                    }
                }
            } catch (Exception e) {
                // ignore errors
            }
        
            return tagClazz;
        }
    }


    /*
     * 访问者，它扫描页面并查找标签文件的标记处理程序, 编译并加载它们.
     */ 
    private class TagFileLoaderVisitor extends Node.Visitor {

        private Compiler compiler;
        private PageInfo pageInfo;

        TagFileLoaderVisitor(Compiler compiler) {
            
            this.compiler = compiler;
            this.pageInfo = compiler.getPageInfo();
        }

        public void visit(Node.CustomTag n) throws JasperException {
            TagFileInfo tagFileInfo = n.getTagFileInfo();
            if (tagFileInfo != null) {
                String tagFilePath = tagFileInfo.getPath();
				JspCompilationContext ctxt = compiler.getCompilationContext();
				if (ctxt.getTagFileJarUrls().get(tagFilePath) == null) {
				    // 现在省略JAR文件上的标签文件依赖信息.
		            pageInfo.addDependant(tagFilePath);
				}
                Class c = loadTagFile(compiler, tagFilePath, n.getTagInfo(),
                                      pageInfo);
                n.setTagHandlerClass(c);
            }
            visitBody(n);
        }
    }

    /**
     * 实现编译JSP文件中使用的标签文件的转换阶段. 标签文件中的指令假定已经被处理, 并封装为CustomTag节点中的TagFileInfo.
     */
    public void loadTagFiles(Compiler compiler, Node.Nodes page)
                throws JasperException {

        tempVector = new Vector();
        page.visit(new TagFileLoaderVisitor(compiler));
    }

    /**
     * 从当前编译生成的标签原型删除java文件和类文件.
     * 
     * @param classFileName 如果是non-null, 仅删除指定名称的类文件.
     */
    public void removeProtoTypeFiles(String classFileName) {
        Iterator iter = tempVector.iterator();
        while (iter.hasNext()) {
            Compiler c = (Compiler) iter.next();
            if (classFileName == null) {
                c.removeGeneratedClassFiles();
            } else if (classFileName.equals(
                        c.getCompilationContext().getClassFileName())) {
                c.removeGeneratedClassFiles();
                tempVector.remove(c);
                return;
            }
        }
    }
}

