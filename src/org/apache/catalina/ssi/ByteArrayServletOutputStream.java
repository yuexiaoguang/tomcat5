package org.apache.catalina.ssi;

import java.io.ByteArrayOutputStream;
import javax.servlet.ServletOutputStream;


/**
 * 封装<code>SsiInclude</code>到内部
 */
public class ByteArrayServletOutputStream extends ServletOutputStream {
    /**
     * 保存流的缓冲区
     */
    protected ByteArrayOutputStream buf = null;


    public ByteArrayServletOutputStream() {
        buf = new ByteArrayOutputStream();
    }


    /**
     * @return the byte array.
     */
    public byte[] toByteArray() {
        return buf.toByteArray();
    }


    /**
     * 写入缓冲区
     *
     * @param b The parameter to write
     */
    public void write(int b) {
        buf.write(b);
    }
}
