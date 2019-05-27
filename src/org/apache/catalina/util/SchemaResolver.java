package org.apache.catalina.util;


import java.util.HashMap;

import org.apache.tomcat.util.digester.Digester;
import org.xml.sax.InputSource;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXException;

/**
 * 这个类实现了一个本地 SAX的<code>EntityResolver</code>.
 * 所有的dtd和模式用来验证web.xml文件将重新定向到一个servlet-api.jar和jsp-api.jar保存的本地文件.
 */
public class SchemaResolver implements EntityResolver {

    protected Digester digester;


    /**
     * 已经注册的DTD和模式的URL, 对应的公共标识符作为key.
     */
    protected HashMap entityValidator = new HashMap();


    /**
     * 正在分析的DTD的公共标识符
     */
    protected String publicId = null;


    /**
     * DTD和模式之间的区别扩展.
     */
    protected String schemaExtension = "xsd";


    /**
     * 创建一个新的<code>EntityResolver</code> that 将所有远程DTD和模式重定向到一个本地定义.
     * 
     * @param digester The digester instance.
     */
    public SchemaResolver(Digester digester) {
        this.digester = digester;
    }


    /**
     * 使用指定的公共标识符注册指定的 DTD/Schema URL. 在第一次调用<code>parse()</code>之前调用这个方法.
     *
     * 当添加模式文件 (*.xsd)时, 只有文件名才会被添加. 如果添加了同名的两个模式, 只有最后一个将被储存.
     *
     * @param publicId 要解析的DTD的公共标识符
     * @param entityURL 用于读取此DTD的URL
     */
     public void register(String publicId, String entityURL) {
         String key = publicId;
         if (publicId.indexOf(schemaExtension) != -1)
             key = publicId.substring(publicId.lastIndexOf('/')+1);
         entityValidator.put(key, entityURL);
     }


    /**
     * 解析请求的外部实体.
     *
     * @param publicId 被引用实体的公共标识符
     * @param systemId 被引用实体的系统标识符
     *
     * @exception SAXException 如果发生解析异常
     */
    public InputSource resolveEntity(String publicId, String systemId)
        throws SAXException {

        if (publicId != null) {
            this.publicId = publicId;
            digester.setPublicId(publicId);
        }

        // 此系统标识符是否已注册?
        String entityURL = null;
        if (publicId != null) {
            entityURL = (String) entityValidator.get(publicId);
        }

        // 将架构位置重定向到本地目的地
        String key = null;
        if (entityURL == null && systemId != null) {
            key = systemId.substring(systemId.lastIndexOf('/')+1);
            entityURL = (String)entityValidator.get(key);
        }

        if (entityURL == null) {
           return (null);
        }

        try {
            return (new InputSource(entityURL));
        } catch (Exception e) {
            throw new SAXException(e);
        }
    }
}
