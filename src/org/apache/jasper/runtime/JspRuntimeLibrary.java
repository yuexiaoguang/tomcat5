package org.apache.jasper.runtime;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Enumeration;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.BodyContent;

import org.apache.jasper.JasperException;
import org.apache.jasper.compiler.Localizer;

/**
 * __begin, __end 如果不想在这个类上运行时依赖，JSP引擎就可以实际解析这个文件并内联它们. 但是, 我不知道现在是否有效.
 */
public class JspRuntimeLibrary {
    
    private static final String SERVLET_EXCEPTION = "javax.servlet.error.exception";
    private static final String JSP_EXCEPTION = "javax.servlet.jsp.jspException";

    protected static class PrivilegedIntrospectHelper implements PrivilegedExceptionAction {

		private Object bean;
		private String prop;
		private String value;
		private ServletRequest request;
		private String param;
		private boolean ignoreMethodNF;

        PrivilegedIntrospectHelper(Object bean, String prop,
                                   String value, ServletRequest request,
                                   String param, boolean ignoreMethodNF) {
		    this.bean = bean;
		    this.prop = prop;
		    this.value = value;
	            this.request = request;
		    this.param = param;
		    this.ignoreMethodNF = ignoreMethodNF;
        }
         
        public Object run() throws JasperException {
		    internalIntrospecthelper(
	                bean,prop,value,request,param,ignoreMethodNF);
            return null;
        }
    }

    /**
     * 返回javax.servlet.error.exception 请求属性值, 否则返回
     * javax.servlet.jsp.jspException 请求属性值.
     *
     * 这个方法在生成JSP错误页面的servlet代码开始的时候调用, 当"exception"初始化隐式脚本语言变量的时候.
     */
    public static Throwable getThrowable(ServletRequest request) {
		Throwable error = (Throwable) request.getAttribute(SERVLET_EXCEPTION);
		if (error == null) {
		    error = (Throwable) request.getAttribute(JSP_EXCEPTION);
		    if (error != null) {
			/*
			 * 唯一设置 JSP_EXCEPTION的地方 是 PageContextImpl.handlePageException(). 它真的应该设置
			 * SERVLET_EXCEPTION, 但这会影响到 ErrorReportValve. 因此, 如果JSP_EXCEPTION 被设置, 需要设置 SERVLET_EXCEPTION.
			 */
			request.setAttribute(SERVLET_EXCEPTION, error);
		    }
		}
		return error;
    }

    public static boolean coerceToBoolean(String s) {
		if (s == null || s.length() == 0)
		    return false;
		else
		    return Boolean.valueOf(s).booleanValue();
    }

    public static byte coerceToByte(String s) {
		if (s == null || s.length() == 0)
		    return (byte) 0;
		else
		    return Byte.valueOf(s).byteValue();
    }

    public static char coerceToChar(String s) {
		if (s == null || s.length() == 0) {
		    return (char) 0;
		} else {
		    // 这个技巧避免了转义问题
		    return (char)(int) s.charAt(0);
		}
    }

    public static double coerceToDouble(String s) {
		if (s == null || s.length() == 0)
		    return (double) 0;
		else
		    return Double.valueOf(s).doubleValue();
    }

    public static float coerceToFloat(String s) {
		if (s == null || s.length() == 0)
		    return (float) 0;
		else
		    return Float.valueOf(s).floatValue();
    }

    public static int coerceToInt(String s) {
		if (s == null || s.length() == 0)
		    return 0;
		else
		    return Integer.valueOf(s).intValue();
    }

    public static short coerceToShort(String s) {
		if (s == null || s.length() == 0)
		    return (short) 0;
		else
		    return Short.valueOf(s).shortValue();
    }

    public static long coerceToLong(String s) {
		if (s == null || s.length() == 0)
		    return (long) 0;
		else
		    return Long.valueOf(s).longValue();
    }

    public static Object coerce(String s, Class target) {

		boolean isNullOrEmpty = (s == null || s.length() == 0);
	
		if (target == Boolean.class) {
		    if (isNullOrEmpty) {
		    	s = "false";
		    }
		    return new Boolean(s);
		} else if (target == Byte.class) {
		    if (isNullOrEmpty)
		    	return new Byte((byte) 0);
		    else
		    	return new Byte(s);
		} else if (target == Character.class) {
		    if (isNullOrEmpty)
		    	return new Character((char) 0);
		    else 
		    	return new Character(s.charAt(0));
		} else if (target == Double.class) {
		    if (isNullOrEmpty)
		    	return new Double(0);
		    else
		    	return new Double(s);
		} else if (target == Float.class) {
		    if (isNullOrEmpty)
		    	return new Float(0);
		    else
		    	return new Float(s);
		} else if (target == Integer.class) {
		    if (isNullOrEmpty)
		    	return new Integer(0);
		    else
		    	return new Integer(s);
		} else if (target == Short.class) {
		    if (isNullOrEmpty)
		    	return new Short((short) 0);
		    else
		    	return new Short(s);
		} else if (target == Long.class) {
		    if (isNullOrEmpty)
		    	return new Long(0);
		    else
		    	return new Long(s);
		} else {
		    return null;
		}
    }

    // __begin convertMethod
    public static Object convert(String propertyName, String s, Class t,
				 Class propertyEditorClass) 
       throws JasperException {
        try {
            if (s == null) {
                if (t.equals(Boolean.class) || t.equals(Boolean.TYPE))
                    s = "false";
                else
                    return null;
            }
	    if (propertyEditorClass != null) {
		return getValueFromBeanInfoPropertyEditor(
				    t, propertyName, s, propertyEditorClass);
	    } else if ( t.equals(Boolean.class) || t.equals(Boolean.TYPE) ) {
                if (s.equalsIgnoreCase("on") || s.equalsIgnoreCase("true"))
                    s = "true";
                else
                    s = "false";
                return new Boolean(s);
            } else if ( t.equals(Byte.class) || t.equals(Byte.TYPE) ) {
                return new Byte(s);
            } else if (t.equals(Character.class) || t.equals(Character.TYPE)) {
                return s.length() > 0 ? new Character(s.charAt(0)) : null;
            } else if ( t.equals(Short.class) || t.equals(Short.TYPE) ) {
                return new Short(s);
            } else if ( t.equals(Integer.class) || t.equals(Integer.TYPE) ) {
                return new Integer(s);
            } else if ( t.equals(Float.class) || t.equals(Float.TYPE) ) {
                return new Float(s);
            } else if ( t.equals(Long.class) || t.equals(Long.TYPE) ) {
                return new Long(s);
            } else if ( t.equals(Double.class) || t.equals(Double.TYPE) ) {
                return new Double(s);
            } else if ( t.equals(String.class) ) {
                return s;
            } else if ( t.equals(java.io.File.class) ) {
                return new java.io.File(s);
            } else if (t.getName().equals("java.lang.Object")) {
                return new Object[] {s};
	    } else {
		return getValueFromPropertyEditorManager(
                                            t, propertyName, s);
            }
        } catch (Exception ex) {
            throw new JasperException(ex);
        }
    }
    // __end convertMethod

    // __begin introspectMethod
    public static void introspect(Object bean, ServletRequest request)
                                  throws JasperException {
		Enumeration e = request.getParameterNames();
		while ( e.hasMoreElements() ) {
		    String name  = (String) e.nextElement();
		    String value = request.getParameter(name);
		    introspecthelper(bean, name, value, request, name, true);
		}
    }
    // __end introspectMethod
    
    // __begin introspecthelperMethod
    public static void introspecthelper(Object bean, String prop,
                                        String value, ServletRequest request,
                                        String param, boolean ignoreMethodNF)
                                        throws JasperException {
        if( System.getSecurityManager() != null ) {
            try {
                PrivilegedIntrospectHelper dp = new PrivilegedIntrospectHelper(
                		bean,prop,value,request,param,ignoreMethodNF);
                AccessController.doPrivileged(dp);
            } catch( PrivilegedActionException pe) {
                Exception e = pe.getException();
                throw (JasperException)e;
            }
        } else {
            internalIntrospecthelper(
            		bean,prop,value,request,param,ignoreMethodNF);
        }
    }

    private static void internalIntrospecthelper(Object bean, String prop,
					String value, ServletRequest request,
					String param, boolean ignoreMethodNF) 
					throws JasperException {
        Method method = null;
        Class type = null;
        Class propertyEditorClass = null;
        try {
		    java.beans.BeanInfo info = java.beans.Introspector.getBeanInfo(bean.getClass());
		    if ( info != null ) {
				java.beans.PropertyDescriptor pd[]
				    = info.getPropertyDescriptors();
				for (int i = 0 ; i < pd.length ; i++) {
				    if ( pd[i].getName().equals(prop) ) {
						method = pd[i].getWriteMethod();
						type   = pd[i].getPropertyType();
						propertyEditorClass = pd[i].getPropertyEditorClass();
						break;
				    }
				}
		    }
		    if ( method != null ) {
				if (type.isArray()) {
                    if (request == null) {
						throw new JasperException(
					            Localizer.getMessage("jsp.error.beans.setproperty.noindexset"));
                    }
				    Class t = type.getComponentType();
				    String[] values = request.getParameterValues(param);
				    //XXX Please check.
				    if(values == null) return;
				    if(t.equals(String.class)) {
				    	method.invoke(bean, new Object[] { values });
				    } else {
						Object tmpval = null;
						createTypedArray (prop, bean, method, values, t,
								  propertyEditorClass); 
				    }
				} else {
				    if(value == null || (param != null && value.equals(""))) return;
				    	Object oval = convert(prop, value, type, propertyEditorClass);
				    if ( oval != null )
				    	method.invoke(bean, new Object[] { oval });
				}
		    }
		} catch (Exception ex) {
		    throw new JasperException(ex);
		}
        if (!ignoreMethodNF && (method == null)) {
            if (type == null) {
				throw new JasperException(
		                    Localizer.getMessage("jsp.error.beans.noproperty",
							 prop,
							 bean.getClass().getName()));
            } else {
				throw new JasperException(
			            Localizer.getMessage("jsp.error.beans.nomethod.setproperty",
							 prop,
							 type.getName(),
							 bean.getClass().getName()));
            }
        }
    }
    // __end introspecthelperMethod
    
    //-------------------------------------------------------------------
    // 函数转化内置java数据类型的字符串.
    //-------------------------------------------------------------------
    // __begin toStringMethod
    public static String toString(Object o) {
        return String.valueOf(o);
    }

    public static String toString(byte b) {
        return new Byte(b).toString();
    }

    public static String toString(boolean b) {
        return new Boolean(b).toString();
    }

    public static String toString(short s) {
        return new Short(s).toString();
    }

    public static String toString(int i) {
        return new Integer(i).toString();
    }

    public static String toString(float f) {
        return new Float(f).toString();
    }

    public static String toString(long l) {
        return new Long(l).toString();
    }

    public static String toString(double d) {
        return new Double(d).toString();
    }

    public static String toString(char c) {
        return new Character(c).toString();
    }
    // __end toStringMethod


    /**
     * 创建类型数组.
     * 这是一个特殊的情况，通过请求和属性传递的参数被索引.
     */
    public static void createTypedArray(String propertyName,
					Object bean,
					Method method,
					String[] values,
					Class t,
					Class propertyEditorClass)
	        throws JasperException {

		try {
		    if (propertyEditorClass != null) {
				Object[] tmpval = new Integer[values.length];
				for (int i=0; i<values.length; i++) {
				    tmpval[i] = getValueFromBeanInfoPropertyEditor(
		                            t, propertyName, values[i], propertyEditorClass);
				}
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(Integer.class)) {
				Integer []tmpval = new Integer[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] =  new Integer (values[i]);
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(Byte.class)) {
				Byte[] tmpval = new Byte[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] = new Byte (values[i]);
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(Boolean.class)) {
				Boolean[] tmpval = new Boolean[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] = new Boolean (values[i]);
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(Short.class)) {
				Short[] tmpval = new Short[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] = new Short (values[i]);
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(Long.class)) {
				Long[] tmpval = new Long[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] = new Long (values[i]);
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(Double.class)) {
				Double[] tmpval = new Double[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] = new Double (values[i]);
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(Float.class)) {
				Float[] tmpval = new Float[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] = new Float (values[i]);
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(Character.class)) {
				Character[] tmpval = new Character[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] = new Character(values[i].charAt(0));
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(int.class)) {
				int []tmpval = new int[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] = Integer.parseInt (values[i]);
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(byte.class)) {
				byte[] tmpval = new byte[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] = Byte.parseByte (values[i]);
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(boolean.class)) {
				boolean[] tmpval = new boolean[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] = (Boolean.valueOf(values[i])).booleanValue();
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(short.class)) {
				short[] tmpval = new short[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] = Short.parseShort (values[i]);
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(long.class)) {
				long[] tmpval = new long[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] = Long.parseLong (values[i]);
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(double.class)) {
				double[] tmpval = new double[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] = Double.valueOf(values[i]).doubleValue();
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(float.class)) {
				float[] tmpval = new float[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] = Float.valueOf(values[i]).floatValue();
				method.invoke (bean, new Object[] {tmpval});
		    } else if (t.equals(char.class)) {
				char[] tmpval = new char[values.length];
				for (int i = 0 ; i < values.length; i++)
				    tmpval[i] = values[i].charAt(0);
				method.invoke (bean, new Object[] {tmpval});
		    } else {
				Object[] tmpval = new Integer[values.length];
				for (int i=0; i<values.length; i++) {
				    tmpval[i] = getValueFromPropertyEditorManager(t, propertyName, values[i]);
				}
				method.invoke (bean, new Object[] {tmpval});
		    }
		} catch (Exception ex) {
	            throw new JasperException ("error in invoking method", ex);
		}
    }

    /**
     * 转义特殊的shell 字符.
     * @param unescString 要转义的shell字符串
     * @return The escaped shell string.
     */
    public static String escapeQueryString(String unescString) {
	    if ( unescString == null )
	        return null;
	   
	    String escString    = "";
	    String shellSpChars = "&;`'\"|*?~<>^()[]{}$\\\n";
	   
	    for(int index=0; index<unescString.length(); index++) {
	        char nextChar = unescString.charAt(index);
	
	        if( shellSpChars.indexOf(nextChar) != -1 )
	        	escString += "\\";
	
	        escString += nextChar;
	    }
	    return escString;
    }

    /**
     * 解码 URL.
     * @param encoded 要解码的字符串.
     * @return The decoded string.
     */
    public static String decode(String encoded) {
        // 如果我们不需要迅速离开
	    if (encoded == null) return null;
        if (encoded.indexOf('%') == -1 && encoded.indexOf('+') == -1)
        return encoded;

        //分配缓冲区 - 使用byte[] 避免调用 new.
        byte holdbuffer[] = new byte[encoded.length()];

        char holdchar;
        int bufcount = 0;

        for (int count = 0; count < encoded.length(); count++) {
	        char cur = encoded.charAt(count);
	            if (cur == '%') {
	            holdbuffer[bufcount++] =
	          (byte)Integer.parseInt(encoded.substring(count+1,count+3),16);
	                if (count + 2 >= encoded.length())
	                    count = encoded.length();
	                else
	                    count += 2;
	            } else if (cur == '+') {
	        holdbuffer[bufcount++] = (byte) ' ';
	        } else {
	            holdbuffer[bufcount++] = (byte) cur;
            }
        }
		// REVISIT -- remedy for Deprecated warning.
	    //return new String(holdbuffer,0,0,bufcount);
	    return new String(holdbuffer,0,bufcount);
    }

    // __begin lookupReadMethodMethod
    public static Object handleGetProperty(Object o, String prop) throws JasperException {
        if (o == null) {
		    throw new JasperException(
		            Localizer.getMessage("jsp.error.beans.nullbean"));
        }
        Object value = null;
        try {
            Method method = getReadMethod(o.getClass(), prop);
            value = method.invoke(o, null);
        } catch (Exception ex) {
        	throw new JasperException (ex);
        }
        return value;
    }
    // __end lookupReadMethodMethod

    // handles <jsp:setProperty> with EL expression for 'value' attribute
/** Use proprietaryEvaluate
    public static void handleSetPropertyExpression(Object bean,
        String prop, String expression, PageContext pageContext,
        VariableResolver variableResolver, FunctionMapper functionMapper )
	throws JasperException
    {
	try {
            Method method = getWriteMethod(bean.getClass(), prop);
	    method.invoke(bean, new Object[] { 
		pageContext.getExpressionEvaluator().evaluate(
		    expression,
		    method.getParameterTypes()[0],
                    variableResolver,
                    functionMapper,
                    null )
	    });
	} catch (Exception ex) {
	    throw new JasperException(ex);
	}
    }
**/
    public static void handleSetPropertyExpression(Object bean,
        String prop, String expression, PageContext pageContext,
        ProtectedFunctionMapper functionMapper )
        throws JasperException {
        try {
            Method method = getWriteMethod(bean.getClass(), prop);
            method.invoke(bean, new Object[] {
                PageContextImpl.proprietaryEvaluate(
                    expression,
                    method.getParameterTypes()[0],
                    pageContext,
                    functionMapper,
                    false )
            });
        } catch (Exception ex) {
            throw new JasperException(ex);
        }
    }

    public static void handleSetProperty(Object bean, String prop,
					 Object value) throws JasperException {
		try {
            Method method = getWriteMethod(bean.getClass(), prop);
		    method.invoke(bean, new Object[] { value });
		} catch (Exception ex) {
		    throw new JasperException(ex);
		}
    }
    
    public static void handleSetProperty(Object bean, String prop,
					 int value) throws JasperException {
		try {
            Method method = getWriteMethod(bean.getClass(), prop);
		    method.invoke(bean, new Object[] { new Integer(value) });
		} catch (Exception ex) {
		    throw new JasperException(ex);
		}	
    }
    
    public static void handleSetProperty(Object bean, String prop,
					 short value) throws JasperException {
		try {
	        Method method = getWriteMethod(bean.getClass(), prop);
		    method.invoke(bean, new Object[] { new Short(value) });
		} catch (Exception ex) {
		    throw new JasperException(ex);
		}	
    }
    
    public static void handleSetProperty(Object bean, String prop,
					 long value) throws JasperException {
		try {
	        Method method = getWriteMethod(bean.getClass(), prop);
		    method.invoke(bean, new Object[] { new Long(value) });
		} catch (Exception ex) {
		    throw new JasperException(ex);
		}	
    } 
    
    public static void handleSetProperty(Object bean, String prop,
					 double value) throws JasperException {
		try {
	        Method method = getWriteMethod(bean.getClass(), prop);
		    method.invoke(bean, new Object[] { new Double(value) });
		} catch (Exception ex) {
		    throw new JasperException(ex);
		}	
    }
    
    public static void handleSetProperty(Object bean, String prop,
					 float value) throws JasperException {
		try {
	        Method method = getWriteMethod(bean.getClass(), prop);
		    method.invoke(bean, new Object[] { new Float(value) });
		} catch (Exception ex) {
		    throw new JasperException(ex);
		}	
    }
    
    public static void handleSetProperty(Object bean, String prop,
					 char value) throws JasperException {
		try {
	        Method method = getWriteMethod(bean.getClass(), prop);
		    method.invoke(bean, new Object[] { new Character(value) });
		} catch (Exception ex) {
		    throw new JasperException(ex);
		}	
    }

    public static void handleSetProperty(Object bean, String prop,
					 byte value) throws JasperException {
		try {
            Method method = getWriteMethod(bean.getClass(), prop);
		    method.invoke(bean, new Object[] { new Byte(value) });
		} catch (Exception ex) {
		    throw new JasperException(ex);
		}	
    }
    
    public static void handleSetProperty(Object bean, String prop,
					 boolean value) throws JasperException {
		try {
	        Method method = getWriteMethod(bean.getClass(), prop);
		    method.invoke(bean, new Object[] { new Boolean(value) });
		} catch (Exception ex) {
		    throw new JasperException(ex);
		}	
    }
    
    public static Method getWriteMethod(Class beanClass, String prop) throws JasperException {
    	Method method = null;	
        Class type = null;
		try {
		    java.beans.BeanInfo info
	                = java.beans.Introspector.getBeanInfo(beanClass);
		    if ( info != null ) {
				java.beans.PropertyDescriptor pd[]
				    = info.getPropertyDescriptors();
				for (int i = 0 ; i < pd.length ; i++) {
				    if ( pd[i].getName().equals(prop) ) {
						method = pd[i].getWriteMethod();
						type   = pd[i].getPropertyType();
						break;
				    }
				}
            } else {        
                // just in case introspection silently fails.
                throw new JasperException(
                    Localizer.getMessage("jsp.error.beans.nobeaninfo",
					 beanClass.getName()));
            }
        } catch (Exception ex) {
            throw new JasperException (ex);
        }
        if (method == null) {
            if (type == null) {
				throw new JasperException(
		                        Localizer.getMessage("jsp.error.beans.noproperty",
							     prop,
							     beanClass.getName()));
            } else {
				throw new JasperException(
				    Localizer.getMessage("jsp.error.beans.nomethod.setproperty",
							 prop,
							 type.getName(),
							 beanClass.getName()));
            }
        }
        return method;
    }

    public static Method getReadMethod(Class beanClass, String prop)
	    throws JasperException {

        Method method = null;        
        Class type = null;
        try {
            java.beans.BeanInfo info
                = java.beans.Introspector.getBeanInfo(beanClass);
            if ( info != null ) {
                java.beans.PropertyDescriptor pd[]
                    = info.getPropertyDescriptors();
                for (int i = 0 ; i < pd.length ; i++) {
                    if ( pd[i].getName().equals(prop) ) {
                        method = pd[i].getReadMethod();
                        type   = pd[i].getPropertyType();
                        break;
                    }
                }
            } else {        
                // just in case introspection silently fails.
				throw new JasperException(
		                    Localizer.getMessage("jsp.error.beans.nobeaninfo",
							 beanClass.getName()));
            }
		} catch (Exception ex) {
		    throw new JasperException (ex);
		}
        if (method == null) {
            if (type == null) {
				throw new JasperException(
		                    Localizer.getMessage("jsp.error.beans.noproperty", prop,
							 beanClass.getName()));
            } else {
				throw new JasperException(
		                    Localizer.getMessage("jsp.error.beans.nomethod", prop,
							 beanClass.getName()));
            }
        }
        return method;
    }

    //*********************************************************************
    // PropertyEditor Support

    public static Object getValueFromBeanInfoPropertyEditor(
		           Class attrClass, String attrName, String attrValue,
			   Class propertyEditorClass) throws JasperException {
		try {
		    PropertyEditor pe = (PropertyEditor)propertyEditorClass.newInstance();
		    pe.setAsText(attrValue);
		    return pe.getValue();
		} catch (Exception ex) {
		    throw new JasperException(
	                Localizer.getMessage("jsp.error.beans.property.conversion",
					     attrValue, attrClass.getName(), attrName,
					     ex.getMessage()));
		}
    }

    public static Object getValueFromPropertyEditorManager(
	             Class attrClass, String attrName, String attrValue) throws JasperException {
		try {
		    PropertyEditor propEditor = 
			PropertyEditorManager.findEditor(attrClass);
		    if (propEditor != null) {
				propEditor.setAsText(attrValue);
				return propEditor.getValue();
		    } else {
				throw new IllegalArgumentException(
		                    Localizer.getMessage("jsp.error.beans.propertyeditor.notregistered"));
		    }
		} catch (IllegalArgumentException ex) {
		    throw new JasperException(
	                Localizer.getMessage("jsp.error.beans.property.conversion",
					     attrValue, attrClass.getName(), attrName,
					     ex.getMessage()));
		}
    }


    // ************************************************************************
    // General Purpose Runtime Methods
    // ************************************************************************


    /**
     * 将可能的相对资源路径转换为相对上下文的资源路径, 以'/'开头.
     *
     * @param request 要处理的servlet 请求
     * @param relativePath 可能相对于资源路径
     */
    public static String getContextRelativePath(ServletRequest request,
                                                String relativePath) {

        if (relativePath.startsWith("/"))
            return (relativePath);
        if (!(request instanceof HttpServletRequest))
            return (relativePath);
        HttpServletRequest hrequest = (HttpServletRequest) request;
        String uri = (String) request.getAttribute("javax.servlet.include.servlet_path");
        if (uri != null) {
            String pathInfo = (String) request.getAttribute("javax.servlet.include.path_info");
            if (pathInfo == null) {
                if (uri.lastIndexOf('/') >= 0) 
                    uri = uri.substring(0, uri.lastIndexOf('/'));
            }
        } else {
            uri = hrequest.getServletPath();
            if (uri.lastIndexOf('/') >= 0) 
                uri = uri.substring(0, uri.lastIndexOf('/'));
        }
        return uri + '/' + relativePath;
    }


    /**
     * 执行一个 RequestDispatcher.include() 操作, 预先选择响应刷新.
     *
     * @param request 要处理的servlet 请求
     * @param response 要处理的servlet响应
     * @param relativePath 包含的资源的相对路径
     * @param out 要写入的Writer
     * @param flush 是否在包含处理之前刷新?
     *
     * @exception IOException 如果包含的servlet抛出
     * @exception ServletException 如果包含的servlet抛出
     */
    public static void include(ServletRequest request,
                               ServletResponse response,
                               String relativePath,
                               JspWriter out,
                               boolean flush)
        throws IOException, ServletException {

        if (flush && !(out instanceof BodyContent))
            out.flush();

        // FIXME - 尝试使用request.getRequestDispatcher() 直接解析相对路径, 但是Catalina 目前不考虑调用者是否是在一个RequestDispatcher中.
        // Catalina 是否应该考虑*是目前正在审查的规范问题吗. 与此同时, 复制Jasper之前的行为

        String resourcePath = getContextRelativePath(request, relativePath);
        RequestDispatcher rd = request.getRequestDispatcher(resourcePath);

        rd.include(request, new ServletResponseWrapperInclude(response, out));
    }

    /**
     * URL 编码, 基于所提供的字符编码.
     * 执行和J2SDK1.4中的java.next.URLEncode.encode相同的函数, 如果唯一支持的平台是 1.4 或更高版本, 应该删除.
     * @param s 要URL 编码的字符串.
     * @param enc 字符编码
     * @return The URL encoded String
     */
    public static String URLEncode(String s, String enc) {

		if (s == null) {
		    return "null";
		}
	
		if (enc == null) {
		    enc = "ISO-8859-1";	// The default request encoding 
		}
	
		StringBuffer out = new StringBuffer(s.length());
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		OutputStreamWriter writer = null;
		try {
		    writer = new OutputStreamWriter(buf, enc);
		} catch (java.io.UnsupportedEncodingException ex) {
		    // 使用默认的编码?
		    writer = new OutputStreamWriter(buf);
		}
		
		for (int i = 0; i < s.length(); i++) {
		    int c = s.charAt(i);
		    if (c == ' ') {
		    	out.append('+');
		    } else if (isSafeChar(c)) {
		    	out.append((char)c);
		    } else {
				// 十六进制转换前转换为外部编码
				try {
				    writer.write(c);
				    writer.flush();
				} catch(IOException e) {
				    buf.reset();
				    continue;
				}
				byte[] ba = buf.toByteArray();
				for (int j = 0; j < ba.length; j++) {
				    out.append('%');
				    // 在每个字节缓冲区转换
				    out.append(Character.forDigit((ba[j]>>4) & 0xf, 16));
				    out.append(Character.forDigit(ba[j] & 0xf, 16));
				}
				buf.reset();
		    }
		}
		return out.toString();
    }

    private static boolean isSafeChar(int c) {
		if (c >= 'a' && c <= 'z') {
		    return true;
		}
		if (c >= 'A' && c <= 'Z') {
		    return true;
		}
		if (c >= '0' && c <= '9') {
		    return true;
		}
		if (c == '-' || c == '_' || c == '.' || c == '!' ||
		    c == '~' || c == '*' || c == '\'' || c == '(' || c == ')') {
		    return true;
		}
		return false;
    }
}
