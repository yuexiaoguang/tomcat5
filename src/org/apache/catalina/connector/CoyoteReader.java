package org.apache.catalina.connector;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * 缓冲区读取的实现.
 */
public class CoyoteReader extends BufferedReader {

    // -------------------------------------------------------------- Constants

    private static final char[] LINE_SEP = { '\r', '\n' };
    private static final int MAX_LINE_LENGTH = 4096;

    // ----------------------------------------------------- Instance Variables

    protected InputBuffer ib;

    protected char[] lineBuffer = null;

    // ----------------------------------------------------------- Constructors

    public CoyoteReader(InputBuffer ib) {
        super(ib, 1);
        this.ib = ib;
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
        ib = null;
    }

    // --------------------------------------------------------- Reader Methods

    public void close()
        throws IOException {
        ib.close();
    }


    public int read()
        throws IOException {
        return ib.read();
    }


    public int read(char[] cbuf)
        throws IOException {
        return ib.read(cbuf, 0, cbuf.length);
    }


    public int read(char[] cbuf, int off, int len)
        throws IOException {
        return ib.read(cbuf, off, len);
    }


    public long skip(long n)
        throws IOException {
        return ib.skip(n);
    }


    public boolean ready()
        throws IOException {
        return ib.ready();
    }


    public boolean markSupported() {
        return true;
    }


    public void mark(int readAheadLimit)
        throws IOException {
        ib.mark(readAheadLimit);
    }


    public void reset()
        throws IOException {
        ib.reset();
    }


    public String readLine() throws IOException {

        if (lineBuffer == null) {
            lineBuffer = new char[MAX_LINE_LENGTH];
        }

        String result = null;

        int pos = 0;
        int end = -1;
        int skip = -1;
        StringBuffer aggregator = null;
        while (end < 0) {
            mark(MAX_LINE_LENGTH);
            while ((pos < MAX_LINE_LENGTH) && (end < 0)) {
                int nRead = read(lineBuffer, pos, MAX_LINE_LENGTH - pos);
                if (nRead < 0) {
                    if (pos == 0) {
                        return null;
                    }
                    end = pos;
                    skip = pos;
                }
                for (int i = pos; (i < (pos + nRead)) && (end < 0); i++) {
                    if (lineBuffer[i] == LINE_SEP[0]) {
                        end = i;
                        skip = i + 1;
                        char nextchar;
                        if (i == (pos + nRead - 1)) {
                            nextchar = (char) read();
                        } else {
                            nextchar = lineBuffer[i+1];
                        }
                        if (nextchar == LINE_SEP[1]) {
                            skip++;
                        }
                    } else if (lineBuffer[i] == LINE_SEP[1]) {
                        end = i;
                        skip = i + 1;
                    }
                }
                if (nRead > 0) {
                    pos += nRead;
                }
            }
            if (end < 0) {
                if (aggregator == null) {
                    aggregator = new StringBuffer();
                }
                aggregator.append(lineBuffer);
                pos = 0;
            } else {
                reset();
                skip(skip);
            }
        }

        if (aggregator == null) {
            result = new String(lineBuffer, 0, end);
        } else {
            aggregator.append(lineBuffer, 0, end);
            result = aggregator.toString();
        }

        return result;
    }
}
