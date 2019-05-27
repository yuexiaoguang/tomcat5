package org.apache.catalina;


/**
 * 用于指示与生命周期相关的问题的通用异常. 
 * 这种异常通常被认为是致命的
 */
public final class LifecycleException extends Exception {

    //------------------------------------------------------------ Constructors

    public LifecycleException() {
        this(null, null);
    }


    public LifecycleException(String message) {
        this(message, null);
    }

    public LifecycleException(Throwable throwable) {
        this(null, throwable);
    }


    public LifecycleException(String message, Throwable throwable) {
        super();
        this.message = message;
        this.throwable = throwable;
    }

    //------------------------------------------------------ Instance Variables

    /**
     * 错误信息
     */
    protected String message = null;


    /**
     * 错误对象
     */
    protected Throwable throwable = null;


    //---------------------------------------------------------- Public Methods


    public String getMessage() {
        return (message);
    }


    public Throwable getThrowable() {
        return (throwable);
    }


    public String toString() {
        StringBuffer sb = new StringBuffer("LifecycleException:  ");
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
