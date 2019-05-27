package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;
import org.apache.jasper.tagplugins.jstl.Util;

public class Remove implements TagPlugin {
    
    public void doTag(TagPluginContext ctxt) {
        
        //范围标志
        boolean hasScope = ctxt.isAttributeSpecified("scope");
        
        //"var"的值
        String strVar = ctxt.getConstantAttribute("var");
        
        //在一定范围内删除属性. 默认范围是 "page".
        if(hasScope){
            int iScope = Util.getScope(ctxt.getConstantAttribute("scope"));
            ctxt.generateJavaSource("pageContext.removeAttribute(\"" + strVar + "\"," + iScope + ");");
        }else{
            ctxt.generateJavaSource("pageContext.removeAttribute(\"" + strVar + "\");");
        }
    }
    
}
