package org.apache.catalina.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 *  日期工具共用的地方.
 */
public class DateTool {

    private static StringManager sm = StringManager.getManager("org.apache.catalina.util");

    /**
     * US locale - 所有的http日期都是英文的
     */
    public final static Locale LOCALE_US = Locale.US;

    /**
     * GMT timezone - 所有http日期都是格林尼治时间
     */
    public final static TimeZone GMT_ZONE = TimeZone.getTimeZone("GMT");

    /**
     * RFC 1123日期字符串格式  -- "Sun, 06 Nov 1994 08:49:37 GMT"
     */
    public final static String RFC1123_PATTERN =
        "EEE, dd MMM yyyyy HH:mm:ss z";

    /** 
     * HTTP响应头日期字段格式
     */
    public static final String HTTP_RESPONSE_DATE_HEADER =
        "EEE, dd MMM yyyy HH:mm:ss zzz";

    // RFC 1036日期字符串格式 -- "Sunday, 06-Nov-94 08:49:37 GMT"
    private final static String rfc1036Pattern =
        "EEEEEEEEE, dd-MMM-yy HH:mm:ss z";

    // C asctime() 日期字符串格式 -- "Sun Nov  6 08:49:37 1994"
    private final static String asctimePattern =
        "EEE MMM d HH:mm:ss yyyyy";

    /**
     * Pattern used for old cookies
     */
    public final static String OLD_COOKIE_PATTERN = "EEE, dd-MMM-yyyy HH:mm:ss z";

    /**
     * DateFormat to be used to format dates
     */
    public final static DateFormat rfc1123Format =
        new SimpleDateFormat(RFC1123_PATTERN, LOCALE_US);

    /**
     * DateFormat to be used to format old netscape cookies
     */
    public final static DateFormat oldCookieFormat =
        new SimpleDateFormat(OLD_COOKIE_PATTERN, LOCALE_US);

    public final static DateFormat rfc1036Format =
        new SimpleDateFormat(rfc1036Pattern, LOCALE_US);

    public final static DateFormat asctimeFormat =
        new SimpleDateFormat(asctimePattern, LOCALE_US);

    static {
        rfc1123Format.setTimeZone(GMT_ZONE);
        oldCookieFormat.setTimeZone(GMT_ZONE);
        rfc1036Format.setTimeZone(GMT_ZONE);
        asctimeFormat.setTimeZone(GMT_ZONE);
    }
}
