package org.apache.jasper.compiler;

import java.io.CharArrayWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import java.util.Iterator;
import java.util.List;
import java.util.jar.JarFile;

import javax.servlet.jsp.tagext.TagFileInfo;
import javax.servlet.jsp.tagext.TagInfo;
import javax.servlet.jsp.tagext.TagLibraryInfo;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 实现了JSP 文档解析器, 即XML语法中的 JSP页面.
 */
class JspDocumentParser extends DefaultHandler implements LexicalHandler, TagConstants {

    private static final String JSP_VERSION = "version";
    private static final String LEXICAL_HANDLER_PROPERTY =
        "http://xml.org/sax/properties/lexical-handler";
    private static final String JSP_URI = "http://java.sun.com/JSP/Page";

    private static final EnableDTDValidationException ENABLE_DTD_VALIDATION_EXCEPTION =
        new EnableDTDValidationException(
            "jsp.error.enable_dtd_validation",
            null);

    private ParserController parserController;
    private JspCompilationContext ctxt;
    private PageInfo pageInfo;
    private String path;
    private StringBuffer charBuffer;

    // 表示当前正在解析的XML元素的节点
    private Node current;

    /*
     * 最外层(在嵌套层次结构中)节点, 其主体被声明为scriptless. 如果一个节点的主体被声明为scriptless, 所有嵌套的节点也必须是scriptless.
     */ 
    private Node scriptlessBodyNode;

    private Locator locator;

    //表示当前元素的开始.
    //注意：locator.getLineNumber() 和 locator.getColumnNumber()返回行号和列号.
    //底层XMl 解析器忽略不属于字符数据的空格, 所以 Node不是从字符数据中创建的, 这是可以做到的最好的程度了.
    //但在解析字符数据时, 从startMark设置的前一个元素获得准确的起始位置, 并更新它，因为我们通过字符前进.
    private Mark startMark;

    // 是否在DTD声明内
    private boolean inDTD;

    private boolean isValidating;

    private ErrorDispatcher err;
    private boolean isTagFile;
    private boolean directivesOnly;
    private boolean isTop;

    // 标签依赖体的嵌套级别
    private int tagDependentNesting = 0;
    // 延迟 incrmenting tagDependentNesting, 直到第一次遇到 jsp:body
    private boolean tagDependentPending = false;

    public JspDocumentParser(
        ParserController pc,
        String path,
        boolean isTagFile,
        boolean directivesOnly) {
        this.parserController = pc;
        this.ctxt = pc.getJspCompilationContext();
        this.pageInfo = pc.getCompiler().getPageInfo();
        this.err = pc.getCompiler().getErrorDispatcher();
        this.path = path;
        this.isTagFile = isTagFile;
        this.directivesOnly = directivesOnly;
        this.isTop = true;
    }

    /*
     * 通过响应SAX事件解析JSP文档.
     *
     * @throws JasperException
     */
    public static Node.Nodes parse(
        ParserController pc,
        String path,
        JarFile jarFile,
        Node parent,
        boolean isTagFile,
        boolean directivesOnly,
        String pageEnc,
        String jspConfigPageEnc,
        boolean isEncodingSpecifiedInProlog)
        throws JasperException {

        JspDocumentParser jspDocParser = new JspDocumentParser(pc, path, isTagFile, directivesOnly);
        Node.Nodes pageNodes = null;

        try {
            // 创建并初始化虚拟根节点, 使用指定的页面编码
            Node.Root dummyRoot = new Node.Root(null, parent, true);
            dummyRoot.setPageEncoding(pageEnc);
            dummyRoot.setJspConfigPageEncoding(jspConfigPageEnc);
            dummyRoot.setIsEncodingSpecifiedInProlog(
                isEncodingSpecifiedInProlog);
            jspDocParser.current = dummyRoot;
            if (parent == null) {
                jspDocParser.addInclude(
                    dummyRoot,
                    jspDocParser.pageInfo.getIncludePrelude());
            } else {
                jspDocParser.isTop = false;
            }

            // 解析输入
            SAXParser saxParser = getSAXParser(false, jspDocParser);
            InputStream inStream = null;
            try {
                inStream = JspUtil.getInputStream(path, jarFile,
                                                  jspDocParser.ctxt,
                                                  jspDocParser.err);
                saxParser.parse(new InputSource(inStream), jspDocParser);
            } catch (EnableDTDValidationException e) {
                saxParser = getSAXParser(true, jspDocParser);
                jspDocParser.isValidating = true;
                if (inStream != null) {
                    try {
                        inStream.close();
                    } catch (Exception any) {
                    }
                }
                inStream = JspUtil.getInputStream(path, jarFile,
                                                  jspDocParser.ctxt,
                                                  jspDocParser.err);
                saxParser.parse(new InputSource(inStream), jspDocParser);
            } finally {
                if (inStream != null) {
                    try {
                        inStream.close();
                    } catch (Exception any) {
                    }
                }
            }

            if (parent == null) {
                jspDocParser.addInclude(
                    dummyRoot,
                    jspDocParser.pageInfo.getIncludeCoda());
            }

            // 从虚拟根节点创建 Node.Nodes
            pageNodes = new Node.Nodes(dummyRoot);

        } catch (IOException ioe) {
            jspDocParser.err.jspError("jsp.error.data.file.read", path, ioe);
        } catch (SAXParseException e) {
            jspDocParser.err.jspError
                (new Mark(jspDocParser.ctxt, path, e.getLineNumber(),
                          e.getColumnNumber()),
                 e.getMessage());
        } catch (Exception e) {
            jspDocParser.err.jspError(e);
        }

        return pageNodes;
    }

    /*
     * 处理给定的包含文件列表.
     *
     * 这是用于实现include-prelude和web.xml中的jsp配置元素的include-coda子元素
     */
    private void addInclude(Node parent, List files) throws SAXException {
        if (files != null) {
            Iterator iter = files.iterator();
            while (iter.hasNext()) {
                String file = (String)iter.next();
                AttributesImpl attrs = new AttributesImpl();
                attrs.addAttribute("", "file", "file", "CDATA", file);

            	// 创建一个虚拟的包含指令节点
                Node includeDir = new Node.IncludeDirective(attrs, null, parent);
                processIncludeDirective(file, includeDir);
            }
        }
    }

    /*
     * 接收元素开始的通知.
     *
     * 此方法将给定的标记属性分配给3个桶中的一个:
     * 
     * - "xmlns" 属性, 表示(标准的或自定义的)标签库.
     * - "xmlns" 属性, 不表示标签库.
     * - all 剩余的属性.
     *
     * 对于每一个表示自定义标签库的 "xmlns"属性, 对应的 TagLibraryInfo 对象被添加到自定义标签库集合.
     */
    public void startElement(
        String uri,
        String localName,
        String qName,
        Attributes attrs)
        throws SAXException {

        AttributesImpl taglibAttrs = null;
        AttributesImpl nonTaglibAttrs = null;
        AttributesImpl nonTaglibXmlnsAttrs = null;

        processChars();

        checkPrefixes(uri, qName, attrs);

        if (directivesOnly &&
            !(JSP_URI.equals(uri) && localName.startsWith(DIRECTIVE_ACTION))) {
            return;
        }

        // jsp:text 不能有任何子元素
        if (JSP_URI.equals(uri) && TEXT_ACTION.equals(current.getLocalName())) {
            throw new SAXParseException(
                Localizer.getMessage("jsp.error.text.has_subelement"),
                locator);
        }

        startMark = new Mark(ctxt, path, locator.getLineNumber(),
                             locator.getColumnNumber());

        if (attrs != null) {
            /*
             * 注意，由于底层SAX解析器中存在一个bug, 属性必须按降序排列. 
             */
            boolean isTaglib = false;
            for (int i = attrs.getLength() - 1; i >= 0; i--) {
                isTaglib = false;
                String attrQName = attrs.getQName(i);
                if (!attrQName.startsWith("xmlns")) {
                    if (nonTaglibAttrs == null) {
                        nonTaglibAttrs = new AttributesImpl();
                    }
                    nonTaglibAttrs.addAttribute(
                        attrs.getURI(i),
                        attrs.getLocalName(i),
                        attrs.getQName(i),
                        attrs.getType(i),
                        attrs.getValue(i));
                } else {
                    if (attrQName.startsWith("xmlns:jsp")) {
                        isTaglib = true;
                    } else {
                        String attrUri = attrs.getValue(i);
                        // 这个URI的TaglibInfo已经在 startPrefixMapping中建立
                        isTaglib = pageInfo.hasTaglib(attrUri);
                    }
                    if (isTaglib) {
                        if (taglibAttrs == null) {
                            taglibAttrs = new AttributesImpl();
                        }
                        taglibAttrs.addAttribute(
                            attrs.getURI(i),
                            attrs.getLocalName(i),
                            attrs.getQName(i),
                            attrs.getType(i),
                            attrs.getValue(i));
                    } else {
                        if (nonTaglibXmlnsAttrs == null) {
                            nonTaglibXmlnsAttrs = new AttributesImpl();
                        }
                        nonTaglibXmlnsAttrs.addAttribute(
                            attrs.getURI(i),
                            attrs.getLocalName(i),
                            attrs.getQName(i),
                            attrs.getType(i),
                            attrs.getValue(i));
                    }
                }
            }
        }

        Node node = null;

        if (tagDependentPending && JSP_URI.equals(uri) &&
                     localName.equals(BODY_ACTION)) {
            tagDependentPending = false;
            tagDependentNesting++;
            current =
                parseStandardAction(
                    qName,
                    localName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    startMark,
                    current);
            return;
        }

        if (tagDependentPending && JSP_URI.equals(uri) &&
                     localName.equals(ATTRIBUTE_ACTION)) {
            current =
                parseStandardAction(
                    qName,
                    localName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    startMark,
                    current);
            return;
        }

        if (tagDependentPending) {
            tagDependentPending = false;
            tagDependentNesting++;
        }

        if (tagDependentNesting > 0) {
            node =
                new Node.UninterpretedTag(
                    qName,
                    localName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    startMark,
                    current);
        } else if (JSP_URI.equals(uri)) {
            node =
                parseStandardAction(
                    qName,
                    localName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    startMark,
                    current);
        } else {
            node =
                parseCustomAction(
                    qName,
                    localName,
                    uri,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    startMark,
                    current);
            if (node == null) {
                node =
                    new Node.UninterpretedTag(
                        qName,
                        localName,
                        nonTaglibAttrs,
                        nonTaglibXmlnsAttrs,
                        taglibAttrs,
                        startMark,
                        current);
            } else {
                // 自定义操作
                String bodyType = getBodyType((Node.CustomTag) node);

                if (scriptlessBodyNode == null
                        && bodyType.equalsIgnoreCase(TagInfo.BODY_CONTENT_SCRIPTLESS)) {
                    scriptlessBodyNode = node;
                }
                else if (TagInfo.BODY_CONTENT_TAG_DEPENDENT.equalsIgnoreCase(bodyType)) {
                    tagDependentPending = true;
                }
            }
        }
        current = node;
    }

    /*
     * 接收元素内字符数据的通知.
     *
     * SAX不使用所有的模板文本调用此方法, 但是可以用某一块来调用这个方法. 这是一个问题，当我们试图确定文本只包含空格, 或者当我们在寻找EL表达式字符串时.
     * 因此有必要缓冲和连接块和稍后处理的级联文本(at beginTag and endTag)
     *
     * @param buf 字符
     * @param offset 字符数组中的起始位置
     * @param len 从字符数组中使用的字符数
     *
     * @throws SAXException
     */
    public void characters(char[] buf, int offset, int len) {

        if (charBuffer == null) {
            charBuffer = new StringBuffer();
        }
        charBuffer.append(buf, offset, len);
    }

    private void processChars() throws SAXException {

        if (charBuffer == null) {
            return;
        }

        /*
         * JSP.6.1.1: 所有空白的文本节点都将从文档中删除, 除非 jsp:text 元素中的节点,
         * 以及jsp:attribute中的任何前导空格和尾随空格, 其'trim' 属性被设置为 FALSE, 要一字不差地保留下来.
         * JSP.6.2.3 定义空格字符.
         */
        boolean isAllSpace = true;
        if (!(current instanceof Node.JspText)
            && !(current instanceof Node.NamedAttribute)) {
            for (int i = 0; i < charBuffer.length(); i++) {
                if (!(charBuffer.charAt(i) == ' '
                    || charBuffer.charAt(i) == '\n'
                    || charBuffer.charAt(i) == '\r'
                    || charBuffer.charAt(i) == '\t')) {
                    isAllSpace = false;
                    break;
                }
            }
        }

        if (!isAllSpace && tagDependentPending) {
            tagDependentPending = false;
            tagDependentNesting++;
        }

        if (tagDependentNesting > 0) {
            if (charBuffer.length() > 0) {
                new Node.TemplateText(charBuffer.toString(), startMark, current);
            }
            startMark = new Mark(ctxt, path, locator.getLineNumber(),
                                 locator.getColumnNumber());
            charBuffer = null;
            return;
        }

        if ((current instanceof Node.JspText)
            || (current instanceof Node.NamedAttribute)
            || !isAllSpace) {

            int line = startMark.getLineNumber();
            int column = startMark.getColumnNumber();

            CharArrayWriter ttext = new CharArrayWriter();
            int lastCh = 0;
            for (int i = 0; i < charBuffer.length(); i++) {

                int ch = charBuffer.charAt(i);
                if (ch == '\n') {
                    column = 1;
                    line++;
                } else {
                    column++;
                }
                if (lastCh == '$' && ch == '{') {
                    if (ttext.size() > 0) {
                        new Node.TemplateText(
                            ttext.toString(),
                            startMark,
                            current);
                        ttext = new CharArrayWriter();
                        //从列数中减去两个来计算, 为了已经解析的 '${'
                        startMark = new Mark(ctxt, path, line, column - 2);
                    }
                    // 随后的 "${" 第一个引号 "}"
                    i++;
                    boolean singleQ = false;
                    boolean doubleQ = false;
                    lastCh = 0;
                    for (;; i++) {
                        if (i >= charBuffer.length()) {
                            throw new SAXParseException(
                                Localizer.getMessage(
                                    "jsp.error.unterminated",
                                    "${"),
                                locator);

                        }
                        ch = charBuffer.charAt(i);
                        if (ch == '\n') {
                            column = 1;
                            line++;
                        } else {
                            column++;
                        }
                        if (lastCh == '\\' && (singleQ || doubleQ)) {
                            ttext.write(ch);
                            lastCh = 0;
                            continue;
                        }
                        if (ch == '}') {
                            new Node.ELExpression(
                                ttext.toString(),
                                startMark,
                                current);
                            ttext = new CharArrayWriter();
                            startMark = new Mark(ctxt, path, line, column);
                            break;
                        }
                        if (ch == '"')
                            doubleQ = !doubleQ;
                        else if (ch == '\'')
                            singleQ = !singleQ;

                        ttext.write(ch);
                        lastCh = ch;
                    }
                } else if (lastCh == '\\' && ch == '$') {
                    ttext.write('$');
                    ch = 0;  // Not start of EL anymore
                } else {
                    if (lastCh == '$' || lastCh == '\\') {
                        ttext.write(lastCh);
                    }
                    if (ch != '$' && ch != '\\') {
                        ttext.write(ch);
                    }
                }
                lastCh = ch;
            }
            if (lastCh == '$' || lastCh == '\\') {
                ttext.write(lastCh);
            }
            if (ttext.size() > 0) {
                new Node.TemplateText(ttext.toString(), startMark, current);
            }
        }
        startMark = new Mark(ctxt, path, locator.getLineNumber(),
                             locator.getColumnNumber());

        charBuffer = null;
    }

    /*
     * 接收元素结束的通知.
     */
    public void endElement(String uri, String localName, String qName)
        throws SAXException {

        processChars();

        if (directivesOnly &&
            !(JSP_URI.equals(uri) && localName.startsWith(DIRECTIVE_ACTION))) {
            return;
        }

        if (current instanceof Node.NamedAttribute) {
            boolean isTrim = ((Node.NamedAttribute)current).isTrim();
            Node.Nodes subElems = ((Node.NamedAttribute)current).getBody();
            for (int i = 0; subElems != null && i < subElems.size(); i++) {
                Node subElem = subElems.getNode(i);
                if (!(subElem instanceof Node.TemplateText)) {
                    continue;
                }
                // 忽略任何空格 (包括空格, 回车, 换行, 和tab, 出现在<jsp:attribute>操作主体开始和结尾的时候,
                // 如果操作的'trim'属性被设置为TRUE (默认).
                // 此外, 任何<jsp:attribute>中只有空格的文本节点将从文档中删除, 除了<jsp:attribute>中引导空格和尾随空格外,
                // 其'trim'属性被设置为FALSE, 必须一字不差地保留下来.
                if (i == 0) {
                    if (isTrim) {
                        ((Node.TemplateText)subElem).ltrim();
                    }
                } else if (i == subElems.size() - 1) {
                    if (isTrim) {
                        ((Node.TemplateText)subElem).rtrim();
                    }
                } else {
                    if (((Node.TemplateText)subElem).isAllSpace()) {
                        subElems.remove(subElem);
                    }
                }
            }
        } else if (current instanceof Node.ScriptingElement) {
            checkScriptingBody((Node.ScriptingElement)current);
        }

        if ( isTagDependent(current)) {
            tagDependentNesting--;
        }

        if (scriptlessBodyNode != null
                && current.equals(scriptlessBodyNode)) {
            scriptlessBodyNode = null;
        }

        if (current.getParent() != null) {
            current = current.getParent();
        }
    }

    /*
     * 接收文档定位器.
     *
     * @param locator 文档定位器
     */
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }

    /*
     * See org.xml.sax.ext.LexicalHandler.
     */
    public void comment(char[] buf, int offset, int len) throws SAXException {

        processChars();  // 刷新char缓冲区并删除空白

        // 忽略DTD中的注释
        if (!inDTD) {
            startMark =
                new Mark(
                    ctxt,
                    path,
                    locator.getLineNumber(),
                    locator.getColumnNumber());
            new Node.Comment(new String(buf, offset, len), startMark, current);
        }
    }

    /*
     * See org.xml.sax.ext.LexicalHandler.
     */
    public void startCDATA() throws SAXException {

        processChars();  // 刷新char缓冲区并删除空白
        startMark = new Mark(ctxt, path, locator.getLineNumber(),
                             locator.getColumnNumber());
    }

    /*
     * See org.xml.sax.ext.LexicalHandler.
     */
    public void endCDATA() throws SAXException {
        processChars();  // 刷新char缓冲区并删除空白
    }

    /*
     * See org.xml.sax.ext.LexicalHandler.
     */
    public void startEntity(String name) throws SAXException {
        // do nothing
    }

    /*
     * See org.xml.sax.ext.LexicalHandler.
     */
    public void endEntity(String name) throws SAXException {
        // do nothing
    }

    /*
     * See org.xml.sax.ext.LexicalHandler.
     */
    public void startDTD(String name, String publicId, String systemId)
        throws SAXException {
        if (!isValidating) {
            fatalError(ENABLE_DTD_VALIDATION_EXCEPTION);
        }

        inDTD = true;
    }

    /*
     * See org.xml.sax.ext.LexicalHandler.
     */
    public void endDTD() throws SAXException {
        inDTD = false;
    }

    /*
     * 接收不可恢复错误的通知.
     */
    public void fatalError(SAXParseException e) throws SAXException {
        throw e;
    }

    /*
     * 接收可恢复错误的通知.
     */
    public void error(SAXParseException e) throws SAXException {
        throw e;
    }

    /*
     * 接收命名空间映射开始的通知. 
     */
    public void startPrefixMapping(String prefix, String uri)
        throws SAXException {
        TagLibraryInfo taglibInfo;
        try {
            taglibInfo = getTaglibInfo(prefix, uri);
        } catch (JasperException je) {
            throw new SAXParseException(
                Localizer.getMessage("jsp.error.could.not.add.taglibraries"),
                locator,
                je);
        }

        if (taglibInfo != null) {
            if (pageInfo.getTaglib(uri) == null) {
                pageInfo.addTaglib(uri, taglibInfo);
            }
            pageInfo.pushPrefixMapping(prefix, uri);
        } else {
            pageInfo.pushPrefixMapping(prefix, null);
        }
    }

    /*
     * 接收命名空间映射结束的通知. 
     */
    public void endPrefixMapping(String prefix) throws SAXException {
        pageInfo.popPrefixMapping(prefix);
    }

    //*********************************************************************
    // 实用方法
    private Node parseStandardAction(
        String qName,
        String localName,
        Attributes nonTaglibAttrs,
        Attributes nonTaglibXmlnsAttrs,
        Attributes taglibAttrs,
        Mark start,
        Node parent)
        throws SAXException {

        Node node = null;

        if (localName.equals(ROOT_ACTION)) {
            if (!(current instanceof Node.Root)) {
                throw new SAXParseException(
                    Localizer.getMessage("jsp.error.nested_jsproot"),
                    locator);
            }
            node =
                new Node.JspRoot(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
            if (isTop) {
                pageInfo.setHasJspRoot(true);
            }
        } else if (localName.equals(PAGE_DIRECTIVE_ACTION)) {
            if (isTagFile) {
                throw new SAXParseException(
                    Localizer.getMessage(
                        "jsp.error.action.istagfile",
                        localName),
                    locator);
            }
            node =
                new Node.PageDirective(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
            String imports = nonTaglibAttrs.getValue("import");
            // 每一个页面指令只能有一个 'import'属性
            if (imports != null) {
                ((Node.PageDirective)node).addImport(imports);
            }
        } else if (localName.equals(INCLUDE_DIRECTIVE_ACTION)) {
            node =
                new Node.IncludeDirective(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
            processIncludeDirective(nonTaglibAttrs.getValue("file"), node);
        } else if (localName.equals(DECLARATION_ACTION)) {
            if (scriptlessBodyNode != null) {
                // 嵌套在一个节点内, 其主体被声明为scriptless
                throw new SAXParseException(
                    Localizer.getMessage(
                        "jsp.error.no.scriptlets",
                        localName),
                    locator);
            }
            node =
                new Node.Declaration(
                    qName,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(SCRIPTLET_ACTION)) {
            if (scriptlessBodyNode != null) {
                // 嵌套在一个节点内, 其主体被声明为scriptless
                throw new SAXParseException(
                    Localizer.getMessage(
                        "jsp.error.no.scriptlets",
                        localName),
                    locator);
            }
            node =
                new Node.Scriptlet(
                    qName,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(EXPRESSION_ACTION)) {
            if (scriptlessBodyNode != null) {
                // 嵌套在一个节点内, 其主体被声明为scriptless
                throw new SAXParseException(
                    Localizer.getMessage(
                        "jsp.error.no.scriptlets",
                        localName),
                    locator);
            }
            node =
                new Node.Expression(
                    qName,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(USE_BEAN_ACTION)) {
            node =
                new Node.UseBean(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(SET_PROPERTY_ACTION)) {
            node =
                new Node.SetProperty(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(GET_PROPERTY_ACTION)) {
            node =
                new Node.GetProperty(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(INCLUDE_ACTION)) {
            node =
                new Node.IncludeAction(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(FORWARD_ACTION)) {
            node =
                new Node.ForwardAction(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(PARAM_ACTION)) {
            node =
                new Node.ParamAction(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(PARAMS_ACTION)) {
            node =
                new Node.ParamsAction(
                    qName,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(PLUGIN_ACTION)) {
            node =
                new Node.PlugIn(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(TEXT_ACTION)) {
            node =
                new Node.JspText(
                    qName,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(BODY_ACTION)) {
            node =
                new Node.JspBody(
                    qName,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(ATTRIBUTE_ACTION)) {
            node =
                new Node.NamedAttribute(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(OUTPUT_ACTION)) {
            node =
                new Node.JspOutput(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(TAG_DIRECTIVE_ACTION)) {
            if (!isTagFile) {
                throw new SAXParseException(
                    Localizer.getMessage(
                        "jsp.error.action.isnottagfile",
                        localName),
                    locator);
            }
            node =
                new Node.TagDirective(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
            String imports = nonTaglibAttrs.getValue("import");
            // 每一个标签指令只能有一个 'import'属性
            if (imports != null) {
                ((Node.TagDirective)node).addImport(imports);
            }
        } else if (localName.equals(ATTRIBUTE_DIRECTIVE_ACTION)) {
            if (!isTagFile) {
                throw new SAXParseException(
                    Localizer.getMessage(
                        "jsp.error.action.isnottagfile",
                        localName),
                    locator);
            }
            node =
                new Node.AttributeDirective(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(VARIABLE_DIRECTIVE_ACTION)) {
            if (!isTagFile) {
                throw new SAXParseException(
                    Localizer.getMessage(
                        "jsp.error.action.isnottagfile",
                        localName),
                    locator);
            }
            node =
                new Node.VariableDirective(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(INVOKE_ACTION)) {
            if (!isTagFile) {
                throw new SAXParseException(
                    Localizer.getMessage(
                        "jsp.error.action.isnottagfile",
                        localName),
                    locator);
            }
            node =
                new Node.InvokeAction(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(DOBODY_ACTION)) {
            if (!isTagFile) {
                throw new SAXParseException(
                    Localizer.getMessage(
                        "jsp.error.action.isnottagfile",
                        localName),
                    locator);
            }
            node =
                new Node.DoBodyAction(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(ELEMENT_ACTION)) {
            node =
                new Node.JspElement(
                    qName,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else if (localName.equals(FALLBACK_ACTION)) {
            node =
                new Node.FallBackAction(
                    qName,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    current);
        } else {
            throw new SAXParseException(
                Localizer.getMessage(
                    "jsp.error.xml.badStandardAction",
                    localName),
                locator);
        }

        return node;
    }

    /*
     * 检查给定标签名的 XML 元素是一个自定义行为, 并返回一个对应的 Node 对象.
     */
    private Node parseCustomAction(
        String qName,
        String localName,
        String uri,
        Attributes nonTaglibAttrs,
        Attributes nonTaglibXmlnsAttrs,
        Attributes taglibAttrs,
        Mark start,
        Node parent)
        throws SAXException {

        // 检查是否是用户定义的标签
        TagLibraryInfo tagLibInfo = pageInfo.getTaglib(uri);
        if (tagLibInfo == null) {
            return null;
        }

        TagInfo tagInfo = tagLibInfo.getTag(localName);
        TagFileInfo tagFileInfo = tagLibInfo.getTagFile(localName);
        if (tagInfo == null && tagFileInfo == null) {
            throw new SAXException(
                Localizer.getMessage("jsp.error.xml.bad_tag", localName, uri));
        }
        Class tagHandlerClass = null;
        if (tagInfo != null) {
            String handlerClassName = tagInfo.getTagClassName();
            try {
                tagHandlerClass =
                    ctxt.getClassLoader().loadClass(handlerClassName);
            } catch (Exception e) {
                throw new SAXException(
                    Localizer.getMessage("jsp.error.loadclass.taghandler",
                                         handlerClassName,
                                         qName),
                    e);
            }
        }

        String prefix = "";
        int colon = qName.indexOf(':');
        if (colon != -1) {
            prefix = qName.substring(0, colon);
        }

        Node.CustomTag ret = null;
        if (tagInfo != null) {
            ret =
                new Node.CustomTag(
                    qName,
                    prefix,
                    localName,
                    uri,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    parent,
                    tagInfo,
                    tagHandlerClass);
        } else {
            ret =
                new Node.CustomTag(
                    qName,
                    prefix,
                    localName,
                    uri,
                    nonTaglibAttrs,
                    nonTaglibXmlnsAttrs,
                    taglibAttrs,
                    start,
                    parent,
                    tagFileInfo);
        }

        return ret;
    }

    /*
     * 创建与给定URI名称空间关联的标签库, 并返回它.
     *
     * @param prefix xmlns属性的前缀
     * @param uri URI命名空间(xmlns属性的值)
     *
     * @return 与给定URI名称空间关联的标签库
     */
    private TagLibraryInfo getTaglibInfo(String prefix, String uri)
        throws JasperException {

        TagLibraryInfo result = null;

        if (uri.startsWith(URN_JSPTAGDIR)) {
            // uri ("urn:jsptagdir:path"的形式)引用标签文件目录
            String tagdir = uri.substring(URN_JSPTAGDIR.length());
            result =
                new ImplicitTagLibraryInfo(
                    ctxt,
                    parserController,
                    prefix,
                    tagdir,
                    err);
        } else {
            // uri 引用TLD文件
            boolean isPlainUri = false;
            if (uri.startsWith(URN_JSPTLD)) {
                // "urn:jsptld:path"形式的URI
                uri = uri.substring(URN_JSPTLD.length());
            } else {
                isPlainUri = true;
            }

            String[] location = ctxt.getTldLocation(uri);
            if (location != null || !isPlainUri) {
                if (ctxt.getOptions().isCaching()) {
                    result = (TagLibraryInfoImpl) ctxt.getOptions().getCache().get(uri);
                }
                if (result == null) {
                    /*
                     * 如果URI值是一个普通URI, 不能生成翻译错误, 如果URI没有在taglib的map中找到.
                     * 相反, 由URI值定义的命名空间的任何行动必须当作无解释的.
                     */
                    result =
                        new TagLibraryInfoImpl(
                            ctxt,
                            parserController,
                            prefix,
                            uri,
                            location,
                            err);
                    if (ctxt.getOptions().isCaching()) {
                        ctxt.getOptions().getCache().put(uri, result);
                    }
                }
            }
        }
        return result;
    }

    /*
     * 确定给定的主体只包含TemplateText实例的节点.
     *
     * 此检查仅对脚本元素的主体执行(即: 声明, 脚本片段, 或符号), 到达脚本元素的结束标签之后.
     */
    private void checkScriptingBody(Node.ScriptingElement scriptingElem)
        throws SAXException {
        Node.Nodes body = scriptingElem.getBody();
        if (body != null) {
            int size = body.size();
            for (int i = 0; i < size; i++) {
                Node n = body.getNode(i);
                if (!(n instanceof Node.TemplateText)) {
                    String elemType = SCRIPTLET_ACTION;
                    if (scriptingElem instanceof Node.Declaration)
                        elemType = DECLARATION_ACTION;
                    if (scriptingElem instanceof Node.Expression)
                        elemType = EXPRESSION_ACTION;
                    String msg =
                        Localizer.getMessage(
                            "jsp.error.parse.xml.scripting.invalid.body",
                            elemType);
                    throw new SAXException(msg);
                }
            }
        }
    }

    /*
     * 通过包含指令解析给定的文件.
     *
     * @param fname 包含资源的路径, 通过包含指令的'file'属性指定
     * @param parent 包含指令的Node
     */
    private void processIncludeDirective(String fname, Node parent) throws SAXException {

        if (fname == null) {
            return;
        }

        try {
            parserController.parse(fname, parent, null);
        } catch (FileNotFoundException fnfe) {
            throw new SAXParseException(
                Localizer.getMessage("jsp.error.file.not.found", fname),
                locator,
                fnfe);
        } catch (Exception e) {
            throw new SAXException(e);
        }
    }

    /*
     * 检查元素的给定URI, qname, 属性来查看它们的 'jsp' 前缀, 即, 将其绑定到命名空间除了 http://java.sun.com/JSP/Page.
     *
     * @param uri 元素的 URI
     * @param qName 元素的 qname
     * @param attrs 元素的 attributes
     */
    private void checkPrefixes(String uri, String qName, Attributes attrs) {

        checkPrefix(uri, qName);

        int len = attrs.getLength();
        for (int i = 0; i < len; i++) {
            checkPrefix(attrs.getURI(i), attrs.getQName(i));
        }
    }

    /*
     * 检查给定的 URI 和 qname 查看'jsp'前缀,
     * 如果qName 包含 'jsp' 前缀, 而且URI和 http://java.sun.com/JSP/Page不相同.
     *
     * @param uri 要检查的URI
     * @param qName 要检查的qname
     */
    private void checkPrefix(String uri, String qName) {

        int index = qName.indexOf(':');
        if (index != -1) {
            String prefix = qName.substring(0, index);
            pageInfo.addPrefix(prefix);
            if ("jsp".equals(prefix) && !JSP_URI.equals(uri)) {
                pageInfo.setIsJspPrefixHijacked(true);
            }
        }
    }

    /*
     * 获取 SAXParser.
     *
     * @param validating 是否应该验证请求的 SAXParser
     * @param jspDocParser JSP文档解析器
     *
     * @return The SAXParser
     */
    private static SAXParser getSAXParser(
        boolean validating,
        JspDocumentParser jspDocParser)
        throws Exception {

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);

        // 保存xmlns属性
        factory.setFeature(
            "http://xml.org/sax/features/namespace-prefixes",
            true);
        factory.setValidating(validating);
        //factory.setFeature(
        //    "http://xml.org/sax/features/validation",
        //    validating);
        
        // 配置解析器
        SAXParser saxParser = factory.newSAXParser();
        XMLReader xmlReader = saxParser.getXMLReader();
        xmlReader.setProperty(LEXICAL_HANDLER_PROPERTY, jspDocParser);
        xmlReader.setErrorHandler(jspDocParser);

        return saxParser;
    }

    /*
     * 表示 DOCTYPE 声明是存在的, 但是验证已经关闭.
     */
    private static class EnableDTDValidationException extends SAXParseException {

        EnableDTDValidationException(String message, Locator loc) {
            super(message, loc);
        }
    }

    private static String getBodyType(Node.CustomTag custom) {

        if (custom.getTagInfo() != null) {
            return custom.getTagInfo().getBodyContent();
        }

        return custom.getTagFileInfo().getTagInfo().getBodyContent();
    }

    private boolean isTagDependent(Node n) {

        if (n instanceof Node.CustomTag) {
            String bodyType = getBodyType((Node.CustomTag) n);
            return
                TagInfo.BODY_CONTENT_TAG_DEPENDENT.equalsIgnoreCase(bodyType);
        }
        return false;
    }
}
