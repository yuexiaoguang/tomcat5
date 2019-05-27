package org.apache.catalina.deploy;

import org.apache.catalina.util.RequestUtil;
import java.io.Serializable;

/**
 * Web应用程序过滤器映射的表示, 在部署描述中使用<code>&lt;filter-mapping&gt;</code>元素表示.
 * 每个过滤器映射必须包含过滤器名称，再加上URL模式或servlet名称.
 */
public class FilterMap implements Serializable {

    // ------------------------------------------------------------- Properties

    /**
     * 此映射匹配特定请求时要执行的过滤器的名称.
     */
    public static final int ERROR = 1;
    public static final int FORWARD = 2;
    public static final int FORWARD_ERROR =3;  
    public static final int INCLUDE = 4;
    public static final int INCLUDE_ERROR  = 5;
    public static final int INCLUDE_ERROR_FORWARD  =6;
    public static final int INCLUDE_FORWARD  = 7;
    public static final int REQUEST = 8;
    public static final int REQUEST_ERROR = 9;
    public static final int REQUEST_ERROR_FORWARD = 10;
    public static final int REQUEST_ERROR_FORWARD_INCLUDE = 11;
    public static final int REQUEST_ERROR_INCLUDE = 12;
    public static final int REQUEST_FORWARD = 13;
    public static final int REQUEST_INCLUDE = 14;
    public static final int REQUEST_FORWARD_INCLUDE= 15;
    
    // 表示未设置任何内容. 等同于一个 REQUEST
    private static final int NOT_SET = -1;
    
    private int dispatcherMapping=NOT_SET;
    
    private String filterName = null;    

    public String getFilterName() {
        return (this.filterName);
    }

    public void setFilterName(String filterName) {
        this.filterName = filterName;
    }


    /**
     * 映射匹配的servlet名称
     */
    private String servletName = null;

    public String getServletName() {
        return (this.servletName);
    }

    public void setServletName(String servletName) {
        this.servletName = servletName;
    }


    /**
     * 映射匹配的URL模式.
     */
    private String urlPattern = null;

    public String getURLPattern() {
        return (this.urlPattern);
    }

    public void setURLPattern(String urlPattern) {
        this.urlPattern = RequestUtil.URLDecode(urlPattern);
    }
    
    /**
     * 该方法将用于设置FilterMap的当前状态, 表示应用过滤器时的状态:
     *
     *        ERROR
     *        FORWARD
     *        FORWARD_ERROR
     *        INCLUDE
     *        INCLUDE_ERROR
     *        INCLUDE_ERROR_FORWARD
     *        REQUEST
     *        REQUEST_ERROR
     *        REQUEST_ERROR_INCLUDE
     *        REQUEST_ERROR_FORWARD_INCLUDE
     *        REQUEST_INCLUDE
     *        REQUEST_FORWARD,
     *        REQUEST_FORWARD_INCLUDE
     *
     */
    public void setDispatcher(String dispatcherString) {
        String dispatcher = dispatcherString.toUpperCase();
        
        if (dispatcher.equals("FORWARD")) {

            // apply FORWARD to the global dispatcherMapping.
            switch (dispatcherMapping) {
                case NOT_SET  :  dispatcherMapping = FORWARD; break;
                case ERROR : dispatcherMapping = FORWARD_ERROR; break;
                case INCLUDE  :  dispatcherMapping = INCLUDE_FORWARD; break;
                case INCLUDE_ERROR  :  dispatcherMapping = INCLUDE_ERROR_FORWARD; break;
                case REQUEST : dispatcherMapping = REQUEST_FORWARD; break;
                case REQUEST_ERROR : dispatcherMapping = REQUEST_ERROR_FORWARD; break;
                case REQUEST_ERROR_INCLUDE : dispatcherMapping = REQUEST_ERROR_FORWARD_INCLUDE; break;
                case REQUEST_INCLUDE : dispatcherMapping = REQUEST_FORWARD_INCLUDE; break;
            }
        } else if (dispatcher.equals("INCLUDE")) {
            // apply INCLUDE to the global dispatcherMapping.
            switch (dispatcherMapping) {
                case NOT_SET  :  dispatcherMapping = INCLUDE; break;
                case ERROR : dispatcherMapping = INCLUDE_ERROR; break;
                case FORWARD  :  dispatcherMapping = INCLUDE_FORWARD; break;
                case FORWARD_ERROR  :  dispatcherMapping = INCLUDE_ERROR_FORWARD; break;
                case REQUEST : dispatcherMapping = REQUEST_INCLUDE; break;
                case REQUEST_ERROR : dispatcherMapping = REQUEST_ERROR_INCLUDE; break;
                case REQUEST_ERROR_FORWARD : dispatcherMapping = REQUEST_ERROR_FORWARD_INCLUDE; break;
                case REQUEST_FORWARD : dispatcherMapping = REQUEST_FORWARD_INCLUDE; break;
            }
        } else if (dispatcher.equals("REQUEST")) {
            // apply REQUEST to the global dispatcherMapping.
            switch (dispatcherMapping) {
                case NOT_SET  :  dispatcherMapping = REQUEST; break;
                case ERROR : dispatcherMapping = REQUEST_ERROR; break;
                case FORWARD  :  dispatcherMapping = REQUEST_FORWARD; break;
                case FORWARD_ERROR  :  dispatcherMapping = REQUEST_ERROR_FORWARD; break;
                case INCLUDE  :  dispatcherMapping = REQUEST_INCLUDE; break;
                case INCLUDE_ERROR  :  dispatcherMapping = REQUEST_ERROR_INCLUDE; break;
                case INCLUDE_FORWARD : dispatcherMapping = REQUEST_FORWARD_INCLUDE; break;
                case INCLUDE_ERROR_FORWARD : dispatcherMapping = REQUEST_ERROR_FORWARD_INCLUDE; break;
            }
        }  else if (dispatcher.equals("ERROR")) {
            // apply ERROR to the global dispatcherMapping.
            switch (dispatcherMapping) {
                case NOT_SET  :  dispatcherMapping = ERROR; break;
                case FORWARD  :  dispatcherMapping = FORWARD_ERROR; break;
                case INCLUDE  :  dispatcherMapping = INCLUDE_ERROR; break;
                case INCLUDE_FORWARD : dispatcherMapping = INCLUDE_ERROR_FORWARD; break;
                case REQUEST : dispatcherMapping = REQUEST_ERROR; break;
                case REQUEST_INCLUDE : dispatcherMapping = REQUEST_ERROR_INCLUDE; break;
                case REQUEST_FORWARD : dispatcherMapping = REQUEST_ERROR_FORWARD; break;
                case REQUEST_FORWARD_INCLUDE : dispatcherMapping = REQUEST_ERROR_FORWARD_INCLUDE; break;
            }
        }
    }
    
    public int getDispatcherMapping() {
        // 每个 SRV.6.2.5 absence of any dispatcher elements is
        // equivelant to a REQUEST value
        if (dispatcherMapping == NOT_SET) return REQUEST;
        else return dispatcherMapping; 
    }

    // --------------------------------------------------------- Public Methods

    public String toString() {
        StringBuffer sb = new StringBuffer("FilterMap[");
        sb.append("filterName=");
        sb.append(this.filterName);
        if (servletName != null) {
            sb.append(", servletName=");
            sb.append(servletName);
        }
        if (urlPattern != null) {
            sb.append(", urlPattern=");
            sb.append(urlPattern);
        }
        sb.append("]");
        return (sb.toString());
    }
}
