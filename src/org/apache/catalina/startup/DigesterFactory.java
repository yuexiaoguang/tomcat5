package org.apache.catalina.startup;

import java.net.URL;

import org.apache.catalina.util.SchemaResolver;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSet;

/**
 * 包装一个Digester, 隐藏Digester的初始化细节
 */
public class DigesterFactory {
   protected static org.apache.commons.logging.Log log = 
       org.apache.commons.logging.LogFactory.getLog(DigesterFactory.class);

    /**
     * 由Digester使用的XML解析器.
     */
    private static SchemaResolver schemaResolver;


    /**
     * 创建<code>Digester</code>解析器, 不使用<code>Rule</code>并关闭XML验证.
     */
    public static Digester newDigester(){
        return newDigester(false, false, null);
    }

    
    /**
     * 创建<code>Digester</code>解析器, 关闭XML验证.
     * @param rule 用于解析xml的<code>RuleSet</code>.
     */
    public static Digester newDigester(RuleSet rule){
        return newDigester(false,false,rule);
    }

    
    /**
     * 创建<code>Digester</code>解析器.
     * 
     * @param xmlValidation 启动/关闭XML验证
     * @param xmlNamespaceAware 启动/关闭命名空间验证
     * @param rule 用于解析xml的<code>RuleSet</code>.
     */
    public static Digester newDigester(boolean xmlValidation,
                                       boolean xmlNamespaceAware,
                                       RuleSet rule) {
        Digester digester = new Digester();
        digester.setNamespaceAware(xmlNamespaceAware);
        digester.setValidating(xmlValidation);
        digester.setUseContextClassLoader(true);

        if (xmlValidation || xmlNamespaceAware){
            configureSchema(digester);        
        }

        schemaResolver = new SchemaResolver(digester);
        registerLocalSchema();
        
        digester.setEntityResolver(schemaResolver);
        if ( rule != null ) {
            digester.addRuleSet(rule);
        }
        return (digester);
    }


    /**
     * 用于强制解析器使用本地模式的实用程序, 当可用的时候, 替换<code>schemaLocation</code> XML 元素.
     */
    protected static void registerLocalSchema(){
        // J2EE
        register(Constants.J2eeSchemaResourcePath_14,
                 Constants.J2eeSchemaPublicId_14);
        // W3C
        register(Constants.W3cSchemaResourcePath_10,
                 Constants.W3cSchemaPublicId_10);
        // JSP
        register(Constants.JspSchemaResourcePath_20,
                 Constants.JspSchemaPublicId_20);
        // TLD
        register(Constants.TldDtdResourcePath_11,  
                 Constants.TldDtdPublicId_11);
        
        register(Constants.TldDtdResourcePath_12,
                 Constants.TldDtdPublicId_12);

        register(Constants.TldSchemaResourcePath_20,
                 Constants.TldSchemaPublicId_20);

        // web.xml    
        register(Constants.WebDtdResourcePath_22,
                 Constants.WebDtdPublicId_22);

        register(Constants.WebDtdResourcePath_23,
                 Constants.WebDtdPublicId_23);

        register(Constants.WebSchemaResourcePath_24,
                 Constants.WebSchemaPublicId_24);

        // Web Service
        register(Constants.J2eeWebServiceSchemaResourcePath_11,
                 Constants.J2eeWebServiceSchemaPublicId_11);

        register(Constants.J2eeWebServiceClientSchemaResourcePath_11,
                 Constants.J2eeWebServiceClientSchemaPublicId_11);

    }


    /**
     * 加载资源并将其添加到解析器中.
     */
    protected static void register(String resourceURL, String resourcePublicId){
        URL url = DigesterFactory.class.getResource(resourceURL);
   
        if(url == null) {
            log.warn("Could not get url for " + resourceURL);
        } else {
            schemaResolver.register(resourcePublicId , url.toString() );
        }
    }


    /**
     * 开启 DTD 和 验证 (基于解析器实现类)
     */
    protected static void configureSchema(Digester digester){
        URL url = DigesterFactory.class
                        .getResource(Constants.WebSchemaResourcePath_24);
  
        if(url == null) {
            log.error("Could not get url for " 
                                        + Constants.WebSchemaResourcePath_24);
        } else {
            digester.setSchema(url.toString());     
        }
    }
}
