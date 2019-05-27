package org.apache.jasper.runtime;

import java.util.Enumeration;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.Tag;

import org.apache.jasper.Constants;

/**
 * 可以重用的标签处理程序的Thread-local 基础池.
 */
public class PerThreadTagHandlerPool extends TagHandlerPool {

    private int maxSize;

    // For cleanup
    private Vector perThreadDataVector;

    private ThreadLocal perThread;

    private static class PerThreadData {
        Tag handlers[];
        int current;
    }

    public PerThreadTagHandlerPool() {
        super();
        perThreadDataVector = new Vector();
    }

    protected void init(ServletConfig config) {
        maxSize = Constants.MAX_POOL_SIZE;
        String maxSizeS = getOption(config, OPTION_MAXSIZE, null);
        if (maxSizeS != null) {
            maxSize = Integer.parseInt(maxSizeS);
            if (maxSize < 0) {
                maxSize = Constants.MAX_POOL_SIZE;
            }
        }

        perThread = new ThreadLocal() {
            protected Object initialValue() {
                PerThreadData ptd = new PerThreadData();
                ptd.handlers = new Tag[maxSize];
                ptd.current = -1;
                perThreadDataVector.addElement(ptd);
                return ptd;
            }
        };
    }

    /**
     * 从这个标签处理程序池中获取下一个可用的标签处理程序, 实例化一个如果这个标签处理池是空的.
     *
     * @param handlerClass 标签处理程序类
     *
     * @return 重用或新实例化的标记处理程序
     *
     * @throws JspException 如果无法实例化标签处理程序
     */
    public Tag get(Class handlerClass) throws JspException {
        PerThreadData ptd = (PerThreadData)perThread.get();
        if(ptd.current >=0 ) {
            return ptd.handlers[ptd.current--];
        } else {
		    try {
		    	return (Tag) handlerClass.newInstance();
		    } catch (Exception e) {
		    	throw new JspException(e.getMessage(), e);
		    }
        }
    }

    /**
     * 将给定的标签处理程序添加到这个标签处理程序池中, 除非这个标签处理程序池已经达到它的容量, 这种情况下标签处理程序池的release()方法会被调用.
     *
     * @param handler 添加到这个标签处理程序池的标签处理程序
     */
    public void reuse(Tag handler) {
        PerThreadData ptd=(PerThreadData)perThread.get();
        if (ptd.current < (ptd.handlers.length - 1)) {
        	ptd.handlers[++ptd.current] = handler;
        } else {
            handler.release();
        }
    }

    /**
     * 调用标签处理程序池中的所有标签处理程序的release()方法.
     */
    public void release() {        
        Enumeration enumeration = perThreadDataVector.elements();
        while (enumeration.hasMoreElements()) {
	    PerThreadData ptd = (PerThreadData)enumeration.nextElement();
            if (ptd.handlers != null) {
                for (int i=ptd.current; i>=0; i--) {
                    if (ptd.handlers[i] != null) {
                        ptd.handlers[i].release();
                    }
                }
            }
        }
    }
}
