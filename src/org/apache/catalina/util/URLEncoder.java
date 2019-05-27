package org.apache.catalina.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.BitSet;

/**
 * 这个类和java.net.URLEncoder非常相似.
 *
 * 不幸的是, java.net.URLEncoder 没有办法指定哪些字符不应该被编码.
 *
 * 这些代码从 DefaultServlet.java移动过来的
 */
public class URLEncoder {
    protected static final char[] hexadecimal =
    {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
     'A', 'B', 'C', 'D', 'E', 'F'};

    //包含安全字符集的数组
    protected BitSet safeCharacters = new BitSet(256);

    public URLEncoder() {
        for (char i = 'a'; i <= 'z'; i++) {
            addSafeCharacter(i);
        }
        for (char i = 'A'; i <= 'Z'; i++) {
            addSafeCharacter(i);
        }
        for (char i = '0'; i <= '9'; i++) {
            addSafeCharacter(i);
        }
    }

    public void addSafeCharacter( char c ) {
	safeCharacters.set( c );
    }

    public String encode( String path ) {
        int maxBytesPerChar = 10;
        int caseDiff = ('a' - 'A');
        StringBuffer rewrittenPath = new StringBuffer(path.length());
        ByteArrayOutputStream buf = new ByteArrayOutputStream(maxBytesPerChar);
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(buf, "UTF8");
        } catch (Exception e) {
            e.printStackTrace();
            writer = new OutputStreamWriter(buf);
        }

        for (int i = 0; i < path.length(); i++) {
            int c = (int) path.charAt(i);
            if (safeCharacters.get(c)) {
                rewrittenPath.append((char)c);
            } else {
                //  十六进制转换前转换为外部编码
                try {
                    writer.write((char)c);
                    writer.flush();
                } catch(IOException e) {
                    buf.reset();
                    continue;
                }
                byte[] ba = buf.toByteArray();
                for (int j = 0; j < ba.length; j++) {
                    // 转换缓冲区中的每个字节
                    byte toEncode = ba[j];
                    rewrittenPath.append('%');
                    int low = (int) (toEncode & 0x0f);
                    int high = (int) ((toEncode & 0xf0) >> 4);
                    rewrittenPath.append(hexadecimal[high]);
                    rewrittenPath.append(hexadecimal[low]);
                }
                buf.reset();
            }
        }
        return rewrittenPath.toString();
    }
}
