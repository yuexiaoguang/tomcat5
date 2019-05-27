package org.apache.catalina.session;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import javax.servlet.ServletContext;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Loader;
import org.apache.catalina.Session;
import org.apache.catalina.Store;
import org.apache.catalina.util.CustomObjectInputStream;


/**
 * <b>Store</b>接口实现类，在已配置的目录中使用每个保存会话的文件. 
 * 保存的静止的会话仍将过期.
 */
public final class FileStore extends StoreBase implements Store {

    // ----------------------------------------------------- Constants

    /**
     */
    private static final String FILE_EXT = ".session";


    // ----------------------------------------------------- Instance Variables


    /**
     * 存储在会话目录的路径.
     * 这可能是一个绝对路径名, 或相对于此应用程序的临时工作目录解析的相对路径.
     */
    private String directory = ".";


    /**
     * 存储会话的目录.
     */
    private File directoryFile = null;


    /**
     * 实现类的描述信息.
     */
    private static final String info = "FileStore/1.0";

    /**
     * 注册此存储的名称，用于日志记录.
     */
    private static final String storeName = "fileStore";

    /**
     * 注册后台线程的名称.
     */
    private static final String threadName = "FileStore";


    // ------------------------------------------------------------- Properties


    /**
     * 返回目录路径.
     */
    public String getDirectory() {
        return (directory);
    }


    /**
     * 设置目录路径.
     *
     * @param path The new directory path
     */
    public void setDirectory(String path) {
        String oldDirectory = this.directory;
        this.directory = path;
        this.directoryFile = null;
        support.firePropertyChange("directory", oldDirectory, this.directory);
    }


    /**
     * 返回描述信息和版本号, 格式为
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    public String getInfo() {
        return (info);
    }

    /**
     * 返回后台线程名称.
     */
    public String getThreadName() {
        return(threadName);
    }

    /**
     * 返回名称，用于记录日志.
     */
    public String getStoreName() {
        return(storeName);
    }


    /**
     * 返回当前会话的数目.
     *
     * @exception IOException if an input/output error occurs
     */
    public int getSize() throws IOException {

        // 获取存储目录中的文件列表
        File file = directory();
        if (file == null) {
            return (0);
        }
        String files[] = file.list();

        // 计算哪些文件是会话
        int keycount = 0;
        for (int i = 0; i < files.length; i++) {
            if (files[i].endsWith(FILE_EXT)) {
                keycount++;
            }
        }
        return (keycount);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 删除所有会话.
     *
     * @exception IOException if an input/output error occurs
     */
    public void clear() throws IOException {
        String[] keys = keys();
        for (int i = 0; i < keys.length; i++) {
            remove(keys[i]);
        }
    }


    /**
     * 返回一个数组包含当前保存的所有会话的会话标识符. 
     * 如果没有, 返回一个零长度数组.
     *
     * @exception IOException if an input/output error occurred
     */
    public String[] keys() throws IOException {

        // 获取存储目录中的文件列表
        File file = directory();
        if (file == null) {
            return (new String[0]);
        }

        String files[] = file.list();
        
        // Bugzilla 32130
        if((files == null) || (files.length < 1)) {
            return (new String[0]);
        }

        // Build and return the list of session identifiers
        ArrayList list = new ArrayList();
        int n = FILE_EXT.length();
        for (int i = 0; i < files.length; i++) {
            if (files[i].endsWith(FILE_EXT)) {
                list.add(files[i].substring(0, files[i].length() - n));
            }
        }
        return ((String[]) list.toArray(new String[list.size()]));
    }


    /**
     * 加载并返回与指定会话标识符关联的会话, 不删除它. 
     * 如果没有, 返回<code>null</code>.
     *
     * @param id Session identifier of the session to load
     *
     * @exception ClassNotFoundException if a deserialization error occurs
     * @exception IOException if an input/output error occurs
     */
    public Session load(String id)
        throws ClassNotFoundException, IOException {

        // 打开一个输入流到指定的路径
        File file = file(id);
        if (file == null) {
            return (null);
        }

        if (! file.exists()) {
            return (null);
        }
        if (manager.getContainer().getLogger().isDebugEnabled()) {
            manager.getContainer().getLogger().debug(sm.getString(getStoreName()+".loading",
                             id, file.getAbsolutePath()));
        }

        FileInputStream fis = null;
        ObjectInputStream ois = null;
        Loader loader = null;
        ClassLoader classLoader = null;
        try {
            fis = new FileInputStream(file.getAbsolutePath());
            BufferedInputStream bis = new BufferedInputStream(fis);
            Container container = manager.getContainer();
            if (container != null)
                loader = container.getLoader();
            if (loader != null)
                classLoader = loader.getClassLoader();
            if (classLoader != null)
                ois = new CustomObjectInputStream(bis, classLoader);
            else
                ois = new ObjectInputStream(bis);
        } catch (FileNotFoundException e) {
            if (manager.getContainer().getLogger().isDebugEnabled())
                manager.getContainer().getLogger().debug("No persisted data file found");
            return (null);
        } catch (IOException e) {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException f) {
                    ;
                }
                ois = null;
            }
            throw e;
        }

        try {
            StandardSession session =
                (StandardSession) manager.createEmptySession();
            session.readObjectData(ois);
            session.setManager(manager);
            return (session);
        } finally {
            // Close the input stream
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException f) {
                    ;
                }
            }
        }
    }


    /**
     * 删除指定的会话标识符的会话 . 如果没有, 什么都不做.
     *
     * @param id Session identifier of the Session to be removed
     *
     * @exception IOException if an input/output error occurs
     */
    public void remove(String id) throws IOException {

        File file = file(id);
        if (file == null) {
            return;
        }
        if (manager.getContainer().getLogger().isDebugEnabled()) {
            manager.getContainer().getLogger().debug(sm.getString(getStoreName()+".removing",
                             id, file.getAbsolutePath()));
        }
        file.delete();
    }


    /**
     * 保存指定的 Session. 替换先前保存的关联会话标识符的信息.
     *
     * @param session Session to be saved
     *
     * @exception IOException if an input/output error occurs
     */
    public void save(Session session) throws IOException {

        // 打开输出流到指定的路径
        File file = file(session.getIdInternal());
        if (file == null) {
            return;
        }
        if (manager.getContainer().getLogger().isDebugEnabled()) {
            manager.getContainer().getLogger().debug(sm.getString(getStoreName()+".saving",
                             session.getIdInternal(), file.getAbsolutePath()));
        }
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fos = new FileOutputStream(file.getAbsolutePath());
            oos = new ObjectOutputStream(new BufferedOutputStream(fos));
        } catch (IOException e) {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException f) {
                    ;
                }
            }
            throw e;
        }

        try {
            ((StandardSession)session).writeObjectData(oos);
        } finally {
            oos.close();
        }
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 会话持久化目录的路径信息. 如果目录不存在，则将创建该目录.
     */
    private File directory() {

        if (this.directory == null) {
            return (null);
        }
        if (this.directoryFile != null) {
            // NOTE:  竞争是无害的, 所以不需要同步
            return (this.directoryFile);
        }
        File file = new File(this.directory);
        if (!file.isAbsolute()) {
            Container container = manager.getContainer();
            if (container instanceof Context) {
                ServletContext servletContext =
                    ((Context) container).getServletContext();
                File work = (File)
                    servletContext.getAttribute(Globals.WORK_DIR_ATTR);
                file = new File(work, this.directory);
            } else {
                throw new IllegalArgumentException
                    ("Parent Container is not a Context");
            }
        }
        if (!file.exists() || !file.isDirectory()) {
            file.delete();
            file.mkdirs();
        }
        this.directoryFile = file;
        return (file);

    }


    /**
     * 会话持久化文件的路径信息.
     *
     * @param id 要检索的会话的id. 这在文件命名中使用.
     */
    private File file(String id) {
        if (this.directory == null) {
            return (null);
        }
        String filename = id + FILE_EXT;
        File file = new File(directory(), filename);
        return (file);
    }
}
