package org.apache.catalina.ssi;


import java.io.PrintWriter;

/**
 * 返回与所提供的服务器变量相关联的结果.
 */
public class SSIEcho implements SSICommand {
    protected final static String DEFAULT_ENCODING = "entity";
    protected final static String MISSING_VARIABLE_VALUE = "(none)";


    public long process(SSIMediator ssiMediator, String commandName,
            String[] paramNames, String[] paramValues, PrintWriter writer) {
    	long lastModified = 0;
        String encoding = DEFAULT_ENCODING;
        String errorMessage = ssiMediator.getConfigErrMsg();
        for (int i = 0; i < paramNames.length; i++) {
            String paramName = paramNames[i];
            String paramValue = paramValues[i];
            if (paramName.equalsIgnoreCase("var")) {
                String variableValue = ssiMediator.getVariableValue(
                        paramValue, encoding);
                if (variableValue == null) {
                    variableValue = MISSING_VARIABLE_VALUE;
                }
                writer.write(variableValue);
                lastModified = System.currentTimeMillis();
            } else if (paramName.equalsIgnoreCase("encoding")) {
                if (isValidEncoding(paramValue)) {
                    encoding = paramValue;
                } else {
                    ssiMediator.log("#echo--Invalid encoding: " + paramValue);
                    writer.write(errorMessage);
                }
            } else {
                ssiMediator.log("#echo--Invalid attribute: " + paramName);
                writer.write(errorMessage);
            }
        }
        return lastModified;
    }


    protected boolean isValidEncoding(String encoding) {
        return encoding.equalsIgnoreCase("url")
                || encoding.equalsIgnoreCase("entity")
                || encoding.equalsIgnoreCase("none");
    }
}