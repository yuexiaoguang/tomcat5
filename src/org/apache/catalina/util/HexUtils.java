package org.apache.catalina.util;

import java.io.ByteArrayOutputStream;

/**
 * 用于处理将字节数组转换为十六进制数字字符串的实用方法库.
 */
public final class HexUtils {

    // Table for HEX to DEC byte translation
    public static final int[] DEC = {
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        00, 01, 02, 03, 04, 05, 06, 07,  8,  9, -1, -1, -1, -1, -1, -1,
        -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, 10, 11, 12, 13, 14, 15, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    };



    /**
     * The string manager for this package.
     */
    private static StringManager sm =
        StringManager.getManager("org.apache.catalina.util");


    /**
     * 通过将每个十六进制数字编码为字节，将十六进制数字转换成相应的字节数组.
     *
     * @param digits 十六进制数的表示
     *
     * @exception IllegalArgumentException 如果找到无效的十六进制数字, 或者输入字符串包含奇数个十六进制数字
     */
    public static byte[] convert(String digits) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i = 0; i < digits.length(); i += 2) {
            char c1 = digits.charAt(i);
            if ((i+1) >= digits.length())
                throw new IllegalArgumentException
                    (sm.getString("hexUtil.odd"));
            char c2 = digits.charAt(i + 1);
            byte b = 0;
            if ((c1 >= '0') && (c1 <= '9'))
                b += ((c1 - '0') * 16);
            else if ((c1 >= 'a') && (c1 <= 'f'))
                b += ((c1 - 'a' + 10) * 16);
            else if ((c1 >= 'A') && (c1 <= 'F'))
                b += ((c1 - 'A' + 10) * 16);
            else
                throw new IllegalArgumentException
                    (sm.getString("hexUtil.bad"));
            if ((c2 >= '0') && (c2 <= '9'))
                b += (c2 - '0');
            else if ((c2 >= 'a') && (c2 <= 'f'))
                b += (c2 - 'a' + 10);
            else if ((c2 >= 'A') && (c2 <= 'F'))
                b += (c2 - 'A' + 10);
            else
                throw new IllegalArgumentException
                    (sm.getString("hexUtil.bad"));
            baos.write(b);
        }
        return (baos.toByteArray());

    }


    /**
     * 将字节数组转换为可打印格式，其中包含十六进制数字字符的字符串(two per byte).
     *
     * @param bytes Byte array representation
     */
    public static String convert(byte bytes[]) {

        StringBuffer sb = new StringBuffer(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(convertDigit((int) (bytes[i] >> 4)));
            sb.append(convertDigit((int) (bytes[i] & 0x0f)));
        }
        return (sb.toString());

    }

    /**
     *将4个十六进制数字转换为int，并返回转换字节数.
     *
     * @param hex 包含四个十六进制数字的字节数组
     *
     * @exception IllegalArgumentException 如果包含一个无效的十六进制数字
     */
    public static int convert2Int( byte[] hex ) {
        // Code from Ajp11, from Apache's JServ

        // assert b.length==4
        // assert valid data
        int len;
        if(hex.length < 4 ) return 0;
        if( DEC[hex[0]]<0 )
            throw new IllegalArgumentException(sm.getString("hexUtil.bad"));
        len = DEC[hex[0]];
        len = len << 4;
        if( DEC[hex[1]]<0 )
            throw new IllegalArgumentException(sm.getString("hexUtil.bad"));
        len += DEC[hex[1]];
        len = len << 4;
        if( DEC[hex[2]]<0 )
            throw new IllegalArgumentException(sm.getString("hexUtil.bad"));
        len += DEC[hex[2]];
        len = len << 4;
        if( DEC[hex[3]]<0 )
            throw new IllegalArgumentException(sm.getString("hexUtil.bad"));
        len += DEC[hex[3]];
        return len;
    }



    /**
     * 转换指定的值(0 .. 15)到相应的十六进制数字.
     *
     * @param value 要转换的值
     */
    private static char convertDigit(int value) {
        value &= 0x0f;
        if (value >= 10)
            return ((char) (value - 10 + 'a'));
        else
            return ((char) (value + '0'));
    }
}
