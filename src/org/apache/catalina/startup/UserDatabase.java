package org.apache.catalina.startup;


import java.util.Enumeration;


/**
 * 在当前服务器平台上抽象操作系统定义的用户集合.
 */
public interface UserDatabase {

    // ----------------------------------------------------------- Properties

    /**
     * 返回UserConfig监听器.
     */
    public UserConfig getUserConfig();


    /**
     * 设置UserConfig监听器.
     *
     * @param userConfig The new UserConfig listener
     */
    public void setUserConfig(UserConfig userConfig);


    // ------------------------------------------------------- Public Methods


    /**
     * 返回一个绝对路径名为指定用户的主目录.
     *
     * @param user User for which a home directory should be retrieved
     */
    public String getHome(String user);


    /**
     * 返回此服务器上的用户名枚举.
     */
    public Enumeration getUsers();


}
