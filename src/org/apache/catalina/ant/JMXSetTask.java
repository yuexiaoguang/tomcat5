package org.apache.catalina.ant;

import org.apache.tools.ant.BuildException;

/**
 * Ant 任务, 实现了 JMX Set 命令 (<code>/jmxproxy/?set</code>)
 */
public class JMXSetTask extends AbstractCatalinaTask {

    /**
     * 完整bean名称
     */
    protected String bean      = null;

    /**
     * 希望更改的属性
     */
    protected String attribute = null;

    /**
     * 属性的新值
     */
    protected String value     = null;
    
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

    public String getValue () {
        return this.value;
    }

    public void setValue (String value) {
        this.value = value;
    }

    /**
     * 执行所请求的操作.
     *
     * @exception BuildException if an error occurs
     */
    public void execute() throws BuildException {
        super.execute();
        if (bean == null || attribute == null || value == null) {
            throw new BuildException
                ("Must specify 'bean', 'attribute' and 'value' attributes");
        }
        log("Setting attribute " + attribute +
                            " in bean " + bean +
                            " to " + value); 
        execute("/jmxproxy/?set=" + bean 
                + "&att=" + attribute 
                + "&val=" + value);
    }
}
