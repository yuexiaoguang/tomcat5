package org.apache.jasper.security;

/**
 * 静态类用于预加载java类, 当使用Java SecurityManager的时候, 因此defineClassInPackage
 * RuntimePermission 不会触发一个 AccessControlException.
 */
public final class SecurityClassLoad {

    private static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( SecurityClassLoad.class );

    public static void securityClassLoad(ClassLoader loader){

        if( System.getSecurityManager() == null ){
            return;
        }

        String basePackage = "org.apache.jasper.";
        try {
            loader.loadClass( basePackage +
                "runtime.JspFactoryImpl$PrivilegedGetPageContext");
            loader.loadClass( basePackage +
                "runtime.JspFactoryImpl$PrivilegedReleasePageContext");

            loader.loadClass( basePackage +
                "runtime.JspRuntimeLibrary");
            loader.loadClass( basePackage +
                "runtime.JspRuntimeLibrary$PrivilegedIntrospectHelper");
            
            loader.loadClass( basePackage +
                "runtime.ServletResponseWrapperInclude");
            loader.loadClass( basePackage +
                "runtime.TagHandlerPool");
            loader.loadClass( basePackage +
                "runtime.JspFragmentHelper");

            loader.loadClass( basePackage +
                "runtime.ProtectedFunctionMapper");
            loader.loadClass( basePackage +
                "runtime.ProtectedFunctionMapper$1");
            loader.loadClass( basePackage +
                "runtime.ProtectedFunctionMapper$2"); 
            loader.loadClass( basePackage +
                "runtime.ProtectedFunctionMapper$3");
            loader.loadClass( basePackage +
                "runtime.ProtectedFunctionMapper$4"); 

            loader.loadClass( basePackage +
                "runtime.PageContextImpl");      
            loader.loadClass( basePackage +
                "runtime.PageContextImpl$1");      
            loader.loadClass( basePackage +
                "runtime.PageContextImpl$2");      
            loader.loadClass( basePackage +
                "runtime.PageContextImpl$3");      
            loader.loadClass( basePackage +
                "runtime.PageContextImpl$4");      
            loader.loadClass( basePackage +
                "runtime.PageContextImpl$5");      
            loader.loadClass( basePackage +
                "runtime.PageContextImpl$6");      
            loader.loadClass( basePackage +
                "runtime.PageContextImpl$7");      
            loader.loadClass( basePackage +
                "runtime.PageContextImpl$8");      
            loader.loadClass( basePackage +
                "runtime.PageContextImpl$9");      
            loader.loadClass( basePackage +
                "runtime.PageContextImpl$10");      
            loader.loadClass( basePackage +
                "runtime.PageContextImpl$11");      
            loader.loadClass( basePackage +
                "runtime.PageContextImpl$12");      
            loader.loadClass( basePackage +
                "runtime.PageContextImpl$13");      

            loader.loadClass( basePackage +
                "runtime.JspContextWrapper");   

            loader.loadClass( basePackage +
                "servlet.JspServletWrapper");

            loader.loadClass( basePackage +
                "runtime.JspWriterImpl$1");
        } catch (ClassNotFoundException ex) {
            log.error("SecurityClassLoad", ex);
        }
    }
}
