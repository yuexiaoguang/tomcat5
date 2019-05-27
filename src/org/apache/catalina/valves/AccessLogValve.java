package org.apache.catalina.valves;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
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
import org.apache.catalina.util.StringManager;


/**
 * <p><b>Valve</b>接口实现类生成Web服务器访问日志, 具有匹配可配置模式的详细行内容.
 * 可用模式的语法与Apache <code>mod_log_config</code>模块类似. 
 * 作为附加功能, 支持日期更改时的日志文件自动翻转.</p>
 *
 * <p>记录的消息的模式可以包括常量文本或以下替换字符串中的任何一个, 用于替换指定响应的相应信息:</p>
 * <ul>
 * <li><b>%a</b> - 远程IP地址
 * <li><b>%A</b> - 本地IP地址
 * <li><b>%b</b> - 发送字节数, 不包括HTTP头, 或'-' 如果没有字节发送
 * <li><b>%B</b> - 发送字节数, 不包括HTTP头
 * <li><b>%h</b> - 远程主机名
 * <li><b>%H</b> - 请求协议
 * <li><b>%l</b> - 远程逻辑用户名(总是返回 '-')
 * <li><b>%m</b> - 请求的方法
 * <li><b>%p</b> - 本地端口
 * <li><b>%q</b> - 查询字符串(前面加上一个 '?' 如果它真的存在, 否则是空字符串
 * <li><b>%r</b> - 请求的第一行
 * <li><b>%s</b> - 响应的HTTP状态码
 * <li><b>%S</b> - User session ID
 * <li><b>%t</b> - 日期和时间, 常用的日志格式格式
 * <li><b>%u</b> - 已验证的远程用户
 * <li><b>%U</b> - 请求的URL路径
 * <li><b>%v</b> - 本地服务器的名称
 * <li><b>%D</b> - 处理请求的时间, 毫秒
 * <li><b>%T</b> - 处理请求的时间, 秒
 * </ul>
 * <p>此外, 调用者可以为常用模式指定下列别名之一:</p>
 * <ul>
 * <li><b>common</b> - <code>%h %l %u %t "%r" %s %b</code>
 * <li><b>combined</b> -
 *   <code>%h %l %u %t "%r" %s %b "%{Referer}i" "%{User-Agent}i"</code>
 * </ul>
 *
 * <p>
 * 还支持从cookie中写入信息, 输入标头, ServletRequest中的Session或其它.<br>
 * 它是仿照Apache语法建模的:
 * <ul>
 * <li><code>%{xxx}i</code> 输入标题
 * <li><code>%{xxx}c</code> 对于特定的cookie
 * <li><code>%{xxx}r</code> xxx ServletRequest的属性
 * <li><code>%{xxx}s</code> xxx HttpSession的属性
 * </ul>
 * </p>
 *
 * <p>
 * 还支持条件日志记录. 这可以用<code>condition</code>属性.
 * 如果从ServletRequest.getAttribute(condition)返回值非空值. 将跳过日志记录.
 * </p>
 */
public class AccessLogValve extends ValveBase implements Lifecycle {

    // ----------------------------------------------------------- Constructors

    public AccessLogValve() {
        super();
        setPattern("common");
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 当前打开日志文件的日期, 或零长度字符串，如果没有打开的日志文件.
     */
    private String dateStamp = "";


    /**
     * 创建日志文件的目录.
     */
    private String directory = "logs";


    /**
     * 此实现的描述性信息.
     */
    protected static final String info =
        "org.apache.catalina.valves.AccessLogValve/1.0";


    /**
     * 生命周期事件支持
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
    private StringManager sm =
        StringManager.getManager(Constants.Package);


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
     * 取3位小数格式的时间.
     */
     private DecimalFormat timeTakenFormatter = null;


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
     * 系统时间，这个valve 用于日志行的最后更新时间.
     */
    private Date currentDate = null;


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

        if (this.pattern.equals(Constants.AccessLog.COMMON_PATTERN))
            common = true;
        else
            common = false;

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
     * 设置日期格式基于日期的日志旋转
     */
    public void setFileDateFormat(String fileDateFormat) {
        this.fileDateFormat =  fileDateFormat;
    }

    // --------------------------------------------------------- Public Methods


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
        long t1=System.currentTimeMillis();

        getNext().invoke(request, response);

        long t2=System.currentTimeMillis();
        long time=t2-t1;

        if (condition!=null &&
                null!=request.getRequest().getAttribute(condition)) {
            return;
        }


        Date date = getDate();
        StringBuffer result = new StringBuffer();

        // Check to see if we should log using the "common" access log pattern
        if (common || combined) {
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

            result.append("[");
            result.append(dayFormatter.format(date));           // Day
            result.append('/');
            result.append(lookup(monthFormatter.format(date))); // Month
            result.append('/');
            result.append(yearFormatter.format(date));          // Year
            result.append(':');
            result.append(timeFormatter.format(date));          // Time
            result.append(space);
            result.append(getTimeZone(date));                   // Time Zone
            result.append("] \"");

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

        } else {
            // Generate a message based on the defined pattern
            boolean replace = false;
            for (int i = 0; i < pattern.length(); i++) {
                char ch = pattern.charAt(i);
                if (replace) {
                    /* For code that processes {, the behavior will be ... if I
                     * do not enounter a closing } - then I ignore the {
                     */
                    if ('{' == ch){
                        StringBuffer name = new StringBuffer();
                        int j = i + 1;
                        for(;j < pattern.length() && '}' != pattern.charAt(j); j++) {
                            name.append(pattern.charAt(j));
                        }
                        if (j+1 < pattern.length()) {
                            /* the +1 was to account for } which we increment now */
                            j++;
                            result.append(replace(name.toString(),
                                                pattern.charAt(j),
                                                request,
                                                response));
                            i=j; /*Since we walked more than one character*/
                        } else {
                            //D'oh - end of string - pretend we never did this
                            //and do processing the "old way"
                            result.append(replace(ch, date, request, response, time));
                        }
                    } else {
                        result.append(replace(ch, date, request, response,time ));
                    }
                    replace = false;
                } else if (ch == '%') {
                    replace = true;
                } else {
                    result.append(ch);
                }
            }
        }
        log(result.toString(), date);
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
     * @param date the current Date object (所以这个方法不需要创建一个新的)
     */
    public void log(String message, Date date) {

        if (rotatable){
            // Only do a logfile switch check once a second, max.
            long systime = System.currentTimeMillis();
            if ((systime - rotationLastChecked) > 1000) {

                // We need a new currentDate
                currentDate = new Date(systime);
                rotationLastChecked = systime;

                // Check for a change of date
                String tsDate = dateFormatter.format(currentDate);

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

        // 打开当前日志文件
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
            writer = new PrintWriter(new FileWriter(pathname, true), true);
        } catch (IOException e) {
            writer = null;
        }
    }


    /**
     * 返回指定字符的替换文本.
     *
     * @param pattern 识别所需文本的模式字符
     * @param date 当前日期，以便此方法不需要创建
     * @param request Request being processed
     * @param response Response being processed
     */
    private String replace(char pattern, Date date, Request request,
                           Response response, long time) {

        String value = null;

        if (pattern == 'a') {
            value = request.getRemoteAddr();
        } else if (pattern == 'A') {
            try {
                value = InetAddress.getLocalHost().getHostAddress();
            } catch(Throwable e){
                value = "127.0.0.1";
            }
        } else if (pattern == 'b') {
            int length = response.getContentCount();
            if (length <= 0)
                value = "-";
            else
                value = "" + length;
        } else if (pattern == 'B') {
            value = "" + response.getContentLength();
        } else if (pattern == 'h') {
            value = request.getRemoteHost();
        } else if (pattern == 'H') {
            value = request.getProtocol();
        } else if (pattern == 'l') {
            value = "-";
        } else if (pattern == 'm') {
            if (request != null)
                value = request.getMethod();
            else
                value = "";
        } else if (pattern == 'p') {
            value = "" + request.getServerPort();
        } else if (pattern == 'D') {
                    value = "" + time;
        } else if (pattern == 'q') {
            String query = null;
            if (request != null)
                query = request.getQueryString();
            if (query != null)
                value = "?" + query;
            else
                value = "";
        } else if (pattern == 'r') {
            StringBuffer sb = new StringBuffer();
            if (request != null) {
                sb.append(request.getMethod());
                sb.append(space);
                sb.append(request.getRequestURI());
                if (request.getQueryString() != null) {
                    sb.append('?');
                    sb.append(request.getQueryString());
                }
                sb.append(space);
                sb.append(request.getProtocol());
            } else {
                sb.append("- - ");
                sb.append(request.getProtocol());
            }
            value = sb.toString();
        } else if (pattern == 'S') {
            if (request != null)
                if (request.getSession(false) != null)
                    value = request.getSessionInternal(false).getIdInternal();
                else value = "-";
            else
                value = "-";
        } else if (pattern == 's') {
            if (response != null)
                value = "" + response.getStatus();
            else
                value = "-";
        } else if (pattern == 't') {
            StringBuffer temp = new StringBuffer("[");
            temp.append(dayFormatter.format(date));             // Day
            temp.append('/');
            temp.append(lookup(monthFormatter.format(date)));   // Month
            temp.append('/');
            temp.append(yearFormatter.format(date));            // Year
            temp.append(':');
            temp.append(timeFormatter.format(date));            // Time
            temp.append(' ');
            temp.append(getTimeZone(date));                     // Timezone
            temp.append(']');
            value = temp.toString();
        } else if (pattern == 'T') {
            value = timeTakenFormatter.format(time/1000d);
        } else if (pattern == 'u') {
            if (request != null)
                value = request.getRemoteUser();
            if (value == null)
                value = "-";
        } else if (pattern == 'U') {
            if (request != null)
                value = request.getRequestURI();
            else
                value = "-";
        } else if (pattern == 'v') {
            value = request.getServerName();
        } else {
            value = "???" + pattern + "???";
        }

        if (value == null)
            return ("");
        else
            return (value);
    }


    /**
     * 返回指定的"header/parameter"替换文本.
     *
     * @param header The header/parameter to get
     * @param type Where to get it from i=input,c=cookie,r=ServletRequest,s=Session
     * @param request Request being processed
     * @param response Response being processed
     */
    private String replace(String header, char type, Request request,
                           Response response) {

        Object value = null;

        switch (type) {
            case 'i':
                if (null != request)
                    value = request.getHeader(header);
                else
                    value= "??";
                break;
/*
            // Someone please make me work
            case 'o':
                break;
*/
            case 'c':
                 Cookie[] c = request.getCookies();
                 for (int i=0; c != null && i < c.length; i++){
                     if (header.equals(c[i].getName())){
                         value = c[i].getValue();
                         break;
                     }
                 }
                break;
            case 'r':
                if (null != request)
                    value = request.getAttribute(header);
                else
                    value= "??";
                break;
            case 's':
                if (null != request) {
                    HttpSession sess = request.getSession(false);
                    if (null != sess)
                        value = sess.getAttribute(header);
                }
               break;
            default:
                value = "???";
        }

        /* try catch in case toString() barfs */
        try {
            if (value!=null)
                if (value instanceof String)
                    return (String)value;
                else
                    return value.toString();
            else
               return "-";
        } catch(Throwable e) {
            return "-";
        }
    }


    /**
     * 此方法返回一个精确到一秒钟的日期对象. 
     * 如果一个线程调用这个方法来获取而且还不到1秒, 因为创建了一个新的日期, 这个方法简单地给出相同的日期，这样系统就不会花时间不必要地创建日期对象.
     */
    private Date getDate() {
        if(currentDate == null) {
        currentDate = new Date();
        } else {
          // Only create a new Date once per second, max.
          long systime = System.currentTimeMillis();
          if ((systime - currentDate.getTime()) > 1000) {
              currentDate = new Date(systime);
          }
        }
        return currentDate;
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
        currentDate = new Date();
        dateStamp = dateFormatter.format(currentDate);
        timeTakenFormatter = new DecimalFormat("0.000");

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
