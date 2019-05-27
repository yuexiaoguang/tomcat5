package org.apache.catalina.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.util.ArrayList;

/**
 * 清单文件及其可用扩展和所需扩展
 */
public class ManifestResource {
    
    // ------------------------------------------------------------- Properties

    // 这些是用于确定影响错误消息的资源类型
    public static final int SYSTEM = 1;
    public static final int WAR = 2;
    public static final int APPLICATION = 3;
    
    private HashMap availableExtensions = null;
    private ArrayList requiredExtensions = null;
    
    private String resourceName = null;
    private int resourceType = -1;
        
    public ManifestResource(String resourceName, Manifest manifest, 
                            int resourceType) {
        this.resourceName = resourceName;
        this.resourceType = resourceType;
        processManifest(manifest);
    }
    
    /**
     * 获取资源的名称
     */
    public String getResourceName() {
        return resourceName;
    }

    /**
     * 获取可用扩展的映射
     *
     * @return Map of available extensions
     */
    public HashMap getAvailableExtensions() {
        return availableExtensions;
    }
    
    /**
     * 获取所需扩展名的列表
     *
     * @return List of required extensions
     */
    public ArrayList getRequiredExtensions() {
        return requiredExtensions;   
    }
    
    // --------------------------------------------------------- Public Methods

    /**
     * 获取可用扩展名的数目
     *
     * @return 可用扩展的数量
     */
    public int getAvailableExtensionCount() {
        return (availableExtensions != null) ? availableExtensions.size() : 0;
    }
    
    /**
     * 获取所需扩展名的数目
     *
     * @return 所需扩展的数量
     */
    public int getRequiredExtensionCount() {
        return (requiredExtensions != null) ? requiredExtensions.size() : 0;
    }
    
    /**
     * 检查<code>ManifestResource</code>是否有一个所需的扩展.
     *
     * @return true 如果是所需的扩展
     */
    public boolean requiresExtensions() {
        return (requiredExtensions != null) ? true : false;
    }
    
    /**
     * 检查<code>ManifestResource</code>是否有一个可用的扩展
     *
     * @param key extension identifier
     *
     * @return true if extension available
     */
    public boolean containsExtension(String key) {
        return (availableExtensions != null) ?
                availableExtensions.containsKey(key) : false;
    }
    
    /**
     * 返回<code>true</code>如果所有的扩展依赖都满足此<code>ManifestResource</code>对象.
     *
     * @return boolean true 如果满足所有扩展依赖项
     */
    public boolean isFulfilled() {
        if (requiredExtensions == null) {
            return false;
        }
        Iterator it = requiredExtensions.iterator();
        while (it.hasNext()) {
            Extension ext = (Extension)it.next();
            if (!ext.isFulfilled()) return false;            
        }
        return true;
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer("ManifestResource[");
        sb.append(resourceName);

        sb.append(", isFulfilled=");
        sb.append(isFulfilled() +"");
        sb.append(", requiredExtensionCount =");
        sb.append(getRequiredExtensionCount());
        sb.append(", availableExtensionCount=");
        sb.append(getAvailableExtensionCount());
        switch (resourceType) {
            case SYSTEM : sb.append(", resourceType=SYSTEM"); break;
            case WAR : sb.append(", resourceType=WAR"); break;
            case APPLICATION : sb.append(", resourceType=APPLICATION"); break;
        }
        sb.append("]");
        return (sb.toString());
    }


    // -------------------------------------------------------- Private Methods

    private void processManifest(Manifest manifest) {
        availableExtensions = getAvailableExtensions(manifest);
        requiredExtensions = getRequiredExtensions(manifest);
    }
    
    /**
     * 返回一组<code>Extension</code>对象，表示与指定清单相关联的应用程序所需的可选包.
     *
     * @param manifest 要解析的清单
     *
     * @return 所需扩展的列表, 或者 null
     */
    private ArrayList getRequiredExtensions(Manifest manifest) {

        Attributes attributes = manifest.getMainAttributes();
        String names = attributes.getValue("Extension-List");
        if (names == null)
            return null;

        ArrayList extensionList = new ArrayList();
        names += " ";

        while (true) {

            int space = names.indexOf(' ');
            if (space < 0)
                break;
            String name = names.substring(0, space).trim();
            names = names.substring(space + 1);

            String value =
                attributes.getValue(name + "-Extension-Name");
            if (value == null)
                continue;
            Extension extension = new Extension();
            extension.setExtensionName(value);
            extension.setImplementationURL
                (attributes.getValue(name + "-Implementation-URL"));
            extension.setImplementationVendorId
                (attributes.getValue(name + "-Implementation-Vendor-Id"));
            String version = attributes.getValue(name + "-Implementation-Version");
            extension.setImplementationVersion(version);
            extension.setSpecificationVersion
                (attributes.getValue(name + "-Specification-Version"));
            extensionList.add(extension);
        }
        return extensionList;
    }
    
    /**
     * 返回一组<code>Extension</code>对象， 表示与指定清单相关联的应用程序捆绑的可选包.
     *
     * @param manifest 要解析的清单
     *
     * @return 有效扩展的Map, 或者 null
     */
    private HashMap getAvailableExtensions(Manifest manifest) {

        Attributes attributes = manifest.getMainAttributes();
        String name = attributes.getValue("Extension-Name");
        if (name == null)
            return null;

        HashMap extensionMap = new HashMap();

        Extension extension = new Extension();
        extension.setExtensionName(name);
        extension.setImplementationURL(
            attributes.getValue("Implementation-URL"));
        extension.setImplementationVendor(
            attributes.getValue("Implementation-Vendor"));
        extension.setImplementationVendorId(
            attributes.getValue("Implementation-Vendor-Id"));
        extension.setImplementationVersion(
            attributes.getValue("Implementation-Version"));
        extension.setSpecificationVersion(
            attributes.getValue("Specification-Version"));

        if (!extensionMap.containsKey(extension.getUniqueId())) {
            extensionMap.put(extension.getUniqueId(), extension);
        }
        return extensionMap;
    }
}
