package org.apache.jasper.compiler;

import org.apache.jasper.JasperException;

/**
 * ErrorHandler接口的默认实现类.
 */
class DefaultErrorHandler implements ErrorHandler {
    
    /**
     * 处理指定的JSP 解析错误.
     *
     * @param fname 发生解析错误的JSP文件的名称
     * @param line 解析错误行号
     * @param column 解析错误列号
     * @param errMsg 解析错误消息
     * @param exception 解析异常
     */
    public void jspError(String fname, int line, int column, String errMsg,
            Exception ex) throws JasperException {
        throw new JasperException(fname + "(" + line + "," + column + ")"
                + " " + errMsg, ex);
    }
    
    /**
     * 处理指定的JSP 解析错误.
     *
     * @param errMsg 解析错误消息
     * @param exception 解析异常
     */
    public void jspError(String errMsg, Exception ex) throws JasperException {
        throw new JasperException(errMsg, ex);
    }
    
    /**
     * 处理指定的 javac 编译错误.
     *
     * @param details JavacErrorDetail实例数组, 对应编译错误
     */
    public void javacError(JavacErrorDetail[] details) throws JasperException {
        
        if (details == null) {
            return;
        }
        
        Object[] args = null;
        StringBuffer buf = new StringBuffer();
        
        for (int i=0; i < details.length; i++) {
            if (details[i].getJspBeginLineNumber() >= 0) {
                args = new Object[] {
                        new Integer(details[i].getJspBeginLineNumber()), 
                        details[i].getJspFileName() };
                buf.append(Localizer.getMessage("jsp.error.single.line.number",
                        args));
                buf.append("\n"); 
            }
            
            buf.append(
                    Localizer.getMessage("jsp.error.corresponding.servlet"));
            buf.append(details[i].getErrorMessage());
            buf.append("\n\n");
        }
        
        throw new JasperException(Localizer.getMessage("jsp.error.unable.compile") + "\n\n" + buf);
    }
    
    /**
     * 处理指定的 javac 错误报告和异常.
     *
     * @param errorReport 编译错误报告
     * @param exception 编译异常
     */
    public void javacError(String errorReport, Exception exception) throws JasperException {
        throw new JasperException(Localizer.getMessage("jsp.error.unable.compile"), exception);
    }
}
