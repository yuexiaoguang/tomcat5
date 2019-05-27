package org.apache.catalina.startup;


import java.io.File;
import java.net.URL;
import java.util.ArrayList;

import org.apache.catalina.loader.StandardClassLoader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * <p>构建Catalina的类加载器的帮助类. 工厂方法需要以下参数来构建一个新的类装入器(在所有情况下都有适当的默认值):</p>
 * <ul>
 * <li>包含在类装入器存储库中的解包类（和资源）的一组目录.</li>
 * <li>包含JAR文件中类和资源的一组目录.
 *     在这些目录中发现的每个可读JAR文件将被添加到类装入器的存储库中.</li>
 * <li><code>ClassLoader</code> 实例应该成为新类装入器的父节点.</li>
 * </ul>
 */
public final class ClassLoaderFactory {


    private static Log log = LogFactory.getLog(ClassLoaderFactory.class);

    // --------------------------------------------------------- Public Methods


    /**
     * 创建并返回一个新的类装入器, 基于配置默认值和指定的目录路径:
     *
     * @param unpacked 打开的目录的路径名数组，应该添加到类装入器的存储库中, 或者<code>null</code> 不考虑未解包的目录
     * @param packed 包含JAR文件应该被添加到类加载器的库目录的路径名的数组, 或者<code>null</code>不考虑JAR文件的目录
     * @param parent 新类装入器的父类装入器, 或者<code>null</code> 对于系统类装入器.
     *
     * @exception Exception if an error occurs constructing the class loader
     */
    public static ClassLoader createClassLoader(File unpacked[],
                                                File packed[],
                                                ClassLoader parent) throws Exception {
        return createClassLoader(unpacked, packed, null, parent);
    }


    /**
     * 创建并返回一个新的类装入器, 基于配置默认值和指定的目录路径:
     *
     * @param unpacked 打开的目录的路径名数组，应该添加到类装入器的存储库中, 或者<code>null</code> 不考虑未解包的目录
     * @param packed 包含JAR文件应该被添加到类加载器的库目录的路径名的数组, 或者<code>null</code>不考虑JAR文件的目录
     * @param urls 远程存储库的URL数组, 设计JAR资源或未压缩目录，这些目录应添加到类装入器的存储库中, 或者<code>null</code>不考虑JAR文件目录
     * @param parent 新类装入器的父类装入器, 或者<code>null</code> 对于系统类装入器.
     *
     * @exception Exception if an error occurs constructing the class loader
     */
    public static ClassLoader createClassLoader(File unpacked[],
                                                File packed[],
                                                URL urls[],
                                                ClassLoader parent)
        throws Exception {

        if (log.isDebugEnabled())
            log.debug("Creating new class loader");

        // Construct the "class path" for this class loader
        ArrayList list = new ArrayList();

        // 添加未解压的目录
        if (unpacked != null) {
            for (int i = 0; i < unpacked.length; i++)  {
                File file = unpacked[i];
                if (!file.exists() || !file.canRead())
                    continue;
                file = new File(file.getCanonicalPath() + File.separator);
                URL url = file.toURL();
                if (log.isDebugEnabled())
                    log.debug("  Including directory " + url);
                list.add(url);
            }
        }

        // 添加解压后的JAR 文件
        if (packed != null) {
            for (int i = 0; i < packed.length; i++) {
                File directory = packed[i];
                if (!directory.isDirectory() || !directory.exists() ||
                    !directory.canRead())
                    continue;
                String filenames[] = directory.list();
                for (int j = 0; j < filenames.length; j++) {
                    String filename = filenames[j].toLowerCase();
                    if (!filename.endsWith(".jar"))
                        continue;
                    File file = new File(directory, filenames[j]);
                    if (log.isDebugEnabled())
                        log.debug("  Including jar file " + file.getAbsolutePath());
                    URL url = file.toURL();
                    list.add(url);
                }
            }
        }

        // Add URLs
        if (urls != null) {
            for (int i = 0; i < urls.length; i++) {
                if (log.isDebugEnabled())
                    log.debug("  Including URL " + urls[i]);
                list.add(urls[i]);
            }
        }

        // 创建类加载器本身
        URL[] array = (URL[]) list.toArray(new URL[list.size()]);
        StandardClassLoader classLoader = null;
        if (parent == null)
            classLoader = new StandardClassLoader(array);
        else
            classLoader = new StandardClassLoader(array, parent);
        return (classLoader);
    }
}
