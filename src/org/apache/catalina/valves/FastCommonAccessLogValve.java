package org.apache.catalina.valves;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import javax.servlet.ServletException;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.util.StringManager;

/**
 * <p><b>Valve</b>实现类, 生成一个Web服务器访问日志，其中包含与公共模式或组合模式匹配的详细行内容.
 * 作为附加功能, 支持日期更改时的日志文件自动翻转.</p>
 * <p>
 * 还支持条件日志记录. 可以使用<code>condition</code>属性指定.
 * 如果 ServletRequest.getAttribute(condition)返回一个非null值. 日志将被跳过.
 * </p>
 */
public final class FastCommonAccessLogValve extends ValveBase implements Lifecycle {


    // ----------------------------------------------------------- Constructors

    public FastCommonAccessLogValve() {
        super();
        setPattern("common");
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 当前打开日志文件的日期, 如果没有打开日志文件，则为零长度字符串.
     */
    private String dateStamp = "";


    /**
     * 创建日志文件的目录.
     */
    private String directory = "logs";


    /**
     * 实现类描述信息.
     */
    protected static final String info =
        "org.apache.catalina.valves.FastCommonAccessLogValve/1.0";


    /**
     * 生命周期事件支持.
     */
    protected LifecycleSupport lifecycle = new LifecycleSupport(this);


    /**
     * 日志消息的月份缩写集.
     */
    protected static final String months[] =
    { "Jan", "Feb", "Mar", "Apr", "May", "Jun",
      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };


    /**
     * 如果当前日志模式与公共访问日志格式模式相同, 然后，将这个变量设置为true，并以更优化和硬编码的方式登录.
     */
    private boolean common = false;


    /**
     * 对于组合格式(常见的，加上用户和参考),也这样做
     */
    private boolean combined = false;


    /**
     * 用于格式化访问日志行的模式.
     */
    private String pattern = null;


    /**
     * 添加到日志文件的文件名的前缀.
     */
    private String prefix = "access_log.";


    /**
     * 应该轮转日志文件吗? 默认是true (例如旧行为)
     */
    private boolean rotatable = true;


    /**
     * The string manager for this package.
     */
    private StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 是否已启动?
     */
    private boolean started = false;


    /**
     * 添加到日志文件的文件名的后缀.
     */
    private String suffix = "";


    /**
     * 当前记录使用的PrintWriter.
     */
    private PrintWriter writer = null;


    /**
     * "yyyy-MM-dd".
     */
    private SimpleDateFormat dateFormatter = null;


    /**
     * "dd".
     */
    private SimpleDateFormat dayFormatter = null;


    /**
     * "MM".
     */
    private SimpleDateFormat monthFormatter = null;


    /**
     * "yyyy".
     */
    private SimpleDateFormat yearFormatter = null;


    /**
     * "kk:mm:ss" (kk is a 24-hour representation of the hour).
     */
    private SimpleDateFormat timeFormatter = null;


    /**
     * 系统时区.
     */
    private TimeZone timezone = null;

    
    /**
     * 夏时制不工作时，以文本形式与GMT相对应的时区偏移量.
     */
    private String timeZoneNoDST = null;
 
    /**
     * 夏时制时，以文本形式与GMT相对应的时区偏移量.
     */
    private String timeZoneDST = null;


    /**
     * 这个valve最后更新时间，用于日志行.
     */
    private String currentDateString = null;
    
    
    /**
     * 最后更新时间时间戳.
     */
    private long currentDate = 0L;


    /**
     * 格式化日志行时, 经常使用像这样的字符串(" ").
     */
    private String space = " ";


    /**
     * 解决主机
     */
    private boolean resolveHosts = false;


    /**
     * 日志每天最后检查的时间戳
     */
    private long rotationLastChecked = 0L;


    /**
     * 正在做条件记录吗. 默认false.
     */
    private String condition = null;


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
     * 返回实现类的描述信息
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
     * @param pattern 新模式
     */
    public void setPattern(String pattern) {
        if (pattern == null)
            pattern = "";
        if (pattern.equals(Constants.AccessLog.COMMON_ALIAS))
            pattern = Constants.AccessLog.COMMON_PATTERN;
        if (pattern.equals(Constants.AccessLog.COMBINED_ALIAS))
            pattern = Constants.AccessLog.COMBINED_PATTERN;
        this.pattern = pattern;

        if (this.pattern.equals(Constants.AccessLog.COMBINED_PATTERN))
            combined = true;
        else
            combined = false;
    }


    /**
     * 返回日志文件前缀
     */
    public String getPrefix() {
        return (prefix);
    }


    /**
     * 设置日志文件前缀
     *
     * @param prefix 新的日志文件前缀
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }


    /**
     * 轮转日志
     */
    public boolean isRotatable() {
        return rotatable;
    }


    /**
     * 轮转日志
     *
     * @param rotatable true is we should rotate.
     */
    public void setRotatable(boolean rotatable) {
        this.rotatable = rotatable;
    }


    /**
     * 返回日志文件后缀.
     */
    public String getSuffix() {
        return (suffix);
    }


    /**
     * 设置日志文件后缀
     *
     * @param suffix 新的日志文件后缀
     */
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }


    /**
     * 设置解析主机标志
     *
     * @param resolveHosts The new resolve hosts value
     */
    public void setResolveHosts(boolean resolveHosts) {
        this.resolveHosts = resolveHosts;
    }


    /**
     * 获取解析主机标志的值.
     */
    public boolean isResolveHosts() {
        return resolveHosts;
    }


    /**
     * 返回条件记录日志时，要检索的属性名.
     * 如果是null, 每个请求被记录.
     */
    public String getCondition() {
        return condition;
    }


    /**
     * 设置ServletRequest.attribute查找执行条件日志.设置为 null来记录所有.
     *
     * @param condition Set to null to log everything
     */
    public void setCondition(String condition) {
        this.condition = condition;
    }

    /**
     *  返回日期格式基于日期的日志旋转
     */
    public String getFileDateFormat() {
        return fileDateFormat;
    }


    /**
     *  设置日期格式基于日期的日志旋转
     */
    public void setFileDateFormat(String fileDateFormat) {
        this.fileDateFormat =  fileDateFormat;
    }

    // --------------------------------------------------------- Public Methods


    /**
     * 执行周期任务, 例如重新加载, etc. 该方法将在该容器的类加载上下文中被调用.
     * 异常将被捕获并记录.
     */
    public void backgroundProcess() {
        if (writer != null)
            writer.flush();
    }


    /**
     * 记录信息描述指定的请求和响应, 根据<code>pattern</code>属性指定的格式.
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
        getNext().invoke(request, response);

        if (condition!=null &&
                null!=request.getRequest().getAttribute(condition)) {
            return;
        }

        StringBuffer result = new StringBuffer();

        // Check to see if we should log using the "common" access log pattern
        String value = null;
        
        if (isResolveHosts())
            result.append(request.getRemoteHost());
        else
            result.append(request.getRemoteAddr());
        
        result.append(" - ");
        
        value = request.getRemoteUser();
        if (value == null)
            result.append("- ");
        else {
            result.append(value);
            result.append(space);
        }
        
        result.append(getCurrentDateString());
        
        result.append(request.getMethod());
        result.append(space);
        result.append(request.getRequestURI());
        if (request.getQueryString() != null) {
            result.append('?');
            result.append(request.getQueryString());
        }
        result.append(space);
        result.append(request.getProtocol());
        result.append("\" ");
        
        result.append(response.getStatus());
        
        result.append(space);
        
        int length = response.getContentCount();
        
        if (length <= 0)
            value = "-";
        else
            value = "" + length;
        result.append(value);
        
        if (combined) {
            result.append(space);
            result.append("\"");
            String referer = request.getHeader("referer");
            if(referer != null)
                result.append(referer);
            else
                result.append("-");
            result.append("\"");
            
            result.append(space);
            result.append("\"");
            String ua = request.getHeader("user-agent");
            if(ua != null)
                result.append(ua);
            else
                result.append("-");
            result.append("\"");
        }
        
        log(result.toString());
        
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 关闭当前打开的日志文件
     */
    private synchronized void close() {
        if (writer == null)
            return;
        writer.flush();
        writer.close();
        writer = null;
        dateStamp = "";
    }


    /**
     * 将指定的消息记录到日志文件中, 如果日期从上一次日志调用更改后切换文件.
     *
     * @param message Message to be logged
     */
    public void log(String message) {
        // Log this message
        if (writer != null) {
            writer.println(message);
        }
    }


    /**
     * 返回指定月份的月份缩写, 它必须是一个两位数字的字符串.
     *
     * @param month Month number ("01" .. "12").
     */
    private String lookup(String month) {

        int index;
        try {
            index = Integer.parseInt(month) - 1;
        } catch (Throwable t) {
            index = 0;  // Can not happen, in theory
        }
        return (months[index]);
    }


    /**
     * 打开<code>dateStamp</code>指定的日期的新日志文件.
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
            writer = new PrintWriter(new BufferedWriter
                    (new FileWriter(pathname, true), 128000), false);
        } catch (IOException e) {
            writer = null;
        }
    }


    /**
     * 此方法返回一个精确到一秒钟的日期对象. 如果一个线程在一秒钟之内连续两次调用这个方法, 这个方法将返回相同的日期对象, 这样系统就不会花时间创建不必要的日期对象.
     *
     * @return Date
     */
    private String getCurrentDateString() {
        // 只需每秒创建一个新日期, max.
        long systime = System.currentTimeMillis();
        if ((systime - currentDate) > 1000) {
            synchronized (this) {
                // 不在乎这里是否准确: 如果一个条目确实记录在前一秒发生，它将不会有任何区别
                if ((systime - currentDate) > 1000) {

                    // Format the new date
                    Date date = new Date();
                    StringBuffer result = new StringBuffer(32);
                    result.append("[");
                    // Day
                    result.append(dayFormatter.format(date));
                    result.append('/');
                    // Month
                    result.append(lookup(monthFormatter.format(date)));
                    result.append('/');
                    // Year
                    result.append(yearFormatter.format(date));
                    result.append(':');
                    // Time
                    result.append(timeFormatter.format(date));
                    result.append(space);
                    // Time zone
                    result.append(getTimeZone(date));
                    result.append("] \"");
                    
                    // Check for log rotation
                    if (rotatable) {
                        // Check for a change of date
                        String tsDate = dateFormatter.format(date);
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
                    
                    currentDateString = result.toString();
                    currentDate = date.getTime();
                }
            }
        }
        return currentDateString;
    }


    private String getTimeZone(Date date) {
        if (timezone.inDaylightTime(date)) {
            return timeZoneDST;
        } else {
            return timeZoneNoDST;
        }
    }
    
    
    private String calculateTimeZoneOffset(long offset) {
        StringBuffer tz = new StringBuffer();
        if ((offset<0))  {
            tz.append("-");
            offset = -offset;
        } else {
            tz.append("+");
        }

        long hourOffset = offset/(1000*60*60);
        long minuteOffset = (offset/(1000*60)) % 60;

        if (hourOffset<10)
            tz.append("0");
        tz.append(hourOffset);

        if (minuteOffset<10)
            tz.append("0");
        tz.append(minuteOffset);

        return tz.toString();
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
     * 删除生命周期事件监听器.
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
                (sm.getString("accessLogValve.alreadyStarted"));
        lifecycle.fireLifecycleEvent(START_EVENT, null);
        started = true;

        // Initialize the timeZone, Date formatters, and currentDate
        timezone = TimeZone.getDefault();
        timeZoneNoDST = calculateTimeZoneOffset(timezone.getRawOffset());
        Calendar calendar = Calendar.getInstance(timezone);
        int offset = calendar.get(Calendar.DST_OFFSET);
        timeZoneDST = calculateTimeZoneOffset(timezone.getRawOffset()+offset);
        
        if (fileDateFormat==null || fileDateFormat.length()==0)
            fileDateFormat = "yyyy-MM-dd";
        dateFormatter = new SimpleDateFormat(fileDateFormat);
        dateFormatter.setTimeZone(timezone);
        dayFormatter = new SimpleDateFormat("dd");
        dayFormatter.setTimeZone(timezone);
        monthFormatter = new SimpleDateFormat("MM");
        monthFormatter.setTimeZone(timezone);
        yearFormatter = new SimpleDateFormat("yyyy");
        yearFormatter.setTimeZone(timezone);
        timeFormatter = new SimpleDateFormat("HH:mm:ss");
        timeFormatter.setTimeZone(timezone);
        currentDateString = getCurrentDateString();
        dateStamp = dateFormatter.format(new Date());

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
                (sm.getString("accessLogValve.notStarted"));
        lifecycle.fireLifecycleEvent(STOP_EVENT, null);
        started = false;

        close();
    }
}
