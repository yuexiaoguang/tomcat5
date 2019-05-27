package org.apache.catalina.core;

import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

/**
 * <b>StandardWrapper</b>对象的外观模式
 */
public final class StandardWrapperFacade implements ServletConfig {

    // ----------------------------------------------------------- Constructors

    public StandardWrapperFacade(StandardWrapper config) {
        super();
        this.config = (ServletConfig) config;
    }

    // ----------------------------------------------------- Instance Variables

    /**
     * Wrapped config.
     */
    private ServletConfig config = null;


    // -------------------------------------------------- ServletConfig Methods


    public String getServletName() {
        return config.getServletName();
    }


    public ServletContext getServletContext() {
        ServletContext theContext = config.getServletContext();
        if ((theContext != null) && (theContext instanceof ApplicationContext))
            theContext = ((ApplicationContext) theContext).getFacade();
        return (theContext);
    }


    public String getInitParameter(String name) {
        return config.getInitParameter(name);
    }


    public Enumeration getInitParameterNames() {
        return config.getInitParameterNames();
    }
}
