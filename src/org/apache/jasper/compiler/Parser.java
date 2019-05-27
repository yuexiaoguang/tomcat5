package org.apache.jasper.compiler;

import java.io.CharArrayWriter;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import javax.servlet.jsp.tagext.TagAttributeInfo;
import javax.servlet.jsp.tagext.TagFileInfo;
import javax.servlet.jsp.tagext.TagInfo;
import javax.servlet.jsp.tagext.TagLibraryInfo;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

/**
 * 这个类实现了JSP 页面的解析器 (non-xml view).
 * JSP 页面语法包含在这里以供参考. 生成中显示的token '#'是当前生成中的输入令牌位置.
 */
class Parser implements TagConstants {

    private ParserController parserController;
    private JspCompilationContext ctxt;
    private JspReader reader;
    private String currentFile;
    private Mark start;
    private ErrorDispatcher err;
    private int scriptlessCount;
    private boolean isTagFile;
    private boolean directivesOnly;
    private URL jarFileUrl;
    private PageInfo pageInfo;

    // 虚拟主体内容类型, 使分析变得容易一些.
    // 这些是无法从解析器外部访问的.
    private static final String JAVAX_BODY_CONTENT_PARAM = 
        "JAVAX_BODY_CONTENT_PARAM";
    private static final String JAVAX_BODY_CONTENT_PLUGIN = 
        "JAVAX_BODY_CONTENT_PLUGIN";
    private static final String JAVAX_BODY_CONTENT_TEMPLATE_TEXT = 
        "JAVAX_BODY_CONTENT_TEMPLATE_TEXT";

    private Parser(ParserController pc, JspReader reader, boolean isTagFile,
		   boolean directivesOnly, URL jarFileUrl) {
		this.parserController = pc;
		this.ctxt = pc.getJspCompilationContext();
		this.pageInfo = pc.getCompiler().getPageInfo();
		this.err = pc.getCompiler().getErrorDispatcher();
		this.reader = reader;
		this.currentFile = reader.mark().getFile();
	        this.scriptlessCount = 0;
		this.isTagFile = isTagFile;
		this.directivesOnly = directivesOnly;
		this.jarFileUrl = jarFileUrl;
        start = reader.mark();
    }

    /**
     * 解析器的主要条目
     * 
     * @param pc ParseController, 用于获取编译器中的其他对象和解析包含的页面
     * @param reader 读取页面
     * @param parent 此页面的父节点, 对于顶级页面是null
     * @return 表示解析页面的节点列表
     */
    public static Node.Nodes parse(ParserController pc,
				   JspReader reader,
				   Node parent,
				   boolean isTagFile,
				   boolean directivesOnly,
				   URL jarFileUrl,
				   String pageEnc,
				   String jspConfigPageEnc,
				   boolean isDefaultPageEncoding)
		throws JasperException {

		Parser parser = new Parser(pc, reader, isTagFile, directivesOnly,
					   jarFileUrl);
	
		Node.Root root = new Node.Root(reader.mark(), parent, false);
		root.setPageEncoding(pageEnc);
		root.setJspConfigPageEncoding(jspConfigPageEnc);
		root.setIsDefaultPageEncoding(isDefaultPageEncoding);
	
		if (directivesOnly) {
		    parser.parseTagFileDirectives(root);
		    return new Node.Nodes(root);
		}
	
		// 对于顶级页面, add inlcude-prelude and include-coda
		PageInfo pageInfo = pc.getCompiler().getPageInfo();
		if (parent == null) {
		    parser.addInclude(root, pageInfo.getIncludePrelude());
		}
		while (reader.hasMoreInput()) {
		    parser.parseElements(root);
		}
		if (parent == null) {
		    parser.addInclude(root, pageInfo.getIncludeCoda());
		}
	
		Node.Nodes page = new Node.Nodes(root);
		return page;
    }

    /**
     * Attributes ::= (S Attribute)* S?
     */
    Attributes parseAttributes() throws JasperException {
		AttributesImpl attrs = new AttributesImpl();
	
		reader.skipSpaces();
		while (parseAttribute(attrs))
		    reader.skipSpaces();
	
		return attrs;
    }

    /**
     * 为一个reader解析属性, 提供额外的用途
     */
    public static Attributes parseAttributes(ParserController pc,
					     JspReader reader)
		throws JasperException {
		Parser tmpParser = new Parser(pc, reader, false, false, null);
		return tmpParser.parseAttributes();
    }

    /**
     * Attribute ::= Name S? Eq S?
     *               (   '"<%=' RTAttributeValueDouble
     *                 | '"' AttributeValueDouble
     *                 | "'<%=" RTAttributeValueSingle
     *                 | "'" AttributeValueSingle
     *               }
     * Note: JSP 和 XML 规范不允许 Eq周围有空格. 它被添加到向后兼容的Tomcat中, 还有其他的XML解析器.
     */
    private boolean parseAttribute(AttributesImpl attrs)
	        throws JasperException {

		// 获取限定名称
		String qName = parseName();
		if (qName == null)
		    return false;
	
		// 确定前缀和本地名称组件
		String localName = qName;
		String uri = "";
		int index = qName.indexOf(':');
		if (index != -1) {
		    String prefix = qName.substring(0, index);
		    uri = pageInfo.getURI(prefix);
		    if (uri == null) {
				err.jspError(reader.mark(),
					     "jsp.error.attribute.invalidPrefix", prefix);
		    }
		    localName = qName.substring(index+1);
		}
	
	 	reader.skipSpaces();
		if (!reader.matches("="))
		    err.jspError(reader.mark(), "jsp.error.attribute.noequal");
	
	 	reader.skipSpaces();
		char quote = (char) reader.nextChar();
		if (quote != '\'' && quote != '"')
		    err.jspError(reader.mark(), "jsp.error.attribute.noquote");
	
	 	String watchString = "";
		if (reader.matches("<%="))
		    watchString = "%>";
		watchString = watchString + quote;
		
		String attrValue = parseAttributeValue(watchString);
		attrs.addAttribute(uri, localName, qName, "CDATA", attrValue);
		return true;
    }

    /**
     * Name ::= (Letter | '_' | ':') (Letter | Digit | '.' | '_' | '-' | ':')*
     */
    private String parseName() throws JasperException {
		char ch = (char)reader.peekChar();
		if (Character.isLetter(ch) || ch == '_' || ch == ':') {
		    StringBuffer buf = new StringBuffer();
		    buf.append(ch);
		    reader.nextChar();
		    ch = (char)reader.peekChar();
		    while (Character.isLetter(ch) || Character.isDigit(ch) ||
				ch == '.' || ch == '_' || ch == '-' || ch == ':') {
				buf.append(ch);
				reader.nextChar();
				ch = (char) reader.peekChar();
		    }
		    return buf.toString();
		}
		return null;
    }

    /**
     * AttributeValueDouble ::= (QuotedChar - '"')*
     *				('"' | <TRANSLATION_ERROR>)
     * RTAttributeValueDouble ::= ((QuotedChar - '"')* - ((QuotedChar-'"')'%>"')
     *				  ('%>"' | TRANSLATION_ERROR)
     */
    private String parseAttributeValue(String watch) throws JasperException {
		Mark start = reader.mark();
		Mark stop = reader.skipUntilIgnoreEsc(watch);
		if (stop == null) {
		    err.jspError(start, "jsp.error.attribute.unterminated", watch);
		}
	
		String ret = parseQuoted(reader.getText(start, stop));
		if (watch.length() == 1)	// 引号
		    return ret;
	
		// 回放分隔符 '<%=' 和 '%>', 由于属性不允许RTexpression，它们是必需的.
		return "<%=" + ret + "%>";
    }

    /**
     * QuotedChar ::=   '&apos;'
     *	              | '&quot;'
     *                | '\\'
     *                | '\"'
     *                | "\'"
     *                | '\>'
     *                | '\$'
     *                | Char
     */
    private String parseQuoted(String tx) {
		StringBuffer buf = new StringBuffer();
		int size = tx.length();
		int i = 0;
		while (i < size) {
		    char ch = tx.charAt(i);
		    if (ch == '&') {
				if (i+5 < size && tx.charAt(i+1) == 'a'
				        && tx.charAt(i+2) == 'p' && tx.charAt(i+3) == 'o'
				        && tx.charAt(i+4) == 's' && tx.charAt(i+5) == ';') {
				    buf.append('\'');
				    i += 6;
				} else if (i+5 < size && tx.charAt(i+1) == 'q'
					   && tx.charAt(i+2) == 'u' && tx.charAt(i+3) == 'o'
					   && tx.charAt(i+4) == 't' && tx.charAt(i+5) == ';') {
				    buf.append('"');
				    i += 6;
				} else {
				    buf.append(ch);
				    ++i;
				}
		    } else if (ch == '\\' && i+1 < size) {
				ch = tx.charAt(i+1);
				if (ch == '\\' || ch == '\"' || ch == '\'' || ch == '>') {
				    buf.append(ch);
				    i += 2;
				} else if (ch == '$') {
				    // 用一些特别的字符替换 "\$".  XXX hack!
				    buf.append(Constants.ESC);
				    i += 2;
				} else {
				    buf.append('\\');
				    ++i;
				}
		    } else {
				buf.append(ch);
				++i;
		    }
		}
		return buf.toString();
    }

    private String parseScriptText(String tx) {
		CharArrayWriter cw = new CharArrayWriter();
		int size = tx.length();
		int i = 0;
		while (i < size) {
		    char ch = tx.charAt(i);
		    if (i+2 < size && ch == '%' && tx.charAt(i+1) == '\\'
			    && tx.charAt(i+2) == '>') {
				cw.write('%');
				cw.write('>');
				i += 3;
		    } else {
				cw.write(ch);
				++i;
		    }
		}
		cw.close();
		return cw.toString();
    }

    /*
     * 执行parserController解析包含的页面
     */
    private void processIncludeDirective(String file, Node parent) throws JasperException {
		if (file == null) {
		    return;
		}
	
		try {
		    parserController.parse(file, parent, jarFileUrl);
		} catch (FileNotFoundException ex) {
		    err.jspError(start, "jsp.error.file.not.found", file);
		} catch (Exception ex) {
		    err.jspError(start, ex.getMessage());
		}
    }

    /*
     * 用以下语法解析页面指令:
     *   PageDirective ::= ( S Attribute)*
     */
    private void parsePageDirective(Node parent) throws JasperException {
		Attributes attrs = parseAttributes();
		Node.PageDirective n = new Node.PageDirective(attrs, start, parent);
		/*
		 * 页面指令可以包含多个 'import'属性, 每一个都由一个逗号分隔的包名列表组成.
		 * 用节点存储每个列表.
		 */
		for (int i = 0; i < attrs.getLength(); i++) {
		    if ("import".equals(attrs.getQName(i))) {
		    	n.addImport(attrs.getValue(i));
		    }
		}
    }

    /*
     * 用以下语法解析一个包含指令:
     *   IncludeDirective ::= ( S Attribute)*
     */
    private void parseIncludeDirective(Node parent) throws JasperException {
		Attributes attrs = parseAttributes();
	
		// 包含的文件在这里展开
		Node includeNode = new Node.IncludeDirective(attrs, start, parent);
		processIncludeDirective(attrs.getValue("file"), includeNode);
    }

    /**
     * 添加文件列表. 用于实现web.xml中的jsp-config元素的 include-prelude和include-coda
     */
    private void addInclude(Node parent, List files) throws JasperException {
        if( files != null ) {
            Iterator iter = files.iterator();
            while (iter.hasNext()) {
                String file = (String) iter.next();
                AttributesImpl attrs = new AttributesImpl();
                attrs.addAttribute("", "file", "file", "CDATA", file);

                // 创建一个虚拟的包含指令节点
                Node includeNode = new Node.IncludeDirective(attrs, 
                    reader.mark(), parent);
                processIncludeDirective(file, includeNode);
            }
        }
    }

    /*
     * 解析taglib伪指令用下面的语法:
     *   Directive ::= ( S Attribute)*
     */
    private void parseTaglibDirective(Node parent) throws JasperException {

		Attributes attrs = parseAttributes();
		String uri = attrs.getValue("uri");
		String prefix = attrs.getValue("prefix");
		if (prefix != null) {
	            Mark prevMark = pageInfo.getNonCustomTagPrefix(prefix);
	            if (prevMark != null) {
	                err.jspError(reader.mark(), "jsp.error.prefix.use_before_dcl",
	                    prefix, prevMark.getFile(), "" + prevMark.getLineNumber());
	            }
		    if (uri != null) {
				String uriPrev = pageInfo.getURI(prefix);
				if (uriPrev != null && !uriPrev.equals(uri)) {
				    err.jspError(reader.mark(), "jsp.error.prefix.refined",
					prefix, uri, uriPrev);
				}
				if (pageInfo.getTaglib(uri) == null) {
		            TagLibraryInfoImpl impl = null;
		            if (ctxt.getOptions().isCaching()) {
		                impl = (TagLibraryInfoImpl) ctxt.getOptions().getCache().get(uri);
		            }
		            if (impl == null) {
		                String[] location = ctxt.getTldLocation(uri);
		                impl = new TagLibraryInfoImpl(ctxt,
		                        parserController,
		                        prefix,
		                        uri,
		                        location,
		                        err);
		                if (ctxt.getOptions().isCaching()) {
		                    ctxt.getOptions().getCache().put(uri, impl);
		                }
		            }
				    pageInfo.addTaglib(uri, impl);
				}
				pageInfo.addPrefixMapping(prefix, uri);
		    } else {
				String tagdir = attrs.getValue("tagdir");
				if (tagdir != null) {
				    String urnTagdir = URN_JSPTAGDIR + tagdir;
				    if (pageInfo.getTaglib(urnTagdir) == null) {
						pageInfo.addTaglib(urnTagdir,
								   new ImplicitTagLibraryInfo(ctxt,
									   parserController,
									   prefix, 
									   tagdir,
									   err));
				    }
				    pageInfo.addPrefixMapping(prefix, urnTagdir);
				}
		    }
		}
		new Node.TaglibDirective(attrs, start, parent);
    }

    /*
     * 用以下语法解析指令:
     *   Directive ::= S? (   'page' PageDirective
     *			    | 'include' IncludeDirective
     *			    | 'taglib' TagLibDirective)
     *		       S? '%>'
     *
     *   TagDirective ::= S? ('tag' PageDirective
     *			    | 'include' IncludeDirective
     *			    | 'taglib' TagLibDirective)
     *                      | 'attribute AttributeDirective
     *                      | 'variable VariableDirective
     *		       S? '%>'
     */
    private void parseDirective(Node parent) throws JasperException {
		reader.skipSpaces();
	
		String directive = null;
		if (reader.matches("page")) {
		    directive = "&lt;%@ page";
		    if (isTagFile) {
			err.jspError(reader.mark(), "jsp.error.directive.istagfile",
						    directive);
		    }
		    parsePageDirective(parent);
		} else if (reader.matches("include")) {
		    directive = "&lt;%@ include";
		    parseIncludeDirective(parent);
		} else if (reader.matches("taglib")) {
		    if (directivesOnly) {
		        // 不需要获取tagLibInfo 对象. 这也阻止了这个标签文件中使用的其他标签文件的解析.
		        return;
		    }
		    directive = "&lt;%@ taglib";
		    parseTaglibDirective(parent);
		} else if (reader.matches("tag")) {
		    directive = "&lt;%@ tag";
		    if (!isTagFile) {
			err.jspError(reader.mark(), "jsp.error.directive.isnottagfile",
						    directive);
		    }
		    parseTagDirective(parent);
		} else if (reader.matches("attribute")) {
		    directive = "&lt;%@ attribute";
		    if (!isTagFile) {
			err.jspError(reader.mark(), "jsp.error.directive.isnottagfile",
						    directive);
		    }
		    parseAttributeDirective(parent);
		} else if (reader.matches("variable")) {
		    directive = "&lt;%@ variable";
		    if (!isTagFile) {
			err.jspError(reader.mark(), "jsp.error.directive.isnottagfile",
						    directive);
		    }
		    parseVariableDirective(parent);
		} else {
		    err.jspError(reader.mark(), "jsp.error.invalid.directive");
		}
	
		reader.skipSpaces();
		if (!reader.matches("%>")) {
		    err.jspError(start, "jsp.error.unterminated", directive);
		}
    }
	
    /*
     * 用以下语法解析指令:
     *
     *   XMLJSPDirectiveBody ::= S? (   ( 'page' PageDirectiveAttrList
     *                                    S? ( '/>' | ( '>' S? ETag ) )
     *                               | ( 'include' IncludeDirectiveAttrList
     *                                    S? ( '/>' | ( '>' S? ETag ) )
     *                           | <TRANSLATION_ERROR>
     *
     *   XMLTagDefDirectiveBody ::= (   ( 'tag' TagDirectiveAttrList
     *                                    S? ( '/>' | ( '>' S? ETag ) )
     *                                | ( 'include' IncludeDirectiveAttrList
     *                                    S? ( '/>' | ( '>' S? ETag ) )
     *                                | ( 'attribute' AttributeDirectiveAttrList
     *                                    S? ( '/>' | ( '>' S? ETag ) )
     *                                | ( 'variable' VariableDirectiveAttrList
     *                                    S? ( '/>' | ( '>' S? ETag ) )
     *                              )
     *                            | <TRANSLATION_ERROR>
     */
    private void parseXMLDirective(Node parent) throws JasperException {
       reader.skipSpaces();

       String eTag = null;
       if (reader.matches("page")) {
            eTag = "jsp:directive.page";
           if (isTagFile) {
               err.jspError(reader.mark(), "jsp.error.directive.istagfile",
                                           "&lt;" + eTag);
           }
           parsePageDirective(parent);
       } else if (reader.matches("include")) {
            eTag = "jsp:directive.include";
           parseIncludeDirective(parent);
       } else if (reader.matches("tag")) {
            eTag = "jsp:directive.tag";
           if (!isTagFile) {
               err.jspError(reader.mark(), "jsp.error.directive.isnottagfile",
                                           "&lt;" + eTag);
           }
           parseTagDirective(parent);
       } else if (reader.matches("attribute")) {
            eTag = "jsp:directive.attribute";
           if (!isTagFile) {
               err.jspError(reader.mark(), "jsp.error.directive.isnottagfile",
                                           "&lt;" + eTag);
           }
           parseAttributeDirective(parent);
       } else if (reader.matches("variable")) {
            eTag = "jsp:directive.variable";
           if (!isTagFile) {
               err.jspError(reader.mark(), "jsp.error.directive.isnottagfile",
                                           "&lt;" + eTag);
           }
           parseVariableDirective(parent);
       } else {
           err.jspError(reader.mark(), "jsp.error.invalid.directive");
       }

       reader.skipSpaces();
        if( reader.matches( ">" ) ) {
            reader.skipSpaces();
            if( !reader.matchesETag( eTag ) ) {
                err.jspError(start, "jsp.error.unterminated", "&lt;" + eTag );
            }
        }
        else if( !reader.matches( "/>" ) ) {
            err.jspError(start, "jsp.error.unterminated", "&lt;" + eTag );
        }
    }

    /*
     * 用以下语法解析标记指令:
     *   PageDirective ::= ( S Attribute)*
     */
    private void parseTagDirective(Node parent) throws JasperException {
		Attributes attrs = parseAttributes();
		Node.TagDirective n = new Node.TagDirective(attrs, start, parent);
        /*
         * 一个页面指令可以包含多个 'import'属性, 每一个都由一个逗号分隔的包名列表组成.
         * 用节点存储每个列表.
         */
        for (int i = 0; i < attrs.getLength(); i++) {
            if ("import".equals(attrs.getQName(i))) {
                n.addImport(attrs.getValue(i));
            }
        }
    }

    /*
     * 用以下语法解析属性指令:
     *   AttributeDirective ::= ( S Attribute)*
     */
    private void parseAttributeDirective(Node parent) throws JasperException {
		Attributes attrs = parseAttributes();
		Node.AttributeDirective n =
			new Node.AttributeDirective(attrs, start, parent);
    }

    /*
     * 用以下语法解析变量指令:
     *   PageDirective ::= ( S Attribute)*
     */
    private void parseVariableDirective(Node parent) throws JasperException {
		Attributes attrs = parseAttributes();
		Node.VariableDirective n =
			new Node.VariableDirective(attrs, start, parent);
    }

    /*
     * JSPCommentBody ::= (Char* - (Char* '--%>')) '--%>'
     */
    private void parseComment(Node parent) throws JasperException {	
		start = reader.mark();
		Mark stop = reader.skipUntil("--%>");
		if (stop == null) {
		    err.jspError(start, "jsp.error.unterminated", "&lt;%--");
		}
	
		new Node.Comment(reader.getText(start, stop), start, parent);
    }

    /*
     * DeclarationBody ::= (Char* - (char* '%>')) '%>'
     */
    private void parseDeclaration(Node parent) throws JasperException {
		start = reader.mark();
		Mark stop = reader.skipUntil("%>");
		if (stop == null) {
		    err.jspError(start, "jsp.error.unterminated", "&lt;%!");
		}
	
		new Node.Declaration(parseScriptText(reader.getText(start, stop)),
				     start, parent);
    }

    /*
     * XMLDeclarationBody ::=   ( S? '/>' )
     *                        | ( S? '>' (Char* - (char* '<')) CDSect?)* ETag
     *                        | <TRANSLATION_ERROR>
     * CDSect ::= CDStart CData CDEnd
     * CDStart ::= '<![CDATA['
     * CData ::= (Char* - (Char* ']]>' Char*))
     * CDEnd ::= ']]>'
     */
    private void parseXMLDeclaration(Node parent) throws JasperException {
        reader.skipSpaces();
        if( !reader.matches( "/>" ) ) {
            if( !reader.matches( ">" ) ) {
                err.jspError(start, "jsp.error.unterminated",
                        "&lt;jsp:declaration&gt;");
            }
            Mark stop;
            String text;
            while (true) {
                start = reader.mark();
                stop = reader.skipUntil("<");
                if (stop == null) {
                    err.jspError(start, "jsp.error.unterminated",
                        "&lt;jsp:declaration&gt;");
                }
                text = parseScriptText(reader.getText(start, stop));
                new Node.Declaration(text, start, parent);
                if (reader.matches("![CDATA[")) {
                    start = reader.mark();
                    stop = reader.skipUntil("]]>");
                    if (stop == null) {
                        err.jspError(start, "jsp.error.unterminated", "CDATA");
                    }
                    text = parseScriptText(reader.getText(start, stop));
                    new Node.Declaration(text, start, parent);
                }
                else {
                    break;
                }
           }
		
            if (!reader.matchesETagWithoutLessThan( "jsp:declaration" ) ) {
                err.jspError(start, "jsp.error.unterminated",
                        "&lt;jsp:declaration&gt;");
            }
        }
    }

    /*
     * ExpressionBody ::= (Char* - (char* '%>')) '%>'
     */
    private void parseExpression(Node parent) throws JasperException {
		start = reader.mark();
		Mark stop = reader.skipUntil("%>");
		if (stop == null) {
		    err.jspError(start, "jsp.error.unterminated", "&lt;%=");
		}
	
		new Node.Expression(parseScriptText(reader.getText(start, stop)),
				    start, parent);
    }

    /*
     * XMLExpressionBody ::=   ( S? '/>' )
     *                       | ( S? '>' (Char* - (char* '<')) CDSect?)* ETag )
     *                       | <TRANSLATION_ERROR>
     */
    private void parseXMLExpression(Node parent) throws JasperException {
        reader.skipSpaces();
        if( !reader.matches( "/>" ) ) {
            if( !reader.matches( ">" ) ) {
                err.jspError(start, "jsp.error.unterminated",
                    "&lt;jsp:expression&gt;");
            }
            Mark stop;
            String text;
            while (true) {
                start = reader.mark();
                stop = reader.skipUntil("<");
                if (stop == null) {
                    err.jspError(start, "jsp.error.unterminated",
                        "&lt;jsp:expression&gt;");
                }
                text = parseScriptText(reader.getText(start, stop));
                new Node.Expression(text, start, parent);
                if (reader.matches("![CDATA[")) {
                    start = reader.mark();
                    stop = reader.skipUntil("]]>");
                    if (stop == null) {
                        err.jspError(start, "jsp.error.unterminated", "CDATA");
                    }
                    text = parseScriptText(reader.getText(start, stop));
                    new Node.Expression(text, start, parent);
                }
                else {
                    break;
                }
            }
            if (!reader.matchesETagWithoutLessThan( "jsp:expression" )) {
                err.jspError(start, "jsp.error.unterminated",
                    "&lt;jsp:expression&gt;");
            }
        }
    }

    /*
     * ELExpressionBody
     * (following "${" to first unquoted "}")
     * // XXX 添加正式生成并确认实现, 一旦决定
     */
    private void parseELExpression(Node parent) throws JasperException {
        start = reader.mark();
        Mark last = null;
        boolean singleQuoted = false, doubleQuoted = false;
        int currentChar;
        do {
            // XXX 可以把这个逻辑移到JspReader
            last = reader.mark();               // XXX somewhat wasteful
            currentChar = reader.nextChar();
            if (currentChar == '\\' && (singleQuoted || doubleQuoted)) {
                // 跳过引号中 '\' 后面的字符
                reader.nextChar();
                currentChar = reader.nextChar();
            }
            if (currentChar == -1)
                err.jspError(start, "jsp.error.unterminated", "${");
            if (currentChar == '"')
                doubleQuoted = !doubleQuoted;
            if (currentChar == '\'')
                singleQuoted = !singleQuoted;
        } while (currentChar != '}' || (singleQuoted || doubleQuoted));

        new Node.ELExpression(reader.getText(start, last), start, parent);
    }

    /*
     * ScriptletBody ::= (Char* - (char* '%>')) '%>'
     */
    private void parseScriptlet(Node parent) throws JasperException {
		start = reader.mark();
		Mark stop = reader.skipUntil("%>");
		if (stop == null) {
		    err.jspError(start, "jsp.error.unterminated", "&lt;%");
		}
	
		new Node.Scriptlet(parseScriptText(reader.getText(start, stop)),
				   start, parent);
    }

    /*
     * XMLScriptletBody ::=   ( S? '/>' )
     *                      | ( S? '>' (Char* - (char* '<')) CDSect?)* ETag )
     *                      | <TRANSLATION_ERROR>
     */
    private void parseXMLScriptlet(Node parent) throws JasperException {
        reader.skipSpaces();
        if( !reader.matches( "/>" ) ) {
            if( !reader.matches( ">" ) ) {
                err.jspError(start, "jsp.error.unterminated",
                    "&lt;jsp:scriptlet&gt;");
            }
            Mark stop;
            String text;
            while (true) {
                start = reader.mark();
                stop = reader.skipUntil("<");
                if (stop == null) {
                    err.jspError(start, "jsp.error.unterminated",
                        "&lt;jsp:scriptlet&gt;");
                }
                text = parseScriptText(reader.getText(start, stop));
                new Node.Scriptlet(text, start, parent);
                if (reader.matches("![CDATA[")) {
                    start = reader.mark();
                    stop = reader.skipUntil("]]>");
                    if (stop == null) {
                        err.jspError(start, "jsp.error.unterminated", "CDATA");
                    }
                    text = parseScriptText(reader.getText(start, stop));
                    new Node.Scriptlet(text, start, parent);
                }
                else {
                    break;
                }
            }

            if (!reader.matchesETagWithoutLessThan( "jsp:scriptlet" )) {
                err.jspError(start, "jsp.error.unterminated",
                    "&lt;jsp:scriptlet&gt;");
            }
        }
    }
	
    /**
     * Param ::= '<jsp:param' S Attributes S? EmptyBody S?
     */
    private void parseParam(Node parent) throws JasperException {
		if (!reader.matches("<jsp:param")) {
		    err.jspError(reader.mark(), "jsp.error.paramexpected");
		}
		Attributes attrs = parseAttributes();
		reader.skipSpaces();
        
        Node paramActionNode = new Node.ParamAction( attrs, start, parent );
        
        parseEmptyBody( paramActionNode, "jsp:param" );
        
        reader.skipSpaces();
    }

    /*
     * 对于Include:
     * StdActionContent ::= Attributes ParamBody
     *
     * ParamBody ::=   EmptyBody
     *               | ( '>' S? ( '<jsp:attribute' NamedAttributes )?
     *                   '<jsp:body'
     *                   (JspBodyParam | <TRANSLATION_ERROR> )
     *                   S? ETag
     *                 )
     *               | ( '>' S? Param* ETag )
     *
     * EmptyBody ::=   '/>'
     *               | ( '>' ETag )
     *               | ( '>' S? '<jsp:attribute' NamedAttributes ETag )
     *
     * JspBodyParam ::= S? '>' Param* '</jsp:body>'
     */
    private void parseInclude(Node parent) throws JasperException {
		Attributes attrs = parseAttributes();
		reader.skipSpaces();

        Node includeNode = new Node.IncludeAction( attrs, start, parent );
        
        parseOptionalBody(includeNode, "jsp:include", 
			  JAVAX_BODY_CONTENT_PARAM);
    }

    /*
     * For Forward:
     * StdActionContent ::= Attributes ParamBody
     */
    private void parseForward(Node parent) throws JasperException {
		Attributes attrs = parseAttributes();
		reader.skipSpaces();

        Node forwardNode = new Node.ForwardAction( attrs, start, parent );
        
        parseOptionalBody(forwardNode, "jsp:forward",
			  JAVAX_BODY_CONTENT_PARAM);
    }

    private void parseInvoke(Node parent) throws JasperException {
		Attributes attrs = parseAttributes();
		reader.skipSpaces();

        Node invokeNode = new Node.InvokeAction(attrs, start, parent);
        
        parseEmptyBody(invokeNode, "jsp:invoke");
    }

    private void parseDoBody(Node parent) throws JasperException {
		Attributes attrs = parseAttributes();
		reader.skipSpaces();

        Node doBodyNode = new Node.DoBodyAction(attrs, start, parent);
        
        parseEmptyBody(doBodyNode, "jsp:doBody");
    }

    private void parseElement(Node parent) throws JasperException {
		Attributes attrs = parseAttributes();
		reader.skipSpaces();

        Node elementNode = new Node.JspElement(attrs, start, parent);
        
        parseOptionalBody( elementNode, "jsp:element", TagInfo.BODY_CONTENT_JSP );
    }

    /*
     * For GetProperty:
     * StdActionContent ::= Attributes EmptyBody
     */
    private void parseGetProperty(Node parent) throws JasperException {
		Attributes attrs = parseAttributes();
		reader.skipSpaces();

        Node getPropertyNode = new Node.GetProperty( attrs, start, parent );
        
        parseOptionalBody(getPropertyNode, "jsp:getProperty", TagInfo.BODY_CONTENT_EMPTY);
    }

    /*
     * For SetProperty:
     * StdActionContent ::= Attributes EmptyBody
     */
    private void parseSetProperty(Node parent) throws JasperException {
		Attributes attrs = parseAttributes();
		reader.skipSpaces();

        Node setPropertyNode = new Node.SetProperty( attrs, start, parent );
        
        parseOptionalBody(setPropertyNode, "jsp:setProperty", TagInfo.BODY_CONTENT_EMPTY);
    }

    /*
     * EmptyBody ::=   '/>'
     *               | ( '>' ETag )
     *               | ( '>' S? '<jsp:attribute' NamedAttributes ETag )
     */
    private void parseEmptyBody( Node parent, String tag ) 
        throws JasperException {
    	if( reader.matches("/>") ) {
            // Done
        } else if( reader.matches( ">" ) ) {
            if( reader.matchesETag( tag ) ) {
                // Done
            } else if( reader.matchesOptionalSpacesFollowedBy(
                "<jsp:attribute" ) ) {
                // Parse the one or more named attribute nodes
                parseNamedAttributes( parent );
                if( !reader.matchesETag( tag ) ) {
                    // Body not allowed
                    err.jspError(reader.mark(),
                        "jsp.error.jspbody.emptybody.only",
                        "&lt;" + tag );
                }
            } else {
                err.jspError(reader.mark(), "jsp.error.jspbody.emptybody.only",
                    "&lt;" + tag );
            }
        } else {
	    err.jspError(reader.mark(), "jsp.error.unterminated",
                "&lt;" + tag );
        }
    }

    /*
     * For UseBean:
     * StdActionContent ::= Attributes OptionalBody
     */
    private void parseUseBean(Node parent) throws JasperException {
		Attributes attrs = parseAttributes();
		reader.skipSpaces();
        
        Node useBeanNode = new Node.UseBean( attrs, start, parent );
        
        parseOptionalBody( useBeanNode, "jsp:useBean", TagInfo.BODY_CONTENT_JSP );
    }

    /*
     * 解析 OptionalBody, 也用于解析插件主体和参数, 因为语法是相同的(实质上唯一不同的是如何处理主体, 因此我们接受主体类型作为参数).
     *
     * OptionalBody ::= EmptyBody | ActionBody
     *
     * ScriptlessOptionalBody ::= EmptyBody | ScriptlessActionBody
     *
     * TagDependentOptionalBody ::= EmptyBody | TagDependentActionBody
     *
     * EmptyBody ::=   '/>'
     *               | ( '>' ETag )
     *               | ( '>' S? '<jsp:attribute' NamedAttributes ETag )
     *
     * ActionBody ::=   JspAttributeAndBody
     *                | ( '>' Body ETag )
     *
     * ScriptlessActionBody ::=   JspAttributeAndBody 
     *                          | ( '>' ScriptlessBody ETag )
     * 
     * TagDependentActionBody ::=   JspAttributeAndBody
     *                            | ( '>' TagDependentBody ETag )
     *
     */
    private void parseOptionalBody( Node parent, String tag, String bodyType ) 
        throws JasperException  {
		if (reader.matches("/>")) {
		    // EmptyBody
		    return;
		}
	
		if (!reader.matches(">")) {
		    err.jspError(reader.mark(), "jsp.error.unterminated",
				 "&lt;" + tag );
		}
        
        if( reader.matchesETag( tag ) ) {
            // EmptyBody
            return;
        }
        
        if( !parseJspAttributeAndBody( parent, tag, bodyType ) ) {
            // Must be ( '>' # Body ETag )
            parseBody(parent, tag, bodyType );
        }
    }
    
    /**
     * 尝试解析'JspAttributeAndBody'产品. 返回true 如果匹配, 或者false. 假设EmptyBody也是可以的.
     *
     * JspAttributeAndBody ::=
     *                  ( '>' # S? ( '<jsp:attribute' NamedAttributes )?
     *                    '<jsp:body'
     *                    ( JspBodyBody | <TRANSLATION_ERROR> )
     *                    S? ETag
     *                  )
     */
    private boolean parseJspAttributeAndBody( Node parent, String tag, 
        String bodyType ) throws JasperException {
        boolean result = false;
        
        if( reader.matchesOptionalSpacesFollowedBy( "<jsp:attribute" ) ) {
            // 可能是一个 EmptyBody, 根据在ETag之前是否有一个 "<jsp:body"
            
            // 首先, 解析<jsp:attribute>元素:
            parseNamedAttributes( parent );
            result = true;
        }
        
        if( reader.matchesOptionalSpacesFollowedBy( "<jsp:body" ) ) {
            // ActionBody
            parseJspBody( parent, bodyType );
            reader.skipSpaces();
            if( !reader.matchesETag( tag ) ) {
                err.jspError(reader.mark(), "jsp.error.unterminated", 
                    "&lt;" + tag );
            }
            
            result = true;
        }
        else if( result && !reader.matchesETag( tag ) ) {
            // 如果有<jsp:attribute>, 除了别的<jsp:body>或结束标签, 翻译错误.
            err.jspError(reader.mark(), "jsp.error.jspbody.required", 
                "&lt;" + tag );
        }
        return result;
    }

    /*
     * Params ::=  `>' S?
     *              (   ( `<jsp:body>'
     *                    ( ( S? Param+ S? `</jsp:body>' )
     *                      | <TRANSLATION_ERROR>
     *                    )
     *                  )
     *                | Param+
     *              )
     *              '</jsp:params>'
     */
    private void parseJspParams(Node parent) throws JasperException {
		Node jspParamsNode = new Node.ParamsAction(start, parent);
		parseOptionalBody(jspParamsNode, "jsp:params", JAVAX_BODY_CONTENT_PARAM );
    }

    /*
     * Fallback ::=   '/>'
     *               | ( `>' S? `<jsp:body>'
     *                   (   ( S?
     *                         ( Char* - ( Char* `</jsp:body>' ) )
     *                         `</jsp:body>' S?
     *                       )
     *                     | <TRANSLATION_ERROR>
     *                   )
     *                   `</jsp:fallback>'
     *                 )
     *               | ( '>'
     *                   ( Char* - ( Char* '</jsp:fallback>' ) )
     *                   '</jsp:fallback>'
     *                 )
     */
    private void parseFallBack(Node parent) throws JasperException {
		Node fallBackNode = new Node.FallBackAction(start, parent);
		parseOptionalBody(fallBackNode, "jsp:fallback", JAVAX_BODY_CONTENT_TEMPLATE_TEXT);
    }

    /*
     * For Plugin:
     * StdActionContent ::= Attributes PluginBody
     *
     * PluginBody ::=   EmptyBody 
     *                | ( '>' S? ( '<jsp:attribute' NamedAttributes )?
     *                    '<jsp:body'
     *                    ( JspBodyPluginTags | <TRANSLATION_ERROR> )
     *                    S? ETag
     *                  )
     *                | ( '>' S? PluginTags ETag )
     *
     * EmptyBody ::=   '/>'
     *               | ( '>' ETag )
     *               | ( '>' S? '<jsp:attribute' NamedAttributes ETag )
     *
     */
    private void parsePlugin(Node parent) throws JasperException {
		Attributes attrs = parseAttributes();
		reader.skipSpaces();
	        
		Node pluginNode = new Node.PlugIn(attrs, start, parent);
        
        parseOptionalBody( pluginNode, "jsp:plugin", JAVAX_BODY_CONTENT_PLUGIN );
    }

    /*
     * PluginTags ::= ( '<jsp:params' Params S? )?
     *                ( '<jsp:fallback' Fallback? S? )?
     */
    private void parsePluginTags( Node parent ) throws JasperException {
        reader.skipSpaces();
        
        if( reader.matches( "<jsp:params" ) ) {
            parseJspParams( parent );
            reader.skipSpaces();
        }
        
        if( reader.matches( "<jsp:fallback" ) ) {
            parseFallBack( parent );
            reader.skipSpaces();
        }
    }
        
    /*
     * StandardAction ::=   'include'       StdActionContent
     *                    | 'forward'       StdActionContent
     *                    | 'invoke'        StdActionContent
     *                    | 'doBody'        StdActionContent
     *                    | 'getProperty'   StdActionContent
     *                    | 'setProperty'   StdActionContent
     *                    | 'useBean'       StdActionContent
     *                    | 'plugin'        StdActionContent
     *                    | 'element'       StdActionContent
     */
    private void parseStandardAction(Node parent) throws JasperException {
		Mark start = reader.mark();
	
		if (reader.matches(INCLUDE_ACTION)) {
		    parseInclude(parent);
		} else if (reader.matches(FORWARD_ACTION)) {
		    parseForward(parent);
		} else if (reader.matches(INVOKE_ACTION)) {
		    if (!isTagFile) {
				err.jspError(reader.mark(), "jsp.error.action.isnottagfile",
					     "&lt;jsp:invoke");
		    }
		    parseInvoke(parent);
		} else if (reader.matches(DOBODY_ACTION)) {
		    if (!isTagFile) {
				err.jspError(reader.mark(), "jsp.error.action.isnottagfile",
					     "&lt;jsp:doBody");
		    }
		    parseDoBody(parent);
		} else if (reader.matches(GET_PROPERTY_ACTION)) {
		    parseGetProperty(parent);
		} else if (reader.matches(SET_PROPERTY_ACTION)) {
		    parseSetProperty(parent);
		} else if (reader.matches(USE_BEAN_ACTION)) {
		    parseUseBean(parent);
		} else if (reader.matches(PLUGIN_ACTION)) {
		    parsePlugin(parent);
		} else if (reader.matches(ELEMENT_ACTION)) {
		    parseElement(parent);
		} else if (reader.matches(ATTRIBUTE_ACTION)) {
		    err.jspError(start, "jsp.error.namedAttribute.invalidUse");
		} else if (reader.matches(BODY_ACTION)) {
		    err.jspError(start, "jsp.error.jspbody.invalidUse");
		} else if (reader.matches(FALLBACK_ACTION)) {
		    err.jspError(start, "jsp.error.fallback.invalidUse");
		} else if (reader.matches(PARAMS_ACTION)) {
		    err.jspError(start, "jsp.error.params.invalidUse");
		} else if (reader.matches(PARAM_ACTION)) {
		    err.jspError(start, "jsp.error.param.invalidUse");
		} else if (reader.matches(OUTPUT_ACTION)) {
		    err.jspError(start, "jsp.error.jspoutput.invalidUse");
		} else {
		    err.jspError(start, "jsp.error.badStandardAction");
		}
    }

    /*
     * # '<' CustomAction CustomActionBody
     *
     * CustomAction ::= TagPrefix ':' CustomActionName
     *
     * TagPrefix ::= Name
     *
     * CustomActionName ::= Name
     *
     * CustomActionBody ::=   ( Attributes CustomActionEnd )
     *                      | <TRANSLATION_ERROR>
     *
     * Attributes ::= ( S Attribute )* S?
     *
     * CustomActionEnd ::=   CustomActionTagDependent
     *                     | CustomActionJSPContent
     *                     | CustomActionScriptlessContent
     *
     * CustomActionTagDependent ::= TagDependentOptionalBody
     *
     * CustomActionJSPContent ::= OptionalBody
     *
     * CustomActionScriptlessContent ::= ScriptlessOptionalBody
     */
    private boolean parseCustomTag(Node parent) throws JasperException {

		if (reader.peekChar() != '<') {
		    return false;
		}

        // Parse 'CustomAction' production (tag prefix and custom action name)
		reader.nextChar();	// skip '<'
		String tagName = reader.parseToken(false);
		int i = tagName.indexOf(':');
		if (i == -1) {
		    reader.reset(start);
		    return false;
		}
	
		String prefix = tagName.substring(0, i);
		String shortTagName = tagName.substring(i+1);
	
		// 检查这是否是用户定义的标签.
		String uri = pageInfo.getURI(prefix);
	        if (uri == null) {
		    reader.reset(start);
            // 记住后面的错误检查的前缀
            pageInfo.putNonCustomTagPrefix(prefix, reader.mark());
		    return false;
		}
	
	        TagLibraryInfo tagLibInfo = pageInfo.getTaglib(uri);
		TagInfo tagInfo = tagLibInfo.getTag(shortTagName);
		TagFileInfo tagFileInfo = tagLibInfo.getTagFile(shortTagName);
		if (tagInfo == null && tagFileInfo == null) {
		    err.jspError(start, "jsp.error.bad_tag", shortTagName, prefix);
		}
		Class tagHandlerClass = null;
		if (tagInfo != null) {
		    // 必须是一个经典标签, 在这里加载它.
		    // 标签文件稍后将加载, in TagFileProcessor
		    String handlerClassName = tagInfo.getTagClassName();
		    try {
		        tagHandlerClass = ctxt.getClassLoader().loadClass(handlerClassName);
		    } catch (Exception e) {
		        err.jspError(start, "jsp.error.loadclass.taghandler",
				     handlerClassName, tagName);
		    }
		}
	
        // 解析'CustomActionBody':
        // 如果失败, 会产生翻译错误.

        // 解析'Attributes':
		Attributes attrs = parseAttributes();
		reader.skipSpaces();
		
        // 解析'CustomActionEnd':
		if (reader.matches("/>")) {
		    if (tagInfo != null) {
				new Node.CustomTag(tagName, prefix, shortTagName, uri, attrs,
						   start, parent, tagInfo, tagHandlerClass);
		    } else {
				new Node.CustomTag(tagName, prefix, shortTagName, uri, attrs,
						   start, parent, tagFileInfo);
		    }
		    return true;
		}
		
        // 现在解析'CustomActionTagDependent', 'CustomActionJSPContent', 或 'CustomActionScriptlessContent'其中之一.
        // 根据TLD的主体内容.
	
		// 查找一个主体, 它仍然可以是空的; 但是如果有一个标签主体, 它的语法将取决于TLD中声明的正文内容的类型.
		String bc;
		if (tagInfo != null) {
		    bc = tagInfo.getBodyContent();
		} else {
		    bc = tagFileInfo.getTagInfo().getBodyContent();
		}
	
		Node tagNode = null;
		if (tagInfo != null) {
		    tagNode = new Node.CustomTag(tagName, prefix, shortTagName, uri,
						 attrs, start, parent, tagInfo,
						 tagHandlerClass);
		} else {
		    tagNode = new Node.CustomTag(tagName, prefix, shortTagName, uri,
						 attrs, start, parent, tagFileInfo);
		}
		parseOptionalBody( tagNode, tagName, bc );
		return true;
    }

    /*
     * 解析模板的文本字符串, 直到'<' 或 "${"被遇到, 识别的转义序列 "\%" 和 "\$".
     */
    private void parseTemplateText(Node parent) throws JasperException {

		if (!reader.hasMoreInput())
		    return;
	
		CharArrayWriter ttext = new CharArrayWriter();
		// 输出第一个字符
		int ch = reader.nextChar();
	        if (ch == '\\') {
	            reader.pushChar();
	        } else {
	            ttext.write(ch);
	        }
	
		while (reader.hasMoreInput()) {
		    ch = reader.nextChar();
		    if (ch == '<') {
	                reader.pushChar();
	                break;
	            }
		    else if( ch == '$' ) {
				if (!reader.hasMoreInput()) {
				    ttext.write('$');
				    break;
                }
				ch = reader.nextChar();
				if (ch == '{') {
				    reader.pushChar();
				    reader.pushChar();
				    break;
				}
				ttext.write('$');
				reader.pushChar();
				continue;
		    }
		    else if (ch == '\\') {
				if (!reader.hasMoreInput()) {
				    ttext.write('\\');
				    break;
				}
                char next = (char)reader.peekChar();
                // 查找 \% 或 \$
                // TODO: 只识别 \$ , 如果isELIgnored是 false, 但是因为它可以在页面指令中设置, 在这里不能确定.
                if (next == '%' || next == '$') {
                    ch = reader.nextChar();
                }
		    }
		    ttext.write(ch);
		}
		new Node.TemplateText(ttext.toString(), start, parent);
    }
    
    /*
     * XMLTemplateText ::=   ( S? '/>' )
     *                     | ( S? '>'
     *                         ( ( Char* - ( Char* ( '<' | '${' ) ) )
     *                           ( '${' ELExpressionBody )?
     *                           CDSect?
     *                         )* ETag
     *                       )
     *                     | <TRANSLATION_ERROR>
     */
    private void parseXMLTemplateText(Node parent) throws JasperException {
        reader.skipSpaces();
        if( !reader.matches( "/>" ) ) {
            if( !reader.matches( ">" ) ) {
                err.jspError(start, "jsp.error.unterminated",
                    "&lt;jsp:text&gt;" );
            }
            CharArrayWriter ttext = new CharArrayWriter();
            while (reader.hasMoreInput()) {
            	int ch = reader.nextChar();
                if( ch == '<' ) {
                    // Check for <![CDATA[
                    if (!reader.matches("![CDATA[")) {
                        break;
                    }
                    start = reader.mark();
                    Mark stop = reader.skipUntil("]]>");
                    if (stop == null) {
                        err.jspError(start, "jsp.error.unterminated", "CDATA");
                    }
                    String text = reader.getText(start, stop);
                    ttext.write(text, 0, text.length());
                }
                else if( ch == '\\') {
                    if (!reader.hasMoreInput()) {
                        ttext.write('\\');
                        break;
                    }
                    ch = reader.nextChar();
                    if (ch != '$' ) {
                        ttext.write('\\');
                    }
                    ttext.write(ch);
                } else if( ch == '$' ) {
                    if (!reader.hasMoreInput()) {
                        ttext.write('$');
                        break;
                    }
                    ch = reader.nextChar();
                    if (ch != '{') {
                        ttext.write('$');
                        reader.pushChar();
                        continue;
                    }
                    // 创建一个模板文本节点
                    new Node.TemplateText( ttext.toString(), start, parent);

                    // 标记并解析 EL 表达式并创建它的节点:
                    start = reader.mark();
                    parseELExpression(parent);

                    start = reader.mark();
                    ttext = new CharArrayWriter();
                } else {
                    ttext.write( ch );
                }
            }

            new Node.TemplateText( ttext.toString(), start, parent );

		    if (! reader.hasMoreInput()) {
	                err.jspError( start, "jsp.error.unterminated",
	                    "&lt;jsp:text&gt;" );
		    } else if( !reader.matchesETagWithoutLessThan( "jsp:text" ) ) {
                err.jspError( start, "jsp.error.jsptext.badcontent");
            }
        }
    }

    /*
     * AllBody ::=       ( '<%--'              JSPCommentBody     )
     *                 | ( '<%@'               DirectiveBody      )
     *                 | ( '<jsp:directive.'   XMLDirectiveBody   )
     *                 | ( '<%!'               DeclarationBody    )
     *                 | ( '<jsp:declaration'  XMLDeclarationBody )
     *                 | ( '<%='               ExpressionBody     )
     *                 | ( '<jsp:expression'   XMLExpressionBody  )
     *                 | ( '${'                ELExpressionBody   )
     *                 | ( '<%'                ScriptletBody      )
     *                 | ( '<jsp:scriptlet'    XMLScriptletBody   )
     *                 | ( '<jsp:text'         XMLTemplateText    )
     *                 | ( '<jsp:'             StandardAction     )
     *                 | ( '<'                 CustomAction
     *                                         CustomActionBody   )
     *	               | TemplateText
     */
    private void parseElements(Node parent) throws JasperException {
        if( scriptlessCount > 0 ) {
            // vc: ScriptlessBody
            // 必须跟随ScriptlessBody, 如果父级中的一个是 ScriptlessBody.
            parseElementsScriptless( parent );
            return;
        }
        
		start = reader.mark();
		if (reader.matches("<%--")) {
		    parseComment(parent);
		} else if (reader.matches("<%@")) {
		    parseDirective(parent);
        } else if (reader.matches("<jsp:directive.")) {
            parseXMLDirective(parent);
		} else if (reader.matches("<%!")) {
		    parseDeclaration(parent);
        } else if (reader.matches("<jsp:declaration")) {
            parseXMLDeclaration(parent);
        } else if (reader.matches("<%=")) {
            parseExpression(parent);
        } else if (reader.matches("<jsp:expression")) {
            parseXMLExpression(parent);
		} else if (reader.matches("<%")) {
		    parseScriptlet(parent);
        } else if (reader.matches("<jsp:scriptlet")) {
            parseXMLScriptlet(parent);
        } else if (reader.matches("<jsp:text")) {
            parseXMLTemplateText(parent);
        } else if (reader.matches("${")) {
            parseELExpression(parent);
		} else if (reader.matches("<jsp:")) {
		    parseStandardAction(parent);
		} else if (!parseCustomTag(parent)) {
            checkUnbalancedEndTag();
            parseTemplateText(parent);
		}
    }

    /*
     * ScriptlessBody ::=  ( '<%--'              JSPCommentBody      )
     *                   | ( '<%@'               DirectiveBody       )
     *                   | ( '<jsp:directive.'   XMLDirectiveBody    )
     *                   | ( '<%!'               <TRANSLATION_ERROR> )
     *                   | ( '<jsp:declaration'  <TRANSLATION_ERROR> )
     *                   | ( '<%='               <TRANSLATION_ERROR> )
     *                   | ( '<jsp:expression'   <TRANSLATION_ERROR> )
     *                   | ( '<%'                <TRANSLATION_ERROR> )
     *                   | ( '<jsp:scriptlet'    <TRANSLATION_ERROR> )
     *                   | ( '<jsp:text'         XMLTemplateText     )
     *                   | ( '${'                ELExpressionBody    )
     *                   | ( '<jsp:'             StandardAction      )
     *                   | ( '<'                 CustomAction
     *                                           CustomActionBody    )
     *                   | TemplateText
     */
    private void parseElementsScriptless(Node parent) throws JasperException {
        // 跟踪遇到了多少scriptless 节点, 所以我们知道子节点是否被强制 scriptless
        scriptlessCount++;
        
		start = reader.mark();
		if (reader.matches("<%--")) {
		    parseComment(parent);
		} else if (reader.matches("<%@")) {
		    parseDirective(parent);
        } else if (reader.matches("<jsp:directive.")) {
            parseXMLDirective(parent);
		} else if (reader.matches("<%!")) {
		    err.jspError( reader.mark(), "jsp.error.no.scriptlets" );
        } else if (reader.matches("<jsp:declaration")) {
            err.jspError( reader.mark(), "jsp.error.no.scriptlets" );
		} else if (reader.matches("<%=")) {
		    err.jspError( reader.mark(), "jsp.error.no.scriptlets" );
        } else if (reader.matches("<jsp:expression")) {
            err.jspError( reader.mark(), "jsp.error.no.scriptlets" );
		} else if (reader.matches("<%")) {
		    err.jspError( reader.mark(), "jsp.error.no.scriptlets" );
        } else if (reader.matches("<jsp:scriptlet")) {
            err.jspError( reader.mark(), "jsp.error.no.scriptlets" );
        } else if (reader.matches("<jsp:text")) {
            parseXMLTemplateText(parent);
		} else if (reader.matches("${")) {
		    parseELExpression(parent);
		} else if (reader.matches("<jsp:")) {
		    parseStandardAction(parent);
		} else if (!parseCustomTag(parent)) {
            checkUnbalancedEndTag();
            parseTemplateText(parent);
		}
        scriptlessCount--;
    }
    
    /*
     * TemplateTextBody ::=   ( '<%--'              JSPCommentBody      )
     *                      | ( '<%@'               DirectiveBody       )
     *                      | ( '<jsp:directive.'   XMLDirectiveBody    )
     *                      | ( '<%!'               <TRANSLATION_ERROR> )
     *                      | ( '<jsp:declaration'  <TRANSLATION_ERROR> )
     *                      | ( '<%='               <TRANSLATION_ERROR> )
     *                      | ( '<jsp:expression'   <TRANSLATION_ERROR> )
     *                      | ( '<%'                <TRANSLATION_ERROR> )
     *                      | ( '<jsp:scriptlet'    <TRANSLATION_ERROR> )
     *                      | ( '<jsp:text'         <TRANSLATION_ERROR> )
     *                      | ( '${'                <TRANSLATION_ERROR> )
     *                      | ( '<jsp:'             <TRANSLATION_ERROR> )
     *                      | TemplateText
     */
    private void parseElementsTemplateText(Node parent) throws JasperException {
        start = reader.mark();
        if (reader.matches("<%--")) {
            parseComment(parent);
        } else if (reader.matches("<%@")) {
            parseDirective(parent);
        } else if (reader.matches("<jsp:directive.")) {
            parseXMLDirective(parent);
        } else if (reader.matches("<%!")) {
            err.jspError( reader.mark(), "jsp.error.not.in.template", "Declarations" );
        } else if (reader.matches("<jsp:declaration")) {
            err.jspError( reader.mark(), "jsp.error.not.in.template", "Declarations" );
        } else if (reader.matches("<%=")) {
            err.jspError( reader.mark(), "jsp.error.not.in.template", "Expressions" );
        } else if (reader.matches("<jsp:expression")) {
            err.jspError( reader.mark(), "jsp.error.not.in.template", "Expressions" );
        } else if (reader.matches("<%")) {
            err.jspError( reader.mark(), "jsp.error.not.in.template", "Scriptlets" );
        } else if (reader.matches("<jsp:scriptlet")) {
            err.jspError( reader.mark(), "jsp.error.not.in.template", "Scriptlets" );
        } else if (reader.matches("<jsp:text")) {
            err.jspError( reader.mark(), "jsp.error.not.in.template", "&lt;jsp:text" );
        } else if (reader.matches("${")) {
            err.jspError( reader.mark(), "jsp.error.not.in.template", "Expression language" );
        } else if (reader.matches("<jsp:")) {
            err.jspError( reader.mark(), "jsp.error.not.in.template", "Standard actions" );
		} else if (parseCustomTag(parent)) {
            err.jspError( reader.mark(), "jsp.error.not.in.template", "Custom actions" );
		} else {
            checkUnbalancedEndTag();
            parseTemplateText(parent);
		}
    }

    /*
     * 如果不对称的结束标记本身出现，则标记为错误.
     */
    private void checkUnbalancedEndTag() throws JasperException {

        if (!reader.matches("</")) {
            return;
        }

        // 检查不对称的标准动作
        if (reader.matches("jsp:")) {
            err.jspError(start, "jsp.error.unbalanced.endtag", "jsp:");
        }

        // 检查不对称的自定义动作
        String tagName = reader.parseToken(false);
        int i = tagName.indexOf(':');
        if (i == -1 || pageInfo.getURI(tagName.substring(0, i)) == null) {
            reader.reset(start);
            return;
        }

        err.jspError(start, "jsp.error.unbalanced.endtag", tagName);
    }

    /**
     * TagDependentBody := 
     */
    private void parseTagDependentBody(Node parent, String tag) throws JasperException{
		Mark bodyStart = reader.mark();
		Mark bodyEnd = reader.skipUntilETag(tag);
		if (bodyEnd == null) {
		    err.jspError(start, "jsp.error.unterminated", "&lt;"+tag );
		}
		new Node.TemplateText(reader.getText(bodyStart, bodyEnd), bodyStart,
			      parent);
    }

    /*
     * 解析 jsp:body 动作.
     */
    private void parseJspBody(Node parent, String bodyType) throws JasperException {
        Mark start = reader.mark();
		Node bodyNode = new Node.JspBody(start, parent);
	
		reader.skipSpaces();
		if (!reader.matches("/>")) {
		    if (!reader.matches(">")) {
			err.jspError(start, "jsp.error.unterminated",
				     "&lt;jsp:body");
		    }
		    parseBody( bodyNode, "jsp:body", bodyType );
		}
    }

    /*
     * 解析主体为 JSP 内容.
     * @param tag 其结束标签将终止该主体的标签的名称
     * @param bodyType TagInfo主体类型之一
     */
    private void parseBody(Node parent, String tag, String bodyType) throws JasperException {
        if( bodyType.equalsIgnoreCase( TagInfo.BODY_CONTENT_TAG_DEPENDENT ) ) {
            parseTagDependentBody( parent, tag );
        }
        else if( bodyType.equalsIgnoreCase( TagInfo.BODY_CONTENT_EMPTY ) ) {
            if( !reader.matchesETag( tag ) ) {
				err.jspError(start, "jasper.error.emptybodycontent.nonempty",
					     tag);
            }
        }
        else if( bodyType == JAVAX_BODY_CONTENT_PLUGIN ) {
            // (note the == since we won't recognize JAVAX_* 
            // from outside this module).
            parsePluginTags(parent);
            if( !reader.matchesETag( tag ) ) {
                err.jspError( reader.mark(), "jsp.error.unterminated",
                    "&lt;" + tag  );
            }
        }
        else if( bodyType.equalsIgnoreCase( TagInfo.BODY_CONTENT_JSP ) ||
            bodyType.equalsIgnoreCase( TagInfo.BODY_CONTENT_SCRIPTLESS ) ||
            (bodyType == JAVAX_BODY_CONTENT_PARAM) ||
            (bodyType == JAVAX_BODY_CONTENT_TEMPLATE_TEXT) ) {
        	
            while (reader.hasMoreInput()) {
                if (reader.matchesETag(tag)) {
                    return;
                }
                
                // 检查潜逃的 jsp:body 或 jsp:attribute
                if (tag.equals("jsp:body") || tag.equals("jsp:attribute")) {
                    if (reader.matches("<jsp:attribute")) {
                        err.jspError(reader.mark(), "jsp.error.nested.jspattribute");
                    }
                    else if (reader.matches("<jsp:body")) {
                        err.jspError(reader.mark(), "jsp.error.nested.jspbody");
                    }
                }

                if( bodyType.equalsIgnoreCase( TagInfo.BODY_CONTENT_JSP ) ) {
                    parseElements( parent );
                }
                else if( bodyType.equalsIgnoreCase( 
                    TagInfo.BODY_CONTENT_SCRIPTLESS ) ) {
                    parseElementsScriptless( parent );
                } else if( bodyType == JAVAX_BODY_CONTENT_PARAM ) {
                    // (note the == since we won't recognize JAVAX_* 
                    // from outside this module).
                    reader.skipSpaces();
                    parseParam( parent );
                } else if (bodyType == JAVAX_BODY_CONTENT_TEMPLATE_TEXT) {
				    parseElementsTemplateText(parent);
				}
            }
            err.jspError(start, "jsp.error.unterminated", "&lt;"+tag );
        } else {
        	err.jspError(start, "jasper.error.bad.bodycontent.type");
        }
    }

    /*
     * 解析命名属性.
     */
    private void parseNamedAttributes(Node parent) throws JasperException {
        do {
            Mark start = reader.mark();
            Attributes attrs = parseAttributes();
            Node.NamedAttribute namedAttributeNode =
                new Node.NamedAttribute( attrs, start, parent );

            reader.skipSpaces();
		    if (!reader.matches("/>")) {
				if (!reader.matches(">")) {
				    err.jspError(start, "jsp.error.unterminated",
						 "&lt;jsp:attribute");
				}
	            if (namedAttributeNode.isTrim()) {
	                reader.skipSpaces();
	            }
	            parseBody(namedAttributeNode, "jsp:attribute", 
				  getAttributeBodyType(parent, attrs.getValue("name")));
	            
	            if (namedAttributeNode.isTrim()) {
	                Node.Nodes subElems = namedAttributeNode.getBody();
				    if (subElems != null) {
						Node lastNode = subElems.getNode(subElems.size() - 1);
						if (lastNode instanceof Node.TemplateText) {
						    ((Node.TemplateText)lastNode).rtrim();
						}
				    }
	            }
            }
            reader.skipSpaces();
        } while( reader.matches( "<jsp:attribute" ) );
    }

    /**
     * 确定关闭的节点中的<jsp:attribute>的主体类型
     */
    private String getAttributeBodyType(Node n, String name) {

		if (n instanceof Node.CustomTag) {
		    TagInfo tagInfo = ((Node.CustomTag)n).getTagInfo();
		    TagAttributeInfo[] tldAttrs = tagInfo.getAttributes();
		    for (int i=0; i<tldAttrs.length; i++) {
				if (name.equals(tldAttrs[i].getName())) {
				    if (tldAttrs[i].isFragment()) {
				        return TagInfo.BODY_CONTENT_SCRIPTLESS;
				    }
				    if (tldAttrs[i].canBeRequestTime()) {
				        return TagInfo.BODY_CONTENT_JSP;
				    }
				}
		    }
		    if (tagInfo.hasDynamicAttributes()) {
		    	return TagInfo.BODY_CONTENT_JSP;
		    }
		} else if (n instanceof Node.IncludeAction) {
		    if ("page".equals(name)) {
		    	return TagInfo.BODY_CONTENT_JSP;
		    }
		} else if (n instanceof Node.ForwardAction) {
		    if ("page".equals(name)) {
		    	return TagInfo.BODY_CONTENT_JSP;
		    }
		} else if (n instanceof Node.SetProperty) {
		    if ("value".equals(name)) {
		    	return TagInfo.BODY_CONTENT_JSP;
		    }
		} else if (n instanceof Node.UseBean) {
		    if ("beanName".equals(name)) {
		    	return TagInfo.BODY_CONTENT_JSP;
		    }
		} else if (n instanceof Node.PlugIn) {
		    if ("width".equals(name) || "height".equals(name)) {
		    	return TagInfo.BODY_CONTENT_JSP;
		    }
		} else if (n instanceof Node.ParamAction) {
		    if ("value".equals(name)) {
		    	return TagInfo.BODY_CONTENT_JSP;
		    }
		} else if (n instanceof Node.JspElement) {
		    return TagInfo.BODY_CONTENT_JSP;
		}
	
		return JAVAX_BODY_CONTENT_TEMPLATE_TEXT;
    }

    private void parseTagFileDirectives(Node parent) throws JasperException {
		reader.setSingleFile(true);
		reader.skipUntil("<");
        while (reader.hasMoreInput()) {
            start = reader.mark();
            if (reader.matches("%--")) {
                parseComment(parent);
            } else if (reader.matches("%@")) {
                parseDirective(parent);
            } else if (reader.matches("jsp:directive.")) {
                parseXMLDirective(parent);
            }
            reader.skipUntil("<");
		}
    }
}

