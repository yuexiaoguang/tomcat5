package org.apache.naming.factory;

import java.util.Hashtable;
import java.util.Enumeration;
import javax.naming.Name;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.RefAddr;
import javax.naming.spi.ObjectFactory;
import org.apache.naming.ResourceRef;

import java.beans.Introspector;
import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;

import java.lang.reflect.Method;

/**
 * 符合JavaBean规范的任何资源的对象工厂.
 * 
 * <p>这个工厂可以在<code>conf/server.xml</code>配置文件中使用<code>&lt;DefaultContext&gt;</code>
 * 或<code>&lt;Context&gt;</code>元素配置. 工厂配置的一个例子是:</p>
 * <pre>
 * &lt;Resource name="jdbc/myDataSource" auth="SERVLET"
 *   type="oracle.jdbc.pool.OracleConnectionCacheImpl"/&gt;
 * &lt;ResourceParams name="jdbc/myDataSource"&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;factory&lt;/name&gt;
 *     &lt;value&gt;org.apache.naming.factory.BeanFactory&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;driverType&lt;/name&gt;
 *     &lt;value&gt;thin&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;serverName&lt;/name&gt;
 *     &lt;value&gt;hue&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;networkProtocol&lt;/name&gt;
 *     &lt;value&gt;tcp&lt;/value&gt;
 *   &lt;/parameter&gt; 
 *   &lt;parameter&gt;
 *     &lt;name&gt;databaseName&lt;/name&gt;
 *     &lt;value&gt;XXXX&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;portNumber&lt;/name&gt;
 *     &lt;value&gt;NNNN&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;user&lt;/name&gt;
 *     &lt;value&gt;XXXX&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;password&lt;/name&gt;
 *     &lt;value&gt;XXXX&lt;/value&gt;
 *   &lt;/parameter&gt;
 *   &lt;parameter&gt;
 *     &lt;name&gt;maxLimit&lt;/name&gt;
 *     &lt;value&gt;5&lt;/value&gt;
 *   &lt;/parameter&gt;
 * &lt;/ResourceParams&gt;
 * </pre>
 */
public class BeanFactory implements ObjectFactory {

    /**
     * 创建一个新bean实例
     * 
     * @param obj 描述bean的引用对象
     */
    public Object getObjectInstance(Object obj, Name name, Context nameCtx,
                                    Hashtable environment) throws NamingException {

        if (obj instanceof ResourceRef) {
            try {
                Reference ref = (Reference) obj;
                String beanClassName = ref.getClassName();
                Class beanClass = null;
                ClassLoader tcl = 
                    Thread.currentThread().getContextClassLoader();
                if (tcl != null) {
                    try {
                        beanClass = tcl.loadClass(beanClassName);
                    } catch(ClassNotFoundException e) {
                    }
                } else {
                    try {
                        beanClass = Class.forName(beanClassName);
                    } catch(ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                if (beanClass == null) {
                    throw new NamingException
                        ("Class not found: " + beanClassName);
                }
                
                BeanInfo bi = Introspector.getBeanInfo(beanClass);
                PropertyDescriptor[] pda = bi.getPropertyDescriptors();
                
                Object bean = beanClass.newInstance();
                
                Enumeration e = ref.getAll();
                while (e.hasMoreElements()) {
                    
                    RefAddr ra = (RefAddr) e.nextElement();
                    String propName = ra.getType();
                    
                    if (propName.equals(Constants.FACTORY) ||
                        propName.equals("scope") || propName.equals("auth")) {
                        continue;
                    }
                    
                    String value = (String)ra.getContent();
                    
                    Object[] valueArray = new Object[1];
                    
                    int i = 0;
                    for (i = 0; i<pda.length; i++) {

                        if (pda[i].getName().equals(propName)) {

                            Class propType = pda[i].getPropertyType();

                            if (propType.equals(String.class)) {
                                valueArray[0] = value;
                            } else if (propType.equals(Character.class) 
                                       || propType.equals(char.class)) {
                                valueArray[0] = new Character(value.charAt(0));
                            } else if (propType.equals(Byte.class) 
                                       || propType.equals(byte.class)) {
                                valueArray[0] = new Byte(value);
                            } else if (propType.equals(Short.class) 
                                       || propType.equals(short.class)) {
                                valueArray[0] = new Short(value);
                            } else if (propType.equals(Integer.class) 
                                       || propType.equals(int.class)) {
                                valueArray[0] = new Integer(value);
                            } else if (propType.equals(Long.class) 
                                       || propType.equals(long.class)) {
                                valueArray[0] = new Long(value);
                            } else if (propType.equals(Float.class) 
                                       || propType.equals(float.class)) {
                                valueArray[0] = new Float(value);
                            } else if (propType.equals(Double.class) 
                                       || propType.equals(double.class)) {
                                valueArray[0] = new Double(value);
                            } else if (propType.equals(Boolean.class)
                                       || propType.equals(boolean.class)) {
                                valueArray[0] = new Boolean(value);
                            } else {
                                throw new NamingException
                                    ("String conversion for property type '"
                                     + propType.getName() + "' not available");
                            }
                            
                            Method setProp = pda[i].getWriteMethod();
                            if (setProp != null) {
                                setProp.invoke(bean, valueArray);
                            } else {
                                throw new NamingException
                                    ("Write not allowed for property: " + propName);
                            }
                            break;
                        }
                    }

                    if (i == pda.length) {
                        throw new NamingException("No set method found for property: " + propName);
                    }
                }
                return bean;
            } catch (java.beans.IntrospectionException ie) {
                NamingException ne = new NamingException(ie.getMessage());
                ne.setRootCause(ie);
                throw ne;
            } catch (java.lang.IllegalAccessException iae) {
                NamingException ne = new NamingException(iae.getMessage());
                ne.setRootCause(iae);
                throw ne;
            } catch (java.lang.InstantiationException ie2) {
                NamingException ne = new NamingException(ie2.getMessage());
                ne.setRootCause(ie2);
                throw ne;
            } catch (java.lang.reflect.InvocationTargetException ite) {
                NamingException ne = new NamingException(ite.getMessage());
                ne.setRootCause(ite);
                throw ne;
            }
        } else {
            return null;
        }
    }
}
