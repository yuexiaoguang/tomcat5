package org.apache.catalina.util;

import java.io.IOException;
import java.io.Writer;

/**
 * XMLWriter helper class.
 */
public class XMLWriter {

    // -------------------------------------------------------------- Constants

    /**
     * 开放标签
     */
    public static final int OPENING = 0;


    /**
     * 关闭标签
     */
    public static final int CLOSING = 1;


    /**
     * 无内容元素
     */
    public static final int NO_CONTENT = 2;


    // ----------------------------------------------------- Instance Variables


    /**
     * Buffer.
     */
    protected StringBuffer buffer = new StringBuffer();


    /**
     * Writer.
     */
    protected Writer writer = null;


    // ----------------------------------------------------------- Constructors


    /**
     * Constructor.
     */
    public XMLWriter() {
    }


    /**
     * Constructor.
     */
    public XMLWriter(Writer writer) {
        this.writer = writer;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 检索生成的 XML.
     *
     * @return String containing the generated XML
     */
    public String toString() {
        return buffer.toString();
    }


    /**
     * 将属性写入XML.
     *
     * @param namespace Namespace
     * @param namespaceInfo Namespace info
     * @param name Property name
     * @param value Property value
     */
    public void writeProperty(String namespace, String namespaceInfo,
                              String name, String value) {
        writeElement(namespace, namespaceInfo, name, OPENING);
        buffer.append(value);
        writeElement(namespace, namespaceInfo, name, CLOSING);
    }


    /**
     * 将属性写入XML.
     *
     * @param namespace Namespace
     * @param name Property name
     * @param value Property value
     */
    public void writeProperty(String namespace, String name, String value) {
        writeElement(namespace, name, OPENING);
        buffer.append(value);
        writeElement(namespace, name, CLOSING);
    }


    /**
     * 将属性写入XML.
     *
     * @param namespace Namespace
     * @param name Property name
     */
    public void writeProperty(String namespace, String name) {
        writeElement(namespace, name, NO_CONTENT);
    }


    /**
     * 写入一个元素
     *
     * @param name Element name
     * @param namespace Namespace abbreviation
     * @param type Element type
     */
    public void writeElement(String namespace, String name, int type) {
        writeElement(namespace, null, name, type);
    }


    /**
     * 写入一个元素
     *
     * @param namespace Namespace abbreviation
     * @param namespaceInfo Namespace info
     * @param name Element name
     * @param type Element type
     */
    public void writeElement(String namespace, String namespaceInfo,
                             String name, int type) {
        if ((namespace != null) && (namespace.length() > 0)) {
            switch (type) {
            case OPENING:
                if (namespaceInfo != null) {
                    buffer.append("<" + namespace + ":" + name + " xmlns:"
                                  + namespace + "=\""
                                  + namespaceInfo + "\">");
                } else {
                    buffer.append("<" + namespace + ":" + name + ">");
                }
                break;
            case CLOSING:
                buffer.append("</" + namespace + ":" + name + ">\n");
                break;
            case NO_CONTENT:
            default:
                if (namespaceInfo != null) {
                    buffer.append("<" + namespace + ":" + name + " xmlns:"
                                  + namespace + "=\""
                                  + namespaceInfo + "\"/>");
                } else {
                    buffer.append("<" + namespace + ":" + name + "/>");
                }
                break;
            }
        } else {
            switch (type) {
            case OPENING:
                buffer.append("<" + name + ">");
                break;
            case CLOSING:
                buffer.append("</" + name + ">\n");
                break;
            case NO_CONTENT:
            default:
                buffer.append("<" + name + "/>");
                break;
            }
        }
    }


    /**
     * 写入文本
     *
     * @param text Text to append
     */
    public void writeText(String text) {
        buffer.append(text);
    }


    /**
     * 写入数据
     *
     * @param data Data to append
     */
    public void writeData(String data) {
        buffer.append("<![CDATA[" + data + "]]>");
    }


    /**
     * 写入XML Header.
     */
    public void writeXMLHeader() {
        buffer.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n");
    }


    /**
     * 发送数据并初始化缓冲区
     */
    public void sendData()
        throws IOException {
        if (writer != null) {
            writer.write(buffer.toString());
            buffer = new StringBuffer();
        }
    }
}
