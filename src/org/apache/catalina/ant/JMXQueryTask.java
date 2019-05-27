package org.apache.catalina.ant;

import org.apache.tools.ant.BuildException;

/**
 * Ant任务，实现了JMX Query 命令(<code>/jmxproxy/?qry</code>).
 */
public class JMXQueryTask extends AbstractCatalinaTask {

    /**
     * JMX 查询字符串
     */
    protected String query      = null;
    
    public String getQuery () {
        return this.query;
    }

    /**
     * 设置 JMX 查询字符串.
    * <P>查询格式示例:
     * <UL>
     * <LI>*:*</LI>
     * <LI>*:type=RequestProcessor,*</LI>
     * <LI>*:j2eeType=Servlet,*</LI>
     * <LI>Catalina:type=Environment,resourcetype=Global,name=simpleValue</LI>
     * </UL>
     * </P> 
     * @param query JMX 查询字符串
     */
    public void setQuery (String query) {
        this.query = query;
    }

    /**
     * 执行所请求的操作
     *
     * @exception BuildException 如果发生错误
     */
    public void execute() throws BuildException {
        super.execute();
        String queryString = (query == null) ? "":("?qry="+query);
        log("Query string is " + queryString); 
        execute ("/jmxproxy/" + queryString);
    }
}
