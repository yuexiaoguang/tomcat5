package org.apache.catalina.startup;

import org.xml.sax.Attributes;

import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Rule;

/**
 * 使用反射设置上下文的属性的Rule(除了 "path").
 */
public class SetContextPropertiesRule extends Rule {

    /**
     * 处理XML 元素的开始.
     *
     * @param attributes 元素属性
     *
     * @exception Exception if a processing error occurs
     */
    public void begin(String namespace, String nameX, Attributes attributes)
        throws Exception {
        for (int i = 0; i < attributes.getLength(); i++) {
            String name = attributes.getLocalName(i);
            if ("".equals(name)) {
                name = attributes.getQName(i);
            }
            if ("path".equals(name) || "docBase".equals(name)) {
                continue;
            }
            String value = attributes.getValue(i);
            IntrospectionUtils.setProperty(digester.peek(), name, value);
        }
    }
}
