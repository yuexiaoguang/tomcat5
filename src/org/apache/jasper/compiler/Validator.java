package org.apache.jasper.compiler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;

import javax.servlet.jsp.el.FunctionMapper;
import javax.servlet.jsp.tagext.FunctionInfo;
import javax.servlet.jsp.tagext.JspFragment;
import javax.servlet.jsp.tagext.PageData;
import javax.servlet.jsp.tagext.TagAttributeInfo;
import javax.servlet.jsp.tagext.TagData;
import javax.servlet.jsp.tagext.TagExtraInfo;
import javax.servlet.jsp.tagext.TagInfo;
import javax.servlet.jsp.tagext.TagLibraryInfo;
import javax.servlet.jsp.tagext.ValidationMessage;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.xml.sax.Attributes;

/**
 * 对页面元素执行验证. 强制检查属性是否存在, 输入值的有效性, 和一致性.
 * 作为副作用, 某些页面全局值(例如来自页面指令的某些东西)被存储, 供以后使用.
 */
class Validator {

    /**
     * 验证和提取页面指令信息的访问者
     */
    static class DirectiveVisitor extends Node.Visitor {

		private PageInfo pageInfo;
		private ErrorDispatcher err;
	
		private static final JspUtil.ValidAttribute[] pageDirectiveAttrs = {
		    new JspUtil.ValidAttribute("language"),
		    new JspUtil.ValidAttribute("extends"),
		    new JspUtil.ValidAttribute("import"),
		    new JspUtil.ValidAttribute("session"),
		    new JspUtil.ValidAttribute("buffer"),
		    new JspUtil.ValidAttribute("autoFlush"),
		    new JspUtil.ValidAttribute("isThreadSafe"),
		    new JspUtil.ValidAttribute("info"),
		    new JspUtil.ValidAttribute("errorPage"),
		    new JspUtil.ValidAttribute("isErrorPage"),
		    new JspUtil.ValidAttribute("contentType"),
		    new JspUtil.ValidAttribute("pageEncoding"),
		    new JspUtil.ValidAttribute("isELIgnored")
		};
	
		private boolean pageEncodingSeen = false;

		DirectiveVisitor(Compiler compiler) throws JasperException {
		    this.pageInfo = compiler.getPageInfo();
		    this.err = compiler.getErrorDispatcher();
		    JspCompilationContext ctxt = compiler.getCompilationContext();
		}
	
		public void visit(Node.IncludeDirective n) throws JasperException {
            // 由于 pageDirectiveSeen标志只适用于当前页面, 保存在这里，并在文件被包含后恢复.
            boolean pageEncodingSeenSave = pageEncodingSeen;
            pageEncodingSeen = false;
            visitBody(n);
            pageEncodingSeen = pageEncodingSeenSave;
        }

		public void visit(Node.PageDirective n) throws JasperException {    
	
	            JspUtil.checkAttributes("Page directive", n,
	                                    pageDirectiveAttrs, err);
	
		    // JSP.2.10.1
		    Attributes attrs = n.getAttributes();
		    for (int i = 0; attrs != null && i < attrs.getLength(); i++) {
				String attr = attrs.getQName(i);
				String value = attrs.getValue(i);
		
				if ("language".equals(attr)) {
				    if (pageInfo.getLanguage(false) == null) {
				    	pageInfo.setLanguage(value, n, err, true);
				    } else if (!pageInfo.getLanguage(false).equals(value)) {
						err.jspError(n, "jsp.error.page.conflict.language",
							     pageInfo.getLanguage(false), value);
				    }
				} else if ("extends".equals(attr)) {
				    if (pageInfo.getExtends(false) == null) {
				    	pageInfo.setExtends(value, n);
				    } else if (!pageInfo.getExtends(false).equals(value)) {
						err.jspError(n, "jsp.error.page.conflict.extends",
							     pageInfo.getExtends(false), value);
				    }
				} else if ("contentType".equals(attr)) {
				    if (pageInfo.getContentType() == null) {
				    	pageInfo.setContentType(value);
				    } else if (!pageInfo.getContentType().equals(value)) {
						err.jspError(n, "jsp.error.page.conflict.contenttype",
							     pageInfo.getContentType(), value);
				    }
				} else if ("session".equals(attr)) {
				    if (pageInfo.getSession() == null) {
				    	pageInfo.setSession(value, n, err);
				    } else if (!pageInfo.getSession().equals(value)) {
						err.jspError(n, "jsp.error.page.conflict.session",
							     pageInfo.getSession(), value);
				    }
				} else if ("buffer".equals(attr)) {
				    if (pageInfo.getBufferValue() == null) {
				    	pageInfo.setBufferValue(value, n, err);
				    } else if (!pageInfo.getBufferValue().equals(value)) {
						err.jspError(n, "jsp.error.page.conflict.buffer",
							     pageInfo.getBufferValue(), value);
				    }
				} else if ("autoFlush".equals(attr)) {
				    if (pageInfo.getAutoFlush() == null) {
				    	pageInfo.setAutoFlush(value, n, err);
				    } else if (!pageInfo.getAutoFlush().equals(value)) {
						err.jspError(n, "jsp.error.page.conflict.autoflush",
							     pageInfo.getAutoFlush(), value);
				    }
				} else if ("isThreadSafe".equals(attr)) {
				    if (pageInfo.getIsThreadSafe() == null) {
				    	pageInfo.setIsThreadSafe(value, n, err);
				    } else if (!pageInfo.getIsThreadSafe().equals(value)) {
						err.jspError(n, "jsp.error.page.conflict.isthreadsafe",
							     pageInfo.getIsThreadSafe(), value);
				    }
				} else if ("isELIgnored".equals(attr)) {
				    if (pageInfo.getIsELIgnored() == null) {
                        pageInfo.setIsELIgnored(value, n, err, true);
				    } else if (!pageInfo.getIsELIgnored().equals(value)) {
						err.jspError(n, "jsp.error.page.conflict.iselignored",
							     pageInfo.getIsELIgnored(), value);
				    }
				} else if ("isErrorPage".equals(attr)) {
				    if (pageInfo.getIsErrorPage() == null) {
				    	pageInfo.setIsErrorPage(value, n, err);
				    } else if (!pageInfo.getIsErrorPage().equals(value)) {
						err.jspError(n, "jsp.error.page.conflict.iserrorpage",
							     pageInfo.getIsErrorPage(), value);
				    }
				} else if ("errorPage".equals(attr)) {
				    if (pageInfo.getErrorPage() == null) {
				    	pageInfo.setErrorPage(value);
				    } else if (!pageInfo.getErrorPage().equals(value)) {
						err.jspError(n, "jsp.error.page.conflict.errorpage",
							     pageInfo.getErrorPage(), value);
				    }
				} else if ("info".equals(attr)) {
				    if (pageInfo.getInfo() == null) {
				    	pageInfo.setInfo(value);
				    } else if (!pageInfo.getInfo().equals(value)) {
						err.jspError(n, "jsp.error.page.conflict.info",
							     pageInfo.getInfo(), value);
				    }
				} else if ("pageEncoding".equals(attr)) {
				    if (pageEncodingSeen) 
				    	err.jspError(n, "jsp.error.page.multi.pageencoding");
				    // 每个文件最多可以发生一次'pageEncoding'
				    pageEncodingSeen = true;
				    comparePageEncodings(value, n);
				}
		    }
	
		    // 检查不良组合
		    if (pageInfo.getBuffer() == 0 && !pageInfo.isAutoFlush())
		    	err.jspError(n, "jsp.error.page.badCombo");
	
		    // 此节点导入的属性已由解析器处理, 将它们添加到pageInfo.
		    pageInfo.addImports(n.getImports());
		}

		public void visit(Node.TagDirective n) throws JasperException {
            // Note:大多数验证都在TagFileProcessor中完成, 当它从该指令出现的标签文件中创建一个 TagInfo 对象.
        
            // 此方法进行额外的处理以收集页面信息
		    Attributes attrs = n.getAttributes();
		    for (int i = 0; attrs != null && i < attrs.getLength(); i++) {
				String attr = attrs.getQName(i);
				String value = attrs.getValue(i);
		
				if ("language".equals(attr)) {
				    if (pageInfo.getLanguage(false) == null) {
				    	pageInfo.setLanguage(value, n, err, false);
				    } else if (!pageInfo.getLanguage(false).equals(value)) {
						err.jspError(n, "jsp.error.tag.conflict.language",
							     pageInfo.getLanguage(false), value);
				    }
				} else if ("isELIgnored".equals(attr)) {
				    if (pageInfo.getIsELIgnored() == null) {
                        pageInfo.setIsELIgnored(value, n, err, false);
				    } else if (!pageInfo.getIsELIgnored().equals(value)) {
						err.jspError(n, "jsp.error.tag.conflict.iselignored",
							     pageInfo.getIsELIgnored(), value);
				    }
				} else if ("pageEncoding".equals(attr)) {
				    if (pageEncodingSeen) 
				    	err.jspError(n, "jsp.error.tag.multi.pageencoding");
				    pageEncodingSeen = true;
				    n.getRoot().setPageEncoding(value);
				}
		    }
	
		    // 此节点导入的属性已由解析器处理, 将它们添加到pageInfo.
		    pageInfo.addImports(n.getImports());
		}
	
		public void visit(Node.AttributeDirective n) throws JasperException {
		    // 什么都不做, 因为这个属性指令已经被 TagFileProcessor验证, 当它从该指令出现的标签文件中创建一个 TagInfo 对象.
		}
	
		public void visit(Node.VariableDirective n) throws JasperException {
		    // 什么都不做, 因为这个属性指令已经被 TagFileProcessor验证, 当它从该指令出现的标签文件中创建一个 TagInfo 对象.
		}

        /*
         * 比较不同地方的指定网页编码, 并在页面编码不匹配时抛出异常.
         *
         * @param pageDirEnc 页面指令的pageEncoding属性的值
         * @param pageDir 页面指令节点
         *
         * @throws JasperException 在页面编码不匹配的情况下
         */
        private void comparePageEncodings(String pageDirEnc,
                                          Node.PageDirective pageDir) throws JasperException {

            Node.Root root = pageDir.getRoot();
            String configEnc = root.getJspConfigPageEncoding();

            /*
             * 比较页面指令的'pageEncoding'属性, 使用URL模式匹配这个页面的JSP配置元素指定的编码.
             * "UTF-16", "UTF-16BE", "UTF-16LE"是相同的.
             */
            if (configEnc != null && !pageDirEnc.equals(configEnc) 
                    && (!pageDirEnc.startsWith("UTF-16")
                        || !configEnc.startsWith("UTF-16"))) {
                err.jspError(pageDir,
                             "jsp.error.config_pagedir_encoding_mismatch",
                             configEnc, pageDirEnc);
            }

            /*
             * 比较页面指令的'pageEncoding'属性, 使用XML前言中指定的编码(仅用于XML语法,
             * 而且只有当JSP文档包含带有编码声明的XML 前言时).
             * "UTF-16", "UTF-16BE", "UTF-16LE"是相同的.
             */
		    if (root.isXmlSyntax() && root.isEncodingSpecifiedInProlog()) {
		    	String pageEnc = root.getPageEncoding();
                if (!pageDirEnc.equals(pageEnc) 
                        && (!pageDirEnc.startsWith("UTF-16")
                            || !pageEnc.startsWith("UTF-16"))) {
                    err.jspError(pageDir,
                                 "jsp.error.prolog_pagedir_encoding_mismatch",
                                 pageEnc, pageDirEnc);
                }
            }
        }
    }

    /**
     * 用于验证除页面指令以外的节点的访问者
     */
    static class ValidateVisitor extends Node.Visitor {
	
		private PageInfo pageInfo;
		private ErrorDispatcher err;
		private TagInfo tagInfo;
        private ClassLoader loader;
	
		private static final JspUtil.ValidAttribute[] jspRootAttrs = {
            new JspUtil.ValidAttribute("xsi:schemaLocation"),
		    new JspUtil.ValidAttribute("version", true)
	    };
	
		private static final JspUtil.ValidAttribute[] includeDirectiveAttrs = {
		    new JspUtil.ValidAttribute("file", true)
	    };
	
		private static final JspUtil.ValidAttribute[] taglibDirectiveAttrs = {
		    new JspUtil.ValidAttribute("uri"),
		    new JspUtil.ValidAttribute("tagdir"),
		    new JspUtil.ValidAttribute("prefix", true)
	    };
	
		private static final JspUtil.ValidAttribute[] includeActionAttrs = {
		    new JspUtil.ValidAttribute("page", true, true),
		    new JspUtil.ValidAttribute("flush")
	    };
	
		private static final JspUtil.ValidAttribute[] paramActionAttrs = {
		    new JspUtil.ValidAttribute("name", true),
		    new JspUtil.ValidAttribute("value", true, true)
	    };
	
		private static final JspUtil.ValidAttribute[] forwardActionAttrs = {
		    new JspUtil.ValidAttribute("page", true, true)
	    };
	
		private static final JspUtil.ValidAttribute[] getPropertyAttrs = {
		    new JspUtil.ValidAttribute("name", true),
		    new JspUtil.ValidAttribute("property", true)
	    };
	
		private static final JspUtil.ValidAttribute[] setPropertyAttrs = {
		    new JspUtil.ValidAttribute("name", true),
		    new JspUtil.ValidAttribute("property", true),
		    new JspUtil.ValidAttribute("value", false, true),
		    new JspUtil.ValidAttribute("param")
	    };
	
		private static final JspUtil.ValidAttribute[] useBeanAttrs = {
		    new JspUtil.ValidAttribute("id", true),
		    new JspUtil.ValidAttribute("scope"),
		    new JspUtil.ValidAttribute("class"),
		    new JspUtil.ValidAttribute("type"),
		    new JspUtil.ValidAttribute("beanName", false, true)
	    };
	
		private static final JspUtil.ValidAttribute[] plugInAttrs = {
		    new JspUtil.ValidAttribute("type",true),
		    new JspUtil.ValidAttribute("code", true),
		    new JspUtil.ValidAttribute("codebase"),
		    new JspUtil.ValidAttribute("align"),
		    new JspUtil.ValidAttribute("archive"),
		    new JspUtil.ValidAttribute("height", false, true),
		    new JspUtil.ValidAttribute("hspace"),
		    new JspUtil.ValidAttribute("jreversion"),
		    new JspUtil.ValidAttribute("name"),
		    new JspUtil.ValidAttribute("vspace"),
		    new JspUtil.ValidAttribute("width", false, true),
		    new JspUtil.ValidAttribute("nspluginurl"),
		    new JspUtil.ValidAttribute("iepluginurl")
	    };
	            
        private static final JspUtil.ValidAttribute[] attributeAttrs = {
	            new JspUtil.ValidAttribute("name", true),
	            new JspUtil.ValidAttribute("trim")
        };
	            
        private static final JspUtil.ValidAttribute[] invokeAttrs = {
	            new JspUtil.ValidAttribute("fragment", true),
		    new JspUtil.ValidAttribute("var"),
		    new JspUtil.ValidAttribute("varReader"),
		    new JspUtil.ValidAttribute("scope")
	    };
	
        private static final JspUtil.ValidAttribute[] doBodyAttrs = {
            new JspUtil.ValidAttribute("var"),
		    new JspUtil.ValidAttribute("varReader"),
		    new JspUtil.ValidAttribute("scope")
	    };
	
		private static final JspUtil.ValidAttribute[] jspOutputAttrs = {
		    new JspUtil.ValidAttribute("omit-xml-declaration"),
		    new JspUtil.ValidAttribute("doctype-root-element"),
		    new JspUtil.ValidAttribute("doctype-public"),
		    new JspUtil.ValidAttribute("doctype-system")
	    };
	
		ValidateVisitor(Compiler compiler) {
		    this.pageInfo = compiler.getPageInfo();
		    this.err = compiler.getErrorDispatcher();
		    this.tagInfo = compiler.getCompilationContext().getTagInfo();
		    this.loader = compiler.getCompilationContext().getClassLoader();
		}
	
		public void visit(Node.JspRoot n) throws JasperException {
		    JspUtil.checkAttributes("Jsp:root", n,
					    jspRootAttrs, err);
		    String version = n.getTextAttribute("version");
		    if (!version.equals("1.2") && !version.equals("2.0")) {
		    	err.jspError(n, "jsp.error.jsproot.version.invalid", version);
		    }
		    visitBody(n);
		}
	
		public void visit(Node.IncludeDirective n) throws JasperException {
            JspUtil.checkAttributes("Include directive", n,
	                                    includeDirectiveAttrs, err);
		    visitBody(n);
		}
	
		public void visit(Node.TaglibDirective n) throws JasperException {
            JspUtil.checkAttributes("Taglib directive", n,
	                                    taglibDirectiveAttrs, err);
		    // 必须指定 'uri' 或 'tagdir'属性
		    String uri = n.getAttributeValue("uri");
		    String tagdir = n.getAttributeValue("tagdir");
		    if (uri == null && tagdir == null) {
		    	err.jspError(n, "jsp.error.taglibDirective.missing.location");
		    }
		    if (uri != null && tagdir != null) {
		    	err.jspError(n, "jsp.error.taglibDirective.both_uri_and_tagdir");
		    }
		}
	
		public void visit(Node.ParamAction n) throws JasperException {
            JspUtil.checkAttributes("Param action", n,
	                                    paramActionAttrs, err);
		    // 确定'name'属性的值不是一个请求时间的表达式
		    throwErrorIfExpression(n, "name", "jsp:param");
		    n.setValue(getJspAttribute("value", null, null,
					       					n.getAttributeValue("value"),
	                                       java.lang.String.class,
	                                       n, false));
            visitBody(n);
		}
	
		public void visit(Node.ParamsAction n) throws JasperException {
		    // 确保至少有一个嵌套的 jsp:param
            Node.Nodes subElems = n.getBody();
            if (subElems == null) {
            	err.jspError(n, "jsp.error.params.emptyBody");
            }
            visitBody(n);
		}
	
		public void visit(Node.IncludeAction n) throws JasperException {
            JspUtil.checkAttributes("Include action", n,
	                                    includeActionAttrs, err);
		    n.setPage(getJspAttribute("page", null, null,
					      n.getAttributeValue("page"), 
	                                      java.lang.String.class, n, false));
		    visitBody(n);
        };
	
		public void visit(Node.ForwardAction n) throws JasperException {
	            JspUtil.checkAttributes("Forward", n,
	                                    forwardActionAttrs, err);
		    n.setPage(getJspAttribute("page", null, null,
					      n.getAttributeValue("page"), 
	                                      java.lang.String.class, n, false));
		    visitBody(n);
		}
	
		public void visit(Node.GetProperty n) throws JasperException {
            JspUtil.checkAttributes("GetProperty", n, getPropertyAttrs, err);
		}
	
		public void visit(Node.SetProperty n) throws JasperException {
            JspUtil.checkAttributes("SetProperty", n, setPropertyAttrs, err);
		    String name = n.getTextAttribute("name");
		    String property = n.getTextAttribute("property");
		    String param = n.getTextAttribute("param");
		    String value = n.getAttributeValue("value");
	
            n.setValue(getJspAttribute("value", null, null, value, 
                java.lang.Object.class, n, false));

            boolean valueSpecified = n.getValue() != null;
	
		    if ("*".equals(property)) { 
                if (param != null || valueSpecified)
                	err.jspError(n, "jsp.error.setProperty.invalid");
			
            } else if (param != null && valueSpecified) {
            	err.jspError(n, "jsp.error.setProperty.invalid");
		    }
	            
            visitBody(n);
		}
	
		public void visit(Node.UseBean n) throws JasperException {
            JspUtil.checkAttributes("UseBean", n, useBeanAttrs, err);
	
		    String name = n.getTextAttribute ("id");
		    String scope = n.getTextAttribute ("scope");
		    JspUtil.checkScope(scope, n, err);
		    String className = n.getTextAttribute ("class");
		    String type = n.getTextAttribute ("type");
		    BeanRepository beanInfo = pageInfo.getBeanRepository();
	
		    if (className == null && type == null)
		    	err.jspError(n, "jsp.error.usebean.missingType");
	
		    if (beanInfo.checkVariable(name))
		    	err.jspError(n, "jsp.error.usebean.duplicate");
	
		    if ("session".equals(scope) && !pageInfo.isSession())
		    	err.jspError(n, "jsp.error.usebean.noSession");
	
		    Node.JspAttribute jattr = getJspAttribute("beanName", null, null,
					  n.getAttributeValue("beanName"),
					  java.lang.String.class, n, false);
		    n.setBeanName(jattr);
		    if (className != null && jattr != null)
		    	err.jspError(n, "jsp.error.usebean.notBoth");
	
		    if (className == null)
		    	className = type;
	
		    beanInfo.addBean(n, name, className, scope);
	
		    visitBody(n);
		}
	
		public void visit(Node.PlugIn n) throws JasperException {
            JspUtil.checkAttributes("Plugin", n, plugInAttrs, err);
	
		    throwErrorIfExpression(n, "type", "jsp:plugin");
		    throwErrorIfExpression(n, "code", "jsp:plugin");
		    throwErrorIfExpression(n, "codebase", "jsp:plugin");
		    throwErrorIfExpression(n, "align", "jsp:plugin");
		    throwErrorIfExpression(n, "archive", "jsp:plugin");
		    throwErrorIfExpression(n, "hspace", "jsp:plugin");
		    throwErrorIfExpression(n, "jreversion", "jsp:plugin");
		    throwErrorIfExpression(n, "name", "jsp:plugin");
		    throwErrorIfExpression(n, "vspace", "jsp:plugin");
		    throwErrorIfExpression(n, "nspluginurl", "jsp:plugin");
		    throwErrorIfExpression(n, "iepluginurl", "jsp:plugin");
	
		    String type = n.getTextAttribute("type");
		    if (type == null)
		    	err.jspError(n, "jsp.error.plugin.notype");
		    if (!type.equals("bean") && !type.equals("applet"))
		    	err.jspError(n, "jsp.error.plugin.badtype");
		    if (n.getTextAttribute("code") == null)
		    	err.jspError(n, "jsp.error.plugin.nocode");
	            
		    Node.JspAttribute width = getJspAttribute("width", null, null,
					  n.getAttributeValue("width"), 
	                                  java.lang.String.class, n, false);
		    n.setWidth( width );
	            
		    Node.JspAttribute height = getJspAttribute("height", null, null,
					  					n.getAttributeValue("height"), 
	                                  java.lang.String.class, n, false);
		    n.setHeight( height );
	
		    visitBody(n);
		}
	
		public void visit(Node.NamedAttribute n) throws JasperException {
		    JspUtil.checkAttributes("Attribute", n, attributeAttrs, err);
            visitBody(n);
		}
	        
		public void visit(Node.JspBody n) throws JasperException {
            visitBody(n);
		}
	        
		public void visit(Node.Declaration n) throws JasperException {
		    if (pageInfo.isScriptingInvalid()) {
		    	err.jspError(n.getStart(), "jsp.error.no.scriptlets");
		    }
		}
	
        public void visit(Node.Expression n) throws JasperException {
		    if (pageInfo.isScriptingInvalid()) {
		    	err.jspError(n.getStart(), "jsp.error.no.scriptlets");
		    }
		}
	
        public void visit(Node.Scriptlet n) throws JasperException {
		    if (pageInfo.isScriptingInvalid()) {
		    	err.jspError(n.getStart(), "jsp.error.no.scriptlets");
		    }
		}
	
		public void visit(Node.ELExpression n) throws JasperException {
            if ( !pageInfo.isELIgnored() ) {
				String expressions = "${" + new String(n.getText()) + "}";
				ELNode.Nodes el = ELParser.parse(expressions);
				validateFunctions(el, n);
                JspUtil.validateExpressions(
		                    n.getStart(),
		                    expressions,
		                    java.lang.String.class, // XXX - 模板文本应该始终被评估为字符串吗?
		                    getFunctionMapper(el),
		                    err);
				n.setEL(el);
            }
        }
	
		public void visit(Node.UninterpretedTag n) throws JasperException {
            if (n.getNamedAttributeNodes().size() != 0) {
            	err.jspError(n, "jsp.error.namedAttribute.invalidUse");
            }
	
		    Attributes attrs = n.getAttributes();
		    if (attrs != null) {
				int attrSize = attrs.getLength();
				Node.JspAttribute[] jspAttrs = new Node.JspAttribute[attrSize];
				for (int i=0; i < attrSize; i++) {
				    jspAttrs[i] = getJspAttribute(attrs.getQName(i),
								  attrs.getURI(i),
								  attrs.getLocalName(i),
								  attrs.getValue(i),
								  java.lang.Object.class,
								  n,
								  false);
				}
				n.setJspAttributes(jspAttrs);
		    }
	
		    visitBody(n);
        }
	
		public void visit(Node.CustomTag n) throws JasperException {
	
		    TagInfo tagInfo = n.getTagInfo();
		    if (tagInfo == null) {
		    	err.jspError(n, "jsp.error.missing.tagInfo", n.getQName());
		    }
	
		    /*
		     * 一个SimpleTag的bodyconet可以是 JSP.
		     */
		    if (n.implementsSimpleTag() &&
                tagInfo.getBodyContent().equalsIgnoreCase(TagInfo.BODY_CONTENT_JSP)) {
                err.jspError(n, "jsp.error.simpletag.badbodycontent",
                        tagInfo.getTagClassName());
		    }
	
		    /*
		     * 如果标签处理程序在TLD中声明它支持动态属性, 它必须实现 DynamicAttributes接口.
		     */
		    if (tagInfo.hasDynamicAttributes()
			    && !n.implementsDynamicAttributes()) {
				err.jspError(n, "jsp.error.dynamic.attributes.not.implemented",
					     n.getQName());
		    }
	
		    /*
		     * 确保所有必需的属性都存在, 要么是属性，要么是命名属性(<jsp:attribute>).
	 	     * 还要确保在属性或命名属性中没有指定相同的属性.
		     */
		    TagAttributeInfo[] tldAttrs = tagInfo.getAttributes();
		    String customActionUri = n.getURI();
		    Attributes attrs = n.getAttributes();
		    int attrsSize = (attrs == null) ? 0 : attrs.getLength();
		    for (int i=0; i<tldAttrs.length; i++) {
				String attr = null;
				if (attrs != null) {
				    attr = attrs.getValue(tldAttrs[i].getName());
				    if (attr == null) {
						attr = attrs.getValue(customActionUri,
								      tldAttrs[i].getName());
				    }
				}
				Node.NamedAttribute na = n.getNamedAttributeNode(tldAttrs[i].getName());
				
				if (tldAttrs[i].isRequired() && attr == null && na == null) {
				    err.jspError(n, "jsp.error.missing_attribute",
						 tldAttrs[i].getName(), n.getLocalName());
				}
				if (attr != null && na != null) {
				    err.jspError(n, "jsp.error.duplicate.name.jspattribute",
					tldAttrs[i].getName());
				}
		    }
	
            Node.Nodes naNodes = n.getNamedAttributeNodes();
		    int jspAttrsSize = naNodes.size() + attrsSize;
		    Node.JspAttribute[] jspAttrs = null;
		    if (jspAttrsSize > 0) {
		    	jspAttrs = new Node.JspAttribute[jspAttrsSize];
		    }
		    Hashtable tagDataAttrs = new Hashtable(attrsSize);
	
		    checkXmlAttributes(n, jspAttrs, tagDataAttrs);
            checkNamedAttributes(n, jspAttrs, attrsSize, tagDataAttrs);
	
		    TagData tagData = new TagData(tagDataAttrs);
	
		    // JSP.C1: 它是拥有一个或多个变量子元素的动作的一个 (翻译时)错误, 拥有一个返回非null对象的 TagExtraInfo类.
		    TagExtraInfo tei = tagInfo.getTagExtraInfo();
		    if (tei != null
				    && tei.getVariableInfo(tagData) != null
				    && tei.getVariableInfo(tagData).length > 0
				    && tagInfo.getTagVariableInfos().length > 0) {
				err.jspError("jsp.error.non_null_tei_and_var_subelems",
					     n.getQName());
		    }
	
		    n.setTagData(tagData);
		    n.setJspAttributes(jspAttrs);
	
		    visitBody(n);
		}
	
		public void visit(Node.JspElement n) throws JasperException {
	
		    Attributes attrs = n.getAttributes();
		    if (attrs == null) {
		    	err.jspError(n, "jsp.error.jspelement.missing.name");
		    }
		    int xmlAttrLen = attrs.getLength();
	
            Node.Nodes namedAttrs = n.getNamedAttributeNodes();
	
		    // XML风格的'name'属性, 这是强制性的, JspAttribute 数组不能包含它
		    int jspAttrSize = xmlAttrLen-1 + namedAttrs.size();
	
		    Node.JspAttribute[] jspAttrs = new Node.JspAttribute[jspAttrSize];
		    int jspAttrIndex = 0;
	
		    // 处理XML风格的属性
		    for (int i=0; i<xmlAttrLen; i++) {
				if ("name".equals(attrs.getLocalName(i))) {
				    n.setNameAttribute(getJspAttribute(attrs.getQName(i),
								       attrs.getURI(i),
								       attrs.getLocalName(i),
								       attrs.getValue(i),
								       java.lang.String.class,
								       n,
								       false));
				} else {
				    if (jspAttrIndex<jspAttrSize) {
						jspAttrs[jspAttrIndex++] = getJspAttribute(attrs.getQName(i),
															      attrs.getURI(i),
															      attrs.getLocalName(i),
															      attrs.getValue(i),
															      java.lang.Object.class,
															      n,
															      false);
				    }
				}
		    }
		    if (n.getNameAttribute() == null) {
		    	err.jspError(n, "jsp.error.jspelement.missing.name");
		    }
	
		    // 处理命名属性
		    for (int i=0; i<namedAttrs.size(); i++) {
	                Node.NamedAttribute na = (Node.NamedAttribute) namedAttrs.getNode(i);
	                jspAttrs[jspAttrIndex++] = new Node.JspAttribute(na, false);
		    }
	
		    n.setJspAttributes(jspAttrs);
	
		    visitBody(n);
		}
	
		public void visit(Node.JspOutput n) throws JasperException {
            JspUtil.checkAttributes("jsp:output", n, jspOutputAttrs, err);
	
		    if (n.getBody() != null) {
	                err.jspError(n, "jsp.error.jspoutput.nonemptybody");
		    }
	
		    String omitXmlDecl = n.getAttributeValue("omit-xml-declaration");
		    String doctypeName = n.getAttributeValue("doctype-root-element");
		    String doctypePublic = n.getAttributeValue("doctype-public");
		    String doctypeSystem = n.getAttributeValue("doctype-system");
	
		    String omitXmlDeclOld = pageInfo.getOmitXmlDecl();
		    String doctypeNameOld = pageInfo.getDoctypeName();
		    String doctypePublicOld = pageInfo.getDoctypePublic();
		    String doctypeSystemOld = pageInfo.getDoctypeSystem();
	
		    if (omitXmlDecl != null && omitXmlDeclOld != null &&
				!omitXmlDecl.equals(omitXmlDeclOld) ) {
	                err.jspError(n, "jsp.error.jspoutput.conflict",
	                		"omit-xml-declaration", omitXmlDeclOld, omitXmlDecl);
		    }
	
		    if (doctypeName != null && doctypeNameOld != null &&
				!doctypeName.equals(doctypeNameOld) ) {
	                err.jspError(n, "jsp.error.jspoutput.conflict",
	                		"doctype-root-element", doctypeNameOld, doctypeName);
		    }
	
		    if (doctypePublic != null && doctypePublicOld != null &&
				!doctypePublic.equals(doctypePublicOld) ) {
	                err.jspError(n, "jsp.error.jspoutput.conflict",
	                		"doctype-public", doctypePublicOld, doctypePublic);
		    }
	
		    if (doctypeSystem != null && doctypeSystemOld != null &&
				!doctypeSystem.equals(doctypeSystemOld) ) {
	                err.jspError(n, "jsp.error.jspoutput.conflict",
	                			"doctype-system", doctypeSystemOld, doctypeSystem);
		    }
	
		    if (doctypeName == null && doctypeSystem != null ||
				doctypeName != null && doctypeSystem == null) {
				err.jspError(n, "jsp.error.jspoutput.doctypenamesystem");
		    }
	
		    if (doctypePublic != null && doctypeSystem == null) {
		    	err.jspError(n, "jsp.error.jspoutput.doctypepulicsystem");
		    }
	
		    if (omitXmlDecl != null) {
		    	pageInfo.setOmitXmlDecl(omitXmlDecl);
		    }
		    if (doctypeName != null) {
		    	pageInfo.setDoctypeName(doctypeName);
		    }
		    if (doctypeSystem != null) {
		    	pageInfo.setDoctypeSystem(doctypeSystem);
		    }
		    if (doctypePublic != null) {
		    	pageInfo.setDoctypePublic(doctypePublic);
		    }
		}
	
		public void visit(Node.InvokeAction n) throws JasperException {
	
            JspUtil.checkAttributes("Invoke", n, invokeAttrs, err);
		    String scope = n.getTextAttribute ("scope");
		    JspUtil.checkScope(scope, n, err);
	
		    String var = n.getTextAttribute("var");
		    String varReader = n.getTextAttribute("varReader");
		    if (scope != null && var == null && varReader == null) {
		    	err.jspError(n, "jsp.error.missing_var_or_varReader");
		    }
		    if (var != null && varReader != null) {
		    	err.jspError(n, "jsp.error.var_and_varReader");
		    }
		}
	
		public void visit(Node.DoBodyAction n) throws JasperException {
	
            JspUtil.checkAttributes("DoBody", n, doBodyAttrs, err);
		    String scope = n.getTextAttribute ("scope");
		    JspUtil.checkScope(scope, n, err);
	
		    String var = n.getTextAttribute("var");
		    String varReader = n.getTextAttribute("varReader");
		    if (scope != null && var == null && varReader == null) {
		    	err.jspError(n, "jsp.error.missing_var_or_varReader");
		    }
		    if (var != null && varReader != null) {
		    	err.jspError(n, "jsp.error.var_and_varReader");
		    }
		}
	
		/*
		 * 确保给定的自定义操作没有任何无效属性.
		 *
		 * 自定义操作及其声明的属性始终属于同一命名空间, 它是由自定义标签调用的前缀名称标识的. 例如, 这样调用:
		 *
		 *     <my:test a="1" b="2" c="3"/>
		 *
		 * "test"动作和它的属性 "a", "b", "c" 全部属于"my"前缀标识的命名空间.
		 * 上面的调用相当于:
		 *
		 *     <my:test my:a="1" my:b="2" my:c="3"/>
		 *
		 * 只有当底层标签处理程序支持动态属性时，action属性可能具有与动作调用不同的前缀, 在这种情况下, 具有不同前缀的属性被视为动态属性.
		 */
		private void checkXmlAttributes(Node.CustomTag n,
						Node.JspAttribute[] jspAttrs,
						Hashtable tagDataAttrs) throws JasperException {
			
		    TagInfo tagInfo = n.getTagInfo();
		    if (tagInfo == null) {
		    	err.jspError(n, "jsp.error.missing.tagInfo", n.getQName());
		    }
		    TagAttributeInfo[] tldAttrs = tagInfo.getAttributes();
		    Attributes attrs = n.getAttributes();
	
		    for (int i=0; attrs != null && i<attrs.getLength(); i++) {
				boolean found = false;
				for (int j=0; tldAttrs != null && j<tldAttrs.length; j++) {
				    if (attrs.getLocalName(i).equals(tldAttrs[j].getName())
					    && (attrs.getURI(i) == null
						|| attrs.getURI(i).length() == 0
						|| attrs.getURI(i).equals(n.getURI()))) {
						if (tldAttrs[j].canBeRequestTime()) {
                            Class expectedType = String.class;
                            try {
                                String typeStr = tldAttrs[j].getTypeName();
                                if( tldAttrs[j].isFragment() ) {
                                    expectedType = JspFragment.class;
                                } else if( typeStr != null ) {
                                    expectedType = JspUtil.toClass(typeStr,
								   loader);
                                }
                                jspAttrs[i] = getJspAttribute(attrs.getQName(i),
                                                      attrs.getURI(i),
                                                      attrs.getLocalName(i),
                                                      attrs.getValue(i),
                                                      expectedType,
                                                      n,
                                                      false);
                            } catch (ClassNotFoundException e) {
                                err.jspError(n, 
                                    "jsp.error.unknown_attribute_type",
                                    tldAttrs[j].getName(), 
                                    tldAttrs[j].getTypeName() );
                            }
						} else {
						    // 属性不接受任何表达式. 确保它的值不包含任何值.
						    if (isExpression(n, attrs.getValue(i))) {
			                                err.jspError(n,
							        "jsp.error.attribute.custom.non_rt_with_expr",
								     tldAttrs[j].getName());
						    }
						    jspAttrs[i] = new Node.JspAttribute(attrs.getQName(i),
																attrs.getURI(i),
																attrs.getLocalName(i),
																attrs.getValue(i),
																false,
																null,
																false);
						}
						if (jspAttrs[i].isExpression()) {
						    tagDataAttrs.put(attrs.getQName(i),
								     TagData.REQUEST_TIME_VALUE);
						} else {
						    tagDataAttrs.put(attrs.getQName(i),
								     attrs.getValue(i));
						}
						found = true;
						break;
				    }
				}
				if (!found) {
				    if (tagInfo.hasDynamicAttributes()) {
						jspAttrs[i] = getJspAttribute(attrs.getQName(i),
												      attrs.getURI(i),
												      attrs.getLocalName(i),
												      attrs.getValue(i),
												      java.lang.Object.class,
						                              n,
												      true);
				    } else {
						err.jspError(n, "jsp.error.bad_attribute",
							     attrs.getQName(i), n.getLocalName());
				    }
				}
		    }
		}
	
		/*
		 * 确保给定的自定义操作没有任何无效的命名属性
		 */
		private void checkNamedAttributes(Node.CustomTag n,
						  Node.JspAttribute[] jspAttrs,
						  int start,
						  Hashtable tagDataAttrs) throws JasperException {
	
		    TagInfo tagInfo = n.getTagInfo();
		    if (tagInfo == null) {
			err.jspError(n, "jsp.error.missing.tagInfo", n.getQName());
		    }
		    TagAttributeInfo[] tldAttrs = tagInfo.getAttributes();
            Node.Nodes naNodes = n.getNamedAttributeNodes();
	
		    for (int i=0; i<naNodes.size(); i++) {
                Node.NamedAttribute na = (Node.NamedAttribute)
			    naNodes.getNode(i);
				boolean found = false;
				for (int j=0; j<tldAttrs.length; j++) {
				    /*
				     * 见上面关于命名空间匹配的注释. 对于命名属性,使用前缀而不是URI作为匹配标准, 因为在JSP文档中,
				     * 在解析命名属性时，必须跟踪哪个命名空间在作用域中, 为了确定命名属性名称的前缀匹配的URI.
				     */
				    String attrPrefix = na.getPrefix();
				    if (na.getLocalName().equals(tldAttrs[j].getName())
					    && (attrPrefix == null || attrPrefix.length() == 0
						|| attrPrefix.equals(n.getPrefix()))) {
						jspAttrs[start + i] = new Node.JspAttribute(na, false);
						NamedAttributeVisitor nav = null;
						if (na.getBody() != null) {
						    nav = new NamedAttributeVisitor();
						    na.getBody().visit(nav);
						}
						if (nav != null && nav.hasDynamicContent()) {
						    tagDataAttrs.put(na.getName(),
								     TagData.REQUEST_TIME_VALUE);
						} else {
						    tagDataAttrs.put(na.getName(), na.getText());    
						}
						found = true;
						break;
				    }
				}
				if (!found) {
				    if (tagInfo.hasDynamicAttributes()) {
				    	jspAttrs[start + i] = new Node.JspAttribute(na, true);
				    } else {
						err.jspError(n, "jsp.error.bad_attribute",
							     na.getName(), n.getLocalName());
				    }
				}
		    }
		}
	
		/**
		 * 预处理属性可以是表达式. 表达式分隔符被删除.
         * <p>
         * 如果value 是 null, 检查树节点中是否有NamedAttribute 子元素, 如果这样的话,
         * 构造一个JspAttribute 由于一个 child NamedAttribute 节点.
		 */
		private Node.JspAttribute getJspAttribute(String qName,
							  String uri,
							  String localName,
							  String value,
	                          Class expectedType,
	                          Node n,
							  boolean dynamic)
                throws JasperException {

            Node.JspAttribute result = null;

		    // XXX 在non-Xml页面中查看"%=foo%"是否是一个错误?
		    // (不会在xml页面中查看"<%=foo%>, 因为'<' 在xml中不是一个有效的属性值).

            if (value != null) {
                if (n.getRoot().isXmlSyntax() && value.startsWith("%=")) {
                    result = new Node.JspAttribute(qName,
													uri,
													localName,
													value.substring(2, value.length()-1),
													true,
													null,
													dynamic);
                } else if(!n.getRoot().isXmlSyntax() && value.startsWith("<%=")) {
                    result = new Node.JspAttribute(qName,
													uri,
													localName,
													value.substring(3, value.length()-2),
													true,
													null,
													dynamic);
                } else {
                    // 属性可以包含表达式而不是脚本的表达式; 因此, 我们想通过表达式解释器运行它

                    // 如果字符串包含表达式，则验证表达式语法
                    ELNode.Nodes el = ELParser.parse(value);
                    if (el.containsEL() && !pageInfo.isELIgnored()) {
                    	validateFunctions(el, n);
                        JspUtil.validateExpressions(n.getStart(),
						                            value, 
						                            expectedType, 
						                            getFunctionMapper(el),
						                            this.err);

                        
                        result = new Node.JspAttribute(qName, uri, localName,
																       value, false, el, dynamic);
                    } else {
                    	value = value.replace(Constants.ESC, '$');
                        result = new Node.JspAttribute(qName, uri, localName,
						       value, false, null,
						       dynamic);
                    }
                }
            } else {
                // Value是 null. 检查所有的 NamedAttribute 子节点是否包含这个属性的值.
                // 否则, 找不到属性就返回null.
                Node.NamedAttribute namedAttributeNode = n.getNamedAttributeNode( qName );
                if( namedAttributeNode != null ) {
                    result = new Node.JspAttribute(namedAttributeNode,
						   dynamic);
                }
            }

            return result;
        }
	
		/*
		 * 检查给定的属性值是运行时或EL表达式.
		 */
		private boolean isExpression(Node n, String value) {
		    if ((n.getRoot().isXmlSyntax() && value.startsWith("%="))
			    || (!n.getRoot().isXmlSyntax() && value.startsWith("<%="))
	   		    || (value.indexOf("${") != -1 && !pageInfo.isELIgnored()))
			return true;
		    else
			return false;
		}
	
		/*
		 * 抛出异常, 如果指定的节点中指定名称的属性值是指定的RT 或 EL 表达式, 但是规范需要静态值.
		 */
		private void throwErrorIfExpression(Node n, String attrName,
						    String actionName)
		            throws JasperException {
		    if (n.getAttributes() != null
			    && n.getAttributes().getValue(attrName) != null
			    && isExpression(n, n.getAttributes().getValue(attrName))) {
				err.jspError(n,
					     "jsp.error.attribute.standard.non_rt_with_expr",
					     attrName, actionName);
		    }
		}
	
		private static class NamedAttributeVisitor extends Node.Visitor {
		    private boolean hasDynamicContent;
	
		    public void doVisit(Node n) throws JasperException {
				if (!(n instanceof Node.JspText)
				        && !(n instanceof Node.TemplateText)) {
				    hasDynamicContent = true;
				}
				visitBody(n);
		    }
		    
		    public boolean hasDynamicContent() {
		    	return hasDynamicContent;
		    }
		}
	
		private String findUri(String prefix, Node n) {
	
		    for (Node p = n; p != null; p = p.getParent()) {
				Attributes attrs = p.getTaglibAttributes();
				if (attrs == null) {
				    continue;
				}
				for (int i = 0; i < attrs.getLength(); i++) {
				    String name = attrs.getQName(i);
				    int k = name.indexOf(':');
				    if (prefix == null && k < 0) {
						// 未指定前缀，找到默认ns
						return attrs.getValue(i);
				    }
				    if (prefix != null && k >= 0 &&
				    		prefix.equals(name.substring(k+1))) {
				    	return attrs.getValue(i);
				    }
				}
		    }
		    return null;
		}
	
		/**
		 * 验证EL表达式中的函数
		 */
		private void validateFunctions(ELNode.Nodes el, Node n) 
			throws JasperException {
	
		    class FVVisitor extends ELNode.Visitor {
	
				Node n;
		
				FVVisitor(Node n) {
				    this.n = n;
				}
		
				public void visit(ELNode.Function func) throws JasperException {
				    String prefix = func.getPrefix();
				    String function = func.getName();
				    String uri = null;
		
				    if (n.getRoot().isXmlSyntax()) {
				        uri = findUri(prefix, n);
				    } else if (prefix != null) {
				    	uri = pageInfo.getURI(prefix);
				    }
		
				    if (uri == null) {
						if (prefix == null) {
						    err.jspError(n, "jsp.error.noFunctionPrefix",
							function);
						}
						else {
						    err.jspError(n,
							"jsp.error.attribute.invalidPrefix", prefix);
						}
				    }
				    TagLibraryInfo taglib = pageInfo.getTaglib(uri);
				    FunctionInfo funcInfo = null;
				    if (taglib != null) {
				    	funcInfo = taglib.getFunction(function);
				    }
				    if (funcInfo == null) {
				    	err.jspError(n, "jsp.error.noFunction", function);
				    }
				    // 跳过TLD函数唯一性检查.  Done by Schema ?
				    func.setUri(uri);
				    func.setFunctionInfo(funcInfo);
				    processSignature(func);
				}
		    }
		    el.visit(new FVVisitor(n));
		}
	
		private void processSignature(ELNode.Function func)
			throws JasperException {
		    func.setMethodName(getMethod(func));
		    func.setParameters(getParameters(func));
		}
	
		/**
		 * 从签名中获取方法名.
		 */
		private String getMethod(ELNode.Function func)
			throws JasperException {
		    FunctionInfo funcInfo = func.getFunctionInfo();
		    String signature = funcInfo.getFunctionSignature();
		    
		    int start = signature.indexOf(' ');
		    if (start < 0) {
				err.jspError("jsp.error.tld.fn.invalid.signature",
					     func.getPrefix(), func.getName());
		    }
		    int end = signature.indexOf('(');
		    if (end < 0) {
				err.jspError("jsp.error.tld.fn.invalid.signature.parenexpected",
					     func.getPrefix(), func.getName());
		    }
		    return signature.substring(start+1, end).trim();
		}
	
		/**
		 * 从函数签名中获取参数类型.
		 * @return 参数类名称数组
		 */
		private String[] getParameters(ELNode.Function func) 
			throws JasperException {
		    FunctionInfo funcInfo = func.getFunctionInfo();
		    String signature = funcInfo.getFunctionSignature();
		    ArrayList params = new ArrayList();
		    // 签名的形式是
		    // <return-type> S <method-name S? '('
		    // < <arg-type> ( ',' <arg-type> )* )? ')'
		    int start = signature.indexOf('(') + 1;
		    boolean lastArg = false;
		    while (true) {
				int p = signature.indexOf(',', start);
				if (p < 0) {
				    p = signature.indexOf(')', start);
				    if (p < 0) {
					err.jspError("jsp.error.tld.fn.invalid.signature",
						     func.getPrefix(), func.getName());
				    }
				    lastArg = true;
				}
                String arg = signature.substring(start, p).trim();
                if (!"".equals(arg)) {
                    params.add(arg);
                }
				if (lastArg) {
				    break;
				}
				start = p+1;
		    }
		    return (String[]) params.toArray(new String[params.size()]);
		}
	
		private FunctionMapper getFunctionMapper(ELNode.Nodes el)
			throws JasperException {
	
		    class ValidateFunctionMapper implements FunctionMapper {
	
				private HashMap fnmap = new java.util.HashMap();
				public void mapFunction(String fnQName, Method method) {
				    fnmap.put(fnQName, method);
				}
		
				public Method resolveFunction(String prefix,
							      String localName) {
				    return (Method) this.fnmap.get(prefix + ":" + localName);
				}
		    }
	
		    class MapperELVisitor extends ELNode.Visitor {
				ValidateFunctionMapper fmapper;
		
				MapperELVisitor(ValidateFunctionMapper fmapper) {
				    this.fmapper = fmapper;
				}
		
				public void visit(ELNode.Function n) throws JasperException {
		
				    Class c = null;
				    Method method = null;
				    try {
						c = loader.loadClass(
							n.getFunctionInfo().getFunctionClass());
				    } catch (ClassNotFoundException e) {
						err.jspError("jsp.error.function.classnotfound",
							     n.getFunctionInfo().getFunctionClass(),
							     n.getPrefix() + ':' + n.getName(),
							     e.getMessage());
				    }
				    String paramTypes[] = n.getParameters();
				    int size = paramTypes.length;
				    Class params[] = new Class[size];
				    int i = 0;
				    try {
						for (i = 0; i < size; i++) {
						    params[i] = JspUtil.toClass(paramTypes[i], loader);
						}
						method = c.getDeclaredMethod(n.getMethodName(), params);
				    } catch (ClassNotFoundException e) {
						err.jspError("jsp.error.signature.classnotfound",
							     paramTypes[i],
							     n.getPrefix() + ':' + n.getName(),
							     e.getMessage());
				    } catch (NoSuchMethodException e ) {
						err.jspError("jsp.error.noFunctionMethod",
							     n.getMethodName(), n.getName(),
							     c.getName());
				    }
				    fmapper.mapFunction(n.getPrefix() + ':' + n.getName(), method);
				}
		    }
	
		    ValidateFunctionMapper fmapper = new ValidateFunctionMapper();
		    el.visit(new MapperELVisitor(fmapper));
		    return fmapper;
		}
    }

    /**
     * 验证所有标签的TagExtraInfo 类的访问者
     */
    static class TagExtraInfoVisitor extends Node.Visitor {
	
		private PageInfo pageInfo;
		private ErrorDispatcher err;
	
		TagExtraInfoVisitor(Compiler compiler) {
		    this.pageInfo = compiler.getPageInfo();
		    this.err = compiler.getErrorDispatcher();
		}
	
		public void visit(Node.CustomTag n) throws JasperException {
		    TagInfo tagInfo = n.getTagInfo();
		    if (tagInfo == null) {
		    	err.jspError(n, "jsp.error.missing.tagInfo", n.getQName());
		    }
	
		    ValidationMessage[] errors = tagInfo.validate(n.getTagData());
            if (errors != null && errors.length != 0) {
            	StringBuffer errMsg = new StringBuffer();
                errMsg.append("<h3>");
                errMsg.append(Localizer.getMessage("jsp.error.tei.invalid.attributes",
						   n.getQName()));
                errMsg.append("</h3>");
                for (int i=0; i<errors.length; i++) {
                    errMsg.append("<p>");
				    if (errors[i].getId() != null) {
						errMsg.append(errors[i].getId());
						errMsg.append(": ");
				    }
                    errMsg.append(errors[i].getMessage());
                    errMsg.append("</p>");
                }
                err.jspError(n, errMsg.toString());
            }
		    visitBody(n);
		}
    }

    public static void validate(Compiler compiler,
				Node.Nodes page) throws JasperException {

		/*
		 * 首先访问 page/tag 指令, 因为它们对页面是全局的，并且是独立于位置的.
		 */
		page.visit(new DirectiveVisitor(compiler));
	
		// 确定默认输出内容类型
		PageInfo pageInfo = compiler.getPageInfo();
		String contentType = pageInfo.getContentType();
	
		if (contentType == null || contentType.indexOf("charset=") < 0) {
		    boolean isXml = page.getRoot().isXmlSyntax();
		    String defaultType;
		    if (contentType == null) {
		    	defaultType = isXml? "text/xml": "text/html";
		    } else {
		    	defaultType = contentType;
		    }
	
		    String charset = null;
		    if (isXml) {
		    	charset = "UTF-8";
		    } else {
				if (!page.getRoot().isDefaultPageEncoding()) {
				    charset = page.getRoot().getPageEncoding();
				}
		    }
	
		    if (charset != null) {
		    	pageInfo.setContentType(defaultType + ";charset=" + charset);
		    } else {
		    	pageInfo.setContentType(defaultType);
		    }
		}
	
		/*
		 * 验证所有其他节点.
		 * 此验证步骤包括检查自定义标记对TLD中的信息的强制属性和可选属性(自定义标签的第一个验证步骤, 根据 JSP.10.5).
		 */
		page.visit(new ValidateVisitor(compiler));
	
		/*
		 * 调用所有引入标签的TagLibraryValidator 类(自定义标记的第二个验证步骤, 根据 JSP.10.5).
		 */
		validateXmlView(new PageDataImpl(page, compiler), compiler);
	
		/*
		 * 调用所有引入标签的TagExtraInfo 方法 isValid() (自定义标记的第三个验证步骤, 根据JSP.10.5).
		 */
		page.visit(new TagExtraInfoVisitor(compiler));

    }


    //*********************************************************************
    // Private (utility) methods

    /**
     * 验证所有引入的标签库的TagLibraryValidator类的 XML视图.
     */
    private static void validateXmlView(PageData xmlView, Compiler compiler)
	        throws JasperException {

		StringBuffer errMsg = null;
		ErrorDispatcher errDisp = compiler.getErrorDispatcher();
	
		for (Iterator iter=compiler.getPageInfo().getTaglibs().iterator();
		         iter.hasNext(); ) {
	
		    Object o = iter.next();
		    if (!(o instanceof TagLibraryInfoImpl))
			continue;
		    TagLibraryInfoImpl tli = (TagLibraryInfoImpl) o;
	
		    ValidationMessage[] errors = tli.validate(xmlView);
            if ((errors != null) && (errors.length != 0)) {
                if (errMsg == null) {
				    errMsg = new StringBuffer();
				}
                errMsg.append("<h3>");
                errMsg.append(Localizer.getMessage("jsp.error.tlv.invalid.page",
						   tli.getShortName()));
                errMsg.append("</h3>");
                for (int i=0; i<errors.length; i++) {
				    if (errors[i] != null) {
						errMsg.append("<p>");
						errMsg.append(errors[i].getId());
						errMsg.append(": ");
						errMsg.append(errors[i].getMessage());
						errMsg.append("</p>");
				    }
                }
            }
        }
		if (errMsg != null) {
            errDisp.jspError(errMsg.toString());
		}
    }
}

