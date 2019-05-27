package org.apache.jasper.compiler;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import javax.servlet.jsp.tagext.TagAttributeInfo;
import javax.servlet.jsp.tagext.TagInfo;
import javax.servlet.jsp.tagext.TagVariableInfo;
import javax.servlet.jsp.tagext.VariableInfo;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.runtime.JspRuntimeLibrary;
import org.xml.sax.Attributes;

/**
 * 从Node生成java源
 */
class Generator {

    private static final Class[] OBJECT_CLASS = { Object.class };
    private ServletWriter out;
    private ArrayList methodsBuffered;
    private FragmentHelperClass fragmentHelperClass;
    private ErrorDispatcher err;
    private BeanRepository beanInfo;
    private JspCompilationContext ctxt;
    private boolean isPoolingEnabled;
    private boolean breakAtLF;
    private PageInfo pageInfo;
    private Vector tagHandlerPoolNames;
    private GenBuffer charArrayBuffer;

    /**
     * @param s 输入的字符串
     * @return 引号和转义字符串, 每个java规则
     */
    static String quote(String s) {
        if (s == null)
            return "null";

        return '"' + escape(s) + '"';
    }

    /**
     * @param s 输入的字符串
     * @return 转义字符串, 每个java规则
     */
    static String escape(String s) {

        if (s == null)
            return "";

        StringBuffer b = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"')
                b.append('\\').append('"');
            else if (c == '\\')
                b.append('\\').append('\\');
            else if (c == '\n')
                b.append('\\').append('n');
            else if (c == '\r')
                b.append('\\').append('r');
            else
                b.append(c);
        }
        return b.toString();
    }

    /**
     * 单引号和转义字符
     */
    static String quote(char c) {

        StringBuffer b = new StringBuffer();
        b.append('\'');
        if (c == '\'')
            b.append('\\').append('\'');
        else if (c == '\\')
            b.append('\\').append('\\');
        else if (c == '\n')
            b.append('\\').append('n');
        else if (c == '\r')
            b.append('\\').append('r');
        else
            b.append(c);
        b.append('\'');
        return b.toString();
    }

    /**
     * 生成的声明.  包含页面指令的 "info", 和脚本的声明.
     */
    private void generateDeclarations(Node.Nodes page) throws JasperException {

        class DeclarationVisitor extends Node.Visitor {

            private boolean getServletInfoGenerated = false;

            /*
             * 生成getServletInfo() 方法, 该方法返回页面指令的'info'属性的值.
             *
             * 已经确保的Validator, 如果'info'属性的翻译单元包含多个页面指令, 它们的值匹配.
             */
            public void visit(Node.PageDirective n) throws JasperException {

                if (getServletInfoGenerated) {
                    return;
                }

                String info = n.getAttributeValue("info");
                if (info == null)
                    return;

                getServletInfoGenerated = true;
                out.printil("public String getServletInfo() {");
                out.pushIndent();
                out.printin("return ");
                out.print(quote(info));
                out.println(";");
                out.popIndent();
                out.printil("}");
                out.println();
            }

            public void visit(Node.Declaration n) throws JasperException {
                n.setBeginJavaLine(out.getJavaLine());
                out.printMultiLn(new String(n.getText()));
                out.println();
                n.setEndJavaLine(out.getJavaLine());
            }

            // 自定义标签可能包含来自标签插件的声明.
            public void visit(Node.CustomTag n) throws JasperException {
                if (n.useTagPlugin()) {
                    if (n.getAtSTag() != null) {
                        n.getAtSTag().visit(this);
                    }
                    visitBody(n);
                    if (n.getAtETag() != null) {
                        n.getAtETag().visit(this);
                    }
                } else {
                    visitBody(n);
                }
            }
        }
        out.println();
        page.visit(new DeclarationVisitor());
    }

    /**
     * 编译标签处理程序池名称列表.
     */
    private void compileTagHandlerPoolList(Node.Nodes page)
        throws JasperException {

        class TagHandlerPoolVisitor extends Node.Visitor {

            private Vector names;

            /*
             * @param v 要填充的标签处理程序池名称
             */
            TagHandlerPoolVisitor(Vector v) {
                names = v;
            }

            /*
             * 获取给定自定义标签的标签处理程序池的名称, 并将其添加到标签处理程序池的名称列表中, 除非列表已经包含它.
             */
            public void visit(Node.CustomTag n) throws JasperException {

                if (!n.implementsSimpleTag()) {
                    String name =
                        createTagHandlerPoolName(
                            n.getPrefix(),
                            n.getLocalName(),
                            n.getAttributes(),
                            n.hasEmptyBody());
                    n.setTagHandlerPoolName(name);
                    if (!names.contains(name)) {
                        names.add(name);
                    }
                }
                visitBody(n);
            }

            /*
             * 创建标签处理程序池的名称，其标签处理程序可以用于服务此操作.
             *
             * @return 标签处理程序池的名称
             */
            private String createTagHandlerPoolName(
                String prefix,
                String shortName,
                Attributes attrs,
                boolean hasEmptyBody) {
                String poolName = null;

                poolName = "_jspx_tagPool_" + prefix + "_" + shortName;
                if (attrs != null) {
                    String[] attrNames = new String[attrs.getLength()];
                    for (int i = 0; i < attrNames.length; i++) {
                        attrNames[i] = attrs.getQName(i);
                    }
                    Arrays.sort(attrNames, Collections.reverseOrder());
                    for (int i = 0; i < attrNames.length; i++) {
                        poolName = poolName + "_" + attrNames[i];
                    }
                }
                if (hasEmptyBody) {
                    poolName = poolName + "_nobody";
                }
                return JspUtil.makeXmlJavaIdentifier(poolName);
            }
        }

        page.visit(new TagHandlerPoolVisitor(tagHandlerPoolNames));
    }

    private void declareTemporaryScriptingVars(Node.Nodes page)
        throws JasperException {

        class ScriptingVarVisitor extends Node.Visitor {

            private Vector vars;

            ScriptingVarVisitor() {
                vars = new Vector();
            }

            public void visit(Node.CustomTag n) throws JasperException {

                if (n.getCustomNestingLevel() > 0) {
                    TagVariableInfo[] tagVarInfos = n.getTagVariableInfos();
                    VariableInfo[] varInfos = n.getVariableInfos();

                    if (varInfos.length > 0) {
                        for (int i = 0; i < varInfos.length; i++) {
                            String varName = varInfos[i].getVarName();
                            String tmpVarName =
                                "_jspx_"
                                    + varName
                                    + "_"
                                    + n.getCustomNestingLevel();
                            if (!vars.contains(tmpVarName)) {
                                vars.add(tmpVarName);
                                out.printin(varInfos[i].getClassName());
                                out.print(" ");
                                out.print(tmpVarName);
                                out.print(" = ");
                                out.print(null);
                                out.println(";");
                            }
                        }
                    } else {
                        for (int i = 0; i < tagVarInfos.length; i++) {
                            String varName = tagVarInfos[i].getNameGiven();
                            if (varName == null) {
                                varName =
                                    n.getTagData().getAttributeString(
                                        tagVarInfos[i].getNameFromAttribute());
                            } else if (
                                tagVarInfos[i].getNameFromAttribute()
                                    != null) {
                                // alias
                                continue;
                            }
                            String tmpVarName =
                                "_jspx_"
                                    + varName
                                    + "_"
                                    + n.getCustomNestingLevel();
                            if (!vars.contains(tmpVarName)) {
                                vars.add(tmpVarName);
                                out.printin(tagVarInfos[i].getClassName());
                                out.print(" ");
                                out.print(tmpVarName);
                                out.print(" = ");
                                out.print(null);
                                out.println(";");
                            }
                        }
                    }
                }

                visitBody(n);
            }
        }

        page.visit(new ScriptingVarVisitor());
    }

    /**
     * 实例化标签处理池, 生成 _jspInit() 方法.
     * 对于标签文件, _jspInit 必须手动调用, 而且ServletConfig对象显式传递.
     */
    private void generateInit() {

        if (ctxt.isTagFile()) {
            out.printil("private void _jspInit(ServletConfig config) {");
        } else {
            out.printil("public void _jspInit() {");
        }

        out.pushIndent();
        for (int i = 0; i < tagHandlerPoolNames.size(); i++) {
            out.printin((String)tagHandlerPoolNames.elementAt(i));
            out.print(
                " = org.apache.jasper.runtime.TagHandlerPool.getTagHandlerPool(");
            if (ctxt.isTagFile()) {
                out.print("config");
            } else {
                out.print("getServletConfig()");
            }
            out.println(");");
        }
        out.popIndent();
        out.printil("}");
        out.println();
    }

    /**
     * 生成 _jspDestroy() 方法, 负责调用任何标签处理程序池中的每一个标签处理程序的release()方法.
     */
    private void generateDestroy() {

        out.printil("public void _jspDestroy() {");
        out.pushIndent();
        for (int i = 0; i < tagHandlerPoolNames.size(); i++) {
            out.printin((String)tagHandlerPoolNames.elementAt(i));
            out.println(".release();");
        }
        out.popIndent();
        out.printil("}");
        out.println();
    }

    /**
     * 生成包名(servlet 和标签处理程序共享)
     */
    private void genPreamblePackage(String packageName)
        throws JasperException {
        if (!"".equals(packageName) && packageName != null) {
            out.printil("package " + packageName + ";");
            out.println();
        }
    }

    /**
     * 生成import (servlet 和标签处理程序共享)
     */
    private void genPreambleImports() throws JasperException {
        Iterator iter = pageInfo.getImports().iterator();
        while (iter.hasNext()) {
            out.printin("import ");
            out.print((String)iter.next());
            out.println(";");
        }
        out.println();
    }

    /**
     * 静态初始化器生成.
     * 例如, 依赖列表, el 功能 map, 前缀 map. (servlet 和标签处理程序共享)
     */
    private void genPreambleStaticInitializers() throws JasperException {
        // Static data for getDependants()
        out.printil("private static java.util.List _jspx_dependants;");
        out.println();
        List dependants = pageInfo.getDependants();
        Iterator iter = dependants.iterator();
        if (!dependants.isEmpty()) {
            out.printil("static {");
            out.pushIndent();
            out.printin("_jspx_dependants = new java.util.ArrayList(");
            out.print("" + dependants.size());
            out.println(");");
            while (iter.hasNext()) {
                out.printin("_jspx_dependants.add(\"");
                out.print((String)iter.next());
                out.println("\");");
            }
            out.popIndent();
            out.printil("}");
            out.println();
        }
    }

    /**
     * 声明标签处理程序池 (相同类型和相同属性集的标签共享相同的标签处理程序池)
     * (servlet 和标签处理程序共享)
     */
    private void genPreambleClassVariableDeclarations(String className)
        throws JasperException {
        if (isPoolingEnabled && !tagHandlerPoolNames.isEmpty()) {
            for (int i = 0; i < tagHandlerPoolNames.size(); i++) {
                out.printil(
                    "private org.apache.jasper.runtime.TagHandlerPool "
                        + tagHandlerPoolNames.elementAt(i)
                        + ";");
            }
            out.println();
        }
    }

    /**
     * 声明的通用方法 (servlet 和标签处理程序共享)
     */
    private void genPreambleMethods() throws JasperException {
        // Method 用于获取编译时文件依赖项
        out.printil("public Object getDependants() {");
        out.pushIndent();
        out.printil("return _jspx_dependants;");
        out.popIndent();
        out.printil("}");
        out.println();

        if (isPoolingEnabled && !tagHandlerPoolNames.isEmpty()) {
            generateInit();
            generateDestroy();
        }
    }

    /**
     * 生成servlet静态部分的开始部分.
     */
    private void generatePreamble(Node.Nodes page) throws JasperException {

        String servletPackageName = ctxt.getServletPackageName();
        String servletClassName = ctxt.getServletClassName();
        String serviceMethodName = Constants.SERVICE_METHOD_NAME;

        // 第一个包名:
        genPreamblePackage(servletPackageName);

        // 生成import
        genPreambleImports();

        // 生成的类声明
        out.printin("public final class ");
        out.print(servletClassName);
        out.print(" extends ");
        out.println(pageInfo.getExtends());
        out.printin(
            "    implements org.apache.jasper.runtime.JspSourceDependent");
        if (!pageInfo.isThreadSafe()) {
            out.println(",");
            out.printin("                 SingleThreadModel");
        }
        out.println(" {");
        out.pushIndent();

        // 类主体开始
        generateDeclarations(page);

        // Static 初始化器
        genPreambleStaticInitializers();

        // 类变量声明
        genPreambleClassVariableDeclarations(servletClassName);

        // Constructor
        //  generateConstructor(className);

        // Methods here
        genPreambleMethods();

        // Now the service method
        out.printin("public void ");
        out.print(serviceMethodName);
        out.println(
            "(HttpServletRequest request, HttpServletResponse response)");
        out.println("        throws java.io.IOException, ServletException {");

        out.pushIndent();
        out.println();

        // 局部变量定义
        out.printil("JspFactory _jspxFactory = null;");
        out.printil("PageContext pageContext = null;");
        if (pageInfo.isSession())
            out.printil("HttpSession session = null;");

        if (pageInfo.isErrorPage()) {
            out.printil(
                "Throwable exception = org.apache.jasper.runtime.JspRuntimeLibrary.getThrowable(request);");
            out.printil("if (exception != null) {");
            out.pushIndent();
            out.printil(
                "response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);");
            out.popIndent();
            out.printil("}");
        }

        out.printil("ServletContext application = null;");
        out.printil("ServletConfig config = null;");
        out.printil("JspWriter out = null;");
        out.printil("Object page = this;");

        out.printil("JspWriter _jspx_out = null;");
        out.printil("PageContext _jspx_page_context = null;");
        out.println();

        declareTemporaryScriptingVars(page);
        out.println();

        out.printil("try {");
        out.pushIndent();

        out.printil("_jspxFactory = JspFactory.getDefaultFactory();");

        out.printin("response.setContentType(");
        out.print(quote(pageInfo.getContentType()));
        out.println(");");

        if (ctxt.getOptions().isXpoweredBy()) {
            out.printil("response.addHeader(\"X-Powered-By\", \"JSP/2.0\");");
        }

        out.printil(
            "pageContext = _jspxFactory.getPageContext(this, request, response,");
        out.printin("\t\t\t");
        out.print(quote(pageInfo.getErrorPage()));
        out.print(", " + pageInfo.isSession());
        out.print(", " + pageInfo.getBuffer());
        out.print(", " + pageInfo.isAutoFlush());
        out.println(");");
        out.printil("_jspx_page_context = pageContext;");

        out.printil("application = pageContext.getServletContext();");
        out.printil("config = pageContext.getServletConfig();");

        if (pageInfo.isSession())
            out.printil("session = pageContext.getSession();");
        out.printil("out = pageContext.getOut();");
        out.printil("_jspx_out = out;");
        out.println();
    }

    /**
     * 生成一个XML Prolog, 其中包括XML声明和XML文档类型声明.
     */
    private void generateXmlProlog(Node.Nodes page) {

        /*
         * 在以下条件下生成xml声明:
         *
         * - 'omit-xml-declaration' attribute of <jsp:output>动作的'omit-xml-declaration'属性被设置为"no" 或 "false"
         * - JSP 文档包括<jsp:root>
         */
        String omitXmlDecl = pageInfo.getOmitXmlDecl();
        if ((omitXmlDecl != null && !JspUtil.booleanValue(omitXmlDecl))
            || (omitXmlDecl == null
                && page.getRoot().isXmlSyntax()
                && !pageInfo.hasJspRoot()
                && !ctxt.isTagFile())) {
            String cType = pageInfo.getContentType();
            String charSet = cType.substring(cType.indexOf("charset=") + 8);
            out.printil(
                "out.write(\"<?xml version=\\\"1.0\\\" encoding=\\\""
                    + charSet
                    + "\\\"?>\\n\");");
        }

        /*
         * 输出一个 DOCTYPE 声明, 如果文档根元素出现.
         * 如果文档根元素出现:
         *     <!DOCTYPE name PUBLIC "doctypePublic" "doctypeSystem">
         * 否则
         *     <!DOCTYPE name SYSTEM "doctypeSystem" >
         */

        String doctypeName = pageInfo.getDoctypeName();
        if (doctypeName != null) {
            String doctypePublic = pageInfo.getDoctypePublic();
            String doctypeSystem = pageInfo.getDoctypeSystem();
            out.printin("out.write(\"<!DOCTYPE ");
            out.print(doctypeName);
            if (doctypePublic == null) {
                out.print(" SYSTEM \\\"");
            } else {
                out.print(" PUBLIC \\\"");
                out.print(doctypePublic);
                out.print("\\\" \\\"");
            }
            out.print(doctypeSystem);
            out.println("\\\">\\n\");");
        }
    }

    /*
     * 生成的构造函数. (servlet 和标签处理程序共享)
     */
    private void generateConstructor(String className) {
        out.printil("public " + className + "() {");
        out.printil("}");
        out.println();
    }

    /**
     * 为页面中的元素生成代码的访问者.
     */
    class GenerateVisitor extends Node.Visitor {

        /*
         * Hashtable 包含标签处理程序上的内省信息:
         *   <key>: 标签前缀
         *   <value>: hashtable 包含标签处理程序上的内省信息:
         *              <key>: 标签短名称
         *              <value>: <prefix:shortName>标签的标签处理程序上的内省信息
         */
        private Hashtable handlerInfos;

        private Hashtable tagVarNumbers;
        private String parent;
        private boolean isSimpleTagParent; // Is parent a SimpleTag?
        private String pushBodyCountVar;
        private String simpleTagHandlerVar;
        private boolean isSimpleTagHandler;
        private boolean isFragment;
        private boolean isTagFile;
        private ServletWriter out;
        private ArrayList methodsBuffered;
        private FragmentHelperClass fragmentHelperClass;
        private int methodNesting;
        private TagInfo tagInfo;
        private ClassLoader loader;
        private int charArrayCount;
        private HashMap textMap;

        /**
         * Constructor.
         */
        public GenerateVisitor(
            boolean isTagFile,
            ServletWriter out,
            ArrayList methodsBuffered,
            FragmentHelperClass fragmentHelperClass,
            ClassLoader loader,
            TagInfo tagInfo) {

            this.isTagFile = isTagFile;
            this.out = out;
            this.methodsBuffered = methodsBuffered;
            this.fragmentHelperClass = fragmentHelperClass;
            this.loader = loader;
            this.tagInfo = tagInfo;
            methodNesting = 0;
            handlerInfos = new Hashtable();
            tagVarNumbers = new Hashtable();
            textMap = new HashMap();
        }

        /**
         * 返回一个属性值, 可选的URL编码. 如果该值是一个运行时表达式, 结果就是表达式本身, 一个字符串.
         * 如果结果是EL表达式, 插入一个调用给编译器. 如果结果是一个命名属性，插入生成的变量名. 否则，结果是字符串文字, 引号和转义.
         *
         * @param attr 一个JspAttribute 对象
         * @param encode true , 如果被URL编码
         * @param expectedType EL评估的期望类型(忽略不是EL表达式的属性)
         */
        private String attributeValue(
            Node.JspAttribute attr,
            boolean encode,
            Class expectedType) {
            String v = attr.getValue();
            if (!attr.isNamedAttribute() && (v == null))
                return "";

            if (attr.isExpression()) {
                if (encode) {
                    return "org.apache.jasper.runtime.JspRuntimeLibrary.URLEncode(String.valueOf("
                        + v
                        + "), request.getCharacterEncoding())";
                }
                return v;
            } else if (attr.isELInterpreterInput()) {
                boolean replaceESC = v.indexOf(Constants.ESC) > 0;
                v =
                    JspUtil.interpreterCall(
                        this.isTagFile,
                        v,
                        expectedType,
                        attr.getEL().getMapName(),
                        false);
                // XXX ESC replacement hack
                if (replaceESC) {
                    v = "(" + v + ").replace(" + Constants.ESCStr + ", '$')";
                }
                if (encode) {
                    return "org.apache.jasper.runtime.JspRuntimeLibrary.URLEncode("
                        + v
                        + ", request.getCharacterEncoding())";
                }
                return v;
            } else if (attr.isNamedAttribute()) {
                return attr.getNamedAttributeNode().getTemporaryVariableName();
            } else {
                if (encode) {
                    return "org.apache.jasper.runtime.JspRuntimeLibrary.URLEncode("
                        + quote(v)
                        + ", request.getCharacterEncoding())";
                }
                return quote(v);
            }
        }

        /**
         * 打印参数行为中指定的属性值, 以name=value 字符串的形式.
         *
         * @param n 参数行为节点的父节点
         */
        private void printParams(Node n, String pageParam, boolean literal)
            throws JasperException {

            class ParamVisitor extends Node.Visitor {
                String separator;

                ParamVisitor(String separator) {
                    this.separator = separator;
                }

                public void visit(Node.ParamAction n) throws JasperException {

                    out.print(" + ");
                    out.print(separator);
                    out.print(" + ");
                    out.print(
                        "org.apache.jasper.runtime.JspRuntimeLibrary."
                            + "URLEncode("
                            + quote(n.getTextAttribute("name"))
                            + ", request.getCharacterEncoding())");
                    out.print("+ \"=\" + ");
                    out.print(attributeValue(n.getValue(), true, String.class));

                    // The separator is '&' after the second use
                    separator = "\"&\"";
                }
            }

            String sep;
            if (literal) {
                sep = pageParam.indexOf('?') > 0 ? "\"&\"" : "\"?\"";
            } else {
                sep = "((" + pageParam + ").indexOf('?')>0? '&': '?')";
            }
            if (n.getBody() != null) {
                n.getBody().visit(new ParamVisitor(sep));
            }
        }

        public void visit(Node.Expression n) throws JasperException {
            n.setBeginJavaLine(out.getJavaLine());
            out.printin("out.print(");
            out.printMultiLn(n.getText());
            out.println(");");
            n.setEndJavaLine(out.getJavaLine());
        }

        public void visit(Node.Scriptlet n) throws JasperException {
            n.setBeginJavaLine(out.getJavaLine());
            out.printMultiLn(n.getText());
            out.println();
            n.setEndJavaLine(out.getJavaLine());
        }

        public void visit(Node.ELExpression n) throws JasperException {
            n.setBeginJavaLine(out.getJavaLine());
            if (!pageInfo.isELIgnored()) {
                out.printil(
                    "out.write("
                        + JspUtil.interpreterCall(
                            this.isTagFile,
                            "${" + new String(n.getText()) + "}",
                            String.class,
                            n.getEL().getMapName(),
                            false)
                        + ");");
            } else {
                out.printil(
                    "out.write("
                        + quote("${" + new String(n.getText()) + "}")
                        + ");");
            }
            n.setEndJavaLine(out.getJavaLine());
        }

        public void visit(Node.IncludeAction n) throws JasperException {

            String flush = n.getTextAttribute("flush");
            Node.JspAttribute page = n.getPage();

            boolean isFlush = false; // 默认是 false;
            if ("true".equals(flush))
                isFlush = true;

            n.setBeginJavaLine(out.getJavaLine());

            String pageParam;
            if (page.isNamedAttribute()) {
                // 如果页面通过jsp:attribute指定的 jsp:include, 首先生成代码以评估该主体.
                pageParam =
                    generateNamedAttributeValue(page.getNamedAttributeNode());
            } else {
                pageParam = attributeValue(page, false, String.class);
            }

            // 如果任何参数的值都是由jsp:attribute指定的, 首先准备这些值.
            Node jspBody = findJspBody(n);
            if (jspBody != null) {
                prepareParams(jspBody);
            } else {
                prepareParams(n);
            }

            out.printin(
                "org.apache.jasper.runtime.JspRuntimeLibrary.include(request, response, "
                    + pageParam);
            printParams(n, pageParam, page.isLiteral());
            out.println(", out, " + isFlush + ");");

            n.setEndJavaLine(out.getJavaLine());
        }

        /**
         * 扫描给定父节点的所有子节点.  对于每一个<param>元素, 如果它的值通过(<jsp:attribute>)属性指定,
         * 生成代码，首先对这些机构进行评估.
         * <p>
         * 如果父节点是null, 直接返回.
         */
        private void prepareParams(Node parent) throws JasperException {
            if (parent == null)
                return;

            Node.Nodes subelements = parent.getBody();
            if (subelements != null) {
                for (int i = 0; i < subelements.size(); i++) {
                    Node n = subelements.getNode(i);
                    if (n instanceof Node.ParamAction) {
                        Node.Nodes paramSubElements = n.getBody();
                        for (int j = 0;
                            (paramSubElements != null)
                                && (j < paramSubElements.size());
                            j++) {
                            Node m = paramSubElements.getNode(j);
                            if (m instanceof Node.NamedAttribute) {
                                generateNamedAttributeValue(
                                    (Node.NamedAttribute)m);
                            }
                        }
                    }
                }
            }
        }

        /**
         * 查找给定父节点的<jsp:body>子元素.
         * 如果未找到, 返回null.
         */
        private Node.JspBody findJspBody(Node parent) throws JasperException {
            Node.JspBody result = null;

            Node.Nodes subelements = parent.getBody();
            for (int i = 0;
                (subelements != null) && (i < subelements.size());
                i++) {
                Node n = subelements.getNode(i);
                if (n instanceof Node.JspBody) {
                    result = (Node.JspBody)n;
                    break;
                }
            }
            return result;
        }

        public void visit(Node.ForwardAction n) throws JasperException {
            Node.JspAttribute page = n.getPage();

            n.setBeginJavaLine(out.getJavaLine());

            out.printil("if (true) {"); // 因此javac 不会编译
            out.pushIndent(); // codes after "return"

            String pageParam;
            if (page.isNamedAttribute()) {
                // 如果 jsp:forward的页面通过jsp:attribute指定, 首先生成的代码以评估该主体.
                pageParam =
                    generateNamedAttributeValue(page.getNamedAttributeNode());
            } else {
                pageParam = attributeValue(page, false, String.class);
            }

            // 如果参数通过jsp:attribute指定值, 首先准备这些值.
            Node jspBody = findJspBody(n);
            if (jspBody != null) {
                prepareParams(jspBody);
            } else {
                prepareParams(n);
            }

            out.printin("_jspx_page_context.forward(");
            out.print(pageParam);
            printParams(n, pageParam, page.isLiteral());
            out.println(");");
            if (isTagFile || isFragment) {
                out.printil("throw new SkipPageException();");
            } else {
                out.printil((methodNesting > 0) ? "return true;" : "return;");
            }
            out.popIndent();
            out.printil("}");

            n.setEndJavaLine(out.getJavaLine());
            // XXX 不知道是否能在这之后消除死代码.
        }

        public void visit(Node.GetProperty n) throws JasperException {
            String name = n.getTextAttribute("name");
            String property = n.getTextAttribute("property");

            n.setBeginJavaLine(out.getJavaLine());

            if (beanInfo.checkVariable(name)) {
                // 使用 useBean定义bean, 编译时的自省
                Class bean = beanInfo.getBeanType(name);
                String beanName = JspUtil.getCanonicalName(bean);
                java.lang.reflect.Method meth =
                    JspRuntimeLibrary.getReadMethod(bean, property);
                String methodName = meth.getName();
                out.printil(
                    "out.write(org.apache.jasper.runtime.JspRuntimeLibrary.toString("
                        + "((("
                        + beanName
                        + ")_jspx_page_context.findAttribute("
                        + "\""
                        + name
                        + "\"))."
                        + methodName
                        + "())));");
            } else {
                // 对象可以是关联的自定义操作
                // 这个名称的VariableInfo 条目.
                // 获取类名，然后在运行时进行内省.
                out.printil(
                    "out.write(org.apache.jasper.runtime.JspRuntimeLibrary.toString"
                        + "(org.apache.jasper.runtime.JspRuntimeLibrary.handleGetProperty"
                        + "(_jspx_page_context.findAttribute(\""
                        + name
                        + "\"), \""
                        + property
                        + "\")));");
            }

            n.setEndJavaLine(out.getJavaLine());
        }

        public void visit(Node.SetProperty n) throws JasperException {
            String name = n.getTextAttribute("name");
            String property = n.getTextAttribute("property");
            String param = n.getTextAttribute("param");
            Node.JspAttribute value = n.getValue();

            n.setBeginJavaLine(out.getJavaLine());

            if ("*".equals(property)) {
                out.printil(
                    "org.apache.jasper.runtime.JspRuntimeLibrary.introspect("
                        + "_jspx_page_context.findAttribute("
                        + "\""
                        + name
                        + "\"), request);");
            } else if (value == null) {
                if (param == null)
                    param = property; // 默认值与属性相同
                out.printil(
                    "org.apache.jasper.runtime.JspRuntimeLibrary.introspecthelper("
                        + "_jspx_page_context.findAttribute(\""
                        + name
                        + "\"), \""
                        + property
                        + "\", request.getParameter(\""
                        + param
                        + "\"), "
                        + "request, \""
                        + param
                        + "\", false);");
            } else if (value.isExpression()) {
                out.printil(
                    "org.apache.jasper.runtime.JspRuntimeLibrary.handleSetProperty("
                        + "_jspx_page_context.findAttribute(\""
                        + name
                        + "\"), \""
                        + property
                        + "\",");
                out.print(attributeValue(value, false, null));
                out.println(");");
            } else if (value.isELInterpreterInput()) {
                // 必须在运行时决定对解释器的调用，因为不知道一般情况下需要什么类型的解释器; 因此不能硬将调用连接到生成的代码中.
            	//(XXX 优化<jsp:useBean>暴露bean的地方, 就像getProperty的代码.)

                // 下列参数适合传递给JspRuntimeLibrary.handleSetPropertyExpression():
                // - 'pageContext' 是一个 VariableResolver.
                // - 'this' (Tag 文件生成的Servlet 或生成的标签处理程序)是一个 FunctionMapper.
                out.printil(
                    "org.apache.jasper.runtime.JspRuntimeLibrary.handleSetPropertyExpression("
                        + "_jspx_page_context.findAttribute(\""
                        + name
                        + "\"), \""
                        + property
                        + "\", "
                        + quote(value.getValue())
                        + ", "
                        + "_jspx_page_context, "
                        + value.getEL().getMapName()
                        + ");");
            } else if (value.isNamedAttribute()) {
                // 如果 setProperty的值通过jsp:attribute指定, 首先生成代码以评估该主体.
                String valueVarName =
                    generateNamedAttributeValue(value.getNamedAttributeNode());
                out.printil(
                    "org.apache.jasper.runtime.JspRuntimeLibrary.introspecthelper("
                        + "_jspx_page_context.findAttribute(\""
                        + name
                        + "\"), \""
                        + property
                        + "\", "
                        + valueVarName
                        + ", null, null, false);");
            } else {
                out.printin(
                    "org.apache.jasper.runtime.JspRuntimeLibrary.introspecthelper("
                        + "_jspx_page_context.findAttribute(\""
                        + name
                        + "\"), \""
                        + property
                        + "\", ");
                out.print(attributeValue(value, false, null));
                out.println(", null, null, false);");
            }

            n.setEndJavaLine(out.getJavaLine());
        }

        public void visit(Node.UseBean n) throws JasperException {

            String name = n.getTextAttribute("id");
            String scope = n.getTextAttribute("scope");
            String klass = n.getTextAttribute("class");
            String type = n.getTextAttribute("type");
            Node.JspAttribute beanName = n.getBeanName();

            if (type == null) // 如果未指定, 使用类作为bean的类型
                type = klass;

            String scopename = "PageContext.PAGE_SCOPE"; // Default to page
            String lock = "_jspx_page_context";

            if ("request".equals(scope)) {
                scopename = "PageContext.REQUEST_SCOPE";
                lock = "request";
            } else if ("session".equals(scope)) {
                scopename = "PageContext.SESSION_SCOPE";
                lock = "session";
            } else if ("application".equals(scope)) {
                scopename = "PageContext.APPLICATION_SCOPE";
                lock = "application";
            }

            n.setBeginJavaLine(out.getJavaLine());

            // Declare bean
            out.printin(type);
            out.print(' ');
            out.print(name);
            out.println(" = null;");

            // 获取或创建bean时锁定
            out.printin("synchronized (");
            out.print(lock);
            out.println(") {");
            out.pushIndent();

            // 从上下文中定位bean
            out.printin(name);
            out.print(" = (");
            out.print(type);
            out.print(") _jspx_page_context.getAttribute(");
            out.print(quote(name));
            out.print(", ");
            out.print(scopename);
            out.println(");");

            // Create bean
            /*
             * 检查是否已经有bean
             */
            out.printin("if (");
            out.print(name);
            out.println(" == null){");
            out.pushIndent();
            if (klass == null && beanName == null) {
                /*
                 * 如果类名和 beanName 都没有指定, 必须在本地找到bean, 否则它是一个错误
                 */
                out.printin(
                    "throw new java.lang.InstantiationException(\"bean ");
                out.print(name);
                out.println(" not found within scope\");");
            } else {
                /*
                 * 如果bean不在指定的范围内，则实例化它.
                 */
                boolean generateNew = false;
                if (beanName == null) {
                    try {
                        Class bean = ctxt.getClassLoader().loadClass(klass);
                        int modifiers = bean.getModifiers();
                        if (!Modifier.isPublic(modifiers) ||
                            Modifier.isInterface(modifiers) ||
                            Modifier.isAbstract(modifiers)) {
                            throw new Exception("Invalid bean class modifier");
                        }
                        // 检查是否有一个无参构造函数
                        bean.getConstructor(new Class[] {});
                        generateNew = true;
                    } catch (Exception e) {
                        // 无法实例化指定的类
                        if (ctxt.getOptions().getErrorOnUseBeanInvalidClassAttribute()) {
                            err.jspError(n, "jsp.error.invalid.bean", klass);
                        }
                    }
                }
                if (!generateNew) {
                    String className;
                    if (beanName != null) {
                        if (beanName.isNamedAttribute()) {
                            // 如果beanName的值通过jsp:attribute指定, 首先生成代码以评估该主体.
                            className =
                                generateNamedAttributeValue(
                                    beanName.getNamedAttributeNode());
                        } else {
                            className =
                                attributeValue(beanName, false, String.class);
                        }
                    } else {
                        // 意味着 klass不是 null
                        className = quote(klass);
                    }
                    out.printil("try {");
                    out.pushIndent();
                    out.printin(name);
                    out.print(" = (");
                    out.print(type);
                    out.print(") java.beans.Beans.instantiate(");
                    out.print("this.getClass().getClassLoader(), ");
                    out.print(className);
                    out.println(");");
                    out.popIndent();
                    /*
                     * Note: Beans.instantiate throws ClassNotFoundException
                     * if the bean class is abstract.
                     */
                    out.printil("} catch (ClassNotFoundException exc) {");
                    out.pushIndent();
                    out.printil(
                        "throw new InstantiationException(exc.getMessage());");
                    out.popIndent();
                    out.printil("} catch (Exception exc) {");
                    out.pushIndent();
                    out.printin("throw new ServletException(");
                    out.print("\"Cannot create bean of class \" + ");
                    out.print(className);
                    out.println(", exc);");
                    out.popIndent();
                    out.printil("}"); // close of try
                } else {
                    // 意味着 klass不是 null
                    // 生成代码以实例化bean类
                    out.printin(name);
                    out.print(" = new ");
                    out.print(klass);
                    out.println("();");
                }
                /*
                 * 在指定范围内为bean设置属性
                 */
                out.printin("_jspx_page_context.setAttribute(");
                out.print(quote(name));
                out.print(", ");
                out.print(name);
                out.print(", ");
                out.print(scopename);
                out.println(");");

                // 只在bean实例化时访问主体
                visitBody(n);
            }
            out.popIndent();
            out.printil("}");

            // 锁结束
            out.popIndent();
            out.printil("}");

            n.setEndJavaLine(out.getJavaLine());
        }

        /**
         * @return 'attr = "value"'形式的字符串
         */
        private String makeAttr(String attr, String value) {
            if (value == null)
                return "";

            return " " + attr + "=\"" + value + '\"';
        }

        public void visit(Node.PlugIn n) throws JasperException {

            /**
             * 在插件中处理 <jsp:param> 的访问者
             */
            class ParamVisitor extends Node.Visitor {

                private boolean ie;

                ParamVisitor(boolean ie) {
                    this.ie = ie;
                }

                public void visit(Node.ParamAction n) throws JasperException {

                    String name = n.getTextAttribute("name");
                    if (name.equalsIgnoreCase("object"))
                        name = "java_object";
                    else if (name.equalsIgnoreCase("type"))
                        name = "java_type";

                    n.setBeginJavaLine(out.getJavaLine());
                    // XXX - 解决了一个bug - 用于内联输出的值, 只有值不是EL表达式才行.  而且, 嵌入标签的key/value 对没有被正确生成.
                    // 仔细检查一下这是不是正确的行为.
                    if (ie) {
                        // We want something of the form
                        // out.println( "<PARAM name=\"blah\"
                        //     value=\"" + ... + "\">" );
                        out.printil(
                            "out.write( \"<PARAM name=\\\""
                                + escape(name)
                                + "\\\" value=\\\"\" + "
                                + attributeValue(
                                    n.getValue(),
                                    false,
                                    String.class)
                                + " + \"\\\">\" );");
                        out.printil("out.write(\"\\n\");");
                    } else {
                        // We want something of the form
                        // out.print( " blah=\"" + ... + "\"" );
                        out.printil(
                            "out.write( \" "
                                + escape(name)
                                + "=\\\"\" + "
                                + attributeValue(
                                    n.getValue(),
                                    false,
                                    String.class)
                                + " + \"\\\"\" );");
                    }

                    n.setEndJavaLine(out.getJavaLine());
                }
            }

            String type = n.getTextAttribute("type");
            String code = n.getTextAttribute("code");
            String name = n.getTextAttribute("name");
            Node.JspAttribute height = n.getHeight();
            Node.JspAttribute width = n.getWidth();
            String hspace = n.getTextAttribute("hspace");
            String vspace = n.getTextAttribute("vspace");
            String align = n.getTextAttribute("align");
            String iepluginurl = n.getTextAttribute("iepluginurl");
            String nspluginurl = n.getTextAttribute("nspluginurl");
            String codebase = n.getTextAttribute("codebase");
            String archive = n.getTextAttribute("archive");
            String jreversion = n.getTextAttribute("jreversion");

            String widthStr = null;
            if (width != null) {
                if (width.isNamedAttribute()) {
                    widthStr =
                        generateNamedAttributeValue(
                            width.getNamedAttributeNode());
                } else {
                    widthStr = attributeValue(width, false, String.class);
                }
            }

            String heightStr = null;
            if (height != null) {
                if (height.isNamedAttribute()) {
                    heightStr =
                        generateNamedAttributeValue(
                            height.getNamedAttributeNode());
                } else {
                    heightStr = attributeValue(height, false, String.class);
                }
            }

            if (iepluginurl == null)
                iepluginurl = Constants.IE_PLUGIN_URL;
            if (nspluginurl == null)
                nspluginurl = Constants.NS_PLUGIN_URL;

            n.setBeginJavaLine(out.getJavaLine());

            // 如果参数值通过jsp:attribute指定, 首先准备这些值.
            // 查找一个参数节点和预备参数的子元素:
            Node.JspBody jspBody = findJspBody(n);
            if (jspBody != null) {
                Node.Nodes subelements = jspBody.getBody();
                if (subelements != null) {
                    for (int i = 0; i < subelements.size(); i++) {
                        Node m = subelements.getNode(i);
                        if (m instanceof Node.ParamsAction) {
                            prepareParams(m);
                            break;
                        }
                    }
                }
            }

            // XXX - 解决了一个bug - width 和 height可以动态设置.  仔细检查这个生成是否正确.

            // IE 风格插件
            // <OBJECT ...>
            // 首先编写运行时输出字符串
            String s0 = "<OBJECT"
                    + makeAttr("classid", ctxt.getOptions().getIeClassId())
                    + makeAttr("name", name);

            String s1 = "";
            if (width != null) {
                s1 = " + \" width=\\\"\" + " + widthStr + " + \"\\\"\"";
            }

            String s2 = "";
            if (height != null) {
                s2 = " + \" height=\\\"\" + " + heightStr + " + \"\\\"\"";
            }

            String s3 = makeAttr("hspace", hspace)
                    + makeAttr("vspace", vspace)
                    + makeAttr("align", align)
                    + makeAttr("codebase", iepluginurl)
                    + '>';

            // 然后打印输出字符串到java文件
            out.printil(
                "out.write(" + quote(s0) + s1 + s2 + " + " + quote(s3) + ");");
            out.printil("out.write(\"\\n\");");

            // <PARAM > for java_code
            s0 = "<PARAM name=\"java_code\"" + makeAttr("value", code) + '>';
            out.printil("out.write(" + quote(s0) + ");");
            out.printil("out.write(\"\\n\");");

            // <PARAM > for java_codebase
            if (codebase != null) {
                s0 = "<PARAM name=\"java_codebase\""
                        + makeAttr("value", codebase)
                        + '>';
                out.printil("out.write(" + quote(s0) + ");");
                out.printil("out.write(\"\\n\");");
            }

            // <PARAM > for java_archive
            if (archive != null) {
                s0 = "<PARAM name=\"java_archive\""
                        + makeAttr("value", archive)
                        + '>';
                out.printil("out.write(" + quote(s0) + ");");
                out.printil("out.write(\"\\n\");");
            }

            // <PARAM > for type
            s0 = "<PARAM name=\"type\""
                    + makeAttr(
                        "value",
                        "application/x-java-"
                            + type
                            + ";"
                            + ((jreversion == null)
                                ? ""
                                : "version=" + jreversion))
                    + '>';
            out.printil("out.write(" + quote(s0) + ");");
            out.printil("out.write(\"\\n\");");

            /*
             * 在插件主体中为每个<jsp:param>生成一个<PARAM>
             */
            if (n.getBody() != null)
                n.getBody().visit(new ParamVisitor(true));

            /*
             * Netscape风格插件部分
             */
            out.printil("out.write(" + quote("<COMMENT>") + ");");
            out.printil("out.write(\"\\n\");");
            s0 = "<EMBED"
                    + makeAttr(
                        "type",
                        "application/x-java-"
                            + type
                            + ";"
                            + ((jreversion == null)
                                ? ""
                                : "version=" + jreversion))
                    + makeAttr("name", name);

            // 之前s1 和 s2 是相同的
            s3 = makeAttr("hspace", hspace)
                    + makeAttr("vspace", vspace)
                    + makeAttr("align", align)
                    + makeAttr("pluginspage", nspluginurl)
                    + makeAttr("java_code", code)
                    + makeAttr("java_codebase", codebase)
                    + makeAttr("java_archive", archive);
            out.printil(
                "out.write(" + quote(s0) + s1 + s2 + " + " + quote(s3) + ");");

            /*
             * 在插件主体中为每个<jsp:param>生成一个'attr = "value"'
             */
            if (n.getBody() != null)
                n.getBody().visit(new ParamVisitor(false));

            out.printil("out.write(" + quote("/>") + ");");
            out.printil("out.write(\"\\n\");");

            out.printil("out.write(" + quote("<NOEMBED>") + ");");
            out.printil("out.write(\"\\n\");");

            /*
             * Fallback
             */
            if (n.getBody() != null) {
                visitBody(n);
                out.printil("out.write(\"\\n\");");
            }

            out.printil("out.write(" + quote("</NOEMBED>") + ");");
            out.printil("out.write(\"\\n\");");

            out.printil("out.write(" + quote("</COMMENT>") + ");");
            out.printil("out.write(\"\\n\");");

            out.printil("out.write(" + quote("</OBJECT>") + ");");
            out.printil("out.write(\"\\n\");");

            n.setEndJavaLine(out.getJavaLine());
        }

        public void visit(Node.NamedAttribute n) throws JasperException {
            // 不要访问这个标签的主体 - 已经在之前做过了.
        }

        public void visit(Node.CustomTag n) throws JasperException {

            // 使用插件生成更有效的代码.
            if (n.useTagPlugin()) {
                generateTagPlugin(n);
                return;
            }

            TagHandlerInfo handlerInfo = getTagHandlerInfo(n);

            // 创建变量名
            String baseVar =
                createTagVarName(n.getQName(), n.getPrefix(), n.getLocalName());
            String tagEvalVar = "_jspx_eval_" + baseVar;
            String tagHandlerVar = "_jspx_th_" + baseVar;
            String tagPushBodyCountVar = "_jspx_push_body_count_" + baseVar;

            // 如果标记不包含脚本元素, 生成代码到一个方法.
            ServletWriter outSave = null;
            Node.ChildInfo ci = n.getChildInfo();
            if (ci.isScriptless() && !ci.hasScriptingVars()) {
                // 标记处理程序及其主体代码可以驻留在一个单独的方法中, 如果是在没有任何脚本变量定义的情况下.
                String tagMethod = "_jspx_meth_" + baseVar;

                // 生成对该方法的调用
                out.printin("if (");
                out.print(tagMethod);
                out.print("(");
                if (parent != null) {
                    out.print(parent);
                    out.print(", ");
                }
                out.print("_jspx_page_context");
                if (pushBodyCountVar != null) {
                    out.print(", ");
                    out.print(pushBodyCountVar);
                }
                out.println("))");
                out.pushIndent();
                out.printil((methodNesting > 0) ? "return true;" : "return;");
                out.popIndent();

                // 为方法设置新的缓冲区
                outSave = out;
                /* 对于分段, 它们的主体将在分段辅助类中生成, 和java行调整将在那里完成, 因此，它们在这里设置为null，以避免双重调整.
                */
                GenBuffer genBuffer =
                    new GenBuffer(n, n.implementsSimpleTag()? null: n.getBody());
                methodsBuffered.add(genBuffer);
                out = genBuffer.getOut();

                methodNesting++;
                // 生成方法声明的代码
                out.println();
                out.pushIndent();
                out.printin("private boolean ");
                out.print(tagMethod);
                out.print("(");
                if (parent != null) {
                    out.print("javax.servlet.jsp.tagext.JspTag ");
                    out.print(parent);
                    out.print(", ");
                }
                out.print("PageContext _jspx_page_context");
                if (pushBodyCountVar != null) {
                    out.print(", int[] ");
                    out.print(pushBodyCountVar);
                }
                out.println(")");
                out.printil("        throws Throwable {");
                out.pushIndent();

                // 初始化该方法的局部变量.
                if (! isTagFile) {
                    out.printil("PageContext pageContext = _jspx_page_context;");
                }
                out.printil("JspWriter out = _jspx_page_context.getOut();");
                generateLocalVariables(out, n);
            }

            if (n.implementsSimpleTag()) {
                generateCustomDoTag(n, handlerInfo, tagHandlerVar);
            } else {
                /*
                 * 经典的标签处理程序: 生成start元素, 主体,和end元素的代码
                 */
                generateCustomStart(
                    n,
                    handlerInfo,
                    tagHandlerVar,
                    tagEvalVar,
                    tagPushBodyCountVar);

                // 访问主体
                String tmpParent = parent;
                parent = tagHandlerVar;
                boolean isSimpleTagParentSave = isSimpleTagParent;
                isSimpleTagParent = false;
                String tmpPushBodyCountVar = null;
                if (n.implementsTryCatchFinally()) {
                    tmpPushBodyCountVar = pushBodyCountVar;
                    pushBodyCountVar = tagPushBodyCountVar;
                }
                boolean tmpIsSimpleTagHandler = isSimpleTagHandler;
                isSimpleTagHandler = false;

                visitBody(n);

                parent = tmpParent;
                isSimpleTagParent = isSimpleTagParentSave;
                if (n.implementsTryCatchFinally()) {
                    pushBodyCountVar = tmpPushBodyCountVar;
                }
                isSimpleTagHandler = tmpIsSimpleTagHandler;

                generateCustomEnd(
                    n,
                    tagHandlerVar,
                    tagEvalVar,
                    tagPushBodyCountVar);
            }

            if (ci.isScriptless() && !ci.hasScriptingVars()) {
                // 生成方法结束
                if (methodNesting > 0) {
                    out.printil("return false;");
                }
                out.popIndent();
                out.printil("}");
                out.popIndent();

                methodNesting--;

                // 恢复之前的writer
                out = outSave;
            }
        }

        private static final String SINGLE_QUOTE = "'";
        private static final String DOUBLE_QUOTE = "\\\"";

        public void visit(Node.UninterpretedTag n) throws JasperException {

            n.setBeginJavaLine(out.getJavaLine());

            /*
             * 写入开始标签
             */
            out.printin("out.write(\"<");
            out.print(n.getQName());

            Attributes attrs = n.getNonTaglibXmlnsAttributes();
            int attrsLen = (attrs == null) ? 0 : attrs.getLength();
            for (int i = 0; i < attrsLen; i++) {
                out.print(" ");
                out.print(attrs.getQName(i));
                out.print("=");
                String quote = DOUBLE_QUOTE;
                String value = attrs.getValue(i);
                if (value.indexOf('"') != -1) {
                    quote = SINGLE_QUOTE;
                }
                out.print(quote);
                out.print(value);
                out.print(quote);
            }

            attrs = n.getAttributes();
            attrsLen = (attrs == null) ? 0 : attrs.getLength();
            Node.JspAttribute[] jspAttrs = n.getJspAttributes();
            for (int i = 0; i < attrsLen; i++) {
                out.print(" ");
                out.print(attrs.getQName(i));
                out.print("=");
                if (jspAttrs[i].isELInterpreterInput()) {
                    out.print("\\\"\" + ");
                    out.print(attributeValue(jspAttrs[i], false, String.class));
                    out.print(" + \"\\\"");
                } else {
                    String quote = DOUBLE_QUOTE;
                    String value = attrs.getValue(i);
                    if (value.indexOf('"') != -1) {
                        quote = SINGLE_QUOTE;
                    }
                    out.print(quote);
                    out.print(value);
                    out.print(quote);
                }
            }

            if (n.getBody() != null) {
                out.println(">\");");

                // 访问标签主体
                visitBody(n);

                /*
                 * 写入结束标签
                 */
                out.printin("out.write(\"</");
                out.print(n.getQName());
                out.println(">\");");
            } else {
                out.println("/>\");");
            }

            n.setEndJavaLine(out.getJavaLine());
        }

        public void visit(Node.JspElement n) throws JasperException {

            n.setBeginJavaLine(out.getJavaLine());

            // 计算XML-style和命名的属性的属性值字符串
            Hashtable map = new Hashtable();
            Node.JspAttribute[] attrs = n.getJspAttributes();
            for (int i = 0; attrs != null && i < attrs.length; i++) {
                String attrStr = null;
                if (attrs[i].isNamedAttribute()) {
                    attrStr =
                        generateNamedAttributeValue(
                            attrs[i].getNamedAttributeNode());
                } else {
                    attrStr = attributeValue(attrs[i], false, Object.class);
                }
                String s =
                    " + \" "
                        + attrs[i].getName()
                        + "=\\\"\" + "
                        + attrStr
                        + " + \"\\\"\"";
                map.put(attrs[i].getName(), s);
            }

            // 写入开始标签, 使用XML-style 'name'属性作为元素名称
            String elemName =
                attributeValue(n.getNameAttribute(), false, String.class);
            out.printin("out.write(\"<\"");
            out.print(" + " + elemName);

            // 写入剩余的属性
            Enumeration enumeration = map.keys();
            while (enumeration.hasMoreElements()) {
                String attrName = (String)enumeration.nextElement();
                out.print((String)map.get(attrName));
            }

            // <jsp:element> 是否有嵌套标签不同于 <jsp:attribute>
            boolean hasBody = false;
            Node.Nodes subelements = n.getBody();
            if (subelements != null) {
                for (int i = 0; i < subelements.size(); i++) {
                    Node subelem = subelements.getNode(i);
                    if (!(subelem instanceof Node.NamedAttribute)) {
                        hasBody = true;
                        break;
                    }
                }
            }
            if (hasBody) {
                out.println(" + \">\");");

                // Smap不应该包含主体
                n.setEndJavaLine(out.getJavaLine());

                // 访问标签主体
                visitBody(n);

                // 写入结束标签
                out.printin("out.write(\"</\"");
                out.print(" + " + elemName);
                out.println(" + \">\");");
            } else {
                out.println(" + \"/>\");");
                n.setEndJavaLine(out.getJavaLine());
            }
        }

        public void visit(Node.TemplateText n) throws JasperException {

            String text = n.getText();

            int textSize = text.length();
            if (textSize == 0) {
                return;
            }

            if (textSize <= 3) {
               // 特殊情况下的小文本字符串
               n.setBeginJavaLine(out.getJavaLine());
               int lineInc = 0;
               for (int i = 0; i < textSize; i++) {
                   char ch = text.charAt(i);
                   out.printil("out.write(" + quote(ch) + ");");
                   if (i > 0) {
                       n.addSmap(lineInc);
                   }
                   if (ch == '\n') {
                       lineInc++;
                   }
               }
               n.setEndJavaLine(out.getJavaLine());
               return;
           }

            if (ctxt.getOptions().genStringAsCharArray()) {
               // 将Strings 转换为char 数组, 提高性能
                ServletWriter caOut;
                if (charArrayBuffer == null) {
                    charArrayBuffer = new GenBuffer();
                    caOut = charArrayBuffer.getOut();
                    caOut.pushIndent();
                    textMap = new HashMap();
                } else {
                    caOut = charArrayBuffer.getOut();
                }
                String charArrayName = (String) textMap.get(text);
                if (charArrayName == null) {
                    charArrayName = "_jspx_char_array_" + charArrayCount++;
                    textMap.put(text, charArrayName);
                    caOut.printin("static char[] ");
                    caOut.print(charArrayName);
                    caOut.print(" = ");
                    caOut.print(quote(text));
                    caOut.println(".toCharArray();");
                }

                n.setBeginJavaLine(out.getJavaLine());
                out.printil("out.write(" + charArrayName + ");");
                n.setEndJavaLine(out.getJavaLine());
                return;
            }

            n.setBeginJavaLine(out.getJavaLine());

            out.printin();
            StringBuffer sb = new StringBuffer("out.write(\"");
            int initLength = sb.length();
            int count = JspUtil.CHUNKSIZE;
            int srcLine = 0;    // 相对于起始资源行
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                --count;
                switch (ch) {
                    case '"' :
                        sb.append('\\').append('\"');
                        break;
                    case '\\' :
                        sb.append('\\').append('\\');
                        break;
                    case '\r' :
                        sb.append('\\').append('r');
                        break;
                    case '\n' :
                        sb.append('\\').append('n');
                        srcLine++;

                        if (breakAtLF || count < 0) {
                            // 生成一个 out.write(), 当在模板中看到 '\n'
                            sb.append("\");");
                            out.println(sb.toString());
                            if (i < text.length() - 1) {
                                out.printin();
                            }
                            sb.setLength(initLength);
                            count = JspUtil.CHUNKSIZE;
                        }
                        // 在这个行中添加一个 Smap
                        n.addSmap(srcLine);
                        break;
                    case '\t' : // 不确定是否需要它
                        sb.append('\\').append('t');
                        break;
                    default :
                        sb.append(ch);
                }
            }

            if (sb.length() > initLength) {
                sb.append("\");");
                out.println(sb.toString());
            }

            n.setEndJavaLine(out.getJavaLine());
        }

        public void visit(Node.JspBody n) throws JasperException {
            if (n.getBody() != null) {
                if (isSimpleTagHandler) {
                    out.printin(simpleTagHandlerVar);
                    out.print(".setJspBody(");
                    generateJspFragment(n, simpleTagHandlerVar);
                    out.println(");");
                } else {
                    visitBody(n);
                }
            }
        }

        public void visit(Node.InvokeAction n) throws JasperException {

            n.setBeginJavaLine(out.getJavaLine());

            // 将标签文件的虚拟页面范围复制到调用页的页面范围
            out.printil(
                "((org.apache.jasper.runtime.JspContextWrapper) this.jspContext).syncBeforeInvoke();");
            String varReaderAttr = n.getTextAttribute("varReader");
            String varAttr = n.getTextAttribute("var");
            if (varReaderAttr != null || varAttr != null) {
                out.printil("_jspx_sout = new java.io.StringWriter();");
            } else {
                out.printil("_jspx_sout = null;");
            }

            // 调用分段, 除非分段是 null
            out.printin("if (");
            out.print(toGetterMethod(n.getTextAttribute("fragment")));
            out.println(" != null) {");
            out.pushIndent();
            out.printin(toGetterMethod(n.getTextAttribute("fragment")));
            out.println(".invoke(_jspx_sout);");
            out.popIndent();
            out.printil("}");

            // 在适当的范围内保存varReader
            if (varReaderAttr != null || varAttr != null) {
                String scopeName = n.getTextAttribute("scope");
                out.printin("_jspx_page_context.setAttribute(");
                if (varReaderAttr != null) {
                    out.print(quote(varReaderAttr));
                    out.print(
                        ", new java.io.StringReader(_jspx_sout.toString())");
                } else {
                    out.print(quote(varAttr));
                    out.print(", _jspx_sout.toString()");
                }
                if (scopeName != null) {
                    out.print(", ");
                    out.print(getScopeConstant(scopeName));
                }
                out.println(");");
            }
            n.setEndJavaLine(out.getJavaLine());
        }

        public void visit(Node.DoBodyAction n) throws JasperException {

            n.setBeginJavaLine(out.getJavaLine());

            // 将标签文件的虚拟页面范围复制到调用页的页面范围
            out.printil(
                "((org.apache.jasper.runtime.JspContextWrapper) this.jspContext).syncBeforeInvoke();");

            // Invoke body
            String varReaderAttr = n.getTextAttribute("varReader");
            String varAttr = n.getTextAttribute("var");
            if (varReaderAttr != null || varAttr != null) {
                out.printil("_jspx_sout = new java.io.StringWriter();");
            } else {
                out.printil("_jspx_sout = null;");
            }
            out.printil("if (getJspBody() != null)");
            out.pushIndent();
            out.printil("getJspBody().invoke(_jspx_sout);");
            out.popIndent();

            // 在适当的范围内保存varReader
            if (varReaderAttr != null || varAttr != null) {
                String scopeName = n.getTextAttribute("scope");
                out.printin("_jspx_page_context.setAttribute(");
                if (varReaderAttr != null) {
                    out.print(quote(varReaderAttr));
                    out.print(
                        ", new java.io.StringReader(_jspx_sout.toString())");
                } else {
                    out.print(quote(varAttr));
                    out.print(", _jspx_sout.toString()");
                }
                if (scopeName != null) {
                    out.print(", ");
                    out.print(getScopeConstant(scopeName));
                }
                out.println(");");
            }

            n.setEndJavaLine(out.getJavaLine());
        }

        public void visit(Node.AttributeGenerator n) throws JasperException {
            Node.CustomTag tag = n.getTag();
            Node.JspAttribute[] attrs = tag.getJspAttributes();
            for (int i = 0; attrs != null && i < attrs.length; i++) {
                if (attrs[i].getName().equals(n.getName())) {
                    out.print(
                        evaluateAttribute(
                            getTagHandlerInfo(tag),
                            attrs[i],
                            tag,
                            null));
                    break;
                }
            }
        }

        private TagHandlerInfo getTagHandlerInfo(Node.CustomTag n)
            throws JasperException {
            Hashtable handlerInfosByShortName =
                (Hashtable)handlerInfos.get(n.getPrefix());
            if (handlerInfosByShortName == null) {
                handlerInfosByShortName = new Hashtable();
                handlerInfos.put(n.getPrefix(), handlerInfosByShortName);
            }
            TagHandlerInfo handlerInfo =
                (TagHandlerInfo)handlerInfosByShortName.get(n.getLocalName());
            if (handlerInfo == null) {
                handlerInfo =
                    new TagHandlerInfo(n, n.getTagHandlerClass(), err);
                handlerInfosByShortName.put(n.getLocalName(), handlerInfo);
            }
            return handlerInfo;
        }

        private void generateTagPlugin(Node.CustomTag n)
            throws JasperException {
            if (n.getAtSTag() != null) {
                n.getAtSTag().visit(this);
            }
            visitBody(n);
            if (n.getAtETag() != null) {
                n.getAtETag().visit(this);
            }
        }

        private void generateCustomStart(
            Node.CustomTag n,
            TagHandlerInfo handlerInfo,
            String tagHandlerVar,
            String tagEvalVar,
            String tagPushBodyCountVar)
            throws JasperException {

            Class tagHandlerClass = handlerInfo.getTagHandlerClass();

            out.printin("//  ");
            out.println(n.getQName());
            n.setBeginJavaLine(out.getJavaLine());

            // 声明 AT_BEGIN 脚本变量
            declareScriptingVars(n, VariableInfo.AT_BEGIN);
            saveScriptingVars(n, VariableInfo.AT_BEGIN);

            String tagHandlerClassName =
                JspUtil.getCanonicalName(tagHandlerClass);
            out.printin(tagHandlerClassName);
            out.print(" ");
            out.print(tagHandlerVar);
            out.print(" = ");
            if (isPoolingEnabled) {
                out.print("(");
                out.print(tagHandlerClassName);
                out.print(") ");
                out.print(n.getTagHandlerPoolName());
                out.print(".get(");
                out.print(tagHandlerClassName);
                out.println(".class);");
            } else {
                out.print("new ");
                out.print(tagHandlerClassName);
                out.println("();");
            }

            generateSetters(n, tagHandlerVar, handlerInfo, false);

            if (n.implementsTryCatchFinally()) {
                out.printin("int[] ");
                out.print(tagPushBodyCountVar);
                out.println(" = new int[] { 0 };");
                out.printil("try {");
                out.pushIndent();
            }
            out.printin("int ");
            out.print(tagEvalVar);
            out.print(" = ");
            out.print(tagHandlerVar);
            out.println(".doStartTag();");

            if (!n.implementsBodyTag()) {
                // 同步 AT_BEGIN 脚本变量
                syncScriptingVars(n, VariableInfo.AT_BEGIN);
            }

            if (!n.hasEmptyBody()) {
                out.printin("if (");
                out.print(tagEvalVar);
                out.println(" != javax.servlet.jsp.tagext.Tag.SKIP_BODY) {");
                out.pushIndent();

                // 声明 NESTED 脚本变量
                declareScriptingVars(n, VariableInfo.NESTED);
                saveScriptingVars(n, VariableInfo.NESTED);

                if (n.implementsBodyTag()) {
                    out.printin("if (");
                    out.print(tagEvalVar);
                    out.println(
                        " != javax.servlet.jsp.tagext.Tag.EVAL_BODY_INCLUDE) {");
                    // Assume EVAL_BODY_BUFFERED
                    out.pushIndent();
                    out.printil("out = _jspx_page_context.pushBody();");
                    if (n.implementsTryCatchFinally()) {
                        out.printin(tagPushBodyCountVar);
                        out.println("[0]++;");
                    } else if (pushBodyCountVar != null) {
                        out.printin(pushBodyCountVar);
                        out.println("[0]++;");
                    }
                    out.printin(tagHandlerVar);
                    out.println(
                        ".setBodyContent((javax.servlet.jsp.tagext.BodyContent) out);");
                    out.printin(tagHandlerVar);
                    out.println(".doInitBody();");

                    out.popIndent();
                    out.printil("}");

                    // 同步 AT_BEGIN 和 NESTED 脚本变量
                    syncScriptingVars(n, VariableInfo.AT_BEGIN);
                    syncScriptingVars(n, VariableInfo.NESTED);

                } else {
                    // 同步  NESTED 脚本变量
                    syncScriptingVars(n, VariableInfo.NESTED);
                }

                if (n.implementsIterationTag()) {
                    out.printil("do {");
                    out.pushIndent();
                }
            }
            // 映射处理自定义标签的开始的Java行到这个标签的JSP行
            n.setEndJavaLine(out.getJavaLine());
        }

        private void generateCustomEnd(
            Node.CustomTag n,
            String tagHandlerVar,
            String tagEvalVar,
            String tagPushBodyCountVar) {

            if (!n.hasEmptyBody()) {
                if (n.implementsIterationTag()) {
                    out.printin("int evalDoAfterBody = ");
                    out.print(tagHandlerVar);
                    out.println(".doAfterBody();");

                    // 同步 AT_BEGIN and NESTED 脚本变量
                    syncScriptingVars(n, VariableInfo.AT_BEGIN);
                    syncScriptingVars(n, VariableInfo.NESTED);

                    out.printil(
                        "if (evalDoAfterBody != javax.servlet.jsp.tagext.BodyTag.EVAL_BODY_AGAIN)");
                    out.pushIndent();
                    out.printil("break;");
                    out.popIndent();

                    out.popIndent();
                    out.printil("} while (true);");
                }

                restoreScriptingVars(n, VariableInfo.NESTED);

                if (n.implementsBodyTag()) {
                    out.printin("if (");
                    out.print(tagEvalVar);
                    out.println(
                        " != javax.servlet.jsp.tagext.Tag.EVAL_BODY_INCLUDE)");
                    out.pushIndent();
                    out.printil("out = _jspx_page_context.popBody();");
                    if (n.implementsTryCatchFinally()) {
                        out.printin(tagPushBodyCountVar);
                        out.println("[0]--;");
                    } else if (pushBodyCountVar != null) {
                        out.printin(pushBodyCountVar);
                        out.println("[0]--;");
                    }
                    out.popIndent();
                }

                out.popIndent(); // EVAL_BODY
                out.printil("}");
            }

            out.printin("if (");
            out.print(tagHandlerVar);
            out.println(
                ".doEndTag() == javax.servlet.jsp.tagext.Tag.SKIP_PAGE) {");
            out.pushIndent();
            if(!n.implementsTryCatchFinally()) {
                if(isPoolingEnabled) {
                    out.printin(n.getTagHandlerPoolName());
                    out.print(".reuse(");
                    out.print(tagHandlerVar);
                    out.println(");");
                } else {
                    out.printin(tagHandlerVar);
                    out.println(".release();");
                }
            }
            if (isTagFile || isFragment) {
                out.printil("throw new SkipPageException();");
            } else {
                out.printil((methodNesting > 0) ? "return true;" : "return;");
            }
            out.popIndent();
            out.printil("}");
            // 同步 AT_BEGIN 脚本变量
            syncScriptingVars(n, VariableInfo.AT_BEGIN);

            // TryCatchFinally
            if (n.implementsTryCatchFinally()) {
                out.popIndent(); // try
                out.printil("} catch (Throwable _jspx_exception) {");
                out.pushIndent();

                out.printin("while (");
                out.print(tagPushBodyCountVar);
                out.println("[0]-- > 0)");
                out.pushIndent();
                out.printil("out = _jspx_page_context.popBody();");
                out.popIndent();

                out.printin(tagHandlerVar);
                out.println(".doCatch(_jspx_exception);");
                out.popIndent();
                out.printil("} finally {");
                out.pushIndent();
                out.printin(tagHandlerVar);
                out.println(".doFinally();");
            }

            if (isPoolingEnabled) {
                out.printin(n.getTagHandlerPoolName());
                out.print(".reuse(");
                out.print(tagHandlerVar);
                out.println(");");
            } else {
                out.printin(tagHandlerVar);
                out.println(".release();");
            }

            if (n.implementsTryCatchFinally()) {
                out.popIndent();
                out.printil("}");
            }

            //声明并同步 AT_END 脚本变量(必须在 try/catch/finally 块之外进行)
            declareScriptingVars(n, VariableInfo.AT_END);
            syncScriptingVars(n, VariableInfo.AT_END);

            restoreScriptingVars(n, VariableInfo.AT_BEGIN);
        }

        private void generateCustomDoTag(
            Node.CustomTag n,
            TagHandlerInfo handlerInfo,
            String tagHandlerVar)
            throws JasperException {

            Class tagHandlerClass = handlerInfo.getTagHandlerClass();

            n.setBeginJavaLine(out.getJavaLine());
            out.printin("//  ");
            out.println(n.getQName());

            // 声明 AT_BEGIN 脚本变量
            declareScriptingVars(n, VariableInfo.AT_BEGIN);
            saveScriptingVars(n, VariableInfo.AT_BEGIN);

            String tagHandlerClassName =
                JspUtil.getCanonicalName(tagHandlerClass);
            out.printin(tagHandlerClassName);
            out.print(" ");
            out.print(tagHandlerVar);
            out.print(" = ");
            out.print("new ");
            out.print(tagHandlerClassName);
            out.println("();");

            generateSetters(n, tagHandlerVar, handlerInfo, true);

            // 设置主体
            if (findJspBody(n) == null) {
                /*
                 * 封装JspFragment中的自定义标签调用的主体, 并将其传递到标签处理程序的 setJspBody(), 除非标签主体是空的
                 */
                if (!n.hasEmptyBody()) {
                    out.printin(tagHandlerVar);
                    out.print(".setJspBody(");
                    generateJspFragment(n, tagHandlerVar);
                    out.println(");");
                }
            } else {
                /*
                 * 标签的主体是<jsp:body> 元素的主体.
                 * 该元素的访问方法将封装JspFragment中该元素的主体, 并将其传递到标签处理程序的 setJspBody()
                 */
                String tmpTagHandlerVar = simpleTagHandlerVar;
                simpleTagHandlerVar = tagHandlerVar;
                boolean tmpIsSimpleTagHandler = isSimpleTagHandler;
                isSimpleTagHandler = true;
                visitBody(n);
                simpleTagHandlerVar = tmpTagHandlerVar;
                isSimpleTagHandler = tmpIsSimpleTagHandler;
            }

            out.printin(tagHandlerVar);
            out.println(".doTag();");

            restoreScriptingVars(n, VariableInfo.AT_BEGIN);

            // 同步 AT_BEGIN 脚本变量
            syncScriptingVars(n, VariableInfo.AT_BEGIN);

            // 声明并同步 AT_END 脚本变量
            declareScriptingVars(n, VariableInfo.AT_END);
            syncScriptingVars(n, VariableInfo.AT_END);

            n.setEndJavaLine(out.getJavaLine());
        }

        private void declareScriptingVars(Node.CustomTag n, int scope) {

            Vector vec = n.getScriptingVars(scope);
            if (vec != null) {
                for (int i = 0; i < vec.size(); i++) {
                    Object elem = vec.elementAt(i);
                    if (elem instanceof VariableInfo) {
                        VariableInfo varInfo = (VariableInfo)elem;
                        if (varInfo.getDeclare()) {
                            out.printin(varInfo.getClassName());
                            out.print(" ");
                            out.print(varInfo.getVarName());
                            out.println(" = null;");
                        }
                    } else {
                        TagVariableInfo tagVarInfo = (TagVariableInfo)elem;
                        if (tagVarInfo.getDeclare()) {
                            String varName = tagVarInfo.getNameGiven();
                            if (varName == null) {
                                varName =
                                    n.getTagData().getAttributeString(
                                        tagVarInfo.getNameFromAttribute());
                            } else if (
                                tagVarInfo.getNameFromAttribute() != null) {
                                // alias
                                continue;
                            }
                            out.printin(tagVarInfo.getClassName());
                            out.print(" ");
                            out.print(varName);
                            out.println(" = null;");
                        }
                    }
                }
            }
        }

        /*
         * 此方法被称为自定义标签的开始元素的一部分.
         *
         * 如果给定的自定义标签具有自定义嵌套级别大于 0, 保存它的脚本变量的当前值 到临时变量, 因此，这些值可以在标签的结束元素中恢复.
         * 这种方式, 脚本变量可以由给定的标签同步而不影响它们的原始值.
         */
        private void saveScriptingVars(Node.CustomTag n, int scope) {
            if (n.getCustomNestingLevel() == 0) {
                return;
            }

            TagVariableInfo[] tagVarInfos = n.getTagVariableInfos();
            VariableInfo[] varInfos = n.getVariableInfos();
            if ((varInfos.length == 0) && (tagVarInfos.length == 0)) {
                return;
            }

            if (varInfos.length > 0) {
                for (int i = 0; i < varInfos.length; i++) {
                    if (varInfos[i].getScope() != scope)
                        continue;
                    // 如果已声明脚本变量, 跳过, 用于保存和恢复它.
                    if (n.getScriptingVars(scope).contains(varInfos[i]))
                        continue;
                    String varName = varInfos[i].getVarName();
                    String tmpVarName =
                        "_jspx_" + varName + "_" + n.getCustomNestingLevel();
                    out.printin(tmpVarName);
                    out.print(" = ");
                    out.print(varName);
                    out.println(";");
                }
            } else {
                for (int i = 0; i < tagVarInfos.length; i++) {
                    if (tagVarInfos[i].getScope() != scope)
                        continue;
                    // 如果已声明脚本变量, 跳过, 用于保存和恢复它.
                    if (n.getScriptingVars(scope).contains(tagVarInfos[i]))
                        continue;
                    String varName = tagVarInfos[i].getNameGiven();
                    if (varName == null) {
                        varName =
                            n.getTagData().getAttributeString(
                                tagVarInfos[i].getNameFromAttribute());
                    } else if (tagVarInfos[i].getNameFromAttribute() != null) {
                        // alias
                        continue;
                    }
                    String tmpVarName =
                        "_jspx_" + varName + "_" + n.getCustomNestingLevel();
                    out.printin(tmpVarName);
                    out.print(" = ");
                    out.print(varName);
                    out.println(";");
                }
            }
        }

        /*
         * 此方法被称为自定义标签的结束元素的一部分.
         *
         * 如果给定的自定义标签具有自定义嵌套级别大于 0, 恢复它的脚本变量 到在标签开始元素中保存的原始值.
         */
        private void restoreScriptingVars(Node.CustomTag n, int scope) {
            if (n.getCustomNestingLevel() == 0) {
                return;
            }

            TagVariableInfo[] tagVarInfos = n.getTagVariableInfos();
            VariableInfo[] varInfos = n.getVariableInfos();
            if ((varInfos.length == 0) && (tagVarInfos.length == 0)) {
                return;
            }

            if (varInfos.length > 0) {
                for (int i = 0; i < varInfos.length; i++) {
                    if (varInfos[i].getScope() != scope)
                        continue;
                    // 如果已声明脚本变量, 跳过, 用于保存和恢复它.
                    if (n.getScriptingVars(scope).contains(varInfos[i]))
                        continue;
                    String varName = varInfos[i].getVarName();
                    String tmpVarName =
                        "_jspx_" + varName + "_" + n.getCustomNestingLevel();
                    out.printin(varName);
                    out.print(" = ");
                    out.print(tmpVarName);
                    out.println(";");
                }
            } else {
                for (int i = 0; i < tagVarInfos.length; i++) {
                    if (tagVarInfos[i].getScope() != scope)
                        continue;
                    // 如果已声明脚本变量, 跳过, 用于保存和恢复它.
                    if (n.getScriptingVars(scope).contains(tagVarInfos[i]))
                        continue;
                    String varName = tagVarInfos[i].getNameGiven();
                    if (varName == null) {
                        varName =
                            n.getTagData().getAttributeString(
                                tagVarInfos[i].getNameFromAttribute());
                    } else if (tagVarInfos[i].getNameFromAttribute() != null) {
                        // alias
                        continue;
                    }
                    String tmpVarName =
                        "_jspx_" + varName + "_" + n.getCustomNestingLevel();
                    out.printin(varName);
                    out.print(" = ");
                    out.print(tmpVarName);
                    out.println(";");
                }
            }
        }

        /*
         * 在给定范围内同步给定自定义标签的脚本变量.
         */
        private void syncScriptingVars(Node.CustomTag n, int scope) {
            TagVariableInfo[] tagVarInfos = n.getTagVariableInfos();
            VariableInfo[] varInfos = n.getVariableInfos();

            if ((varInfos.length == 0) && (tagVarInfos.length == 0)) {
                return;
            }

            if (varInfos.length > 0) {
                for (int i = 0; i < varInfos.length; i++) {
                    if (varInfos[i].getScope() == scope) {
                        out.printin(varInfos[i].getVarName());
                        out.print(" = (");
                        out.print(varInfos[i].getClassName());
                        out.print(") _jspx_page_context.findAttribute(");
                        out.print(quote(varInfos[i].getVarName()));
                        out.println(");");
                    }
                }
            } else {
                for (int i = 0; i < tagVarInfos.length; i++) {
                    if (tagVarInfos[i].getScope() == scope) {
                        String name = tagVarInfos[i].getNameGiven();
                        if (name == null) {
                            name =
                                n.getTagData().getAttributeString(
                                    tagVarInfos[i].getNameFromAttribute());
                        } else if (
                            tagVarInfos[i].getNameFromAttribute() != null) {
                            // alias
                            continue;
                        }
                        out.printin(name);
                        out.print(" = (");
                        out.print(tagVarInfos[i].getClassName());
                        out.print(") _jspx_page_context.findAttribute(");
                        out.print(quote(name));
                        out.println(");");
                    }
                }
            }
        }

        /*
         * 创建标签变量名, 通过给定的前缀名和shortName和endcoded 使其成为有效的java标识符字符串.
         */
        private String createTagVarName(
            String fullName,
            String prefix,
            String shortName) {

            String varName;
            synchronized (tagVarNumbers) {
                varName = prefix + "_" + shortName + "_";
                if (tagVarNumbers.get(fullName) != null) {
                    Integer i = (Integer)tagVarNumbers.get(fullName);
                    varName = varName + i.intValue();
                    tagVarNumbers.put(fullName, new Integer(i.intValue() + 1));
                } else {
                    tagVarNumbers.put(fullName, new Integer(1));
                    varName = varName + "0";
                }
            }
            return JspUtil.makeXmlJavaIdentifier(varName);
        }

        private String evaluateAttribute(
            TagHandlerInfo handlerInfo,
            Node.JspAttribute attr,
            Node.CustomTag n,
            String tagHandlerVar)
            throws JasperException {

            String attrValue = attr.getValue();
            if (attrValue == null) {
                if (attr.isNamedAttribute()) {
                    if (n.checkIfAttributeIsJspFragment(attr.getName())) {
                        // XXX - 这里不需要生成临时变量
                        attrValue =
                            generateNamedAttributeJspFragment(
                                attr.getNamedAttributeNode(),
                                tagHandlerVar);
                    } else {
                        attrValue =
                            generateNamedAttributeValue(
                                attr.getNamedAttributeNode());
                    }
                } else {
                    return null;
                }
            }

            String localName = attr.getLocalName();

            Method m = null;
            Class[] c = null;
            if (attr.isDynamic()) {
                c = OBJECT_CLASS;
            } else {
                m = handlerInfo.getSetterMethod(localName);
                if (m == null) {
                    err.jspError(
                        n,
                        "jsp.error.unable.to_find_method",
                        attr.getName());
                }
                c = m.getParameterTypes();
                // XXX assert(c.length > 0)
            }

            if (attr.isExpression()) {
                // Do nothing
            } else if (attr.isNamedAttribute()) {
                if (!n.checkIfAttributeIsJspFragment(attr.getName())
                    && !attr.isDynamic()) {
                    attrValue =
                        convertString(
                            c[0],
                            attrValue,
                            localName,
                            handlerInfo.getPropertyEditorClass(localName),
                            true);
                }
            } else if (attr.isELInterpreterInput()) {
                // 通过表达式解释器运行attrValue
                boolean replaceESC = attrValue.indexOf(Constants.ESC) > 0;
                attrValue =
                    JspUtil.interpreterCall(
                        this.isTagFile,
                        attrValue,
                        c[0],
                        attr.getEL().getMapName(),
                        false);
                // XXX hack: Replace ESC with '$'
                if (replaceESC) {
                    attrValue =
                        "("
                            + attrValue
                            + ").replace("
                            + Constants.ESCStr
                            + ", '$')";
                }
            } else {
                attrValue =
                    convertString(
                        c[0],
                        attrValue,
                        localName,
                        handlerInfo.getPropertyEditorClass(localName),
                        false);
            }
            return attrValue;
        }

        /**
         * 生成代码以创建别名变量的映射
         * @return 映射名称
         */
        private String generateAliasMap(Node.CustomTag n, String tagHandlerVar) throws JasperException {

            TagVariableInfo[] tagVars = n.getTagVariableInfos();
            String aliasMapVar = null;

            boolean aliasSeen = false;
            for (int i = 0; i < tagVars.length; i++) {

                String nameFrom = tagVars[i].getNameFromAttribute();
                if (nameFrom != null) {
                    String aliasedName = n.getAttributeValue(nameFrom);
                    if (aliasedName == null)
                        continue;

                    if (!aliasSeen) {
                        out.printin("java.util.HashMap ");
                        aliasMapVar = tagHandlerVar + "_aliasMap";
                        out.print(aliasMapVar);
                        out.println(" = new java.util.HashMap();");
                        aliasSeen = true;
                    }
                    out.printin(aliasMapVar);
                    out.print(".put(");
                    out.print(quote(tagVars[i].getNameGiven()));
                    out.print(", ");
                    out.print(quote(aliasedName));
                    out.println(");");
                }
            }
            return aliasMapVar;
        }

        private void generateSetters(
            Node.CustomTag n,
            String tagHandlerVar,
            TagHandlerInfo handlerInfo,
            boolean simpleTag)
            throws JasperException {

            // 设置上下文
            if (simpleTag) {
                // 生成别名 map
                String aliasMapVar = null;
                if (n.isTagFile()) {
                    aliasMapVar = generateAliasMap(n, tagHandlerVar);
                }
                out.printin(tagHandlerVar);
                if (aliasMapVar == null) {
                    out.println(".setJspContext(_jspx_page_context);");
                } else {
                    out.print(".setJspContext(_jspx_page_context, ");
                    out.print(aliasMapVar);
                    out.println(");");
                }
            } else {
                out.printin(tagHandlerVar);
                out.println(".setPageContext(_jspx_page_context);");
            }

            // Set parent
            if (!simpleTag) {
                out.printin(tagHandlerVar);
                out.print(".setParent(");
                if (parent != null) {
                    if (isSimpleTagParent) {
                        out.print("new javax.servlet.jsp.tagext.TagAdapter(");
                        out.print("(javax.servlet.jsp.tagext.SimpleTag) ");
                        out.print(parent);
                        out.println("));");
                    } else {
                        out.print("(javax.servlet.jsp.tagext.Tag) ");
                        out.print(parent);
                        out.println(");");
                    }
                } else {
                    out.println("null);");
                }
            } else {
                // setParent()方法不需要被调用, 如果传递的值是null, 因为SimpleTag 实例不重用
                if (parent != null) {
                    out.printin(tagHandlerVar);
                    out.print(".setParent(");
                    out.print(parent);
                    out.println(");");
                }
            }

            Node.JspAttribute[] attrs = n.getJspAttributes();
            for (int i = 0; attrs != null && i < attrs.length; i++) {
                String attrValue =
                    evaluateAttribute(handlerInfo, attrs[i], n, tagHandlerVar);

                if (attrs[i].isDynamic()) {
                    out.printin(tagHandlerVar);
                    out.print(".");
                    out.print("setDynamicAttribute(");
                    String uri = attrs[i].getURI();
                    if ("".equals(uri) || (uri == null)) {
                        out.print("null");
                    } else {
                        out.print("\"" + attrs[i].getURI() + "\"");
                    }
                    out.print(", \"");
                    out.print(attrs[i].getLocalName());
                    out.print("\", ");
                    out.print(attrValue);
                    out.println(");");
                } else {
                    out.printin(tagHandlerVar);
                    out.print(".");
                    out.print(
                        handlerInfo
                            .getSetterMethod(attrs[i].getLocalName())
                            .getName());
                    out.print("(");
                    out.print(attrValue);
                    out.println(");");
                }
            }
        }

        /*
         * @param c 要强制给定字符串的目标类
         * @param s 字符串值
         * @param attrName 提供其值的属性的名称
         * @param propEditorClass 给定属性的属性编辑器
         * @param isNamedAttribute true 如果给定属性是一个命名属性(使用 jsp:attribute 标准行为指定), 否则false
         */
        private String convertString(
            Class c,
            String s,
            String attrName,
            Class propEditorClass,
            boolean isNamedAttribute)
            throws JasperException {

            String quoted = s;
            if (!isNamedAttribute) {
                quoted = quote(s);
            }

            if (propEditorClass != null) {
                String className = JspUtil.getCanonicalName(c);
                return "("
                    + className
                    + ")org.apache.jasper.runtime.JspRuntimeLibrary.getValueFromBeanInfoPropertyEditor("
                    + className
                    + ".class, \""
                    + attrName
                    + "\", "
                    + quoted
                    + ", "
                    + JspUtil.getCanonicalName(propEditorClass)
                    + ".class)";
            } else if (c == String.class) {
                return quoted;
            } else if (c == boolean.class) {
                return JspUtil.coerceToPrimitiveBoolean(s, isNamedAttribute);
            } else if (c == Boolean.class) {
                return JspUtil.coerceToBoolean(s, isNamedAttribute);
            } else if (c == byte.class) {
                return JspUtil.coerceToPrimitiveByte(s, isNamedAttribute);
            } else if (c == Byte.class) {
                return JspUtil.coerceToByte(s, isNamedAttribute);
            } else if (c == char.class) {
                return JspUtil.coerceToChar(s, isNamedAttribute);
            } else if (c == Character.class) {
                return JspUtil.coerceToCharacter(s, isNamedAttribute);
            } else if (c == double.class) {
                return JspUtil.coerceToPrimitiveDouble(s, isNamedAttribute);
            } else if (c == Double.class) {
                return JspUtil.coerceToDouble(s, isNamedAttribute);
            } else if (c == float.class) {
                return JspUtil.coerceToPrimitiveFloat(s, isNamedAttribute);
            } else if (c == Float.class) {
                return JspUtil.coerceToFloat(s, isNamedAttribute);
            } else if (c == int.class) {
                return JspUtil.coerceToInt(s, isNamedAttribute);
            } else if (c == Integer.class) {
                return JspUtil.coerceToInteger(s, isNamedAttribute);
            } else if (c == short.class) {
                return JspUtil.coerceToPrimitiveShort(s, isNamedAttribute);
            } else if (c == Short.class) {
                return JspUtil.coerceToShort(s, isNamedAttribute);
            } else if (c == long.class) {
                return JspUtil.coerceToPrimitiveLong(s, isNamedAttribute);
            } else if (c == Long.class) {
                return JspUtil.coerceToLong(s, isNamedAttribute);
            } else if (c == Object.class) {
                return "new String(" + quoted + ")";
            } else {
                String className = JspUtil.getCanonicalName(c);
                return "("
                    + className
                    + ")org.apache.jasper.runtime.JspRuntimeLibrary.getValueFromPropertyEditorManager("
                    + className
                    + ".class, \""
                    + attrName
                    + "\", "
                    + quoted
                    + ")";
            }
        }

        /*
         * 转换范围字符串表示形式, 其值可能是 "page", "request", "session", "application", 到对应的范围常量.
         */
        private String getScopeConstant(String scope) {
            String scopeName = "PageContext.PAGE_SCOPE"; // 默认 page

            if ("request".equals(scope)) {
                scopeName = "PageContext.REQUEST_SCOPE";
            } else if ("session".equals(scope)) {
                scopeName = "PageContext.SESSION_SCOPE";
            } else if ("application".equals(scope)) {
                scopeName = "PageContext.APPLICATION_SCOPE";
            }

            return scopeName;
        }

        /**
         * 产生匿名内部类JspFragment, 作为一个参数传递给 SimpleTag.setJspBody().
         */
        private void generateJspFragment(Node n, String tagHandlerVar)
            throws JasperException {
            // XXX - 这里的一个可能的优化是检查看看, 如果父节点的唯一子节点是TemplateText. 
            // 如果是这样的话, 我们知道不会有任何参数, 这样我们就可以产生低开销的 JspFragment 这正好呼应了它的主体.
        	// 这个片段的实现可以来自 org.apache.jasper.runtime 包作为一个支持类.
            FragmentHelperClass.Fragment fragment =
                fragmentHelperClass.openFragment(
                    n,
                    tagHandlerVar,
                    methodNesting);
            ServletWriter outSave = out;
            out = fragment.getGenBuffer().getOut();
            String tmpParent = parent;
            parent = "_jspx_parent";
            boolean isSimpleTagParentSave = isSimpleTagParent;
            isSimpleTagParent = true;
            boolean tmpIsFragment = isFragment;
            isFragment = true;
            String pushBodyCountVarSave = pushBodyCountVar;
            if (pushBodyCountVar != null) {
                // 使用固定名称进行计数, 简化代码生成
                pushBodyCountVar = "_jspx_push_body_count";
            }
            visitBody(n);
            out = outSave;
            parent = tmpParent;
            isSimpleTagParent = isSimpleTagParentSave;
            isFragment = tmpIsFragment;
            pushBodyCountVar = pushBodyCountVarSave;
            fragmentHelperClass.closeFragment(fragment, methodNesting);
            // XXX - 需要修改 pageContext 为 jspContext, 如果我们不在pageContext定义的地方(在片段或标签文件中.
            out.print(
                "new "
                    + fragmentHelperClass.getClassName()
                    + "( "
                    + fragment.getId()
                    + ", _jspx_page_context, "
                    + tagHandlerVar
                    + ", "
                    + pushBodyCountVar
                    + ")");
        }

        /**
         * 生成获取给定命名属性的运行时值所需的代码.
         *
         * @return 存储在结果中的临时变量的名称.
         */
        public String generateNamedAttributeValue(Node.NamedAttribute n)
            throws JasperException {

            String varName = n.getTemporaryVariableName();

            // 如果这个命名属性节点的唯一主体元素是模板文本, 不需要生成额外的调用 pushBody 和 popBody.
            // 也许我们可以通过去掉临时变量来进一步优化这里, 但在现实中它看起来像javac为我们做了这些.
            Node.Nodes body = n.getBody();
            if (body != null) {
                boolean templateTextOptimization = false;
                if (body.size() == 1) {
                    Node bodyElement = body.getNode(0);
                    if (bodyElement instanceof Node.TemplateText) {
                        templateTextOptimization = true;
                        out.printil(
                            "String "
                                + varName
                                + " = "
                                + quote(
                                    new String(
                                        ((Node.TemplateText)bodyElement)
                                            .getText()))
                                + ";");
                    }
                }

                // XXX - 另一种可能的优化是孤立EL表达式(不需要在这里pushBody).

                if (!templateTextOptimization) {
                    out.printil("out = _jspx_page_context.pushBody();");
                    visitBody(n);
                    out.printil(
                        "String "
                            + varName
                            + " = "
                            + "((javax.servlet.jsp.tagext.BodyContent)"
                            + "out).getString();");
                    out.printil("out = _jspx_page_context.popBody();");
                }
            } else {
                // 空主体必须被视为""
                out.printil("String " + varName + " = \"\";");
            }

            return varName;
        }

        /**
         * 类似 generateNamedAttributeValue, 但在内部创建了一个 JspFragment.
         *
         * @param n 命名属性的父节点
         * @param tagHandlerVar 标签处理程序的变量存储的地方, 所以片段知道它的父标签.
         * @return 存储临时变量的片段名称.
         */
        public String generateNamedAttributeJspFragment(
            Node.NamedAttribute n,
            String tagHandlerVar)
            throws JasperException {
            String varName = n.getTemporaryVariableName();

            out.printin(
                "javax.servlet.jsp.tagext.JspFragment " + varName + " = ");
            generateJspFragment(n, tagHandlerVar);
            out.println(";");

            return varName;
        }
    }

    private static void generateLocalVariables(ServletWriter out, Node n)
        throws JasperException {
        Node.ChildInfo ci;
        if (n instanceof Node.CustomTag) {
            ci = ((Node.CustomTag)n).getChildInfo();
        } else if (n instanceof Node.JspBody) {
            ci = ((Node.JspBody)n).getChildInfo();
        } else if (n instanceof Node.NamedAttribute) {
            ci = ((Node.NamedAttribute)n).getChildInfo();
        } else {
            // 无法访问错误，因为此方法是静态的, 但至少标志着一个错误.
            throw new JasperException("Unexpected Node Type");
            //err.getString(
            //    "jsp.error.internal.unexpected_node_type" ) );
        }

        if (ci.hasUseBean()) {
            out.printil("HttpSession session = _jspx_page_context.getSession();");
            out.printil(
                "ServletContext application = _jspx_page_context.getServletContext();");
        }
        if (ci.hasUseBean()
            || ci.hasIncludeAction()
            || ci.hasSetProperty()
            || ci.hasParamAction()) {
            out.printil(
                "HttpServletRequest request = (HttpServletRequest)_jspx_page_context.getRequest();");
        }
        if (ci.hasIncludeAction()) {
            out.printil(
                "HttpServletResponse response = (HttpServletResponse)_jspx_page_context.getResponse();");
        }
    }

    /**
     * 常见的后部分, servlet和标签文件共享.
     */
    private void genCommonPostamble() {
        // 追加缓冲区中生成的任何方法.
        for (int i = 0; i < methodsBuffered.size(); i++) {
            GenBuffer methodBuffer = (GenBuffer)methodsBuffered.get(i);
            methodBuffer.adjustJavaLines(out.getJavaLine() - 1);
            out.printMultiLn(methodBuffer.toString());
        }

        // 添加辅助类
        if (fragmentHelperClass.isUsed()) {
            fragmentHelperClass.generatePostamble();
            fragmentHelperClass.adjustJavaLines(out.getJavaLine() - 1);
            out.printMultiLn(fragmentHelperClass.toString());
        }

        // 添加char数组声明
        if (charArrayBuffer != null) {
            out.printMultiLn(charArrayBuffer.toString());
        }

        // 关闭类定义
        out.popIndent();
        out.printil("}");
    }

    /**
     * 生成servlet静态部分的结束部分.
     */
    private void generatePostamble(Node.Nodes page) {
        out.popIndent();
        out.printil("} catch (Throwable t) {");
        out.pushIndent();
        out.printil(
            "if (!(t instanceof SkipPageException)){");
        out.pushIndent();
        out.printil("out = _jspx_out;");
        out.printil("if (out != null && out.getBufferSize() != 0)");
        out.pushIndent();
        out.printil("out.clearBuffer();");
        out.popIndent();

        out.printil(
            "if (_jspx_page_context != null) _jspx_page_context.handlePageException(t);");
        out.popIndent();
        out.printil("}");
        out.popIndent();
        out.printil("} finally {");
        out.pushIndent();

        out.printil(
            "if (_jspxFactory != null) _jspxFactory.releasePageContext(_jspx_page_context);");

        out.popIndent();
        out.printil("}");

        // 关闭服务方法
        out.popIndent();
        out.printil("}");

        // 生成的方法, 帮助类, 等.
        genCommonPostamble();
    }

    Generator(ServletWriter out, Compiler compiler) {
        this.out = out;
        methodsBuffered = new ArrayList();
        charArrayBuffer = null;
        err = compiler.getErrorDispatcher();
        ctxt = compiler.getCompilationContext();
        fragmentHelperClass =
            new FragmentHelperClass(ctxt.getServletClassName() + "Helper");
        pageInfo = compiler.getPageInfo();

        /*
         * 临时. 如果一个JSP 页面使用页面指令的"extends"属性, 生成的servlet类的_jspInit()方法不会被调用
         * (默认，它只是被那些生成的继承HttpJspBase的servlet调用), 造成标签处理池不被初始化，并导致NPE.
         * JSP规范需要说明容器是否可以重写 init() 和 destroy(). 现在, 禁用使用"extends"的页面的标签池.
         */
        if (pageInfo.getExtends(false) == null) {
            isPoolingEnabled = ctxt.getOptions().isPoolingEnabled();
        } else {
            isPoolingEnabled = false;
        }
        beanInfo = pageInfo.getBeanRepository();
        breakAtLF = ctxt.getOptions().getMappedFile();
        if (isPoolingEnabled) {
            tagHandlerPoolNames = new Vector();
        }
    }

    /**
     * 生成器的主要入口.
     * @param out servlet输出 writer
     * @param compiler 编译器
     * @param page 输入页面
     */
    public static void generate(
        ServletWriter out,
        Compiler compiler,
        Node.Nodes page)
        throws JasperException {

        Generator gen = new Generator(out, compiler);

        if (gen.isPoolingEnabled) {
            gen.compileTagHandlerPoolList(page);
        }
        if (gen.ctxt.isTagFile()) {
            JasperTagInfo tagInfo = (JasperTagInfo)gen.ctxt.getTagInfo();
            gen.generateTagHandlerPreamble(tagInfo, page);

            if (gen.ctxt.isPrototypeMode()) {
                return;
            }

            gen.generateXmlProlog(page);
            gen.fragmentHelperClass.generatePreamble();
            page.visit(
                gen.new GenerateVisitor(
                    gen.ctxt.isTagFile(),
                    out,
                    gen.methodsBuffered,
                    gen.fragmentHelperClass,
                    gen.ctxt.getClassLoader(),
                    tagInfo));
            gen.generateTagHandlerPostamble(tagInfo);
        } else {
            gen.generatePreamble(page);
            gen.generateXmlProlog(page);
            gen.fragmentHelperClass.generatePreamble();
            page.visit(
                gen.new GenerateVisitor(
                    gen.ctxt.isTagFile(),
                    out,
                    gen.methodsBuffered,
                    gen.fragmentHelperClass,
                    gen.ctxt.getClassLoader(),
                    null));
            gen.generatePostamble(page);
        }
    }

    /*
     * 生成标签处理程序前导代码.
     */
    private void generateTagHandlerPreamble(
        JasperTagInfo tagInfo,
        Node.Nodes tag)
        throws JasperException {

        // 生成包的声明
        String className = tagInfo.getTagClassName();
        int lastIndex = className.lastIndexOf('.');
        if (lastIndex != -1) {
            String pkgName = className.substring(0, lastIndex);
            genPreamblePackage(pkgName);
            className = className.substring(lastIndex + 1);
        }

        // 生成 import
        genPreambleImports();

        // 生成类声明
        out.printin("public final class ");
        out.println(className);
        out.printil("    extends javax.servlet.jsp.tagext.SimpleTagSupport");
        out.printin(
            "    implements org.apache.jasper.runtime.JspSourceDependent");
        if (tagInfo.hasDynamicAttributes()) {
            out.println(",");
            out.printin(
                "               javax.servlet.jsp.tagext.DynamicAttributes");
        }
        out.println(" {");
        out.println();
        out.pushIndent();

        /*
         * 类主体开始
         */
        generateDeclarations(tag);

        // Static 初始化
        genPreambleStaticInitializers();

        out.printil("private JspContext jspContext;");

        // 声明writer用于保存分段/主体调用的结果, 如果 'varReader' 或 'var'属性被指定
        out.printil("private java.io.Writer _jspx_sout;");

        // 类变量声明
        genPreambleClassVariableDeclarations(tagInfo.getTagName());

        generateSetJspContext(tagInfo);

        // 标签处理程序特定声明
        generateTagHandlerAttributes(tagInfo);
        if (tagInfo.hasDynamicAttributes())
            generateSetDynamicAttribute();

        // Methods here
        genPreambleMethods();

        // 现在doTag() 方法
        out.printil(
            "public void doTag() throws JspException, java.io.IOException {");

        if (ctxt.isPrototypeMode()) {
            out.printil("}");
            out.popIndent();
            out.printil("}");
            return;
        }

        out.pushIndent();

        /*
         * 根据规范, 'pageContext'必须让标签文件中的隐式对象不可用.
         * 声明 _jspx_page_context, 所以可以与JSP共享代码生成器.
         */
        out.printil("PageContext _jspx_page_context = (PageContext)jspContext;");

        // 声明隐式对象.
        out.printil(
            "HttpServletRequest request = "
                + "(HttpServletRequest) _jspx_page_context.getRequest();");
        out.printil(
            "HttpServletResponse response = "
                + "(HttpServletResponse) _jspx_page_context.getResponse();");
        out.printil("HttpSession session = _jspx_page_context.getSession();");
        out.printil(
            "ServletContext application = _jspx_page_context.getServletContext();");
        out.printil("ServletConfig config = _jspx_page_context.getServletConfig();");
        out.printil("JspWriter out = jspContext.getOut();");
        if (isPoolingEnabled && !tagHandlerPoolNames.isEmpty()) {
            out.printil("_jspInit(config);");
        }
        generatePageScopedVariables(tagInfo);

        declareTemporaryScriptingVars(tag);
        out.println();

        out.printil("try {");
        out.pushIndent();
    }

    private void generateTagHandlerPostamble(TagInfo tagInfo) {
        out.popIndent();

        // 必须捕获 Throwable,因为一个经典的标签处理帮助方法被声明抛出一个 Throwable.
        out.printil("} catch( Throwable t ) {");
        out.pushIndent();
        out.printil("if( t instanceof SkipPageException )");
        out.printil("    throw (SkipPageException) t;");
        out.printil("if( t instanceof java.io.IOException )");
        out.printil("    throw (java.io.IOException) t;");
        out.printil("if( t instanceof IllegalStateException )");
        out.printil("    throw (IllegalStateException) t;");
        out.printil("if( t instanceof JspException )");
        out.printil("    throw (JspException) t;");
        out.printil("throw new JspException(t);");
        out.popIndent();
        out.printil("} finally {");
        out.pushIndent();
        out.printil(
            "((org.apache.jasper.runtime.JspContextWrapper) jspContext).syncEndTagFile();");
        if (isPoolingEnabled && !tagHandlerPoolNames.isEmpty()) {
            out.printil("_jspDestroy();");
        }
        out.popIndent();
        out.printil("}");

        // 关闭 doTag 方法
        out.popIndent();
        out.printil("}");

        // 生成的方法, 帮助类, etc.
        genCommonPostamble();
    }

    /**
     * 生成标签处理程序属性的声明, 并定义getter 和 setter 方法.
     */
    private void generateTagHandlerAttributes(TagInfo tagInfo)
        throws JasperException {

        if (tagInfo.hasDynamicAttributes()) {
            out.printil(
                "private java.util.HashMap _jspx_dynamic_attrs = new java.util.HashMap();");
        }

        // 声明属性
        TagAttributeInfo[] attrInfos = tagInfo.getAttributes();
        for (int i = 0; i < attrInfos.length; i++) {
            out.printin("private ");
            if (attrInfos[i].isFragment()) {
                out.print("javax.servlet.jsp.tagext.JspFragment ");
            } else {
                out.print(JspUtil.toJavaSourceType(attrInfos[i].getTypeName()));
                out.print(" ");
            }
            out.print(attrInfos[i].getName());
            out.println(";");
        }
        out.println();

        // 声明属性getter 和 setter方法
        if (attrInfos != null) {
            for (int i = 0; i < attrInfos.length; i++) {
                // getter method
                out.printin("public ");
                if (attrInfos[i].isFragment()) {
                    out.print("javax.servlet.jsp.tagext.JspFragment ");
                } else {
                    out.print(JspUtil.toJavaSourceType(attrInfos[i].getTypeName()));
                    out.print(" ");
                }
                out.print(toGetterMethod(attrInfos[i].getName()));
                out.println(" {");
                out.pushIndent();
                out.printin("return this.");
                out.print(attrInfos[i].getName());
                out.println(";");
                out.popIndent();
                out.printil("}");
                out.println();

                // setter method
                out.printin("public void ");
                out.print(toSetterMethodName(attrInfos[i].getName()));
                if (attrInfos[i].isFragment()) {
                    out.print("(javax.servlet.jsp.tagext.JspFragment ");
                } else {
                    out.print("(");
                    out.print(JspUtil.toJavaSourceType(attrInfos[i].getTypeName()));
                    out.print(" ");
                }
                out.print(attrInfos[i].getName());
                out.println(") {");
                out.pushIndent();
                out.printin("this.");
                out.print(attrInfos[i].getName());
                out.print(" = ");
                out.print(attrInfos[i].getName());
                out.println(";");
                out.popIndent();
                out.printil("}");
                out.println();
            }
        }
    }

    /*
     * 生成JspContext的setter, 因此可以创建一个包装器，并存储原始的和包装后的. 我们需要包装器从标签文件中屏蔽页面上下文，并模拟一个新的页面上下文.
     * 我们需要原始的东西, 例如同步 AT_BEGIN 和 AT_END 脚本变量.
     */
    private void generateSetJspContext(TagInfo tagInfo) {

        boolean nestedSeen = false;
        boolean atBeginSeen = false;
        boolean atEndSeen = false;

        // 确定是否有别名
        boolean aliasSeen = false;
        TagVariableInfo[] tagVars = tagInfo.getTagVariableInfos();
        for (int i = 0; i < tagVars.length; i++) {
            if (tagVars[i].getNameFromAttribute() != null
                && tagVars[i].getNameGiven() != null) {
                aliasSeen = true;
                break;
            }
        }

        if (aliasSeen) {
            out.printil(
                "public void setJspContext(JspContext ctx, java.util.Map aliasMap) {");
        } else {
            out.printil("public void setJspContext(JspContext ctx) {");
        }
        out.pushIndent();
        out.printil("super.setJspContext(ctx);");
        out.printil("java.util.ArrayList _jspx_nested = null;");
        out.printil("java.util.ArrayList _jspx_at_begin = null;");
        out.printil("java.util.ArrayList _jspx_at_end = null;");

        for (int i = 0; i < tagVars.length; i++) {

            switch (tagVars[i].getScope()) {
                case VariableInfo.NESTED :
                    if (!nestedSeen) {
                        out.printil(
                            "_jspx_nested = new java.util.ArrayList();");
                        nestedSeen = true;
                    }
                    out.printin("_jspx_nested.add(");
                    break;

                case VariableInfo.AT_BEGIN :
                    if (!atBeginSeen) {
                        out.printil(
                            "_jspx_at_begin = new java.util.ArrayList();");
                        atBeginSeen = true;
                    }
                    out.printin("_jspx_at_begin.add(");
                    break;

                case VariableInfo.AT_END :
                    if (!atEndSeen) {
                        out.printil(
                            "_jspx_at_end = new java.util.ArrayList();");
                        atEndSeen = true;
                    }
                    out.printin("_jspx_at_end.add(");
                    break;
            } // switch

            out.print(quote(tagVars[i].getNameGiven()));
            out.println(");");
        }
        if (aliasSeen) {
            out.printil(
                "this.jspContext = new org.apache.jasper.runtime.JspContextWrapper(ctx, _jspx_nested, _jspx_at_begin, _jspx_at_end, aliasMap);");
        } else {
            out.printil(
                "this.jspContext = new org.apache.jasper.runtime.JspContextWrapper(ctx, _jspx_nested, _jspx_at_begin, _jspx_at_end, null);");
        }
        out.popIndent();
        out.printil("}");
        out.println();
        out.printil("public JspContext getJspContext() {");
        out.pushIndent();
        out.printil("return this.jspContext;");
        out.popIndent();
        out.printil("}");
    }

    /*
     * 生成javax.servlet.jsp.tagext.DynamicAttributes.setDynamicAttribute() 方法的实现,
     * 保存每个传递进来的动态属性, 所以可以为其创建限定了作用域的变量.
     */
    public void generateSetDynamicAttribute() {
        out.printil(
            "public void setDynamicAttribute(String uri, String localName, Object value) throws JspException {");
        out.pushIndent();
        /*
         * 根据规范, 在map中只包含没有URI的动态属性; 所有其他动态属性都将被忽略.
         */
        out.printil("if (uri == null)");
        out.pushIndent();
        out.printil("_jspx_dynamic_attrs.put(localName, value);");
        out.popIndent();
        out.popIndent();
        out.printil("}");
    }

    /*
     * 为每个声明的标签属性创建一个页面作用域变量.
     * 如果标签接受动态属性, 对于每个传入的每个动态属性, 一个页面作用域变量是可用的.
     */
    private void generatePageScopedVariables(JasperTagInfo tagInfo) {

        // "normal" 属性
        TagAttributeInfo[] attrInfos = tagInfo.getAttributes();
        for (int i = 0; i < attrInfos.length; i++) {
            String attrName = attrInfos[i].getName();
            out.printil("if( " + toGetterMethod(attrName) + " != null ) ");
            out.pushIndent();
            out.printin("_jspx_page_context.setAttribute(");
            out.print(quote(attrName));
            out.print(", ");
            out.print(toGetterMethod(attrName));
            out.println(");");
            out.popIndent();
        }

        // 暴露包含页面作用域的动态属性的 Map
        if (tagInfo.hasDynamicAttributes()) {
            out.printin("_jspx_page_context.setAttribute(\"");
            out.print(tagInfo.getDynamicAttributesMapName());
            out.print("\", _jspx_dynamic_attrs);");
        }
    }

    /*
     * 生成给定属性名称的getter 方法.
     */
    private String toGetterMethod(String attrName) {
        char[] attrChars = attrName.toCharArray();
        attrChars[0] = Character.toUpperCase(attrChars[0]);
        return "get" + new String(attrChars) + "()";
    }

    /*
     * 生成给定属性名称的setter 方法.
     */
    private String toSetterMethodName(String attrName) {
        char[] attrChars = attrName.toCharArray();
        attrChars[0] = Character.toUpperCase(attrChars[0]);
        return "set" + new String(attrChars);
    }

    /**
     * 存储自定义标签处理结果的类.
     */
    private static class TagHandlerInfo {

        private Hashtable methodMaps;
        private Hashtable propertyEditorMaps;
        private Class tagHandlerClass;

        /**
         * @param n 标签处理类需要内省的自定义标签
         * @param tagHandlerClass 标签处理程序类
         * @param err 错误分派器
         */
        TagHandlerInfo(Node n, Class tagHandlerClass, ErrorDispatcher err)
            throws JasperException {
            this.tagHandlerClass = tagHandlerClass;
            this.methodMaps = new Hashtable();
            this.propertyEditorMaps = new Hashtable();

            try {
                BeanInfo tagClassInfo =
                    Introspector.getBeanInfo(tagHandlerClass);
                PropertyDescriptor[] pd = tagClassInfo.getPropertyDescriptors();
                for (int i = 0; i < pd.length; i++) {
                    /*
                     * FIXME: 应该检查一下, 例如pageContext, bodyContent, 和父节点 -akv
                     */
                    if (pd[i].getWriteMethod() != null) {
                        methodMaps.put(pd[i].getName(), pd[i].getWriteMethod());
                    }
                    if (pd[i].getPropertyEditorClass() != null)
                        propertyEditorMaps.put(
                            pd[i].getName(),
                            pd[i].getPropertyEditorClass());
                }
            } catch (IntrospectionException ie) {
                err.jspError(
                    n,
                    "jsp.error.introspect.taghandler",
                    tagHandlerClass.getName(),
                    ie);
            }
        }

        /**
         * XXX
         */
        public Method getSetterMethod(String attrName) {
            return (Method)methodMaps.get(attrName);
        }

        /**
         * XXX
         */
        public Class getPropertyEditorClass(String attrName) {
            return (Class)propertyEditorMaps.get(attrName);
        }

        /**
         * XXX
         */
        public Class getTagHandlerClass() {
            return tagHandlerClass;
        }
    }

    /**
     * 生成缓冲区代码的类. 包括跟踪java源码行映射的支持.
     */
    private static class GenBuffer {

        /*
         * 对于一个 CustomTag, 标签开始时生成的代码可能与标签主体的代码不在同一缓冲区内.
         * 这里使用两个字段保持这个连续的. 对于不对应任何JSP行的代码, 应该是 null.
         */
        private Node node;
        private Node.Nodes body;
        private java.io.CharArrayWriter charWriter;
        protected ServletWriter out;

        GenBuffer() {
            this(null, null);
        }

        GenBuffer(Node n, Node.Nodes b) {
            node = n;
            body = b;
            if (body != null) {
                body.setGeneratedInBuffer(true);
            }
            charWriter = new java.io.CharArrayWriter();
            out = new ServletWriter(new java.io.PrintWriter(charWriter));
        }

        public ServletWriter getOut() {
            return out;
        }

        public String toString() {
            return charWriter.toString();
        }

        /**
         * 调整Java 行. 这是必要的, 因为存储Java行的节点相对于这个缓冲区的开始需要调整, 当这个缓冲区插入到源代码中时.
         */
        public void adjustJavaLines(final int offset) {

            if (node != null) {
                adjustJavaLine(node, offset);
            }

            if (body != null) {
                try {
                    body.visit(new Node.Visitor() {

                        public void doVisit(Node n) {
                            adjustJavaLine(n, offset);
                        }

                        public void visit(Node.CustomTag n)
                            throws JasperException {
                            Node.Nodes b = n.getBody();
                            if (b != null && !b.isGeneratedInBuffer()) {
                                // 不要为在缓冲区中生成的嵌套标签调整行, 因为调整将在别处进行.
                                b.visit(this);
                            }
                        }
                    });
                } catch (JasperException ex) {
                }
            }
        }

        private static void adjustJavaLine(Node n, int offset) {
            if (n.getBeginJavaLine() > 0) {
                n.setBeginJavaLine(n.getBeginJavaLine() + offset);
                n.setEndJavaLine(n.getEndJavaLine() + offset);
            }
        }
    }

    /**
     * 跟踪生成的片段辅助类
     */
    private static class FragmentHelperClass {

        private static class Fragment {
            private GenBuffer genBuffer;
            private int id;

            public Fragment(int id, Node node) {
                this.id = id;
                genBuffer = new GenBuffer(null, node.getBody());
            }

            public GenBuffer getGenBuffer() {
                return this.genBuffer;
            }

            public int getId() {
                return this.id;
            }
        }

        // True 如果要生成辅助类.
        private boolean used = false;

        private ArrayList fragments = new ArrayList();

        private String className;

        // 整个辅助类的缓冲区
        private GenBuffer classBuffer = new GenBuffer();

        public FragmentHelperClass(String className) {
            this.className = className;
        }

        public String getClassName() {
            return this.className;
        }

        public boolean isUsed() {
            return this.used;
        }

        public void generatePreamble() {
            ServletWriter out = this.classBuffer.getOut();
            out.println();
            out.pushIndent();
            // Note: 不能是static, 因为我们需要引用类似_jspx_meth_*的东西
            out.printil("private class " + className);
            out.printil(
                "    extends " + "org.apache.jasper.runtime.JspFragmentHelper");
            out.printil("{");
            out.pushIndent();
            out.printil(
                "private javax.servlet.jsp.tagext.JspTag _jspx_parent;");
            out.printil("private int[] _jspx_push_body_count;");
            out.println();
            out.printil(
                "public "
                    + className
                    + "( int discriminator, JspContext jspContext, "
                    + "javax.servlet.jsp.tagext.JspTag _jspx_parent, "
                    + "int[] _jspx_push_body_count ) {");
            out.pushIndent();
            out.printil("super( discriminator, jspContext, _jspx_parent );");
            out.printil("this._jspx_parent = _jspx_parent;");
            out.printil("this._jspx_push_body_count = _jspx_push_body_count;");
            out.popIndent();
            out.printil("}");
        }

        public Fragment openFragment(
            Node parent,
            String tagHandlerVar,
            int methodNesting)
            throws JasperException {
            Fragment result = new Fragment(fragments.size(), parent);
            fragments.add(result);
            this.used = true;
            parent.setInnerClassName(className);

            ServletWriter out = result.getGenBuffer().getOut();
            out.pushIndent();
            out.pushIndent();
            // XXX - 返回boolean值, 因为如果从这个片段中调用一个标签, 生成器有时可能生成代码类似"return true". 这一点现在被忽略了, 跳过片段.
            // JSR-152 专家组目前正在讨论该如何处理此案.
            // 查看评论closeFragment()
            if (methodNesting > 0) {
                out.printin("public boolean invoke");
            } else {
                out.printin("public void invoke");
            }
            out.println(result.getId() + "( " + "JspWriter out ) ");
            out.pushIndent();
            // Note: 需要Throwable, 因为方法类似 _jspx_meth_* throw Throwable.
            out.printil("throws Throwable");
            out.popIndent();
            out.printil("{");
            out.pushIndent();
            generateLocalVariables(out, parent);

            return result;
        }

        public void closeFragment(Fragment fragment, int methodNesting) {
            ServletWriter out = fragment.getGenBuffer().getOut();
            // XXX - See comment in openFragment()
            if (methodNesting > 0) {
                out.printil("return false;");
            } else {
                out.printil("return;");
            }
            out.popIndent();
            out.printil("}");
        }

        public void generatePostamble() {
            ServletWriter out = this.classBuffer.getOut();
            // 生成所有片段方法:
            for (int i = 0; i < fragments.size(); i++) {
                Fragment fragment = (Fragment)fragments.get(i);
                fragment.getGenBuffer().adjustJavaLines(out.getJavaLine() - 1);
                out.printMultiLn(fragment.getGenBuffer().toString());
            }

            // 生成:
            out.printil("public void invoke( java.io.Writer writer )");
            out.pushIndent();
            out.printil("throws JspException");
            out.popIndent();
            out.printil("{");
            out.pushIndent();
            out.printil("JspWriter out = null;");
            out.printil("if( writer != null ) {");
            out.pushIndent();
            out.printil("out = this.jspContext.pushBody(writer);");
            out.popIndent();
            out.printil("} else {");
            out.pushIndent();
            out.printil("out = this.jspContext.getOut();");
            out.popIndent();
            out.printil("}");
            out.printil("try {");
            out.pushIndent();
            out.printil("switch( this.discriminator ) {");
            out.pushIndent();
            for (int i = 0; i < fragments.size(); i++) {
                out.printil("case " + i + ":");
                out.pushIndent();
                out.printil("invoke" + i + "( out );");
                out.printil("break;");
                out.popIndent();
            }
            out.popIndent();
            out.printil("}"); // switch
            out.popIndent();
            out.printil("}"); // try
            out.printil("catch( Throwable e ) {");
            out.pushIndent();
            out.printil(
                "if (e instanceof SkipPageException)");
            out.printil("    throw (SkipPageException) e;");
            out.printil("throw new JspException( e );");
            out.popIndent();
            out.printil("}"); // catch
            out.printil("finally {");
            out.pushIndent();

            out.printil("if( writer != null ) {");
            out.pushIndent();
            out.printil("this.jspContext.popBody();");
            out.popIndent();
            out.printil("}");

            out.popIndent();
            out.printil("}"); // finally
            out.popIndent();
            out.printil("}"); // invoke method
            out.popIndent();
            out.printil("}"); // helper class
            out.popIndent();
        }

        public String toString() {
            return classBuffer.toString();
        }

        public void adjustJavaLines(int offset) {
            for (int i = 0; i < fragments.size(); i++) {
                Fragment fragment = (Fragment)fragments.get(i);
                fragment.getGenBuffer().adjustJavaLines(offset);
            }
        }
    }
}
