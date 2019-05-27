package org.apache.jasper.compiler;

/**
 * 提供有关javac编译错误信息.
 */
public class JavacErrorDetail {

    private String javaFileName;
    private int javaLineNum;
    private String jspFileName;
    private int jspBeginLineNum;
    private StringBuffer errMsg;

    /**
     * @param javaFileName 发生编译错误的Java 文件的名称
     * @param javaLineNum 编译错误行号
     * @param errMsg 编译错误消息
     */
    public JavacErrorDetail(String javaFileName, int javaLineNum, StringBuffer errMsg) {
		this.javaFileName = javaFileName;
		this.javaLineNum = javaLineNum;
		this.errMsg = errMsg;
        this.jspBeginLineNum = -1;
    }

    /**
     * @param javaFileName 发生编译错误的Java 文件的名称
     * @param javaLineNum 编译错误行号
     * @param jspFileName 生成Java源文件的JSP文件的名称
     * @param jspBeginLineNum 负责编译错误的JSP元素的开始行号
     * @param errMsg 编译错误消息
     */
    public JavacErrorDetail(String javaFileName,
			    int javaLineNum,
			    String jspFileName,
			    int jspBeginLineNum,
			    StringBuffer errMsg) {

        this(javaFileName, javaLineNum, errMsg);
		this.jspFileName = jspFileName;
		this.jspBeginLineNum = jspBeginLineNum;
    }

    /**
     * 获取发生编译错误的Java源文件的名称
     */
    public String getJavaFileName() {
    	return this.javaFileName;
    }

    /**
     * 获取编译错误行号.
     */
    public int getJavaLineNumber() {
    	return this.javaLineNum;
    }

    /**
     * 获取生成Java源文件的JSP文件的名称.
     */
    public String getJspFileName() {
    	return this.jspFileName;
    }

    /**
     * 获取负责编译错误的JSP元素的开始行号.
     */
    public int getJspBeginLineNumber() {
    	return this.jspBeginLineNum;
    }

    /**
     * 获取编译错误消息.
     */
    public String getErrorMessage() {
    	return this.errMsg.toString();
    }
}
