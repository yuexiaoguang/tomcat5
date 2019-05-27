package org.apache.catalina.startup;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.catalina.Host;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 扩展Host的appBase的 WAR.
 */
public class ExpandWar {

    private static Log log = LogFactory.getLog(ExpandWar.class);

    /**
     * The string resources for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * 将指定URL上找到的WAR文件扩展到解压的目录结构中, 返回到扩展目录的绝对路径名.
     *
     * @param host 正在安装的Host
     * @param war 要扩展的Web应用程序归档文件的URL(必须以 "jar:"开头)
     *
     * @exception IllegalArgumentException if this is not a "jar:" URL
     * @exception IOException 如果在扩展过程中遇到输入/输出错误
     */
    public static String expand(Host host, URL war)
        throws IOException {

        // 计算扩展目录的目录名
        if (host.getLogger().isDebugEnabled()) {
            host.getLogger().debug("expand(" + war.toString() + ")");
        }
        String pathname = war.toString().replace('\\', '/');
        if (pathname.endsWith("!/")) {
            pathname = pathname.substring(0, pathname.length() - 2);
        }
        int period = pathname.lastIndexOf('.');
        if (period >= pathname.length() - 4)
            pathname = pathname.substring(0, period);
        int slash = pathname.lastIndexOf('/');
        if (slash >= 0) {
            pathname = pathname.substring(slash + 1);
        }
        if (host.getLogger().isDebugEnabled()) {
            host.getLogger().debug("  Proposed directory name: " + pathname);
        }
        return expand(host, war, pathname);
    }


    /**
     * 将指定URL上找到的WAR文件扩展到解压的目录结构中, 返回到扩展目录的绝对路径名.
     *
     * @param host 正在安装的Host
     * @param war 要扩展的Web应用程序归档文件的URL(必须以 "jar:"开头)
     * @param pathname Web应用程序的上下文路径名
     *
     * @exception IllegalArgumentException if this is not a "jar:" URL
     * @exception IOException if an input/output error was encountered
     *  during expansion
     */
    public static String expand(Host host, URL war, String pathname)
        throws IOException {

        // 请确保没有这样的目录已经存在
        File appBase = new File(host.getAppBase());
        if (!appBase.isAbsolute()) {
            appBase = new File(System.getProperty("catalina.base"),
                               host.getAppBase());
        }
        if (!appBase.exists() || !appBase.isDirectory()) {
            throw new IOException
                (sm.getString("hostConfig.appBase",
                              appBase.getAbsolutePath()));
        }
        File docBase = new File(appBase, pathname);
        if (docBase.exists()) {
            // War file is already installed
            return (docBase.getAbsolutePath());
        }

        // 创建新的文档基目录
        docBase.mkdir();

        // 将WAR扩展到新的文档基目录
        JarURLConnection juc = (JarURLConnection) war.openConnection();
        juc.setUseCaches(false);
        JarFile jarFile = null;
        InputStream input = null;
        try {
            jarFile = juc.getJarFile();
            Enumeration jarEntries = jarFile.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) jarEntries.nextElement();
                String name = jarEntry.getName();
                int last = name.lastIndexOf('/');
                if (last >= 0) {
                    File parent = new File(docBase,
                                           name.substring(0, last));
                    parent.mkdirs();
                }
                if (name.endsWith("/")) {
                    continue;
                }
                input = jarFile.getInputStream(jarEntry);

                // Bugzilla 33636
                File expandedFile = expand(input, docBase, name);
                long lastModified = jarEntry.getTime();
                if ((lastModified != -1) && (lastModified != 0) && (expandedFile != null)) {
                    expandedFile.setLastModified(lastModified);
                }

                input.close();
                input = null;
            }
        } catch (IOException e) {
            // If something went wrong, delete expanded dir to keep things 
            // clean
            deleteDir(docBase);
            throw e;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Throwable t) {
                    ;
                }
                input = null;
            }
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (Throwable t) {
                    ;
                }
                jarFile = null;
            }
        }

        // 返回新文档基目录的绝对路径
        return (docBase.getAbsolutePath());

    }


    /**
     * 将指定的文件或目录复制到目的地.
     *
     * @param src 源文件
     * @param dest 目标文件
     */
    public static boolean copy(File src, File dest) {
        
        boolean result = true;
        
        String files[] = null;
        if (src.isDirectory()) {
            files = src.list();
            result = dest.mkdir();
        } else {
            files = new String[1];
            files[0] = "";
        }
        if (files == null) {
            files = new String[0];
        }
        for (int i = 0; (i < files.length) && result; i++) {
            File fileSrc = new File(src, files[i]);
            File fileDest = new File(dest, files[i]);
            if (fileSrc.isDirectory()) {
                result = copy(fileSrc, fileDest);
            } else {
                FileChannel ic = null;
                FileChannel oc = null;
                try {
                    ic = (new FileInputStream(fileSrc)).getChannel();
                    oc = (new FileOutputStream(fileDest)).getChannel();
                    ic.transferTo(0, ic.size(), oc);
                } catch (IOException e) {
                    log.error(sm.getString
                            ("expandWar.copy", fileSrc, fileDest), e);
                    result = false;
                } finally {
                    if (ic != null) {
                        try {
                            ic.close();
                        } catch (IOException e) {
                        }
                    }
                    if (oc != null) {
                        try {
                            oc.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
        }
        return result;
    }
    
    
    /**
     * 删除指定的目录，包括其所有内容和递归子目录.
     *
     * @param dir File object representing the directory to be deleted
     */
    public static boolean delete(File dir) {
        if (dir.isDirectory()) {
            return deleteDir(dir);
        } else {
            return dir.delete();
        }
    }
    
    
    /**
     * 删除指定的目录，包括其所有内容和递归子目录.
     *
     * @param dir File object representing the directory to be deleted
     */
    public static boolean deleteDir(File dir) {

        String files[] = dir.list();
        if (files == null) {
            files = new String[0];
        }
        for (int i = 0; i < files.length; i++) {
            File file = new File(dir, files[i]);
            if (file.isDirectory()) {
                deleteDir(file);
            } else {
                file.delete();
            }
        }
        return dir.delete();
    }


    /**
     * 将指定的输入流扩展到指定的目录, 创建指定的相对路径命名的文件.
     *
     * @param input 要复制的InputStream
     * @param docBase 正在扩展的文档基目录
     * @param name 被创建的该文件的相对路径名
     * @return A handle to the expanded File
     *
     * @exception IOException if an input/output error occurs
     */
    protected static File expand(InputStream input, File docBase, String name)
        throws IOException {

        File file = new File(docBase, name);
        BufferedOutputStream output = null;
        try {
            output = 
                new BufferedOutputStream(new FileOutputStream(file));
            byte buffer[] = new byte[2048];
            while (true) {
                int n = input.read(buffer);
                if (n <= 0)
                    break;
                output.write(buffer, 0, n);
            }
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        return file;
    }
}
