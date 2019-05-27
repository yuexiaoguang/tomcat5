package org.apache.catalina.launcher;

import java.util.ArrayList;
import org.apache.commons.launcher.LaunchCommand;
import org.apache.commons.launcher.LaunchFilter;
import org.apache.tools.ant.BuildException;

/**
 * 这个类实现了LaunchFilter接口. 这个类设计用来无条件地强制某些Catalina应用的"waitforchild"属性为true.
 */
public class CatalinaLaunchFilter implements LaunchFilter {

    //----------------------------------------------------------- Static Fields

    /**
     * Catalina 引导类名.
     */
    private static String CATALINA_BOOTSTRAP_CLASS_NAME = "org.apache.catalina.startup.Bootstrap";

    //----------------------------------------------------------------- Methods

    /**
     * 这种方法允许动态配置和属性的错误检查, 以及Catalina 应用的“启动”任务中的嵌套元素.
     * 此方法评估嵌套命令行参数, 根据任务的"classname"属性指定的类, 可能强制应用程序在前台运行, 通过强制 "waitforchild" 属性为"true".
     *
     * @param launchCommand {@link LaunchCommand}类的配置实例
     * @throws BuildException if any errors occur
     */
    public void filter(LaunchCommand launchCommand) throws BuildException {

        // Get attributes
        String mainClassName = launchCommand.getClassname();
        boolean waitForChild = launchCommand.getWaitforchild();
        ArrayList argsList = launchCommand.getArgs();
        String[] args = (String[])argsList.toArray(new String[argsList.size()]);

        // Evaluate main class
        if (CatalinaLaunchFilter.CATALINA_BOOTSTRAP_CLASS_NAME.equals(mainClassName)) {
            // 如果"start"不是最后一个参数, 设置"waitforchild"为 true
            if (args.length == 0 || !"start".equals(args[args.length - 1])) {
                launchCommand.setWaitforchild(true);
                return;
            }

            // 如果"start"是最后一个参数, 在后台确保前面所有的参数  OK
            for (int i = 0; i < args.length - 1; i++) {
                if ("-config".equals(args[i])) {
                    // 跳过下一个参数，因为它应该是一个文件
                    if (args.length > i + 1) {
                        i++;
                    } else {
                        launchCommand.setWaitforchild(true);
                        return;
                    }
                } else if ("-debug".equals(args[i])) {
                    // Do nothing
                } else if ("-nonaming".equals(args[i])) {
                    // Do nothing
                } else {
                     launchCommand.setWaitforchild(true);
                     return;
                }
            }
        }
    }
}
