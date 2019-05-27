package org.apache.catalina.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import javax.naming.Binding;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;

import org.apache.catalina.core.StandardContext;
import org.apache.naming.resources.Resource;

/**
 * 确保所有的扩展依赖对于Web应用是满足的. 这个类建立了一个有效的应用扩展的清单并验证了这些扩展.
 */
public final class ExtensionValidator {

    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog(ExtensionValidator.class);

    /**
     * The string resources for this package.
     */
    private static StringManager sm = StringManager.getManager("org.apache.catalina.util");
    
    private static HashMap containerAvailableExtensions = null;
    private static ArrayList containerManifestResources = new ArrayList();
    private static ResourceBundle messages = null;


    // ----------------------------------------------------- Static Initializer


    /**
     *  这个静态初始化器加载了所有Web应用程序都可用的容器级扩展名. 这种方法通过"java.ext.dirs"系统属性扫描所有可用的扩展目录. 
     *
     *  系统类路径也被扫描为JAR文件，这些文件可能包含可用的扩展名.
     */
    static {

        // 检查容器级可选包
        String systemClasspath = System.getProperty("java.class.path");

        StringTokenizer strTok = new StringTokenizer(systemClasspath, 
                                                     File.pathSeparator);

        // 在类路径中建立一个列表的jar文件
        while (strTok.hasMoreTokens()) {
            String classpathItem = strTok.nextToken();
            if (classpathItem.toLowerCase().endsWith(".jar")) {
                File item = new File(classpathItem);
                if (item.exists()) {
                    try {
                        addSystemResource(item);
                    } catch (IOException e) {
                        log.error(sm.getString
                                  ("extensionValidator.failload", item), e);
                    }
                }
            }
        }
        // 将指定的文件夹添加到列表中
        addFolderList("java.ext.dirs");
        addFolderList("catalina.ext.dirs");
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 一个Web应用程序的运行时验证.
     *
     * 此方法使用JNDI查找<code>DirContext</code>目录下的资源. 它定位web应用的/META-INF/目录下的 MANIFEST.MF文件，
     * 和WEB-INF/lib目录下的每个JAR文件的MANIFEST.MF 文件, 并创建一个<code>ManifestResorce<code>对象的<code>ArrayList</code>.
     * 这些对象随后传递给validateManifestResources 方法验证.
     *
     * @param dirContext Web应用程序的根目录
     * @param context Logger和应用路径的上下文
     *
     * @return true 如果所有需要的扩展都满足
     */
    public static synchronized boolean validateApplication(
                                           DirContext dirContext, 
                                           StandardContext context) throws IOException {

        String appName = context.getPath();
        ArrayList appManifestResources = new ArrayList();
        ManifestResource appManifestResource = null;
        // 如果应用程序上下文为null，则它不存在，因此无效
        if (dirContext == null) return false;
        // 查找Web应用清单
        InputStream inputStream = null;
        try {
            NamingEnumeration wne = dirContext.listBindings("/META-INF/");
            Binding binding = (Binding) wne.nextElement();
            if (binding.getName().toUpperCase().equals("MANIFEST.MF")) {
                Resource resource = (Resource)dirContext.lookup
                                    ("/META-INF/" + binding.getName());
                inputStream = resource.streamContent();
                Manifest manifest = new Manifest(inputStream);
                inputStream.close();
                inputStream = null;
                ManifestResource mre = new ManifestResource
                    (sm.getString("extensionValidator.web-application-manifest"),
                    manifest, ManifestResource.WAR);
                appManifestResources.add(mre);
            } 
        } catch (NamingException nex) {
            // 应用程序不包含 MANIFEST.MF 文件
        } catch (NoSuchElementException nse) {
            // 应用程序不包含 MANIFEST.MF 文件
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Throwable t) {
                    // Ignore
                }
            }
        }

        // Locate the Manifests for all bundled JARs
        NamingEnumeration ne = null;
        try {
            if (dirContext != null) {
                ne = dirContext.listBindings("WEB-INF/lib/");
            }
            while ((ne != null) && ne.hasMoreElements()) {
                Binding binding = (Binding)ne.nextElement();
                if (!binding.getName().toLowerCase().endsWith(".jar")) {
                    continue;
                }
                Resource resource = (Resource)dirContext.lookup
                                        ("/WEB-INF/lib/" + binding.getName());
                Manifest jmanifest = getManifest(resource.streamContent());
                if (jmanifest != null) {
                    ManifestResource mre = new ManifestResource(
                                                binding.getName(),
                                                jmanifest, 
                                                ManifestResource.APPLICATION);
                    appManifestResources.add(mre);
                }
            }
        } catch (NamingException nex) {
            // 跳出此应用程序的检查，因为它没有资源
        }

        return validateManifestResources(appName, appManifestResources);
    }


    /**
     * 指定的系统JAR 文件是否包含 MANIFEST, 并将其添加到容器的资源清单.
     *
     * @param jarFile The system JAR whose manifest to add
     */
    public static void addSystemResource(File jarFile) throws IOException {
        Manifest manifest = getManifest(new FileInputStream(jarFile));
        if (manifest != null)  {
            ManifestResource mre
                = new ManifestResource(jarFile.getAbsolutePath(),
                                       manifest,
                                       ManifestResource.SYSTEM);
            containerManifestResources.add(mre);
        }
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 验证<code>ManifestResource</code>对象的<code>ArrayList</code>. 此方法需要应用程序名(这是应用程序运行时的上下文根).  
     *
     * 如果任何<code>ManifestResource</code>对象代表的扩展依赖不满足, 返回<code>false</false>.
     *
     * 这个方法还应该提供一个Web应用程序的静态验证, 如果提供必要的参数.
     *
     * @param appName 将出现在错误消息中的应用程序的名称
     * @param resources 要验证的<code>ManifestResource</code>对象的列表
     *
     * @return true 如果满足资源文件要求
     */
    private static boolean validateManifestResources(String appName, 
                                                     ArrayList resources) {
        boolean passes = true;
        int failureCount = 0;        
        HashMap availableExtensions = null;

        Iterator it = resources.iterator();
        while (it.hasNext()) {
            ManifestResource mre = (ManifestResource)it.next();
            ArrayList requiredList = mre.getRequiredExtensions();
            if (requiredList == null) {
                continue;
            }

            // 建立可用的扩展列表
            if (availableExtensions == null) {
                availableExtensions = buildAvailableExtensionsMap(resources);
            }

            // 如果尚未构建容器级资源映射，则加载它
            if (containerAvailableExtensions == null) {
                containerAvailableExtensions
                    = buildAvailableExtensionsMap(containerManifestResources);
            }

            // 遍历所需扩展名的列表
            Iterator rit = requiredList.iterator();
            while (rit.hasNext()) {
                Extension requiredExt = (Extension)rit.next();
                String extId = requiredExt.getUniqueId();
                // 检查应用本身的扩展
                if (availableExtensions != null
                                && availableExtensions.containsKey(extId)) {
                   Extension targetExt = (Extension)
                       availableExtensions.get(extId);
                   if (targetExt.isCompatibleWith(requiredExt)) {
                       requiredExt.setFulfilled(true);
                   }
                // 检查扩展名的容器级别列表
                } else if (containerAvailableExtensions != null
                        && containerAvailableExtensions.containsKey(extId)) {
                   Extension targetExt = (Extension)
                       containerAvailableExtensions.get(extId);
                   if (targetExt.isCompatibleWith(requiredExt)) {
                       requiredExt.setFulfilled(true);
                   }
                } else {
                    // Failure
                    log.info(sm.getString(
                        "extensionValidator.extension-not-found-error",
                        appName, mre.getResourceName(),
                        requiredExt.getExtensionName()));
                    passes = false;
                    failureCount++;
                }
            }
        }

        if (!passes) {
            log.info(sm.getString(
                     "extensionValidator.extension-validation-error", appName,
                     failureCount + ""));
        }

        return passes;
    }
    
   /**
    * 构建这个可用扩展列表，这样我们每次迭代遍历所需扩展列表时就不必重新构建这个列表. 
    * <code>MainfestResource</code>对象的所有可用的扩展将被添加到一个在第一个依赖项列表处理过程中返回的HashMap. 
    *
    * key 是 name + 实现类版本.
    *
    * NOTE: 只有在需要检查依赖项的情况下才能构建列表(性能优化).
    *
    * @param resources <code>ManifestResource</code>对象的列表
    *
    * @return HashMap 可用扩展名的Map
    */
    private static HashMap buildAvailableExtensionsMap(ArrayList resources) {

        HashMap availableMap = null;

        Iterator it = resources.iterator();
        while (it.hasNext()) {
            ManifestResource mre = (ManifestResource)it.next();
            HashMap map = mre.getAvailableExtensions();
            if (map != null) {
                Iterator values = map.values().iterator();
                while (values.hasNext()) {
                    Extension ext = (Extension) values.next();
                    if (availableMap == null) {
                        availableMap = new HashMap();
                        availableMap.put(ext.getUniqueId(), ext);
                    } else if (!availableMap.containsKey(ext.getUniqueId())) {
                        availableMap.put(ext.getUniqueId(), ext);
                    }
                }
            }
        }

        return availableMap;
    }
    
    /**
     * 从JAR文件或WAR文件返回的清单
     *
     * @param inStream Input stream to a WAR or JAR file
     * @return The WAR's or JAR's manifest
     */
    private static Manifest getManifest(InputStream inStream)
            throws IOException {

        Manifest manifest = null;
        JarInputStream jin = null;

        try {
            jin = new JarInputStream(inStream);
            manifest = jin.getManifest();
            jin.close();
            jin = null;
        } finally {
            if (jin != null) {
                try {
                    jin.close();
                } catch (Throwable t) {
                    // Ignore
                }
            }
        }
        return manifest;
    }


    /**
     * 将指定的JAR添加到扩展列表中.
     */
    private static void addFolderList(String property) {

        // 获取扩展目录中的文件
        String extensionsDir = System.getProperty(property);
        if (extensionsDir != null) {
            StringTokenizer extensionsTok
                = new StringTokenizer(extensionsDir, File.pathSeparator);
            while (extensionsTok.hasMoreTokens()) {
                File targetDir = new File(extensionsTok.nextToken());
                if (!targetDir.exists() || !targetDir.isDirectory()) {
                    continue;
                }
                File[] files = targetDir.listFiles();
                for (int i = 0; i < files.length; i++) {
                    if (files[i].getName().toLowerCase().endsWith(".jar")) {
                        try {
                            addSystemResource(files[i]);
                        } catch (IOException e) {
                            log.error(sm.getString("extensionValidator.failload", files[i]), e);
                        }
                    }
                }
            }
        }
    }
}
