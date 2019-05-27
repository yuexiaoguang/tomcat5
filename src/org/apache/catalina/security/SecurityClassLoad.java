package org.apache.catalina.security;

/**
 * 用于预加载Java类，当使用Java SecurityManager的时候.
 * 因此 defineClassInPackage RuntimePermission 不会触发一个 AccessControlException.
 */
public final class SecurityClassLoad {

    public static void securityClassLoad(ClassLoader loader)
        throws Exception {

        if( System.getSecurityManager() == null ){
            return;
        }
        
        loadCorePackage(loader);
        loadLoaderPackage(loader);
        loadSessionPackage(loader);
        loadUtilPackage(loader);
        loadJavaxPackage(loader);
        loadCoyotePackage(loader);        
        loadHttp11Package(loader);        
    }
    
    
    private final static void loadCorePackage(ClassLoader loader)
        throws Exception {
        String basePackage = "org.apache.catalina.";
        loader.loadClass
            (basePackage +
             "core.ApplicationContextFacade$1");
        loader.loadClass
            (basePackage +
             "core.ApplicationDispatcher$PrivilegedForward");
        loader.loadClass
            (basePackage +
             "core.ApplicationDispatcher$PrivilegedInclude");
        loader.loadClass
            (basePackage +
             "core.ContainerBase$PrivilegedAddChild");
        loader.loadClass
            (basePackage +
             "core.StandardWrapper$1");
    }
    
    
    private final static void loadLoaderPackage(ClassLoader loader)
        throws Exception {
        String basePackage = "org.apache.catalina.";
        loader.loadClass
            (basePackage +
             "loader.WebappClassLoader$PrivilegedFindResource");
    }
    
    
    private final static void loadSessionPackage(ClassLoader loader)
        throws Exception {
        String basePackage = "org.apache.catalina.";
        loader.loadClass
            (basePackage + "session.StandardSession");
        loader.loadClass
            (basePackage +
             "session.StandardSession$1");
        loader.loadClass
            (basePackage +
             "session.StandardManager$PrivilegedDoUnload");
    }
    
    
    private final static void loadUtilPackage(ClassLoader loader)
        throws Exception {
        String basePackage = "org.apache.catalina.";
        loader.loadClass
            (basePackage + "util.URL");
        loader.loadClass(basePackage + "util.Enumerator");
    }
    
    
    private final static void loadJavaxPackage(ClassLoader loader)
        throws Exception {
        loader.loadClass("javax.servlet.http.Cookie");
    }
    

    private final static void loadHttp11Package(ClassLoader loader)
        throws Exception {
        String basePackage = "org.apache.coyote.http11.";
        loader.loadClass(basePackage + "Http11Processor$1");
        loader.loadClass(basePackage + "InternalOutputBuffer$1");
        loader.loadClass(basePackage + "InternalOutputBuffer$2");
    }
    
    
    private final static void loadCoyotePackage(ClassLoader loader)
        throws Exception {
        String basePackage = "org.apache.catalina.connector.";
        loader.loadClass
            (basePackage +
             "RequestFacade$GetAttributePrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetParameterMapPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetRequestDispatcherPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetParameterPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetParameterNamesPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetParameterValuePrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetCharacterEncodingPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetHeadersPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetHeaderNamesPrivilegedAction");  
        loader.loadClass
            (basePackage +
             "RequestFacade$GetCookiesPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetLocalePrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetLocalesPrivilegedAction");
        loader.loadClass
            (basePackage +
             "ResponseFacade$SetContentTypePrivilegedAction");
        loader.loadClass
            (basePackage + 
             "ResponseFacade$DateHeaderPrivilegedAction");
        loader.loadClass
            (basePackage +
             "RequestFacade$GetSessionPrivilegedAction");
        loader.loadClass
            (basePackage +
             "ResponseFacade$1");
        loader.loadClass
            (basePackage +
             "OutputBuffer$1");
        loader.loadClass
            (basePackage +
             "CoyoteInputStream$1");
        loader.loadClass
            (basePackage +
             "CoyoteInputStream$2");
        loader.loadClass
            (basePackage +
             "CoyoteInputStream$3");
        loader.loadClass
            (basePackage +
             "CoyoteInputStream$4");
        loader.loadClass
            (basePackage +
             "CoyoteInputStream$5");
        loader.loadClass
            (basePackage +
             "InputBuffer$1");
        loader.loadClass
            (basePackage +
             "Response$1");
        loader.loadClass
            (basePackage +
             "Response$2");
        loader.loadClass
            (basePackage +
             "Response$3");
    }
}
