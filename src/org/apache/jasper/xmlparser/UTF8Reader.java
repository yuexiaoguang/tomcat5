package org.apache.jasper.xmlparser;

import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.UTFDataFormatException;
import org.apache.jasper.compiler.Localizer;

public class UTF8Reader extends Reader {

    private org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( UTF8Reader.class );
    

    /** 默认字节缓冲区大小(2048). */
    public static final int DEFAULT_BUFFER_SIZE = 2048;

    /** Debug read. */
    private static final boolean DEBUG_READ = false;

    /** Input stream. */
    protected InputStream fInputStream;

    /** Byte buffer. */
    protected byte[] fBuffer;

    /** 偏移到缓冲区. */
    protected int fOffset;

    /** 代理项字符. */
    private int fSurrogate = -1;


    /** 
     * @param inputStream 输入流.
     * @param size        初始缓冲区大小.
     */
    public UTF8Reader(InputStream inputStream, int size) {
        fInputStream = inputStream;
        fBuffer = new byte[size];
    }


    /**
     * 读取单个字符. 此方法将阻塞直到字符可用, 或者I/O 错误发生, 或到达流的末尾.
     *
     * <p>子类可以覆盖整个方法来支持高效的单字符输入.
     *
     * @return  读取的字符, 作为一个0 到 127的integer (<tt>0x00-0x7f</tt>), 或者-1 如果流的结尾已经到达
     *
     * @exception  IOException  I/O 错误发生
     */
    public int read() throws IOException {

        // 解码字符
        int c = fSurrogate;
        if (fSurrogate == -1) {
            // NOTE: 如果最后一个块读取剩余字节，则我们将索引用于缓冲区. -Ac
            int index = 0;

            // 获取第一个字节
            int b0 = index == fOffset 
                   ? fInputStream.read() : fBuffer[index++] & 0x00FF;
            if (b0 == -1) {
                return -1;
            }

            // UTF-8:   [0xxx xxxx]
            // Unicode: [0000 0000] [0xxx xxxx]
            if (b0 < 0x80) {
                c = (char)b0;
            }

            // UTF-8:   [110y yyyy] [10xx xxxx]
            // Unicode: [0000 0yyy] [yyxx xxxx]
            else if ((b0 & 0xE0) == 0xC0) {
                int b1 = index == fOffset 
                       ? fInputStream.read() : fBuffer[index++] & 0x00FF;
                if (b1 == -1) {
                    expectedByte(2, 2);
                }
                if ((b1 & 0xC0) != 0x80) {
                    invalidByte(2, 2, b1);
                }
                c = ((b0 << 6) & 0x07C0) | (b1 & 0x003F);
            }

            // UTF-8:   [1110 zzzz] [10yy yyyy] [10xx xxxx]
            // Unicode: [zzzz yyyy] [yyxx xxxx]
            else if ((b0 & 0xF0) == 0xE0) {
                int b1 = index == fOffset
                       ? fInputStream.read() : fBuffer[index++] & 0x00FF;
                if (b1 == -1) {
                    expectedByte(2, 3);
                }
                if ((b1 & 0xC0) != 0x80) {
                    invalidByte(2, 3, b1);
                }
                int b2 = index == fOffset 
                       ? fInputStream.read() : fBuffer[index++] & 0x00FF;
                if (b2 == -1) {
                    expectedByte(3, 3);
                }
                if ((b2 & 0xC0) != 0x80) {
                    invalidByte(3, 3, b2);
                }
                c = ((b0 << 12) & 0xF000) | ((b1 << 6) & 0x0FC0) |
                    (b2 & 0x003F);
            }

            // UTF-8:   [1111 0uuu] [10uu zzzz] [10yy yyyy] [10xx xxxx]*
            // Unicode: [1101 10ww] [wwzz zzyy] (high surrogate)
            //          [1101 11yy] [yyxx xxxx] (low surrogate)
            //          * uuuuu = wwww + 1
            else if ((b0 & 0xF8) == 0xF0) {
                int b1 = index == fOffset 
                       ? fInputStream.read() : fBuffer[index++] & 0x00FF;
                if (b1 == -1) {
                    expectedByte(2, 4);
                }
                if ((b1 & 0xC0) != 0x80) {
                    invalidByte(2, 3, b1);
                }
                int b2 = index == fOffset 
                       ? fInputStream.read() : fBuffer[index++] & 0x00FF;
                if (b2 == -1) {
                    expectedByte(3, 4);
                }
                if ((b2 & 0xC0) != 0x80) {
                    invalidByte(3, 3, b2);
                }
                int b3 = index == fOffset 
                       ? fInputStream.read() : fBuffer[index++] & 0x00FF;
                if (b3 == -1) {
                    expectedByte(4, 4);
                }
                if ((b3 & 0xC0) != 0x80) {
                    invalidByte(4, 4, b3);
                }
                int uuuuu = ((b0 << 2) & 0x001C) | ((b1 >> 4) & 0x0003);
                if (uuuuu > 0x10) {
                    invalidSurrogate(uuuuu);
                }
                int wwww = uuuuu - 1;
                int hs = 0xD800 | 
                         ((wwww << 6) & 0x03C0) | ((b1 << 2) & 0x003C) | 
                         ((b2 >> 4) & 0x0003);
                int ls = 0xDC00 | ((b2 << 6) & 0x03C0) | (b3 & 0x003F);
                c = hs;
                fSurrogate = ls;
            } else {
                invalidByte(1, 1, b0);
            }
        } else {
            fSurrogate = -1;
        }

        // 返回字符
        if (DEBUG_READ) {
            if (log.isDebugEnabled())
                log.debug("read(): 0x"+Integer.toHexString(c));
        }
        return c;
    }

    /**
     * 将字符读入数组的一部分. 此方法将阻塞，直到某些输入可用, 发生I/O 错误, 或者到达流的结尾.
     *
     * @param      ch  目的缓冲区
     * @param      offset 开始存储字符的偏移量
     * @param      length 要读取的字符的最大数目
     *
     * @return     读取的字符数, 或者-1 如果流的末尾已经到达
     *
     * @exception  IOException  如果发生I/O 错误
     */
    public int read(char ch[], int offset, int length) throws IOException {

        // 处理代理
        int out = offset;
        if (fSurrogate != -1) {
            ch[offset + 1] = (char)fSurrogate;
            fSurrogate = -1;
            length--;
            out++;
        }

        // 读取的字节数
        int count = 0;
        if (fOffset == 0) {
            // 调整读取长度
            if (length > fBuffer.length) {
                length = fBuffer.length;
            }

            // 执行读操作
            count = fInputStream.read(fBuffer, 0, length);
            if (count == -1) {
                return -1;
            }
            count += out - offset;
        }

        // 跳过读取; 最后一个字符出错了
        // NOTE: 具有零以外的偏移值意味着在最后一个字符读取时出现错误. 在这种情况下, 我们跳过了读，所以我们不消耗任何字节过去的错误.
        // 通过在下一个块读的错误信号，我们允许该方法返回上一个块读取的最有效字符. -Ac
        else {
            count = fOffset;
            fOffset = 0;
        }

        // 转换字节为字符
        final int total = count;
        for (int in = 0; in < total; in++) {
            int b0 = fBuffer[in] & 0x00FF;

            // UTF-8:   [0xxx xxxx]
            // Unicode: [0000 0000] [0xxx xxxx]
            if (b0 < 0x80) {
                ch[out++] = (char)b0;
                continue;
            }

            // UTF-8:   [110y yyyy] [10xx xxxx]
            // Unicode: [0000 0yyy] [yyxx xxxx]
            if ((b0 & 0xE0) == 0xC0) {
                int b1 = -1;
                if (++in < total) { 
                    b1 = fBuffer[in] & 0x00FF; 
                }
                else {
                    b1 = fInputStream.read();
                    if (b1 == -1) {
                        if (out > offset) {
                            fBuffer[0] = (byte)b0;
                            fOffset = 1;
                            return out - offset;
                        }
                        expectedByte(2, 2);
                    }
                    count++;
                }
                if ((b1 & 0xC0) != 0x80) {
                    if (out > offset) {
                        fBuffer[0] = (byte)b0;
                        fBuffer[1] = (byte)b1;
                        fOffset = 2;
                        return out - offset;
                    }
                    invalidByte(2, 2, b1);
                }
                int c = ((b0 << 6) & 0x07C0) | (b1 & 0x003F);
                ch[out++] = (char)c;
                count -= 1;
                continue;
            }

            // UTF-8:   [1110 zzzz] [10yy yyyy] [10xx xxxx]
            // Unicode: [zzzz yyyy] [yyxx xxxx]
            if ((b0 & 0xF0) == 0xE0) {
                int b1 = -1;
                if (++in < total) { 
                    b1 = fBuffer[in] & 0x00FF; 
                }
                else {
                    b1 = fInputStream.read();
                    if (b1 == -1) {
                        if (out > offset) {
                            fBuffer[0] = (byte)b0;
                            fOffset = 1;
                            return out - offset;
                        }
                        expectedByte(2, 3);
                    }
                    count++;
                }
                if ((b1 & 0xC0) != 0x80) {
                    if (out > offset) {
                        fBuffer[0] = (byte)b0;
                        fBuffer[1] = (byte)b1;
                        fOffset = 2;
                        return out - offset;
                    }
                    invalidByte(2, 3, b1);
                }
                int b2 = -1;
                if (++in < total) { 
                    b2 = fBuffer[in] & 0x00FF; 
                }
                else {
                    b2 = fInputStream.read();
                    if (b2 == -1) {
                        if (out > offset) {
                            fBuffer[0] = (byte)b0;
                            fBuffer[1] = (byte)b1;
                            fOffset = 2;
                            return out - offset;
                        }
                        expectedByte(3, 3);
                    }
                    count++;
                }
                if ((b2 & 0xC0) != 0x80) {
                    if (out > offset) {
                        fBuffer[0] = (byte)b0;
                        fBuffer[1] = (byte)b1;
                        fBuffer[2] = (byte)b2;
                        fOffset = 3;
                        return out - offset;
                    }
                    invalidByte(3, 3, b2);
                }
                int c = ((b0 << 12) & 0xF000) | ((b1 << 6) & 0x0FC0) |
                        (b2 & 0x003F);
                ch[out++] = (char)c;
                count -= 2;
                continue;
            }

            // UTF-8:   [1111 0uuu] [10uu zzzz] [10yy yyyy] [10xx xxxx]*
            // Unicode: [1101 10ww] [wwzz zzyy] (high surrogate)
            //          [1101 11yy] [yyxx xxxx] (low surrogate)
            //          * uuuuu = wwww + 1
            if ((b0 & 0xF8) == 0xF0) {
                int b1 = -1;
                if (++in < total) { 
                    b1 = fBuffer[in] & 0x00FF; 
                }
                else {
                    b1 = fInputStream.read();
                    if (b1 == -1) {
                        if (out > offset) {
                            fBuffer[0] = (byte)b0;
                            fOffset = 1;
                            return out - offset;
                        }
                        expectedByte(2, 4);
                    }
                    count++;
                }
                if ((b1 & 0xC0) != 0x80) {
                    if (out > offset) {
                        fBuffer[0] = (byte)b0;
                        fBuffer[1] = (byte)b1;
                        fOffset = 2;
                        return out - offset;
                    }
                    invalidByte(2, 4, b1);
                }
                int b2 = -1;
                if (++in < total) { 
                    b2 = fBuffer[in] & 0x00FF; 
                }
                else {
                    b2 = fInputStream.read();
                    if (b2 == -1) {
                        if (out > offset) {
                            fBuffer[0] = (byte)b0;
                            fBuffer[1] = (byte)b1;
                            fOffset = 2;
                            return out - offset;
                        }
                        expectedByte(3, 4);
                    }
                    count++;
                }
                if ((b2 & 0xC0) != 0x80) {
                    if (out > offset) {
                        fBuffer[0] = (byte)b0;
                        fBuffer[1] = (byte)b1;
                        fBuffer[2] = (byte)b2;
                        fOffset = 3;
                        return out - offset;
                    }
                    invalidByte(3, 4, b2);
                }
                int b3 = -1;
                if (++in < total) { 
                    b3 = fBuffer[in] & 0x00FF; 
                }
                else {
                    b3 = fInputStream.read();
                    if (b3 == -1) {
                        if (out > offset) {
                            fBuffer[0] = (byte)b0;
                            fBuffer[1] = (byte)b1;
                            fBuffer[2] = (byte)b2;
                            fOffset = 3;
                            return out - offset;
                        }
                        expectedByte(4, 4);
                    }
                    count++;
                }
                if ((b3 & 0xC0) != 0x80) {
                    if (out > offset) {
                        fBuffer[0] = (byte)b0;
                        fBuffer[1] = (byte)b1;
                        fBuffer[2] = (byte)b2;
                        fBuffer[3] = (byte)b3;
                        fOffset = 4;
                        return out - offset;
                    }
                    invalidByte(4, 4, b2);
                }

                // 将字节解码为代理字符
                int uuuuu = ((b0 << 2) & 0x001C) | ((b1 >> 4) & 0x0003);
                if (uuuuu > 0x10) {
                    invalidSurrogate(uuuuu);
                }
                int wwww = uuuuu - 1;
                int zzzz = b1 & 0x000F;
                int yyyyyy = b2 & 0x003F;
                int xxxxxx = b3 & 0x003F;
                int hs = 0xD800 | ((wwww << 6) & 0x03C0) | (zzzz << 2) | (yyyyyy >> 4);
                int ls = 0xDC00 | ((yyyyyy << 6) & 0x03C0) | xxxxxx;

                // 设置字符
                ch[out++] = (char)hs;
                ch[out++] = (char)ls;
                count -= 2;
                continue;
            }

            // error
            if (out > offset) {
                fBuffer[0] = (byte)b0;
                fOffset = 1;
                return out - offset;
            }
            invalidByte(1, 1, b0);
        }

        // 返回转换字符的数量
        if (DEBUG_READ) {
            if (log.isDebugEnabled())
                log.debug("read(char[],"+offset+','+length+"): count="+count);
        }
        return count;
    }

    /**
     * 跳过字符. 此方法将阻塞，直到某些输入可用, 发生I/O 错误, 或者到达流的结尾.
     *
     * @param  n  跳过的字符数
     *
     * @return   实际跳过的字符数
     *
     * @exception  IOException  如果发生I/O 错误
     */
    public long skip(long n) throws IOException {

        long remaining = n;
        final char[] ch = new char[fBuffer.length];
        do {
            int length = ch.length < remaining ? ch.length : (int)remaining;
            int count = read(ch, 0, length);
            if (count > 0) {
                remaining -= count;
            }
            else {
                break;
            }
        } while (remaining > 0);

        long skipped = n - remaining;
        return skipped;

    }

    /**
     * 判断此流是否已准备好读取.
     *
     * @return True 如果下一个read()保证不阻止输入, 否则false. 注意返回 false 不保证下一个读将阻塞.
     *
     * @exception  IOException  如果发生I/O 错误
     */
    public boolean ready() throws IOException {
	    return false;
    }

    /**
     * 判断此流是否支持 mark() 操作.
     */
    public boolean markSupported() {
	    return false;
    }

    /**
     * 在流中标出目前的位置. 随后调用 reset() 试图将流重新定位到这一点. 并非所有字符输入流都支持 mark() 操作.
     *
     * @param  readAheadLimit  限制在保留标记时可能读取的字符数. 读了这么多字符之后, 试图重置流可能失败.
     *
     * @exception  IOException  如果流不支持 mark(), 或者发生I/O 错误
     */
    public void mark(int readAheadLimit) throws IOException {
    	throw new IOException(
                Localizer.getMessage("jsp.error.xml.operationNotSupported",
				     "mark()", "UTF-8"));
    }

    /**
     * 重置流. 如果流已标记, 然后试着在标记处重新定位. 如果流没有被标记, 然后尝试以某种特定于特定流的方式重新设置它,
     * 例如，通过将其重新定位到起始点. 并非所有字符输入流都支持 reset()操作, 一些支持reset() 但不支持 mark().
     *
     * @exception  IOException  如果流没有被标记, 或者如果标记失效了, 或者如果流不支持reset(), 或者发生其它I/O 错误
     */
    public void reset() throws IOException {
        fOffset = 0;
        fSurrogate = -1;
    }

    /**
     * 关闭流. 一旦流关闭, 进一步read(), ready(), mark(), reset() 调用将抛出一个 IOException.
     * 关闭先前关闭的流, 但是, 有没有效果.
     *
     * @exception  IOException  如果发生I/O 错误
     */
    public void close() throws IOException {
        fInputStream.close();
    }

    /** 为预期的字节抛出异常. */
    private void expectedByte(int position, int count) throws UTFDataFormatException {

        throw new UTFDataFormatException(Localizer.getMessage("jsp.error.xml.expectedByte",
				     Integer.toString(position),
				     Integer.toString(count)));

    }

    /** 为无效字节抛出异常. */
    private void invalidByte(int position, int count, int c) throws UTFDataFormatException {

        throw new UTFDataFormatException(
                Localizer.getMessage("jsp.error.xml.invalidByte",
				     Integer.toString(position),
				     Integer.toString(count)));
    }

    /** 为无效的代理位抛出异常. */
    private void invalidSurrogate(int uuuuu) throws UTFDataFormatException {
        
        throw new UTFDataFormatException(Localizer.getMessage("jsp.error.xml.invalidHighSurrogate",
				     Integer.toHexString(uuuuu)));
    }
}
