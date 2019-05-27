package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.*;

public final class Otherwise implements TagPlugin {
    
    public void doTag(TagPluginContext ctxt) {
        
        // 查看 When.java 了解为什么需要 "}".
        ctxt.generateJavaSource("} else {");
        ctxt.generateBody();
    }
}
