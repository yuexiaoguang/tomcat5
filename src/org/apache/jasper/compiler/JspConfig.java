package org.apache.jasper.compiler;

import java.io.InputStream;
import java.util.Iterator;
import java.util.Vector;
import java.net.URL;

import javax.servlet.ServletContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jasper.JasperException;
import org.apache.jasper.xmlparser.ParserUtils;
import org.apache.jasper.xmlparser.TreeNode;
import org.xml.sax.InputSource;

/**
 * 处理WEB_INF/web.xml中的JSP配置元素. 用于在JSP页面上指定JSP配置信息
 */
public class JspConfig {

    private static final String WEB_XML = "/WEB-INF/web.xml";

    // Logger
    private Log log = LogFactory.getLog(JspConfig.class);

    private Vector jspProperties = null;
    private ServletContext ctxt;
    private boolean initialized = false;

    private String defaultIsXml = null;		// unspecified
    private String defaultIsELIgnored = null;	// unspecified
    private String defaultIsScriptingInvalid = "false";
    private JspProperty defaultJspProperty;

    public JspConfig(ServletContext ctxt) {
	this.ctxt = ctxt;
    }

    private void processWebDotXml(ServletContext ctxt) throws JasperException {

        InputStream is = null;
        try {
            URL uri = ctxt.getResource(WEB_XML);
            if (uri == null) {
            	// no web.xml
                return;
            }

	        is = uri.openStream();
	        InputSource ip = new InputSource(is);
	        ip.setSystemId(uri.toExternalForm()); 
	
	        ParserUtils pu = new ParserUtils();
	        TreeNode webApp = pu.parseXMLDocument(WEB_XML, ip);
	
		    if (webApp == null
	                    || !"2.4".equals(webApp.findAttribute("version"))) {
		        defaultIsELIgnored = "true";
		        return;
		    }
		    TreeNode jspConfig = webApp.findChild("jsp-config");
		    if (jspConfig == null) {
		        return;
		    }

            jspProperties = new Vector();
            Iterator jspPropertyList = jspConfig.findChildren("jsp-property-group");
            while (jspPropertyList.hasNext()) {

                TreeNode element = (TreeNode) jspPropertyList.next();
                Iterator list = element.findChildren();

                Vector urlPatterns = new Vector();
                String pageEncoding = null;
                String scriptingInvalid = null;
                String elIgnored = null;
                String isXml = null;
                Vector includePrelude = new Vector();
                Vector includeCoda = new Vector();

                while (list.hasNext()) {

                    element = (TreeNode) list.next();
                    String tname = element.getName();

                    if ("url-pattern".equals(tname))
                        urlPatterns.addElement( element.getBody() );
                    else if ("page-encoding".equals(tname))
                        pageEncoding = element.getBody();
                    else if ("is-xml".equals(tname))
                        isXml = element.getBody();
                    else if ("el-ignored".equals(tname))
                        elIgnored = element.getBody();
                    else if ("scripting-invalid".equals(tname))
                        scriptingInvalid = element.getBody();
                    else if ("include-prelude".equals(tname))
                        includePrelude.addElement(element.getBody());
                    else if ("include-coda".equals(tname))
                        includeCoda.addElement(element.getBody());
                }

                if (urlPatterns.size() == 0) {
                    continue;
                }
 
                // 为每个URL模式添加一个JspPropertyGroup. 这使得匹配逻辑更容易.
                for( int p = 0; p < urlPatterns.size(); p++ ) {
                    String urlPattern = (String)urlPatterns.elementAt( p );
                    String path = null;
                    String extension = null;
 
                    if (urlPattern.indexOf('*') < 0) {
                        // 精确匹配
                        path = urlPattern;
                    } else {
                        int i = urlPattern.lastIndexOf('/');
                        String file;
                        if (i >= 0) {
                            path = urlPattern.substring(0,i+1);
                            file = urlPattern.substring(i+1);
                        } else {
                            file = urlPattern;
                        }
 
                        // 模式必须是 "*", 或 "*.jsp"的形式
                        if (file.equals("*")) {
                            extension = "*";
                        } else if (file.startsWith("*.")) {
                            extension = file.substring(file.indexOf('.')+1);
                        }

                        // URL模式重构为以下:
                        // path != null, extension == null:  / or /foo/bar.ext
                        // path == null, extension != null:  *.ext
                        // path != null, extension == "*":   /foo/*
                        boolean isStar = "*".equals(extension);
                        if ((path == null && (extension == null || isStar))
                                || (path != null && !isStar)) {
                            if (log.isWarnEnabled()) {
                            	log.warn(Localizer.getMessage("jsp.warning.bad.urlpattern.propertygroup",
                                    urlPattern));
                            }
                            continue;
                        }
                    }

                    JspProperty property = new JspProperty(isXml,
                                                           elIgnored,
                                                           scriptingInvalid,
                                                           pageEncoding,
                                                           includePrelude,
                                                           includeCoda);
                    JspPropertyGroup propertyGroup =
                        new JspPropertyGroup(path, extension, property);

                    jspProperties.addElement(propertyGroup);
                }
            }
        } catch (Exception ex) {
            throw new JasperException(ex);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Throwable t) {}
            }
        }
    }

    private void init() throws JasperException {
		if (!initialized) {
		    processWebDotXml(ctxt);
		    defaultJspProperty = new JspProperty(defaultIsXml,
							 defaultIsELIgnored,
							 defaultIsScriptingInvalid,
							 null, null, null);
		    initialized = true;
		}
    }

    /**
     * 选择具有更严格的URL模式的属性组.
     * 如果有多个, 选择第一个.
     */
    private JspPropertyGroup selectProperty(JspPropertyGroup prev,
                                            JspPropertyGroup curr) {
        if (prev == null) {
            return curr;
        }
        if (prev.getExtension() == null) {
            // 精确匹配
            return prev;
        }
        if (curr.getExtension() == null) {
            // 精确匹配
            return curr;
        }
        String prevPath = prev.getPath();
        String currPath = curr.getPath();
        if (prevPath == null && currPath == null) {
            // 指定一个 *.ext, 保留第一个
            return prev;
        }
        if (prevPath == null && currPath != null) {
            return curr;
        }
        if (prevPath != null && currPath == null) {
            return prev;
        }
        if (prevPath.length() >= currPath.length()) {
            return prev;
        }
        return curr;
    }
            

    /**
     * 找到与所提供资源最匹配的属性.
     * @param uri 提供的资源.
     * @return 表示最佳匹配的JspProperty, 或一些默认的.
     */
    public JspProperty findJspProperty(String uri) throws JasperException {

		init();
	
		// JSP 配置设置不适用于标签文件
		if (jspProperties == null || uri.endsWith(".tag")
		        || uri.endsWith(".tagx")) {
		    return defaultJspProperty;
		}
	
		String uriPath = null;
		int index = uri.lastIndexOf('/');
		if (index >=0 ) {
		    uriPath = uri.substring(0, index+1);
		}
		String uriExtension = null;
		index = uri.lastIndexOf('.');
		if (index >=0) {
		    uriExtension = uri.substring(index+1);
		}
	
		Vector includePreludes = new Vector();
		Vector includeCodas = new Vector();
	
		JspPropertyGroup isXmlMatch = null;
		JspPropertyGroup elIgnoredMatch = null;
		JspPropertyGroup scriptingInvalidMatch = null;
		JspPropertyGroup pageEncodingMatch = null;
	
		Iterator iter = jspProperties.iterator();
		while (iter.hasNext()) {
	
		    JspPropertyGroup jpg = (JspPropertyGroup) iter.next();
		    JspProperty jp = jpg.getJspProperty();
	
	             // (数组长度必须相同)
	             String extension = jpg.getExtension();
	             String path = jpg.getPath();
	 
	             if (extension == null) {
	                 // 精确匹配模式: /a/foo.jsp
	                 if (!uri.equals(path)) {
	                     // not matched;
	                     continue;
	                 }
	             } else {
	                 // 匹配模式 *.ext 或 /p/*
	                 if (path != null && uriPath != null &&
	                         ! uriPath.startsWith(path)) {
	                     // 不匹配
	                     continue;
	                 }
	                 if (!extension.equals("*") &&
	                                 !extension.equals(uriExtension)) {
	                     // 不匹配
	                     continue;
	                 }
	             }
	             // 有一个匹配
	             // 添加include-preludes 和 include-codas
	             if (jp.getIncludePrelude() != null) {
	                 includePreludes.addAll(jp.getIncludePrelude());
	             }
	             if (jp.getIncludeCoda() != null) {
	                 includeCodas.addAll(jp.getIncludeCoda());
	             }
	
	             // 如果有相同属性的前一个匹配项, 记住那个更严格的.
	             if (jp.isXml() != null) {
	                 isXmlMatch = selectProperty(isXmlMatch, jpg);
	             }
	             if (jp.isELIgnored() != null) {
	                 elIgnoredMatch = selectProperty(elIgnoredMatch, jpg);
	             }
	             if (jp.isScriptingInvalid() != null) {
	                 scriptingInvalidMatch =
	                     selectProperty(scriptingInvalidMatch, jpg);
	             }
	             if (jp.getPageEncoding() != null) {
	                 pageEncodingMatch = selectProperty(pageEncodingMatch, jpg);
	             }
		}
	
	
		String isXml = defaultIsXml;
		String isELIgnored = defaultIsELIgnored;
		String isScriptingInvalid = defaultIsScriptingInvalid;
		String pageEncoding = null;
	
		if (isXmlMatch != null) {
		    isXml = isXmlMatch.getJspProperty().isXml();
		}
		if (elIgnoredMatch != null) {
		    isELIgnored = elIgnoredMatch.getJspProperty().isELIgnored();
		}
		if (scriptingInvalidMatch != null) {
		    isScriptingInvalid =
			scriptingInvalidMatch.getJspProperty().isScriptingInvalid();
		}
		if (pageEncodingMatch != null) {
		    pageEncoding = pageEncodingMatch.getJspProperty().getPageEncoding();
		}
	
		return new JspProperty(isXml, isELIgnored, isScriptingInvalid,
				       pageEncoding, includePreludes, includeCodas);
    }

    /**
     * 要了解URI是否与JSP配置中的URL模式匹配. 如果匹配, 随后uri 是一个 JSP 页面. 这是主要用于jspc.
     */
    public boolean isJspPage(String uri) throws JasperException {

        init();
        if (jspProperties == null) {
            return false;
        }

        String uriPath = null;
        int index = uri.lastIndexOf('/');
        if (index >=0 ) {
            uriPath = uri.substring(0, index+1);
        }
        String uriExtension = null;
        index = uri.lastIndexOf('.');
        if (index >=0) {
            uriExtension = uri.substring(index+1);
        }

        Iterator iter = jspProperties.iterator();
        while (iter.hasNext()) {

            JspPropertyGroup jpg = (JspPropertyGroup) iter.next();
            JspProperty jp = jpg.getJspProperty();

            String extension = jpg.getExtension();
            String path = jpg.getPath();

            if (extension == null) {
                if (uri.equals(path)) {
                    // 有一个精确的匹配
                    return true;
                }
            } else {
                if ((path == null || path.equals(uriPath)) &&
                    (extension.equals("*") || extension.equals(uriExtension))) {
                    // 匹配 *, *.ext, /p/*, or /p/*.ext
                    return true;
                }
            }
        }
        return false;
    }

    static class JspPropertyGroup {
		private String path;
		private String extension;
		private JspProperty jspProperty;
	
		JspPropertyGroup(String path, String extension,
				 JspProperty jspProperty) {
		    this.path = path;
		    this.extension = extension;
		    this.jspProperty = jspProperty;
		}
	
		public String getPath() {
		    return path;
		}
	
		public String getExtension() {
		    return extension;
		}
	
		public JspProperty getJspProperty() {
		    return jspProperty;
		}
    }

    static public class JspProperty {

		private String isXml;
		private String elIgnored;
		private String scriptingInvalid;
		private String pageEncoding;
		private Vector includePrelude;
		private Vector includeCoda;
	
		public JspProperty(String isXml, String elIgnored,
			    String scriptingInvalid, String pageEncoding,
			    Vector includePrelude, Vector includeCoda) {
	
		    this.isXml = isXml;
		    this.elIgnored = elIgnored;
		    this.scriptingInvalid = scriptingInvalid;
		    this.pageEncoding = pageEncoding;
		    this.includePrelude = includePrelude;
		    this.includeCoda = includeCoda;
		}
	
		public String isXml() {
		    return isXml;
		}
	
		public String isELIgnored() {
		    return elIgnored;
		}
	
		public String isScriptingInvalid() {
		    return scriptingInvalid;
		}
	
		public String getPageEncoding() {
		    return pageEncoding;
		}
	
		public Vector getIncludePrelude() {
		    return includePrelude;
		}
	
		public Vector getIncludeCoda() {
		    return includeCoda;
		}
    }
}
