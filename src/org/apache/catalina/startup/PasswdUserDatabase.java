package org.apache.catalina.startup;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Enumeration;


/**
 * 在Unix系统中处理<code>/etc/passwd</code>文件.
 */
public final class PasswdUserDatabase implements UserDatabase {


    // --------------------------------------------------------- Constructors

    public PasswdUserDatabase() {
        super();
    }


    // --------------------------------------------------- Instance Variables


    /**
     * UNIX密码文件的路径.
     */
    private static final String PASSWORD_FILE = "/etc/passwd";


    /**
     * 所有定义用户的主目录集合, 使用用户名作为key.
     */
    private Hashtable homes = new Hashtable();


    /**
     * UserConfig监听器
     */
    private UserConfig userConfig = null;


    // ----------------------------------------------------------- Properties


    /**
     * 返回UserConfig监听器.
     */
    public UserConfig getUserConfig() {
        return (this.userConfig);
    }


    /**
     * 设置UserConfig监听器.
     *
     * @param userConfig The new UserConfig listener
     */
    public void setUserConfig(UserConfig userConfig) {
        this.userConfig = userConfig;
        init();
    }


    // ------------------------------------------------------- Public Methods


    /**
     * 返回一个绝对路径名为指定用户的主目录.
     *
     * @param user 应检索主目录的用户
     */
    public String getHome(String user) {
        return ((String) homes.get(user));
    }


    /**
     * 返回此服务器上的用户名枚举.
     */
    public Enumeration getUsers() {
        return (homes.keys());
    }


    // ------------------------------------------------------ Private Methods


    /**
     * 初始化用户集和主目录.
     */
    private void init() {

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(PASSWORD_FILE));

            while (true) {

                // 累积下一行
                StringBuffer buffer = new StringBuffer();
                while (true) {
                    int ch = reader.read();
                    if ((ch < 0) || (ch == '\n'))
                        break;
                    buffer.append((char) ch);
                }
                String line = buffer.toString();
                if (line.length() < 1)
                    break;

                // 将行解析为组成元素
                int n = 0;
                String tokens[] = new String[7];
                for (int i = 0; i < tokens.length; i++)
                    tokens[i] = null;
                while (n < tokens.length) {
                    String token = null;
                    int colon = line.indexOf(':');
                    if (colon >= 0) {
                        token = line.substring(0, colon);
                        line = line.substring(colon + 1);
                    } else {
                        token = line;
                        line = "";
                    }
                    tokens[n++] = token;
                }

                // 添加这个用户和相应的目录
                if ((tokens[0] != null) && (tokens[5] != null))
                    homes.put(tokens[0], tokens[5]);
            }
            reader.close();
            reader = null;
        } catch (Exception e) {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException f) {
                    ;
                }
                reader = null;
            }
        }
    }
}
