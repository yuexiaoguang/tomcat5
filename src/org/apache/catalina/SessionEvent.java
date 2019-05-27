package org.apache.catalina;

import java.util.EventObject;

/**
 * 一般事件，通知监听器Session中有重大修改
 */
public final class SessionEvent extends EventObject {

    private Object data = null;


    private Session session = null;


    private String type = null;


    /**
     * @param session 发生事件的Session
     * @param type Event type
     * @param data Event data
     */
    public SessionEvent(Session session, String type, Object data) {
        super(session);
        this.session = session;
        this.type = type;
        this.data = data;
    }


    public Object getData() {
        return (this.data);
    }


    public Session getSession() {
        return (this.session);
    }


    public String getType() {
        return (this.type);
    }


    public String toString() {
        return ("SessionEvent['" + getSession() + "','" +
                getType() + "']");
    }
}
