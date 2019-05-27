package org.apache.jasper.runtime;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.JspFragment;
import javax.servlet.jsp.tagext.JspTag;

/**
 * 所有Jsp 分段帮助类继承的帮助类.
 * 这个类允许在单个类中模拟大量分段, 这反过来又降低了类装载器的负荷, 因为在单个页面中有许多潜在的JspFragment.
 * <p>
 * 该类还为JspFragment实现提供了各种实用方法.
 */
public abstract class JspFragmentHelper extends JspFragment {
    
    protected int discriminator;
    protected JspContext jspContext;
    protected PageContext _jspx_page_context;
    protected JspTag parentTag;

    public JspFragmentHelper( int discriminator, JspContext jspContext, 
        JspTag parentTag ) {
        this.discriminator = discriminator;
        this.jspContext = jspContext;
        this._jspx_page_context = null;
        if( jspContext instanceof PageContext ) {
            _jspx_page_context = (PageContext)jspContext;
        }
        this.parentTag = parentTag;
    }
    
    public JspContext getJspContext() {
        return this.jspContext;
    }
    
    public JspTag getParentTag() {
        return this.parentTag;
    }
    
}
