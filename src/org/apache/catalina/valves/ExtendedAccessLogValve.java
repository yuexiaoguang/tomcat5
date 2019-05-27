package org.apache.catalina.valves;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * W3C扩展日志文件格式的实现类.
 * See http://www.w3.org/TR/WD-logfile.html 有关格式的更多信息.
 *
 * 支持以下字段:
 * <ul>
 * <li><code>c-dns</code>:  客户端主机名</li>
 * <li><code>c-ip</code>:  客户端IP地址</li>
 * <li><code>bytes</code>:  字节服务</li>
 * <li><code>cs-method</code>:  请求的方法</li>
 * <li><code>cs-uri</code>:  请求的完整URI</li>
 * <li><code>cs-uri-query</code>:  查询字符串</li>
 * <li><code>cs-uri-stem</code>:  没有查询字符串的URI</li>
 * <li><code>date</code>:  GMT在yyyy-mm-dd格式的日期</li>
 * <li><code>s-dns</code>: 服务器DNS条目</li>
 * <li><code>s-ip</code>:  服务器IP地址</li>
 * <li><code>cs(XXX)</code>:  客户端到服务器的 header XXX 的值</li>
 * <li><code>sc(XXX)</code>: 服务器到客户端的header XXX 的值</li>
 * <li><code>sc-status</code>:  状态码</li>
 * <li><code>time</code>:  请求送达的时间</li>
 * <li><code>time-taken</code>:  用于服务请求的时间（以秒为单位）</li>
 * <li><code>x-A(XXX)</code>: 从servlet上下文获取 XXX 属性</li>
 * <li><code>x-C(XXX)</code>: 获取XXX名字的第一个cookie</li>
 * <li><code>x-R(XXX)</code>: 从servlet请求获取XXX属性</li>
 * <li><code>x-S(XXX)</code>: 从会话获取XXX属性</li>
 * <li><code>x-P(...)</code>:  调用request.getParameter(...)并URL编码它. 有助于捕获某些POST参数.</li>
 * <li>所有的x-H(...)以下方法将从HttpServletRequestObject调用</li>
 * <li><code>x-H(authType)</code>: getAuthType </li>
 * <li><code>x-H(characterEncoding)</code>: getCharacterEncoding </li>
 * <li><code>x-H(contentLength)</code>: getContentLength </li>
 * <li><code>x-H(locale)</code>:  getLocale</li>
 * <li><code>x-H(protocol)</code>: getProtocol </li>
 * <li><code>x-H(remoteUser)</code>:  getRemoteUser</li>
 * <li><code>x-H(requestedSessionId)</code>: getGequestedSessionId</li>
 * <li><code>x-H(requestedSessionIdFromCookie)</code>:
 *                  isRequestedSessionIdFromCookie </li>
 * <li><code>x-H(requestedSessionIdValid)</code>:
 *                  isRequestedSessionIdValid</li>
 * <li><code>x-H(scheme)</code>:  getScheme</li>
 * <li><code>x-H(secure)</code>:  isSecure</li>
 * </ul>
 *
 * <p>
 * 日志轮转可以打开或关闭. 这是由<code>rotatable</code>属性决定的.
 * </p>
 *
 * <p>
 * 对于UNIX 用户, 另一个字段调用<code>checkExists</code>也是可用的. 
 * 如果设置为true, 日志文件是否存在将在每次日志记录之前检查. 这样，外部日志轮转器可以将文件移动到某个地方，Tomcat将以一个新文件开始.
 * </p>
 *
 * <p>
 * 对于JMX 用户, 一个public 方法调用</code>rotate</code>已经允许您告诉这个实例将现有的日志文件移动到其他地方，开始编写一个新的日志文件.
 * </p>
 *
 * <p>
 * 还支持条件日志记录. 可以通过<code>condition</code>属性设置.
 * 如果ServletRequest.getAttribute(condition)返回值产生非null值. 日志将被跳过.
 * </p>
 *
 * <p>
 * 对于来自getAttribute()调用的扩展属性, 这是你的责任，以确保没有换行符或控制字符.
 * </p>
 */
public class ExtendedAccessLogValve extends ValveBase implements Lifecycle {

    // ----------------------------------------------------------- Constructors

    public ExtendedAccessLogValve() {
        super();
    }


    // ----------------------------------------------------- Instance Variables
    private static Log log = LogFactory.getLog(ExtendedAccessLogValve.class);


    /**
     * 实现类描述信息.
     */
    protected static final String info =
        "org.apache.catalina.valves.ExtendedAccessLogValve/1.0";


    /**
     * 生命周期事件支持.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);



    /**
     * The string manager for this package.
     */
    private StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 是否已启动?
     */
    private boolean started = false;


    /**
     * 当前打开日志文件的日期, 如果没有打开日志文件，则为零长度字符串.
     */
    private String dateStamp = "";


    /**
     * 记录日志使用的PrintWriter.
     */
    private PrintWriter writer = null;


    /**
     * 文件名中包含的日期地formatter
     */
    private SimpleDateFormat fileDateFormatter = null;


    /**
     * "yyyy-MM-dd".
     */
    private SimpleDateFormat dateFormatter = null;


    /**
     * "kk:mm:ss" (kk is a 24-hour representation of the hour).
     */
    private SimpleDateFormat timeFormatter = null;


    /**
     * 小数点后3位时间formatter.
     */
     private DecimalFormat timeTakenFormatter = null;


    /**
     * 我的IP地址. 查找一次并记住它. 转储，如果我们可以确定另一个可靠的方式获得服务器IP地址，因为这个服务器可能有许多IP的.
     */
    private String myIpAddress = null;


    /**
     * 我的DNS名称. 查找一次并记住它. 转储，如果我们可以确定另一个可靠的方式获得服务器名称地址，因为这个服务器可能有许多IP的.
     */
    private String myDNSName = null;


    /**
     * 要记录的所有字段的存储器，在模式被解码之后
     */
    private FieldInfo[] fieldInfos;


    /**
     * 正在写入的当前日志文件. 当checkExists是true时，是有用的.
     */
    private File currentLogFile = null;



    /**
     * 系统时间，当这个valve 用于记录日志的最后使用时间.
     */
    private Date currentDate = null;


    /**
     * 最后一次检查日志每日轮转的时间.
     */
    private long rotationLastChecked = 0L;


    /**
     * 创建日志文件的目录.
     */
    private String directory = "logs";


    /**
     * 用于格式化访问日志行的模式.
     */
    private String pattern = null;


    /**
     * 添加到日志文件的文件名的前缀.
     */
    private String prefix = "access_log.";


    /**
     * 是否轮转日志文件? 默认是true (像旧行为)
     */
    private boolean rotatable = true;


    /**
     * 添加到日志文件的文件名的后缀.
     */
    private String suffix = "";


    /**
     * 正在做条件记录吗. 默认false.
     */
    private String condition = null;


    /**
     * 检查日志文件的存在吗? 有助于外部代理重命名日志文件可以自动重新创建它.
     */
    private boolean checkExists = false;


    /**
     * 放置在日志文件名中的日期格式. 自负使用风险!
     */
    private String fileDateFormat = null;


    // ------------------------------------------------------------- Properties


    /**
     * 返回创建日志文件的目录.
     */
    public String getDirectory() {
        return (directory);
    }


    /**
     * 设置创建日志文件的目录
     *
     * @param directory 新的日志文件目录
     */
    public void setDirectory(String directory) {
        this.directory = directory;
    }


    /**
     * 返回此实现类的描述信息
     */
    public String getInfo() {
        return (info);
    }


    /**
     * 返回格式模式
     */
    public String getPattern() {
        return (this.pattern);
    }


    /**
     * 设置格式模式, 首先翻译任何识别的别名.
     *
     * @param pattern The new pattern pattern
     */
    public void setPattern(String pattern) {
        FieldInfo[] f= decodePattern(pattern);
        if (f!=null) {
            this.pattern = pattern;
            this.fieldInfos = f;
        }
    }


    /**
     * 返回日志文件前缀.
     */
    public String getPrefix() {
        return (prefix);
    }


    /**
     * 设置日志文件前缀
     *
     * @param prefix The new log file prefix
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }


    /**
     * 如果日志自动轮转，返回true.
     */
    public boolean isRotatable() {
        return rotatable;
    }


    /**
     * 设置日志轮转标志
     *
     * @param rotatable true is we should rotate.
     */
    public void setRotatable(boolean rotatable) {
        this.rotatable = rotatable;
    }


    /**
     * 返回日志文件后缀
     */
    public String getSuffix() {
        return (suffix);
    }


    /**
     * 设置日志文件后缀
     *
     * @param suffix The new log file suffix
     */
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }



    /**
     * 当执行条件日志时，返回是否查找属性名称.
     * 如果是null, 每个请求都会被记录.
     */
    public String getCondition() {
        return condition;
    }


    /**
     * 设置ServletRequest.attribute 查找执行条件日志. 设置为NULL以记录所有.
     *
     * @param condition Set to null to log everything
     */
    public void setCondition(String condition) {
        this.condition = condition;
    }



    /**
     * 在日志记录之前检查文件是否存在.
     */
    public boolean isCheckExists() {
        return checkExists;
    }


    /**
     * 设置是否在日志记录之前检查日志文件是否存在.
     *
     * @param checkExists true meaning to check for file existence.
     */
    public void setCheckExists(boolean checkExists) {
        this.checkExists = checkExists;
    }


    /**
     *  放置在日志文件名中的日期格式.
     */
    public String getFileDateFormat() {
        return fileDateFormat;
    }


    /**
     * 放置在日志文件名中的日期格式
     */
    public void setFileDateFormat(String fileDateFormat) {
        this.fileDateFormat =  fileDateFormat;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 记录描述指定请求和响应的消息, 根据<code>pattern</code>属性指定的格式.
     *
     * @param request Request being processed
     * @param response Response being processed
     *
     * @exception IOException if an input/output error has occurred
     * @exception ServletException if a servlet error has occurred
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        // Pass this request on to the next valve in our pipeline
        long endTime;
        long runTime;
        long startTime=System.currentTimeMillis();

        getNext().invoke(request, response);

        endTime = System.currentTimeMillis();
        runTime = endTime-startTime;

        if (fieldInfos==null || condition!=null &&
              null!=request.getRequest().getAttribute(condition)) {
            return;
        }


        Date date = getDate(endTime);
        StringBuffer result = new StringBuffer();

        for (int i=0; fieldInfos!=null && i<fieldInfos.length; i++) {
            switch(fieldInfos[i].type) {
                case FieldInfo.DATA_CLIENT:
                    if (FieldInfo.FIELD_IP==fieldInfos[i].location)
                        result.append(request.getRequest().getRemoteAddr());
                    else if (FieldInfo.FIELD_DNS==fieldInfos[i].location)
                        result.append(request.getRequest().getRemoteHost());
                    else
                        result.append("?WTF?"); /* This should never happen! */
                    break;
                case FieldInfo.DATA_SERVER:
                    if (FieldInfo.FIELD_IP==fieldInfos[i].location)
                        result.append(myIpAddress);
                    else if (FieldInfo.FIELD_DNS==fieldInfos[i].location)
                        result.append(myDNSName);
                    else
                        result.append("?WTF?"); /* This should never happen! */
                    break;
                case FieldInfo.DATA_REMOTE:
                    result.append('?'); /* I don't know how to handle these! */
                    break;
                case FieldInfo.DATA_CLIENT_TO_SERVER:
                    result.append(getClientToServer(fieldInfos[i], request));
                    break;
                case FieldInfo.DATA_SERVER_TO_CLIENT:
                    result.append(getServerToClient(fieldInfos[i], response));
                    break;
                case FieldInfo.DATA_SERVER_TO_RSERVER:
                    result.append('-');
                    break;
                case FieldInfo.DATA_RSERVER_TO_SERVER:
                    result.append('-');
                    break;
                case FieldInfo.DATA_APP_SPECIFIC:
                    result.append(getAppSpecific(fieldInfos[i], request));
                    break;
                case FieldInfo.DATA_SPECIAL:
                    if (FieldInfo.SPECIAL_DATE==fieldInfos[i].location)
                        result.append(dateFormatter.format(date));
                    else if (FieldInfo.SPECIAL_TIME_TAKEN==fieldInfos[i].location)
                        result.append(timeTakenFormatter.format(runTime/1000d));
                    else if (FieldInfo.SPECIAL_TIME==fieldInfos[i].location)
                        result.append(timeFormatter.format(date));
                    else if (FieldInfo.SPECIAL_BYTES==fieldInfos[i].location) {
                        int length = response.getContentCount();
                        if (length > 0)
                            result.append(length);
                        else
                            result.append("-");
                    } else if (FieldInfo.SPECIAL_CACHED==fieldInfos[i].location)
                        result.append('-'); /* I don't know how to evaluate this! */
                    else
                        result.append("?WTF?"); /* This should never happen! */
                    break;
                default:
                    result.append("?WTF?"); /* This should never happen! */
            }

            if (fieldInfos[i].postWhiteSpace!=null) {
                result.append(fieldInfos[i].postWhiteSpace);
            }
        }
        log(result.toString(), date);
    }


    /**
     * 将现有日志文件重命名为其他文件. 然后再次打开旧日志文件名. 拟由JMX代理.
     *
     * @param newFileName 将日志文件项移动到的新文件名
     * @return true 如果文件被轮转了，没有报错
     */
    public synchronized boolean rotate(String newFileName) {

        if (currentLogFile!=null) {
            File holder = currentLogFile;
            close();
            try {
                holder.renameTo(new File(newFileName));
            } catch(Throwable e){
                log.error("rotate failed", e);
            }

            /* Make sure date is correct */
            currentDate = new Date(System.currentTimeMillis());
            dateStamp = fileDateFormatter.format(currentDate);

            open();
            return true;
        } else {
            return false;
        }
    }

    // -------------------------------------------------------- Private Methods


    /**
     *  将客户端返回到服务器数据.
     *  @param fieldInfo 要解码的字段
     *  @param request 提取数据的对象
     *  @return The appropriate value.
     */
     private String getClientToServer(FieldInfo fieldInfo, Request request) {

        switch(fieldInfo.location) {
            case FieldInfo.FIELD_METHOD:
                return request.getMethod();
            case FieldInfo.FIELD_URI:
                if (null==request.getQueryString())
                    return request.getRequestURI();
                else
                    return request.getRequestURI() + "?" + request.getQueryString();
            case FieldInfo.FIELD_URI_STEM:
                return request.getRequestURI();
            case FieldInfo.FIELD_URI_QUERY:
                if (null==request.getQueryString())
                    return "-";
                return request.getQueryString();
            case FieldInfo.FIELD_HEADER:
                return wrap(request.getHeader(fieldInfo.value));
            default:
                ;
        }
        return "-";
    }


    /**
     *  将服务器返回到客户端数据
     *  @param fieldInfo 要解码的字段
     *  @param response 提取数据的对象
     *  @return The appropriate value.
     */
    private String getServerToClient(FieldInfo fieldInfo, Response response) {
        switch(fieldInfo.location) {
            case FieldInfo.FIELD_STATUS:
                return "" + response.getStatus();
            case FieldInfo.FIELD_COMMENT:
                return "?"; /* Not coded yet*/
            case FieldInfo.FIELD_HEADER:
                return wrap(response.getHeader(fieldInfo.value));
            default:
                ;
        }
        return "-";
    }


    /**
     * 获取特定于应用程序的数据
     * @param fieldInfo 要解码的字段
     * @param request 提取数据的对象
     * @return The appropriate value
     */
    private String getAppSpecific(FieldInfo fieldInfo, Request request) {

        switch(fieldInfo.xType) {
            case FieldInfo.X_PARAMETER:
                return wrap(urlEncode(request.getParameter(fieldInfo.value)));
            case FieldInfo.X_REQUEST:
                return wrap(request.getAttribute(fieldInfo.value));
            case FieldInfo.X_SESSION:
                HttpSession session = null;
                if (request!=null){
                    session = request.getSession(false);
                    if (session!=null)
                        return wrap(session.getAttribute(fieldInfo.value));
                }
                break;
            case FieldInfo.X_COOKIE:
                Cookie[] c = request.getCookies();
                for (int i=0; c != null && i < c.length; i++){
                    if (fieldInfo.value.equals(c[i].getName())){
                        return wrap(c[i].getValue());
                    }
                 }
            case FieldInfo.X_APP:
                return wrap(request.getContext().getServletContext()
                                .getAttribute(fieldInfo.value));
            case FieldInfo.X_SERVLET_REQUEST:
                if (fieldInfo.location==FieldInfo.X_LOC_AUTHTYPE) {
                    return wrap(request.getAuthType());
                } else if (fieldInfo.location==FieldInfo.X_LOC_REMOTEUSER) {
                    return wrap(request.getRemoteUser());
                } else if (fieldInfo.location==
                            FieldInfo.X_LOC_REQUESTEDSESSIONID) {
                    return wrap(request.getRequestedSessionId());
                } else if (fieldInfo.location==
                            FieldInfo.X_LOC_REQUESTEDSESSIONIDFROMCOOKIE) {
                    return wrap(""+request.isRequestedSessionIdFromCookie());
                } else if (fieldInfo.location==
                            FieldInfo.X_LOC_REQUESTEDSESSIONIDVALID) {
                    return wrap(""+request.isRequestedSessionIdValid());
                } else if (fieldInfo.location==FieldInfo.X_LOC_CONTENTLENGTH) {
                    return wrap(""+request.getContentLength());
                } else if (fieldInfo.location==
                            FieldInfo.X_LOC_CHARACTERENCODING) {
                    return wrap(request.getCharacterEncoding());
                } else if (fieldInfo.location==FieldInfo.X_LOC_LOCALE) {
                    return wrap(request.getLocale());
                } else if (fieldInfo.location==FieldInfo.X_LOC_PROTOCOL) {
                    return wrap(request.getProtocol());
                } else if (fieldInfo.location==FieldInfo.X_LOC_SCHEME) {
                    return wrap(request.getScheme());
                } else if (fieldInfo.location==FieldInfo.X_LOC_SECURE) {
                    return wrap(""+request.isSecure());
                }
                break;
            default:
                ;
        }
        return "-";
    }


    /**
     *  URL对给定字符串进行编码. 如果是null或空, 返回null.
     */
    private String urlEncode(String value) {
        if (null==value || value.length()==0) {
            return null;
        }
        return URLEncoder.encode(value);
    }


    /**
     *  将传入的值包装成引号，并用双引号转义任何内部引号.
     *
     *  @param value - The value to wrap quotes around
     *  @return '-' 如果是null或空. 否则, toString()将调用对象，该值将以引号括起来，任何引号将以2组引号转义.
     */
    private String wrap(Object value) {
        String svalue;
        // 值是否包含一个 " ? 如果有，必须编码它
        if (value==null || "-".equals(value))
            return "-";

        try {
            svalue = value.toString();
            if ("".equals(svalue))
                return "-";
        } catch(Throwable e){
            /* Log error */
            return "-";
        }

        /* 用双引号包装所有引号. */
        StringBuffer buffer = new StringBuffer(svalue.length()+2);
        buffer.append('"');
        int i=0;
        while (i<svalue.length()) {
            int j = svalue.indexOf('"', i);
            if (j==-1) {
                buffer.append(svalue.substring(i));
                i=svalue.length();
            } else {
                buffer.append(svalue.substring(i, j+1));
                buffer.append('"');
                i=j+2;
            }
        }
        buffer.append('"');
        return buffer.toString();
    }


    /**
     * 关闭当前打开的日志文件
     */
    private synchronized void close() {
        if (writer == null)
            return;
        writer.flush();
        writer.close();
        writer = null;
        currentLogFile = null;
    }


    /**
     * 将指定的消息记录到日志文件中, 如果日期从上一次日志调用更改后切换文件.
     *
     * @param message Message to be logged
     * @param date the current Date object (so this method doesn't need to
     *        create a new one)
     */
    private void log(String message, Date date) {

        if (rotatable){
            // Only do a logfile switch check once a second, max.
            long systime = System.currentTimeMillis();
            if ((systime - rotationLastChecked) > 1000) {

                // We need a new currentDate
                currentDate = new Date(systime);
                rotationLastChecked = systime;

                // Check for a change of date
                String tsDate = fileDateFormatter.format(currentDate);

                // If the date has changed, switch log files
                if (!dateStamp.equals(tsDate)) {
                    synchronized (this) {
                        if (!dateStamp.equals(tsDate)) {
                            close();
                            dateStamp = tsDate;
                            open();
                        }
                    }
                }
            }
        }

        /* In case something external rotated the file instead */
        if (checkExists){
            synchronized (this) {
                if (currentLogFile!=null && !currentLogFile.exists()) {
                    try {
                        close();
                    } catch (Throwable e){
                        log.info("at least this wasn't swallowed", e);
                    }

                    /* Make sure date is correct */
                    currentDate = new Date(System.currentTimeMillis());
                    dateStamp = fileDateFormatter.format(currentDate);

                    open();
                }
            }
        }
        // Log this message
        if (writer != null) {
            writer.println(message);
        }
    }


    /**
     * 打开<code>dateStamp</code>属性指定的日期的新日志文件.
     */
    private synchronized void open() {

        // Create the directory if necessary
        File dir = new File(directory);
        if (!dir.isAbsolute())
            dir = new File(System.getProperty("catalina.base"), directory);
        dir.mkdirs();

        // Open the current log file
        try {
            String pathname;

            // If no rotate - no need for dateStamp in fileName
            if (rotatable){
                pathname = dir.getAbsolutePath() + File.separator +
                            prefix + dateStamp + suffix;
            } else {
                pathname = dir.getAbsolutePath() + File.separator +
                            prefix + suffix;
            }

            currentLogFile = new File(pathname);
            writer = new PrintWriter(new FileWriter(pathname, true), true);
            if (currentLogFile.length()==0) {
                writer.println("#Fields: " + pattern);
                writer.println("#Version: 1.0");
                writer.println("#Software: " + ServerInfo.getServerInfo());
            }
        } catch (IOException e) {
            writer = null;
            currentLogFile = null;
        }
    }


    /**
     * 此方法返回一个精确到一秒钟的日期对象. 
     * 如果一个线程调用这个方法来获取而且还不到1秒, 因为创建了一个新的日期, 这个方法简单地给出相同的日期，这样系统就不会花时间不必要地创建日期对象.
     */
    private Date getDate(long systime) {
        /* 避免额外调用 System.currentTimeMillis(); */
        if (0==systime) {
            systime = System.currentTimeMillis();
        }

        // 只需每秒创建一个新日期, max.
        if ((systime - currentDate.getTime()) > 1000) {
            currentDate.setTime(systime);
        }
        return currentDate;
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * 添加生命周期事件监听器.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener) {
        lifecycle.addLifecycleListener(listener);
    }


    /**
     * 获取关联的所有生命周期事件监听器.
     * 如果没有, 返回零长度数组.
     */
    public LifecycleListener[] findLifecycleListeners() {
        return lifecycle.findLifecycleListeners();
    }


    /**
     * 删除生命周期事件监听器
     *
     * @param listener The listener to add
     */
    public void removeLifecycleListener(LifecycleListener listener) {
        lifecycle.removeLifecycleListener(listener);
    }


    /**
     * 这个方法应该在<code>configure()</code>方法之后调用, 在其他方法之前调用.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    public void start() throws LifecycleException {

        // Validate and update our current component state
        if (started)
            throw new LifecycleException
                (sm.getString("extendedAccessLogValve.alreadyStarted"));
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        // Initialize the timeZone, Date formatters, and currentDate
        TimeZone tz = TimeZone.getTimeZone("GMT");
        dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
        dateFormatter.setTimeZone(tz);
        timeFormatter = new SimpleDateFormat("HH:mm:ss");
        timeFormatter.setTimeZone(tz);
        currentDate = new Date(System.currentTimeMillis());
        if (fileDateFormat==null || fileDateFormat.length()==0)
            fileDateFormat = "yyyy-MM-dd";
        fileDateFormatter = new SimpleDateFormat(fileDateFormat);
        dateStamp = fileDateFormatter.format(currentDate);
        timeTakenFormatter = new DecimalFormat("0.000");

        /* Everybody say ick ... ick */
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            myIpAddress = inetAddress.getHostAddress();
            myDNSName = inetAddress.getHostName();
        } catch(Throwable e){
            myIpAddress="127.0.0.1";
            myDNSName="localhost";
        }
        open();
    }


    /**
     * 这个方法应该最后一个调用.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public void stop() throws LifecycleException {

        // Validate and update our current component state
        if (!started)
            throw new LifecycleException
                (sm.getString("extendedAccessLogValve.notStarted"));
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        close();
    }


    /**
     * 解码给定模式. 是公开的，所以模式可以被验证.
     * @param fields 解码模式
     * @return null on error. 否则解码字段数组
     */
    public FieldInfo[] decodePattern(String fields) {

        if (log.isDebugEnabled())
            log.debug("decodePattern, fields=" + fields);

        LinkedList list = new LinkedList();

        //Ignore leading whitespace.
        int i=0;
        for (;i<fields.length() && Character.isWhitespace(fields.charAt(i));i++);

        if (i>=fields.length()) {
            log.info("fields was just empty or whitespace");
            return null;
        }

        int j;
        while(i<fields.length()) {
            if (log.isDebugEnabled())
                log.debug("fields.substring(i)=" + fields.substring(i));

            FieldInfo currentFieldInfo = new FieldInfo();


            if (fields.startsWith("date",i)) {
                currentFieldInfo.type = FieldInfo.DATA_SPECIAL;
                currentFieldInfo.location = FieldInfo.SPECIAL_DATE;
                i+="date".length();
            } else if (fields.startsWith("time-taken",i)) {
                currentFieldInfo.type = FieldInfo.DATA_SPECIAL;
                currentFieldInfo.location = FieldInfo.SPECIAL_TIME_TAKEN;
                i+="time-taken".length();
            } else if (fields.startsWith("time",i)) {
                currentFieldInfo.type = FieldInfo.DATA_SPECIAL;
                currentFieldInfo.location = FieldInfo.SPECIAL_TIME;
                i+="time".length();
            } else if (fields.startsWith("bytes",i)) {
                currentFieldInfo.type = FieldInfo.DATA_SPECIAL;
                currentFieldInfo.location = FieldInfo.SPECIAL_BYTES;
                i+="bytes".length();
            } else if (fields.startsWith("cached",i)) {
                currentFieldInfo.type = FieldInfo.DATA_SPECIAL;
                currentFieldInfo.location = FieldInfo.SPECIAL_CACHED;
                i+="cached".length();
            } else if (fields.startsWith("c-ip",i)) {
                currentFieldInfo.type = FieldInfo.DATA_CLIENT;
                currentFieldInfo.location = FieldInfo.FIELD_IP;
                i+="c-ip".length();
            } else if (fields.startsWith("c-dns",i)) {
                currentFieldInfo.type = FieldInfo.DATA_CLIENT;
                currentFieldInfo.location = FieldInfo.FIELD_DNS;
                i+="c-dns".length();
            } else if (fields.startsWith("s-ip",i)) {
                currentFieldInfo.type = FieldInfo.DATA_SERVER;
                currentFieldInfo.location = FieldInfo.FIELD_IP;
                i+="s-ip".length();
            } else if (fields.startsWith("s-dns",i)) {
                currentFieldInfo.type = FieldInfo.DATA_SERVER;
                currentFieldInfo.location = FieldInfo.FIELD_DNS;
                i+="s-dns".length();
            } else if (fields.startsWith("cs",i)) {
                i = decode(fields, i+2, currentFieldInfo,
                            FieldInfo.DATA_CLIENT_TO_SERVER);
                if (i<0)
                    return null;
            } else if (fields.startsWith("sc",i)) {
                i = decode(fields, i+2, currentFieldInfo,
                            FieldInfo.DATA_SERVER_TO_CLIENT);
                if (i<0)
                    return null;
            } else if (fields.startsWith("sr",i)) {
                i = decode(fields, i+2, currentFieldInfo,
                            FieldInfo.DATA_SERVER_TO_RSERVER);
                if (i<0)
                    return null;
            } else if (fields.startsWith("rs",i)) {
                i = decode(fields, i+2, currentFieldInfo,
                            FieldInfo.DATA_RSERVER_TO_SERVER);
                if (i<0)
                    return null;
            } else if (fields.startsWith("x",i)) {
                i = decodeAppSpecific(fields, i, currentFieldInfo);
            } else {
                // Unable to decode ...
                log.error("unable to decode with rest of chars being: " +
                            fields.substring(i));
                return null;
            }

            // 到这里应该拥有这个字段, 获取空格
            j=i;
            for (;j<fields.length() && Character.isWhitespace(fields.charAt(j));j++);

            if (j>=fields.length()) {
                if (j==i) {
                    // Special case - end of string
                    currentFieldInfo.postWhiteSpace = "";
                } else {
                    currentFieldInfo.postWhiteSpace = fields.substring(i);
                    i=j;
                }
            } else {
                currentFieldInfo.postWhiteSpace = fields.substring(i,j);
                i=j;
            }

            list.add(currentFieldInfo);
        }

        i=0;
        FieldInfo[] f = new FieldInfo[list.size()];
        for (Iterator k = list.iterator(); k.hasNext();)
             f[i++] = (FieldInfo)k.next();

        if (log.isDebugEnabled())
            log.debug("finished decoding with length of: " + i);

        return f;
    }

    /**
     * 解码CS或SC字段
     * 返回负的错误
     *
     * @param fields 解码模式
     * @param i 正在解码的字符串索引
     * @param fieldInfo 存储结果的地方
     * @param type 正在解码的类型
     * @return -1 on error. 否则，新的字符串索引
     */
    private int decode(String fields, int i, FieldInfo fieldInfo, short type) {

        if (fields.startsWith("-status",i)) {
            fieldInfo.location = FieldInfo.FIELD_STATUS;
            i+="-status".length();
        } else if (fields.startsWith("-comment",i)) {
            fieldInfo.location = FieldInfo.FIELD_COMMENT;
            i+="-comment".length();
        } else if (fields.startsWith("-uri-query",i)) {
            fieldInfo.location = FieldInfo.FIELD_URI_QUERY;
            i+="-uri-query".length();
        } else if (fields.startsWith("-uri-stem",i)) {
            fieldInfo.location = FieldInfo.FIELD_URI_STEM;
            i+="-uri-stem".length();
        } else if (fields.startsWith("-uri",i)) {
            fieldInfo.location = FieldInfo.FIELD_URI;
            i+="-uri".length();
        } else if (fields.startsWith("-method",i)) {
            fieldInfo.location = FieldInfo.FIELD_METHOD;
            i+="-method".length();
        } else if (fields.startsWith("(",i)) {
            fieldInfo.location = FieldInfo.FIELD_HEADER;
            i++;                                  /* Move past the ( */
            int j = fields.indexOf(')', i);
            if (j==-1) {                          /* Not found */
                log.error("No closing ) found for in decode");
                return -1;
            }
            fieldInfo.value = fields.substring(i,j);
            i=j+1;                                // Move pointer past ) */
        } else {
            log.error("The next characters couldn't be decoded: " + fields.substring(i));
            return -1;
        }
        fieldInfo.type = type;
        return i;
    }


    /**
      * 解码特定于应用程序的日志条目
      *
      * 特殊字段的形式:
      * x-C(...) - For cookie
      * x-A(...) - Value in servletContext
      * x-S(...) - Value in session
      * x-R(...) - Value in servletRequest
      * @param fields 解码模式
      * @param i 正在解码的字符串索引
      * @param fieldInfo 存储结果的地方
      * @return -1 on error. 否则，新的字符串索引
      */
    private int decodeAppSpecific(String fields, int i, FieldInfo fieldInfo) {

        fieldInfo.type = FieldInfo.DATA_APP_SPECIFIC;
        /* Move past 'x-' */
        i+=2;

        if (i>=fields.length()) {
            log.error("End of line reached before decoding x- param");
            return -1;
        }

        switch(fields.charAt(i)) {
            case 'A':
                fieldInfo.xType = FieldInfo.X_APP;
                break;
            case 'C':
                fieldInfo.xType = FieldInfo.X_COOKIE;
                break;
            case 'R':
                fieldInfo.xType = FieldInfo.X_REQUEST;
                break;
            case 'S':
                fieldInfo.xType = FieldInfo.X_SESSION;
                break;
            case 'H':
                fieldInfo.xType = FieldInfo.X_SERVLET_REQUEST;
                break;
            case 'P':
                fieldInfo.xType = FieldInfo.X_PARAMETER;
                break;
            default:
                return -1;
        }

        /* test that next char is a ( */
        if (i+1!=fields.indexOf('(',i)) {
            log.error("x param in wrong format. Needs to be 'x-#(...)' read the docs!");
            return -1;
        }
        i+=2; /* Move inside of the () */

        /* Look for ending ) and return error if not found. */
        int j = fields.indexOf(')',i);
        if (j==-1) {
            log.error("x param in wrong format. No closing ')'!");
            return -1;
        }

        fieldInfo.value = fields.substring(i,j);

        if (fieldInfo.xType == FieldInfo.X_SERVLET_REQUEST) {
            if ("authType".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_AUTHTYPE;
            } else if ("remoteUser".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_REMOTEUSER;
            } else if ("requestedSessionId".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_REQUESTEDSESSIONID;
            } else if ("requestedSessionIdFromCookie".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_REQUESTEDSESSIONIDFROMCOOKIE;
            } else if ("requestedSessionIdValid".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_REQUESTEDSESSIONIDVALID;
            } else if ("contentLength".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_CONTENTLENGTH;
            } else if ("characterEncoding".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_CHARACTERENCODING;
            } else if ("locale".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_LOCALE;
            } else if ("protocol".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_PROTOCOL;
            } else if ("scheme".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_SCHEME;
            } else if ("secure".equals(fieldInfo.value)){
                fieldInfo.location = FieldInfo.X_LOC_SECURE;
            } else {
                log.error("x param for servlet request, couldn't decode value: " +
                            fieldInfo.location);
                return -1;
            }
        }
        return j+1;
    }
}

/**
 * 解码模式
 */
class FieldInfo {
    /*
       	下列常量的目标是将日志项建设尽快通过numerci解码的方法, 而不是在每个日志请求上执行多个字符串比较.
    */

    /* 数据位于何处. */
    static final short DATA_CLIENT = 0;
    static final short DATA_SERVER = 1;
    static final short DATA_REMOTE = 2;
    static final short DATA_CLIENT_TO_SERVER = 3;
    static final short DATA_SERVER_TO_CLIENT = 4;
    static final short DATA_SERVER_TO_RSERVER = 5; /* Here to honor the spec. */
    static final short DATA_RSERVER_TO_SERVER = 6; /* Here to honor the spec. */
    static final short DATA_APP_SPECIFIC = 7;
    static final short DATA_SPECIAL = 8;

    /* 特殊字段类型. */
    static final short SPECIAL_DATE         = 1;
    static final short SPECIAL_TIME_TAKEN   = 2;
    static final short SPECIAL_TIME         = 3;
    static final short SPECIAL_BYTES        = 4;
    static final short SPECIAL_CACHED       = 5;

    /* Where to pull the data for prefixed values */
    static final short FIELD_IP            = 1;
    static final short FIELD_DNS           = 2;
    static final short FIELD_STATUS        = 3;
    static final short FIELD_COMMENT       = 4;
    static final short FIELD_METHOD        = 5;
    static final short FIELD_URI           = 6;
    static final short FIELD_URI_STEM      = 7;
    static final short FIELD_URI_QUERY     = 8;
    static final short FIELD_HEADER        = 9;


    /* Application Specific parameters */
    static final short X_REQUEST = 1; /* For x app specific */
    static final short X_SESSION = 2; /* For x app specific */
    static final short X_COOKIE  = 3; /* For x app specific */
    static final short X_APP     = 4; /* For x app specific */
    static final short X_SERVLET_REQUEST = 5; /* For x app specific */
    static final short X_PARAMETER = 6; /* For x app specific */

    static final short X_LOC_AUTHTYPE                       = 1;
    static final short X_LOC_REMOTEUSER                     = 2;
    static final short X_LOC_REQUESTEDSESSIONID             = 3;
    static final short X_LOC_REQUESTEDSESSIONIDFROMCOOKIE   = 4;
    static final short X_LOC_REQUESTEDSESSIONIDVALID        = 5;
    static final short X_LOC_CONTENTLENGTH                  = 6;
    static final short X_LOC_CHARACTERENCODING              = 7;
    static final short X_LOC_LOCALE                         = 8;
    static final short X_LOC_PROTOCOL                       = 9;
    static final short X_LOC_SCHEME                         = 10;
    static final short X_LOC_SECURE                         = 11;



    /** 字段类型 */
    short type;

    /** 获取数据的位置? 讨厌的变量名. */
    short location;

    /** 指定的 x- 位置，来获取数据 */
    short xType;

    /** 需要的字段值. 需要标头和应用程序特定的. */
    String value;

    /** 此字段后面有空格吗? Put it here. */
    String postWhiteSpace = null;

}
