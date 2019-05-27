package org.apache.catalina.startup;


import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * <p>通用命令行工具, 应该在Catalina设置的常见的类装载器环境中执行.
 * 这应该从符合以下要求的命令行脚本中执行:</p>
 * <ul>
 * <li>通过<code>catalina.home</code>系统属性配置Tomcat安装目录的路径名.</li>
 * <li>设置系统路径, 包括<code>bootstrap.jar</code>和<code>$JAVA_HOME/lib/tools.jar</code>.</li>
 * </ul>
 *
 * <p>执行该工具的命令行看起来像:</p>
 * <pre>
 *   java -classpath $CLASSPATH org.apache.catalina.startup.Tool \
 *     ${options} ${classname} ${arguments}
 * </pre>
 *
 * <p>有以下替换内容:
 * <ul>
 * <li><strong>${options}</strong> - 此工具包装器的命令行选项.
 *     支持以下选项:
 *     <ul>
 *     <li><em>-ant</em> : 设置<code>ant.home</code>系统属性
 *         对应<code>catalina.home</code>的值(当命令行工具运行Ant时有用).</li>
 *     <li><em>-common</em> : 添加<code>common/classes</code>和<code>common/lib</code>到类装入器库.</li>
 *     <li><em>-debug</em> : 启用此包装器的调试消息.</li>
 *     <li><em>-server</em> : 添加<code>server/classes</code>和<code>server/lib</code>到类装入器库.</li>
 *     <li><em>-shared</em> : 添加<code>shared/classes</code>和<code>shared/lib</code>到类装入器库.</li>
 *     </ul>
 * <li><strong>${classname}</strong> - 应用程序主类的Java类完全限定名称.</li>
 * <li><strong>${arguments}</strong> - 传递给应用的<code>main()</code>方法的命令行参数.</li>
 * </ul>
 */
public final class Tool {


    private static Log log = LogFactory.getLog(Tool.class);
    
    // ------------------------------------------------------- Static Variables


    /**
     * 是否设置<code>ant.home</code>系统属性?
     */
    private static boolean ant = false;


    /**
     * 安装基础目录的路径名.
     */
    private static String catalinaHome = System.getProperty("catalina.home");


    /**
     * 是否包含存储库中的公共类?
     */
    private static boolean common = false;


    /**
     * 是否在存储库中包含服务器类?
     */
    private static boolean server = false;


    /**
     * 是否在存储库中包含共享类?
     */
    private static boolean shared = false;


    // ----------------------------------------------------------- Main Program


    /**
     * The main program for the bootstrap.
     *
     * @param args Command line arguments to be processed
     */
    public static void main(String args[]) {

        // Verify that "catalina.home" was passed.
        if (catalinaHome == null) {
            log.error("Must set 'catalina.home' system property");
            System.exit(1);
        }

        // Process command line options
        int index = 0;
        while (true) {
            if (index == args.length) {
                usage();
                System.exit(1);
            }
            if ("-ant".equals(args[index]))
                ant = true;
            else if ("-common".equals(args[index]))
                common = true;
            else if ("-server".equals(args[index]))
                server = true;
            else if ("-shared".equals(args[index]))
                shared = true;
            else
                break;
            index++;
        }
        if (index > args.length) {
            usage();
            System.exit(1);
        }

        // Set "ant.home" if requested
        if (ant)
            System.setProperty("ant.home", catalinaHome);

        // Construct the class loader we will be using
        ClassLoader classLoader = null;
        try {
            ArrayList packed = new ArrayList();
            ArrayList unpacked = new ArrayList();
            unpacked.add(new File(catalinaHome, "classes"));
            packed.add(new File(catalinaHome, "lib"));
            if (common) {
                unpacked.add(new File(catalinaHome,
                                      "common" + File.separator + "classes"));
                packed.add(new File(catalinaHome,
                                    "common" + File.separator + "lib"));
            }
            if (server) {
                unpacked.add(new File(catalinaHome,
                                      "server" + File.separator + "classes"));
                packed.add(new File(catalinaHome,
                                    "server" + File.separator + "lib"));
            }
            if (shared) {
                unpacked.add(new File(catalinaHome,
                                      "shared" + File.separator + "classes"));
                packed.add(new File(catalinaHome,
                                    "shared" + File.separator + "lib"));
            }
            classLoader =
                ClassLoaderFactory.createClassLoader
                ((File[]) unpacked.toArray(new File[0]),
                 (File[]) packed.toArray(new File[0]),
                 null);
        } catch (Throwable t) {
            log.error("Class loader creation threw exception", t);
            System.exit(1);
        }
        Thread.currentThread().setContextClassLoader(classLoader);

        // Load our application class
        Class clazz = null;
        String className = args[index++];
        try {
            if (log.isDebugEnabled())
                log.debug("Loading application class " + className);
            clazz = classLoader.loadClass(className);
        } catch (Throwable t) {
            log.error("Exception creating instance of " + className, t);
            System.exit(1);
        }

        // Locate the static main() method of the application class
        Method method = null;
        String params[] = new String[args.length - index];
        System.arraycopy(args, index, params, 0, params.length);
        try {
            if (log.isDebugEnabled())
                log.debug("Identifying main() method");
            String methodName = "main";
            Class paramTypes[] = new Class[1];
            paramTypes[0] = params.getClass();
            method = clazz.getMethod(methodName, paramTypes);
        } catch (Throwable t) {
            log.error("Exception locating main() method", t);
            System.exit(1);
        }

        // Invoke the main method of the application class
        try {
            if (log.isDebugEnabled())
                log.debug("Calling main() method");
            Object paramValues[] = new Object[1];
            paramValues[0] = params;
            method.invoke(null, paramValues);
        } catch (Throwable t) {
            log.error("Exception calling main() method", t);
            System.exit(1);
        }
    }

    /**
     * 显示此工具的用法
     */
    private static void usage() {
        log.info("Usage:  java org.apache.catalina.startup.Tool [<options>] <class> [<arguments>]");
    }
}
