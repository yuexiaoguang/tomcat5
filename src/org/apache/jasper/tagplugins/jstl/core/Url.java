package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;
import org.apache.jasper.tagplugins.jstl.Util;

public class Url implements TagPlugin {
    
    public void doTag(TagPluginContext ctxt) {
        
        //标志
        boolean hasVar, hasContext, hasScope;
        
        //初始化标志
        hasVar = ctxt.isAttributeSpecified("var");
        hasContext = ctxt.isAttributeSpecified("context");
        hasScope = ctxt.isAttributeSpecified("scope");
        
        // 定义的临时变量的名字
        String valueName = ctxt.getTemporaryVariableName();
        String contextName = ctxt.getTemporaryVariableName();
        String baseUrlName = ctxt.getTemporaryVariableName();
        String resultName = ctxt.getTemporaryVariableName();
        String responseName = ctxt.getTemporaryVariableName();
        
        //获取范围
        String strScope = "page";
        if(hasScope){
            strScope = ctxt.getConstantAttribute("scope");
        }
        int iScope = Util.getScope(strScope);
        
        //获取值
        ctxt.generateJavaSource("String " + valueName + " = ");
        ctxt.generateAttribute("value");
        ctxt.generateJavaSource(";");
        
        //获取上下文
        ctxt.generateJavaSource("String " + contextName + " = null;");
        if(hasContext){
            ctxt.generateJavaSource(contextName + " = ");
            ctxt.generateAttribute("context");
            ctxt.generateJavaSource(";");
        }
        
        //获取原始 url
        ctxt.generateJavaSource("String " + baseUrlName + " = " +
                "org.apache.jasper.tagplugins.jstl.Util.resolveUrl(" + valueName + ", " + contextName + ", pageContext);");
        ctxt.generateJavaSource("pageContext.setAttribute" +
                "(\"url_without_param\", " + baseUrlName + ");");
        
        //添加参数
        ctxt.generateBody();
        
        ctxt.generateJavaSource("String " + resultName + " = " +
        "(String)pageContext.getAttribute(\"url_without_param\");");
        ctxt.generateJavaSource("pageContext.removeAttribute(\"url_without_param\");");
        
        //如果url 是相对的, 编码它
        ctxt.generateJavaSource("if(!org.apache.jasper.tagplugins.jstl.Util.isAbsoluteUrl(" + resultName + ")){");
        ctxt.generateJavaSource("    HttpServletResponse " + responseName + " = " +
        "((HttpServletResponse) pageContext.getResponse());");
        ctxt.generateJavaSource("    " + resultName + " = "
                + responseName + ".encodeURL(" + resultName + ");");
        ctxt.generateJavaSource("}");
        
        //如果指定了"var", url字符串保存到var定义的属性中
        if(hasVar){
            String strVar = ctxt.getConstantAttribute("var");
            ctxt.generateJavaSource("pageContext.setAttribute" +
                    "(\"" + strVar + "\", " + resultName + ", " + iScope + ");");
            
            //如果未指定var, 只要打印出URL字符串
        }else{
            ctxt.generateJavaSource("try{");
            ctxt.generateJavaSource("    pageContext.getOut().print(" + resultName + ");");
            ctxt.generateJavaSource("}catch(java.io.IOException ex){");
            ctxt.generateJavaSource("    throw new JspTagException(ex.toString(), ex);");
            ctxt.generateJavaSource("}");
        }
    }
}
