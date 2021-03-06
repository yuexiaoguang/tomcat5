package org.apache.naming;

import java.text.MessageFormat;
import java.util.Hashtable;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 一个国际化/本地化辅助类，从而减少处理ResourceBundles的麻烦和照顾消息格式化的常见情况，否则需要创建对象数组等等.
 *
 * <p>StringManager在包的基础上运行. 每个包都有一个StringManager可以创建并通过getmanager方法调用访问.
 *
 * <p>StringManager将查找包名加上"LocalStrings"后缀命名的ResourceBundle.
 * 在实践中, 这意味着将包含本地化信息，在位于类路径中的包目录的LocalStrings.properties文件.
 */
public class StringManager {

    /**
     * The ResourceBundle for this StringManager.
     */
    private ResourceBundle bundle;

    /**
     * 所有的访问是由调用静态getManager方法，因此每个包只有一个StringManager被创建.
     *
     * @param packageName Name of package to create StringManager for.
     */
    private StringManager(String packageName) {
		String bundleName = packageName + ".LocalStrings";
		bundle = ResourceBundle.getBundle(bundleName);
    }

    /**
     * 从底层资源包获取一个字符串.
     *
     * @param key 
     */
    public String getString(String key) {
        if (key == null) {
            String msg = "key is null";

            throw new NullPointerException(msg);
        }

        String str = null;
        try {
	    str = bundle.getString(key);
        } catch (MissingResourceException mre) {
            str = "Cannot find message associated with key '" + key + "'";
        }
        return str;
    }

    /**
     * 从底层资源包获取一个字符串, 并用给定的参数集格式化它.
     *
     * @param key
     * @param args
     */
    public String getString(String key, Object[] args) {
		String iString = null;
	        String value = getString(key);
	
		// 此检查运行时异常是一些预先1.1.6 VM的不要对传入的对象自动tostring()和吐出
		try {
            // 确保参数不为空，预1.2虚拟机不吐出
            Object nonNullArgs[] = args;
            for (int i=0; i<args.length; i++) {
				if (args[i] == null) {
				    if (nonNullArgs==args) nonNullArgs=(Object[])args.clone();
				    nonNullArgs[i] = "null";
				}
		    }
            iString = MessageFormat.format(value, nonNullArgs);
		} catch (IllegalArgumentException iae) {
		    StringBuffer buf = new StringBuffer();
		    buf.append(value);
		    for (int i = 0; i < args.length; i++) {
		    	buf.append(" arg[" + i + "]=" + args[i]);
		    }
		    iString = buf.toString();
		}
		return iString;
    }

    /**
     * 从底层资源包中获取一个字符串，并用给定的对象参数格式化它. 这个参数当然可以是String对象.
     *
     * @param key
     * @param arg
     */
    public String getString(String key, Object arg) {
		Object[] args = new Object[] {arg};
		return getString(key, args);
    }

    /**
     * 从底层资源包中获取一个字符串，并用给定的对象参数格式化它. 这些参数当然可以是字符串对象.
     *
     * @param key
     * @param arg1
     * @param arg2
     */
    public String getString(String key, Object arg1, Object arg2) {
		Object[] args = new Object[] {arg1, arg2};
		return getString(key, args);
    }
    
    /**
     * 从底层资源包中获取一个字符串，并用给定的对象参数格式化它. 这些参数当然可以是字符串对象.
     *
     * @param key
     * @param arg1
     * @param arg2
     * @param arg3
     */
    public String getString(String key, Object arg1, Object arg2, Object arg3) {
		Object[] args = new Object[] {arg1, arg2, arg3};
		return getString(key, args);
    }
    
    /**
     * 从底层资源包中获取一个字符串，并用给定的对象参数格式化它. 这些参数当然可以是字符串对象.
     *
     * @param key
     * @param arg1
     * @param arg2
     * @param arg3
     * @param arg4
     */
    public String getString(String key, Object arg1, Object arg2, Object arg3, Object arg4) {
		Object[] args = new Object[] {arg1, arg2, arg3, arg4};
		return getString(key, args);
    }   
    // --------------------------------------------------------------
    // STATIC SUPPORT METHODS
    // --------------------------------------------------------------

    private static Hashtable managers = new Hashtable();

    /**
     * 获取一个特定的StringManager. 如果包的管理器已经存在, 它将被重用, 然后一个新的StringManager将创建并返回.
     *
     * @param packageName
     */
    public synchronized static StringManager getManager(String packageName) {
		StringManager mgr = (StringManager)managers.get(packageName);
		if (mgr == null) {
		    mgr = new StringManager(packageName);
		    managers.put(packageName, mgr);
		}
		return mgr;
    }
}
