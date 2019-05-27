package org.apache.catalina.util;


/**
 * 将MD5摘要编码为字符串.
 * <p>
 * 128位MD5哈希被转换成一个32字符的长字符串.
 * 字符串的每个字符都是4位摘要的十六进制表示.
 */
public final class MD5Encoder {


    // ----------------------------------------------------- Instance Variables


    private static final char[] hexadecimal =
    {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
     'a', 'b', 'c', 'd', 'e', 'f'};


    // --------------------------------------------------------- Public Methods


    /**
     * 128位(16 字节)MD5哈希被转换成一个32字符的长字符串.
     *
     * @param binaryData 包含摘要的数组
     * @return Encoded MD5, or null if encoding failed
     */
    public String encode( byte[] binaryData ) {

        if (binaryData.length != 16)
            return null;

        char[] buffer = new char[32];

        for (int i=0; i<16; i++) {
            int low = (int) (binaryData[i] & 0x0f);
            int high = (int) ((binaryData[i] & 0xf0) >> 4);
            buffer[i*2] = hexadecimal[high];
            buffer[i*2 + 1] = hexadecimal[low];
        }
        return new String(buffer);
    }
}

