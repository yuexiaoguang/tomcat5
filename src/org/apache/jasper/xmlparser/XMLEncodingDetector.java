package org.apache.jasper.xmlparser;

import java.io.EOFException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Locale;
import java.util.jar.JarFile;

import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.apache.jasper.compiler.ErrorDispatcher;
import org.apache.jasper.compiler.JspUtil;

public class XMLEncodingDetector {
    
    private InputStream stream;
    private String encoding;
    private boolean isEncodingSetInProlog;
    private Boolean isBigEndian;
    private Reader reader;
    
    // org.apache.xerces.impl.XMLEntityManager 字段
    public static final int DEFAULT_BUFFER_SIZE = 2048;
    public static final int DEFAULT_XMLDECL_BUFFER_SIZE = 64;
    private boolean fAllowJavaEncodings;
    private SymbolTable fSymbolTable;
    private XMLEncodingDetector fCurrentEntity;
    private int fBufferSize = DEFAULT_BUFFER_SIZE;
    
    // org.apache.xerces.impl.XMLEntityManager.ScannedEntity 字段
    private int lineNumber = 1;
    private int columnNumber = 1;
    private boolean literal;
    private char[] ch = new char[DEFAULT_BUFFER_SIZE];
    private int position;
    private int count;
    private boolean mayReadChunks = false;
    
    // org.apache.xerces.impl.XMLScanner 字段
    private XMLString fString = new XMLString();    
    private XMLStringBuffer fStringBuffer = new XMLStringBuffer();
    private XMLStringBuffer fStringBuffer2 = new XMLStringBuffer();
    private final static String fVersionSymbol = "version";
    private final static String fEncodingSymbol = "encoding";
    private final static String fStandaloneSymbol = "standalone";
    
    // org.apache.xerces.impl.XMLDocumentFragmentScannerImpl 字段
    private int fMarkupDepth = 0;
    private String[] fStrings = new String[3];

    private ErrorDispatcher err;

    public XMLEncodingDetector() {
        fSymbolTable = new SymbolTable();
        fCurrentEntity = this;
    }

    /**
     * 自动检测给定输入流提供的XML文档的编码.
     *
     * 编码自动检测是根据XML 1.0规范进行的,
     * 附录 F.1: 无外部编码信息的检测.
     *
     * @return 两个元素的数组, 第一个元素(java.lang.String类型)包含自动检测的编码的名称,
     * 第二个元素(java.lang.Boolean类型)指定编码是否使用XML序言的(TRUE) 或自动检测的(FALSE)'encoding'属性指定.
     */
    public static Object[] getEncoding(String fname, JarFile jarFile,
                                       JspCompilationContext ctxt,
                                       ErrorDispatcher err)
        throws IOException, JasperException {
        InputStream inStream = JspUtil.getInputStream(fname, jarFile, ctxt,
                                                      err);
        XMLEncodingDetector detector = new XMLEncodingDetector();
        Object[] ret = detector.getEncoding(inStream, err);
        inStream.close();

        return ret;
    }

    private Object[] getEncoding(InputStream in, ErrorDispatcher err)
        throws IOException, JasperException {
        this.stream = in;
        this.err=err;
        createInitialReader();
        scanXMLDecl();
	
        return new Object[] { this.encoding,
                              new Boolean(this.isEncodingSetInProlog) };
    }
    
    // 子方法
    void endEntity() {
    }
    
    // 改编自:
    // org.apache.xerces.impl.XMLEntityManager.startEntity()
    private void createInitialReader() throws IOException, JasperException {

		// 使用 RewindableInputStream包装这个流
		stream = new RewindableInputStream(stream);
	
		// 如有必要，执行自动检测编码
		if (encoding == null) {
		    // 读取前四字节并确定编码
		    final byte[] b4 = new byte[4];
		    int count = 0;
		    for (; count<4; count++ ) {
		    	b4[count] = (byte)stream.read();
		    }
		    if (count == 4) {
				Object [] encodingDesc = getEncodingName(b4, count);
				encoding = (String)(encodingDesc[0]);
				isBigEndian = (Boolean)(encodingDesc[1]);
		
				stream.reset();
				// 特殊情况: Microsoft工具创建的BOM地 UTF-8 文件. 比对读取器进行额外的检查具有更高的效率. -Ac
				if (count > 2 && encoding.equals("UTF-8")) {
				    int b0 = b4[0] & 0xFF;
				    int b1 = b4[1] & 0xFF;
				    int b2 = b4[2] & 0xFF;
				    if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
						// 忽略前三字节...
						stream.skip(3);
				    }
				}
				reader = createReader(stream, encoding, isBigEndian);
		    } else {
		    	reader = createReader(stream, encoding, isBigEndian);
		    }
		}
    }

    /**
     * 创建一个能够在指定编码中读取给定输入流的读取器.
     *
     * @param inputStream  输入流.
     * @param encoding     输入流使用的编码名称. 如果用户已指定java编码名称是允许的, 然后编码名称可能是一个java的编码名称;
     *                     否则, 是一个 ianaEncoding 名称.
     * @param isBigEndian  对于编码(例如 uCS-4),其名称不能指定字节顺序, 这说明顺序是 bigEndian. null 意思是未知的或不相关的.
     *
     * @return Returns a reader.
     */
    private Reader createReader(InputStream inputStream, String encoding,
				Boolean isBigEndian)
                throws IOException, JasperException {

        // 规范的编码名称
        if (encoding == null) {
            encoding = "UTF-8";
        }

        // 尝试使用一个优化的 reader
        String ENCODING = encoding.toUpperCase(Locale.ENGLISH);
        if (ENCODING.equals("UTF-8")) {
            return new UTF8Reader(inputStream, fBufferSize);
        }
        if (ENCODING.equals("US-ASCII")) {
            return new ASCIIReader(inputStream, fBufferSize);
        }
        if (ENCODING.equals("ISO-10646-UCS-4")) {
            if (isBigEndian != null) {
                boolean isBE = isBigEndian.booleanValue();
                if (isBE) {
                    return new UCSReader(inputStream, UCSReader.UCS4BE);
                } else {
                    return new UCSReader(inputStream, UCSReader.UCS4LE);
                }
            } else {
                err.jspError("jsp.error.xml.encodingByteOrderUnsupported",
			     encoding);
            }
        }
        if (ENCODING.equals("ISO-10646-UCS-2")) {
            if (isBigEndian != null) { // 永远都不应该用这种编码发生...
                boolean isBE = isBigEndian.booleanValue();
                if (isBE) {
                    return new UCSReader(inputStream, UCSReader.UCS2BE);
                } else {
                    return new UCSReader(inputStream, UCSReader.UCS2LE);
                }
            } else {
                err.jspError("jsp.error.xml.encodingByteOrderUnsupported",
			     encoding);
            }
        }

        // 检查有效名称
        boolean validIANA = XMLChar.isValidIANAEncoding(encoding);
        boolean validJava = XMLChar.isValidJavaEncoding(encoding);
        if (!validIANA || (fAllowJavaEncodings && !validJava)) {
            err.jspError("jsp.error.xml.encodingDeclInvalid", encoding);
            // NOTE: AndyH 建议, 对失效, 使用 ISO Latin 1, 因为每个字节都是有效的 ISO Latin 1字符.
            //       它可能不能正确地翻译，但是如果我们在编码上失败了, 然后我们期望文档的内容是坏的. 这会防止无效UTF-8序列进行检测.
            //       只有在致命错误被打开之后继续这一点才是重要的. -Ac
            encoding = "ISO-8859-1";
        }

        // 尝试使用 Java reader
        String javaEncoding = EncodingMap.getIANA2JavaMapping(ENCODING);
        if (javaEncoding == null) {
            if (fAllowJavaEncodings) {
		javaEncoding = encoding;
            } else {
                err.jspError("jsp.error.xml.encodingDeclInvalid", encoding);
                // 看上面的注释.
                javaEncoding = "ISO8859_1";
            }
        }
        return new InputStreamReader(inputStream, javaEncoding);
    }

    /**
     * 返回从指定的字节自动检测到的IANA 编码名称, 和适当的编码的字节存储次序.
     *
     * @param b4    第一个四字节的输入.
     * @param count 实际读取的字节数目.
     * @return 两个元素的数组: 第一个元素是一个IANA编码的字符串, 第二个元素是一个Boolean(如果节点从小到大为true, 如果节点从大到小为false),
     * 			如果区别不相关为null.
     */
    private Object[] getEncodingName(byte[] b4, int count) {

        if (count < 2) {
            return new Object[]{"UTF-8", null};
        }

        // UTF-16, with BOM
        int b0 = b4[0] & 0xFF;
        int b1 = b4[1] & 0xFF;
        if (b0 == 0xFE && b1 == 0xFF) {
            // UTF-16, big-endian
            return new Object [] {"UTF-16BE", new Boolean(true)};
        }
        if (b0 == 0xFF && b1 == 0xFE) {
            // UTF-16, little-endian
            return new Object [] {"UTF-16LE", new Boolean(false)};
        }

        // 默认 UTF-8, 如果我们没有足够的字节来对编码进行良好的判断
        if (count < 3) {
            return new Object [] {"UTF-8", null};
        }

        // UTF-8 with a BOM
        int b2 = b4[2] & 0xFF;
        if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
            return new Object [] {"UTF-8", null};
        }

        // 默认 UTF-8, 如果我们没有足够的字节来对编码进行良好的判断
        if (count < 4) {
            return new Object [] {"UTF-8", null};
        }

        // 其它编码
        int b3 = b4[3] & 0xFF;
        if (b0 == 0x00 && b1 == 0x00 && b2 == 0x00 && b3 == 0x3C) {
            // UCS-4, 从小到大(1234)
            return new Object [] {"ISO-10646-UCS-4", new Boolean(true)};
        }
        if (b0 == 0x3C && b1 == 0x00 && b2 == 0x00 && b3 == 0x00) {
            // UCS-4, 从大到小(4321)
            return new Object [] {"ISO-10646-UCS-4", new Boolean(false)};
        }
        if (b0 == 0x00 && b1 == 0x00 && b2 == 0x3C && b3 == 0x00) {
            // UCS-4, 不寻常的字节顺序 (2143)
            // REVISIT: What should this be?
            return new Object [] {"ISO-10646-UCS-4", null};
        }
        if (b0 == 0x00 && b1 == 0x3C && b2 == 0x00 && b3 == 0x00) {
            // UCS-4, 不寻常的字节顺序 (3412)
            // REVISIT: What should this be?
            return new Object [] {"ISO-10646-UCS-4", null};
        }
        if (b0 == 0x00 && b1 == 0x3C && b2 == 0x00 && b3 == 0x3F) {
            // UTF-16, 从小到大, no BOM
            // (或者可能是 UCS-2...
            // REVISIT: What should this be?
            return new Object [] {"UTF-16BE", new Boolean(true)};
        }
        if (b0 == 0x3C && b1 == 0x00 && b2 == 0x3F && b3 == 0x00) {
            // UTF-16, 从大到小, no BOM
            // (或者可能是 UCS-2...
            return new Object [] {"UTF-16LE", new Boolean(false)};
        }
        if (b0 == 0x4C && b1 == 0x6F && b2 == 0xA7 && b3 == 0x94) {
            // EBCDIC
            // a la xerces1, 返回 CP037 而不是 EBCDIC
            return new Object [] {"CP037", null};
        }

        // 默认编码
        return new Object [] {"UTF-8", null};
    }

    /** 返回true, 如果正在扫描的当前实体是外部的. */
    public boolean isExternal() {
    	return true;
    }

    /**
     * 返回输入中的下一个字符.
     * <p>
     * <strong>Note:</strong> 不消耗字符.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     */
    public int peekChar() throws IOException {
	
		// 加载更多的字符
		if (fCurrentEntity.position == fCurrentEntity.count) {
		    load(0, true);
		}
		
		// peek at character
		int c = fCurrentEntity.ch[fCurrentEntity.position];
	
		// return peeked character
		if (fCurrentEntity.isExternal()) {
		    return c != '\r' ? c : '\n';
		} else {
		    return c;
		}
    }
    
    /**
     * 返回输入中的下一个字符.
     * <p>
     * <strong>Note:</strong>消耗字符.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     */
    public int scanChar() throws IOException {

		// 加载更多的字符
		if (fCurrentEntity.position == fCurrentEntity.count) {
		    load(0, true);
		}
	
		// 扫描字符
		int c = fCurrentEntity.ch[fCurrentEntity.position++];
		boolean external = false;
		if (c == '\n' ||
		    (c == '\r' && (external = fCurrentEntity.isExternal()))) {
		    fCurrentEntity.lineNumber++;
		    fCurrentEntity.columnNumber = 1;
		    if (fCurrentEntity.position == fCurrentEntity.count) {
				fCurrentEntity.ch[0] = (char)c;
				load(1, false);
		    }
		    if (c == '\r' && external) {
				if (fCurrentEntity.ch[fCurrentEntity.position++] != '\n') {
				    fCurrentEntity.position--;
				}
				c = '\n';
		    }
		}
		// 返回被扫描的字符
		fCurrentEntity.columnNumber++;
		return c;
    }

    /**
     * 返回一个匹配作为一个符号直接出现在输入中的Name生成的字符串, 或null 如果没有Name 字符串是存在的.
     * <p>
     * <strong>Note:</strong> 消耗Name 字符.
     * <p>
     * <strong>Note:</strong> 返回的字符串必须是符号. SymbolTable 可用于此目的.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     */
    public String scanName() throws IOException {
	
		// 加载更多字符
		if (fCurrentEntity.position == fCurrentEntity.count) {
		    load(0, true);
		}
		
		// 扫描名称
		int offset = fCurrentEntity.position;
		if (XMLChar.isNameStart(fCurrentEntity.ch[offset])) {
		    if (++fCurrentEntity.position == fCurrentEntity.count) {
			fCurrentEntity.ch[0] = fCurrentEntity.ch[offset];
			offset = 0;
			if (load(1, false)) {
			    fCurrentEntity.columnNumber++;
			    String symbol = fSymbolTable.addSymbol(fCurrentEntity.ch,
								   0, 1);
			    return symbol;
			}
	    }
	    while (XMLChar.isName(fCurrentEntity.ch[fCurrentEntity.position])) {
				if (++fCurrentEntity.position == fCurrentEntity.count) {
				    int length = fCurrentEntity.position - offset;
				    if (length == fBufferSize) {
					//必须调整缓冲区的大小
					char[] tmp = new char[fBufferSize * 2];
					System.arraycopy(fCurrentEntity.ch, offset,
							 tmp, 0, length);
					fCurrentEntity.ch = tmp;
					fBufferSize *= 2;
				    } else {
					System.arraycopy(fCurrentEntity.ch, offset,
							 fCurrentEntity.ch, 0, length);
				    }
				    offset = 0;
				    if (load(length, false)) {
					break;
				    }
				}
		    }
		}
		int length = fCurrentEntity.position - offset;
		fCurrentEntity.columnNumber += length;
	
		// 返回名称
		String symbol = null;
		if (length > 0) {
		    symbol = fSymbolTable.addSymbol(fCurrentEntity.ch, offset, length);
		}
		return symbol;
    }

    /**
     * 扫描一定范围内的属性值数据, 设置适当的XMLString结构字段.
     * <p>
     * <strong>Note:</strong> 消耗的字符.
     * <p>
     * <strong>Note:</strong>此方法不保证返回运行时间最长的属性值数据. 由于输入缓冲区的结束或其他原因，此方法可能返回到引号字符之前.
     * <p>
     * <strong>Note:</strong> XMLString结构中包含的字段不能保证在实体扫描器的后续调用中保持有效.
     * 因此, 调用者负责立即使用返回的字符数据或其副本.
     *
     * @param quote   表示属性值数据结束的引号字符.
     * @param content 要填充的内容结构.
     *
     * @return 返回输入的下一个字符. 这个值可能是 -1, 不表示文件结束.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     */
    public int scanLiteral(int quote, XMLString content) throws IOException {

		// 加载更多字符
		if (fCurrentEntity.position == fCurrentEntity.count) {
		    load(0, true);
		} else if (fCurrentEntity.position == fCurrentEntity.count - 1) {
		    fCurrentEntity.ch[0] = fCurrentEntity.ch[fCurrentEntity.count - 1];
		    load(1, false);
		    fCurrentEntity.position = 0;
		}
	
		// 标准化新行
		int offset = fCurrentEntity.position;
		int c = fCurrentEntity.ch[offset];
		int newlines = 0;
		boolean external = fCurrentEntity.isExternal();
		if (c == '\n' || (c == '\r' && external)) {
		    do {
				c = fCurrentEntity.ch[fCurrentEntity.position++];
				if (c == '\r' && external) {
				    newlines++;
				    fCurrentEntity.lineNumber++;
				    fCurrentEntity.columnNumber = 1;
				    if (fCurrentEntity.position == fCurrentEntity.count) {
						offset = 0;
						fCurrentEntity.position = newlines;
						if (load(newlines, false)) {
						    break;
						}
				    }
				    if (fCurrentEntity.ch[fCurrentEntity.position] == '\n') {
						fCurrentEntity.position++;
						offset++;
				    }
				    /*** NEWLINE NORMALIZATION ***/
				    else {
				    	newlines++;
				    }
				    /***/
				} else if (c == '\n') {
				    newlines++;
				    fCurrentEntity.lineNumber++;
				    fCurrentEntity.columnNumber = 1;
				    if (fCurrentEntity.position == fCurrentEntity.count) {
						offset = 0;
						fCurrentEntity.position = newlines;
						if (load(newlines, false)) {
						    break;
						}
				    }
				    /*** NEWLINE NORMALIZATION ***
					 if (fCurrentEntity.ch[fCurrentEntity.position] == '\r'
					 && external) {
					 fCurrentEntity.position++;
					 offset++;
					 }
					 /***/
				} else {
				    fCurrentEntity.position--;
				    break;
				}
		    } while (fCurrentEntity.position < fCurrentEntity.count - 1);
		    for (int i = offset; i < fCurrentEntity.position; i++) {
		    	fCurrentEntity.ch[i] = '\n';
		    }
		    int length = fCurrentEntity.position - offset;
		    if (fCurrentEntity.position == fCurrentEntity.count - 1) {
				content.setValues(fCurrentEntity.ch, offset, length);
				return -1;
		    }
		}
	
		// 扫描文本值
		while (fCurrentEntity.position < fCurrentEntity.count) {
		    c = fCurrentEntity.ch[fCurrentEntity.position++];
		    if ((c == quote &&
			 (!fCurrentEntity.literal || external))
			|| c == '%' || !XMLChar.isContent(c)) {
				fCurrentEntity.position--;
				break;
		    }
		}
		int length = fCurrentEntity.position - offset;
		fCurrentEntity.columnNumber += length - newlines;
		content.setValues(fCurrentEntity.ch, offset, length);
	
		// 返回下一个字符
		if (fCurrentEntity.position != fCurrentEntity.count) {
		    c = fCurrentEntity.ch[fCurrentEntity.position];
		    // NOTE: 如果我们正在扩展一个出现在文本中的实体，我们不想意外地显示文字的结束. -Ac
		    if (c == quote && fCurrentEntity.literal) {
		    	c = -1;
		    }
		} else {
		    c = -1;
		}
		return c;
    }

    /**
     * 扫描字符数据到指定分隔符的范围, 适当的设置 XMLString结构的字段.
     * <p>
     * <strong>Note:</strong>消耗字符.
     * <p>
     * <strong>Note:</strong> 假定内部缓冲区至少和分隔符是相同的大小, 或更大, 分隔符至少包含一个字符.
     * <p>
     * <strong>Note:</strong>此方法不保证返回运行时间最长的属性值数据. 由于输入缓冲区的结束或其他原因，此方法可能返回到引号字符之前.
     * <p>
     * <strong>Note:</strong> XMLString结构中包含的字段不能保证在实体扫描器的后续调用中保持有效.
     * 因此, 调用者负责立即使用返回的字符数据或其副本.
     *
     * @param delimiter 表示要扫描的字符数据的结束的字符串.
     * @param buffer    要填充的数据结构.
     *
     * @return 如果有更多数据可扫描返回true, 或者false.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     */
    public boolean scanData(String delimiter, XMLStringBuffer buffer) throws IOException {

		boolean done = false;
		int delimLen = delimiter.length();
		char charAt0 = delimiter.charAt(0);
		boolean external = fCurrentEntity.isExternal();
		do {
	    
		    // 加载更多字符
		    if (fCurrentEntity.position == fCurrentEntity.count) {
		    	load(0, true);
		    } else if (fCurrentEntity.position >= fCurrentEntity.count - delimLen) {
				System.arraycopy(fCurrentEntity.ch, fCurrentEntity.position,
						 fCurrentEntity.ch, 0, fCurrentEntity.count - fCurrentEntity.position);
				load(fCurrentEntity.count - fCurrentEntity.position, false);
				fCurrentEntity.position = 0;
		    } 
		    if (fCurrentEntity.position >= fCurrentEntity.count - delimLen) {
				//输入一定有问题: 即, 文件以未结束的注释结束
				int length = fCurrentEntity.count - fCurrentEntity.position;
				buffer.append (fCurrentEntity.ch, fCurrentEntity.position,
					       length); 
				fCurrentEntity.columnNumber += fCurrentEntity.count;
				fCurrentEntity.position = fCurrentEntity.count;
				load(0,true);
				return false;
		    }
	    
		    // 标准化新行
		    int offset = fCurrentEntity.position;
		    int c = fCurrentEntity.ch[offset];
		    int newlines = 0;
		    if (c == '\n' || (c == '\r' && external)) {
				do {
				    c = fCurrentEntity.ch[fCurrentEntity.position++];
				    if (c == '\r' && external) {
						newlines++;
						fCurrentEntity.lineNumber++;
						fCurrentEntity.columnNumber = 1;
						if (fCurrentEntity.position == fCurrentEntity.count) {
						    offset = 0;
						    fCurrentEntity.position = newlines;
						    if (load(newlines, false)) {
						    	break;
						    }
						}
						if (fCurrentEntity.ch[fCurrentEntity.position] == '\n') {
						    fCurrentEntity.position++;
						    offset++;
						}
						/*** NEWLINE NORMALIZATION ***/
						else {
						    newlines++;
						}
				    } else if (c == '\n') {
						newlines++;
						fCurrentEntity.lineNumber++;
						fCurrentEntity.columnNumber = 1;
						if (fCurrentEntity.position == fCurrentEntity.count) {
						    offset = 0;
						    fCurrentEntity.position = newlines;
						    fCurrentEntity.count = newlines;
						    if (load(newlines, false)) {
						    	break;
						    }
						}
				    } else {
						fCurrentEntity.position--;
						break;
				    }
				} while (fCurrentEntity.position < fCurrentEntity.count - 1);
				for (int i = offset; i < fCurrentEntity.position; i++) {
				    fCurrentEntity.ch[i] = '\n';
				}
				int length = fCurrentEntity.position - offset;
				if (fCurrentEntity.position == fCurrentEntity.count - 1) {
				    buffer.append(fCurrentEntity.ch, offset, length);
				    return true;
				}
		    }
	    
		    // 遍历缓冲区寻找分隔符
			OUTER: while (fCurrentEntity.position < fCurrentEntity.count) {
			    c = fCurrentEntity.ch[fCurrentEntity.position++];
			    if (c == charAt0) {
					// 看起来刚刚碰到分隔符
					int delimOffset = fCurrentEntity.position - 1;
					for (int i = 1; i < delimLen; i++) {
					    if (fCurrentEntity.position == fCurrentEntity.count) {
							fCurrentEntity.position -= i;
							break OUTER;
					    }
					    c = fCurrentEntity.ch[fCurrentEntity.position++];
					    if (delimiter.charAt(i) != c) {
							fCurrentEntity.position--;
							break;
					    }
					}
					if (fCurrentEntity.position == delimOffset + delimLen) {
					    done = true;
					    break;
					}
			    } else if (c == '\n' || (external && c == '\r')) {
					fCurrentEntity.position--;
					break;
			    } else if (XMLChar.isInvalid(c)) {
					fCurrentEntity.position--;
					int length = fCurrentEntity.position - offset;
					fCurrentEntity.columnNumber += length - newlines;
					buffer.append(fCurrentEntity.ch, offset, length); 
					return true;
			    }
			}
		    int length = fCurrentEntity.position - offset;
		    fCurrentEntity.columnNumber += length - newlines;
		    if (done) {
		    	length -= delimLen;
		    }
		    buffer.append (fCurrentEntity.ch, offset, length);
		    // 如果字符串被跳过，则返回true
		} while (!done);
		return !done;
    }

    /**
     * 跳过输入中直接出现的字符.
     * <p>
     * <strong>Note:</strong>只有在匹配指定字符时才使用字符.
     *
     * @param c 跳过字符.
     *
     * @return 返回true, 如果跳过字符.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     */
    public boolean skipChar(int c) throws IOException {
		// 加载更多字符
		if (fCurrentEntity.position == fCurrentEntity.count) {
		    load(0, true);
		}
	
		// 跳过字符
		int cc = fCurrentEntity.ch[fCurrentEntity.position];
		if (cc == c) {
		    fCurrentEntity.position++;
		    if (c == '\n') {
				fCurrentEntity.lineNumber++;
				fCurrentEntity.columnNumber = 1;
		    }
		    else {
		    	fCurrentEntity.columnNumber++;
		    }
		    return true;
		} else if (c == '\n' && cc == '\r' && fCurrentEntity.isExternal()) {
		    // 处理新行
		    if (fCurrentEntity.position == fCurrentEntity.count) {
				fCurrentEntity.ch[0] = (char)cc;
				load(1, false);
		    }
		    fCurrentEntity.position++;
		    if (fCurrentEntity.ch[fCurrentEntity.position] == '\n') {
		    	fCurrentEntity.position++;
		    }
		    fCurrentEntity.lineNumber++;
		    fCurrentEntity.columnNumber = 1;
		    return true;
		}
	
		// 不跳过字符
		return false;
    }

    /**
     * 跳过输入中直接出现的空格字符.
     * <p>
     * <strong>Note:</strong>只有字符是空格字符时才被使用.
     *
     * @return 返回true, 如果跳过至少一个空格字符.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     */
    public boolean skipSpaces() throws IOException {

		// 加载更多字符
		if (fCurrentEntity.position == fCurrentEntity.count) {
		    load(0, true);
		}
	
		// 跳过空格
		int c = fCurrentEntity.ch[fCurrentEntity.position];
		if (XMLChar.isSpace(c)) {
		    boolean external = fCurrentEntity.isExternal();
		    do {
				boolean entityChanged = false;
				// 处理新行
				if (c == '\n' || (external && c == '\r')) {
				    fCurrentEntity.lineNumber++;
				    fCurrentEntity.columnNumber = 1;
				    if (fCurrentEntity.position == fCurrentEntity.count - 1) {
						fCurrentEntity.ch[0] = (char)c;
						entityChanged = load(1, true);
						if (!entityChanged)
	                        // 修改加载位置为 1, 当实体未改变时需要还原它
						    fCurrentEntity.position = 0;
				    }
				    if (c == '\r' && external) {
						// REVISIT: 是否需要更新以修复 #x0D ^#x0A 换行符的规范化问题? -Ac
						if (fCurrentEntity.ch[++fCurrentEntity.position] != '\n') {
						    fCurrentEntity.position--;
						}
				    }
				    /*** NEWLINE NORMALIZATION ***
					 else {
					 if (fCurrentEntity.ch[fCurrentEntity.position + 1] == '\r'
					 && external) {
					 fCurrentEntity.position++;
					 }
					 }
					 /***/
				} else {
				    fCurrentEntity.columnNumber++;
				}
				// 加载更多字符
				if (!entityChanged)
				    fCurrentEntity.position++;
				if (fCurrentEntity.position == fCurrentEntity.count) {
				    load(0, true);
				}
		    } while (XMLChar.isSpace(c = fCurrentEntity.ch[fCurrentEntity.position]));
		    return true;
		}
	
		// 没有空格
		return false;
    }

    /**
     * 跳过输入中直接出现的特殊字符.
     * <p>
     * <strong>Note:</strong>只有当它们是空格字符的时候才消耗字符.
     *
     * @param s 要跳过的字符串.
     *
     * @return 返回true, 如果跳过字符串.
     *
     * @throws IOException  Thrown if i/o error occurs.
     * @throws EOFException Thrown on end of file.
     */
    public boolean skipString(String s) throws IOException {

		// 加载更多字符
		if (fCurrentEntity.position == fCurrentEntity.count) {
		    load(0, true);
		}
	
		// 跳过字符串
		final int length = s.length();
		for (int i = 0; i < length; i++) {
		    char c = fCurrentEntity.ch[fCurrentEntity.position++];
		    if (c != s.charAt(i)) {
				fCurrentEntity.position -= i + 1;
				return false;
		    }
		    if (i < length - 1 && fCurrentEntity.position == fCurrentEntity.count) {
				System.arraycopy(fCurrentEntity.ch, fCurrentEntity.count - i - 1, fCurrentEntity.ch, 0, i + 1);
				// REVISIT: 可以跳过实体边界的字符串吗? -Ac
				if (load(i + 1, false)) {
				    fCurrentEntity.position -= i + 1;
				    return false;
				}
		    }
		}
		fCurrentEntity.columnNumber += length;
		return true;
    }

    /**
     * 加载一组文本.
     *
     * @param offset       进入字符缓冲区以读取下一批字符的偏移量.
     * @param changeEntity True 如果加载应该改变实体末端的实体, 否则，将当前实体保留在适当位置，实体边界将由返回值发出信号.
     *
     * @returns 返回true, 如果实体因为此加载操作而更改.
     */
    final boolean load(int offset, boolean changeEntity) throws IOException {

		// 读取字符
		int length = fCurrentEntity.mayReadChunks?
		    (fCurrentEntity.ch.length - offset):
		    (DEFAULT_XMLDECL_BUFFER_SIZE);
		int count = fCurrentEntity.reader.read(fCurrentEntity.ch, offset, length);
	
		// 重置计数和位置
		boolean entityChanged = false;
		if (count != -1) {
		    if (count != 0) {
				fCurrentEntity.count = count + offset;
				fCurrentEntity.position = offset;
		    }
		} else {
		    fCurrentEntity.count = offset;
		    fCurrentEntity.position = offset;
		    entityChanged = true;
		    if (changeEntity) {
				endEntity();
				if (fCurrentEntity == null) {
				    throw new EOFException();
				}
				// 处理后缘
				if (fCurrentEntity.position == fCurrentEntity.count) {
				    load(0, false);
				}
		    }
		}
		return entityChanged;
    }

    /**
     * 这个类封装了字节输入流.
     * java.io.InputStreams 不提供重新读取处理字节的功能, 而且他们有读取不止一个字符的习惯, 当调用它们的read()方法.
     * 意味着, 一旦发现一个节点的 true (declared)编码, 既不能回头读取整个文档，也不能使用一个新reader重新开始读取.
     *
     * 这个类允许通过设置一个标记倒回一个 inputStream, 和流复位到那个位置.
     * <strong>类假设它需要每次调用读取一个字符, 当调用它的 read() 方法时, 但是使用下列的 InputStream的 read(char[], offset length)方法--它不会缓冲这样读取的数据!</strong>
     */
    private final class RewindableInputStream extends InputStream {

        private InputStream fInputStream;
        private byte[] fData;
        private int fStartOffset;
        private int fEndOffset;
        private int fOffset;
        private int fLength;
        private int fMark;

        public RewindableInputStream(InputStream is) {
            fData = new byte[DEFAULT_XMLDECL_BUFFER_SIZE];
            fInputStream = is;
            fStartOffset = 0;
            fEndOffset = -1;
            fOffset = 0;
            fLength = 0;
            fMark = 0;
        }

        public void setStartOffset(int offset) {
            fStartOffset = offset;
        }

        public void rewind() {
            fOffset = fStartOffset;
        }

        public int read() throws IOException {
            int b = 0;
            if (fOffset < fLength) {
                return fData[fOffset++] & 0xff;
            }
            if (fOffset == fEndOffset) {
                return -1;
            }
            if (fOffset == fData.length) {
                byte[] newData = new byte[fOffset << 1];
                System.arraycopy(fData, 0, newData, 0, fOffset);
                fData = newData;
            }
            b = fInputStream.read();
            if (b == -1) {
                fEndOffset = fOffset;
                return -1;
            }
            fData[fLength++] = (byte)b;
            fOffset++;
            return b & 0xff;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            int bytesLeft = fLength - fOffset;
            if (bytesLeft == 0) {
                if (fOffset == fEndOffset) {
                    return -1;
                }
                // 最好为贪婪的读者多买一些...
                if (fCurrentEntity.mayReadChunks) {
                    return fInputStream.read(b, off, len);
                }
                int returnedVal = read();
                if (returnedVal == -1) {
                    fEndOffset = fOffset;
                    return -1;
                }
                b[off] = (byte)returnedVal;
                return 1;
            }
            if (len < bytesLeft) {
                if (len <= 0) {
                    return 0;
                }
            } else {
                len = bytesLeft;
            }
            if (b != null) {
                System.arraycopy(fData, fOffset, b, off, len);
            }
            fOffset += len;
            return len;
        }

        public long skip(long n) throws IOException {
            int bytesLeft;
            if (n <= 0) {
                return 0;
            }
            bytesLeft = fLength - fOffset;
            if (bytesLeft == 0) {
                if (fOffset == fEndOffset) {
                    return 0;
                }
                return fInputStream.skip(n);
            }
            if (n <= bytesLeft) {
                fOffset += n;
                return n;
            }
            fOffset += bytesLeft;
            if (fOffset == fEndOffset) {
                return bytesLeft;
            }
            n -= bytesLeft;
		    /*
		     * 在某种程度上说, 当这个类不允许一次读取多于一个字节时, 它是"blocking". 
		     * available()方法应该说明不阻塞可以读取多少, 所以当我们处于这种模式时, 它只表示缓冲区中的字节可用;
		     * 否则, 在底层 InputStream上的available()的结果是适当的.
		     */
            return fInputStream.skip(n) + bytesLeft;
        }

        public int available() throws IOException {
            int bytesLeft = fLength - fOffset;
            if (bytesLeft == 0) {
                if (fOffset == fEndOffset) {
                    return -1;
                }
                return fCurrentEntity.mayReadChunks ? fInputStream.available() : 0;
            }
            return bytesLeft;
        }

        public void mark(int howMuch) {
            fMark = fOffset;
        }

        public void reset() {
            fOffset = fMark;
        }

        public boolean markSupported() {
            return true;
        }

        public void close() throws IOException {
            if (fInputStream != null) {
                fInputStream.close();
                fInputStream = null;
            }
        }
    }

    private void scanXMLDecl() throws IOException, JasperException {

		if (skipString("<?xml")) {
		    fMarkupDepth++;
		    // NOTE: 特殊情况下，文档以PI开头，其名称以"xml"开头 (e.g. "xmlfoo")
		    if (XMLChar.isName(peekChar())) {
				fStringBuffer.clear();
				fStringBuffer.append("xml");
				while (XMLChar.isName(peekChar())) {
				    fStringBuffer.append((char)scanChar());
				}
				String target = fSymbolTable.addSymbol(fStringBuffer.ch,
								       fStringBuffer.offset,
								       fStringBuffer.length);
				scanPIData(target, fString);
		    } else {
		    	scanXMLDeclOrTextDecl(false);
		    }
		}
    }
    
    /**
     * 扫描XML或文本声明.
     * <p>
     * <pre>
     * [23] XMLDecl ::= '&lt;?xml' VersionInfo EncodingDecl? SDDecl? S? '?>'
     * [24] VersionInfo ::= S 'version' Eq (' VersionNum ' | " VersionNum ")
     * [80] EncodingDecl ::= S 'encoding' Eq ('"' EncName '"' |  "'" EncName "'" )
     * [81] EncName ::= [A-Za-z] ([A-Za-z0-9._] | '-')*
     * [32] SDDecl ::= S 'standalone' Eq (("'" ('yes' | 'no') "'")
     *                 | ('"' ('yes' | 'no') '"'))
     *
     * [77] TextDecl ::= '&lt;?xml' VersionInfo? EncodingDecl S? '?>'
     * </pre>
     *
     * @param scanningTextDecl True 如果要对文本声明进行扫描，而不是XML声明.
     */
    private void scanXMLDeclOrTextDecl(boolean scanningTextDecl) 
        throws IOException, JasperException {

        // 扫描说明
        scanXMLDeclOrTextDecl(scanningTextDecl, fStrings);
        fMarkupDepth--;

        // 伪属性值
        String encodingPseudoAttr = fStrings[1];

        // 在阅读器上设置编码
        if (encodingPseudoAttr != null) {
            isEncodingSetInProlog = true;
            encoding = encodingPseudoAttr;
        }
    }

    /**
     * 扫描XML或文本声明.
     * <p>
     * <pre>
     * [23] XMLDecl ::= '<?xml' VersionInfo EncodingDecl? SDDecl? S? '?>'
     * [24] VersionInfo ::= S 'version' Eq (' VersionNum ' | " VersionNum ")
     * [80] EncodingDecl ::= S 'encoding' Eq ('"' EncName '"' |  "'" EncName "'" )
     * [81] EncName ::= [A-Za-z] ([A-Za-z0-9._] | '-')*
     * [32] SDDecl ::= S 'standalone' Eq (("'" ('yes' | 'no') "'")
     *                 | ('"' ('yes' | 'no') '"'))
     *
     * [77] TextDecl ::= '<?xml' VersionInfo? EncodingDecl S? '?>'
     * </pre>
     *
     * @param scanningTextDecl True 如果要对文本声明进行扫描，而不是XML声明.
     * @param pseudoAttributeValues 返回版本的大小为3的数组, 编码和独立的伪属性值 (按照这个顺序).
     *
     * <strong>Note:</strong> 该方法采用 fString, 其中的任何东西在调用的时候丢失.
     */
    private void scanXMLDeclOrTextDecl(boolean scanningTextDecl,
				       String[] pseudoAttributeValues) 
                throws IOException, JasperException {

        // 伪属性值
        String version = null;
        String encoding = null;
        String standalone = null;

        // 扫描的伪属性
        final int STATE_VERSION = 0;
        final int STATE_ENCODING = 1;
        final int STATE_STANDALONE = 2;
        final int STATE_DONE = 3;
        int state = STATE_VERSION;

        boolean dataFoundForTarget = false;
        boolean sawSpace = skipSpaces();
        while (peekChar() != '?') {
            dataFoundForTarget = true;
            String name = scanPseudoAttribute(scanningTextDecl, fString);
            switch (state) {
                case STATE_VERSION: {
                    if (name == fVersionSymbol) {
                        if (!sawSpace) {
                            reportFatalError(scanningTextDecl
                                       ? "jsp.error.xml.spaceRequiredBeforeVersionInTextDecl"
                                       : "jsp.error.xml.spaceRequiredBeforeVersionInXMLDecl",
                                             null);
                        }
                        version = fString.toString();
                        state = STATE_ENCODING;
                        if (!version.equals("1.0")) {
                            // REVISIT: XML记录说我们应该在这种情况下抛出错误.
                            // some may object the throwing of fatalError.
                            err.jspError("jsp.error.xml.versionNotSupported", version);
                        }
                    } else if (name == fEncodingSymbol) {
                        if (!scanningTextDecl) {
                            err.jspError("jsp.error.xml.versionInfoRequired");
                        }
                        if (!sawSpace) {
                            reportFatalError(scanningTextDecl
                                      ? "jsp.error.xml.spaceRequiredBeforeEncodingInTextDecl"
                                      : "jsp.error.xml.spaceRequiredBeforeEncodingInXMLDecl",
                                             null);
                        }
                        encoding = fString.toString();
                        state = scanningTextDecl ? STATE_DONE : STATE_STANDALONE;
                    } else {
                        if (scanningTextDecl) {
                            err.jspError("jsp.error.xml.encodingDeclRequired");
                        } else {
                            err.jspError("jsp.error.xml.versionInfoRequired");
                        }
                    }
                    break;
                }
                case STATE_ENCODING: {
                    if (name == fEncodingSymbol) {
                        if (!sawSpace) {
                            reportFatalError(scanningTextDecl
                                      ? "jsp.error.xml.spaceRequiredBeforeEncodingInTextDecl"
                                      : "jsp.error.xml.spaceRequiredBeforeEncodingInXMLDecl",
                                             null);
                        }
                        encoding = fString.toString();
                        state = scanningTextDecl ? STATE_DONE : STATE_STANDALONE;
                        // TODO: 检查编码名称; 在实体扫描器上设置编码
                    } else if (!scanningTextDecl && name == fStandaloneSymbol) {
                        if (!sawSpace) {
                            err.jspError("jsp.error.xml.spaceRequiredBeforeStandalone");
                        }
                        standalone = fString.toString();
                        state = STATE_DONE;
                        if (!standalone.equals("yes") && !standalone.equals("no")) {
                            err.jspError("jsp.error.xml.sdDeclInvalid");
                        }
                    } else {
                        err.jspError("jsp.error.xml.encodingDeclRequired");
                    }
                    break;
                }
                case STATE_STANDALONE: {
                    if (name == fStandaloneSymbol) {
                        if (!sawSpace) {
                            err.jspError("jsp.error.xml.spaceRequiredBeforeStandalone");
                        }
                        standalone = fString.toString();
                        state = STATE_DONE;
                        if (!standalone.equals("yes") && !standalone.equals("no")) {
                            err.jspError("jsp.error.xml.sdDeclInvalid");
                        }
                    } else {
                    	err.jspError("jsp.error.xml.encodingDeclRequired");
                    }
                    break;
                }
                default: {
                    err.jspError("jsp.error.xml.noMorePseudoAttributes");
                }
            }
            sawSpace = skipSpaces();
        }
        // REVISIT: 应该删除这个错误报告吗?
        if (scanningTextDecl && state != STATE_DONE) {
            err.jspError("jsp.error.xml.morePseudoAttributes");
        }
        
        // 如果在XML或文本说明中没有数据, 没有版本或以上的编码信息错误报告.
        if (scanningTextDecl) {
            if (!dataFoundForTarget && encoding == null) {
                err.jspError("jsp.error.xml.encodingDeclRequired");
            }
        } else {
            if (!dataFoundForTarget && version == null) {
                err.jspError("jsp.error.xml.versionInfoRequired");
            }
        }

        // end
        if (!skipChar('?')) {
            err.jspError("jsp.error.xml.xmlDeclUnterminated");
        }
        if (!skipChar('>')) {
            err.jspError("jsp.error.xml.xmlDeclUnterminated");

        }
        
        // 填充返回数组
        pseudoAttributeValues[0] = version;
        pseudoAttributeValues[1] = encoding;
        pseudoAttributeValues[2] = standalone;
    }

    /**
     * 扫描伪属性.
     *
     * @param scanningTextDecl True 如果扫描伪属性为了一个TextDecl; false 如果扫描 XMLDecl. 此标志需要报告正确的错误类型.
     * @param value            要填入属性值的字符串.
     *
     * @return 属性名称
     *
     * <strong>Note:</strong>这个方法使用 fStringBuffer2, 其中的任何东西在调用的时候丢失.
     */
    public String scanPseudoAttribute(boolean scanningTextDecl, 
                                      XMLString value) 
                throws IOException, JasperException {

        String name = scanName();
        if (name == null) {
            err.jspError("jsp.error.xml.pseudoAttrNameExpected");
        }
        skipSpaces();
        if (!skipChar('=')) {
            reportFatalError(scanningTextDecl ?
			     "jsp.error.xml.eqRequiredInTextDecl"
                             : "jsp.error.xml.eqRequiredInXMLDecl",
			     name);
        }
        skipSpaces();
        int quote = peekChar();
        if (quote != '\'' && quote != '"') {
            reportFatalError(scanningTextDecl ?
			     "jsp.error.xml.quoteRequiredInTextDecl"
                             : "jsp.error.xml.quoteRequiredInXMLDecl" ,
			     name);
        }
        scanChar();
        int c = scanLiteral(quote, value);
        if (c != quote) {
            fStringBuffer2.clear();
            do {
                fStringBuffer2.append(value);
                if (c != -1) {
                    if (c == '&' || c == '%' || c == '<' || c == ']') {
                        fStringBuffer2.append((char)scanChar());
                    } else if (XMLChar.isHighSurrogate(c)) {
                        scanSurrogates(fStringBuffer2);
                    } else if (XMLChar.isInvalid(c)) {
                        String key = scanningTextDecl
                            ? "jsp.error.xml.invalidCharInTextDecl"
			    : "jsp.error.xml.invalidCharInXMLDecl";
                        reportFatalError(key, Integer.toString(c, 16));
                        scanChar();
                    }
                }
                c = scanLiteral(quote, value);
            } while (c != quote);
            fStringBuffer2.append(value);
            value.setValues(fStringBuffer2);
        }
        if (!skipChar(quote)) {
            reportFatalError(scanningTextDecl ?
			     "jsp.error.xml.closeQuoteMissingInTextDecl"
                             : "jsp.error.xml.closeQuoteMissingInXMLDecl",
			     name);
        }

        // return
        return name;
    }
    
    /**
     * 扫描处理数据. 这需要处理文档以处理指令开始的情况，其目标名称以"xml"开头.
     *
     * <strong>Note:</strong>这个方法使用 fStringBuffer, 其中的任何东西在调用的时候丢失.
     *
     * @param target PI 目标
     * @param data 填写的数据的字符串
     */
    private void scanPIData(String target, XMLString data) throws IOException, JasperException {

        // 检查目标
        if (target.length() == 3) {
            char c0 = Character.toLowerCase(target.charAt(0));
            char c1 = Character.toLowerCase(target.charAt(1));
            char c2 = Character.toLowerCase(target.charAt(2));
            if (c0 == 'x' && c1 == 'm' && c2 == 'l') {
                err.jspError("jsp.error.xml.reservedPITarget");
            }
        }

        // 空格
        if (!skipSpaces()) {
            if (skipString("?>")) {
                // 查找结尾, 没有数据
                data.clear();
                return;
            } else {
                // 如果有数据，应该有一些空格
                err.jspError("jsp.error.xml.spaceRequiredInPI");
            }
        }

        fStringBuffer.clear();
        // 数据
        if (scanData("?>", fStringBuffer)) {
            do {
                int c = peekChar();
                if (c != -1) {
                    if (XMLChar.isHighSurrogate(c)) {
                        scanSurrogates(fStringBuffer);
                    } else if (XMLChar.isInvalid(c)) {
                        err.jspError("jsp.error.xml.invalidCharInPI",
				     Integer.toHexString(c));
                        scanChar();
                    }
                }
            } while (scanData("?>", fStringBuffer));
        }
        data.setValues(fStringBuffer);
    }

    /**
     * 扫描代理并将它们追加到指定的缓冲区.
     * <p>
     * <strong>Note:</strong>这假定当前char已经被标识为高位代理.
     *
     * @param buf 要追加读取代理的StringBuffer.
     * @returns True 如果成功.
     */
    private boolean scanSurrogates(XMLStringBuffer buf)
        throws IOException, JasperException {

        int high = scanChar();
        int low = peekChar();
        if (!XMLChar.isLowSurrogate(low)) {
            err.jspError("jsp.error.xml.invalidCharInContent",
			Integer.toString(high, 16));
            return false;
        }
        scanChar();

        // 将代理转换为补充字符
        int c = XMLChar.supplemental((char)high, (char)low);

        // 补充字符必须是有效的XML字符
        if (!XMLChar.isValid(c)) {
            err.jspError("jsp.error.xml.invalidCharInContent",
			 Integer.toString(c, 16)); 
            return false;
        }

        // 填充缓冲区
        buf.append((char)high);
        buf.append((char)low);

        return true;
    }

    /**
     * 在所有XML扫描器中使用的便利功能.
     */
    private void reportFatalError(String msgId, String arg)
                throws JasperException {
        err.jspError(msgId, arg);
    }
}
