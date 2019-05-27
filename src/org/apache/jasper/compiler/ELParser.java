package org.apache.jasper.compiler;

/**
 * 这个类实现了EL表达式的解析器.
 *
 * 它以字符串的形式 xxx${..}yyy${..}zzz 等等, 把它变成一个ELNode.Nodes.
 *
 * 目前, 它只处理文本外部的 ${..} 和 ${ ..}中的函数.
 */
public class ELParser {

    private Token curToken;	// current token
    private ELNode.Nodes expr;
    private ELNode.Nodes ELexpr;
    private int index;		// 表达式当前索引
    private String expression;	// EL表达式
    private boolean escapeBS;	// '\'是否是EL以外文本中的转义字符?

    private static final String reservedWords[] = {
        "and", "div", "empty", "eq", "false",
        "ge", "gt", "instanceof", "le", "lt", "mod",
        "ne", "not", "null", "or", "true"};

    public ELParser(String expression) {
		index = 0;
		this.expression = expression;
		expr = new ELNode.Nodes();
    }

    /**
     * 解析EL表达式
     * @param expression 输入表达式字符串形式的 Char* ('${' Char* '}')* Char*
     * @return ELNode.Nodes中解析后的EL表达式
     */
    public static ELNode.Nodes parse(String expression) {
		ELParser parser = new ELParser(expression);
		while (parser.hasNextChar()) {
		    String text = parser.skipUntilEL();
		    if (text.length() > 0) {
		    	parser.expr.add(new ELNode.Text(text));
		    }
		    ELNode.Nodes elexpr = parser.parseEL();
		    if (! elexpr.isEmpty()) {
		    	parser.expr.add(new ELNode.Root(elexpr));
		    }
		}
		return parser.expr;
    }

    /**
     * 解析EL表达式字符串 '${...}'
     * @return 一个 ELNode.Nodes 表示一个EL表达式
     * TODO: 目前只能解析为函数和文本字符串. 这应该重写为一个完整的解析器.
     */
    private ELNode.Nodes parseEL() {

		StringBuffer buf = new StringBuffer();
		ELexpr = new ELNode.Nodes();
		while (hasNext()) {
		    curToken = nextToken();
		    if (curToken instanceof Char) {
			if (curToken.toChar() == '}') {
			    break;
			}
			buf.append(curToken.toChar());
		    } else {
			// 输出缓冲区中的任何输出
			if (buf.length() > 0) {
			    ELexpr.add(new ELNode.ELText(buf.toString()));
			}
			if (!parseFunction()) {
			    ELexpr.add(new ELNode.ELText(curToken.toString()));
			}
		    }
		}
		if (buf.length() > 0) {
		    ELexpr.add(new ELNode.ELText(buf.toString()));
		}
	
		return ELexpr;
    }

    /**
     * 解析函数
     * FunctionInvokation ::= (identifier ':')? identifier '(' (Expression (,Expression)*)? ')'
     * Note: 目前不解析参数
     */
    private boolean parseFunction() {
		if (! (curToken instanceof Id) || isELReserved(curToken.toString())) {
		    return false;
		}
		String s1 = null;                 // Function prefix
		String s2 = curToken.toString();  // Function name
		int mark = getIndex();
		if (hasNext()) {
		    Token t = nextToken();
		    if (t.toChar() == ':') {
			if (hasNext()) {
			    Token t2 = nextToken();
			    if (t2 instanceof Id) {
				s1 = s2;
				s2 = t2.toString();
				if (hasNext()) {
				    t = nextToken();
				}
			    }
			}
		    }
		    if (t.toChar() == '(') {
			ELexpr.add(new ELNode.Function(s1, s2));
			return true;
		    }
		}
		setIndex(mark);
		return false;
    }

    /**
     * 测试EL中的ID是否为保留字
     */
    private boolean isELReserved(String id) {
        int i = 0;
        int j = reservedWords.length;
        while (i < j) {
            int k = (i+j)/2;
            int result = reservedWords[k].compareTo(id);
            if (result == 0) {
                return true;
            }
            if (result < 0) {
                i = k+1;
            } else {
                j = k;
            }
        }
        return false;
    }

    /**
     * 跳过直到EL表达式('${')达到, 允许转义序列 '\\' 和 '\$'.
     * @return 文本字符串到EL表达式
     */
    private String skipUntilEL() {
		char prev = 0;
		StringBuffer buf = new StringBuffer();
		while (hasNextChar()) {
		    char ch = nextChar();
		    if (prev == '\\') {
			prev = 0;
			if (ch == '\\') {
			    buf.append('\\');
			    if (!escapeBS)
				prev = '\\';
			} else if (ch == '$') {
			    buf.append('$');
			}
			// else error!
		    } else if (prev == '$') {
			if (ch == '{') {
			    prev = 0;
			    break;
			} 
			buf.append('$');
			buf.append(ch);
		    } else if (ch == '\\' || ch == '$') {
			prev = ch;
		    } else {
			buf.append(ch);
		    }
		}
		if (prev != 0) {
		    buf.append(prev);
		}
		return buf.toString();
    }

    /*
     * @return true 如果在EL表达式缓冲区中除了空格之外还有其他东西.
     */
    private boolean hasNext() {
		skipSpaces();
		return hasNextChar();
    }

    /*
     * @return EL表达式缓冲区中的下一个 token.
     */
    private Token nextToken() {
		skipSpaces();
		if (hasNextChar()) {
		    char ch = nextChar();
		    if (Character.isJavaIdentifierStart(ch)) {
			StringBuffer buf = new StringBuffer();
			buf.append(ch);
			while ((ch = peekChar()) != -1 &&
					Character.isJavaIdentifierPart(ch)) {
			    buf.append(ch);
			    nextChar();
			}
			return new Id(buf.toString());
		    }
	
		    if (ch == '\'' || ch == '"') {
			return parseQuotedChars(ch);
		    } else {
			// For now...
			return new Char(ch);
		    }
		}
		return null;
    }

    /*
     * 解析单引号或双引号中的字符串, 允许转义序列 '\\', and ('\"', or "\'")
     */
    private Token parseQuotedChars(char quote) {
		StringBuffer buf = new StringBuffer();
		buf.append(quote);
		while (hasNextChar()) {
		    char ch = nextChar();
		    if (ch == '\\') {
			ch = nextChar();
			if (ch == '\\' || ch == quote) {
			    buf.append(ch);
			}
			// else error!
		    } else if (ch == quote) {
			buf.append(ch);
			break;
		    } else {
			buf.append(ch);
		    }
		}
		return new QuotedString(buf.toString());
    }

    /*
     * 处理EL表达式缓冲区中字符的低层次解析方法的集合.
     */
    private void skipSpaces() {
		while (hasNextChar()) {
		    if (expression.charAt(index) > ' ')
			break;
		    index++;
		}
    }

    private boolean hasNextChar() {
    	return index < expression.length();
    }

    private char nextChar() {
		if (index >= expression.length()) {
		    return (char)-1;
		}
		return expression.charAt(index++);
    }

    private char peekChar() {
		if (index >= expression.length()) {
		    return (char)-1;
		}
		return expression.charAt(index);
    }

    private int getIndex() {
    	return index;
    }

    private void setIndex(int i) {
    	index = i;
    }

    /*
     * 表示EL表达式字符串中的token
     */
    private static class Token {
		char toChar() {
		    return 0;
		}
	
		public String toString() {
		    return "";
		}
    }

    /*
     * 表示EL中的 ID
     */
    private static class Id extends Token {
		String id;
	
		Id(String id) {
		    this.id = id;
		}
	
		public String toString() {
		    return id;
		}
    }

    /*
     * 表示EL中的 character
     */
    private static class Char extends Token {

		private char ch;
	
		Char(char ch) {
		    this.ch = ch;
		}
	
		char toChar() {
		    return ch;
		}
	
		public String toString() {
		    return (new Character(ch)).toString();
		}
    }

    /*
     * 表示EL中引用的（单或双）字符串token
     */
    private static class QuotedString extends Token {

		private String value;
	
		QuotedString(String v) {
		    this.value = v;
		}
	
		public String toString() {
		    return value;
		}
    }
}

