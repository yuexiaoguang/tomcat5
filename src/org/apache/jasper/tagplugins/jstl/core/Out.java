package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;


public final class Out implements TagPlugin {
    
    public void doTag(TagPluginContext ctxt) {
        
        //这两个数据成员用来指示是否指定了相应的属性
        boolean hasDefault=false, hasEscapeXml=false;
        hasDefault = ctxt.isAttributeSpecified("default");
        hasEscapeXml = ctxt.isAttributeSpecified("escapeXml");
        
        //strValName, strEscapeXmlName & strDefName 是两个变量的名字
        //standing for value, escapeXml 和默认属性
        String strValName = ctxt.getTemporaryVariableName();
        String strDefName = ctxt.getTemporaryVariableName();
        String strEscapeXmlName = ctxt.getTemporaryVariableName();
        
        //根据标签文件, value 属性是强制性的.
        ctxt.generateJavaSource("String " + strValName + " = null;");
        ctxt.generateJavaSource("if(");
        ctxt.generateAttribute("value");
        ctxt.generateJavaSource("!=null){");
        ctxt.generateJavaSource("    " + strValName + " = (");
        ctxt.generateAttribute("value");
        ctxt.generateJavaSource(").toString();");
        ctxt.generateJavaSource("}");
        
        //初始化strDefName 为 null.
        //如果指定了默认值, 然后将值赋给它;
        ctxt.generateJavaSource("String " + strDefName + " = null;\n");
        if(hasDefault){
            ctxt.generateJavaSource("if(");
            ctxt.generateAttribute("default");
            ctxt.generateJavaSource(" != null){");
            ctxt.generateJavaSource(strDefName + " = (");
            ctxt.generateAttribute("default");
            ctxt.generateJavaSource(").toString();");
            ctxt.generateJavaSource("}");
        }
        
        //初始化strEscapeXmlName 为 true;
        //如果指定了escapeXml, 将值赋给它;
        ctxt.generateJavaSource("boolean " + strEscapeXmlName + " = true;");
        if(hasEscapeXml){
            ctxt.generateJavaSource(strEscapeXmlName + " = Boolean.parseBoolean((");
            ctxt.generateAttribute("default");
            ctxt.generateJavaSource(").toString());");
        }
        
        //主要部分. 
        ctxt.generateJavaSource("if(null != " + strValName +"){");
        ctxt.generateJavaSource("    if(" + strEscapeXmlName + "){");
        ctxt.generateJavaSource("        " + strValName + " = org.apache.jasper.tagplugins.jstl.Util.escapeXml(" + strValName + ");");
        ctxt.generateJavaSource("    }");
        ctxt.generateJavaSource("    out.write(" + strValName + ");");
        ctxt.generateJavaSource("}else{");
        ctxt.generateJavaSource("    if(null != " + strDefName + "){");
        ctxt.generateJavaSource("        if(" + strEscapeXmlName + "){");
        ctxt.generateJavaSource("            " + strDefName + " = org.apache.jasper.tagplugins.jstl.Util.escapeXml(" + strDefName + ");");
        ctxt.generateJavaSource("        }");
        ctxt.generateJavaSource("        out.write(" + strDefName + ");");
        ctxt.generateJavaSource("    }else{");
        ctxt.generateBody();
        ctxt.generateJavaSource("    }");
        ctxt.generateJavaSource("}");   
    }
}
