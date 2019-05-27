package org.apache.catalina.ssi;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Globals;
/**
 * 过滤器来处理网页中的SSI请求. 映射web.xml中的内容类型.
 */
public class SSIFilter implements Filter {
	protected FilterConfig config = null;
    /** 这个servlet的调试等级. */
    protected int debug = 0;
    /** 到期时间, 秒. */
    protected Long expires = null;
    /** 虚拟路径, 可以是应用相对路径 */
    protected boolean isVirtualWebappRelative = false;
    /** 正则表达式模式匹配时，评估的内容类型 */
	protected Pattern contentTypeRegEx = null;
	/** SSI过滤内容类型匹配的默认模式 */
	protected Pattern shtmlRegEx =
        Pattern.compile("text/x-server-parsed-html(;.*)?");


    //----------------- Public methods.
    /**
     * 初始化这个 servlet.
     * 
     * @exception ServletException if an error occurs
     */
    public void init(FilterConfig config) throws ServletException {
    	this.config = config;
    	
        String value = null;
        try {
            value = config.getInitParameter("debug");
            debug = Integer.parseInt(value);
        } catch (Throwable t) {
            ;
        }
        try {
            value = config.getInitParameter("contentType");
            contentTypeRegEx = Pattern.compile(value);
        } catch (Throwable t) {
            contentTypeRegEx = shtmlRegEx;
            StringBuffer msg = new StringBuffer();
            msg.append("Invalid format or no contentType initParam; ");
            msg.append("expected regular expression; defaulting to ");
            msg.append(shtmlRegEx.pattern());
            config.getServletContext().log(msg.toString());
        }
        try {
            value = config.getInitParameter(
                    "isVirtualWebappRelative");
            isVirtualWebappRelative = Integer.parseInt(value) > 0?true:false;
        } catch (Throwable t) {
            ;
        }
        try {
            value = config.getInitParameter("expires");
            expires = Long.valueOf(value);
        } catch (NumberFormatException e) {
            expires = null;
            config.getServletContext().log(
                "Invalid format for expires initParam; expected integer (seconds)"
            );
        } catch (Throwable t) {
            ;
        }
        if (debug > 0)
            config.getServletContext().log(
                    "SSIFilter.init() SSI invoker started with 'debug'=" + debug);
    }

    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        // cast once
        HttpServletRequest req = (HttpServletRequest)request;
        HttpServletResponse res = (HttpServletResponse)response;
        
        // 表示正在处理SSI
        req.setAttribute(Globals.SSI_FLAG_ATTR, "true");           

        // 设置以捕获输出
        ByteArrayServletOutputStream basos = new ByteArrayServletOutputStream();
        ResponseIncludeWrapper responseIncludeWrapper =
            new ResponseIncludeWrapper(config.getServletContext(),req, res, basos);

        // 过滤器链
        chain.doFilter(req, responseIncludeWrapper);

        // 不能假定链会刷新它的输出流
        responseIncludeWrapper.flushOutputStreamOrWriter();
        byte[] bytes = basos.toByteArray();

        // get content type
        String contentType = responseIncludeWrapper.getContentType();

        // 这是SSI处理的允许类型吗?
        if (contentTypeRegEx.matcher(contentType).matches()) {
            String encoding = res.getCharacterEncoding();

            // set up SSI processing 
            SSIExternalResolver ssiExternalResolver =
                new SSIServletExternalResolver(config.getServletContext(), req,
                        res, isVirtualWebappRelative, debug, encoding);
            SSIProcessor ssiProcessor = new SSIProcessor(ssiExternalResolver,
                    debug);
            
            // prepare readers/writers
            Reader reader =
                new InputStreamReader(new ByteArrayInputStream(bytes), encoding);
            ByteArrayOutputStream ssiout = new ByteArrayOutputStream();
            PrintWriter writer =
                new PrintWriter(new OutputStreamWriter(ssiout, encoding));
            
            // do SSI processing  
            long lastModified = ssiProcessor.process(reader,
                    responseIncludeWrapper.getLastModified(), writer);
            
            // set output bytes
            writer.flush();
            bytes = ssiout.toByteArray();
            
            // override headers
            if (expires != null) {
                res.setDateHeader("expires", (new java.util.Date()).getTime()
                        + expires.longValue() * 1000);
            }
            if (lastModified > 0) {
                res.setDateHeader("last-modified", lastModified);
            }
            res.setContentLength(bytes.length);
            
            Matcher shtmlMatcher =
                shtmlRegEx.matcher(responseIncludeWrapper.getContentType());
            if (shtmlMatcher.matches()) {
            	// 转换一个MIME类型为普通HTML MIME类型，但保留编码
            	String enc = shtmlMatcher.group(1);
            	res.setContentType("text/html" + ((enc != null) ? enc : ""));
            }
        }

        // write output
        try {
            OutputStream out = res.getOutputStream();
            out.write(bytes);
        } catch (Throwable t) {
            Writer out = res.getWriter();
            out.write(new String(bytes));
        }
    }

    public void destroy() {
    }
}
