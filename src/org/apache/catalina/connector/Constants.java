package org.apache.catalina.connector;

/**
 * Static constants for this package.
 */
public final class Constants {

	
    // -------------------------------------------------------------- Constants


    public static final String Package = "org.apache.catalina.connector";

    public static final int DEFAULT_CONNECTION_LINGER = -1;
    public static final int DEFAULT_CONNECTION_TIMEOUT = 60000;
    public static final int DEFAULT_CONNECTION_UPLOAD_TIMEOUT = 300000;
    public static final int DEFAULT_SERVER_SOCKET_TIMEOUT = 0;

    public static final int PROCESSOR_IDLE = 0;
    public static final int PROCESSOR_ACTIVE = 1;

    /**
     * Security flag.
     */
    public static final boolean SECURITY = (System.getSecurityManager() != null);

}
