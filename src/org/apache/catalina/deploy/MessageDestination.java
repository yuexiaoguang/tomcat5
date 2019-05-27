package org.apache.catalina.deploy;


/**
 * <p>Web应用程序消息目标的表示, 在部署描述符中使用<code>&lt;message-destination&gt;</code>元素表示.</p>
 */
public class MessageDestination {

    // ------------------------------------------------------------- Properties

    /**
     * 描述信息.
     */
    private String description = null;

    public String getDescription() {
        return (this.description);
    }

    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * 此目标的显示名称.
     */
    private String displayName = null;

    public String getDisplayName() {
        return (this.displayName);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }


    /**
     * 大图标.
     */
    private String largeIcon = null;

    public String getLargeIcon() {
        return (this.largeIcon);
    }

    public void setLargeIcon(String largeIcon) {
        this.largeIcon = largeIcon;
    }


    /**
     * 名称.
     */
    private String name = null;

    public String getName() {
        return (this.name);
    }

    public void setName(String name) {
        this.name = name;
    }


    /**
     * 小图标.
     */
    private String smallIcon = null;

    public String getSmallIcon() {
        return (this.smallIcon);
    }

    public void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }


    // --------------------------------------------------------- Public Methods


    public String toString() {
        StringBuffer sb = new StringBuffer("MessageDestination[");
        sb.append("name=");
        sb.append(name);
        if (displayName != null) {
            sb.append(", displayName=");
            sb.append(displayName);
        }
        if (largeIcon != null) {
            sb.append(", largeIcon=");
            sb.append(largeIcon);
        }
        if (smallIcon != null) {
            sb.append(", smallIcon=");
            sb.append(smallIcon);
        }
        if (description != null) {
            sb.append(", description=");
            sb.append(description);
        }
        sb.append("]");
        return (sb.toString());
    }
}
