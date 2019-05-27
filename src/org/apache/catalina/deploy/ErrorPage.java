package org.apache.catalina.deploy;

import org.apache.catalina.util.RequestUtil;
import java.io.Serializable;

/**
 * Web应用程序的错误页面元素的表示,在部署描述中使用<code>&lt;error-page&gt;</code>元素表示
 */
public class ErrorPage implements Serializable {

    // ----------------------------------------------------- Instance Variables

    /**
     * 此错误页处于活动状态的错误（状态）代码
     */
    private int errorCode = 0;


    /**
     * 此错误页面激活的异常类型.
     */
    private String exceptionType = null;


    /**
     * 处理此错误或异常的上下文相对位置.
     */
    private String location = null;


    // ------------------------------------------------------------- Properties


    /**
     * 返回错误代码
     */
    public int getErrorCode() {
        return (this.errorCode);
    }


    /**
     * 设置错误代码
     *
     * @param errorCode The new error code
     */
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }


    /**
     * 设置错误代码(默认的XmlMapper数据类型).
     *
     * @param errorCode The new error code
     */
    public void setErrorCode(String errorCode) {
        try {
            this.errorCode = Integer.parseInt(errorCode);
        } catch (Throwable t) {
            this.errorCode = 0;
        }
    }


    /**
     * 返回异常类型.
     */
    public String getExceptionType() {
        return (this.exceptionType);
    }


    /**
     * 设置异常类型.
     *
     * @param exceptionType The new exception type
     */
    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }


    /**
     * 返回位置.
     */
    public String getLocation() {
        return (this.location);
    }


    /**
     * 设置位置.
     *
     * @param location The new location
     */
    public void setLocation(String location) {
        //        if ((location == null) || !location.startsWith("/"))
        //            throw new IllegalArgumentException
        //                ("Error Page Location must start with a '/'");
        this.location = RequestUtil.URLDecode(location);
    }


    // --------------------------------------------------------- Public Methods


    public String toString() {
        StringBuffer sb = new StringBuffer("ErrorPage[");
        if (exceptionType == null) {
            sb.append("errorCode=");
            sb.append(errorCode);
        } else {
            sb.append("exceptionType=");
            sb.append(exceptionType);
        }
        sb.append(", location=");
        sb.append(location);
        sb.append("]");
        return (sb.toString());
    }
}
