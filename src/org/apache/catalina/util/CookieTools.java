package org.apache.catalina.util;

import java.text.*;
import java.util.*;

import javax.servlet.http.Cookie;

/**
 * Cookie utils - 生成Cookie标头, etc
 */
public class CookieTools {

    /**返回标头名称以设置cookie, 基于cookie版本
     */
    public static String getCookieHeaderName(Cookie cookie) {
        int version = cookie.getVersion();

        if (version == 1) {
            return "Set-Cookie2";
        } else {
            return "Set-Cookie";
        }
    }

    /** 返回用于设置此cookie的标头值
     *  @deprecated Use StringBuffer version
     */
    public static String getCookieHeaderValue(Cookie cookie) {
        StringBuffer buf = new StringBuffer();
        getCookieHeaderValue( cookie, buf );
        return buf.toString();
    }

    /** 返回用于设置此cookie的标头值
     */
    public static void getCookieHeaderValue(Cookie cookie, StringBuffer buf) {
        int version = cookie.getVersion();

        // 这部分对所有的cookie都是一样的
        String name = cookie.getName();     // Avoid NPE on malformed cookies
        if (name == null)
            name = "";
        String value = cookie.getValue();
        if (value == null)
            value = "";
        
        buf.append(name);
        buf.append("=");
        maybeQuote(version, buf, value);

        // 添加版本1特定信息
        if (version == 1) {
            // Version=1 ... required
            buf.append ("; Version=1");

            // Comment=comment
            if (cookie.getComment() != null) {
                buf.append ("; Comment=");
                maybeQuote (version, buf, cookie.getComment());
            }
        }

        // add domain information, if present
        if (cookie.getDomain() != null) {
            buf.append("; Domain=");
            maybeQuote (version, buf, cookie.getDomain());
        }

        // Max-Age=secs/Discard ... or use old "Expires" format
        if (cookie.getMaxAge() >= 0) {
            if (version == 0) {
                buf.append ("; Expires=");
                if (cookie.getMaxAge() == 0)
                    DateTool.oldCookieFormat.format(new Date(10000), buf,
                                                    new FieldPosition(0));
                else
                    DateTool.oldCookieFormat.format
                        (new Date( System.currentTimeMillis() +
                                   cookie.getMaxAge() *1000L), buf,
                         new FieldPosition(0));
            } else {
                buf.append ("; Max-Age=");
                buf.append (cookie.getMaxAge());
            }
        } else if (version == 1)
          buf.append ("; Discard");

        // Path=path
        if (cookie.getPath() != null) {
            buf.append ("; Path=");
            maybeQuote (version, buf, cookie.getPath());
        }

        // Secure
        if (cookie.getSecure()) {
          buf.append ("; Secure");
        }
    }

    static void maybeQuote (int version, StringBuffer buf,
                                    String value)
    {
        if (version == 0 || isToken (value))
            buf.append (value);
        else {
            buf.append ('"');
            buf.append (value);
            buf.append ('"');
        }
    }

    // from RFC 2068, token special case characters
    private static final String tspecials = "()<>@,;:\\\"/[]?={} \t";

    /*
     * 返回true，如果字符串计数为 HTTP/1.1 "token".
     */
    private static boolean isToken (String value) {
        int len = value.length ();

        for (int i = 0; i < len; i++) {
            char c = value.charAt (i);

            if (c < 0x20 || c >= 0x7f || tspecials.indexOf (c) != -1)
              return false;
        }
        return true;
    }
}
