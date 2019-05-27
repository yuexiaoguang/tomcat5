package org.apache.catalina.ant;


import org.apache.tools.ant.BuildException;


/**
 * Ant任务，实现<code>/undeploy</code>命令.
 */
public class UndeployTask extends AbstractCatalinaTask {


    // ------------------------------------------------------------- Properties

    /**
     * 正在管理的Web应用程序的上下文路径
     */
    protected String path = null;

    public String getPath() {
        return (this.path);
    }

    public void setPath(String path) {
        this.path = path;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 执行所请求的操作.
     *
     * @exception BuildException if an error occurs
     */
    public void execute() throws BuildException {
        super.execute();
        if (path == null) {
            throw new BuildException
                ("Must specify 'path' attribute");
        }

        execute("/undeploy?path=" + this.path);
    }
}
