package org.apache.catalina.startup;


import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.digester.Rule;
import org.xml.sax.Attributes;


/**
 * Rule实现类，用于创建连接器.
 */
public class ConnectorCreateRule extends Rule {

    /**
     * 处理这个元素的开始.
     *
     * @param attributes 这个元素的属性列表
     */
    public void begin(Attributes attributes) throws Exception {
        digester.push(new Connector(attributes.getValue("protocol")));
    }


    /**
     * 处理这个元素的结束.
     */
    public void end() throws Exception {
        Object top = digester.pop();
    }
}
