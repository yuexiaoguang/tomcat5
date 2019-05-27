package org.apache.catalina.util;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.Cookie;


/**
 * 通用请求解析和编码实用方法.
 */
public final class RequestUtil {

    /**
     * 在cookie中生成可读日期的DateFormat
     */
    private static SimpleDateFormat format =
        new SimpleDateFormat(" EEEE, dd-MMM-yy kk:mm:ss zz");

    static {
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
    }


    /**
     * 按照RFC 2109对cookie进行编码. 结果字符串可以用作<code>Set-Cookie</code> header的值.
     *
     * @param cookie The cookie to encode.
     * @return A string following RFC 2109.
     */
    public static String encodeCookie(Cookie cookie) {

        StringBuffer buf = new StringBuffer( cookie.getName() );
        buf.append("=");
        buf.append(cookie.getValue());

        if (cookie.getComment() != null) {
            buf.append("; Comment=\"");
            buf.append(cookie.getComment());
            buf.append("\"");
        }

        if (cookie.getDomain() != null) {
            buf.append("; Domain=\"");
            buf.append(cookie.getDomain());
            buf.append("\"");
        }

        long age = cookie.getMaxAge();
        if (cookie.getMaxAge() >= 0) {
            buf.append("; Max-Age=\"");
            buf.append(cookie.getMaxAge());
            buf.append("\"");
        }

        if (cookie.getPath() != null) {
            buf.append("; Path=\"");
            buf.append(cookie.getPath());
            buf.append("\"");
        }

        if (cookie.getSecure()) {
            buf.append("; Secure");
        }

        if (cookie.getVersion() > 0) {
            buf.append("; Version=\"");
            buf.append(cookie.getVersion());
            buf.append("\"");
        }
        return (buf.toString());
    }


    /**
     * 过滤指定的消息字符串中HTML敏感的字符.
     * 这避免了在错误消息中经常报告的请求URL中包含JavaScript代码所造成的潜在攻击.
     *
     * @param message 要过滤的消息字符串
     */
    public static String filter(String message) {

        if (message == null)
            return (null);

        char content[] = new char[message.length()];
        message.getChars(0, message.length(), content, 0);
        StringBuffer result = new StringBuffer(content.length + 50);
        for (int i = 0; i < content.length; i++) {
            switch (content[i]) {
            case '<':
                result.append("&lt;");
                break;
            case '>':
                result.append("&gt;");
                break;
            case '&':
                result.append("&amp;");
                break;
            case '"':
                result.append("&quot;");
                break;
            default:
                result.append(content[i]);
            }
        }
        return (result.toString());
    }


    /**
     * 规范化可能具有相对值的相对URI路径("/./", "/../", 等等).
     * <strong>WARNING</strong> - 此方法仅用于规范应用程序生成的路径. 它不试图为恶意输入执行安全检查.
     *
     * @param path 相对标准化路径
     */
    public static String normalize(String path) {

        if (path == null)
            return null;

        // Create a place for the normalized path
        String normalized = path;

        if (normalized.equals("/."))
            return "/";

        // Add a leading "/" if necessary
        if (!normalized.startsWith("/"))
            normalized = "/" + normalized;

        // Resolve occurrences of "//" in the normalized path
        while (true) {
            int index = normalized.indexOf("//");
            if (index < 0)
                break;
            normalized = normalized.substring(0, index) +
                normalized.substring(index + 1);
        }

        // Resolve occurrences of "/./" in the normalized path
        while (true) {
            int index = normalized.indexOf("/./");
            if (index < 0)
                break;
            normalized = normalized.substring(0, index) +
                normalized.substring(index + 2);
        }

        // Resolve occurrences of "/../" in the normalized path
        while (true) {
            int index = normalized.indexOf("/../");
            if (index < 0)
                break;
            if (index == 0)
                return (null);  // Trying to go outside our context
            int index2 = normalized.lastIndexOf('/', index - 1);
            normalized = normalized.substring(0, index2) +
                normalized.substring(index + 3);
        }

        // Return the normalized path that we have completed
        return (normalized);

    }


    /**
     * 从指定的内容类型头解析字符编码.
     * 如果内容类型为null, 或者没有显式字符编码,返回<code>null</code>.
     *
     * @param contentType a content type header
     */
    public static String parseCharacterEncoding(String contentType) {

        if (contentType == null)
            return (null);
        int start = contentType.indexOf("charset=");
        if (start < 0)
            return (null);
        String encoding = contentType.substring(start + 8);
        int end = encoding.indexOf(';');
        if (end >= 0)
            encoding = encoding.substring(0, end);
        encoding = encoding.trim();
        if ((encoding.length() > 2) && (encoding.startsWith("\""))
            && (encoding.endsWith("\"")))
            encoding = encoding.substring(1, encoding.length() - 1);
        return (encoding.trim());
    }


    /**
     * 根据RFC 2109将cookie头解析为cookie数组.
     *
     * @param header Value of an HTTP "Cookie" header
     */
    public static Cookie[] parseCookieHeader(String header) {

        if ((header == null) || (header.length() < 1))
            return (new Cookie[0]);

        ArrayList cookies = new ArrayList();
        while (header.length() > 0) {
            int semicolon = header.indexOf(';');
            if (semicolon < 0)
                semicolon = header.length();
            if (semicolon == 0)
                break;
            String token = header.substring(0, semicolon);
            if (semicolon < header.length())
                header = header.substring(semicolon + 1);
            else
                header = "";
            try {
                int equals = token.indexOf('=');
                if (equals > 0) {
                    String name = token.substring(0, equals).trim();
                    String value = token.substring(equals+1).trim();
                    cookies.add(new Cookie(name, value));
                }
            } catch (Throwable e) {
                ;
            }
        }

        return ((Cookie[]) cookies.toArray(new Cookie[cookies.size()]));

    }


    /**
     * 将指定字符串中的请求参数追加到指定的Map. 假定从任何其他线程都不访问指定的Map, 因此不执行同步.
     * <p>
     * <strong>IMPLEMENTATION NOTE</strong>: URL解析在解析的名称和值元素上单独执行, 而不是提前在整个查询字符串上,
     * 妥善处理这种情况, 其中名称或值包含编码的"="或"&"字符, 否则将被解释为分隔符.
     *
     * @param map 累积所得参数的Map
     * @param data 包含请求参数的输入字符串
     * @param urlParameters true: 如果解析URL上的参数
     *
     * @exception IllegalArgumentException 如果数据格式错误
     */
    public static void parseParameters(Map map, String data, String encoding)
        throws UnsupportedEncodingException {

        if ((data != null) && (data.length() > 0)) {

            // 使用指定的编码从给定字符串中提取字节，这样编码就不会丢失. 如果没有指定编码, 让它使用平台默认的
            byte[] bytes = null;
            try {
                if (encoding == null) {
                    bytes = data.getBytes();
                } else {
                    bytes = data.getBytes(encoding);
                }
            } catch (UnsupportedEncodingException uee) {
            }

            parseParameters(map, bytes, encoding);
        }

    }


    /**
     * 解码并返回指定的URL编码字符串.
     * 当字节数组转换为字符串时, 使用系统默认字符编码...  这可能与其他服务器不同.
     *
     * @param str URL编码字符串
     *
     * @exception IllegalArgumentException 如果'%' 编码不是有效数字的十六进制数
     */
    public static String URLDecode(String str) {
        return URLDecode(str, null);
    }


    /**
     * 解码并返回指定的URL编码字符串
     *
     * @param str URL编码字符串
     * @param enc 要使用的编码; 如果null, 使用默认编码
     * @exception IllegalArgumentException if a '%' character is not followed
     * by a valid 2-digit hexadecimal number
     */
    public static String URLDecode(String str, String enc) {

        if (str == null)
            return (null);

        // 使用指定的编码从给定字符串中提取字节，这样编码就不会丢失. 如果没有指定编码, 让它使用平台默认的
        byte[] bytes = null;
        try {
            if (enc == null) {
                bytes = str.getBytes();
            } else {
                bytes = str.getBytes(enc);
            }
        } catch (UnsupportedEncodingException uee) {}
        return URLDecode(bytes, enc);
    }


    /**
     * 解码并返回指定的URL编码字节数组.
     *
     * @param bytes URL编码的字节数组
     * @exception IllegalArgumentException if a '%' character is not followed
     * by a valid 2-digit hexadecimal number
     */
    public static String URLDecode(byte[] bytes) {
        return URLDecode(bytes, null);
    }


    /**
     * 解码并返回指定的URL编码字节数组
     *
     * @param bytes URL编码的字节数组
     * @param enc 要使用的编码; 如果null, 使用默认编码
     * @exception IllegalArgumentException if a '%' character is not followed
     * by a valid 2-digit hexadecimal number
     */
    public static String URLDecode(byte[] bytes, String enc) {

        if (bytes == null)
            return (null);

        int len = bytes.length;
        int ix = 0;
        int ox = 0;
        while (ix < len) {
            byte b = bytes[ix++];     // Get byte to test
            if (b == '+') {
                b = (byte)' ';
            } else if (b == '%') {
                b = (byte) ((convertHexDigit(bytes[ix++]) << 4)
                            + convertHexDigit(bytes[ix++]));
            }
            bytes[ox++] = b;
        }
        if (enc != null) {
            try {
                return new String(bytes, 0, ox, enc);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new String(bytes, 0, ox);
    }


    /**
     * 将一个字节的字符值转换为十六进制数字值
     *
     * @param b the character value byte
     */
    private static byte convertHexDigit( byte b ) {
        if ((b >= '0') && (b <= '9')) return (byte)(b - '0');
        if ((b >= 'a') && (b <= 'f')) return (byte)(b - 'a' + 10);
        if ((b >= 'A') && (b <= 'F')) return (byte)(b - 'A' + 10);
        return 0;
    }


    /**
     * @param map The map to populate
     * @param name The parameter name
     * @param value The parameter value
     */
    private static void putMapEntry( Map map, String name, String value) {
        String[] newValues = null;
        String[] oldValues = (String[]) map.get(name);
        if (oldValues == null) {
            newValues = new String[1];
            newValues[0] = value;
        } else {
            newValues = new String[oldValues.length + 1];
            System.arraycopy(oldValues, 0, newValues, 0, oldValues.length);
            newValues[oldValues.length] = value;
        }
        map.put(name, newValues);
    }


    /**
     * 将指定字符串中的请求参数追加到指定的Map. 
     * 假定从任何其他线程都不访问指定的Map, 因此不执行同步.
     * <p>
     * <strong>IMPLEMENTATION NOTE</strong>: URL解析在解析的名称和值元素上单独执行, 而不是提前在整个查询字符串上,
     * 妥善处理这种情况, 其中名称或值包含编码的"="或"&"字符, 否则将被解释为分隔符.
     *
     * NOTE: 字节数组数据是用这种方法修改的. 调用者当心.
     *
     * @param map 累积所得参数的Map
     * @param data 包含请求参数的输入字符串
     * @param encoding 用于转换十六进制的编码
     *
     * @exception UnsupportedEncodingException 如果数据格式错误
     */
    public static void parseParameters(Map map, byte[] data, String encoding)
        throws UnsupportedEncodingException {

        if (data != null && data.length > 0) {
            int    pos = 0;
            int    ix = 0;
            int    ox = 0;
            String key = null;
            String value = null;
            while (ix < data.length) {
                byte c = data[ix++];
                switch ((char) c) {
                case '&':
                    value = new String(data, 0, ox, encoding);
                    if (key != null) {
                        putMapEntry(map, key, value);
                        key = null;
                    }
                    ox = 0;
                    break;
                case '=':
                    if (key == null) {
                        key = new String(data, 0, ox, encoding);
                        ox = 0;
                    } else {
                        data[ox++] = c;
                    }                   
                    break;  
                case '+':
                    data[ox++] = (byte)' ';
                    break;
                case '%':
                    data[ox++] = (byte)((convertHexDigit(data[ix++]) << 4)
                                    + convertHexDigit(data[ix++]));
                    break;
                default:
                    data[ox++] = c;
                }
            }
            //The last value does not end in '&'.  So save it now.
            if (key != null) {
                value = new String(data, 0, ox, encoding);
                putMapEntry(map, key, value);
            }
        }
    }
}
