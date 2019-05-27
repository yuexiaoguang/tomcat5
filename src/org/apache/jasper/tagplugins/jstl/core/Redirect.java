package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;

public class Redirect implements TagPlugin {
    
    public void doTag(TagPluginContext ctxt) {
        
        //"context"是否存在
        boolean hasContext = ctxt.isAttributeSpecified("context");
        
        //临时变量的名称
        String urlName = ctxt.getTemporaryVariableName();
        String contextName = ctxt.getTemporaryVariableName();
        String baseUrlName = ctxt.getTemporaryVariableName();
        String resultName = ctxt.getTemporaryVariableName();
        String responseName = ctxt.getTemporaryVariableName();
        
        //获取上下文
        ctxt.generateJavaSource("String " + contextName + " = null;");
        if(hasContext){
            ctxt.generateJavaSource(contextName + " = ");
            ctxt.generateAttribute("context");
            ctxt.generateJavaSource(";");
        }
        
        //获取url
        ctxt.generateJavaSource("String " + urlName + " = ");
        ctxt.generateAttribute("url");
        ctxt.generateJavaSource(";");
        
        //获取原始 url ，根据"url" 和 "context"
        ctxt.generateJavaSource("String " + baseUrlName + " = " +
                "org.apache.jasper.tagplugins.jstl.Util.resolveUrl(" + urlName + ", " + contextName + ", pageContext);");
        ctxt.generateJavaSource("pageContext.setAttribute" +
                "(\"url_without_param\", " + baseUrlName + ");");
        
        //添加参数
        ctxt.generateBody();
        
        ctxt.generateJavaSource("String " + resultName + " = " +
        "(String)pageContext.getAttribute(\"url_without_param\");");
        ctxt.generateJavaSource("pageContext.removeAttribute" +
        "(\"url_without_param\");");
        
        //获取响应对象
        ctxt.generateJavaSource("HttpServletResponse " + responseName + " = " +
        "((HttpServletResponse) pageContext.getResponse());");
        
        //如果URL是相对的, 编码它
        ctxt.generateJavaSource("if(!org.apache.jasper.tagplugins.jstl.Util.isAbsoluteUrl(" + resultName + ")){");
        ctxt.generateJavaSource("    " + resultName + " = "
                + responseName + ".encodeRedirectURL(" + resultName + ");");
        ctxt.generateJavaSource("}");
        
        //do redirect
        ctxt.generateJavaSource("try{");
        ctxt.generateJavaSource("    " + responseName + ".sendRedirect(" + resultName + ");");
        ctxt.generateJavaSource("}catch(java.io.IOException ex){");
        ctxt.generateJavaSource("    throw new JspTagException(ex.toString(), ex);");
        ctxt.generateJavaSource("}");
    }
    
}
