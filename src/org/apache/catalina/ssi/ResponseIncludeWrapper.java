package org.apache.catalina.ssi;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.catalina.util.DateTool;

/**
 * A HttpServletResponseWrapper, used from <code>SSIServletExternalResolver</code>
 */
public class ResponseIncludeWrapper extends HttpServletResponseWrapper {
    /**
     * 希望捕获的一些header名称.
     */
    private static final String CONTENT_TYPE = "content-type";
    private static final String LAST_MODIFIED = "last-modified";
    protected long lastModified = -1;
    private String contentType = null;

    /**
     * Our ServletOutputStream
     */
    protected ServletOutputStream captureServletOutputStream;
    protected ServletOutputStream servletOutputStream;
    protected PrintWriter printWriter;
    
    private ServletContext context;
    private HttpServletRequest request;


    /**
     * @param context The servlet context
     * @param request The HttpServletResponse to use
     * @param response The response to use
     * @param captureServletOutputStream The ServletOutputStream to use
     */
    public ResponseIncludeWrapper(ServletContext context, 
    		HttpServletRequest request, HttpServletResponse response,
           ServletOutputStream captureServletOutputStream) {
        super(response);
        this.context = context;
        this.request = request;
        this.captureServletOutputStream = captureServletOutputStream;
    }


    /**
     * 刷新 servletOutputStream 或 printWriter (只有一个是非null )
     * 必须在requestDispatcher.include之后调用, 因为我们不能假设包含的servlet刷新了它的流.
     */
    public void flushOutputStreamOrWriter() throws IOException {
        if (servletOutputStream != null) {
            servletOutputStream.flush();
        }
        if (printWriter != null) {
            printWriter.flush();
        }
    }


    /**
     * 返回一个printwriter, 抛出异常如果一个OutputStream已经返回.
     * 
     * @return a PrintWriter object
     * @exception java.io.IOException if the outputstream already been called
     */
    public PrintWriter getWriter() throws java.io.IOException {
        if (servletOutputStream == null) {
            if (printWriter == null) {
                setCharacterEncoding(getCharacterEncoding());
                printWriter = new PrintWriter(
                        new OutputStreamWriter(captureServletOutputStream,
                                               getCharacterEncoding()));
            }
            return printWriter;
        }
        throw new IllegalStateException();
    }


    /**
     * 返回一个OutputStream, 抛出异常如果一个printwriter已经返回
     * 
     * @return a OutputStream object
     * @exception java.io.IOException if the printwriter already been called
     */
    public ServletOutputStream getOutputStream() throws java.io.IOException {
        if (printWriter == null) {
            if (servletOutputStream == null) {
                servletOutputStream = captureServletOutputStream;
            }
            return servletOutputStream;
        }
        throw new IllegalStateException();
    }
    
    
    /**
     * 返回<code>last-modified</code> header字段的值. 结果是自从January 1, 1970 GMT的毫秒数.
     *
     * @return 通过<code>ResponseIncludeWrapper</code>引用的资源的最后更新日期, 或者 -1 如果未知.
     */
    public long getLastModified() {                                                                                                                                                           
        if (lastModified == -1) {
            // javadocs say to return -1 if date not known, if you want another
            // default, put it here
            return -1;
        }
        return lastModified;
    }

    /**
     * 设置<code>last-modified</code> header字段的值.
     *
     * @param lastModified 自从January 1, 1970 GMT的毫秒数.
     */
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
        ((HttpServletResponse) getResponse()).setDateHeader(LAST_MODIFIED,
                lastModified);
    }

    /**
     * 返回<code>content-type</code> header字段的值.
     *
     * @return 通过<code>ResponseIncludeWrapper</code>引用的资源的内容类型, 或者 -1 如果未知.
     */
    public String getContentType() {
        if (contentType == null) {
            String url = request.getRequestURI();
            String mime = context.getMimeType(url);
            if (mime != null) {
                setContentType(mime);
            } else {
            	// return a safe value
               setContentType("application/x-octet-stream");
            }
        }
        return contentType;
    }
    
    /**
     * 设置<code>content-type</code> header字段的值.
     *
     * @param mime a mime type
     */
    public void setContentType(String mime) {
        contentType = mime;
        if (contentType != null) {
            getResponse().setContentType(contentType);
        }
    }


    public void addDateHeader(String name, long value) {
        super.addDateHeader(name, value);
        String lname = name.toLowerCase();
        if (lname.equals(LAST_MODIFIED)) {
            lastModified = value;
        }
    }

    public void addHeader(String name, String value) {
        super.addHeader(name, value);
        String lname = name.toLowerCase();
        if (lname.equals(LAST_MODIFIED)) {
            try {
                lastModified = DateTool.rfc1123Format.parse(value).getTime();
            } catch (Throwable ignore) { }
        } else if (lname.equals(CONTENT_TYPE)) {
            contentType = value;
        }
    }

    public void setDateHeader(String name, long value) {
        super.setDateHeader(name, value);
        String lname = name.toLowerCase();
        if (lname.equals(LAST_MODIFIED)) {
            lastModified = value;
        }
    }

    public void setHeader(String name, String value) {
        super.setHeader(name, value);
        String lname = name.toLowerCase();
        if (lname.equals(LAST_MODIFIED)) {
            try {
                lastModified = DateTool.rfc1123Format.parse(value).getTime();
            } catch (Throwable ignore) { }
        }
        else if (lname.equals(CONTENT_TYPE))
        {
            contentType = value;
        }
    }
}
