package org.apache.jasper.compiler;

import java.io.CharArrayWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Vector;
import java.util.jar.JarFile;
import java.net.URL;
import java.net.MalformedURLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;

/**
 * JspReader 是JSP解析器的输入缓冲区. 它应该允许无限的lookahead 和 pushback. 它还有一组解析实用工具方法用于理解htmlesque.
 */
class JspReader {

    private Log log = LogFactory.getLog(JspReader.class);

    private Mark current;
    private String master;
    private Vector sourceFiles;
    private int currFileId;
    private int size;
    private JspCompilationContext context;
    private ErrorDispatcher err;

    /*
     * 设置为true, 当在多次重复读取的单个文件中使用 JspReader 的时候.
     * (as in ParserController.figureOutJspDocument()).
     */
    private boolean singleFile;

    public JspReader(JspCompilationContext ctxt,
                     String fname,
                     String encoding,
                     JarFile jarFile,
                     ErrorDispatcher err)
            throws JasperException, FileNotFoundException, IOException {

        this(ctxt, fname, encoding, JspUtil.getReader(fname, encoding, jarFile, ctxt, err), err);
    }

    public JspReader(JspCompilationContext ctxt,
                     String fname,
                     String encoding,
                     InputStreamReader reader,
                     ErrorDispatcher err)
            throws JasperException, FileNotFoundException {

        this.context = ctxt;
        this.err = err;
        sourceFiles = new Vector();
        currFileId = 0;
        size = 0;
        singleFile = false;
        pushFile(fname, encoding, reader);
    }

    /*
     * @return 这个JspReader关联的JSP编译上下文
     */
    JspCompilationContext getJspCompilationContext() {
        return context;
    }
    
    String getFile(int fileid) {
        return (String) sourceFiles.elementAt(fileid);
    }
        
    boolean hasMoreInput() throws JasperException {
        if (current.cursor >= current.stream.length) {
            if (singleFile) return false; 
            while (popFile()) {
                if (current.cursor < current.stream.length) return true;
            }
            return false;
        }
        return true;
    }
    
    int nextChar() throws JasperException {
        if (!hasMoreInput())
            return -1;
        
        int ch = current.stream[current.cursor];

        current.cursor++;
        
        if (ch == '\n') {
            current.line++;
            current.col = 0;
        } else {
            current.col++;
        }
        return ch;
    }

    /**
     * 用一个字符备份当前光标, 假设 current.cursor > 0, 而且推回来的字符不是 '\n'.
     */
    void pushChar() {
        current.cursor--;
        current.col--;
    }

    String getText(Mark start, Mark stop) throws JasperException {
        Mark oldstart = mark();
        reset(start);
        CharArrayWriter caw = new CharArrayWriter();
        while (!stop.equals(mark()))
            caw.write(nextChar());
        caw.close();
        reset(oldstart);
        return caw.toString();
    }

    int peekChar() throws JasperException {
        if (!hasMoreInput())
            return -1;
        return current.stream[current.cursor];
    }

    Mark mark() {
        return new Mark(current);
    }

    void reset(Mark mark) {
        current = new Mark(mark);
    }

    boolean matchesIgnoreCase(String string) throws JasperException {
        Mark mark = mark();
        int ch = 0;
        int i = 0;
        do {
            ch = nextChar();
            if (Character.toLowerCase((char) ch) != string.charAt(i++)) {
                reset(mark);
                return false;
            }
        } while (i < string.length());
        reset(mark);
        return true;
    }

    /**
     * 在流中搜索字符串的匹配项
     * @param string 要匹配的字符串
     * @return <strong>true</strong>表示找到了一个, 流中的当前位置位于搜索字符串之后,
     * 		否则<strong>false</strong>, 流中位置不变.
     */
    boolean matches(String string) throws JasperException {
        Mark mark = mark();
        int ch = 0;
        int i = 0;
        do {
            ch = nextChar();
            if (((char) ch) != string.charAt(i++)) {
                reset(mark);
                return false;
            }
        } while (i < string.length());
        return true;
    }

    boolean matchesETag(String tagName) throws JasperException {
        Mark mark = mark();

        if (!matches("</" + tagName))
            return false;
        skipSpaces();
        if (nextChar() == '>')
            return true;

        reset(mark);
        return false;
    }

    boolean matchesETagWithoutLessThan(String tagName)
        throws JasperException
    {
       Mark mark = mark();

       if (!matches("/" + tagName))
           return false;
       skipSpaces();
       if (nextChar() == '>')
           return true;

       reset(mark);
       return false;
    }


    /**
     * 给定字符串后面是否有可选空格.
     * 如果有, 返回true, 并跳过这些空格和字符.
     * 如果没有, 返回false, 位置恢复到我们以前的位置.
     */
    boolean matchesOptionalSpacesFollowedBy( String s ) throws JasperException {
        Mark mark = mark();

        skipSpaces();
        boolean result = matches( s );
        if( !result ) {
            reset( mark );
        }

        return result;
    }

    int skipSpaces() throws JasperException {
        int i = 0;
        while (hasMoreInput() && isSpace()) {
            i++;
            nextChar();
        }
        return i;
    }

    /**
     * 跳过直到给定字符串在流中匹配为止.
     * 返回的时候, 上下文被定位在匹配结束之前.
     *
     * @param s 要匹配的字符串.
     * @return 一个非null <code>Mark</code>实例 (定位在搜索字符串之前), 或者<strong>null</strong>
     */
    Mark skipUntil(String limit) throws JasperException {
        Mark ret = null;
        int limlen = limit.length();
        int ch;

    skip:
        for (ret = mark(), ch = nextChar() ; ch != -1 ;
                 ret = mark(), ch = nextChar()) {
            if (ch == limit.charAt(0)) {
                Mark restart = mark();
                for (int i = 1 ; i < limlen ; i++) {
                    if (peekChar() == limit.charAt(i))
                        nextChar();
                    else {
                        reset(restart);
                        continue skip;
                    }
                }
                return ret;
            }
        }
        return null;
    }

    /**
     * 跳过直到给定字符串在流中匹配为止, 但是忽略最初被'\'转义的字符.
     * 返回的时候, 上下文被定位在匹配结束之前.
     *
     * @param s 要匹配的字符串.
     * @return 一个非null <code>Mark</code>实例 (定位在搜索字符串之前), 或者<strong>null</strong>
     */
    Mark skipUntilIgnoreEsc(String limit) throws JasperException {
        Mark ret = null;
        int limlen = limit.length();
        int ch;
        int prev = 'x';        // Doesn't matter
        
    skip:
        for (ret = mark(), ch = nextChar() ; ch != -1 ;
                 ret = mark(), prev = ch, ch = nextChar()) {            
            if (ch == '\\' && prev == '\\') {
                ch = 0;                // Double \ is not an escape char anymore
            }
            else if (ch == limit.charAt(0) && prev != '\\') {
                for (int i = 1 ; i < limlen ; i++) {
                    if (peekChar() == limit.charAt(i))
                        nextChar();
                    else
                        continue skip;
                }
                return ret;
            }
        }
        return null;
    }
    
    /**
     * 跳过，直到在流中匹配到给定的结束标签.
     * 返回的时候, 上下文被定位在匹配结束之前.
     *
     * @param tag 要匹配ETag (</tag>)的标签名称
     * @return 一个non-null <code>Mark</code>实例(立即定位在ETag之前), 或者<strong>null</strong>
     */
    Mark skipUntilETag(String tag) throws JasperException {
        Mark ret = skipUntil("</" + tag);
        if (ret != null) {
            skipSpaces();
            if (nextChar() != '>')
                ret = null;
        }
        return ret;
    }

    final boolean isSpace() throws JasperException {
        // Note: 如果这个逻辑改变了, 也更新Node.TemplateText.rtrim()
        return peekChar() <= ' ';
    }

    /**
     * 解析空格分隔的token.
     * 如果引号了token 将消耗所有字符到匹配的引号, 否则, 它消耗到第一个分隔符.
     *
     * @param quoted <strong>true</strong>接受引号字符串.
     */
    String parseToken(boolean quoted) throws JasperException {
        StringBuffer stringBuffer = new StringBuffer();
        skipSpaces();
        stringBuffer.setLength(0);
        
        if (!hasMoreInput()) {
            return "";
        }

        int ch = peekChar();
        
        if (quoted) {
            if (ch == '"' || ch == '\'') {

                char endQuote = ch == '"' ? '"' : '\'';
                // 消费公开的引号: 
                ch = nextChar();
                for (ch = nextChar(); ch != -1 && ch != endQuote;
                         ch = nextChar()) {
                    if (ch == '\\') 
                        ch = nextChar();
                    stringBuffer.append((char) ch);
                }
                // 检查引号的结尾, 跳过关闭的引号:
                if (ch == -1) {
                    err.jspError(mark(), "jsp.error.quotes.unterminated");
                }
            } else {
                err.jspError(mark(), "jsp.error.attr.quoted");
            }
        } else {
            if (!isDelimiter()) {
                // 读取值直到找到分隔符:
                do {
                    ch = nextChar();
                    // 注意这里的引号.
                    if (ch == '\\') {
                        if (peekChar() == '"' || peekChar() == '\'' ||
                               peekChar() == '>' || peekChar() == '%')
                            ch = nextChar();
                    }
                    stringBuffer.append((char) ch);
                } while (!isDelimiter());
            }
        }

        return stringBuffer.toString();
    }

    void setSingleFile(boolean val) {
        singleFile = val;
    }


    /**
     * 获取给定路径名的URL.
     *
     * @param path 路径名
     *
     * @return 给定路径名的URL.
     *
     * @exception MalformedURLException 如果路径名称没有以正确的形式给出
     */
    URL getResource(String path) throws MalformedURLException {
        return context.getResource(path);
    }


    /**
     * 解析工具 - 当前字符是令牌分隔符吗?
     * 分隔符目前定义为 =, &gt;, &lt;, ", ' 或<code>isSpace</code>定义的任何空格字符.
     *
     * @return A boolean.
     */
    private boolean isDelimiter() throws JasperException {
        if (! isSpace()) {
            int ch = peekChar();
            // 查找单个char工作定界符:
            if (ch == '=' || ch == '>' || ch == '"' || ch == '\''
                    || ch == '/') {
                return true;
            }
            // 查找注释结束或标签结束:                
            if (ch == '-') {
                Mark mark = mark();
                if (((ch = nextChar()) == '>')
                        || ((ch == '-') && (nextChar() == '>'))) {
                    reset(mark);
                    return true;
                } else {
                    reset(mark);
                    return false;
                }
            }
            return false;
        } else {
            return true;
        }
    }

    /**
     * 注册一个新的源文件.
     * 此方法用于实现文件包含. 每个包含的文件获取唯一标识符(它是源文件数组中的索引).
     *
     * @return 已注册文件的索引.
     */
    private int registerSourceFile(String file) {
        if (sourceFiles.contains(file))
            return -1;
        sourceFiles.addElement(file);
        this.size++;
        return sourceFiles.size() - 1;
    }
    

    /**
     * 注销源文件.
     * 此方法用于实现文件包含. 每个包含的文件都有一个唯一的标识符(它是源文件数组中的索引).
     *
     * @return 已注册文件的索引.
     */
    private int unregisterSourceFile(String file) {
        if (!sourceFiles.contains(file))
            return -1;
        sourceFiles.removeElement(file);
        this.size--;
        return sourceFiles.size() - 1;
    }

    /**
     * 在文件堆栈上推送文件(及其相关流). 当前文件中被记住的当前位置.
     */
    private void pushFile(String file, String encoding, 
                           InputStreamReader reader) 
                throws JasperException, FileNotFoundException {

        // 注册文件
        String longName = file;

        int fileid = registerSourceFile(longName);

        if (fileid == -1) {
            err.jspError("jsp.error.file.already.registered", file);
        }

        currFileId = fileid;

        try {
            CharArrayWriter caw = new CharArrayWriter();
            char buf[] = new char[1024];
            for (int i = 0 ; (i = reader.read(buf)) != -1 ;)
                caw.write(buf, 0, i);
            caw.close();
            if (current == null) {
                current = new Mark(this, caw.toCharArray(), fileid, 
                                   getFile(fileid), master, encoding);
            } else {
                current.pushStream(caw.toCharArray(), fileid, getFile(fileid),
                                   longName, encoding);
            }
        } catch (Throwable ex) {
            log.error("Exception parsing file ", ex);
            // 流行状态正在形成:
            popFile();
            err.jspError("jsp.error.file.cannot.read", file);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception any) {}
            }
        }
    }

    /**
     * 从文件堆栈中弹出一个文件. "current"字段被恢复为指向前一个文件的值,否则将设置为null.
     * @return true 是堆栈上的前一个文件. 否则false.
     */
    private boolean popFile() throws JasperException {

        // 是堆栈? (如果正在查看的JSP文件丢失，将会发生.
        if (current == null || currFileId < 0) {
            return false;
        }

        // 恢复解析器状态:
        String fName = getFile(currFileId);
        currFileId = unregisterSourceFile(fName);
        if (currFileId < -1) {
            err.jspError("jsp.error.file.not.registered", fName);
        }

        Mark previous = current.popStream();
        if (previous != null) {
            master = current.baseDir;
            current = previous;
            return true;
        }
        // 注意，虽然当前文件未定义在这里, "current"为了方便不设置为 null, 因为它可能用来设置当前（未定义的）位置.
        return false;
    }
}

