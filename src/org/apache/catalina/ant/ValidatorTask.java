package org.apache.catalina.ant;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import org.apache.catalina.startup.Constants;
import org.apache.catalina.startup.DigesterFactory;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tools.ant.BuildException;
import org.xml.sax.InputSource;

/**
 * 验证Web应用程序部署描述符的任务, 使用 XML 框架验证.
 */
public class ValidatorTask extends BaseRedirectorHelperTask {

    // ------------------------------------------------------------- Properties

    /**
     * webapp目录的路径.
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
     * 执行指定命令. 此逻辑只执行所有子类所需的公共属性验证; 它不直接执行任何功能逻辑.
     *
     * @exception BuildException 如果出现验证错误
     */
    public void execute() throws BuildException {

        if (path == null) {
            throw new BuildException("Must specify 'path'");
        }

        File file = new File(path, Constants.ApplicationWebXml);
        if ((!file.exists()) || (!file.canRead())) {
            throw new BuildException("Cannot find web.xml");
        }

        // Commons-logging likes having the context classloader set
        ClassLoader oldCL = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader
            (ValidatorTask.class.getClassLoader());

        Digester digester = DigesterFactory.newDigester(true, true, null);
        try {
            file = file.getCanonicalFile();
            InputStream stream = 
                new BufferedInputStream(new FileInputStream(file));
            InputSource is = new InputSource(file.toURL().toExternalForm());
            is.setByteStream(stream);
            digester.parse(is);
            handleOutput("web.xml validated");
        } catch (Throwable t) {
            if (isFailOnError()) {
                throw new BuildException("Validation failure", t);
            } else {
                handleErrorOutput("Validation failure: " + t);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(oldCL);
            closeRedirector();
        }
    }
}
