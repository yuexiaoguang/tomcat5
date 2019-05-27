package org.apache.catalina.realm;


import java.io.IOException;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <p>JAAS <strong>CallbackHandler</code>接口的实现类,
 * 用于协商指定给构造器的用户名和凭证的传递. 不需要与用户交互(或可能).</p>
 *
 * <p><code>CallbackHandler</code>将预加密提供的密码, 如果<code>server.xml</code>中的<code>&lt;Realm&gt;</code>节点需要的话.</p>
 * <p>目前, <code>JAASCallbackHandler</code>知道怎样处理<code>javax.security.auth.callback.NameCallback</code>和
 * <code>javax.security.auth.callback.PasswordCallback</code>类型的回调.</p>
 */
public class JAASCallbackHandler implements CallbackHandler {
    private static Log log = LogFactory.getLog(JAASCallbackHandler.class);

    // ------------------------------------------------------------ Constructor


    /**
     * @param realm 关联的JAASRealm实例
     * @param username 要验证的Username
     * @param password 要验证的Password
     */
    public JAASCallbackHandler(JAASRealm realm, String username,
                               String password) {

        super();
        this.realm = realm;
        this.username = username;

        if (realm.hasMessageDigest()) {
            this.password = realm.digest(password);
        }
        else {
            this.password = password;
        }
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * 要验证的Password
     */
    protected String password = null;


    /**
     * 关联的JAASRealm实例
     */
    protected JAASRealm realm = null;


    /**
     * 要验证的Username
     */
    protected String username = null;


    // --------------------------------------------------------- Public Methods


    /**
     * 检索提供的Callbacks请求的信息. 
     * 这个实现类仅识别 <code>NameCallback</code>和<code>PasswordCallback</code>实例.
     *
     * @param callbacks 要处理的一组回调
     *
     * @exception IOException if an input/output error occurs
     * @exception UnsupportedCallbackException 如果登录方法请求不支持的回调类型
     */
    public void handle(Callback callbacks[])
        throws IOException, UnsupportedCallbackException {

        for (int i = 0; i < callbacks.length; i++) {

            if (callbacks[i] instanceof NameCallback) {
                if (realm.getContainer().getLogger().isTraceEnabled())
                    realm.getContainer().getLogger().trace(sm.getString("jaasCallback.username", username));
                ((NameCallback) callbacks[i]).setName(username);
            } else if (callbacks[i] instanceof PasswordCallback) {
                final char[] passwordcontents;
                if (password != null) {
                    passwordcontents = password.toCharArray();
                } else {
                    passwordcontents = new char[0];
                }
                ((PasswordCallback) callbacks[i]).setPassword
                    (passwordcontents);
            } else {
                throw new UnsupportedCallbackException(callbacks[i]);
            }
        }
    }
}
