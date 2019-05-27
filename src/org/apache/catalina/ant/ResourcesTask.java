package org.apache.catalina.ant;

import org.apache.tools.ant.BuildException;

/**
 * Ant任务，实现<code>/resources</code>命令.
 */
public class ResourcesTask extends AbstractCatalinaTask {

    // ------------------------------------------------------------- Properties

    /**
     * 所请求的资源类型的完全限定类名.
     */
    protected String type = null;

    public String getType() {
        return (this.type);
    }

    public void setType(String type) {
        this.type = type;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 执行所请求的操作
     *
     * @exception BuildException if an error occurs
     */
    public void execute() throws BuildException {
        super.execute();
        if (type != null) {
            execute("/resources?type=" + type);
        } else {
            execute("/resources");
        }
    }
}
