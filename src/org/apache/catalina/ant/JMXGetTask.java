package org.apache.catalina.ant;

import org.apache.tools.ant.BuildException;

/**
 * Ant任务，实现 JMX Get 命令(<code>/jmxproxy/?get</code>).
 * 由Tomcat 管理器应用支持.
 */
public class JMXGetTask extends AbstractCatalinaTask {

    /**
     * 完整bean名称
     */
    protected String bean      = null;

    /**
     * 希望更改的属性
     */
    protected String attribute = null;

    public String getBean () {
        return this.bean;
    }

    public void setBean (String bean) {
        this.bean = bean;
    }

    public String getAttribute () {
        return this.attribute;
    }

    public void setAttribute (String attribute) {
        this.attribute = attribute;
    }

    /**
     * 执行所请求的操作
     *
     * @exception BuildException 如果错误发生
     */
    public void execute() throws BuildException {
        super.execute();
        if (bean == null || attribute == null) {
            throw new BuildException
                ("Must specify 'bean' and 'attribute' attributes");
        }
        log("Getting attribute " + attribute +
                " in bean " + bean ); 
        execute("/jmxproxy/?get=" + bean 
                + "&att=" + attribute );
    }
}
