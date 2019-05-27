package org.apache.catalina.connector;

import java.io.IOException;
import java.io.Writer;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;

import org.apache.coyote.ActionCode;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.C2BConverter;
import org.apache.tomcat.util.buf.CharChunk;

/**
 * Tomcat响应所使用的缓冲区. 这是Tomcat 3.3 OutputBuffer的扩展, 随着一些状态处理的删除 (在Coyote中主要是 Processor的责任).
 */
public class OutputBuffer extends Writer implements ByteChunk.ByteOutputChannel, CharChunk.CharOutputChannel {

    // -------------------------------------------------------------- Constants

    public static final String DEFAULT_ENCODING = 
        org.apache.coyote.Constants.DEFAULT_CHARACTER_ENCODING;
    public static final int DEFAULT_BUFFER_SIZE = 8*1024;


    // 缓冲区可用于 byte[] 和 char[] 写入
    // (需要支持 ServletOutputStream 以及模板系统的高效实现)
    public final int INITIAL_STATE = 0;
    public final int CHAR_STATE = 1;
    public final int BYTE_STATE = 2;


    // ----------------------------------------------------- Instance Variables


    /**
     * 字节缓冲区
     */
    private ByteChunk bb;


    /**
     * 块缓冲区
     */
    private CharChunk cb;


    /**
     * 输出缓冲区状态
     */
    private int state = 0;


    /**
     * 写入字节数.
     */
    private int bytesWritten = 0;


    /**
     * 写入字符数.
     */
    private int charsWritten = 0;


    /**
     * 指示输出缓冲区是否关闭.
     */
    private boolean closed = false;


    /**
     * 下一次操作刷新
     */
    private boolean doFlush = false;


    /**
     * 用于输出字节的字节块
     */
    private ByteChunk outputChunk = new ByteChunk();


    /**
     * 使用的编码
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
     * 当前字符到字节转换器.
     */
    protected C2BConverter conv;


    /**
     * 关联的Coyote 响应.
     */
    private Response coyoteResponse;


    /**
     * 暂停标志. 如果是true，所有输出字节都将被吞噬.
     */
    private boolean suspended = false;


    // ----------------------------------------------------------- Constructors


    /**
     * 使用默认缓冲区大小分配缓冲区.
     */
    public OutputBuffer() {
        this(DEFAULT_BUFFER_SIZE);
    }


    /**
     * @param size 缓冲区大小
     */
    public OutputBuffer(int size) {
        bb = new ByteChunk(size);
        bb.setLimit(size);
        bb.setByteOutputChannel(this);
        cb = new CharChunk(size);
        cb.setCharOutputChannel(this);
        cb.setLimit(size);
    }


    // ------------------------------------------------------------- Properties


    /**
     * 关联的Coyote 响应.
     * 
     * @param coyoteResponse Associated Coyote response
     */
    public void setResponse(Response coyoteResponse) {
    	this.coyoteResponse = coyoteResponse;
    }


    /**
     * 关联的Coyote 响应.
     * 
     * @return the associated Coyote response
     */
    public Response getResponse() {
        return this.coyoteResponse;
    }


    /**
     * 响应输出暂停吗 ?
     * 
     * @return suspended flag value
     */
    public boolean isSuspended() {
        return this.suspended;
    }


    /**
     * 设置响应输出暂停.
     * 
     * @param suspended New suspended flag value
     */
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 回收输出缓冲区
     */
    public void recycle() {
        state = INITIAL_STATE;
        bytesWritten = 0;
        charsWritten = 0;
        
        cb.recycle();
        bb.recycle(); 
        closed = false;
        suspended = false;
        
        if (conv!= null) {
            conv.recycle();
        }
        
        gotEnc = false;
        enc = null;
    }


    /**
     * 关闭输出缓冲区. 如果未提交响应，则试图计算响应大小.
     * 
     * @throws IOException 底层的IOException
     */
    public void close()
        throws IOException {

        if (closed)
            return;
        if (suspended)
            return;

        if ((!coyoteResponse.isCommitted()) 
            && (coyoteResponse.getContentLengthLong() == -1)) {
            // Flushing the char buffer
            if (state == CHAR_STATE) {
                cb.flushBuffer();
                state = BYTE_STATE;
            }
            // 如果这没有导致响应的提交, 最终的内容长度可以计算出来
            if (!coyoteResponse.isCommitted()) {
                coyoteResponse.setContentLength(bb.getLength());
            }
        }

        doFlush(false);
        closed = true;

        coyoteResponse.finish();
    }


    /**
     * 刷新缓冲区中包含的字节或字符
     * 
     * @throws IOException An underlying IOException occurred
     */
    public void flush() throws IOException {
        doFlush(true);
    }


    /**
     * 刷新缓冲区中包含的字节或字符.
     * 
     * @throws IOException An underlying IOException occurred
     */
    protected void doFlush(boolean realFlush) throws IOException {

        if (suspended)
            return;

        doFlush = true;
        if (state == CHAR_STATE) {
            cb.flushBuffer();
            bb.flushBuffer();
            state = BYTE_STATE;
        } else if (state == BYTE_STATE) {
            bb.flushBuffer();
        } else if (state == INITIAL_STATE) {
            // 如果缓冲区是空的, 提交响应头
            coyoteResponse.sendHeaders();
        }
        doFlush = false;

        if (realFlush) {
            coyoteResponse.action(ActionCode.ACTION_CLIENT_FLUSH, 
                                  coyoteResponse);
            // 如果出现某些异常, 或者一些IOE在这里发生, 通知Servlet与IOE
            if (coyoteResponse.isExceptionPresent()) {
                throw new ClientAbortException(coyoteResponse.getErrorException());
            }
        }
    }


    // ------------------------------------------------- Bytes Handling Methods


    /** 
     * 将缓冲区数据发送到客户端输出, 检查响应状态并调用正确的拦截器.
     * 
     * @param buf 将写入响应的字节缓冲区
     * @param off Offset
     * @param cnt Length
     * 
     * @throws IOException An underlying IOException occurred
     */
    public void realWriteBytes(byte buf[], int off, int cnt) throws IOException {

        if (closed)
            return;
        if (coyoteResponse == null)
            return;

        // 如果真的有东西要写入
        if (cnt > 0) {
            // 真正写入适配器
            outputChunk.setBytes(buf, off, cnt);
            try {
                coyoteResponse.doWrite(outputChunk);
            } catch (IOException e) {
                // 写入时IOException几乎总是由于远程客户端中止请求. 包装这个，以便通过错误分配器更好地处理它.
                throw new ClientAbortException(e);
            }
        }
    }


    public void write(byte b[], int off, int len) throws IOException {
        if (suspended)
            return;

        if (state == CHAR_STATE)
            cb.flushBuffer();
        state = BYTE_STATE;
        writeBytes(b, off, len);
    }


    private void writeBytes(byte b[], int off, int len) throws IOException {

        if (closed)
            return;

        bb.append(b, off, len);
        bytesWritten += len;

        // 如果调用 flush(), 然后立即刷新剩余字节
        if (doFlush) {
            bb.flushBuffer();
        }
    }


    public void writeByte(int b) throws IOException {

        if (suspended)
            return;

        if (state == CHAR_STATE)
            cb.flushBuffer();
        state = BYTE_STATE;

        bb.append( (byte)b );
        bytesWritten++;
    }


    // ------------------------------------------------- Chars Handling Methods


    public void write(int c) throws IOException {

        if (suspended)
            return;

        state = CHAR_STATE;

        cb.append((char) c);
        charsWritten++;
    }


    public void write(char c[]) throws IOException {

        if (suspended)
            return;

        write(c, 0, c.length);
    }


    public void write(char c[], int off, int len) throws IOException {

        if (suspended)
            return;

        state = CHAR_STATE;

        cb.append(c, off, len);
        charsWritten += len;
    }


    public void write(StringBuffer sb) throws IOException {

        if (suspended)
            return;

        state = CHAR_STATE;

        int len = sb.length();
        charsWritten += len;
        cb.append(sb);
    }


    /**
     * 向缓冲区追加一个字符串
     */
    public void write(String s, int off, int len) throws IOException {

        if (suspended)
            return;

        state=CHAR_STATE;

        charsWritten += len;
        if (s==null)
            s="null";
        cb.append( s, off, len );
    }


    public void write(String s) throws IOException {

        if (suspended)
            return;

        state = CHAR_STATE;
        if (s==null)
            s="null";
        write(s, 0, s.length());
    } 


    public void flushChars() throws IOException {
        cb.flushBuffer();
        state = BYTE_STATE;
    }


    public boolean flushCharsNeeded() {
        return state == CHAR_STATE;
    }


    public void setEncoding(String s) {
        enc = s;
    }


    public void realWriteChars(char c[], int off, int len) throws IOException {

        if (!gotEnc)
            setConverter();

        conv.convert(c, off, len);
        conv.flushBuffer();	// ???
    }


    public void checkConverter() throws IOException {
        if (!gotEnc)
            setConverter();
    }


    protected void setConverter() throws IOException {

        if (coyoteResponse != null)
            enc = coyoteResponse.getCharacterEncoding();

        gotEnc = true;
        if (enc == null)
            enc = DEFAULT_ENCODING;
        conv = (C2BConverter) encoders.get(enc);
        if (conv == null) {
            
            if (System.getSecurityManager() != null){
                try{
                    conv = (C2BConverter)AccessController.doPrivileged(
                            new PrivilegedExceptionAction(){

                                public Object run() throws IOException{
                                    return new C2BConverter(bb, enc);
                                }

                            }
                    );              
                }catch(PrivilegedActionException ex){
                    Exception e = ex.getException();
                    if (e instanceof IOException)
                        throw (IOException)e; 
                }
            } else {
                conv = new C2BConverter(bb, enc);
            }
            encoders.put(enc, conv);
        }
    }

    
    // --------------------  BufferedOutputStream compatibility


    /**
     * Real write - 此缓冲区将被发送到客户端
     */
    public void flushBytes() throws IOException {
        bb.flushBuffer();
    }


    public int getBytesWritten() {
        return bytesWritten;
    }


    public int getCharsWritten() {
        return charsWritten;
    }


    public int getContentWritten() {
        return bytesWritten + charsWritten;
    }


    /** 
     * 如果没有使用此缓冲区，则为true( since recycle() ) - 即在缓冲区中没有添加字符或字节.  
     */
    public boolean isNew() {
        return (bytesWritten == 0) && (charsWritten == 0);
    }


    public void setBufferSize(int size) {
        if (size > bb.getLimit()) {// ??????
	    bb.setLimit(size);
	}
    }


    public void reset() {
        //count=0;
        bb.recycle();
        bytesWritten = 0;
        cb.recycle();
        charsWritten = 0;
        gotEnc = false;
        enc = null;
        state = INITIAL_STATE;
    }


    public int getBufferSize() {
    	return bb.getLimit();
    }
}
