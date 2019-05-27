package org.apache.catalina.connector;

import java.io.IOException;

/**
 * 包装一个IOException ，确认它是由远程客户端请求中止引起的
 */
public final class ClientAbortException extends IOException {

    //------------------------------------------------------------ Constructors

    public ClientAbortException() {
        this(null, null);
    }


    /**
     * @param message Message describing this exception
     */
    public ClientAbortException(String message) {
        this(message, null);
    }


    /**
     * @param throwable Throwable that caused this exception
     */
    public ClientAbortException(Throwable throwable) {
        this(null, throwable);
    }


    /**
     * @param message Message describing this exception
     * @param throwable Throwable that caused this exception
     */
    public ClientAbortException(String message, Throwable throwable) {
        super();
        this.message = message;
        this.throwable = throwable;
    }


    //------------------------------------------------------ Instance Variables

    protected String message = null;

    protected Throwable throwable = null;

    //---------------------------------------------------------- Public Methods

    public String getMessage() {
        return (message);
    }


    public Throwable getCause() {
        return (throwable);
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer("ClientAbortException:  ");
        if (message != null) {
            sb.append(message);
            if (throwable != null) {
                sb.append(":  ");
            }
        }
        if (throwable != null) {
            sb.append(throwable.toString());
        }
        return (sb.toString());
    }
}
