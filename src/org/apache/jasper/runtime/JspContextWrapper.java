package org.apache.jasper.runtime;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import javax.el.ELContext;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.el.ELException;
import javax.servlet.jsp.el.ExpressionEvaluator;
import javax.servlet.jsp.el.VariableResolver;
import javax.servlet.jsp.tagext.BodyContent;
import javax.servlet.jsp.tagext.VariableInfo;

import org.apache.commons.el.VariableResolverImpl;
import org.apache.jasper.compiler.Localizer;

/**
 * JSP Context Wrapper的实现.
 *
 * JSP Context Wrapper是一个创建的 JspContext, 并由标签处理程序实现来维护.
 * 它包装了JSP Context的调用, JspContext 实例传递给标签处理程序通过调用 setJspContext().
 */
public class JspContextWrapper
            extends PageContext implements VariableResolver {

    // 调用JSP上下文
    private PageContext invokingJspCtxt;

    private transient Hashtable	pageAttributes;

    // NESTED 脚本变量
    private ArrayList nestedVars;

    // AT_BEGIN 脚本变量
    private ArrayList atBeginVars;

    // AT_END 脚本变量
    private ArrayList atEndVars;

    private Map aliases;

    private Hashtable originalNestedVars;

    /**
     * 变量解析器, 计算 EL 表达式.
     */
    private VariableResolverImpl variableResolver
        = new VariableResolverImpl(this);

    public JspContextWrapper(JspContext jspContext, ArrayList nestedVars,
			     ArrayList atBeginVars, ArrayList atEndVars,
			     Map aliases) {
        this.invokingJspCtxt = (PageContext) jspContext;
		this.nestedVars = nestedVars;
		this.atBeginVars = atBeginVars;
		this.atEndVars = atEndVars;
		this.pageAttributes = new Hashtable(16);
		this.aliases = aliases;
	
		if (nestedVars != null) {
		    this.originalNestedVars = new Hashtable(nestedVars.size());
		}
		syncBeginTagFile();
    }

    public void initialize(Servlet servlet, ServletRequest request,
                           ServletResponse response, String errorPageURL,
                           boolean needsSession, int bufferSize,
                           boolean autoFlush)
        throws IOException, IllegalStateException, IllegalArgumentException
    {
    }
    
    public Object getAttribute(String name) {

		if (name == null) {
		    throw new NullPointerException(
		            Localizer.getMessage("jsp.error.attribute.null_name"));
		}
	
		return pageAttributes.get(name);
    }

    public Object getAttribute(String name, int scope) {

		if (name == null) {
		    throw new NullPointerException(
		            Localizer.getMessage("jsp.error.attribute.null_name"));
		}
	
		if (scope == PAGE_SCOPE) {
		    return pageAttributes.get(name);
		}
	
		return invokingJspCtxt.getAttribute(name, scope);
    }

    public void setAttribute(String name, Object value) {

		if (name == null) {
		    throw new NullPointerException(
		            Localizer.getMessage("jsp.error.attribute.null_name"));
		}
	
		if (value != null) {
		    pageAttributes.put(name, value);
		} else {
		    removeAttribute(name, PAGE_SCOPE);
		}
    }

    public void setAttribute(String name, Object value, int scope) {

		if (name == null) {
		    throw new NullPointerException(
		            Localizer.getMessage("jsp.error.attribute.null_name"));
		}
	
		if (scope == PAGE_SCOPE) {
		    if (value != null) {
			pageAttributes.put(name, value);
		    } else {
			removeAttribute(name, PAGE_SCOPE);
		    }
		} else {
		    invokingJspCtxt.setAttribute(name, value, scope);
		}
    }

    public Object findAttribute(String name) {

		if (name == null) {
		    throw new NullPointerException(
		            Localizer.getMessage("jsp.error.attribute.null_name"));
		}

        Object o = pageAttributes.get(name);
        if (o == null) {
        	o = invokingJspCtxt.getAttribute(name, REQUEST_SCOPE);
		    if (o == null) {
				if (getSession() != null) {
				    o = invokingJspCtxt.getAttribute(name, SESSION_SCOPE);
				}
				if (o == null) {
				    o = invokingJspCtxt.getAttribute(name, APPLICATION_SCOPE);
				} 
		    }
		}
        return o;
    }

    public void removeAttribute(String name) {

		if (name == null) {
		    throw new NullPointerException(
		            Localizer.getMessage("jsp.error.attribute.null_name"));
		}
	
		pageAttributes.remove(name);
		invokingJspCtxt.removeAttribute(name, REQUEST_SCOPE);
		if (getSession() != null) {
		    invokingJspCtxt.removeAttribute(name, SESSION_SCOPE);
		}
		invokingJspCtxt.removeAttribute(name, APPLICATION_SCOPE);
    }

    public void removeAttribute(String name, int scope) {

		if (name == null) {
		    throw new NullPointerException(
		            Localizer.getMessage("jsp.error.attribute.null_name"));
		}
	
		if (scope == PAGE_SCOPE){
		    pageAttributes.remove(name);
		} else {
		    invokingJspCtxt.removeAttribute(name, scope);
		}
    }

    public int getAttributesScope(String name) {

		if (name == null) {
		    throw new NullPointerException(
		            Localizer.getMessage("jsp.error.attribute.null_name"));
		}
	
		if (pageAttributes.get(name) != null) {
		    return PAGE_SCOPE;
		} else {
		    return invokingJspCtxt.getAttributesScope(name);
		}
    }

    public Enumeration getAttributeNamesInScope(int scope) {
        if (scope == PAGE_SCOPE) {
            return pageAttributes.keys();
		}
	
		return invokingJspCtxt.getAttributeNamesInScope(scope);
    }

    public void release() {
    	invokingJspCtxt.release();
    }

    public JspWriter getOut() {
    	return invokingJspCtxt.getOut();
    }

    public HttpSession getSession() {
    	return invokingJspCtxt.getSession();
    }

    public Object getPage() {
    	return invokingJspCtxt.getPage();
    }

    public ServletRequest getRequest() {
    	return invokingJspCtxt.getRequest();
    }

    public ServletResponse getResponse() {
    	return invokingJspCtxt.getResponse();
    }

    public Exception getException() {
    	return invokingJspCtxt.getException();
    }

    public ServletConfig getServletConfig() {
    	return invokingJspCtxt.getServletConfig();
    }

    public ServletContext getServletContext() {
    	return invokingJspCtxt.getServletContext();
    }

    public void forward(String relativeUrlPath) throws ServletException, IOException {
    	invokingJspCtxt.forward(relativeUrlPath);
    }

    public void include(String relativeUrlPath) throws ServletException, IOException {
    	invokingJspCtxt.include(relativeUrlPath);
    }

    public void include(String relativeUrlPath, boolean flush) 
	    throws ServletException, IOException {
    	include(relativeUrlPath, false); // XXX
    }

    public VariableResolver getVariableResolver() {
    	return this;
    }

    public BodyContent pushBody() {
    	return invokingJspCtxt.pushBody();
    }

    public JspWriter pushBody(Writer writer) {
    	return invokingJspCtxt.pushBody(writer);
    }

    public JspWriter popBody() {
        return invokingJspCtxt.popBody();
    }

    public ExpressionEvaluator getExpressionEvaluator() {
    	return invokingJspCtxt.getExpressionEvaluator();
    }

    public void handlePageException(Exception ex)
        throws IOException, ServletException {
		//永远不能调用, 因为调用handleException()会抛出Throwable, 在生成的servlet中.
		handlePageException((Throwable) ex);
    }

    public void handlePageException(Throwable t)
        throws IOException, ServletException {
    	invokingJspCtxt.handlePageException(t);
    }

    /**
     * VariableResolver 接口
     */
    public Object resolveVariable( String pName ) throws ELException {
        return variableResolver.resolveVariable(pName);
    }

    /**
     * 在标签文件开始处同步变量
     */
    public void syncBeginTagFile() {
    	saveNestedVariables();
    }

    /**
     * 在分段调用之前同步变量
     */
    public void syncBeforeInvoke() {
		copyTagToPageScope(VariableInfo.NESTED);
		copyTagToPageScope(VariableInfo.AT_BEGIN);
    }

    /**
     * 在标签文件结尾同步变量
     */
    public void syncEndTagFile() {
		copyTagToPageScope(VariableInfo.AT_BEGIN);
		copyTagToPageScope(VariableInfo.AT_END);
		restoreNestedVariables();
    }

    /**
     * 将给定范围的变量从这个JSP上下文包装器的虚拟页面范围复制到调用JSP上下文的页面范围.
     *
     * @param scope 变量范围(NESTED, AT_BEGIN, AT_END)
     */
    private void copyTagToPageScope(int scope) {
		Iterator iter = null;
	
		switch (scope) {
			case VariableInfo.NESTED:
			    if (nestedVars != null) {
			    	iter = nestedVars.iterator();
			    }
			    break;
			case VariableInfo.AT_BEGIN:
			    if (atBeginVars != null) {
			    	iter = atBeginVars.iterator();
			    }
			    break;
			case VariableInfo.AT_END:
			    if (atEndVars != null) {
			    	iter = atEndVars.iterator();
			    }
			    break;
		}
	
		while ((iter != null) && iter.hasNext()) {
		    String varName = (String) iter.next();
		    Object obj = getAttribute(varName);
		    varName = findAlias(varName);
		    if (obj != null) {
		    	invokingJspCtxt.setAttribute(varName, obj);
		    } else {
		    	invokingJspCtxt.removeAttribute(varName, PAGE_SCOPE);
		    }
		}
    }

    /**
     * 保存存在于调用的JSP上下文中的 NESTED 变量的值, 因此它们之后可以被恢复.
     */
    private void saveNestedVariables() {
		if (nestedVars != null) {
		    Iterator iter = nestedVars.iterator();
		    while (iter.hasNext()) {
				String varName = (String) iter.next();
				varName = findAlias(varName);
				Object obj = invokingJspCtxt.getAttribute(varName);
				if (obj != null) {
				    originalNestedVars.put(varName, obj);
				}
		    }
		}
    }

    /**
     * 恢复存在于调用的JSP上下文中的 NESTED 变量的值.
     */
    private void restoreNestedVariables() {
		if (nestedVars != null) {
		    Iterator iter = nestedVars.iterator();
		    while (iter.hasNext()) {
				String varName = (String) iter.next();
				varName = findAlias(varName);
				Object obj = originalNestedVars.get(varName);
				if (obj != null) {
				    invokingJspCtxt.setAttribute(varName, obj);
				} else {
				    invokingJspCtxt.removeAttribute(varName, PAGE_SCOPE);
				}
		    }
		}
    }

    /**
     * 检查给定变量名是否用作别名, 如果是的话, 返回用作别名的变量名.
     *
     * @param varName 要检查的变量名
     * @return 返回varName或别名
     */
    private String findAlias(String varName) {

		if (aliases == null)
		    return varName;
	
		String alias = (String) aliases.get(varName);
		if (alias == null) {
		    return varName;
		}
		return alias;
    }
    
    public ELContext getELContext() {return null;};
}

