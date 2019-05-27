package org.apache.jasper.compiler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Vector;
import java.net.MalformedURLException;

import org.apache.jasper.JasperException;
import org.xml.sax.SAXException;

/**
 * 负责调度JSP解析和javac编译错误到配置错误处理程序.
 *
 * 该类还负责定位任何错误代码, 在将它们传递给已配置的错误处理程序之前.
 * 
 * 在一个java编译错误的情况下, 编译器错误信息解析为JavacErrorDetail 实例数组, 传递到已配置的错误处理程序.
 */
public class ErrorDispatcher {

    // 自定义错误处理程序
    private ErrorHandler errHandler;

    // 指示是否采用JspServlet或JspC编译
    private boolean jspcMode = false;


    /**
     * @param jspcMode true 如果JspC已开始编译, 否则false
     */
    public ErrorDispatcher(boolean jspcMode) {
		// XXX 检查 web.xml 用于自定义错误处理程序
		errHandler = new DefaultErrorHandler();
        this.jspcMode = jspcMode;
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param errCode 错误码
     */
    public void jspError(String errCode) throws JasperException {
    	dispatch(null, errCode, null, null);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param where 错误的位置
     * @param errCode 错误码
     */
    public void jspError(Mark where, String errCode) throws JasperException {
    	dispatch(where, errCode, null, null);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param n 导致错误的节点
     * @param errCode 错误码
     */
    public void jspError(Node n, String errCode) throws JasperException {
    	dispatch(n.getStart(), errCode, null, null);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param errCode 错误码
     * @param arg 参数替换的参数
     */
    public void jspError(String errCode, String arg) throws JasperException {
    	dispatch(null, errCode, new Object[] {arg}, null);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param where 错误的位置
     * @param errCode 错误码
     * @param arg 参数替换的参数
     */
    public void jspError(Mark where, String errCode, String arg)
	        throws JasperException {
    	dispatch(where, errCode, new Object[] {arg}, null);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param n 导致错误的节点
     * @param errCode 错误码
     * @param arg 参数替换的参数
     */
    public void jspError(Node n, String errCode, String arg)
	        throws JasperException {
    	dispatch(n.getStart(), errCode, new Object[] {arg}, null);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param errCode 错误码
     * @param arg1 参数替换的第一个参数
     * @param arg2 参数替换的第二个参数
     */
    public void jspError(String errCode, String arg1, String arg2)
	        throws JasperException {
    	dispatch(null, errCode, new Object[] {arg1, arg2}, null);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param errCode 错误码
     * @param arg1 参数替换的第一个参数
     * @param arg2 参数替换的第二个参数
     * @param arg3 参数替换的第三个参数
     */
    public void jspError(String errCode, String arg1, String arg2, String arg3)
	        throws JasperException {
    	dispatch(null, errCode, new Object[] {arg1, arg2, arg3}, null);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param where 错误的位置
     * @param errCode 错误码
     * @param arg1 参数替换的第一个参数
     * @param arg2 参数替换的第二个参数
     */
    public void jspError(Mark where, String errCode, String arg1, String arg2)
	        throws JasperException {
    	dispatch(where, errCode, new Object[] {arg1, arg2}, null);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param where 错误的位置
     * @param errCode 错误码
     * @param arg1 参数替换的第一个参数
     * @param arg2 参数替换的第二个参数
     * @param arg3 参数替换的第三个参数
     */
    public void jspError(Mark where, String errCode, String arg1, String arg2,
                         String arg3)
                throws JasperException {
        dispatch(where, errCode, new Object[] {arg1, arg2, arg3}, null);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param n 发生错误的节点
     * @param errCode 错误码
     * @param arg1 参数替换的第一个参数
     * @param arg2 参数替换的第二个参数
     */
    public void jspError(Node n, String errCode, String arg1, String arg2)
	        throws JasperException {
    	dispatch(n.getStart(), errCode, new Object[] {arg1, arg2}, null);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param n 发生错误的节点
     * @param errCode 错误码
     * @param arg1 参数替换的第一个参数
     * @param arg2 参数替换的第二个参数
     * @param arg3 参数替换的第三个参数
     */
    public void jspError(Node n, String errCode, String arg1, String arg2,
                         String arg3)
	        throws JasperException {
    	dispatch(n.getStart(), errCode, new Object[] {arg1, arg2, arg3}, null);
    }

    /**
     * 将给定的解析异常分派给已配置的错误处理程序.
     *
     * @param e 解析异常
     */
    public void jspError(Exception e) throws JasperException {
	dispatch(null, null, null, e);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param errCode 错误码
     * @param arg 参数替换的参数
     * @param e 解析异常
     */
    public void jspError(String errCode, String arg, Exception e) throws JasperException {
    	dispatch(null, errCode, new Object[] {arg}, e);
    }

    /**
     * 将给定的JSP解析错误发送到已配置的错误处理程序.
     *
     * 给定的错误代码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param n 发生错误的节点
     * @param errCode 错误码
     * @param arg 参数替换的参数
     * @param e 解析异常
     */
    public void jspError(Node n, String errCode, String arg, Exception e)
	        throws JasperException {
    	dispatch(n.getStart(), errCode, new Object[] {arg}, e);
    }

    /**
     * 解析给定的错误信息到javac编译错误信息数组中(每一个javac编译错误的行数).
     *
     * @param errMsg 错误信息
     * @param fname 编译失败的java源文件的名字
     * @param page java源文件生成的JSP页面的Node 表示形式
     *
     * @return javac编译错误数组, 或null 如果给定的错误消息不包含任何编译错误行号
     */
    public static JavacErrorDetail[] parseJavacErrors(String errMsg,
                                                      String fname,
                                                      Node.Nodes page) throws JasperException, IOException {

    	return parseJavacMessage(errMsg, fname, page);
    }

    /**
     * 将给定的javac编译错误分配到配置的错误处理程序.
     *
     * @param javacErrors javac编译错误数组
     */
    public void javacError(JavacErrorDetail[] javacErrors) throws JasperException {
        errHandler.javacError(javacErrors);
    }


    /**
     * 将给定的编译错误报告和异常分派给已配置的错误处理程序.
     *
     * @param errorReport 编译错误报告
     * @param e 编译异常
     */
    public void javacError(String errorReport, Exception e) throws JasperException {

        errHandler.javacError(errorReport, e);
    }


    //*********************************************************************
    // Private utility methods

    /**
     * 将给定的JSP解析错误分派到已配置的错误处理程序.
     *
     * 给定的错误码是本地化的. 如果在资源包中找不到本地化错误消息, 它用作错误消息.
     *
     * @param where 错误的位置
     * @param errCode 错误码
     * @param args 参数替换的参数
     * @param e 解析的异常
     */
    private void dispatch(Mark where, String errCode, Object[] args,
			  Exception e) throws JasperException {
		String file = null;
		String errMsg = null;
		int line = -1;
		int column = -1;
		boolean hasLocation = false;
	
		// Localize
		if (errCode != null) {
		    errMsg = Localizer.getMessage(errCode, args);
		} else if (e != null) {
		    // 暗示出了什么问题
		    errMsg = e.getMessage();
		}
	
		// 获取错误的位置
		if (where != null) {
	            if (jspcMode) {
	                // 获取引起错误的资源的完整URL
	                try {
	                    file = where.getURL().toString();
	                } catch (MalformedURLException me) {
	                    // 使用上下文相对路径
	                    file = where.getFile();
	                }
	            } else {
	                // 获取上下文相对资源路径, 以便不泄露任何本地文件系统细节
	                file = where.getFile();
	            }
		    line = where.getLineNumber();
		    column = where.getColumnNumber();
		    hasLocation = true;
		}
	
		// 获取嵌套异常
		Exception nestedEx = e;
		if ((e instanceof SAXException)
		        && (((SAXException) e).getException() != null)) {
		    nestedEx = ((SAXException) e).getException();
		}
	
		if (hasLocation) {
		    errHandler.jspError(file, line, column, errMsg, nestedEx);
		} else {
		    errHandler.jspError(errMsg, nestedEx);
		}
    }

    /**
     * 解析java编译错误信息, 其中可能包含一个或多个编译错误, 返回JavacErrorDetail 实例数组.
     *
     * 每个JavacErrorDetail实例包含一个编译错误的信息.
     *
     * @param errMsg javac编译器生成的编译错误信息
     * @param fname 编译失败的java源文件名称
     * @param page java源文件生成的JSP页面的节点
     *
     * @return JavacErrorDetail实例数组, 对应于编译错误
     */
    private static JavacErrorDetail[] parseJavacMessage(String errMsg, String fname, Node.Nodes page)
	        throws IOException, JasperException {

        Vector errVec = new Vector();
        StringBuffer errMsgBuf = null;
        int lineNum = -1;
        JavacErrorDetail javacError = null;
        
        BufferedReader reader = new BufferedReader(new StringReader(errMsg));
        
        /**
         * 解析编译错误. 每个编译错误由一个文件路径和错误行号组成, 其次是描述错误的若干行.
         */
        String line = null;
        while ((line = reader.readLine()) != null) {
            
            /**
             * 错误行号由冒号分隔的集.
             * 在Windows上忽略驱动器后面的冒号 (fromIndex = 2).
             * XXX 处理但是没有行信息
             */
            int beginColon = line.indexOf(':', 2); 
            int endColon = line.indexOf(':', beginColon + 1);
            if ((beginColon >= 0) && (endColon >= 0)) {
                if (javacError != null) {
                    // 将以前的错误添加到错误集合
                    errVec.add(javacError);
                }
                
                String lineNumStr = line.substring(beginColon + 1, endColon);
                try {
                    lineNum = Integer.parseInt(lineNumStr);
                } catch (NumberFormatException e) {
                    // XXX
                }
                
                errMsgBuf = new StringBuffer();
                
                javacError = createJavacError(fname, page, errMsgBuf, lineNum);
            }
            
            // 忽略第一个错误之前的消息
            if (errMsgBuf != null) {
                errMsgBuf.append(line);
                errMsgBuf.append("\n");
            }
        }
        
        // 将最后一个错误添加到错误集合中
        if (javacError != null) {
            errVec.add(javacError);
        } 
        
        reader.close();
        
        JavacErrorDetail[] errDetails = null;
        if (errVec.size() > 0) {
            errDetails = new JavacErrorDetail[errVec.size()];
            errVec.copyInto(errDetails);
        }
        
        return errDetails;
    }


    /**
     * @param fname
     * @param page
     * @param errMsgBuf
     * @param lineNum
     * @return JavacErrorDetail 错误详情
     * @throws JasperException
     */
    public static JavacErrorDetail createJavacError(String fname, Node.Nodes page, 
            StringBuffer errMsgBuf, int lineNum) throws JasperException {
        JavacErrorDetail javacError;
        // 尝试映射javac错误行号到JSP页面行
        ErrorVisitor errVisitor = new ErrorVisitor(lineNum);
        page.visit(errVisitor);
        Node errNode = errVisitor.getJspSourceNode();
        if ((errNode != null) && (errNode.getStart() != null)) {
            javacError = new JavacErrorDetail(
                    fname,
                    lineNum,
                    errNode.getStart().getFile(),
                    errNode.getStart().getLineNumber(),
                    errMsgBuf);
        } else {
            /**
             * javac错误行号不能映射到JSP页面的行数. 例如, 如果一个脚本缺少右括号, 将破坏代码生成器位置的try-catch-finally 块:
             * 结果就是, javac错误行号将超出为脚本生成的java行号的开始和结束范围, 因此不能被映射到在JSP页面中的脚本开始的行数.
             * 错误详情中只包括 javac错误信息.
             */
            javacError = new JavacErrorDetail(fname, lineNum, errMsgBuf);
        }
        return javacError;
    }


    /**
     * 访问者，负责将生成的servlet源代码中的行号映射到相应的JSP节点.
     */
    static class ErrorVisitor extends Node.Visitor {

		// 要映射的java的源代码行号
		private int lineNum;
	
		/**
		 * 在生成的servlet的Java源代码范围包含的Java源行号要映射的JSP 节点
		 */
		Node found;
	
		/*
		 * @param lineNum 生成的servlet代码中的源行号
		 */
		public ErrorVisitor(int lineNum) {
		    this.lineNum = lineNum;
		}
	
		public void doVisit(Node n) throws JasperException {
		    if ((lineNum >= n.getBeginJavaLine())
			    && (lineNum < n.getEndJavaLine())) {
		    	found = n;
		    }
	    }
	
		/**
		 * 获取生成的servlet代码中的源行号映射的 JSP 节点.
		 */
		public Node getJspSourceNode() {
		    return found;
		}
    }
}
