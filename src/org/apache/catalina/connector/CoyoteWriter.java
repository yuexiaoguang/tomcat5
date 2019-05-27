package org.apache.catalina.connector;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * servlet写入器的实现.
 */
public class CoyoteWriter extends PrintWriter {

    // -------------------------------------------------------------- Constants

    private static final char[] LINE_SEP = { '\r', '\n' };

    // ----------------------------------------------------- Instance Variables

    protected OutputBuffer ob;
    protected boolean error = false;

    // ----------------------------------------------------------- Constructors

    public CoyoteWriter(OutputBuffer ob) {
        super(ob);
        this.ob = ob;
    }

    // --------------------------------------------------------- Public Methods

    /**
     * 防止克隆
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


    /**
     * 重用.
     */
    void recycle() {
        error = false;
    }


    // --------------------------------------------------------- Writer Methods


    public void flush() {
        if (error)
            return;

        try {
            ob.flush();
        } catch (IOException e) {
            error = true;
        }
    }


    public void close() {
        // 不关闭PrintWriter - 不会调用super(), 这样流可以被重用. 关闭 ob.
        try {
            ob.close();
        } catch (IOException ex ) {
            ;
        }
        error = false;
    }


    public boolean checkError() {
        flush();
        return error;
    }


    public void write(int c) {
        if (error)
            return;

        try {
            ob.write(c);
        } catch (IOException e) {
            error = true;
        }
    }


    public void write(char buf[], int off, int len) {
        if (error)
            return;

        try {
            ob.write(buf, off, len);
        } catch (IOException e) {
            error = true;
        }
    }


    public void write(char buf[]) {
    	write(buf, 0, buf.length);
    }


    public void write(String s, int off, int len) {
        if (error)
            return;

        try {
            ob.write(s, off, len);
        } catch (IOException e) {
            error = true;
        }
    }


    public void write(String s) {
        write(s, 0, s.length());
    }


    // ---------------------------------------------------- PrintWriter Methods


    public void print(boolean b) {
        if (b) {
            write("true");
        } else {
            write("false");
        }
    }


    public void print(char c) {
        write(c);
    }


    public void print(int i) {
        write(String.valueOf(i));
    }


    public void print(long l) {
        write(String.valueOf(l));
    }


    public void print(float f) {
        write(String.valueOf(f));
    }


    public void print(double d) {
        write(String.valueOf(d));
    }


    public void print(char s[]) {
        write(s);
    }


    public void print(String s) {
        if (s == null) {
            s = "null";
        }
        write(s);
    }


    public void print(Object obj) {
        write(String.valueOf(obj));
    }


    public void println() {
        write(LINE_SEP);
    }


    public void println(boolean b) {
        print(b);
        println();
    }


    public void println(char c) {
        print(c);
        println();
    }


    public void println(int i) {
        print(i);
        println();
    }


    public void println(long l) {
        print(l);
        println();
    }


    public void println(float f) {
        print(f);
        println();
    }


    public void println(double d) {
        print(d);
        println();
    }


    public void println(char c[]) {
        print(c);
        println();
    }


    public void println(String s) {
        print(s);
        println();
    }


    public void println(Object o) {
        print(o);
        println();
    }
}
