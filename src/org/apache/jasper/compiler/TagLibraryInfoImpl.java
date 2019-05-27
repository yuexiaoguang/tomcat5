package org.apache.jasper.compiler;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import javax.servlet.jsp.tagext.FunctionInfo;
import javax.servlet.jsp.tagext.PageData;
import javax.servlet.jsp.tagext.TagAttributeInfo;
import javax.servlet.jsp.tagext.TagExtraInfo;
import javax.servlet.jsp.tagext.TagFileInfo;
import javax.servlet.jsp.tagext.TagInfo;
import javax.servlet.jsp.tagext.TagLibraryInfo;
import javax.servlet.jsp.tagext.TagLibraryValidator;
import javax.servlet.jsp.tagext.TagVariableInfo;
import javax.servlet.jsp.tagext.ValidationMessage;
import javax.servlet.jsp.tagext.VariableInfo;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.xmlparser.ParserUtils;
import org.apache.jasper.xmlparser.TreeNode;

/**
 * JSP规范的TagLibraryInfo 类的实现. 
 */
class TagLibraryInfoImpl extends TagLibraryInfo implements TagConstants {

    // Logger
    private Log log = LogFactory.getLog(TagLibraryInfoImpl.class);

    private Hashtable jarEntries;
    private JspCompilationContext ctxt;
    private ErrorDispatcher err;
    private ParserController parserController;

    private final void print(String name, String value, PrintWriter w) {
        if (value != null) {
            w.print(name+" = {\n\t");
            w.print(value);
            w.print("\n}\n");
        }
    }

    public String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        print("tlibversion", tlibversion, out);
        print("jspversion", jspversion, out);
        print("shortname", shortname, out);
        print("urn", urn, out);
        print("info", info, out);
        print("uri", uri, out);
        print("tagLibraryValidator", "" + tagLibraryValidator, out);

        for(int i = 0; i < tags.length; i++)
            out.println(tags[i].toString());
        
        for(int i = 0; i < tagFiles.length; i++)
            out.println(tagFiles[i].toString());
        
        for(int i = 0; i < functions.length; i++)
            out.println(functions[i].toString());
        
        return sw.toString();
    }
    
    // XXX FIXME
    // resolveRelativeUri 和 getResourceAsStream似乎没有正确处理相对路径, 当处理home和getDocBase时设置以下变通的办法直到这些问题解决.
    private InputStream getResourceAsStream(String uri) 
        throws FileNotFoundException {
        try {
            // 查看文件是否首先存在于文件系统上
            String real = ctxt.getRealPath(uri);
            if (real == null) {
                return ctxt.getResourceAsStream(uri);
            } else {
                return new FileInputStream(real);
            }
        }
        catch (FileNotFoundException ex) {
            // 如果在filesystem中未找到文件, 通过上下文获取资源
            return ctxt.getResourceAsStream(uri);
        }
    }

    public TagLibraryInfoImpl(JspCompilationContext ctxt,
                              ParserController pc,
                              String prefix, 
                              String uriIn,
                              String[] location,
                              ErrorDispatcher err) throws JasperException {
        super(prefix, uriIn);

        this.ctxt = ctxt;
        this.parserController = pc;
        this.err = err;
        InputStream in = null;
        JarFile jarFile = null;

        if (location == null) {
            // URI指向TLD本身或指向其中存储TLD的JAR文件
            location = generateTLDLocation(uri, ctxt);
        }

        try {
            if (!location[0].endsWith("jar")) {
                // 定位点指向TLD文件
                try {
                    in = getResourceAsStream(location[0]);
                    if (in == null) {
                        throw new FileNotFoundException(location[0]);
                    }
                } catch (FileNotFoundException ex) {
                    err.jspError("jsp.error.file.not.found", location[0]);
                }

                parseTLD(ctxt, location[0], in, null);
                // 将TLD添加到依赖列表
                PageInfo pageInfo = ctxt.createCompiler().getPageInfo();
                if (pageInfo != null) {
                    pageInfo.addDependant(location[0]);
                }
            } else {
                // 标签库打包进jar文件
                try {
                    URL jarFileUrl = new URL("jar:" + location[0] + "!/");
                    JarURLConnection conn =
                        (JarURLConnection) jarFileUrl.openConnection();
                    conn.setUseCaches(false);
                    conn.connect();
                    jarFile = conn.getJarFile();
                    ZipEntry jarEntry = jarFile.getEntry(location[1]);
                    in = jarFile.getInputStream(jarEntry);
                    parseTLD(ctxt, location[0], in, jarFileUrl);
                } catch (Exception ex) {
                    err.jspError("jsp.error.tld.unable_to_read", location[0],
                                 location[1], ex.toString());
                }
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable t) {}
            }
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (Throwable t) {}
            }
        }

    }
    
    /*
     * @param ctxt jsp编译上下文
     * @param uri TLD的 uri
     * @param in TLD的输入流
     * @param jarFileUrl 包含TLD的JAR 文件, 或者 null 如果标记库没有打包在JAR中
     */
    private void parseTLD(JspCompilationContext ctxt,
                          String uri, InputStream in, URL jarFileUrl) 
        throws JasperException {
        Vector tagVector = new Vector();
        Vector tagFileVector = new Vector();
        Hashtable functionTable = new Hashtable();

        // 在<taglib>的子元素上创建一个迭代器
        ParserUtils pu = new ParserUtils();
        TreeNode tld = pu.parseXMLDocument(uri, in);

        // 检查<taglib>根元素是否包含一个'version'属性, 其在JSP 2.0中被添加来替换<jsp-version>子元素
        this.jspversion = tld.findAttribute("version");

        // 处理<taglib>元素的子元素
        Iterator list = tld.findChildren();

        while (list.hasNext()) {
            TreeNode element = (TreeNode) list.next();
            String tname = element.getName();

            if ("tlibversion".equals(tname)                    // JSP 1.1
                        || "tlib-version".equals(tname)) {     // JSP 1.2
                this.tlibversion = element.getBody();
            } else if ("jspversion".equals(tname)
                        || "jsp-version".equals(tname)) {
                this.jspversion = element.getBody();
            } else if ("shortname".equals(tname) ||
                     "short-name".equals(tname))
                this.shortname = element.getBody();
            else if ("uri".equals(tname))
                this.urn = element.getBody();
            else if ("info".equals(tname) ||
                     "description".equals(tname))
                this.info = element.getBody();
            else if ("validator".equals(tname))
                this.tagLibraryValidator = createValidator(element);
            else if ("tag".equals(tname))
                tagVector.addElement(createTagInfo(element, jspversion));
            else if ("tag-file".equals(tname)) {
                TagFileInfo tagFileInfo = createTagFileInfo(element, uri,
                                                            jarFileUrl);
                tagFileVector.addElement(tagFileInfo);
            } else if ("function".equals(tname)) {         // JSP2.0
                FunctionInfo funcInfo = createFunctionInfo(element);
                String funcName = funcInfo.getName();
                if (functionTable.containsKey(funcName)) {
                    err.jspError("jsp.error.tld.fn.duplicate.name",
                                 funcName, uri);

                }
                functionTable.put(funcName, funcInfo);
            } else if ("display-name".equals(tname) ||    // Ignored elements
                     "small-icon".equals(tname) ||
                     "large-icon".equals(tname) ||
                     "listener".equals(tname)) {
                ;
            } else if ("taglib-extension".equals(tname)) {
                // Recognized but ignored
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage(
                            "jsp.warning.unknown.element.in.taglib", tname));
                }
            }

        }

        if (tlibversion == null) {
            err.jspError("jsp.error.tld.mandatory.element.missing", 
                         "tlib-version");
        }
        if (jspversion == null) {
            err.jspError("jsp.error.tld.mandatory.element.missing",
                         "jsp-version");
        }

        this.tags = new TagInfo[tagVector.size()];
        tagVector.copyInto (this.tags);

        this.tagFiles = new TagFileInfo[tagFileVector.size()];
        tagFileVector.copyInto (this.tagFiles);

        this.functions = new FunctionInfo[functionTable.size()];
        int i=0;
        Enumeration enumeration = functionTable.elements();
        while (enumeration.hasMoreElements()) {
            this.functions[i++] = (FunctionInfo) enumeration.nextElement();
        }
    }
    
    /*
     * @param uri TLD的URI
     * @param ctxt 编译上下文
     *
     * @return String 数组, 它的第一个元素表示TLD的路径.
     * 如果指向TLD的路径指向JAR文件, 然后，第二个元素表示JAR文件中TLD条目的名称, 这是硬编码 META-INF/taglib.tld.
     */
    private String[] generateTLDLocation(String uri,
                                         JspCompilationContext ctxt)
                throws JasperException {

        int uriType = TldLocationsCache.uriType(uri);
        if (uriType == TldLocationsCache.ABS_URI) {
            err.jspError("jsp.error.taglibDirective.absUriCannotBeResolved",
                         uri);
        } else if (uriType == TldLocationsCache.NOROOT_REL_URI) {
            uri = ctxt.resolveRelativeUri(uri);
        }

        String[] location = new String[2];
        location[0] = uri;
        if (location[0].endsWith("jar")) {
            URL url = null;
            try {
                url = ctxt.getResource(location[0]);
            } catch (Exception ex) {
                err.jspError("jsp.error.tld.unable_to_get_jar", location[0],
                             ex.toString());
            }
            if (url == null) {
                err.jspError("jsp.error.tld.missing_jar", location[0]);
            }
            location[0] = url.toString();
            location[1] = "META-INF/taglib.tld";
        }

        return location;
    }

    private TagInfo createTagInfo(TreeNode elem, String jspVersion)
            throws JasperException {

        String tagName = null;
        String tagClassName = null;
        String teiClassName = null;

        /*
         * JSP 1.2 标签处理程序的默认主体内容(<body-content> 在JSP 2.0中是强制性的, 因为默认情况下对于简单的标记处理程序无效)
         */
        String bodycontent = "JSP";

        String info = null;
        String displayName = null;
        String smallIcon = null;
        String largeIcon = null;
        boolean dynamicAttributes = false;
        
        Vector attributeVector = new Vector();
        Vector variableVector = new Vector();
        Iterator list = elem.findChildren();
        while (list.hasNext()) {
            TreeNode element = (TreeNode) list.next();
            String tname = element.getName();

            if ("name".equals(tname)) {
                tagName = element.getBody();
            } else if ("tagclass".equals(tname) ||
                     "tag-class".equals(tname)) {
                tagClassName = element.getBody();
            } else if ("teiclass".equals(tname) ||
                     "tei-class".equals(tname)) {
                teiClassName = element.getBody();
            } else if ("bodycontent".equals(tname) ||
                     "body-content".equals(tname)) {
                bodycontent = element.getBody();
            } else if ("display-name".equals(tname)) {
                displayName = element.getBody();
            } else if ("small-icon".equals(tname)) {
                smallIcon = element.getBody();
            } else if ("large-icon".equals(tname)) {
                largeIcon = element.getBody();
            } else if ("icon".equals(tname)) {
                TreeNode icon = element.findChild("small-icon");
                if (icon != null) {
                    smallIcon = icon.getBody();
                }
                icon = element.findChild("large-icon");
                if (icon != null) {
                    largeIcon = icon.getBody();
                }
            } else if ("info".equals(tname) ||
                     "description".equals(tname)) {
                info = element.getBody();
            } else if ("variable".equals(tname)) {
                variableVector.addElement(createVariable(element));
            } else if ("attribute".equals(tname)) {
                attributeVector.addElement(createAttribute(element, jspVersion));
            } else if ("dynamic-attributes".equals(tname)) {
                dynamicAttributes = JspUtil.booleanValue(element.getBody());
            } else if ("example".equals(tname)) {
                // Ignored elements
            } else if ("tag-extension".equals(tname)) {
                // Ignored
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage(
                            "jsp.warning.unknown.element.in.tag", tname));
                }
            }
        }

        TagExtraInfo tei = null;
        if (teiClassName != null && !teiClassName.equals("")) {
            try {
                Class teiClass = ctxt.getClassLoader().loadClass(teiClassName);
                tei = (TagExtraInfo) teiClass.newInstance();
            } catch (Exception e) {
                err.jspError("jsp.error.teiclass.instantiation", teiClassName,
                             e);
            }
        }

        TagAttributeInfo[] tagAttributeInfo
            = new TagAttributeInfo[attributeVector.size()];
        attributeVector.copyInto(tagAttributeInfo);

        TagVariableInfo[] tagVariableInfos
            = new TagVariableInfo[variableVector.size()];
        variableVector.copyInto(tagVariableInfos);

        TagInfo taginfo = new TagInfo(tagName,
                                      tagClassName,
                                      bodycontent,
                                      info,
                                      this, 
                                      tei,
                                      tagAttributeInfo,
                                      displayName,
                                      smallIcon,
                                      largeIcon,
                                      tagVariableInfos,
                                      dynamicAttributes);
        return taginfo;
    }

    /*
     * 解析给定的TagFile的标签文件指令并把它们变成一个TagInfo.
     *
     * @param elem TLD中的<tag-file>元素
     * @param uri TLD的位置, 如果标签文件是相对于它指定的
     * @param jarFile JAR 文件, 如果标签文件打包在一个JAR中
     *
     * @return 对应于标签文件指令的TagInfo
     */
    private TagFileInfo createTagFileInfo(TreeNode elem, String uri,
                                          URL jarFileUrl)
                throws JasperException {

        String name = null;
        String path = null;

        Iterator list = elem.findChildren();
        while (list.hasNext()) {
            TreeNode child = (TreeNode) list.next();
            String tname = child.getName();
            if ("name".equals(tname)) {
                name = child.getBody();
            } else if ("path".equals(tname)) {
                path = child.getBody();
            } else if ("example".equals(tname)) {
                // Ignore <example> element: Bugzilla 33538
            } else if ("tag-extension".equals(tname)) {
                // Ignore <tag-extension> element: Bugzilla 33538
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage(
                            "jsp.warning.unknown.element.in.tagfile", tname));
                }
            }
        }

        if (path.startsWith("/META-INF/tags")) {
            // 标签文件打包在jar中
            ctxt.getTagFileJarUrls().put(path, jarFileUrl);
        } else if (!path.startsWith("/WEB-INF/tags")) {
            err.jspError("jsp.error.tagfile.illegalPath", path);
        }

        TagInfo tagInfo
            = TagFileProcessor.parseTagFileDirectives(parserController, name,
                                                      path, this);
        return new TagFileInfo(name, path, tagInfo);
    }

    TagAttributeInfo createAttribute(TreeNode elem, String jspVersion) {
        String name = null;
        String type = null;
        boolean required = false, rtexprvalue = false, reqTime = false,
            isFragment = false;
        
        Iterator list = elem.findChildren();
        while (list.hasNext()) {
            TreeNode element = (TreeNode) list.next();
            String tname = element.getName();

            if ("name".equals(tname)) {
                name = element.getBody();
            } else if ("required".equals(tname)) {
                String s = element.getBody();
                if (s != null)
                    required = JspUtil.booleanValue(s);
            } else if ("rtexprvalue".equals(tname)) {
                String s = element.getBody();
                if (s != null)
                    rtexprvalue = JspUtil.booleanValue(s);
            } else if ("type".equals(tname)) {
                type = element.getBody();
                if ("1.2".equals(jspVersion)
                        && (type.equals("Boolean")
                            || type.equals("Byte")
                            || type.equals("Character")
                            || type.equals("Double")
                            || type.equals("Float")
                            || type.equals("Integer")
                            || type.equals("Long")
                            || type.equals("Object")
                            || type.equals("Short")
                            || type.equals("String"))) {
                    type = "java.lang." + type;
                }
            } else if ("fragment".equals(tname)) {
                String s = element.getBody();
                if (s != null) {
                    isFragment = JspUtil.booleanValue(s);
                }
            } else if ("description".equals(tname) ||    // Ignored elements
                       false) {
                ;
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage(
                            "jsp.warning.unknown.element.in.attribute", tname));
                }
            }
        }

        if (isFragment) {
            /*
             * 根据JSP.C-3 ("TLD Schema Element Structure - tag"), 
             * 'type' 和 'rtexprvalue' 不能指定, 如果'fragment'已经被指定(这将通过验证解析器来执行).
             * 而且, 如果'fragment'是TRUE, 'type'固定在
             * javax.servlet.jsp.tagext.JspFragment, 并且'rtexprvalue'固定为true. See also JSP.8.5.2.
             */
            type = "javax.servlet.jsp.tagext.JspFragment";
            rtexprvalue = true;            
        }
        
        if (!rtexprvalue) {
            // 根据JSP 规范, 对于静态值(在翻译的时候确定)类型固定为java.lang.String.
            type = "java.lang.String";
        }

        return new TagAttributeInfo(name, required, type, rtexprvalue,
                                    isFragment);
    }

    TagVariableInfo createVariable(TreeNode elem) {
        String nameGiven = null;
        String nameFromAttribute = null;
        String className = "java.lang.String";
        boolean declare = true;
        int scope = VariableInfo.NESTED;

        Iterator list = elem.findChildren();
        while (list.hasNext()) {
            TreeNode element = (TreeNode) list.next();
            String tname = element.getName();
            if ("name-given".equals(tname))
                nameGiven = element.getBody();
            else if ("name-from-attribute".equals(tname))
                nameFromAttribute = element.getBody();
            else if ("variable-class".equals(tname))
                className = element.getBody();
            else if ("declare".equals(tname)) {
                String s = element.getBody();
                if (s != null)
                    declare = JspUtil.booleanValue(s);
            } else if ("scope".equals(tname)) {
                String s = element.getBody();
                if (s != null) {
                    if ("NESTED".equals(s)) {
                        scope = VariableInfo.NESTED;
                    } else if ("AT_BEGIN".equals(s)) {
                        scope = VariableInfo.AT_BEGIN;
                    } else if ("AT_END".equals(s)) {
                        scope = VariableInfo.AT_END;
                    }
                }
            } else if ("description".equals(tname) ||    // Ignored elements
                     false ) {
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage(
                            "jsp.warning.unknown.element.in.variable", tname));
                }
            }
        }
        return new TagVariableInfo(nameGiven, nameFromAttribute,
                                   className, declare, scope);
    }

    private TagLibraryValidator createValidator(TreeNode elem)
            throws JasperException {

        String validatorClass = null;
        Map initParams = new Hashtable();

        Iterator list = elem.findChildren();
        while (list.hasNext()) {
            TreeNode element = (TreeNode) list.next();
            String tname = element.getName();
            if ("validator-class".equals(tname))
                validatorClass = element.getBody();
            else if ("init-param".equals(tname)) {
                String[] initParam = createInitParam(element);
                initParams.put(initParam[0], initParam[1]);
            } else if ("description".equals(tname) ||    // Ignored elements
                     false ) {
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage(
                            "jsp.warning.unknown.element.in.validator", tname));
                }
            }
        }

        TagLibraryValidator tlv = null;
        if (validatorClass != null && !validatorClass.equals("")) {
            try {
                Class tlvClass = 
                    ctxt.getClassLoader().loadClass(validatorClass);
                tlv = (TagLibraryValidator)tlvClass.newInstance();
            } catch (Exception e) {
                err.jspError("jsp.error.tlvclass.instantiation",
                             validatorClass, e);
            }
        }
        if (tlv != null) {
            tlv.setInitParameters(initParams);
        }
        return tlv;
    }

    String[] createInitParam(TreeNode elem) {
        String[] initParam = new String[2];
        
        Iterator list = elem.findChildren();
        while (list.hasNext()) {
            TreeNode element = (TreeNode) list.next();
            String tname = element.getName();
            if ("param-name".equals(tname)) {
                initParam[0] = element.getBody();
            } else if ("param-value".equals(tname)) {
                initParam[1] = element.getBody();
            } else if ("description".equals(tname)) {
                ; // Do nothing
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage(
                            "jsp.warning.unknown.element.in.initParam", tname));
                }
            }
        }
        return initParam;
    }

    FunctionInfo createFunctionInfo(TreeNode elem) {

        String name = null;
        String klass = null;
        String signature = null;

        Iterator list = elem.findChildren();
        while (list.hasNext()) {
            TreeNode element = (TreeNode) list.next();
            String tname = element.getName();

            if ("name".equals(tname)) {
                name = element.getBody();
            } else if ("function-class".equals(tname)) {
                klass = element.getBody();
            } else if ("function-signature".equals(tname)) {
                signature = element.getBody();
            } else if ("display-name".equals(tname) ||    // Ignored elements
                     "small-icon".equals(tname) ||
                     "large-icon".equals(tname) ||
                     "description".equals(tname) || 
                     "example".equals(tname)) {
            } else {
                if (log.isWarnEnabled()) {
                    log.warn(Localizer.getMessage(
                        "jsp.warning.unknown.element.in.function", tname));
                }
            }
        }

        return new FunctionInfo(name, klass, signature);
    }


    //*********************************************************************
    // Until javax.servlet.jsp.tagext.TagLibraryInfo is fixed

    /**
     * TagLibraryValidator 类实例.
     * 
     * @return TagLibraryValidator实例
     */
    public TagLibraryValidator getTagLibraryValidator() {
        return tagLibraryValidator;
    }

    /**
     * 与JSP页面相关联的XML文档的翻译时间验证.
     * 这是一种方便的方法在关联的TagLibraryValidator类上.
     *
     * @param thePage JSP页面对象
     * @return 表示该页面是否是有效的字符串.
     */
    public ValidationMessage[] validate(PageData thePage) {
        TagLibraryValidator tlv = getTagLibraryValidator();
        if (tlv == null) return null;

        String uri = getURI();
        if (uri.startsWith("/")) {
            uri = URN_JSPTLD + uri;
        }
        return tlv.validate(getPrefixString(), uri, thePage);
    }

    protected TagLibraryValidator tagLibraryValidator;

/***************自己加的*************/
	@Override
	public TagLibraryInfo[] getTagLibraryInfos() {
		// TODO Auto-generated method stub
		return null;
	}
/***************自己加的*************/
}
