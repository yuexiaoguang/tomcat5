package org.apache.jasper.xmlparser;

import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;
import org.apache.jasper.compiler.Localizer;

/**
 * 简单的ASCII 字节 reader. 这是一个优化的reader, 读取只包含7位ASCII字符的字节流.
 */
public class ASCIIReader extends Reader {


    /** 默认字节缓冲区大小(2048). */
    public static final int DEFAULT_BUFFER_SIZE = 2048;


    /** Input stream. */
    protected InputStream fInputStream;

    /** Byte buffer. */
    protected byte[] fBuffer;


    /** 
     * @param inputStream 输入流
     * @param size        初始缓冲区大小.
     */
    public ASCIIReader(InputStream inputStream, int size) {
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
        int b0 = fInputStream.read();
        if (b0 > 0x80) {
            throw new IOException(Localizer.getMessage("jsp.error.xml.invalidASCII",
						       Integer.toString(b0)));
        }
        return b0;
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
        if (length > fBuffer.length) {
            length = fBuffer.length;
        }
        int count = fInputStream.read(fBuffer, 0, length);
        for (int i = 0; i < count; i++) {
            int b0 = fBuffer[i];
            if (b0 > 0x80) {
                throw new IOException(Localizer.getMessage("jsp.error.xml.invalidASCII",
							   Integer.toString(b0)));
            }
            ch[offset + i] = (char)b0;
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
        return fInputStream.skip(n);
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
    	return fInputStream.markSupported();
    }

    /**
     * 在流中标出目前的位置. 随后调用 reset() 试图将流重新定位到这一点. 并非所有字符输入流都支持 mark() 操作.
     *
     * @param  readAheadLimit  限制在保留标记时可能读取的字符数. 读了这么多字符之后, 试图重置流可能失败.
     *
     * @exception  IOException  如果流不支持 mark(), 或者发生I/O 错误
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
