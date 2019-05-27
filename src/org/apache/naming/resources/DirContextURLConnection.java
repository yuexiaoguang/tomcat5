package org.apache.naming.resources;

import java.net.URL;
import java.net.URLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.security.Permission;
import java.util.Date;
import java.util.Enumeration;
import java.util.Vector;
import javax.naming.NamingException;
import javax.naming.NamingEnumeration;
import javax.naming.NameClassPair;
import javax.naming.directory.DirContext;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import org.apache.naming.JndiPermission;
import org.apache.naming.resources.Resource;
import org.apache.naming.resources.ResourceAttributes;

/**
 * 连接到一个JNDI目录上下文.
 * <p>
 * Note: 所有对象的属性名是WebDAV的名字, 不是HTTP 名称, 所以这类从URLConnection重写一些方法, 使用正确的名称进行查询.
 * 内容处理程序也没有使用; 内容直接返回.
 */
public class DirContextURLConnection extends URLConnection {
    
    
    // ----------------------------------------------------------- Constructors
    
    
    public DirContextURLConnection(DirContext context, URL url) {
        super(url);
        if (context == null)
            throw new IllegalArgumentException
                ("Directory context can't be null");
        if (System.getSecurityManager() != null) {
            this.permission = new JndiPermission(url.toString());
	}
        this.context = context;
    }
    
    
    // ----------------------------------------------------- Instance Variables
    
    
    /**
     * 目录上下文
     */
    protected DirContext context;
    
    
    /**
     * 相关资源
     */
    protected Resource resource;
    
    
    /**
     * 相关DirContext.
     */
    protected DirContext collection;
    
    
    /**
     * 其他未知对象
     */
    protected Object object;
    
    
    /**
     * 属性.
     */
    protected Attributes attributes;
    
    
    /**
     * 日期.
     */
    protected long date;
    
    
    /**
     * 权限
     */
    protected Permission permission;


    // ------------------------------------------------------------- Properties
    
    
    /**
     * 连接到DirContext, 并检索绑定的对象, 以及它的属性. 如果没有对象与URL中指定的名称绑定, 抛出一个IOException.
     * 
     * @throws IOException Object not found
     */
    public void connect() throws IOException {
        
        if (!connected) {
            try {
                date = System.currentTimeMillis();
                String path = getURL().getFile();
                if (context instanceof ProxyDirContext) {
                    ProxyDirContext proxyDirContext = 
                        (ProxyDirContext) context;
                    String hostName = proxyDirContext.getHostName();
                    String contextName = proxyDirContext.getContextName();
                    if (hostName != null) {
                        if (!path.startsWith("/" + hostName + "/"))
                            return;
                        path = path.substring(hostName.length()+ 1);
                    }
                    if (contextName != null) {
                        if (!path.startsWith(contextName + "/")) {
                            return;
                        } else {
                            path = path.substring(contextName.length());
                        }
                    }
                }
                object = context.lookup(path);
                attributes = context.getAttributes(path);
                if (object instanceof Resource)
                    resource = (Resource) object;
                if (object instanceof DirContext)
                    collection = (DirContext) object;
            } catch (NamingException e) {
                // Object not found
            }
            connected = true;
        }
    }
    
    
    /**
     * 返回内容长度值
     */
    public int getContentLength() {
        return getHeaderFieldInt(ResourceAttributes.CONTENT_LENGTH, -1);
    }
    
    
    /**
     * 返回内容类型值
     */
    public String getContentType() {
        return getHeaderField(ResourceAttributes.CONTENT_TYPE);
    }
    
    
    /**
     * 返回最后修改日期
     */
    public long getDate() {
        return date;
    }
    
    
    /**
     * 返回最后修改日期.
     */
    public long getLastModified() {

        if (!connected) {
            // Try to connect (silently)
            try {
                connect();
            } catch (IOException e) {
            }
        }

        if (attributes == null)
            return 0;

        Attribute lastModified = 
            attributes.get(ResourceAttributes.LAST_MODIFIED);
        if (lastModified != null) {
            try {
                Date lmDate = (Date) lastModified.get();
                return lmDate.getTime();
            } catch (Exception e) {
            }
        }
        return 0;
    }
    
    
    /**
     * 返回指定标头字段的名称.
     */
    public String getHeaderField(String name) {

        if (!connected) {
            // Try to connect (silently)
            try {
                connect();
            } catch (IOException e) {
            }
        }
        
        if (attributes == null)
            return (null);

        Attribute attribute = attributes.get(name);
        try {
            return attribute.get().toString();
        } catch (Exception e) {
            // Shouldn't happen, unless the attribute has no value
        }
        return (null);
    }
    
    
    /**
     * 获取对象内容.
     */
    public Object getContent()
        throws IOException {
        
        if (!connected)
            connect();
        
        if (resource != null)
            return getInputStream();
        if (collection != null)
            return collection;
        if (object != null)
            return object;
        
        throw new FileNotFoundException();
    }
    
    
    /**
     * 获取对象内容
     */
    public Object getContent(Class[] classes)
        throws IOException {
        
        Object object = getContent();
        
        for (int i = 0; i < classes.length; i++) {
            if (classes[i].isInstance(object))
                return object;
        }
        return null;
    }
    
    
    /**
     * 获取输入流
     */
    public InputStream getInputStream() 
        throws IOException {
        
        if (!connected)
            connect();
        
        if (resource == null) {
            throw new FileNotFoundException();
        } else {
            // Reopen resource
            try {
                resource = (Resource) context.lookup(getURL().getFile());
            } catch (NamingException e) {
            }
        }
        return (resource.streamContent());
    }
    
    
    /**
     * 获取此URL的权限
     */
    public Permission getPermission() {
        return permission;
    }


    // --------------------------------------------------------- Public Methods
    
    
    /**
     * 列出此集合的子元素. 给定的名称与此URI的路径相关. 然后是子元素的完整URI : path + "/" + name.
     */
    public Enumeration list() throws IOException {
        
        if (!connected) {
            connect();
        }
        
        if ((resource == null) && (collection == null)) {
            throw new FileNotFoundException();
        }
        
        Vector result = new Vector();
        
        if (collection != null) {
            try {
                NamingEnumeration enumeration = context.list(getURL().getFile());
                while (enumeration.hasMoreElements()) {
                    NameClassPair ncp = (NameClassPair) enumeration.nextElement();
                    result.addElement(ncp.getName());
                }
            } catch (NamingException e) {
                // Unexpected exception
                throw new FileNotFoundException();
            }
        }
        return result.elements();
    }
}
