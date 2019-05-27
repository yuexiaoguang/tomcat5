package org.apache.jasper.xmlparser;

import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;

/** 
 * UCS-2 和 UCS-4 编码的读取器. (即, 来自ISO-10646-UCS-(2|4)的编码).
 */
public class UCSReader extends Reader {

    private org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( UCSReader.class );
    
    /** 默认字节缓冲区大小(8192, 比ASCIIReader更大, 因为UCS-4编码的文件的平均值应该是ASCII编码的文件的平均值的四倍大). 
     */
    public static final int DEFAULT_BUFFER_SIZE = 8192;

    public static final short UCS2LE = 1;
    public static final short UCS2BE = 2;
    public static final short UCS4LE = 4;
    public static final short UCS4BE = 8;

    /** Input stream. */
    protected InputStream fInputStream;

    /** Byte buffer. */
    protected byte[] fBuffer;

    // 正在处理什么样的数据
    protected short fEncoding;


    /** 
     * @param inputStream 输入流.
     * @param encoding UCS2LE, UCS2BE, UCS4LE, UCS4BE其中之一.
     */
    public UCSReader(InputStream inputStream, short encoding) {
        this(inputStream, DEFAULT_BUFFER_SIZE, encoding);
    }

    /** 
     * @param inputStream 输入流.
     * @param size        初始缓冲区大小.
     * @param encoding UCS2LE, UCS2BE, UCS4LE, UCS4BE其中之一.
     */
    public UCSReader(InputStream inputStream, int size, short encoding) {
        fInputStream = inputStream;
        fBuffer = new byte[size];
        fEncoding = encoding;
    }


    /**
     * 读取单个字符. 此方法将阻塞直到字符可用, 或者发生I/O 错误, 或者到达流的结尾.
     *
     * <p>子类可以重写此方法以支持高效的单个字符输入.
     *
     * @return 字符读取, 作为一个0 到 127范围的integer(<tt>0x00-0x7f</tt>), 或者 -1 表示流结束
     *
     * @exception  IOException  如果发生I/O 错误
     */
    public int read() throws IOException { 
        int b0 = fInputStream.read() & 0xff;
        if (b0 == 0xff)
            return -1;
        int b1 = fInputStream.read() & 0xff;
        if (b1 == 0xff)
            return -1;
        if(fEncoding >=4) {
            int b2 = fInputStream.read() & 0xff;
            if (b2 == 0xff)
                return -1;
            int b3 = fInputStream.read() & 0xff;
            if (b3 == 0xff)
                return -1;
            if (log.isDebugEnabled())
                log.debug("b0 is " + (b0 & 0xff) + " b1 " + (b1 & 0xff) + " b2 " + (b2 & 0xff) + " b3 " + (b3 & 0xff));
            if (fEncoding == UCS4BE)
                return (b0<<24)+(b1<<16)+(b2<<8)+b3;
            else
                return (b3<<24)+(b2<<16)+(b1<<8)+b0;
        } else { // UCS-2
            if (fEncoding == UCS2BE)
                return (b0<<8)+b1;
            else
                return (b1<<8)+b0;
        }
    }

    /**
     * 将字符读入数组的一部分. 此方法将阻塞直到字符可用, 或者发生I/O 错误, 或者到达流的结尾.
     *
     * @param      ch     目的缓冲区
     * @param      offset 开始存储字符的偏移量
     * @param      length 要读取的字符的最大数目
     *
     * @return     读取的字符数, 或者 -1 表示流结束
     *
     * @exception  IOException  如果发生I/O 错误
     */
    public int read(char ch[], int offset, int length) throws IOException {
        int byteLength = length << ((fEncoding >= 4)?2:1);
        if (byteLength > fBuffer.length) {
            byteLength = fBuffer.length;
        }
        int count = fInputStream.read(fBuffer, 0, byteLength);
        if(count == -1) return -1;
        // 尝试计数并使计数成为要寻找的字节数的倍数
        if(fEncoding >= 4) { // BigEndian
            // 这看起来很丑陋, 但无论如何，它避免了if...
            int numToRead = (4 - (count & 3) & 3);
            for(int i=0; i<numToRead; i++) {
                int charRead = fInputStream.read();
                if(charRead == -1) { // 输入结束; 缓冲区是 null.
                    for (int j = i;j<numToRead; j++)
                        fBuffer[count+j] = 0;
                    break;
                } else {
                    fBuffer[count+i] = (byte)charRead; 
                }
            }
            count += numToRead;
        } else {
            int numToRead = count & 1;
            if(numToRead != 0) {
                count++;
                int charRead = fInputStream.read();
                if(charRead == -1) { // 输入结束; 缓冲区是 null.
                    fBuffer[count] = 0;
                } else {
                    fBuffer[count] = (byte)charRead;
                }
            }
        }

        // 现在计数是正确字节数的倍数
        int numChars = count >> ((fEncoding >= 4)?2:1);
        int curPos = 0;
        for (int i = 0; i < numChars; i++) {
            int b0 = fBuffer[curPos++] & 0xff;
            int b1 = fBuffer[curPos++] & 0xff;
            if(fEncoding >=4) {
                int b2 = fBuffer[curPos++] & 0xff;
                int b3 = fBuffer[curPos++] & 0xff;
                if (fEncoding == UCS4BE)
                    ch[offset+i] = (char)((b0<<24)+(b1<<16)+(b2<<8)+b3);
                else
                    ch[offset+i] = (char)((b3<<24)+(b2<<16)+(b1<<8)+b0);
            } else { // UCS-2
                if (fEncoding == UCS2BE)
                    ch[offset+i] = (char)((b0<<8)+b1);
                else
                    ch[offset+i] = (char)((b1<<8)+b0);
            }
        }
        return numChars;
    }

    /**
     * 跳过字符. 此方法将阻塞直到字符可用, 或者发生I/O 错误, 或者到达流的结尾.
     *
     * @param  n  要跳过的字符数
     *
     * @return    实际跳过的字符数
     *
     * @exception  IOException  如果发生I/O 错误
     */
    public long skip(long n) throws IOException {
        // charWidth 表示向左移动n个字节数获取要跳过的字节的数量, 然后向右移动结果获取实际跳过的字符的数量.
        // 使用 &, 为了避免使用没有被优化的 /.
        int charWidth = (fEncoding >=4)?2:1;
        long bytesSkipped = fInputStream.skip(n<<charWidth);
        if((bytesSkipped & (charWidth | 1)) == 0) return bytesSkipped >> charWidth;
        return (bytesSkipped >> charWidth) + 1;
    }

    /**
     * 判断此流是否已准备好读取.
     *
     * @return True 如果下一个read() 保证不阻止输入, 否则false. 返回false 不保证下一个读将阻塞.
     *
     * @exception  IOException  如果发生I/O 错误
     */
    public boolean ready() throws IOException {
    	return false;
    }

    /**
     * 判断此流是否支持mark() 操作.
     */
    public boolean markSupported() {
    	return fInputStream.markSupported();
    }

    /**
     * 在流中标出目前的位置. 随后调用reset()将试图将流重新定位到这一点.并非所有字符输入流都支持 mark() 操作.
     *
     * @param  readAheadLimit  限制在保留标记时可能读取的字符数. 读了这么多字符之后, 试图重置流可能失败.
     *
     * @exception  IOException 如果流不支持 mark(), 或者发生I/O 错误
     */
    public void mark(int readAheadLimit) throws IOException {
    	fInputStream.mark(readAheadLimit);
    }

    /**
     * 重置流. 如果流已标记, 然后试着在标记处重新定位. 如果流没有被标记, 然后尝试以某种特定于特定流的方式重新设置它,
     * 例如，通过将其重新定位到起始点. 并非所有字符输入流都支持 reset()操作, 一些支持reset() 但不支持 mark().
     *
     * @exception  IOException  如果流没有被标记, 或者如果标记失效了, 或者如果流不支持reset(), 或者发生其它I/O 错误
     */
    public void reset() throws IOException {
        fInputStream.reset();
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
}
