package org.apache.jasper.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;


/**
 * 此工具类可用于进行复杂重定向到System.out 和 System.err.
 */
public class SystemLogHandler extends PrintStream {


    // ----------------------------------------------------------- Constructors


    /**
     * 构建处理程序来捕获给定流的输出.
     */
    public SystemLogHandler(PrintStream wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 包装的 PrintStream.
     */
    protected PrintStream wrapped = null;


    /**
     * Thread <-> PrintStream 关联.
     */
    protected static ThreadLocal streams = new ThreadLocal();


    /**
     * Thread <-> ByteArrayOutputStream 关联.
     */
    protected static ThreadLocal data = new ThreadLocal();


    // --------------------------------------------------------- Public Methods


    public PrintStream getWrapped() {
      return wrapped;
    }

    /**
     * 开始捕获线程的输出.
     */
    public static void setThread() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        data.set(baos);
        streams.set(new PrintStream(baos));
    }


    /**
     * 停止捕获线程的输出，并将捕获的数据作为字符串返回.
     */
    public static String unsetThread() {
        ByteArrayOutputStream baos = (ByteArrayOutputStream) data.get();
        if (baos == null) {
            return null;
        }
        streams.set(null);
        data.set(null);
        return baos.toString();
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 查找PrintStream 到必须写入的输出.
     */
    protected PrintStream findStream() {
        PrintStream ps = (PrintStream) streams.get();
        if (ps == null) {
            ps = wrapped;
        }
        return ps;
    }


    // ---------------------------------------------------- PrintStream Methods


    public void flush() {
        findStream().flush();
    }

    public void close() {
        findStream().close();
    }

    public boolean checkError() {
        return findStream().checkError();
    }

    protected void setError() {
        //findStream().setError();
    }

    public void write(int b) {
        findStream().write(b);
    }

    public void write(byte[] b)
        throws IOException {
        findStream().write(b);
    }

    public void write(byte[] buf, int off, int len) {
        findStream().write(buf, off, len);
    }

    public void print(boolean b) {
        findStream().print(b);
    }

    public void print(char c) {
        findStream().print(c);
    }

    public void print(int i) {
        findStream().print(i);
    }

    public void print(long l) {
        findStream().print(l);
    }

    public void print(float f) {
        findStream().print(f);
    }

    public void print(double d) {
        findStream().print(d);
    }

    public void print(char[] s) {
        findStream().print(s);
    }

    public void print(String s) {
        findStream().print(s);
    }

    public void print(Object obj) {
        findStream().print(obj);
    }

    public void println() {
        findStream().println();
    }

    public void println(boolean x) {
        findStream().println(x);
    }

    public void println(char x) {
        findStream().println(x);
    }

    public void println(int x) {
        findStream().println(x);
    }

    public void println(long x) {
        findStream().println(x);
    }

    public void println(float x) {
        findStream().println(x);
    }

    public void println(double x) {
        findStream().println(x);
    }

    public void println(char[] x) {
        findStream().println(x);
    }

    public void println(String x) {
        findStream().println(x);
    }

    public void println(Object x) {
        findStream().println(x);
    }
}
