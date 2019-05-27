package org.apache.catalina.connector;

import java.io.IOException;

import javax.servlet.ServletOutputStream;

/**
 * servlet输出流实现类.
 */
public class CoyoteOutputStream  extends ServletOutputStream {

    // ----------------------------------------------------- Instance Variables

    protected OutputBuffer ob;

    // ----------------------------------------------------------- Constructors

    protected CoyoteOutputStream(OutputBuffer ob) {
        this.ob = ob;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * 防止克隆.
     */
    protected Object clone()
        throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    // -------------------------------------------------------- Package Methods

    /**
     * 清空
     */
    void clear() {
        ob = null;
    }

    // --------------------------------------------------- OutputStream Methods

    public void write(int i)
        throws IOException {
        ob.writeByte(i);
    }


    public void write(byte[] b)
        throws IOException {
        write(b, 0, b.length);
    }


    public void write(byte[] b, int off, int len)
        throws IOException {
        ob.write(b, off, len);
    }

    /**
     * 将向客户端发送缓冲区.
     */
    public void flush() throws IOException {
        ob.flush();
    }

    public void close()
        throws IOException {
        ob.close();
    }

    // -------------------------------------------- ServletOutputStream Methods

    public void print(String s)
        throws IOException {
        ob.write(s);
    }
}

