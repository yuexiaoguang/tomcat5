package org.apache.catalina.session;

import org.apache.catalina.Container;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Loader;
import org.apache.catalina.Session;
import org.apache.catalina.Store;
import org.apache.catalina.util.CustomObjectInputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Properties;

/**
 * <code>Store</code>接口实现类，在数据库中存储序列化的会话对象.
 * 保存的会话仍将过期.
 */
public class JDBCStore extends StoreBase implements Store {

    /**
     * 实现类描述信息.
     */
    protected static String info = "JDBCStore/1.0";

    /**
     * 上下文名称
     */
    private String name = null;

    /**
     * 注册名称, 用于记录日志.
     */
    protected static String storeName = "JDBCStore";

    /**
     * 后台线程注册的名称.
     */
    protected String threadName = "JDBCStore";

    /**
     * 连接数据库时使用的连接用户名.
     */
    protected String connectionName = null;


    /**
     * 连接到数据库时要使用的密码.
     */
    protected String connectionPassword = null;

    /**
     * 连接到数据库时要使用的连接URL.
     */
    protected String connectionURL = null;

    /**
     * 数据库连接.
     */
    private Connection dbConnection = null;

    /**
     * JDBC Driver实例.
     */
    protected Driver driver = null;

    /**
     * 使用的驱动类名.
     */
    protected String driverName = null;

    // ------------------------------------------------------------- Table & cols

    /**
     * 使用的表.
     */
    protected String sessionTable = "tomcat$sessions";

    /**
     * /Engine/Host/Context 名称的列
     */
    protected String sessionAppCol = "app";

    /**
     * Id 列.
     */
    protected String sessionIdCol = "id";

    /**
     * Data 列.
     */
    protected String sessionDataCol = "data";

    /**
     * Valid 列
     */
    protected String sessionValidCol = "valid";

    /**
     * 最大闲置数列.
     */
    protected String sessionMaxInactiveCol = "maxinactive";

    /**
     * 最后访问列.
     */
    protected String sessionLastAccessedCol = "lastaccess";

    // ------------------------------------------------------------- SQL Variables

    /**
     * 变量来保存<code>getSize()</code> prepared statement.
     */
    protected PreparedStatement preparedSizeSql = null;

    /**
     * 变量来保存<code>keys()</code> prepared statement.
     */
    protected PreparedStatement preparedKeysSql = null;

    /**
     * 变量来保存<code>save()</code> prepared statement.
     */
    protected PreparedStatement preparedSaveSql = null;

    /**
     * 变量来保存<code>clear()</code> prepared statement.
     */
    protected PreparedStatement preparedClearSql = null;

    /**
     * 变量来保存<code>remove()</code> prepared statement.
     */
    protected PreparedStatement preparedRemoveSql = null;

    /**
     * 变量来保存<code>load()</code> prepared statement.
     */
    protected PreparedStatement preparedLoadSql = null;

    // ------------------------------------------------------------- Properties

    /**
     * 返回描述信息
     */
    public String getInfo() {
        return (info);
    }

    /**
     * 返回此实例的名称(从容器名称创建的)
     */
    public String getName() {
        if (name == null) {
            Container container = manager.getContainer();
            String contextName = container.getName();
            String hostName = "";
            String engineName = "";

            if (container.getParent() != null) {
                Container host = container.getParent();
                hostName = host.getName();
                if (host.getParent() != null) {
                    engineName = host.getParent().getName();
                }
            }
            name = "/" + engineName + "/" + hostName + contextName;
        }
        return name;
    }

    /**
     * 返回线程名称.
     */
    public String getThreadName() {
        return (threadName);
    }

    /**
     * 返回这个Store的名称, 用于记录日志.
     */
    public String getStoreName() {
        return (storeName);
    }

    /**
     * 设置这个Store的驱动.
     *
     * @param driverName The new driver
     */
    public void setDriverName(String driverName) {
        String oldDriverName = this.driverName;
        this.driverName = driverName;
        support.firePropertyChange("driverName",
                oldDriverName,
                this.driverName);
        this.driverName = driverName;
    }

    /**
     * 返回这个Store的驱动类名.
     */
    public String getDriverName() {
        return (this.driverName);
    }

    /**
     * 返回用于连接数据库的用户名.
     */
    public String getConnectionName() {
        return connectionName;
    }

    /**
     * 设置用于连接数据库的用户名.
     *
     * @param connectionName Username
     */
    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    /**
     * 返回用于连接数据库的密码.
     */
    public String getConnectionPassword() {
        return connectionPassword;
    }

    /**
     * 设置用于连接数据库的密码.
     *
     * @param connectionPassword User password
     */
    public void setConnectionPassword(String connectionPassword) {
        this.connectionPassword = connectionPassword;
    }

    /**
     * 设置连接 URL.
     *
     * @param connectionURL The new Connection URL
     */
    public void setConnectionURL(String connectionURL) {
        String oldConnString = this.connectionURL;
        this.connectionURL = connectionURL;
        support.firePropertyChange("connectionURL",
                oldConnString,
                this.connectionURL);
    }

    /**
     * 返回连接 URL.
     */
    public String getConnectionURL() {
        return (this.connectionURL);
    }

    /**
     * 设置表.
     *
     * @param sessionTable The new table
     */
    public void setSessionTable(String sessionTable) {
        String oldSessionTable = this.sessionTable;
        this.sessionTable = sessionTable;
        support.firePropertyChange("sessionTable",
                oldSessionTable,
                this.sessionTable);
    }

    /**
     * 返回表.
     */
    public String getSessionTable() {
        return (this.sessionTable);
    }

    /**
     * 设置表的App列.
     *
     * @param sessionAppCol the column name
     */
    public void setSessionAppCol(String sessionAppCol) {
        String oldSessionAppCol = this.sessionAppCol;
        this.sessionAppCol = sessionAppCol;
        support.firePropertyChange("sessionAppCol",
                oldSessionAppCol,
                this.sessionAppCol);
    }

    /**
     * 返回表的Web应用程序名称列.
     */
    public String getSessionAppCol() {
        return (this.sessionAppCol);
    }

    /**
     * 设置表的 Id 列.
     *
     * @param sessionIdCol the column name
     */
    public void setSessionIdCol(String sessionIdCol) {
        String oldSessionIdCol = this.sessionIdCol;
        this.sessionIdCol = sessionIdCol;
        support.firePropertyChange("sessionIdCol",
                oldSessionIdCol,
                this.sessionIdCol);
    }

    /**
     * 返回表的 Id 列.
     */
    public String getSessionIdCol() {
        return (this.sessionIdCol);
    }

    /**
     * 设置表的 Data 列.
     *
     * @param sessionDataCol the column name
     */
    public void setSessionDataCol(String sessionDataCol) {
        String oldSessionDataCol = this.sessionDataCol;
        this.sessionDataCol = sessionDataCol;
        support.firePropertyChange("sessionDataCol",
                oldSessionDataCol,
                this.sessionDataCol);
    }

    /**
     * 返回表的 Data 列.
     */
    public String getSessionDataCol() {
        return (this.sessionDataCol);
    }

    /**
     * Set the Is Valid column for the table
     *
     * @param sessionValidCol The column name
     */
    public void setSessionValidCol(String sessionValidCol) {
        String oldSessionValidCol = this.sessionValidCol;
        this.sessionValidCol = sessionValidCol;
        support.firePropertyChange("sessionValidCol",
                oldSessionValidCol,
                this.sessionValidCol);
    }

    /**
     * Return the Is Valid column
     */
    public String getSessionValidCol() {
        return (this.sessionValidCol);
    }

    /**
     * Set the Max Inactive column for the table
     *
     * @param sessionMaxInactiveCol The column name
     */
    public void setSessionMaxInactiveCol(String sessionMaxInactiveCol) {
        String oldSessionMaxInactiveCol = this.sessionMaxInactiveCol;
        this.sessionMaxInactiveCol = sessionMaxInactiveCol;
        support.firePropertyChange("sessionMaxInactiveCol",
                oldSessionMaxInactiveCol,
                this.sessionMaxInactiveCol);
    }

    /**
     * Return the Max Inactive column
     */
    public String getSessionMaxInactiveCol() {
        return (this.sessionMaxInactiveCol);
    }

    /**
     * Set the Last Accessed column for the table
     *
     * @param sessionLastAccessedCol The column name
     */
    public void setSessionLastAccessedCol(String sessionLastAccessedCol) {
        String oldSessionLastAccessedCol = this.sessionLastAccessedCol;
        this.sessionLastAccessedCol = sessionLastAccessedCol;
        support.firePropertyChange("sessionLastAccessedCol",
                oldSessionLastAccessedCol,
                this.sessionLastAccessedCol);
    }

    /**
     * Return the Last Accessed column
     */
    public String getSessionLastAccessedCol() {
        return (this.sessionLastAccessedCol);
    }

    // --------------------------------------------------------- Public Methods

    /**
     * 返回当前保存的所有会话的会话标识符.
     * 如果没有, 返回零长度数组.
     *
     * @exception IOException if an input/output error occurred
     */
    public String[] keys() throws IOException {
        ResultSet rst = null;
        String keys[] = null;
        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {

                Connection _conn = getConnection();
                if (_conn == null) {
                    return (new String[0]);
                }
                try {
                    if (preparedKeysSql == null) {
                        String keysSql = "SELECT " + sessionIdCol + " FROM "
                                + sessionTable + " WHERE " + sessionAppCol
                                + " = ?";
                        preparedKeysSql = _conn.prepareStatement(keysSql);
					}

                    preparedKeysSql.setString(1, getName());
                    rst = preparedKeysSql.executeQuery();
                    ArrayList tmpkeys = new ArrayList();
                    if (rst != null) {
                        while (rst.next()) {
                            tmpkeys.add(rst.getString(1));
                        }
                    }
                    keys = (String[]) tmpkeys.toArray(new String[tmpkeys.size()]);
                    // Break out after the finally block
                    numberOfTries = 0;
                } catch (SQLException e) {
                    manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    keys = new String[0];
                    // 关闭连接，以便下次重新打开连接
                    if (dbConnection != null)
                        close(dbConnection);
                } finally {
                    try {
                        if (rst != null) {
                            rst.close();
                        }
                    } catch (SQLException e) {
                        ;
                    }
                    release(_conn);
                }
                numberOfTries--;
            }
        }
        return (keys);
    }

    /**
     * 返回当前保存的会话数量. 
     * 如果没有,返回<code>0</code>.
     *
     * @exception IOException if an input/output error occurred
     */
    public int getSize() throws IOException {
        int size = 0;
        ResultSet rst = null;

        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();

                if (_conn == null) {
                    return (size);
                }

                try {
                    if (preparedSizeSql == null) {
                        String sizeSql = "SELECT COUNT(" + sessionIdCol
                                + ") FROM " + sessionTable + " WHERE "
                                + sessionAppCol + " = ?";
                        preparedSizeSql = _conn.prepareStatement(sizeSql);
					}

                    preparedSizeSql.setString(1, getName());
                    rst = preparedSizeSql.executeQuery();
                    if (rst.next()) {
                        size = rst.getInt(1);
                    }
                    // Break out after the finally block
                    numberOfTries = 0;
                } catch (SQLException e) {
                    manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    if (dbConnection != null)
                        close(dbConnection);
                } finally {
                    try {
                        if (rst != null)
                            rst.close();
                    } catch (SQLException e) {
                        ;
                    }

                    release(_conn);
                }
                numberOfTries--;
            }
        }
        return (size);
    }

    /**
     * 加载指定ID关联的Session.
     * 如果没有，返回 <code>null</code>.
     *
     * @param id a value of type <code>String</code>
     * @return the stored <code>Session</code>
     * @exception ClassNotFoundException if an error occurs
     * @exception IOException if an input/output error occurred
     */
    public Session load(String id)
            throws ClassNotFoundException, IOException {
        ResultSet rst = null;
        StandardSession _session = null;
        Loader loader = null;
        ClassLoader classLoader = null;
        ObjectInputStream ois = null;
        BufferedInputStream bis = null;
        Container container = manager.getContainer();
 
        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();
                if (_conn == null) {
                    return (null);
                }

                try {
                    if (preparedLoadSql == null) {
                        String loadSql = "SELECT " + sessionIdCol + ", "
                                + sessionDataCol + " FROM " + sessionTable
                                + " WHERE " + sessionIdCol + " = ? AND "
                                + sessionAppCol + " = ?";
                        preparedLoadSql = _conn.prepareStatement(loadSql);
                    }

                    preparedLoadSql.setString(1, id);
                    preparedLoadSql.setString(2, getName());
                    rst = preparedLoadSql.executeQuery();
                    if (rst.next()) {
                        bis = new BufferedInputStream(rst.getBinaryStream(2));

                        if (container != null) {
                            loader = container.getLoader();
                        }
                        if (loader != null) {
                            classLoader = loader.getClassLoader();
                        }
                        if (classLoader != null) {
                            ois = new CustomObjectInputStream(bis,
                                    classLoader);
                        } else {
                            ois = new ObjectInputStream(bis);
                        }

                        if (manager.getContainer().getLogger().isDebugEnabled()) {
                            manager.getContainer().getLogger().debug(sm.getString(getStoreName() + ".loading",
                                    id, sessionTable));
                        }

                        _session = (StandardSession) manager.createEmptySession();
                        _session.readObjectData(ois);
                        _session.setManager(manager);
                      } else if (manager.getContainer().getLogger().isDebugEnabled()) {
                        manager.getContainer().getLogger().debug(getStoreName() + ": No persisted data object found");
                    }
                    // Break out after the finally block
                    numberOfTries = 0;
                } catch (SQLException e) {
                    manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    if (dbConnection != null)
                        close(dbConnection);
                } finally {
                    try {
                        if (rst != null) {
                            rst.close();
                        }
                    } catch (SQLException e) {
                        ;
                    }
                    if (ois != null) {
                        try {
                            ois.close();
                        } catch (IOException e) {
                            ;
                        }
                    }
                    release(_conn);
                }
                numberOfTries--;
            }
        }
        return (_session);
    }

    /**
     * 移除指定ID的Session.
     * 如果没有, 什么都不做.
     *
     * @param id Session identifier of the Session to be removed
     *
     * @exception IOException if an input/output error occurs
     */
    public void remove(String id) throws IOException {

        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();

                if (_conn == null) {
                    return;
                }

                try {
                    if (preparedRemoveSql == null) {
                        String removeSql = "DELETE FROM " + sessionTable
                                + " WHERE " + sessionIdCol + " = ?  AND "
                                + sessionAppCol + " = ?";
                        preparedRemoveSql = _conn.prepareStatement(removeSql);
                    }

                    preparedRemoveSql.setString(1, id);
                    preparedRemoveSql.setString(2, getName());
                    preparedRemoveSql.execute();
                    // Break out after the finally block
                    numberOfTries = 0;
                } catch (SQLException e) {
                    manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    if (dbConnection != null)
                        close(dbConnection);
                } finally {
                    release(_conn);
                }
                numberOfTries--;
            }
        }

        if (manager.getContainer().getLogger().isDebugEnabled()) {
            manager.getContainer().getLogger().debug(sm.getString(getStoreName() + ".removing", id, sessionTable));
        }
    }

    /**
     * 删除所有的Sessions.
     *
     * @exception IOException if an input/output error occurs
     */
    public void clear() throws IOException {

        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();
                if (_conn == null) {
                    return;
                }

                try {
                    if (preparedClearSql == null) {
                        String clearSql = "DELETE FROM " + sessionTable
                             + " WHERE " + sessionAppCol + " = ?";
                        preparedClearSql = _conn.prepareStatement(clearSql);
                    }

                    preparedClearSql.setString(1, getName());
                    preparedClearSql.execute();
                    // Break out after the finally block
                    numberOfTries = 0;
                } catch (SQLException e) {
                    manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    if (dbConnection != null)
                        close(dbConnection);
                } finally {
                    release(_conn);
                }
                numberOfTries--;
            }
        }
    }

    /**
     * 保存一个session.
     *
     * @param session the session to be stored
     * @exception IOException if an input/output error occurs
     */
    public void save(Session session) throws IOException {
        ObjectOutputStream oos = null;
        ByteArrayOutputStream bos = null;
        ByteArrayInputStream bis = null;
        InputStream in = null;

        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();
                if (_conn == null) {
                    return;
                }

                // 如果db中已经存在，再次删除和插入
                // TODO:
                // * 检查数据库中是否存在这个ID，如果有，使用更新.
                remove(session.getIdInternal());

                try {
                    bos = new ByteArrayOutputStream();
                    oos = new ObjectOutputStream(new BufferedOutputStream(bos));

                    ((StandardSession) session).writeObjectData(oos);
                    oos.close();
                    oos = null;
                    byte[] obs = bos.toByteArray();
                    int size = obs.length;
                    bis = new ByteArrayInputStream(obs, 0, size);
                    in = new BufferedInputStream(bis, size);

                    if (preparedSaveSql == null) {
                        String saveSql = "INSERT INTO " + sessionTable + " ("
                           + sessionIdCol + ", " + sessionAppCol + ", "
                           + sessionDataCol + ", " + sessionValidCol
                           + ", " + sessionMaxInactiveCol + ", "
                           + sessionLastAccessedCol
                           + ") VALUES (?, ?, ?, ?, ?, ?)";
                       preparedSaveSql = _conn.prepareStatement(saveSql);
					}

                    preparedSaveSql.setString(1, session.getIdInternal());
                    preparedSaveSql.setString(2, getName());
                    preparedSaveSql.setBinaryStream(3, in, size);
                    preparedSaveSql.setString(4, session.isValid() ? "1" : "0");
                    preparedSaveSql.setInt(5, session.getMaxInactiveInterval());
                    preparedSaveSql.setLong(6, session.getLastAccessedTime());
                    preparedSaveSql.execute();
                    // Break out after the finally block
                    numberOfTries = 0;
                } catch (SQLException e) {
                    manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    if (dbConnection != null)
                        close(dbConnection);
                } catch (IOException e) {
                    ;
                } finally {
                    if (oos != null) {
                        oos.close();
                    }
                    if (bis != null) {
                        bis.close();
                    }
                    if (in != null) {
                        in.close();
                    }

                    release(_conn);
                }
                numberOfTries--;
            }
        }

        if (manager.getContainer().getLogger().isDebugEnabled()) {
            manager.getContainer().getLogger().debug(sm.getString(getStoreName() + ".saving",
                    session.getIdInternal(), sessionTable));
        }
    }

    // --------------------------------------------------------- Protected Methods

    /**
     * 检查连接, 如果是<code>null</code>或已经关闭，重新打开它.
     * 返回<code>null</code>，如果无法建立连接.
     *
     * @return <code>Connection</code> if the connection suceeded
     */
    protected Connection getConnection() {
        try {
            if (dbConnection == null || dbConnection.isClosed()) {
                manager.getContainer().getLogger().info(sm.getString(getStoreName() + ".checkConnectionDBClosed"));
                open();
                if (dbConnection == null || dbConnection.isClosed()) {
                    manager.getContainer().getLogger().info(sm.getString(getStoreName() + ".checkConnectionDBReOpenFail"));
                }
            }
        } catch (SQLException ex) {
            manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".checkConnectionSQLException",
                    ex.toString()));
        }

        return dbConnection;
    }

    /**
     * 打开（如果有必要）并返回数据库连接
     *
     * @exception SQLException if a database error occurs
     */
    protected Connection open() throws SQLException {

        // Do nothing if there is a database connection already open
        if (dbConnection != null)
            return (dbConnection);

        // 如有必要，实例化数据库驱动程序
        if (driver == null) {
            try {
                Class clazz = Class.forName(driverName);
                driver = (Driver) clazz.newInstance();
            } catch (ClassNotFoundException ex) {
                manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".checkConnectionClassNotFoundException",
                        ex.toString()));
            } catch (InstantiationException ex) {
                manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".checkConnectionClassNotFoundException",
                        ex.toString()));
            } catch (IllegalAccessException ex) {
                manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".checkConnectionClassNotFoundException",
                        ex.toString()));
            }
        }

        // Open a new connection
        Properties props = new Properties();
        if (connectionName != null)
            props.put("user", connectionName);
        if (connectionPassword != null)
            props.put("password", connectionPassword);
        dbConnection = driver.connect(connectionURL, props);
        dbConnection.setAutoCommit(true);
        return (dbConnection);
    }

    /**
     * 关闭指定的数据库连接.
     *
     * @param dbConnection The connection to be closed
     */
    protected void close(Connection dbConnection) {

        // Do nothing if the database connection is already closed
        if (dbConnection == null)
            return;

        // Close our prepared statements (if any)
        try {
            preparedSizeSql.close();
        } catch (Throwable f) {
            ;
        }
        this.preparedSizeSql = null;

        try {
            preparedKeysSql.close();
        } catch (Throwable f) {
            ;
        }
        this.preparedKeysSql = null;

        try {
            preparedSaveSql.close();
        } catch (Throwable f) {
            ;
        }
        this.preparedSaveSql = null;

        try {
            preparedClearSql.close();
        } catch (Throwable f) {
            ;
        }
         
		try {
            preparedRemoveSql.close();
        } catch (Throwable f) {
            ;
        }
        this.preparedRemoveSql = null;

        try {
            preparedLoadSql.close();
        } catch (Throwable f) {
            ;
        }
        this.preparedLoadSql = null;

        // Close this database connection, and log any errors
        try {
            dbConnection.close();
        } catch (SQLException e) {
            manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".close", e.toString())); // Just log it here
        } finally {
            this.dbConnection = null;
        }

    }

    /**
     * 释放连接, 这里不需要，因为连接与连接池没有关联.
     *
     * @param conn The connection to be released
     */
    protected void release(Connection conn) {
        ;
    }

    /**
     * 这个Store第一次启动的时候，调用一次.
     */
    public void start() throws LifecycleException {
        super.start();

        // Open connection to the database
        this.dbConnection = getConnection();
    }

    /**
     * 终止与数据库相关联的所有内容.
     * 这个Store关闭的时候，调用一次.
     */
    public void stop() throws LifecycleException {
        super.stop();

        // Close and release everything associated with our db.
        if (dbConnection != null) {
            try {
                dbConnection.commit();
            } catch (SQLException e) {
                ;
            }
            close(dbConnection);
        }
    }
}
