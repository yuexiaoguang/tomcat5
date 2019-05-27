package org.apache.jasper.compiler;

import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Vector;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import javax.servlet.jsp.el.ELException;
import javax.servlet.jsp.el.ELParseException;
import javax.servlet.jsp.el.FunctionMapper;

import org.apache.commons.el.ExpressionEvaluatorImpl;
import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.JspCompilationContext;
import org.xml.sax.Attributes;

/** 
 * 理想情况下，应该将所有bean容器移到这里.
 */
public class JspUtil {

    private static final String WEB_INF_TAGS = "/WEB-INF/tags/";
    private static final String META_INF_TAGS = "/META-INF/tags/";

    // Delimiters for request-time expressions (JSP and XML syntax)
    private static final String OPEN_EXPR  = "<%=";
    private static final String CLOSE_EXPR = "%>";
    private static final String OPEN_EXPR_XML  = "%=";
    private static final String CLOSE_EXPR_XML = "%";

    private static int tempSequenceNumber = 0;
    private static ExpressionEvaluatorImpl expressionEvaluator
	= new ExpressionEvaluatorImpl();

    private static final String javaKeywords[] = {
        "abstract", "assert", "boolean", "break", "byte", "case",
        "catch", "char", "class", "const", "continue",
        "default", "do", "double", "else", "enum", "extends",
        "final", "finally", "float", "for", "goto",
        "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package",
        "private", "protected", "public", "return", "short",
        "static", "strictfp", "super", "switch", "synchronized",
        "this", "throws", "transient", "try", "void",
        "volatile", "while" };

    public static final int CHUNKSIZE = 1024;
        
    public static char[] removeQuotes(char []chars) {
        CharArrayWriter caw = new CharArrayWriter();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '%' && chars[i+1] == '\\' &&
                chars[i+2] == '>') {
                caw.write('%');
                caw.write('>');
                i = i + 2;
            } else {
                caw.write(chars[i]);
            }
        }
        return caw.toCharArray();
    }

    public static char[] escapeQuotes (char []chars) {
        // Prescan to convert %\> to %>
        String s = new String(chars);
        while (true) {
            int n = s.indexOf("%\\>");
            if (n < 0)
                break;
            StringBuffer sb = new StringBuffer(s.substring(0, n));
            sb.append("%>");
            sb.append(s.substring(n + 3));
            s = sb.toString();
        }
        chars = s.toCharArray();
        return (chars);


        // 避开所有反斜杠不在java字符串
        /*
        CharArrayWriter caw = new CharArrayWriter();
        boolean inJavaString = false;
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '"') inJavaString = !inJavaString;
            // escape out the escape character
            if (!inJavaString && (chars[i] == '\\')) caw.write('\\');
            caw.write(chars[i]);
        }
        return caw.toCharArray();
        */
    }

    /**
     * 检查令牌是否是运行时表达式.
     * 在标准JSP语法中, 运行时表达式以 '<%'开头而且以 '%>'结尾. 当JSP文档使用XML语法时, 运行时表达式以'%='开头而且以'%'结尾.
     *
     * @param token 待检查的令牌
     * @return令牌是否是运行时表达式.
     */
    public static boolean isExpression(String token, boolean isXml) {
		String openExpr;
		String closeExpr;
		if (isXml) {
		    openExpr = OPEN_EXPR_XML;
		    closeExpr = CLOSE_EXPR_XML;
		} else {
		    openExpr = OPEN_EXPR;
		    closeExpr = CLOSE_EXPR;
		}
		if (token.startsWith(openExpr) && token.endsWith(closeExpr)) {
		    return true;
		} else {
		    return false;
		}
    }

    /**
     * @return 运行时表达式的"expression"部分, 以分隔符分隔.
     */
    public static String getExpr (String expression, boolean isXml) {
		String returnString;
		String openExpr;
		String closeExpr;
		if (isXml) {
		    openExpr = OPEN_EXPR_XML;
		    closeExpr = CLOSE_EXPR_XML;
		} else {
		    openExpr = OPEN_EXPR;
		    closeExpr = CLOSE_EXPR;
		}
		int length = expression.length();
		if (expression.startsWith(openExpr) && 
	                expression.endsWith(closeExpr)) {
		    returnString = expression.substring(
	                               openExpr.length(), length - closeExpr.length());
		} else {
		    returnString = "";
		}
		return returnString;
    }

    /**
     * 获取一个潜在表达式并将其转换为XML形式
     */
    public static String getExprInXml(String expression) {
        String returnString;
        int length = expression.length();

        if (expression.startsWith(OPEN_EXPR) 
                && expression.endsWith(CLOSE_EXPR)) {
            returnString = expression.substring (1, length - 1);
        } else {
            returnString = expression;
        }

        return escapeXml(returnString.replace(Constants.ESC, '$'));
    }

    /**
     * 检查给定范围是否有效.
     *
     * @param scope 要检查的范围
     * @param n 要检查其值的包含'scope'属性的 Node
     * @param err 错误分派器
     *
     * @throws JasperException 如果范围不是 null, 并和
     * &quot;page&quot;, &quot;request&quot;, &quot;session&quot;, &quot;application&quot; 不同
     */
    public static void checkScope(String scope, Node n, ErrorDispatcher err)
            throws JasperException {
	if (scope != null && !scope.equals("page") && !scope.equals("request")
		&& !scope.equals("session") && !scope.equals("application")) {
	    err.jspError(n, "jsp.error.invalid.scope", scope);
	}
    }

    /**
     * 检查所有强制属性是否存在，以及所有当前属性是否有有效名称. 检查指定为XML样式的属性以及使用jsp:attribute标准行为指定的属性. 
     */
    public static void checkAttributes(String typeOfTag,
				       Node n,
				       ValidAttribute[] validAttributes,
				       ErrorDispatcher err)
				throws JasperException {
        Attributes attrs = n.getAttributes();
        Mark start = n.getStart();
        boolean valid = true;

        // AttributesImpl.removeAttribute is broken, so we do this...
        int tempLength = (attrs == null) ? 0 : attrs.getLength();
		Vector temp = new Vector(tempLength, 1);
	        for (int i = 0; i < tempLength; i++) {
	            String qName = attrs.getQName(i);
	            if ((!qName.equals("xmlns")) && (!qName.startsWith("xmlns:")))
	                temp.addElement(qName);
	        }
	
	        // 使用jsp:attribute指定的属性的名称
	        Node.Nodes tagBody = n.getBody();
	        if( tagBody != null ) {
	            int numSubElements = tagBody.size();
	            for( int i = 0; i < numSubElements; i++ ) {
	                Node node = tagBody.getNode( i );
	                if( node instanceof Node.NamedAttribute ) {
	                    String attrName = node.getAttributeValue( "name" );
	                    temp.addElement( attrName );
		    // 检查这个值是否出现在节点的属性中
		    if (n.getAttributeValue(attrName) != null) {
			err.jspError(n, "jsp.error.duplicate.name.jspattribute",
					attrName);
		    }
                }
                else {
                    // 什么都不能在jsp:attribute之前出现, 只有jsp:body 可以在它之后出现.
                    break;
                }
            }
        }

		/*
		 * 首先检查所有强制属性是否存在.
		 * 如果是这样，那么继续查看其他属性是否对特定标记有效.
		 */
		String missingAttribute = null;
	
		for (int i = 0; i < validAttributes.length; i++) {
		    int attrPos;    
		    if (validAttributes[i].mandatory) {
	                attrPos = temp.indexOf(validAttributes[i].name);
			if (attrPos != -1) {
			    temp.remove(attrPos);
			    valid = true;
			} else {
			    valid = false;
			    missingAttribute = validAttributes[i].name;
			    break;
			}
		    }
		}
	
		// 如果缺少强制属性，则抛出异常
		if (!valid)
		    err.jspError(start, "jsp.error.mandatory.attribute", typeOfTag,
				 missingAttribute);
	
		// 检查是否有指定标记的其他属性.
        int attrLeftLength = temp.size();
		if (attrLeftLength == 0)
		    return;
	
		// 现在检查其他属性是否有效.
		String attribute = null;
	
		for (int j = 0; j < attrLeftLength; j++) {
		    valid = false;
		    attribute = (String) temp.elementAt(j);
		    for (int i = 0; i < validAttributes.length; i++) {
			if (attribute.equals(validAttributes[i].name)) {
			    valid = true;
			    break;
			}
		    }
		    if (!valid)
			err.jspError(start, "jsp.error.invalid.attribute", typeOfTag,
				     attribute);
		}
		// XXX *could* move EL-syntax validation here... (sb)
    }
    
    public static String escapeQueryString(String unescString) {
		if ( unescString == null )
		    return null;
		
		String escString    = "";
		String shellSpChars = "\\\"";
		
		for(int index=0; index<unescString.length(); index++) {
		    char nextChar = unescString.charAt(index);
		    
		    if( shellSpChars.indexOf(nextChar) != -1 )
			escString += "\\";
		    
		    escString += nextChar;
		}
		return escString;
    }
 
    /**
     * 转义从XML定义的5个实体.
     */
    public static String escapeXml(String s) {
        if (s == null) return null;
        StringBuffer sb = new StringBuffer();
        for(int i=0; i<s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '\'') {
                sb.append("&apos;");
            } else if (c == '&') {
                sb.append("&amp;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 使用<tt>with</tt>替换<tt>replace</tt>.
     */
    public static String replace(String name, char replace, String with) {
		StringBuffer buf = new StringBuffer();
		int begin = 0;
		int end;
		int last = name.length();
	
		while (true) {
		    end = name.indexOf(replace, begin);
		    if (end < 0) {
			end = last;
		    }
		    buf.append(name.substring(begin, end));
		    if (end == last) {
			break;
		    }
		    buf.append(with);
		    begin = end + 1;
		}
		
		return buf.toString();
    }

    public static class ValidAttribute {
		String name;
		boolean mandatory;
		boolean rtexprvalue;	// not used now
	
		public ValidAttribute (String name, boolean mandatory,
	            boolean rtexprvalue )
	        {
		    this.name = name;
		    this.mandatory = mandatory;
	            this.rtexprvalue = rtexprvalue;
	        }
	
	       public ValidAttribute (String name, boolean mandatory) {
	            this( name, mandatory, false );
		}
	
		public ValidAttribute (String name) {
		    this (name, false);
		}
    }
    
    /**
     * 将String值转换为 'boolean'.
     * 除了标准转换Boolean.valueOf(s).booleanValue(), "yes"值被转换为'true'. 
     * 如果 's'是 null, 返回'false'.
     *
     * @param s 要转换的字符串
     * @return 字符串 s关联的boolean值
     */
    public static boolean booleanValue(String s) {
		boolean b = false;
		if (s != null) {
		    if (s.equalsIgnoreCase("yes")) {
			b = true;
		    } else {
			b = Boolean.valueOf(s).booleanValue();
		    }
		}
		return b;
    }

    /**
     * 返回给定字符串名称的类或接口关联的<tt>Class</tt>对象.
     *
     * <p><tt>Class</tt>对象通过传递给定字符串名称到<tt>Class.forName()</tt>方法确定, 除非给定的字符串名称表示一个原始类型,
     * 原始类型获取<tt>Class</tt>对象是通过".class"来获取(e.g., "int.class").
     */
    public static Class toClass(String type, ClassLoader loader)
	    throws ClassNotFoundException {

		Class c = null;
		int i0 = type.indexOf('[');
		int dims = 0;
		if (i0 > 0) {
		    // 这是一个数组. 计算大小
		    for (int i = 0; i < type.length(); i++) {
				if (type.charAt(i) == '[')
				    dims++;
		    }
		    type = type.substring(0, i0);
		}
	
		if ("boolean".equals(type))
		    c = boolean.class;
		else if ("char".equals(type))
		    c = char.class;
		else if ("byte".equals(type))
		    c =  byte.class;
		else if ("short".equals(type))
		    c = short.class;
		else if ("int".equals(type))
		    c = int.class;
		else if ("long".equals(type))
		    c = long.class;
		else if ("float".equals(type))
		    c = float.class;
		else if ("double".equals(type))
		    c = double.class;
		else if (type.indexOf('[') < 0)
		    c = loader.loadClass(type);
	
		if (dims == 0)
		    return c;
	
		if (dims == 1)
		    return java.lang.reflect.Array.newInstance(c, 1).getClass();
	
		// 维数大于i的数组
		return java.lang.reflect.Array.newInstance(c, new int[dims]).getClass();
    }

    /**
     * 生成一个字符串，表示对EL解释器的调用.
     * @param expression 包括零个或多个 "${}" 表达式字符串
     * @param expectedType 解释结果的预期类型
     * @param fnmapvar 指向函数映射的变量.
     * @param XmlEscape True 如果结果应该转义xml
     * @return 表示对el解释器的调用的字符串.
     */
    public static String interpreterCall(boolean isTagFile,
					 String expression,
                                         Class expectedType,
                                         String fnmapvar,
                                         boolean XmlEscape ) {
        /*
         * 确定要使用的上下文对象.
         */
		String jspCtxt = null;
		if (isTagFile)
		    jspCtxt = "this.getJspContext()";
		else
		    jspCtxt = "_jspx_page_context";
	
		/*
         * 确定是否使用预期类型的文本名称, 或如果它是原始的, 对应装箱类型的名称.
         */
		String targetType = expectedType.getName();
		String primitiveConverterMethod = null;
		if (expectedType.isPrimitive()) {
		    if (expectedType.equals(Boolean.TYPE)) {
			targetType = Boolean.class.getName();
			primitiveConverterMethod = "booleanValue";
		    } else if (expectedType.equals(Byte.TYPE)) {
			targetType = Byte.class.getName();
			primitiveConverterMethod = "byteValue";
		    } else if (expectedType.equals(Character.TYPE)) {
			targetType = Character.class.getName();
			primitiveConverterMethod = "charValue";
		    } else if (expectedType.equals(Short.TYPE)) {
			targetType = Short.class.getName();
			primitiveConverterMethod = "shortValue";
		    } else if (expectedType.equals(Integer.TYPE)) {
			targetType = Integer.class.getName();
			primitiveConverterMethod = "intValue";
		    } else if (expectedType.equals(Long.TYPE)) {
			targetType = Long.class.getName();
			primitiveConverterMethod = "longValue";
		    } else if (expectedType.equals(Float.TYPE)) {
			targetType = Float.class.getName();
			primitiveConverterMethod = "floatValue";
		    } else if (expectedType.equals(Double.TYPE)) { 
			targetType = Double.class.getName();
			primitiveConverterMethod = "doubleValue";
		    }
		}
	 
		if (primitiveConverterMethod != null) {
		    XmlEscape = false;
		}
	
		/*
         * 建立对解释器的基本调用.
         */
        // XXX - 由于目前的标准机器效率不高，需要大量的包装器和适配器，所以我们现在使用专有的调用解释器. 这都要清理一次EL解释器从JSTL移动到自己的项目中.
        // 未来, 这应该被替换通过ExpressionEvaluator.parseExpression() 然后缓存生成的表达式对象. interpreterCall 只需选择预先缓存的表达式中的一个并对其进行评估.
        // 注意： PageContextImpl 实现VariableResolver 并生成 Servlet/SimpleTag 实现 FunctionMapper, 这样机器就已经就位了(mroth).
		targetType = toJavaSourceType(targetType);
		StringBuffer call = new StringBuffer(
	             "(" + targetType + ") "
	               + "org.apache.jasper.runtime.PageContextImpl.proprietaryEvaluate"
	               + "(" + Generator.quote(expression) + ", "
	               +       targetType + ".class, "
		       +       "(PageContext)" + jspCtxt 
	               +       ", " + fnmapvar
		       + ", " + XmlEscape
	               + ")");
	 
		/*
         * 如果需要，添加原始转换器方法.
         */
		if (primitiveConverterMethod != null) {
		    call.insert(0, "(");
		    call.append(")." + primitiveConverterMethod + "()");
		}
		return call.toString();
    }

    /**
     * 验证给定字符串中所有${}表达式语法.
     * @param where JSP页面中表达式的大致位置
     * @param expressions 包含零个或多个 "${}"表达式的字符串
     * @param err 要使用的错误分派器
     */
    public static void validateExpressions(Mark where,
                                           String expressions,
                                           Class expectedType,
                                           FunctionMapper functionMapper,
                                           ErrorDispatcher err)
            throws JasperException {

        try {
            JspUtil.expressionEvaluator.parseExpression( expressions, 
                expectedType, null );
        }
        catch( ELParseException e ) {
            err.jspError(where, "jsp.error.invalid.expression", expressions,
                e.toString() );
        }
        catch( ELException e ) {
            err.jspError(where, "jsp.error.invalid.expression", expressions,
                e.toString() );
        }
    }

    /**
     * 重置临时变量名.(not thread-safe)
     */
    public static void resetTemporaryVariableName() {
        tempSequenceNumber = 0;
    }

    /**
     * 生成一个新的临时变量名.(not thread-safe)
     */
    public static String nextTemporaryVariableName() {
        return Constants.TEMP_VARIABLE_NAME_PREFIX + (tempSequenceNumber++);
    }

    public static String coerceToPrimitiveBoolean(String s,
						  boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToBoolean(" + s + ")";
		} else {
		    if (s == null || s.length() == 0)
			return "false";
		    else
			return Boolean.valueOf(s).toString();
		}
    }

    public static String coerceToBoolean(String s, boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "(Boolean) org.apache.jasper.runtime.JspRuntimeLibrary.coerce(" + s + ", Boolean.class)";
		} else {
		    if (s == null || s.length() == 0) {
			return "new Boolean(false)";
		    } else {
			// 在翻译时检测格式错误
			return "new Boolean(" + Boolean.valueOf(s).toString() + ")";
		    }
		}
    }

    public static String coerceToPrimitiveByte(String s,
					       boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToByte(" + s + ")";
		} else {
		    if (s == null || s.length() == 0)
			return "(byte) 0";
		    else
			return "((byte)" + Byte.valueOf(s).toString() + ")";
		}
    }

    public static String coerceToByte(String s, boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "(Byte) org.apache.jasper.runtime.JspRuntimeLibrary.coerce(" + s + ", Byte.class)";
		} else {
		    if (s == null || s.length() == 0) {
			return "new Byte((byte) 0)";
		    } else {
			// 在翻译时检测格式错误
			return "new Byte((byte)" + Byte.valueOf(s).toString() + ")";
		    }
		}
    }

    public static String coerceToChar(String s, boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToChar(" + s + ")";
		} else {
		    if (s == null || s.length() == 0) {
			return "(char) 0";
		    } else {
			char ch = s.charAt(0);
			// 这个技巧避免了转义问题
			return "((char) " + (int) ch + ")";
		    }
		}
    }

    public static String coerceToCharacter(String s, boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "(Character) org.apache.jasper.runtime.JspRuntimeLibrary.coerce(" + s + ", Character.class)";
		} else {
		    if (s == null || s.length() == 0) {
			return "new Character((char) 0)";
		    } else {
			char ch = s.charAt(0);
			// 这个技巧避免了转义问题
			return "new Character((char) " + (int) ch + ")";
		    }
		}
    }

    public static String coerceToPrimitiveDouble(String s,
						 boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToDouble(" + s + ")";
		} else {
		    if (s == null || s.length() == 0)
			return "(double) 0";
		    else
			return Double.valueOf(s).toString();
		}
    }

    public static String coerceToDouble(String s, boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "(Double) org.apache.jasper.runtime.JspRuntimeLibrary.coerce(" + s + ", Double.class)";
		} else {
		    if (s == null || s.length() == 0) {
			return "new Double(0)";
		    } else {
			// 在翻译时检测格式错误
			return "new Double(" + Double.valueOf(s).toString() + ")";
		    }
		}
    }

    public static String coerceToPrimitiveFloat(String s,
						boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToFloat(" + s + ")";
		} else {
		    if (s == null || s.length() == 0)
			return "(float) 0";
		    else
			return Float.valueOf(s).toString() + "f";
		}
    }

    public static String coerceToFloat(String s, boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "(Float) org.apache.jasper.runtime.JspRuntimeLibrary.coerce(" + s + ", Float.class)";
		} else {
		    if (s == null || s.length() == 0) {
			return "new Float(0)";
		    } else {
			// 在翻译时检测格式错误
			return "new Float(" + Float.valueOf(s).toString() + "f)";
		    }
		}
    }

    public static String coerceToInt(String s, boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToInt(" + s + ")";
		} else {
		    if (s == null || s.length() == 0)
			return "0";
		    else
			return Integer.valueOf(s).toString();
		}
    }

    public static String coerceToInteger(String s, boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "(Integer) org.apache.jasper.runtime.JspRuntimeLibrary.coerce(" + s + ", Integer.class)";
		} else {
		    if (s == null || s.length() == 0) {
			return "new Integer(0)";
		    } else {
			// 在翻译时检测格式错误
			return "new Integer(" + Integer.valueOf(s).toString() + ")";
		    }
		}
    }

    public static String coerceToPrimitiveShort(String s,
						boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToShort(" + s + ")";
		} else {
		    if (s == null || s.length() == 0)
			return "(short) 0";
		    else
			return "((short) " + Short.valueOf(s).toString() + ")";
		}
    }
    
    public static String coerceToShort(String s, boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "(Short) org.apache.jasper.runtime.JspRuntimeLibrary.coerce(" + s + ", Short.class)";
		} else {
		    if (s == null || s.length() == 0) {
			return "new Short((short) 0)";
		    } else {
			// 在翻译时检测格式错误
			return "new Short(\"" + Short.valueOf(s).toString() + "\")";
		    }
		}
    }
    
    public static String coerceToPrimitiveLong(String s,
					       boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "org.apache.jasper.runtime.JspRuntimeLibrary.coerceToLong(" + s + ")";
		} else {
		    if (s == null || s.length() == 0)
			return "(long) 0";
		    else
			return Long.valueOf(s).toString() + "l";
		}
    }

    public static String coerceToLong(String s, boolean isNamedAttribute) {
		if (isNamedAttribute) {
		    return "(Long) org.apache.jasper.runtime.JspRuntimeLibrary.coerce(" + s + ", Long.class)";
		} else {
		    if (s == null || s.length() == 0) {
			return "new Long(0)";
		    } else {
			// 在翻译时检测格式错误
			return "new Long(" + Long.valueOf(s).toString() + "l)";
		    }
		}
    }

    public static InputStream getInputStream(String fname, JarFile jarFile,
					     JspCompilationContext ctxt,
					     ErrorDispatcher err)
		throws JasperException, IOException {

        InputStream in = null;

		if (jarFile != null) {
		    String jarEntryName = fname.substring(1, fname.length());
		    ZipEntry jarEntry = jarFile.getEntry(jarEntryName);
		    if (jarEntry == null) {
			err.jspError("jsp.error.file.not.found", fname);
		    }
		    in = jarFile.getInputStream(jarEntry);
		} else {
		    in = ctxt.getResourceAsStream(fname);
		}
	
		if (in == null) {
		    err.jspError("jsp.error.file.not.found", fname);
		}
	
		return in;
    }

    /**
     * 获取与给定标签文件路径相对应的标签处理程序的完全限定类名.
     *
     * @param path 标签文件路径
     * @param err 错误分派器
     *
     * @return 与给定标签文件路径相对应的标签处理程序的完全限定类名
     */
    public static String getTagHandlerClassName(String path,
						ErrorDispatcher err)
                throws JasperException {

        String className = null;
        int begin = 0;
        int index;
        
        index = path.lastIndexOf(".tag");
        if (index == -1) {
            err.jspError("jsp.error.tagfile.badSuffix", path);
        }

        // 如果删除".tag"后缀, 此标签的完全限定类名可能与其他标签的包名冲突.
        // 对于实例, 标签文件
        //    /WEB-INF/tags/foo.tag
        // 将具有完全限定类名
        //    org.apache.jsp.tag.web.foo
        // 这将与标记文件的包名冲突
        //    /WEB-INF/tags/foo/bar.tag

        index = path.indexOf(WEB_INF_TAGS);
        if (index != -1) {
            className = "org.apache.jsp.tag.web.";
            begin = index + WEB_INF_TAGS.length();
        } else {
        	index = path.indexOf(META_INF_TAGS);
		    if (index != -1) {
				className = "org.apache.jsp.tag.meta.";
				begin = index + META_INF_TAGS.length();
		    } else {
		    	err.jspError("jsp.error.tagfile.illegalPath", path);
		    }
        }

        className += makeJavaPackage(path.substring(begin));
  
       return className;
    }

    /**
     * 转换给定的到一个java包或完全限定类名的路径
     *
     * @param path 要转换的路径
     *
     * @return java包对应于给定的路径
     */
    public static final String makeJavaPackage(String path) {
        String classNameComponents[] = split(path,"/");
        StringBuffer legalClassNames = new StringBuffer();
        for (int i = 0; i < classNameComponents.length; i++) {
            legalClassNames.append(makeJavaIdentifier(classNameComponents[i]));
            if (i < classNameComponents.length - 1) {
                legalClassNames.append('.');
            }
        }
        return legalClassNames.toString();
    }

    /**
     * 把一个字符串拆分成它的组件.
     * @param path 分割的字符串
     * @param pat 分割模式
     * @return 路径的组件
     */
    private static final String [] split(String path, String pat) {
        Vector comps = new Vector();
        int pos = path.indexOf(pat);
        int start = 0;
        while( pos >= 0 ) {
            if(pos > start ) {
                String comp = path.substring(start,pos);
                comps.add(comp);
            }
            start = pos + pat.length();
            pos = path.indexOf(pat,start);
        }
        if( start < path.length()) {
            comps.add(path.substring(start));
        }
        String [] result = new String[comps.size()];
        for(int i=0; i < comps.size(); i++) {
            result[i] = (String)comps.elementAt(i);
        }
        return result;
    }
            
    /**
     * 转换给定的标识符为一个合法的java标识符
     *
     * @param identifier 要转化的标识符
     *
     * @return 对应于给定标识符的合法的Java标识符
     */
    public static final String makeJavaIdentifier(String identifier) {
        StringBuffer modifiedIdentifier = 
            new StringBuffer(identifier.length());
        if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
            modifiedIdentifier.append('_');
        }
        for (int i = 0; i < identifier.length(); i++) {
            char ch = identifier.charAt(i);
            if (Character.isJavaIdentifierPart(ch) && ch != '_') {
                modifiedIdentifier.append(ch);
            } else if (ch == '.') {
                modifiedIdentifier.append('_');
            } else {
                modifiedIdentifier.append(mangleChar(ch));
            }
        }
        if (isJavaKeyword(modifiedIdentifier.toString())) {
            modifiedIdentifier.append('_');
        }
        return modifiedIdentifier.toString();
    }
    
    /**
     * 用指定的字符来创建一个合法的java类的名称.
     */
    public static final String mangleChar(char ch) {
        char[] result = new char[5];
        result[0] = '_';
        result[1] = Character.forDigit((ch >> 12) & 0xf, 16);
        result[2] = Character.forDigit((ch >> 8) & 0xf, 16);
        result[3] = Character.forDigit((ch >> 4) & 0xf, 16);
        result[4] = Character.forDigit(ch & 0xf, 16);
        return new String(result);
    }

    /**
     * 测试参数是一个java关键字
     */
    public static boolean isJavaKeyword(String key) {
        int i = 0;
        int j = javaKeywords.length;
        while (i < j) {
            int k = (i+j)/2;
            int result = javaKeywords[k].compareTo(key);
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
     * 将给定的XML名称转换为合法的java标识符. 这是比makeJavaIdentifier更有效的, 我们只需要担心字符串中的 '.', '-', ':'.
     * 我们还假设得到的字符串是进一步级联一些前缀字符串，不必担心这是一个java关键字.
     *
     * @param name 要转换的标识符
     *
     * @return 对应于给定标识符的合法Java标识符
     */
    public static final String makeXmlJavaIdentifier(String name) {
        if (name.indexOf('-') >= 0)
            name = replace(name, '-', "$1");
        if (name.indexOf('.') >= 0)
            name = replace(name, '.', "$2");
        if (name.indexOf(':') >= 0)
            name = replace(name, ':', "$3");
        return name;
    }

    static InputStreamReader getReader(String fname, String encoding,
				       JarFile jarFile,
				       JspCompilationContext ctxt,
				       ErrorDispatcher err)
		throws JasperException, IOException {

        InputStreamReader reader = null;
		InputStream in = getInputStream(fname, jarFile, ctxt, err);
	
		try {
            reader = new InputStreamReader(in, encoding);
		} catch (UnsupportedEncodingException ex) {
		    err.jspError("jsp.error.unsupported.encoding", encoding);
		}
		return reader;
    }

    /**
     * Class.getName() 返回"[[[<et>"形式的数组, 其中ET, 元素类型可以是 ZBCDFIJS 或 L<classname>中的一个;
     * 它转换成可以被javac理解的形式.
     */
    public static String toJavaSourceType(String type) {

		if (type.charAt(0) != '[') {
		    return type;
	 	}
	
		int dims = 1;
		String t = null;
		for (int i = 1; i < type.length(); i++) {
		    if (type.charAt(i) == '[') {
		    	dims++;
		    } else {
				switch (type.charAt(i)) {
					case 'Z': t = "boolean"; break;
					case 'B': t = "byte"; break;
					case 'C': t = "char"; break;
					case 'D': t = "double"; break;
					case 'F': t = "float"; break;
					case 'I': t = "int"; break;
					case 'J': t = "long"; break;
					case 'S': t = "short"; break;
					case 'L': t = type.substring(i+1, type.indexOf(';')); break;
				}
				break;
		    }
		}
		StringBuffer resultType = new StringBuffer(t);
		for (; dims > 0; dims--) {
		    resultType.append("[]");
		}
		return resultType.toString();
    }

    /**
     * 从Class实例计算规范名称. 注意，使用二进制名称的'.'替换'$'会出错, 因为 '$'是一个合法的java标识符.
     * @param c java.lang.Class实例
     * @return  c的规范名.
     */
    public static String getCanonicalName(Class c) {

        String binaryName = c.getName();
        c = c.getDeclaringClass();

        if (c == null) {
            return binaryName;
        }

        StringBuffer buf = new StringBuffer(binaryName);
        do {
            buf.setCharAt(c.getName().length(), '.');
            c = c.getDeclaringClass();
        } while ( c != null);

        return buf.toString();
    }
}

