package org.apache.catalina.realm;

import java.security.Principal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.naming.Context;
import javax.sql.DataSource;

import org.apache.naming.ContextBindings;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.ServerFactory;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.util.StringManager;

/**
* <b>Realm</b>实现类，使用JDBC JNDI 数据源.
* 查看 JDBCRealm.howto，知道怎样设置数据源和配置项 .
*/
public class DataSourceRealm extends RealmBase {

    // ----------------------------------------------------- Instance Variables

    /**
     * 为角色PreparedStatement生成的字符串
     */
    private StringBuffer preparedRoles = null;


    /**
     * 为凭据PreparedStatement生成的字符串
     */
    private StringBuffer preparedCredentials = null;


    /**
     * JNDI JDBC数据源名称
     */
    protected String dataSourceName = null;


    /**
     * 描述信息.
     */
    protected static final String info =
        "org.apache.catalina.realm.DataSourceRealm/1.0";


    /**
     * 上下文本地数据源.
     */
    protected boolean localDataSource = false;


    /**
     * 描述信息.
     */
    protected static final String name = "DataSourceRealm";


    /**
     * 命名角色的用户角色表中的列
     */
    protected String roleNameCol = null;


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);


    /**
     * 保存用户凭据的数据表的列
     */
    protected String userCredCol = null;


    /**
     * 保存用户名称的数据表的列
     */
    protected String userNameCol = null;


    /**
     * 保存用户和角色之间关系的表
     */
    protected String userRoleTable = null;


    /**
     * 保存用户数据的表
     */
    protected String userTable = null;


    // ------------------------------------------------------------- Properties


    /**
     * 返回JNDI JDBC数据源名称
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * 设置JNDI JDBC数据源名称
     *
     * @param dataSourceName the name of the JNDI JDBC DataSource
     */
    public void setDataSourceName( String dataSourceName) {
      this.dataSourceName = dataSourceName;
    }

    /**
     * 是否在应用JNDI上下文查找数据源.
     */
    public boolean getLocalDataSource() {
        return localDataSource;
    }

    /**
     * 设置为true, 将在应用JNDI上下文查找数据源.
     *
     * @param localDataSource the new flag value
     */
    public void setLocalDataSource(boolean localDataSource) {
      this.localDataSource = localDataSource;
    }

    /**
     * 返回命名角色的用户角色表中的列.
     */
    public String getRoleNameCol() {
        return roleNameCol;
    }

    /**
     * 设置命名角色的用户角色表中的列.
     *
     * @param roleNameCol The column name
     */
    public void setRoleNameCol( String roleNameCol ) {
        this.roleNameCol = roleNameCol;
    }

    /**
     * 返回保存用户凭据的数据表的列.
     */
    public String getUserCredCol() {
        return userCredCol;
    }

    /**
     * 设置保存用户凭据的数据表的列.
     *
     * @param userCredCol The column name
     */
    public void setUserCredCol( String userCredCol ) {
       this.userCredCol = userCredCol;
    }

    /**
     * 返回保存用户名称的用户表中的列.
     */
    public String getUserNameCol() {
        return userNameCol;
    }

    /**
     * 设置保存用户名称的用户表中的列.
     *
     * @param userNameCol The column name
     */
    public void setUserNameCol( String userNameCol ) {
       this.userNameCol = userNameCol;
    }

    /**
     * 返回保存用户和角色之间关系的表.
     */
    public String getUserRoleTable() {
        return userRoleTable;
    }

    /**
     * 设置保存用户和角色之间关系的表.
     *
     * @param userRoleTable The table name
     */
    public void setUserRoleTable( String userRoleTable ) {
        this.userRoleTable = userRoleTable;
    }

    /**
     * 返回保存用户数据的表.
     */
    public String getUserTable() {
        return userTable;
    }

    /**
     * 设置保存用户数据的表.
     *
     * @param userTable The table name
     */
    public void setUserTable( String userTable ) {
      this.userTable = userTable;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 返回指定用户名和凭据的Principal; 或者<code>null</code>.
     *
     * 如果JDBC连接有任何错误, 执行查询或返回null的任何操作 (不验证).
     * 此事件也被记录, 连接将被关闭，以便随后的请求将自动重新打开它.
     *
     * @param username 要查找的Principal的用户名
     * @param credentials Password 或其它凭据
     */
    public Principal authenticate(String username, String credentials) {
    	
    	// No user - 不可能认证, 不要麻烦数据库了
    	if (username == null) {
    		return null;
    	}
        
    	Connection dbConnection = null;
        try {
            // 确保有一个开放的数据库连接
            dbConnection = open();
            if (dbConnection == null) {
                // 如果db连接打开失败, 返回"not authenticated"
                return null;
            }
            // 获取此用户的Principal对象
            return authenticate(dbConnection, username, credentials);
        } catch (SQLException e) {
            // 记录问题
            containerLog.error(sm.getString("dataSourceRealm.exception"), e);

            // Return "not authenticated" for this request
            return (null);
        } finally {
        	close(dbConnection);
        }
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * 返回指定用户名和凭据关联的Principal; 或者<code>null</code>.
     *
     * @param dbConnection 要使用的数据库连接
     * @param username 要查找的Principal的用户名
     * @param credentials Password或其它的凭据
     */
    protected Principal authenticate(Connection dbConnection,
                                               String username,
                                               String credentials) throws SQLException{

        String dbCredentials = getPassword(dbConnection, username);

        // Validate the user's credentials
        boolean validated = false;
        if (hasMessageDigest()) {
            // Hex hashes 不区分大小写
            validated = (digest(credentials).equalsIgnoreCase(dbCredentials));
        } else
            validated = (digest(credentials).equals(dbCredentials));

        if (validated) {
            if (containerLog.isTraceEnabled())
                containerLog.trace(
                    sm.getString("dataSourceRealm.authenticateSuccess",
                                 username));
        } else {
            if (containerLog.isTraceEnabled())
                containerLog.trace(
                    sm.getString("dataSourceRealm.authenticateFailure",
                                 username));
            return (null);
        }

        ArrayList list = getRoles(dbConnection, username);

        // 为这个用户创建并返回一个合适的Principal
        return (new GenericPrincipal(this, username, credentials, list));
    }


    /**
     * 关闭指定的数据库连接.
     *
     * @param dbConnection 要关闭的连接
     */
    protected void close(Connection dbConnection) {

        // 如果数据库连接已经关闭，请不要做任何操作
        if (dbConnection == null)
            return;

        // 如果没有自动提交
        try {
            if (!dbConnection.getAutoCommit()) {
                dbConnection.commit();
            }            
        } catch (SQLException e) {
            containerLog.error("Exception committing connection before closing:", e);
        }

        // 关闭此数据库连接，并记录任何错误
        try {
            dbConnection.close();
        } catch (SQLException e) {
            containerLog.error(sm.getString("dataSourceRealm.close"), e); // Just log it here
        }
    }

    /**
     * 打开指定的数据库连接
     *
     * @return Connection to the database
     */
    protected Connection open() {

        try {
            Context context = null;
            if (localDataSource) {
                context = ContextBindings.getClassLoader();
                context = (Context) context.lookup("comp/env");
            } else {
                StandardServer server = 
                    (StandardServer) ServerFactory.getServer();
                context = server.getGlobalNamingContext();
            }
            DataSource dataSource = (DataSource)context.lookup(dataSourceName);
	    return dataSource.getConnection();
        } catch (Exception e) {
            // Log the problem for posterity
            containerLog.error(sm.getString("dataSourceRealm.exception"), e);
        }  
        return null;
    }

    /**
     * 返回这个Realm实现类的短名称.
     */
    protected String getName() {
        return (name);
    }

    /**
     * 返回指定用户名的密码.
     */
    protected String getPassword(String username) {
        Connection dbConnection = null;

        // 确保有一个打开的数据库连接
        dbConnection = open();
        if (dbConnection == null) {
            return null;
        }

        try {
        	return getPassword(dbConnection, username);        	
        } finally {
            close(dbConnection);
        }
    }
    
    /**
     * 返回与给定主体的用户名相关联的密码.
     * 
     * @param dbConnection 要使用的数据库连接
     * @param username 应检索密码的用户名
     */
    protected String getPassword(Connection dbConnection, 
								 String username) {
        ResultSet rs = null;
        PreparedStatement stmt = null;
        String dbCredentials = null;

        try {
            stmt = credentials(dbConnection, username);
            rs = stmt.executeQuery();
            if (rs.next()) {
                dbCredentials = rs.getString(1);
            }

            return (dbCredentials != null) ? dbCredentials.trim() : null;
            
        } catch(SQLException e) {
            containerLog.error(sm.getString("dataSourceRealm.getPassword.exception", username));
        } finally {
        	try {
	            if (rs != null) {
	                rs.close();
	            }
	            if (stmt != null) {
	                stmt.close();
	            }
        	} catch (SQLException e) {
                    containerLog.error(sm.getString("dataSourceRealm.getPassword.exception", username));
        	}
        }
        return null;
    }


    /**
     * 返回与给定用户名相关联的Principal.
     */
    protected Principal getPrincipal(String username) {
    	Connection dbConnection = open();
        if (dbConnection == null) {
            return new GenericPrincipal(this,username, null, null);
        }
        try {
        	return (new GenericPrincipal(this,
        			username,
					getPassword(dbConnection, username),
					getRoles(dbConnection, username)));
        } finally {
        	close(dbConnection);
        }
    }

    /**
     * 返回与给定用户名相关联的角色
     * 
     * @param username 应检索哪个角色的用户名
     */
    protected ArrayList getRoles(String username) {

        Connection dbConnection = null;

        // 确保有一个开放的数据库连接
        dbConnection = open();
        if (dbConnection == null) {
            return null;
        }

        try {
            return getRoles(dbConnection, username);
        } finally {
        	close(dbConnection);
        }
    }
    
    /**
     * 返回与给定用户名相关联的角色
     * 
     * @param dbConnection 要使用的数据库连接
     * @param username 应检索哪个角色的用户名
     */
    protected ArrayList getRoles(Connection dbConnection,
                                     String username) {
    	
        ResultSet rs = null;
        PreparedStatement stmt = null;
        ArrayList list = null;
    	
        try {
    		stmt = roles(dbConnection, username);
    		rs = stmt.executeQuery();
    		list = new ArrayList();
    		
    		while (rs.next()) {
    			String role = rs.getString(1);
    			if (role != null) {
    				list.add(role.trim());
    			}
    		}
    		return list;
    	} catch(SQLException e) {
            containerLog.error(
                sm.getString("dataSourceRealm.getRoles.exception", username));
        }
    	finally {
        	try {
	            if (rs != null) {
	                rs.close();
	            }
	            if (stmt != null) {
	                stmt.close();
	            }
        	} catch (SQLException e) {
                    containerLog.error(
                        sm.getString("dataSourceRealm.getRoles.exception",
                                     username));
        	}
        }
    	return null;
    }

    /**
     * 返回一个PreparedStatement配置进行SELECT检索指定的用户名的用户凭据.
     *
     * @param dbConnection 要使用的数据库连接
     * @param username 应检索凭据的用户名
     *
     * @exception SQLException if a database error occurs
     */
    private PreparedStatement credentials(Connection dbConnection,
                                            String username)
        throws SQLException {

        PreparedStatement credentials =
            dbConnection.prepareStatement(preparedCredentials.toString());

        credentials.setString(1, username);
        return (credentials);
    }
    
    /**
     * 返回一个PreparedStatement配置进行SELECT检索指定的用户名的用户角色.
     *
     * @param dbConnection 要使用的数据库连接
     * @param username 应检索凭据的用户名
     *
     * @exception SQLException if a database error occurs
     */
    private PreparedStatement roles(Connection dbConnection, String username)
        throws SQLException {

        PreparedStatement roles = 
            dbConnection.prepareStatement(preparedRoles.toString());

        roles.setString(1, username);
        return (roles);
    }

    // ------------------------------------------------------ Lifecycle Methods


    /**
     *
     * @exception LifecycleException 如果此组件检测到阻止其启动的致命错误
     */
    public void start() throws LifecycleException {

        // Create the roles PreparedStatement string
        preparedRoles = new StringBuffer("SELECT ");
        preparedRoles.append(roleNameCol);
        preparedRoles.append(" FROM ");
        preparedRoles.append(userRoleTable);
        preparedRoles.append(" WHERE ");
        preparedRoles.append(userNameCol);
        preparedRoles.append(" = ?");

        // Create the credentials PreparedStatement string
        preparedCredentials = new StringBuffer("SELECT ");
        preparedCredentials.append(userCredCol);
        preparedCredentials.append(" FROM ");
        preparedCredentials.append(userTable);
        preparedCredentials.append(" WHERE ");
        preparedCredentials.append(userNameCol);
        preparedCredentials.append(" = ?");

        // 执行正常的父类初始化
        super.start();
    }


    /**
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    public void stop() throws LifecycleException {
        // 执行正常的超类的终结
        super.stop();
    }
}
