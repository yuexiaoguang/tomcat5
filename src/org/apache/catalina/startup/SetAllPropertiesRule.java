package org.apache.catalina.startup;

import org.xml.sax.Attributes;

import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.digester.Rule;

/**
 * 使用反射设置属性的Rule.
 */
public class SetAllPropertiesRule extends Rule {

    /**
     * 处理 XML元素的开始.
     *
     * @param attributes 元素的属性
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
            String value = attributes.getValue(i);
            IntrospectionUtils.setProperty(digester.peek(), name, value);
        }
    }
}
