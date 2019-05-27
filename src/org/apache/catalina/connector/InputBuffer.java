package org.apache.catalina.connector;

import java.io.IOException;
import java.io.Reader;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;

import org.apache.catalina.security.SecurityUtil;
import org.apache.coyote.Request;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;

/**
 * Tomcat请求所使用的缓冲区. 这是Tomcat 3.3 OutputBuffer派生, 适合处理输入而不是输出.
 * 这允许完全回收外观对象(ServletInputStream 和 BufferedReader).
 */
public class InputBuffer extends Reader implements ByteChunk.ByteInputChannel, CharChunk.CharInputChannel,
               CharChunk.CharOutputChannel {


    // -------------------------------------------------------------- Constants


    public static final String DEFAULT_ENCODING = org.apache.coyote.Constants.DEFAULT_CHARACTER_ENCODING;
    public static final int DEFAULT_BUFFER_SIZE = 8*1024;

    // 缓冲区可用于 byte[] 和 char[] 读取
    // (需要支持 ServletInputStream 和 BufferedReader )
    public final int INITIAL_STATE = 0;
    public final int CHAR_STATE = 1;
    public final int BYTE_STATE = 2;


    // ----------------------------------------------------- Instance Variables


    /**
     * 字节缓冲区.
     */
    private ByteChunk bb;


    /**
     * 块缓冲区.
     */
    private CharChunk cb;


    /**
     * 输出缓冲区状态.
     */
    private int state = 0;


    /**
     * 读取字节数.
     */
    private int bytesRead = 0;


    /**
     * 读取字符数.
     */
    private int charsRead = 0;


    /**
     * 标志，指示输入缓冲区是否关闭.
     */
    private boolean closed = false;


    /**
     * 用于输入字节的字节块.
     */
    private ByteChunk inputChunk = new ByteChunk();


    /**
     * 使用的编码.
     */
    private String enc;


    /**
     * 编码器设置.
     */
    private boolean gotEnc = false;


    /**
     * 一组编码器.
     */
    protected HashMap encoders = new HashMap();


    /**
     * 当前字节到char转换器.
     */
    protected B2CConverter conv;


    /**
     * 关联的Coyote 请求.
     */
    private Request coyoteRequest;


    /**
     * 缓冲区的位置.
     */
    private int markPos = -1;


    /**
     * 缓冲区大小.
     */
    private int size = -1;


    // ----------------------------------------------------------- Constructors


    /**
     * 使用默认缓冲区大小分配缓冲区.
     */
    public InputBuffer() {
        this(DEFAULT_BUFFER_SIZE);
    }


    /**
     * @param size 初始缓冲区大小
     */
    public InputBuffer(int size) {
        this.size = size;
        bb = new ByteChunk(size);
        bb.setLimit(size);
        bb.setByteInputChannel(this);
        cb = new CharChunk(size);
        cb.setLimit(size);
        cb.setOptimizedWrite(false);
        cb.setCharInputChannel(this);
        cb.setCharOutputChannel(this);
    }


    // ------------------------------------------------------------- Properties


    /**
     * 关联的Coyote 请求.
     * 
     * @param coyoteRequest Associated Coyote request
     */
    public void setRequest(Request coyoteRequest) {
    	this.coyoteRequest = coyoteRequest;
    }


    /**
     * 关联的Coyote 请求.
     * 
     * @return the associated Coyote request
     */
    public Request getRequest() {
        return this.coyoteRequest;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 回收输出缓冲区.
     */
    public void recycle() {
        
        state = INITIAL_STATE;
        bytesRead = 0;
        charsRead = 0;
        
        // 如果使用标记使缓冲区过大, 重新分配它
        if (cb.getChars().length > size) {
            cb = new CharChunk(size);
            cb.setLimit(size);
            cb.setCharInputChannel(this);
            cb.setCharOutputChannel(this);
        } else {
            cb.recycle();
        }
        markPos = -1;
        bb.recycle(); 
        closed = false;
        
        if (conv != null) {
            conv.recycle();
        }
        
        gotEnc = false;
        enc = null;
    }


    /**
     * 关闭输入缓冲区.
     * 
     * @throws IOException 发生的底层IOException
     */
    public void close()
        throws IOException {
        closed = true;
    }


    public int available()
        throws IOException {
        if (state == BYTE_STATE) {
            return bb.getLength();
        } else if (state == CHAR_STATE) {
            return cb.getLength();
        } else {
            return 0;
        }
    }


    // ------------------------------------------------- Bytes Handling Methods


    /** 
     * 读取字节块中的新字节.
     * 
     * @param cbuf 将写入响应的字节缓冲区
     * @param off Offset
     * @param len Length
     * 
     * @throws IOException  发生的底层IOException
     */
    public int realReadBytes(byte cbuf[], int off, int len) throws IOException {
        if (closed)
            return -1;
        if (coyoteRequest == null)
            return -1;

        state = BYTE_STATE;
        int result = coyoteRequest.doRead(bb);
        return result;
    }


    public int readByte()
        throws IOException {
        return bb.substract();
    }


    public int read(byte[] b, int off, int len)
        throws IOException {
        return bb.substract(b, off, len);
    }


    // ------------------------------------------------- Chars Handling Methods


    /**
     * 由于转换器将使用追加, 从缓冲区中取出字符是可能的 .
     * 因为字符之前已经被读取了, 它们被忽略. 如果一个标志被设置, 然后标记就丢失了.
     */
    public void realWriteChars(char c[], int off, int len) 
        throws IOException {
        markPos = -1;
    }


    public void setEncoding(String s) {
        enc = s;
    }


    public int realReadChars(char cbuf[], int off, int len)
        throws IOException {

        if (!gotEnc)
            setConverter();

        if (bb.getLength() <= 0) {
            int nRead = realReadBytes(bb.getBytes(), 0, bb.getBytes().length);
            if (nRead < 0) {
                return -1;
            }
        }

        if (markPos == -1) {
            cb.setOffset(0);
            cb.setEnd(0);
        }

        conv.convert(bb, cb);
        bb.setOffset(bb.getEnd());
        state = CHAR_STATE;

        return cb.getLength();
    }


    public int read()
        throws IOException {
        return cb.substract();
    }


    public int read(char[] cbuf)
        throws IOException {
        return read(cbuf, 0, cbuf.length);
    }


    public int read(char[] cbuf, int off, int len)
        throws IOException {
        return cb.substract(cbuf, off, len);
    }


    public long skip(long n)
        throws IOException {

        if (n < 0) {
            throw new IllegalArgumentException();
        }

        long nRead = 0;
        while (nRead < n) {
            if (cb.getLength() >= n) {
                cb.setOffset(cb.getStart() + (int) n);
                nRead = n;
            } else {
                nRead += cb.getLength();
                cb.setOffset(cb.getEnd());
                int toRead = 0;
                if (cb.getChars().length < (n - nRead)) {
                    toRead = cb.getChars().length;
                } else {
                    toRead = (int) (n - nRead);
                }
                int nb = realReadChars(cb.getChars(), 0, toRead);
                if (nb < 0)
                    break;
            }
        }

        return nRead;
    }


    public boolean ready()
        throws IOException {
        return (cb.getLength() > 0);
    }


    public boolean markSupported() {
        return true;
    }


    public void mark(int readAheadLimit)
        throws IOException {
        if (cb.getLength() <= 0) {
            cb.setOffset(0);
            cb.setEnd(0);
        } else {
            if ((cb.getBuffer().length > (2 * size)) 
                && (cb.getLength()) < (cb.getStart())) {
                System.arraycopy(cb.getBuffer(), cb.getStart(), 
                                 cb.getBuffer(), 0, cb.getLength());
                cb.setEnd(cb.getLength());
                cb.setOffset(0);
            }
        }
        int offset = readAheadLimit;
        if (offset < size) {
            offset = size;
        }
        cb.setLimit(cb.getStart() + offset);
        markPos = cb.getStart();
    }


    public void reset()
        throws IOException {
        if (state == CHAR_STATE) {
            if (markPos < 0) {
                cb.recycle();
                markPos = -1;
                throw new IOException();
            } else {
                cb.setOffset(markPos);
            }
        } else {
            bb.recycle();
        }
    }


    public void checkConverter() throws IOException {
        if (!gotEnc)
            setConverter();
    }


    protected void setConverter() throws IOException {

        if (coyoteRequest != null)
            enc = coyoteRequest.getCharacterEncoding();

        gotEnc = true;
        if (enc == null)
            enc = DEFAULT_ENCODING;
        conv = (B2CConverter) encoders.get(enc);
        if (conv == null) {
            if (SecurityUtil.isPackageProtectionEnabled()){
                try{
                    conv = (B2CConverter)AccessController.doPrivileged(
                            new PrivilegedExceptionAction(){

                                public Object run() throws IOException{
                                    return new B2CConverter(enc);
                                }

                            }
                    );              
                }catch(PrivilegedActionException ex){
                    Exception e = ex.getException();
                    if (e instanceof IOException)
                        throw (IOException)e; 
                }
            } else {
                conv = new B2CConverter(enc);
            }
            encoders.put(enc, conv);
        }
    }
}
