package org.apache.jasper.compiler;

import java.util.Stack;
import java.net.URL;
import java.net.MalformedURLException;
import org.apache.jasper.JspCompilationContext;

/**
 * JSP输入中的一个点. 
 */
final class Mark {

    // 当前流中的位置
    int cursor, line, col;

    // 当前流文件的目录
    String baseDir;

    // 当前流
    char[] stream = null;

    // 当前流的文件标识符
    private int fileId;

    // 当前文件名
    private String fileName;

    /*
     * 包含当前流的和状态的堆栈
     */
    private Stack includeStack = null;

    // 当前文件的编码
    private String encoding = null;

    private JspReader reader;

    private JspCompilationContext ctxt;

    /**
     * @param reader 所属的JspReader
     * @param inStream 此标记的当前流
     * @param fileId 请求的JSP文件的ID
     * @param name JSP文件名
     * @param inBaseDir 请求的JSP文件的基本目录
     * @param inEncoding 当前文件的编码
     */
    Mark(JspReader reader, char[] inStream, int fileId, String name,
         String inBaseDir, String inEncoding) {

        this.reader = reader;
        this.ctxt = reader.getJspCompilationContext();
        this.stream = inStream;
        this.cursor = 0;
        this.line = 1;
        this.col = 1;
        this.fileId = fileId;
        this.fileName = name;
        this.baseDir = inBaseDir;
        this.encoding = inEncoding;
        this.includeStack = new Stack();
    }


    Mark(Mark other) {
        this.reader = other.reader;
        this.ctxt = other.reader.getJspCompilationContext();
        this.stream = other.stream;
        this.fileId = other.fileId;
        this.fileName = other.fileName;
        this.cursor = other.cursor;
        this.line = other.line;
        this.col = other.col;
        this.baseDir = other.baseDir;
        this.encoding = other.encoding;

        // clone includeStack without cloning contents
        includeStack = new Stack();
        for ( int i=0; i < other.includeStack.size(); i++ ) {
            includeStack.addElement( other.includeStack.elementAt(i) );
        }
    }


    Mark(JspCompilationContext ctxt, String filename, int line, int col) {

        this.reader = null;
        this.ctxt = ctxt;
        this.stream = null;
        this.cursor = 0;
        this.line = line;
        this.col = col;
        this.fileId = -1;
        this.fileName = filename;
        this.baseDir = "le-basedir";
        this.encoding = "le-endocing";
        this.includeStack = null;
    }


    /**
     * 将标记状态设置为新流.
     * 它将在它的includeStack中保存当前流.
     *
     * @param inStream 要标记的新流
     * @param inFileId 流来源的新文件的id
     * @param inBaseDir 文件目录
     * @param inEncoding 新文件的编码
     */
    public void pushStream(char[] inStream, int inFileId, String name,
                           String inBaseDir, String inEncoding) 
    {
        // 堆栈中存储当前状态
        includeStack.push(new IncludeState(cursor, line, col, fileId,
                                           fileName, baseDir, encoding, stream) );

        // 设置新变量
        cursor = 0;
        line = 1;
        col = 1;
        fileId = inFileId;
        fileName = name;
        baseDir = inBaseDir;
        encoding = inEncoding;
        stream = inStream;
    }


    /**
     * 将标记状态还原到先前存储的流中.
     * @return 前一个 Mark 实例，当流被推送的时候, 或 null
     */
    public Mark popStream() {
        // 确保我们有什么东西可以弹出
        if ( includeStack.size() <= 0 ) {
            return null;
        }

        // 在堆栈中获取以前的状态
        IncludeState state = (IncludeState) includeStack.pop( );

        // 设置新的变量
        cursor = state.cursor;
        line = state.line;
        col = state.col;
        fileId = state.fileId;
        fileName = state.fileName;
        baseDir = state.baseDir;
        stream = state.stream;
        return this;
    }


    // -------------------- Locator interface --------------------

    public int getLineNumber() {
        return line;
    }

    public int getColumnNumber() {
        return col;
    }

    public String getSystemId() {
        return getFile();
    }

    public String getPublicId() {
        return null;
    }

    public String toString() {
	return getFile()+"("+line+","+col+")";
    }

    public String getFile() {
        return this.fileName;
    }

    /**
     * 获取与此Mark关联的资源的URL
     *
     * @return 与此Mark关联的资源的URL
     *
     * @exception MalformedURLException 如果资源的路径是不正确的
     */
    public URL getURL() throws MalformedURLException {
        return ctxt.getResource(getFile());
    }

    public String toShortString() {
        return "("+line+","+col+")";
    }

    public boolean equals(Object other) {
		if (other instanceof Mark) {
		    Mark m = (Mark) other;
		    return this.reader == m.reader && this.fileId == m.fileId 
			&& this.cursor == m.cursor && this.line == m.line 
			&& this.col == m.col;
		} 
		return false;
    }

    /**
     * @return true 如果这个Mark比<code>other</code>Mark更大, 否则返回false.
     */
    public boolean isGreater(Mark other) {

        boolean greater = false;

        if (this.line > other.line) {
            greater = true;
        } else if (this.line == other.line && this.col > other.col) {
            greater = true;
        }

        return greater;
    }

    /**
     * 在解析包含的文件之前跟踪解析器.
     * 在切换到解析包含的文件之前，这个类将跟踪解析器. 换句话说, 在包含的文件解析完成之后, 它是要重新安装的解析器的延续.
     */
    class IncludeState {
        int cursor, line, col;
        int fileId;
        String fileName;
        String baseDir;
        String encoding;
        char[] stream = null;

        IncludeState(int inCursor, int inLine, int inCol, int inFileId, 
                     String name, String inBaseDir, String inEncoding,
                     char[] inStream) {
            cursor = inCursor;
            line = inLine;
            col = inCol;
            fileId = inFileId;
            fileName = name;
            baseDir = inBaseDir;
            encoding = inEncoding;
            stream = inStream;
        }
    }
}
