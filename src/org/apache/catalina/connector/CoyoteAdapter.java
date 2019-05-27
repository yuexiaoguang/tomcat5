package org.apache.catalina.connector;

import java.io.IOException;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.util.StringManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.Cookies;
import org.apache.tomcat.util.http.ServerCookie;


/**
 * 一个请求处理器的实现，它将处理过程委托给一个Coyote处理器
 */
public class CoyoteAdapter implements Adapter {
    private static Log log = LogFactory.getLog(CoyoteAdapter.class);

    // -------------------------------------------------------------- Constants


    public static final int ADAPTER_NOTES = 1;


    // ----------------------------------------------------------- Constructors


    /**
     * @param connector 拥有这个处理器的CoyoteConnector
     */
    public CoyoteAdapter(Connector connector) {
        super();
        this.connector = connector;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 这个处理器关联的CoyoteConnector.
     */
    private Connector connector = null;


    /**
     * 用于标识会话id参数的匹配字符串.
     */
    private static final String match = ";" + Globals.SESSION_PARAMETER_NAME + "=";


    /**
     * 用于标识会话id参数的匹配字符串.
     */
    private static final char[] SESSION_ID = match.toCharArray();


    /**
     * The string manager for this package.
     */
    protected StringManager sm = StringManager.getManager(Constants.Package);


    // -------------------------------------------------------- Adapter Methods


    /**
     * Service method.
     */
    public void service(org.apache.coyote.Request req, 
    	                org.apache.coyote.Response res)
        throws Exception {

        Request request = (Request) req.getNote(ADAPTER_NOTES);
        Response response = (Response) res.getNote(ADAPTER_NOTES);

        if (request == null) {

            // Create objects
            request = (Request) connector.createRequest();
            request.setCoyoteRequest(req);
            response = (Response) connector.createResponse();
            response.setCoyoteResponse(res);

            // Link objects
            request.setResponse(response);
            response.setRequest(request);

            // Set as notes
            req.setNote(ADAPTER_NOTES, request);
            res.setNote(ADAPTER_NOTES, response);

            // 设置查询字符串编码
            req.getParameters().setQueryStringEncoding
                (connector.getURIEncoding());

        }

        if (connector.getXpoweredBy()) {
            response.addHeader("X-Powered-By", "Servlet/2.4");
        }

        try {

            // 解析和设置Catalina和配置特定的请求参数
            if ( postParseRequest(req, request, res, response) ) {
                // Calling the container
                connector.getContainer().getPipeline().getFirst().invoke(request, response);
            }

            response.finishResponse();
            req.action( ActionCode.ACTION_POST_REQUEST , null);

        } catch (IOException e) {
            ;
        } catch (Throwable t) {
            log.error(sm.getString("coyoteAdapter.service"), t);
        } finally {
            // 回收包装的请求和响应
            request.recycle();
            response.recycle();
        }
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 解析其他请求参数.
     */
    protected boolean postParseRequest(org.apache.coyote.Request req, 
                                       Request request,
    		                       org.apache.coyote.Response res, 
                                       Response response)
            throws Exception {

        // XXX 处理器需要在这之前设置一个正确的方案和端口, 在ajp13 协议中从连接器中获取端口没有意义..
        // XXX 处理器在这之前可能设置了正确的方案和端口, 在ajp13 协议中从连接器中获取端口没有意义..
        // 否则, 使用连接器配置
        if (! req.scheme().isNull()) {
            // 使用处理器指定的方案来确定安全状态
            request.setSecure(req.scheme().equals("https"));
        } else {
            // 使用连接器方案和安全配置, (默认 "http" and false respectively)
            req.scheme().setString(connector.getScheme());
            request.setSecure(connector.getSecure());
        }

        // FIXME: 下面的代码不属于这里,  只有在 Http11中才有意义, 不是在 ajp13..
        // 此时，Host header已被处理.
        // 覆盖，如果 proxyPort/proxyHost 被设置
        String proxyName = connector.getProxyName();
        int proxyPort = connector.getProxyPort();
        if (proxyPort != 0) {
            req.setServerPort(proxyPort);
        }
        if (proxyName != null) {
            req.serverName().setString(proxyName);
        }

        // URI decoding
        MessageBytes decodedURI = req.decodedURI();
        decodedURI.duplicate(req.requestURI());

        if (decodedURI.getType() == MessageBytes.T_BYTES) {
            // %xx decoding of the URL
            try {
                req.getURLDecoder().convert(decodedURI, false);
            } catch (IOException ioe) {
                res.setStatus(400);
                res.setMessage("Invalid URI");
                throw ioe;
            }
            // Normalization
            if (!normalize(req.decodedURI())) {
                res.setStatus(400);
                res.setMessage("Invalid URI");
                return false;
            }
            // Character decoding
            convertURI(decodedURI, request);
        } else {
            // URL 是 chars 或 String, 并且已使用内存中的协议处理程序发送, 必须假设URL已经正确解码了
            decodedURI.toChars();
        }

        // 设置远程主体
        String principal = req.getRemoteUser().toString();
        if (principal != null) {
            request.setUserPrincipal(new CoyotePrincipal(principal));
        }

        // 设置授权类型
        String authtype = req.getAuthType().toString();
        if (authtype != null) {
            request.setAuthType(authtype);
        }

        // 解析会话ID
        parseSessionId(req, request);

        // 从URI删除所有剩余参数 (除了会话id, 它已经从parseSessionId()中删除), 所以它们不会被映射算法考虑.
        CharChunk uriCC = decodedURI.getCharChunk();
        int semicolon = uriCC.indexOf(';');
        if (semicolon > 0) {
            decodedURI.setChars
                (uriCC.getBuffer(), uriCC.getStart(), semicolon);
        }

        // Request mapping.
        MessageBytes serverName;
        if(connector.getUseIPVHosts()) {
            serverName = req.localName();
            if(serverName.isNull()) {
                // well, they did ask for it
                res.action(ActionCode.ACTION_REQ_LOCAL_NAME_ATTRIBUTE, null);
            }
        } else {
            serverName = req.serverName();
        }
        connector.getMapper().map(serverName, decodedURI, 
                                  request.getMappingData());
        request.setContext((Context) request.getMappingData().context);
        request.setWrapper((Wrapper) request.getMappingData().wrapper);

        // Filter trace method
        if (!connector.getAllowTrace() 
                && req.method().equalsIgnoreCase("TRACE")) {
            Wrapper wrapper = request.getWrapper();
            String header = null;
            if (wrapper != null) {
                String[] methods = wrapper.getServletMethods();
                if (methods != null) {
                    for (int i=0; i<methods.length; i++) {
                        if ("TRACE".equals(methods[i])) {
                            continue;
                        }
                        if (header == null) {
                            header = methods[i];
                        } else {
                            header += ", " + methods[i];
                        }
                    }
                }
            }                               
            res.setStatus(405);
            res.addHeader("Allow", header);
            res.setMessage("TRACE method is not allowed");
            return false;
        }

        // Possible redirect
        MessageBytes redirectPathMB = request.getMappingData().redirectPath;
        if (!redirectPathMB.isNull()) {
            String redirectPath = redirectPathMB.toString();
            String query = request.getQueryString();
            if (request.isRequestedSessionIdFromURL()) {
                // 这不是最优的, 但这并不是很普遍, 没关系
                redirectPath = redirectPath + ";jsessionid=" 
                    + request.getRequestedSessionId();
            }
            if (query != null) {
                // 这不是最优的, 但这并不是很普遍, 没关系
                redirectPath = redirectPath + "?" + query;
            }
            response.sendRedirect(redirectPath);
            return false;
        }
        // Parse session Id
        parseSessionCookiesId(req, request);

        return true;
    }


    /**
     * 解析URL中的会话id.
     */
    protected void parseSessionId(org.apache.coyote.Request req, Request request) {

        CharChunk uriCC = req.decodedURI().getCharChunk();
        int semicolon = uriCC.indexOf(match, 0, match.length(), 0);

        if (semicolon > 0) {

            // 解析会话ID, 并从已解码的请求URI中提取它
            int start = uriCC.getStart();
            int end = uriCC.getEnd();

            int sessionIdStart = start + semicolon + match.length();
            int semicolon2 = uriCC.indexOf(';', sessionIdStart);
            if (semicolon2 >= 0) {
                request.setRequestedSessionId
                    (new String(uriCC.getBuffer(), sessionIdStart, 
                                semicolon2 - semicolon - match.length()));
            } else {
                request.setRequestedSessionId
                    (new String(uriCC.getBuffer(), sessionIdStart, 
                                end - sessionIdStart));
            }
            request.setRequestedSessionURL(true);

            // 从请求URI中提取会话ID
            ByteChunk uriBC = req.requestURI().getByteChunk();
            start = uriBC.getStart();
            end = uriBC.getEnd();
            semicolon = uriBC.indexOf(match, 0, match.length(), 0);

            if (semicolon > 0) {
                sessionIdStart = start + semicolon;
                semicolon2 = uriCC.indexOf
                    (';', start + semicolon + match.length());
                uriBC.setEnd(start + semicolon);
                byte[] buf = uriBC.getBuffer();
                if (semicolon2 >= 0) {
                    for (int i = 0; i < end - start - semicolon2; i++) {
                        buf[start + semicolon + i] 
                            = buf[start + i + semicolon2];
                    }
                    uriBC.setBytes(buf, start, semicolon 
                                   + (end - start - semicolon2));
                }
            }
        } else {
            request.setRequestedSessionId(null);
            request.setRequestedSessionURL(false);
        }
    }


    /**
     * 在URL中解析会话id.
     */
    protected void parseSessionCookiesId(org.apache.coyote.Request req, Request request) {

        // 从cookie中解析会话ID
        Cookies serverCookies = req.getCookies();
        int count = serverCookies.getCookieCount();
        if (count <= 0)
            return;

        for (int i = 0; i < count; i++) {
            ServerCookie scookie = serverCookies.getCookie(i);
            if (scookie.getName().equals(Globals.SESSION_COOKIE_NAME)) {
                // 覆盖URL中请求的任何内容
                if (!request.isRequestedSessionIdFromCookie()) {
                    // 只接受第一个会话id cookie
                    convertMB(scookie.getValue());
                    request.setRequestedSessionId
                        (scookie.getValue().toString());
                    request.setRequestedSessionCookie(true);
                    request.setRequestedSessionURL(false);
                    if (log.isDebugEnabled())
                        log.debug(" Requested cookie session id is " +
                            request.getRequestedSessionId());
                } else {
                    if (!request.isRequestedSessionIdValid()) {
                        // 替换会话ID直到有效为止
                        convertMB(scookie.getValue());
                        request.setRequestedSessionId(scookie.getValue().toString());
                    }
                }
            }
        }
    }


    /**
     * URI的字符转换.
     */
    protected void convertURI(MessageBytes uri, Request request) 
        throws Exception {

        ByteChunk bc = uri.getByteChunk();
        CharChunk cc = uri.getCharChunk();
        cc.allocate(bc.getLength(), -1);

        String enc = connector.getURIEncoding();
        if (enc != null) {
            B2CConverter conv = request.getURIConverter();
            try {
                if (conv == null) {
                    conv = new B2CConverter(enc);
                    request.setURIConverter(conv);
                } else {
                    conv.recycle();
                }
            } catch (IOException e) {
                // Ignore
                log.error("Invalid URI encoding; using HTTP default");
                connector.setURIEncoding(null);
            }
            if (conv != null) {
                try {
                    conv.convert(bc, cc);
                    uri.setChars(cc.getBuffer(), cc.getStart(), 
                                 cc.getLength());
                    return;
                } catch (IOException e) {
                    log.error("Invalid URI character encoding; trying ascii");
                    cc.recycle();
                }
            }
        }

        // 默认编码: 快速的转换
        byte[] bbuf = bc.getBuffer();
        char[] cbuf = cc.getBuffer();
        int start = bc.getStart();
        for (int i = 0; i < bc.getLength(); i++) {
            cbuf[i] = (char) (bbuf[i + start] & 0xff);
        }
        uri.setChars(cbuf, 0, bc.getLength());
    }


    /**
     * US-ASCII MessageBytes的字符转换.
     */
    protected void convertMB(MessageBytes mb) {

        // 这当然只对字节有意义
        if (mb.getType() != MessageBytes.T_BYTES)
            return;
        
        ByteChunk bc = mb.getByteChunk();
        CharChunk cc = mb.getCharChunk();
        cc.allocate(bc.getLength(), -1);

        // 默认编码: 快速的转换
        byte[] bbuf = bc.getBuffer();
        char[] cbuf = cc.getBuffer();
        int start = bc.getStart();
        for (int i = 0; i < bc.getLength(); i++) {
            cbuf[i] = (char) (bbuf[i + start] & 0xff);
        }
        mb.setChars(cbuf, 0, bc.getLength());
    }


    /**
     * 规范的URI.
     * <p>
     * 该方法对 "\", "//", "/./" and "/../".
     * 当试图超越根路径或者URI包含一个空字节，这个方法将返回 false
     * 
     * @param uriMB 要规范化的URI
     */
    public static boolean normalize(MessageBytes uriMB) {

        ByteChunk uriBC = uriMB.getByteChunk();
        byte[] b = uriBC.getBytes();
        int start = uriBC.getStart();
        int end = uriBC.getEnd();

        // URL * 是可以接受的
        if ((end - start == 1) && b[start] == (byte) '*')
          return true;

        int pos = 0;
        int index = 0;

        // Replace '\' with '/'
        // Check for null byte
        for (pos = start; pos < end; pos++) {
            if (b[pos] == (byte) '\\')
                b[pos] = (byte) '/';
            if (b[pos] == (byte) 0)
                return false;
        }

        // The URL must start with '/'
        if (b[start] != (byte) '/') {
            return false;
        }

        // Replace "//" with "/"
        for (pos = start; pos < (end - 1); pos++) {
            if (b[pos] == (byte) '/') {
                while ((pos + 1 < end) && (b[pos + 1] == (byte) '/')) {
                    copyBytes(b, pos, pos + 1, end - pos - 1);
                    end--;
                }
            }
        }

        // 如果 URI 以 "/." or "/.."结尾, 然后追加一个额外的 "/"
        // Note: 可以将URI扩展为1，没有任何副作用，因为下一个字符是不重要的WS.
        if (((end - start) >= 2) && (b[end - 1] == (byte) '.')) {
            if ((b[end - 2] == (byte) '/') 
                || ((b[end - 2] == (byte) '.') 
                    && (b[end - 3] == (byte) '/'))) {
                b[end] = (byte) '/';
                end++;
            }
        }

        uriBC.setEnd(end);

        index = 0;

        // 解析"/./"
        while (true) {
            index = uriBC.indexOf("/./", 0, 3, index);
            if (index < 0)
                break;
            copyBytes(b, start + index, start + index + 2, 
                      end - start - index - 2);
            end = end - 2;
            uriBC.setEnd(end);
        }

        index = 0;

        // 解析 "/../"
        while (true) {
            index = uriBC.indexOf("/../", 0, 4, index);
            if (index < 0)
                break;
            // 防止脱离上下文
            if (index == 0)
                return false;
            int index2 = -1;
            for (pos = start + index - 1; (pos >= 0) && (index2 < 0); pos --) {
                if (b[pos] == (byte) '/') {
                    index2 = pos;
                }
            }
            copyBytes(b, start + index2, start + index + 3,
                      end - start - index - 3);
            end = end + index2 - index - 3;
            uriBC.setEnd(end);
            index = index2;
        }
        uriBC.setBytes(b, start, end);
        return true;
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 将一个字节数组复制到不同的位置. 在正常使用.
     */
    protected static void copyBytes(byte[] b, int dest, int src, int len) {
        for (int pos = 0; pos < len; pos++) {
            b[pos + dest] = b[pos + src];
        }
    }
}
