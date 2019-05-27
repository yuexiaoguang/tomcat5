package org.apache.catalina.ant;


import org.apache.tools.ant.BuildException;


/**
 * Ant任务，实现了<code>/serverinfo</code>命令.
 */
public class ServerinfoTask extends AbstractCatalinaTask {

    /**
     * 执行所请求的操作
     *
     * @exception BuildException if an error occurs
     */
    public void execute() throws BuildException {
        super.execute();
        execute("/serverinfo");
    }
}
