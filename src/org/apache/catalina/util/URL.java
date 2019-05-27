package org.apache.catalina.util;


import java.io.Serializable;
import java.net.MalformedURLException;


/**
 * <p><strong>URL</strong>用于提供用于解析的公共API, 合成统一资源定位器尽可能与<code>java.net.URL</code>API类似,
 * 但没有打开流或连接的能力.
 * 这样的后果之一是，你可以建立一个URLStreamHandler协议不可用的URL(例如 "https" URL, 当JSSE 没有安装).</p>
 *
 * <p><strong>WARNING</strong> - 这个类假设URL字符串形式符合<code>spec</code>RFC 2396中描述的参数
 * "Uniform Resource Identifiers: Generic Syntax":
 * <pre>
 *   &lt;scheme&gt;//&lt;authority&gt;&lt;path&gt;?&lt;query&gt;#&lt;fragment&gt;
 * </pre></p>
 *
 * <p><strong>FIXME</strong> - 这个类实际上应该放在某个公共包中.</p>
 */
public final class URL implements Serializable {


    // ----------------------------------------------------------- Constructors


    /**
     * @param spec URL字符串形式
     *
     * @exception MalformedURLException 如果无法成功解析字符串
     */
    public URL(String spec) throws MalformedURLException {
        this(null, spec);
    }


    /**
     * 通过解析与指定上下文相关的字符串来创建URL对象.
     * 基于 JDK 1.3.1 <code>java.net.URL</code>的逻辑.
     *
     * @param context 相对被解析的URL
     * @param spec URL字符串表示 (通常相对)
     *
     * @exception MalformedURLException 如果无法成功解析字符串
     */
    public URL(URL context, String spec) throws MalformedURLException {

        String original = spec;
        int i, limit, c;
        int start = 0;
        String newProtocol = null;
        boolean aRef = false;

        try {

            // 消除开头和结尾空格
            limit = spec.length();
            while ((limit > 0) && (spec.charAt(limit - 1) <= ' ')) {
                limit--;
            }
            while ((start < limit) && (spec.charAt(start) <= ' ')) {
                start++;
            }

            // 如果字符串以 "url:"开头, 跳过它
            if (spec.regionMatches(true, start, "url:", 0, 4)) {
                start += 4;
            }

            // 这是与上下文URL相对应的引用吗?
            if ((start < spec.length()) && (spec.charAt(start) == '#')) {
                aRef = true;
            }

            // 解析新协议
            for (i = start; !aRef && (i < limit) &&
                     ((c = spec.charAt(i)) != '/'); i++) {
                if (c == ':') {
                    String s = spec.substring(start, i).toLowerCase();
                    // 假设所有协议都是有效的
                    newProtocol = s;
                    start = i + 1;
                    break;
                }
            }

            // 仅在协议匹配时使用我们的上下文
            protocol = newProtocol;
            if ((context != null) && ((newProtocol == null) ||
                 newProtocol.equalsIgnoreCase(context.getProtocol()))) {
                // 如果上下文是分层URL方案，而且规范包含一个匹配方案, 那么保持向后兼容性，如果规范不包含该方案 ; see 5.2.3 of RFC2396
                if ((context.getPath() != null) &&
                    (context.getPath().startsWith("/")))
                    newProtocol = null;
                if (newProtocol == null) {
                    protocol = context.getProtocol();
                    authority = context.getAuthority();
                    userInfo = context.getUserInfo();
                    host = context.getHost();
                    port = context.getPort();
                    file = context.getFile();
                    int question = file.lastIndexOf("?");
                    if (question < 0)
                        path = file;
                    else
                        path = file.substring(0, question);
                }
            }

            if (protocol == null)
                throw new MalformedURLException("no protocol: " + original);

            // 解析规范的任何引用部分
            i = spec.indexOf('#', start);
            if (i >= 0) {
                ref = spec.substring(i + 1, limit);
                limit = i;
            }

            // 以特定于协议的方式解析规范的其余部分
            parse(spec, start, limit);
            if (context != null)
                normalize();

        } catch (MalformedURLException e) {
            throw e;
        } catch (Exception e) {
            throw new MalformedURLException(e.toString());
        }
    }


    /**
     * 从指定的组件创建URL对象. 将使用指定协议的默认端口号.
     *
     * @param protocol 要使用的协议的名称
     * @param host 此协议所述主机的名称
     * @param file 指定主机上的文件名
     *
     * @exception MalformedURLException 永远不会抛出, 但提供兼容 APIs
     */
    public URL(String protocol, String host, String file)
        throws MalformedURLException {
        this(protocol, host, -1, file);
    }


    /**
     * 从指定的组件创建URL对象. 
     * 指定端口号为-1表示URL应该使用该协议的缺省端口. 基于JDK 1.3.1的<code>java.net.URL</code>逻辑.
     *
     * @param protocol 要使用的协议的名称
     * @param host 此协议所述主机的名称
     * @param port 端口号, 或 -1 使用该协议的缺省端口
     * @param file 指定主机上的文件名
     *
     * @exception MalformedURLException 永远不会抛出, 但提供兼容 APIs
     */
    public URL(String protocol, String host, int port, String file)
        throws MalformedURLException {

        this.protocol = protocol;
        this.host = host;
        this.port = port;

        int hash = file.indexOf('#');
        this.file = hash < 0 ? file : file.substring(0, hash);
        this.ref = hash < 0 ? null : file.substring(hash + 1);
        int question = file.lastIndexOf('?');
        if (question >= 0) {
            query = file.substring(question + 1);
            path = file.substring(0, question);
        } else
            path = file;

        if ((host != null) && (host.length() > 0))
            authority = (port == -1) ? host : host + ":" + port;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * URL的权限部分
     */
    private String authority = null;


    /**
     * URL的文件名部分
     */
    private String file = null;


    /**
     * URL的主机名部分
     */
    private String host = null;


    /**
     * URL的路径部分
     */
    private String path = null;


    /**
     * URL的端口号部分
     */
    private int port = -1;


    /**
     * URL的协议名称部分
     */
    private String protocol = null;


    /**
     * URL的查询部分
     */
    private String query = null;


    /**
     * URL的引用部分.
     */
    private String ref = null;


    /**
     * URL的用户信息部分
     */
    private String userInfo = null;


    // --------------------------------------------------------- Public Methods


    /**
     * 两个<code>URLs</code>相等，如果它们具有相同的协议并引用同一主机, 主机上的端口号相同, 主机上的文件和锚点相同.
     *
     * @param obj URL比较
     */
    public boolean equals(Object obj) {

        if (obj == null)
            return (false);
        if (!(obj instanceof URL))
            return (false);
        URL other = (URL) obj;
        if (!sameFile(other))
            return (false);
        return (compare(ref, other.getRef()));
    }


    /**
     * 返回URL的权限部分.
     */
    public String getAuthority() {
        return (this.authority);
    }


    /**
     * 返回URL的文件名部分.
     * <strong>NOTE</strong> - 为了兼容<code>java.net.URL</code>, 此值包含查询字符串. 只为路径部分, 调用<code>getPath()</code>.
     */
    public String getFile() {
        if (file == null)
            return ("");
        return (this.file);
    }


    /**
     * 返回URL的主机名部分.
     */
    public String getHost() {
        return (this.host);
    }


    /**
     * 返回URL的路径部分
     */
    public String getPath() {
        if (this.path == null)
            return ("");
        return (this.path);
    }


    /**
     * 返回URL的端口号部分.
     */
    public int getPort() {
        return (this.port);
    }


    /**
     * 返回URL的协议名称部分.
     */
    public String getProtocol() {
        return (this.protocol);
    }


    /**
     * 返回URL的查询部分
     */
    public String getQuery() {
        return (this.query);
    }


    /**
     * 返回URL的引用部分
     */
    public String getRef() {
        return (this.ref);
    }


    /**
     * 返回URL的用户信息部分.
     */
    public String getUserInfo() {
        return (this.userInfo);
    }


    /**
     * 规范此URL的<code>path</code> (以及<code>file</code>) 部分.
     * <p>
     * <strong>NOTE</strong> - 此方法不是<code>java.net.URL</code>公共API的一部分, 但作为该实现的增值服务提供.
     *
     * @exception MalformedURLException 如果出现规范错误,例如试图移动层次根
     */
    public void normalize() throws MalformedURLException {
        // 路径为null
        if (path == null) {
            if (query != null)
                file = "?" + query;
            else
                file = "";
            return;
        }

        // 为标准化路径创建一个位置
        String normalized = path;
        if (normalized.equals("/.")) {
            path = "/";
            if (query != null)
                file = path + "?" + query;
            else
                file = path;
            return;
        }

        // 规范的斜线，必要时加上斜杠
        if (normalized.indexOf('\\') >= 0)
            normalized = normalized.replace('\\', '/');
        if (!normalized.startsWith("/"))
            normalized = "/" + normalized;

        // 解析在标准化路径中的 "//"
        while (true) {
            int index = normalized.indexOf("//");
            if (index < 0)
                break;
            normalized = normalized.substring(0, index) +
                normalized.substring(index + 1);
        }

        // 解析在标准化路径中的 "/./"
        while (true) {
            int index = normalized.indexOf("/./");
            if (index < 0)
                break;
            normalized = normalized.substring(0, index) +
                normalized.substring(index + 2);
        }

        // 解析在标准化路径中的 "/../"
        while (true) {
            int index = normalized.indexOf("/../");
            if (index < 0)
                break;
            if (index == 0)
                throw new MalformedURLException
                    ("Invalid relative URL reference");
            int index2 = normalized.lastIndexOf('/', index - 1);
            normalized = normalized.substring(0, index2) +
                normalized.substring(index + 3);
        }

        // 解析在标准化路径结尾的 "/."
        if (normalized.endsWith("/."))
            normalized = normalized.substring(0, normalized.length() - 1);

        // 解析在标准化路径结尾的 "/.."
        if (normalized.endsWith("/..")) {
            int index = normalized.length() - 3;
            int index2 = normalized.lastIndexOf('/', index - 1);
            if (index2 < 0)
                throw new MalformedURLException
                    ("Invalid relative URL reference");
            normalized = normalized.substring(0, index2 + 1);
        }

        // 返回已完成的标准化路径
        path = normalized;
        if (query != null)
            file = path + "?" + query;
        else
            file = path;

    }


    /**
     * 比较两个URLs, 不包括"ref"字段.
     * 返回<code>true</code>, 如果这个<code>URL</code>和<code>other</code> 参数都引用相同的资源.
     * 两个<code>URLs</code>可能不包含相同的锚.
     */
    public boolean sameFile(URL other) {

        if (!compare(protocol, other.getProtocol()))
            return (false);
        if (!compare(host, other.getHost()))
            return (false);
        if (port != other.getPort())
            return (false);
        if (!compare(file, other.getFile()))
            return (false);
        return (true);
    }


    /**
     * 返回此URL的字符串表示形式. 这符合规则RFC 2396, Section 5.2, Step 7.
     */
    public String toExternalForm() {

        StringBuffer sb = new StringBuffer();
        if (protocol != null) {
            sb.append(protocol);
            sb.append(":");
        }
        if (authority != null) {
            sb.append("//");
            sb.append(authority);
        }
        if (path != null)
            sb.append(path);
        if (query != null) {
            sb.append('?');
            sb.append(query);
        }
        if (ref != null) {
            sb.append('#');
            sb.append(ref);
        }
        return (sb.toString());
    }


    public String toString() {
        StringBuffer sb = new StringBuffer("URL[");
        sb.append("authority=");
        sb.append(authority);
        sb.append(", file=");
        sb.append(file);
        sb.append(", host=");
        sb.append(host);
        sb.append(", port=");
        sb.append(port);
        sb.append(", protocol=");
        sb.append(protocol);
        sb.append(", query=");
        sb.append(query);
        sb.append(", ref=");
        sb.append(ref);
        sb.append(", userInfo=");
        sb.append(userInfo);
        sb.append("]");
        return (sb.toString());

        //        return (toExternalForm());
    }


    // -------------------------------------------------------- Private Methods


    /**
     * 比较字符串值是否相等
     *
     * @param first First string
     * @param second Second string
     */
    private boolean compare(String first, String second) {
        if (first == null) {
            if (second == null)
                return (true);
            else
                return (false);
        } else {
            if (second == null)
                return (false);
            else
                return (first.equals(second));
        }
    }


    /**
     * 解析指定部分的URL字符串, 假设它有一个类似于 <code>http</code>.
     *
     * <p><strong>FIXME</strong> - 毫无疑问，该算法可以优化性能. 但是, 这需要等到完成足够的单元测试之后才能保证正确的行为.</p>
     *
     * @param spec 正在解析的字符串
     * @param start 起始偏移, 在':'之后的 (如果有) 排除协议名称
     * @param limit 结束位置, '#'的位置(如果有)限定了锚
     *
     * @exception MalformedURLException 如果发生解析错误
     */
    private void parse(String spec, int start, int limit)
        throws MalformedURLException {

        // 去除字符串前后空格
        int question = spec.lastIndexOf('?', limit - 1);
        if ((question >= 0) && (question < limit)) {
            query = spec.substring(question + 1, limit);
            limit = question;
        } else {
            query = null;
        }

        // 解析权限部分
        if (spec.indexOf("//", start) == start) {
            int pathStart = spec.indexOf("/", start + 2);
            if ((pathStart >= 0) && (pathStart < limit)) {
                authority = spec.substring(start + 2, pathStart);
                start = pathStart;
            } else {
                authority = spec.substring(start + 2, limit);
                start = limit;
            }
            if (authority.length() > 0) {
                int at = authority.indexOf('@');
                if( at >= 0 ) {
                    userInfo = authority.substring(0,at);
                }
                int colon = authority.indexOf(':',at+1);
                if (colon >= 0) {
                    try {
                        port =
                            Integer.parseInt(authority.substring(colon + 1));
                    } catch (NumberFormatException e) {
                        throw new MalformedURLException(e.toString());
                    }
                    host = authority.substring(at+1, colon);
                } else {
                    host = authority.substring(at+1);
                    port = -1;
                }
            }
        }

        // 解析路径部分
        if (spec.indexOf("/", start) == start) {     // Absolute path
            path = spec.substring(start, limit);
            if (query != null)
                file = path + "?" + query;
            else
                file = path;
            return;
        }

        // 根据上下文文件解析相对路径
        if (path == null) {
            if (query != null)
                file = "?" + query;
            else
                file = null;
            return;
        }
        if (!path.startsWith("/"))
            throw new MalformedURLException
                ("Base path does not start with '/'");
        if (!path.endsWith("/"))
            path += "/../";
        path += spec.substring(start, limit);
        if (query != null)
            file = path + "?" + query;
        else
            file = path;
        return;
    }
}
