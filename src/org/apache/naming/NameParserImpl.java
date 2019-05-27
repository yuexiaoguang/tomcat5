package org.apache.naming;

import javax.naming.NameParser;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.CompositeName;

/**
 * 解析名字.
 */
public class NameParserImpl implements NameParser {

    // ----------------------------------------------------- NameParser Methods

    /**
     * 将名称解析为组件
     * 
     * @param name 要解析的非空字符串名称
     * @return 使用这个解析器的命名约定解析的名称的非null形式.
     */
    public Name parse(String name)
        throws NamingException {
        return new CompositeName(name);
    }
}

