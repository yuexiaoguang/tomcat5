package org.apache.jasper.xmlparser;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.compiler.Localizer;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;


/**
 * 用于处理Web应用程序部署描述符和标记库描述符文件的XML解析实用工具.  FIXME - 让它们使用一个单独的类加载器来使用解析器.
 */
public class ParserUtils {

    /**
     * 用于解析XML文档时使用的错误处理程序.
     */
    static ErrorHandler errorHandler = new MyErrorHandler();

    /**
     * 解析XML文档时使用的实体解析器.
     */
    static EntityResolver entityResolver = new MyEntityResolver();

    // 关闭JSP 2直到切换到使用xschema.
    public static boolean validating = false;


    // --------------------------------------------------------- Public Methods

    /**
     * 解析指定的XML文档, 并返回一个<code>TreeNode</code>对应于文档树的根节点.
     *
     * @param uri 正在解析的XML文档的URI
     * @param is 包含部署描述符的输入源
     *
     * @exception JasperException 如果出现输入/输出错误
     * @exception JasperException 如果发生解析错误
     */
    public TreeNode parseXMLDocument(String uri, InputSource is)
        throws JasperException {

        Document document = null;

        // 执行此文档的XML解析, 通过JAXP
        try {
            DocumentBuilderFactory factory =
            DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(validating);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver(entityResolver);
            builder.setErrorHandler(errorHandler);
            document = builder.parse(is);
		} catch (ParserConfigurationException ex) {
            throw new JasperException(Localizer.getMessage("jsp.error.parse.xml", uri), ex);
		} catch (SAXParseException ex) {
            throw new JasperException(Localizer.getMessage("jsp.error.parse.xml.line",
				      uri,
				      Integer.toString(ex.getLineNumber()),
				      Integer.toString(ex.getColumnNumber())),
            		ex);
		} catch (SAXException sx) {
            throw new JasperException(Localizer.getMessage("jsp.error.parse.xml", uri), sx);
        } catch (IOException io) {
            throw new JasperException(Localizer.getMessage("jsp.error.parse.xml", uri), io);
		}

        // 转换结果文档为TreeNode图
        return (convert(null, document.getDocumentElement()));
    }


    /**
     * 解析指定的XML文档, 并返回一个<code>TreeNode</code>对应于文档树的根节点.
     *
     * @param uri 正在解析的XML文档的URI
     * @param is 包含部署描述符的输入源
     *
     * @exception JasperException 如果出现输入/输出错误
     * @exception JasperException 如果发生解析错误
     */
    public TreeNode parseXMLDocument(String uri, InputStream is)
            throws JasperException {

        return (parseXMLDocument(uri, new InputSource(is)));
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 创建并返回一个对应于指定Node的TreeNode, 包括处理所有属性和子节点.
     *
     * @param parent 新的TreeNode的父级TreeNode
     * @param node 要转换的XML文档节点
     */
    protected TreeNode convert(TreeNode parent, Node node) {

        // 为这个节点创建一个TreeNode
        TreeNode treeNode = new TreeNode(node.getNodeName(), parent);

        // 转换此节点的所有属性
        NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            int n = attributes.getLength();
            for (int i = 0; i < n; i++) {
                Node attribute = attributes.item(i);
                treeNode.addAttribute(attribute.getNodeName(),
                                      attribute.getNodeValue());
            }
        }

        // 创建并附加此节点的所有子节点
        NodeList children = node.getChildNodes();
        if (children != null) {
            int n = children.getLength();
            for (int i = 0; i < n; i++) {
                Node child = children.item(i);
                if (child instanceof Comment)
                    continue;
                if (child instanceof Text) {
                    String body = ((Text) child).getData();
                    if (body != null) {
                        body = body.trim();
                        if (body.length() > 0)
                            treeNode.setBody(body);
                    }
                } else {
                    TreeNode treeChild = convert(treeNode, child);
                }
            }
        }
        
        // 返回完整的TreeNode 图
        return (treeNode);
    }
}


// ------------------------------------------------------------ Private Classes

class MyEntityResolver implements EntityResolver {

    private Log log = LogFactory.getLog(MyEntityResolver.class);

    public InputSource resolveEntity(String publicId, String systemId)
            throws SAXException {
        for (int i = 0; i < Constants.CACHED_DTD_PUBLIC_IDS.length; i++) {
            String cachedDtdPublicId = Constants.CACHED_DTD_PUBLIC_IDS[i];
            if (cachedDtdPublicId.equals(publicId)) {
                String resourcePath = Constants.CACHED_DTD_RESOURCE_PATHS[i];
                InputStream input = this.getClass().getResourceAsStream(
                        resourcePath);
                if (input == null) {
                    throw new SAXException(Localizer.getMessage(
                            "jsp.error.internal.filenotfound", resourcePath));
                }
                InputSource isrc = new InputSource(input);
                return isrc;
            }
        }
        if (log.isDebugEnabled())
            log.debug("Resolve entity failed" + publicId + " " + systemId);
        log.error(Localizer.getMessage("jsp.error.parse.xml.invalidPublicId",
                publicId));
        return null;
    }
}

class MyErrorHandler implements ErrorHandler {

    private Log log = LogFactory.getLog(MyErrorHandler.class);

    public void warning(SAXParseException ex) throws SAXException {
        if (log.isDebugEnabled())
            log.debug("ParserUtils: warning ", ex);
        // 忽略警告
    }

    public void error(SAXParseException ex) throws SAXException {
        throw ex;
    }

    public void fatalError(SAXParseException ex) throws SAXException {
        throw ex;
    }
}