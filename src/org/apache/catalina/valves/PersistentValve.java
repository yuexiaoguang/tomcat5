package org.apache.catalina.valves;


import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Manager;
import org.apache.catalina.Session;
import org.apache.catalina.Store;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.session.PersistentManager;
import org.apache.catalina.util.StringManager;


/**
 * 用于<code>StandardHost</code>容器实现类的实现了默认基本行为的Valve.
 * <p>
 * <b>USAGE CONSTRAINT</b>: 正常工作需要一个 PersistentManager.
 */
public class PersistentValve extends ValveBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * 实现类描述信息.
     */
    private static final String info =
        "org.apache.catalina.valves.PersistentValve/1.0";


    /**
     * The string manager for this package.
     */
    private static final StringManager sm = StringManager.getManager(Constants.Package);


    // ------------------------------------------------------------- Properties


    /**
     * 返回实现类描述信息.
     */
    public String getInfo() {
        return (info);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 选择适当的子上下文来处理此请求, 根据指定的请求 URI. 如果找不到匹配的 Context, 返回适当的 HTTP 错误.
     *
     * @param request Request to be processed
     * @param response Response to be produced
     *
     * @exception IOException if an input/output error occurred
     * @exception ServletException if a servlet error occurred
     */
    public void invoke(Request request, Response response)
        throws IOException, ServletException {

        // 选择要用于此请求的上下文
        StandardHost host = (StandardHost) getContainer();
        Context context = request.getContext();
        if (context == null) {
            response.sendError
                (HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                 sm.getString("standardHost.noContext"));
            return;
        }

        // 绑定上下文 CL 到当前线程
        Thread.currentThread().setContextClassLoader
            (context.getLoader().getClassLoader());

        // 更新会话最后访问时间
        String sessionId = request.getRequestedSessionId();
        Manager manager = context.getManager();
        if (sessionId != null && manager != null) {
            if (manager instanceof PersistentManager) {
                Store store = ((PersistentManager) manager).getStore();
                if (store != null) {
                    Session session = null;
                    try {
                        session = store.load(sessionId);
                    } catch (Exception e) {
                        container.getLogger().error("deserializeError");
                    }
                    if (session != null) {
                        if (!session.isValid() ||
                            isSessionStale(session, System.currentTimeMillis())) {
                            if (container.getLogger().isDebugEnabled())
                                container.getLogger().debug("session swapped in is invalid or expired");
                            session.expire();
                            store.remove(sessionId);
                        } else {
                            session.setManager(manager);
                            // session.setId(sessionId); Only if new ???
                            manager.add(session);
                            // ((StandardSession)session).activate();
                            session.access();
                        }
                    }
                }
            }
        }
        if (container.getLogger().isDebugEnabled())
            container.getLogger().debug("sessionId: " + sessionId);

        // Ask the next valve to process the request.
        getNext().invoke(request, response);

        // Read the sessionid after the response.
        // HttpSession hsess = hreq.getSession(false);
        Session hsess;
        try {
            hsess = request.getSessionInternal();
        } catch (Exception ex) {
            hsess = null;
        }
        String newsessionId = null;
        if (hsess!=null)
            newsessionId = hsess.getIdInternal();

        if (container.getLogger().isDebugEnabled())
            container.getLogger().debug("newsessionId: " + newsessionId);
        if (newsessionId!=null) {
            /* 保存会话并从manager中移除 */
            if (manager instanceof PersistentManager) {
                Session session = manager.findSession(newsessionId);
                Store store = ((PersistentManager) manager).getStore();
                if (store != null && session!=null &&
                    session.isValid() &&
                    !isSessionStale(session, System.currentTimeMillis())) {
                    // ((StandardSession)session).passivate();
                    store.save(session);
                    ((PersistentManager) manager).removeSuper(session);
                    session.recycle();
                } else {
                    if (container.getLogger().isDebugEnabled())
                        container.getLogger().debug("newsessionId store: " + store + " session: " +
                                session + " valid: " + session.isValid() +
                                " Staled: " +
                                isSessionStale(session, System.currentTimeMillis()));

                }
            } else {
                if (container.getLogger().isDebugEnabled())
                    container.getLogger().debug("newsessionId Manager: " + manager);
            }
        }
    }

    /**
     * 指示会话空闲时间是否超过其到期日期，与所提供的时间无关.
     *
     * FIXME: Probably belongs in the Session class.
     */
    protected boolean isSessionStale(Session session, long timeNow) {
 
        int maxInactiveInterval = session.getMaxInactiveInterval();
        if (maxInactiveInterval >= 0) {
            int timeIdle = // Truncate, do not round up
                (int) ((timeNow - session.getLastAccessedTime()) / 1000L);
            if (timeIdle >= maxInactiveInterval)
                return true;
        }
        return false;
    }
}
