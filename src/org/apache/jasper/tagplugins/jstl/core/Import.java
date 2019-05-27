package org.apache.jasper.tagplugins.jstl.core;

import org.apache.jasper.compiler.tagplugin.TagPlugin;
import org.apache.jasper.compiler.tagplugin.TagPluginContext;
import org.apache.jasper.tagplugins.jstl.Util;

public class Import implements TagPlugin {
    
    public void doTag(TagPluginContext ctxt) {
        boolean hasContext, hasVar, hasScope, hasVarReader, hasCharEncoding;
        
        //标志
        hasContext  = ctxt.isAttributeSpecified("context");
        hasVar = ctxt.isAttributeSpecified("var");
        hasScope = ctxt.isAttributeSpecified("scope");
        hasVarReader = ctxt.isAttributeSpecified("varReader");
        hasCharEncoding = ctxt.isAttributeSpecified("charEncoding");
        
        //变量的名字
        String urlName = ctxt.getTemporaryVariableName();           
        String contextName = ctxt.getTemporaryVariableName();       
        String iauName = ctxt.getTemporaryVariableName();           // is absolute url
        String urlObjName = ctxt.getTemporaryVariableName();        //URL object
        String ucName = ctxt.getTemporaryVariableName();            //URLConnection
        String inputStreamName = ctxt.getTemporaryVariableName();   
        String tempReaderName = ctxt.getTemporaryVariableName();
        String tempReaderName2 = ctxt.getTemporaryVariableName();
        String charSetName = ctxt.getTemporaryVariableName();
        String charEncodingName = ctxt.getTemporaryVariableName();
        String contentTypeName = ctxt.getTemporaryVariableName();
        String varReaderName = ctxt.getTemporaryVariableName();
        String servletContextName = ctxt.getTemporaryVariableName();
        String servletPathName = ctxt.getTemporaryVariableName();
        String requestDispatcherName = ctxt.getTemporaryVariableName();
        String irwName = ctxt.getTemporaryVariableName();           //ImportResponseWrapper name
        String brName = ctxt.getTemporaryVariableName();            //BufferedReader name
        String sbName = ctxt.getTemporaryVariableName();            //StringBuffer name
        String tempStringName = ctxt.getTemporaryVariableName();
        
        //是绝对url
        ctxt.generateJavaSource("boolean " + iauName + ";");
        
        //获取url 值
        ctxt.generateJavaSource("String " + urlName + " = ");
        ctxt.generateAttribute("url");
        ctxt.generateJavaSource(";");
        
        //验证 url
        ctxt.generateJavaSource("if(" + urlName + " == null || " + urlName + ".equals(\"\")){");
        ctxt.generateJavaSource("    throw new JspTagException(\"The \\\"url\\\" attribute " +
        "illegally evaluated to \\\"null\\\" or \\\"\\\" in &lt;import&gt;\");");
        ctxt.generateJavaSource("}");
        
        //初始化 is_absolute_url
        ctxt.generateJavaSource(iauName + " = " +
                "org.apache.jasper.tagplugins.jstl.Util.isAbsoluteUrl(" + urlName + ");");
        
        //验证上下文
        if(hasContext){
            
            ctxt.generateJavaSource("String " + contextName + " = ");
            ctxt.generateAttribute("context");
            ctxt.generateJavaSource(";");
            
            ctxt.generateJavaSource("if((!" + contextName + ".startsWith(\"/\")) " +
                    "|| (!" + urlName + ".startsWith(\"/\"))){");
            ctxt.generateJavaSource("    throw new JspTagException" +
                    "(\"In URL tags, when the \\\"context\\\" attribute is specified, " +
            "values of both \\\"context\\\" and \\\"url\\\" must start with \\\"/\\\".\");");
            ctxt.generateJavaSource("}");
            
        }
        
        //定义字符集
        ctxt.generateJavaSource("String " + charSetName + " = null;");
        
        //如果指定了charEncoding属性
        if(hasCharEncoding){
            
            //初始化 charEncoding
            ctxt.generateJavaSource("String " + charEncodingName + " = ");
            ctxt.generateAttribute("charEncoding");
            ctxt.generateJavaSource(";");
            
            //指定适当的字符集
            ctxt.generateJavaSource("if(null != " + charEncodingName + " " +
                    "&& !" + charEncodingName + ".equals(\"\")){");
            ctxt.generateJavaSource("    " + charSetName + " = " 
                    + charEncodingName + ";");
            ctxt.generateJavaSource("}");
        }
        
        //重塑URL字符串
        ctxt.generateJavaSource("if(!"+iauName+"){");
        ctxt.generateJavaSource("    if(!" + urlName + ".startsWith(\"/\")){");
        ctxt.generateJavaSource("        String " + servletPathName + " = " +
        "((HttpServletRequest)pageContext.getRequest()).getServletPath();");
        ctxt.generateJavaSource("        " + urlName + " = " 
                + servletPathName + ".substring(0," + servletPathName + ".lastIndexOf('/')) + '/' + " + urlName + ";");
        ctxt.generateJavaSource("    }");
        ctxt.generateJavaSource("}");
        
        //如果指定了varReader属性
        if(hasVarReader){
            
            //获取varReader的字符串值
            ctxt.generateJavaSource("String " + varReaderName + " = ");
            ctxt.generateAttribute("varReader");
            ctxt.generateJavaSource(";");
            
            //如果url 是绝对url
            ctxt.generateJavaSource("if(" + iauName + "){");
            
            //获取目标内容
            ctxt.generateJavaSource("    java.net.URL " + urlObjName + " = new java.net.URL(" + urlName + ");");
            ctxt.generateJavaSource("    java.net.URLConnection " + ucName + " = " 
                    + urlObjName + ".openConnection();");
            ctxt.generateJavaSource("    java.io.InputStream " + inputStreamName + " = " 
                    + ucName + ".getInputStream();");
            
            ctxt.generateJavaSource("    if(" + charSetName + " == null){");
            ctxt.generateJavaSource("        String " + contentTypeName + " = " 
                    + ucName + ".getContentType();");
            ctxt.generateJavaSource("        if(null != " + contentTypeName + "){");
            ctxt.generateJavaSource("            " + charSetName + " = " +
                    "org.apache.jasper.tagplugins.jstl.Util.getContentTypeAttribute(" + contentTypeName + ", \"charset\");");
            ctxt.generateJavaSource("            if(" + charSetName + " == null) " 
                    + charSetName + " = org.apache.jasper.tagplugins.jstl.Util.DEFAULT_ENCODING;");
            ctxt.generateJavaSource("        }else{");
            ctxt.generateJavaSource("            " + charSetName + " = org.apache.jasper.tagplugins.jstl.Util.DEFAULT_ENCODING;");
            ctxt.generateJavaSource("        }");
            ctxt.generateJavaSource("    }");
            
            if(!hasCharEncoding){
                ctxt.generateJavaSource("    String " + contentTypeName + " = " + ucName + ".getContentType();");
            }
            
            //定义 Reader
            ctxt.generateJavaSource("    java.io.Reader " + tempReaderName + " = null;");
            
            //初始化 Reader 对象
            ctxt.generateJavaSource("    try{");
            ctxt.generateJavaSource("        " + tempReaderName + " = new java.io.InputStreamReader(" + inputStreamName + ", " + charSetName + ");");
            ctxt.generateJavaSource("    }catch(Exception ex){");
            ctxt.generateJavaSource("        " + tempReaderName + " = new java.io.InputStreamReader(" + inputStreamName + ", org.apache.jasper.tagplugins.jstl.Util.DEFAULT_ENCODING);");
            ctxt.generateJavaSource("    }");
            
            //验证响应
            ctxt.generateJavaSource("    if(" + ucName + " instanceof java.net.HttpURLConnection){");
            ctxt.generateJavaSource("        int status = ((java.net.HttpURLConnection) " + ucName + ").getResponseCode();");
            ctxt.generateJavaSource("        if(status < 200 || status > 299){");
            ctxt.generateJavaSource("            throw new JspTagException(status + \" \" + " + urlName + ");");
            ctxt.generateJavaSource("        }");
            ctxt.generateJavaSource("    }");
            
            //在页面上下文范围中设置属性
            ctxt.generateJavaSource("    pageContext.setAttribute(" + varReaderName + ", " + tempReaderName + ");");
            
            //如果 url 是相对的
            ctxt.generateJavaSource("}else{");
            
            //如果 url 是相对的, 需要http 请求
            ctxt.generateJavaSource("    if (!(pageContext.getRequest() instanceof HttpServletRequest  " +
            "&& pageContext.getResponse() instanceof HttpServletResponse)){");
            ctxt.generateJavaSource("        throw new JspTagException(\"Relative &lt;import&gt; from non-HTTP request not allowed\");");
            ctxt.generateJavaSource("    }");
            
            //获取上下文属性中定义的上下文的servlet上下文
            ctxt.generateJavaSource("    ServletContext " + servletContextName + " = null;");
            if(hasContext){
                ctxt.generateJavaSource("    if(null != " + contextName + "){");
                ctxt.generateJavaSource("        " + servletContextName + " = pageContext.getServletContext().getContext(" + contextName + ");" );
                ctxt.generateJavaSource("    }else{");
                ctxt.generateJavaSource("        " + servletContextName + " = pageContext.getServletContext();");
                ctxt.generateJavaSource("    }");
            }else{
                ctxt.generateJavaSource("    " + servletContextName + " = pageContext.getServletContext();");
            }
            
            //
            ctxt.generateJavaSource("    if(" + servletContextName + " == null){");
            if(hasContext){
                ctxt.generateJavaSource("        throw new JspTagException(\"Unable to get RequestDispatcher for Context: \\\" \"+" + contextName + "+\" \\\" and URL: \\\" \" +" + urlName + "+ \" \\\". Verify values and/or enable cross context access.\");");
            }else{
                ctxt.generateJavaSource("        throw new JspTagException(\"Unable to get RequestDispatcher for  URL: \\\" \" +" + urlName + "+ \" \\\". Verify values and/or enable cross context access.\");");
            }
            ctxt.generateJavaSource("    }");
            
            //获取请求分配器
            ctxt.generateJavaSource("    RequestDispatcher " + requestDispatcherName + " = " + servletContextName + ".getRequestDispatcher(org.apache.jasper.tagplugins.jstl.Util.stripSession("+urlName+"));");
            ctxt.generateJavaSource("    if(" + requestDispatcherName + " == null) throw new JspTagException(org.apache.jasper.tagplugins.jstl.Util.stripSession("+urlName+"));");
            
            //初始化一个ImportResponseWrapper 来包含资源
            ctxt.generateJavaSource("    org.apache.jasper.tagplugins.jstl.Util.ImportResponseWrapper " + irwName + " = new org.apache.jasper.tagplugins.jstl.Util.ImportResponseWrapper((HttpServletResponse) pageContext.getResponse());");
            ctxt.generateJavaSource("    if(" + charSetName + " == null){");
            ctxt.generateJavaSource("        " + charSetName + " = org.apache.jasper.tagplugins.jstl.Util.DEFAULT_ENCODING;");
            ctxt.generateJavaSource("    }");
            ctxt.generateJavaSource("    " + irwName + ".setCharEncoding(" + charSetName + ");");
            ctxt.generateJavaSource("    try{");
            ctxt.generateJavaSource("        " + requestDispatcherName + ".include(pageContext.getRequest(), " + irwName + ");");
            ctxt.generateJavaSource("    }catch(java.io.IOException ex){");
            ctxt.generateJavaSource("        throw new JspException(ex);");
            ctxt.generateJavaSource("    }catch(RuntimeException ex){");
            ctxt.generateJavaSource("        throw new JspException(ex);");
            ctxt.generateJavaSource("    }catch(ServletException ex){");
            ctxt.generateJavaSource("        Throwable rc = ex.getRootCause();");
            ctxt.generateJavaSource("        if (rc == null)");
            ctxt.generateJavaSource("            throw new JspException(ex);");
            ctxt.generateJavaSource("        else");
            ctxt.generateJavaSource("            throw new JspException(rc);");
            ctxt.generateJavaSource("    }");
            
            //验证响应状态
            ctxt.generateJavaSource("    if(" + irwName + ".getStatus() < 200 || " + irwName + ".getStatus() > 299){");
            ctxt.generateJavaSource("        throw new JspTagException(" + irwName + ".getStatus()+\" \" + org.apache.jasper.tagplugins.jstl.Util.stripSession(" + urlName + "));");
            ctxt.generateJavaSource("    }");
            
            //推入页面上下文
            ctxt.generateJavaSource("    java.io.Reader " + tempReaderName + " = new java.io.StringReader(" + irwName + ".getString());");
            ctxt.generateJavaSource("    pageContext.setAttribute(" + varReaderName + ", " + tempReaderName + ");");
            
            ctxt.generateJavaSource("}");
            
            //执行主体动作
            ctxt.generateBody();
            
            //关闭 reader
            ctxt.generateJavaSource("java.io.Reader " + tempReaderName2 + " = (java.io.Reader)pageContext.getAttribute(" + varReaderName + ");");
            ctxt.generateJavaSource("if(" + tempReaderName2 + " != null) " + tempReaderName2 + ".close();");
            ctxt.generateJavaSource("pageContext.removeAttribute(" + varReaderName + ",1);");
        }
        
        //如果未指定 varReader
        else{
            
            ctxt.generateJavaSource("pageContext.setAttribute(\"url_without_param\"," + urlName + ");");
            ctxt.generateBody();
            ctxt.generateJavaSource(urlName + " = (String)pageContext.getAttribute(\"url_without_param\");");
            ctxt.generateJavaSource("pageContext.removeAttribute(\"url_without_param\");");
            String strScope = "page";
            if(hasScope){
                strScope = ctxt.getConstantAttribute("scope");
            }
            int iScope = Util.getScope(strScope);
            
            ctxt.generateJavaSource("String " + tempStringName + " = null;");
            
            ctxt.generateJavaSource("if(" + iauName + "){");
            
            //获取目标内容
            ctxt.generateJavaSource("    java.net.URL " + urlObjName + " = new java.net.URL(" + urlName + ");");
            ctxt.generateJavaSource("    java.net.URLConnection " + ucName + " = " + urlObjName + ".openConnection();");
            ctxt.generateJavaSource("    java.io.InputStream " + inputStreamName + " = " + ucName + ".getInputStream();");
            ctxt.generateJavaSource("    java.io.Reader " + tempReaderName + " = null;");
            
            ctxt.generateJavaSource("    if(" + charSetName + " == null){");
            ctxt.generateJavaSource("        String " + contentTypeName + " = " 
                    + ucName + ".getContentType();");
            ctxt.generateJavaSource("        if(null != " + contentTypeName + "){");
            ctxt.generateJavaSource("            " + charSetName + " = " +
                    "org.apache.jasper.tagplugins.jstl.Util.getContentTypeAttribute(" + contentTypeName + ", \"charset\");");
            ctxt.generateJavaSource("            if(" + charSetName + " == null) " 
                    + charSetName + " = org.apache.jasper.tagplugins.jstl.Util.DEFAULT_ENCODING;");
            ctxt.generateJavaSource("        }else{");
            ctxt.generateJavaSource("            " + charSetName + " = org.apache.jasper.tagplugins.jstl.Util.DEFAULT_ENCODING;");
            ctxt.generateJavaSource("        }");
            ctxt.generateJavaSource("    }");
            
            ctxt.generateJavaSource("    try{");
            ctxt.generateJavaSource("        " + tempReaderName + " = new java.io.InputStreamReader(" + inputStreamName + "," + charSetName + ");");
            ctxt.generateJavaSource("    }catch(Exception ex){");
            //ctxt.generateJavaSource("        throw new JspTagException(ex.toString());");
            ctxt.generateJavaSource("        " + tempReaderName + " = new java.io.InputStreamReader(" + inputStreamName + ",org.apache.jasper.tagplugins.jstl.Util.DEFAULT_ENCODING);");
            ctxt.generateJavaSource("    }");
            
            //验证响应
            ctxt.generateJavaSource("    if(" + ucName + " instanceof java.net.HttpURLConnection){");
            ctxt.generateJavaSource("        int status = ((java.net.HttpURLConnection) " + ucName + ").getResponseCode();");
            ctxt.generateJavaSource("        if(status < 200 || status > 299){");
            ctxt.generateJavaSource("            throw new JspTagException(status + \" \" + " + urlName + ");");
            ctxt.generateJavaSource("        }");
            ctxt.generateJavaSource("    }");
            
            ctxt.generateJavaSource("    java.io.BufferedReader " + brName + " =  new java.io.BufferedReader(" + tempReaderName + ");");
            ctxt.generateJavaSource("    StringBuffer " + sbName + " = new StringBuffer();");
            String index = ctxt.getTemporaryVariableName();
            ctxt.generateJavaSource("    int " + index + ";");
            ctxt.generateJavaSource("    while(("+index+" = "+brName+".read()) != -1) "+sbName+".append((char)"+index+");");
            ctxt.generateJavaSource("    " + tempStringName + " = " +sbName + ".toString();");
            
            ctxt.generateJavaSource("}else{");
            
            //如果url 是相对的, 需要http 请求.
            ctxt.generateJavaSource("    if (!(pageContext.getRequest() instanceof HttpServletRequest  " +
            "&& pageContext.getResponse() instanceof HttpServletResponse)){");
            ctxt.generateJavaSource("        throw new JspTagException(\"Relative &lt;import&gt; from non-HTTP request not allowed\");");
            ctxt.generateJavaSource("    }");
            
            //获取上下文属性中定义的上下文的servlet上下文
            ctxt.generateJavaSource("    ServletContext " + servletContextName + " = null;");
            if(hasContext){
                ctxt.generateJavaSource("    if(null != " + contextName + "){");
                ctxt.generateJavaSource("        " + servletContextName + " = pageContext.getServletContext().getContext(" + contextName + ");" );
                ctxt.generateJavaSource("    }else{");
                ctxt.generateJavaSource("        " + servletContextName + " = pageContext.getServletContext();");
                ctxt.generateJavaSource("    }");
            }else{
                ctxt.generateJavaSource("    " + servletContextName + " = pageContext.getServletContext();");
            }
            
            //
            ctxt.generateJavaSource("    if(" + servletContextName + " == null){");
            if(hasContext){
                ctxt.generateJavaSource("        throw new JspTagException(\"Unable to get RequestDispatcher for Context: \\\" \" +" + contextName + "+ \" \\\" and URL: \\\" \" +" + urlName + "+ \" \\\". Verify values and/or enable cross context access.\");");
            }else{
                ctxt.generateJavaSource("        throw new JspTagException(\"Unable to get RequestDispatcher for URL: \\\" \" +" + urlName + "+ \" \\\". Verify values and/or enable cross context access.\");");
            }
            ctxt.generateJavaSource("    }");
            
            //获取请求分配器
            ctxt.generateJavaSource("    RequestDispatcher " + requestDispatcherName + " = " + servletContextName + ".getRequestDispatcher(org.apache.jasper.tagplugins.jstl.Util.stripSession("+urlName+"));");
            ctxt.generateJavaSource("    if(" + requestDispatcherName + " == null) throw new JspTagException(org.apache.jasper.tagplugins.jstl.Util.stripSession("+urlName+"));");
            
            //初始化一个 ImportResponseWrapper 来包含资源
            ctxt.generateJavaSource("    org.apache.jasper.tagplugins.jstl.Util.ImportResponseWrapper " + irwName + " = new org.apache.jasper.tagplugins.jstl.Util.ImportResponseWrapper((HttpServletResponse) pageContext.getResponse());");
            ctxt.generateJavaSource("    if(" + charSetName + " == null){");
            ctxt.generateJavaSource("        " + charSetName + " = org.apache.jasper.tagplugins.jstl.Util.DEFAULT_ENCODING;");
            ctxt.generateJavaSource("    }");
            ctxt.generateJavaSource("    " + irwName + ".setCharEncoding(" + charSetName + ");");
            ctxt.generateJavaSource("    try{");
            ctxt.generateJavaSource("        " + requestDispatcherName + ".include(pageContext.getRequest(), " + irwName + ");");
            ctxt.generateJavaSource("    }catch(java.io.IOException ex){");
            ctxt.generateJavaSource("        throw new JspException(ex);");
            ctxt.generateJavaSource("    }catch(RuntimeException ex){");
            ctxt.generateJavaSource("        throw new JspException(ex);");
            ctxt.generateJavaSource("    }catch(ServletException ex){");
            ctxt.generateJavaSource("        Throwable rc = ex.getRootCause();");
            ctxt.generateJavaSource("        if (rc == null)");
            ctxt.generateJavaSource("            throw new JspException(ex);");
            ctxt.generateJavaSource("        else");
            ctxt.generateJavaSource("            throw new JspException(rc);");
            ctxt.generateJavaSource("    }");
            
            //验证响应状态
            ctxt.generateJavaSource("    if(" + irwName + ".getStatus() < 200 || " + irwName + ".getStatus() > 299){");
            ctxt.generateJavaSource("        throw new JspTagException(" + irwName + ".getStatus()+\" \" + org.apache.jasper.tagplugins.jstl.Util.stripSession(" + urlName + "));");
            ctxt.generateJavaSource("    }");
            
            ctxt.generateJavaSource("    " + tempStringName + " = " + irwName + ".getString();");
            
            ctxt.generateJavaSource("}");
            
            if(hasVar){
                String strVar = ctxt.getConstantAttribute("var");
                ctxt.generateJavaSource("pageContext.setAttribute(\""+strVar+"\"," + tempStringName + "," + iScope + ");");
            }else{
                ctxt.generateJavaSource("pageContext.getOut().print(" + tempStringName + ");");
            }
        }
    }
}
