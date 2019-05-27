package org.apache.catalina.ant;


import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.tools.ant.BuildException;


/**
 * Ant 任务，实现了<code>/sessions</code>命令.
 */
public class SessionsTask extends AbstractCatalinaTask {

    /**
     * 管理的Web应用程序的上下文路径.
     */
    protected String path = null;

    public String getPath() {
        return (this.path);
    }

    public void setPath(String path) {
        this.path = path;
    }

    /**
     * 执行所请求的操作
     *
     * @exception BuildException if an error occurs
     */
    public void execute() throws BuildException {
        super.execute();
        if (path == null) {
            throw new BuildException
                ("Must specify 'path' attribute");
        }
        
        try {
            execute("/sessions?path=" + URLEncoder.encode(this.path, getCharset()));
        } catch (UnsupportedEncodingException e) {
            throw new BuildException
                ("Invalid 'charset' attribute: " + getCharset());
        }
    }
}
