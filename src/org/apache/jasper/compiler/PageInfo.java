package org.apache.jasper.compiler;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import javax.servlet.jsp.tagext.TagLibraryInfo;

/**
 * 编译时有关翻译单元的各种信息的存储库.
 */
class PageInfo {

    private Vector imports;
    private Vector dependants;

    private BeanRepository beanRepository;
    private HashMap taglibsMap;
    private HashMap jspPrefixMapper;
    private HashMap xmlPrefixMapper;
    private HashMap nonCustomTagPrefixMap;
    private String jspFile;
    private String defaultLanguage = "java";
    private String language;
    private String defaultExtends = Constants.JSP_SERVLET_BASE;
    private String xtends;
    private String contentType = null;
    private String session;
    private boolean isSession = true;
    private String bufferValue;
    private int buffer = 8*1024;    // XXX confirm
    private String autoFlush;
    private boolean isAutoFlush = true;
    private String isThreadSafeValue;
    private boolean isThreadSafe = true;
    private String isErrorPageValue;
    private boolean isErrorPage = false;
    private String errorPage = null;
    private String info;

    private boolean scriptless = false;
    private boolean scriptingInvalid = false;
    private String isELIgnoredValue;
    private boolean isELIgnored = false;
    private String omitXmlDecl = null;
    private String doctypeName = null;
    private String doctypePublic = null;
    private String doctypeSystem = null;

    private boolean isJspPrefixHijacked;

    // 在这个翻译单元中使用的所有元素和属性前缀集
    private HashSet prefixes;

    private boolean hasJspRoot = false;
    private Vector includePrelude;
    private Vector includeCoda;
    private Vector pluginDcls;      // Id's for tagplugin declarations


    PageInfo(BeanRepository beanRepository, String jspFile) {

        this.jspFile = jspFile;
        this.beanRepository = beanRepository;
        this.taglibsMap = new HashMap();
        this.jspPrefixMapper = new HashMap();
        this.xmlPrefixMapper = new HashMap();
        this.nonCustomTagPrefixMap = new HashMap();
        this.imports = new Vector();
        this.dependants = new Vector();
        this.includePrelude = new Vector();
        this.includeCoda = new Vector();
        this.pluginDcls = new Vector();
        this.prefixes = new HashSet();

        // Enter standard imports
        for(int i = 0; i < Constants.STANDARD_IMPORTS.length; i++)
            imports.add(Constants.STANDARD_IMPORTS[i]);
    }

    /**
     * 检查插件ID是否已被声明.
     * @return true 如果Id 已经被声明.
     */
    public boolean isPluginDeclared(String id) {
        if (pluginDcls.contains(id))
            return true;
        pluginDcls.add(id);
        return false;
    }

    public void addImports(List imports) {
        this.imports.addAll(imports);
    }

    public void addImport(String imp) {
        this.imports.add(imp);
    }

    public List getImports() {
        return imports;
    }

    public String getJspFile() {
        return jspFile;
    }

    public void addDependant(String d) {
        if (!dependants.contains(d) && !jspFile.equals(d))
                dependants.add(d);
    }

    public List getDependants() {
        return dependants;
    }

    public BeanRepository getBeanRepository() {
        return beanRepository;
    }

    public void setScriptless(boolean s) {
        scriptless = s;
    }

    public boolean isScriptless() {
        return scriptless;
    }

    public void setScriptingInvalid(boolean s) {
        scriptingInvalid = s;
    }

    public boolean isScriptingInvalid() {
        return scriptingInvalid;
    }

    public List getIncludePrelude() {
        return includePrelude;
    }

    public void setIncludePrelude(Vector prelude) {
        includePrelude = prelude;
    }

    public List getIncludeCoda() {
        return includeCoda;
    }

    public void setIncludeCoda(Vector coda) {
        includeCoda = coda;
    }

    public void setHasJspRoot(boolean s) {
        hasJspRoot = s;
    }

    public boolean hasJspRoot() {
        return hasJspRoot;
    }

    public String getOmitXmlDecl() {
        return omitXmlDecl;
    }

    public void setOmitXmlDecl(String omit) {
        omitXmlDecl = omit;
    }

    public String getDoctypeName() {
        return doctypeName;
    }

    public void setDoctypeName(String doctypeName) {
        this.doctypeName = doctypeName;
    }

    public String getDoctypeSystem() {
        return doctypeSystem;
    }

    public void setDoctypeSystem(String doctypeSystem) {
        this.doctypeSystem = doctypeSystem;
    }

    public String getDoctypePublic() {
        return doctypePublic;
    }

    public void setDoctypePublic(String doctypePublic) {
        this.doctypePublic = doctypePublic;
    }

    /* 标签库和xml命名空间管理方法 */
    public void setIsJspPrefixHijacked(boolean isHijacked) {
        isJspPrefixHijacked = isHijacked;
    }

    public boolean isJspPrefixHijacked() {
        return isJspPrefixHijacked;
    }

    /*
     * 将给定的前缀添加到该翻译单元的前缀集中.
     *
     * @param prefix 要添加的前缀
     */
    public void addPrefix(String prefix) {
        prefixes.add(prefix);
    }

    /*
     * 检查该翻译单元是否包含给定前缀.
     *
     * @param prefix 要检查的前缀
     *
     * @return true 如果这个翻译单元包含给定的前缀, 否则false
     */
    public boolean containsPrefix(String prefix) {
        return prefixes.contains(prefix);
    }

    /*
     * 将给定的 URI对应给定的标签库放入Map.
     *
     * @param uri 要映射的URI
     * @param info 对应的标签库
     */
    public void addTaglib(String uri, TagLibraryInfo info) {
        taglibsMap.put(uri, info);
    }

    /*
     * 获取给定的URI对应的标签库.
     *
     * @return 相应的标签库
     */
    public TagLibraryInfo getTaglib(String uri) {
        return (TagLibraryInfo) taglibsMap.get(uri);
    }

    /*
     * 获取所有的标签库
     */
    public Collection getTaglibs() {
        return taglibsMap.values();
    }

    /*
     * 检查是否包含指定的URI.
     *
     * @param uri The URI to map
     *
     * @return true 如果存在, 否则false
     */
    public boolean hasTaglib(String uri) {
        return taglibsMap.containsKey(uri);
    }

    /*
     * 将指定的前缀对应于指定的URI添加到Map中.
     *
     * @param prefix 要映射的前缀
     * @param uri 对应的URI
     */
    public void addPrefixMapping(String prefix, String uri) {
        jspPrefixMapper.put(prefix, uri);
    }

    /*
     * 将指定的URI推送到指定前缀对应的URI栈中.
     *
     * @param prefix 指定的前缀
     * @param uri 要添加的URI
     */
    public void pushPrefixMapping(String prefix, String uri) {
        LinkedList stack = (LinkedList) xmlPrefixMapper.get(prefix);
        if (stack == null) {
            stack = new LinkedList();
            xmlPrefixMapper.put(prefix, stack);
        }
        stack.addFirst(uri);
    }

    /*
     * 删除指定前缀对应的URI栈中第一个URI.
     *
     * @param prefix 要删除URI的前缀
     */
    public void popPrefixMapping(String prefix) {
        LinkedList stack = (LinkedList) xmlPrefixMapper.get(prefix);
        if (stack == null || stack.size() == 0) {
            // XXX throw new Exception("XXX");
        }
        stack.removeFirst();
    }

    /*
     * 返回指定前缀对应的URI.
     *
     * @param prefix 前缀
     *
     * @return 对应的URI
     */
    public String getURI(String prefix) {

        String uri = null;
        LinkedList stack = (LinkedList) xmlPrefixMapper.get(prefix);
        if (stack == null || stack.size() == 0) {
            uri = (String) jspPrefixMapper.get(prefix);
        } else {
            uri = (String) stack.getFirst();
        }
        return uri;
    }


    /* Page/Tag directive attributes */

    /*
     * 语言
     */
    public void setLanguage(String value, Node n, ErrorDispatcher err,
                boolean pagedir)
        throws JasperException {

        if (!"java".equalsIgnoreCase(value)) {
            if (pagedir)
                err.jspError(n, "jsp.error.page.language.nonjava");
            else
                err.jspError(n, "jsp.error.tag.language.nonjava");
        }

        language = value;
    }

    public String getLanguage(boolean useDefault) {
        return (language == null && useDefault ? defaultLanguage : language);
    }

    public String getLanguage() {
        return getLanguage(true);
    }


    /*
     * extends
     */
    public void setExtends(String value, Node.PageDirective n) {

        xtends = value;
        /*
         * 如果页面的父类是顶层的类(即不在包中)直接引用它. 如果没有完成, 编译器会认为扩展类是和生成的servlet在同一个包中.
         */
        if (value.indexOf('.') < 0)
            n.addImport(value);
    }

    /**
     * 获取'extends' 页面指令属性的值.
     *
     * @param useDefault TRUE 如果默认的(org.apache.jasper.runtime.HttpJspBase)应该被返回, 如果这个属性还没有被设置, 或者FALSE
     *
     * @return 'extends' 页面指令属性的值, 或默认的(org.apache.jasper.runtime.HttpJspBase)
     */
    public String getExtends(boolean useDefault) {
        return (xtends == null && useDefault ? defaultExtends : xtends);
    }

    /**
     * 获取'extends' 页面指令属性的值.
     *
     * @return 'extends' 页面指令属性的值, 或默认的(org.apache.jasper.runtime.HttpJspBase)
     */
    public String getExtends() {
        return getExtends(true);
    }


    /*
     * contentType
     */
    public void setContentType(String value) {
        contentType = value;
    }

    public String getContentType() {
        return contentType;
    }


    /*
     * buffer
     */
    public void setBufferValue(String value, Node n, ErrorDispatcher err)
        throws JasperException {

        if ("none".equalsIgnoreCase(value))
            buffer = 0;
        else {
            if (value == null || !value.endsWith("kb"))
                err.jspError(n, "jsp.error.page.invalid.buffer");
            try {
                Integer k = new Integer(value.substring(0, value.length()-2));
                buffer = k.intValue() * 1024;
            } catch (NumberFormatException e) {
                err.jspError(n, "jsp.error.page.invalid.buffer");
            }
        }

        bufferValue = value;
    }

    public String getBufferValue() {
        return bufferValue;
    }

    public int getBuffer() {
        return buffer;
    }


    /*
     * session
     */
    public void setSession(String value, Node n, ErrorDispatcher err)
        throws JasperException {

        if ("true".equalsIgnoreCase(value))
            isSession = true;
        else if ("false".equalsIgnoreCase(value))
            isSession = false;
        else
            err.jspError(n, "jsp.error.page.invalid.session");

        session = value;
    }

    public String getSession() {
        return session;
    }

    public boolean isSession() {
        return isSession;
    }


    /*
     * autoFlush
     */
    public void setAutoFlush(String value, Node n, ErrorDispatcher err)
        throws JasperException {

        if ("true".equalsIgnoreCase(value))
            isAutoFlush = true;
        else if ("false".equalsIgnoreCase(value))
            isAutoFlush = false;
        else
            err.jspError(n, "jsp.error.autoFlush.invalid");

        autoFlush = value;
    }

    public String getAutoFlush() {
        return autoFlush;
    }

    public boolean isAutoFlush() {
        return isAutoFlush;
    }


    /*
     * isThreadSafe
     */
    public void setIsThreadSafe(String value, Node n, ErrorDispatcher err)
        throws JasperException {

        if ("true".equalsIgnoreCase(value))
            isThreadSafe = true;
        else if ("false".equalsIgnoreCase(value))
            isThreadSafe = false;
        else
            err.jspError(n, "jsp.error.page.invalid.isthreadsafe");

        isThreadSafeValue = value;
    }

    public String getIsThreadSafe() {
        return isThreadSafeValue;
    }

    public boolean isThreadSafe() {
        return isThreadSafe;
    }


    /*
     * info
     */
    public void setInfo(String value) {
        info = value;
    }

    public String getInfo() {
        return info;
    }


    /*
     * errorPage
     */
    public void setErrorPage(String value) {
        errorPage = value;
    }

    public String getErrorPage() {
        return errorPage;
    }


    /*
     * isErrorPage
     */
    public void setIsErrorPage(String value, Node n, ErrorDispatcher err)
        throws JasperException {

        if ("true".equalsIgnoreCase(value))
            isErrorPage = true;
        else if ("false".equalsIgnoreCase(value))
            isErrorPage = false;
        else
            err.jspError(n, "jsp.error.page.invalid.iserrorpage");

        isErrorPageValue = value;
    }

    public String getIsErrorPage() {
        return isErrorPageValue;
    }

    public boolean isErrorPage() {
        return isErrorPage;
    }


    /*
     * isELIgnored
     */
    public void setIsELIgnored(String value, Node n, ErrorDispatcher err,
                   boolean pagedir)
        throws JasperException {

        if ("true".equalsIgnoreCase(value))
            isELIgnored = true;
        else if ("false".equalsIgnoreCase(value))
            isELIgnored = false;
        else {
            if (pagedir)
                err.jspError(n, "jsp.error.page.invalid.iselignored");
            else
                err.jspError(n, "jsp.error.tag.invalid.iselignored");
        }

        isELIgnoredValue = value;
    }

    public void setELIgnored(boolean s) {
        isELIgnored = s;
    }

    public String getIsELIgnored() {
        return isELIgnoredValue;
    }

    public boolean isELIgnored() {
        return isELIgnored;
    }

    public void putNonCustomTagPrefix(String prefix, Mark where) {
        nonCustomTagPrefixMap.put(prefix, where);
    }

    public Mark getNonCustomTagPrefix(String prefix) {
        return (Mark) nonCustomTagPrefixMap.get(prefix);
    }
}
