package org.apache.catalina.connector;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collection;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.util.StringManager;
import org.apache.catalina.security.SecurityUtil;

/**
 * 外观类包装Catalina 内部的<b>Response</b>对象.  所有方法都委托给包装响应.
 */
public class ResponseFacade implements HttpServletResponse {

    // ----------------------------------------------------------- DoPrivileged
    
    private final class SetContentTypePrivilegedAction
            implements PrivilegedAction {

        private String contentType;

        public SetContentTypePrivilegedAction(String contentType){
            this.contentType = contentType;
        }
        
        public Object run() {
            response.setContentType(contentType);
            return null;
        }            
    }

    private final class DateHeaderPrivilegedAction
            implements PrivilegedAction {

        private String name;
        private long value;
        private boolean add;

        DateHeaderPrivilegedAction(String name, long value, boolean add) {
            this.name = name;
            this.value = value;
            this.add = add;
        }

        public Object run() {
            if(add) {
                response.addDateHeader(name, value);
            } else {
                response.setDateHeader(name, value);
            }
            return null;
        }
    }
    
    // ----------------------------------------------------------- Constructors


    /**
     * @param response 包装的响应
     */
    public ResponseFacade(Response response) {
         this.response = response;
    }


    // ----------------------------------------------- Class/Instance Variables


    /**
     * The string manager for this package.
     */
    protected static StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 包装的响应.
     */
    protected Response response = null;


    // --------------------------------------------------------- Public Methods


    /**
     * 清空.
     */
    public void clear() {
        response = null;
    }


    /**
     * 防止克隆.
     */
    protected Object clone()
        throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }


    public void finish() {
        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("responseFacade.nullResponse"));
        }

        response.setSuspended(true);
    }


    public boolean isFinished() {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("responseFacade.nullResponse"));
        }

        return response.isSuspended();
    }


    // ------------------------------------------------ ServletResponse Methods


    public String getCharacterEncoding() {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("responseFacade.nullResponse"));
        }

        return response.getCharacterEncoding();
    }


    public ServletOutputStream getOutputStream()
        throws IOException {

        //        if (isFinished())
        //            throw new IllegalStateException
        //                (/*sm.getString("responseFacade.finished")*/);

        ServletOutputStream sos = response.getOutputStream();
        if (isFinished())
            response.setSuspended(true);
        return (sos);

    }


    public PrintWriter getWriter()
        throws IOException {

        //        if (isFinished())
        //            throw new IllegalStateException
        //                (/*sm.getString("responseFacade.finished")*/);

        PrintWriter writer = response.getWriter();
        if (isFinished())
            response.setSuspended(true);
        return (writer);

    }


    public void setContentLength(int len) {

        if (isCommitted())
            return;

        response.setContentLength(len);

    }


    public void setContentType(String type) {

        if (isCommitted())
            return;
        
        if (SecurityUtil.isPackageProtectionEnabled()){
            AccessController.doPrivileged(new SetContentTypePrivilegedAction(type));
        } else {
            response.setContentType(type);            
        }
    }


    public void setBufferSize(int size) {

        if (isCommitted())
            throw new IllegalStateException
                (/*sm.getString("responseBase.reset.ise")*/);

        response.setBufferSize(size);

    }


    public int getBufferSize() {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("responseFacade.nullResponse"));
        }

        return response.getBufferSize();
    }


    public void flushBuffer()
        throws IOException {

        if (isFinished())
            //            throw new IllegalStateException
            //                (/*sm.getString("responseFacade.finished")*/);
            return;

        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                AccessController.doPrivileged(new PrivilegedExceptionAction(){

                    public Object run() throws IOException{
                        response.setAppCommitted(true);

                        response.flushBuffer();
                        return null;
                    }
                });
            } catch(PrivilegedActionException e){
                Exception ex = e.getException();
                if (ex instanceof IOException){
                    throw (IOException)ex;
                }
            }
        } else {
            response.setAppCommitted(true);

            response.flushBuffer();            
        }

    }


    public void resetBuffer() {

        if (isCommitted())
            throw new IllegalStateException
                (/*sm.getString("responseBase.reset.ise")*/);

        response.resetBuffer();
    }


    public boolean isCommitted() {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("responseFacade.nullResponse"));
        }

        return (response.isAppCommitted());
    }


    public void reset() {

        if (isCommitted())
            throw new IllegalStateException
                (/*sm.getString("responseBase.reset.ise")*/);

        response.reset();
    }


    public void setLocale(Locale loc) {

        if (isCommitted())
            return;

        response.setLocale(loc);
    }


    public Locale getLocale() {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("responseFacade.nullResponse"));
        }

        return response.getLocale();
    }


    public void addCookie(Cookie cookie) {

        if (isCommitted())
            return;

        response.addCookie(cookie);

    }


    public boolean containsHeader(String name) {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("responseFacade.nullResponse"));
        }

        return response.containsHeader(name);
    }


    public String encodeURL(String url) {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("responseFacade.nullResponse"));
        }

        return response.encodeURL(url);
    }


    public String encodeRedirectURL(String url) {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("responseFacade.nullResponse"));
        }

        return response.encodeRedirectURL(url);
    }


    public String encodeUrl(String url) {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("responseFacade.nullResponse"));
        }

        return response.encodeURL(url);
    }


    public String encodeRedirectUrl(String url) {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("responseFacade.nullResponse"));
        }

        return response.encodeRedirectURL(url);
    }


    public void sendError(int sc, String msg)
        throws IOException {

        if (isCommitted())
            throw new IllegalStateException
                (/*sm.getString("responseBase.reset.ise")*/);

        response.setAppCommitted(true);

        response.sendError(sc, msg);

    }


    public void sendError(int sc)
        throws IOException {

        if (isCommitted())
            throw new IllegalStateException
                (/*sm.getString("responseBase.reset.ise")*/);

        response.setAppCommitted(true);

        response.sendError(sc);

    }


    public void sendRedirect(String location)
        throws IOException {

        if (isCommitted())
            throw new IllegalStateException
                (/*sm.getString("responseBase.reset.ise")*/);

        response.setAppCommitted(true);

        response.sendRedirect(location);

    }


    public void setDateHeader(String name, long date) {

        if (isCommitted())
            return;

        if(System.getSecurityManager() != null) {
            AccessController.doPrivileged(new DateHeaderPrivilegedAction
                                             (name, date, false));
        } else {
            response.setDateHeader(name, date);
        }

    }


    public void addDateHeader(String name, long date) {

        if (isCommitted())
            return;

        if(System.getSecurityManager() != null) {
            AccessController.doPrivileged(new DateHeaderPrivilegedAction
                                             (name, date, true));
        } else {
            response.addDateHeader(name, date);
        }

    }


    public void setHeader(String name, String value) {

        if (isCommitted())
            return;

        response.setHeader(name, value);

    }


    public void addHeader(String name, String value) {

        if (isCommitted())
            return;

        response.addHeader(name, value);

    }


    public void setIntHeader(String name, int value) {

        if (isCommitted())
            return;

        response.setIntHeader(name, value);

    }


    public void addIntHeader(String name, int value) {

        if (isCommitted())
            return;

        response.addIntHeader(name, value);
    }


    public void setStatus(int sc) {

        if (isCommitted())
            return;

        response.setStatus(sc);
    }


    public void setStatus(int sc, String sm) {

        if (isCommitted())
            return;

        response.setStatus(sc, sm);
    }


    public String getContentType() {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("responseFacade.nullResponse"));
        }

        return response.getContentType();
    }


    public void setCharacterEncoding(String arg0) {

        if (response == null) {
            throw new IllegalStateException(
                            sm.getString("responseFacade.nullResponse"));
        }

        response.setCharacterEncoding(arg0);
    }

/*************************自己加的，解决报错问题****************************/
	@Override
	public String getHeader(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Collection<String> getHeaderNames() {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Collection<String> getHeaders(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public int getStatus() {
		// TODO Auto-generated method stub
		return 0;
	}
/*************************自己加的，解决报错问题****************************/
}
