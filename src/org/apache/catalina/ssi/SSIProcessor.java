package org.apache.catalina.ssi;


import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.StringTokenizer;
import org.apache.catalina.util.IOTools;

/**
 * SSI处理的入口点. 这个类进行实际的解析, 委派到SSIMediator, SSICommand, SSIExternalResolver 是必要的
 */
public class SSIProcessor {
    /** The start pattern */
    protected final static String COMMAND_START = "<!--#";
    /** The end pattern */
    protected final static String COMMAND_END = "-->";
    protected final static int BUFFER_SIZE = 4096;
    protected SSIExternalResolver ssiExternalResolver;
    protected HashMap commands = new HashMap();
    protected int debug;


    public SSIProcessor(SSIExternalResolver ssiExternalResolver, int debug) {
        this.ssiExternalResolver = ssiExternalResolver;
        this.debug = debug;
        addBuiltinCommands();
    }


    protected void addBuiltinCommands() {
        addCommand("config", new SSIConfig());
        addCommand("echo", new SSIEcho());
        addCommand("exec", new SSIExec());
        addCommand("include", new SSIInclude());
        addCommand("flastmod", new SSIFlastmod());
        addCommand("fsize", new SSIFsize());
        addCommand("printenv", new SSIPrintenv());
        addCommand("set", new SSISet());
        SSIConditional ssiConditional = new SSIConditional();
        addCommand("if", ssiConditional);
        addCommand("elif", ssiConditional);
        addCommand("endif", ssiConditional);
        addCommand("else", ssiConditional);
    }


    public void addCommand(String name, SSICommand command) {
        commands.put(name, command);
    }


    /**
     * 用服务器端命令处理文件, 从reader那里读，把处理过的版本写给writer.
     *
     * NOTE: 应该以流式方式来做这件事，而不是先把它转换成数组.
     *
     * @param reader the reader to read the file containing SSIs from
     * @param writer the writer to write the file with the SSIs processed.
     * @throws IOException 当事情变得糟糕的时候. 应该不太可能, 因为SSICommand通常捕获'normal' IOExceptions.
     */
    public long process(Reader reader, long lastModifiedDate,
            PrintWriter writer) throws IOException {
        SSIMediator ssiMediator = new SSIMediator(ssiExternalResolver,
                lastModifiedDate, debug);
        StringWriter stringWriter = new StringWriter();
        IOTools.flow(reader, stringWriter);
        String fileContents = stringWriter.toString();
        stringWriter = null;
        int index = 0;
        boolean inside = false;
        StringBuffer command = new StringBuffer();
        try {
            while (index < fileContents.length()) {
                char c = fileContents.charAt(index);
                if (!inside) {
                    if (c == COMMAND_START.charAt(0)
                            && charCmp(fileContents, index, COMMAND_START)) {
                        inside = true;
                        index += COMMAND_START.length();
                        command.setLength(0); //clear the command string
                    } else {
                        if (!ssiMediator.getConditionalState().processConditionalCommandsOnly) {
                            writer.write(c);
                        }
                        index++;
                    }
                } else {
                    if (c == COMMAND_END.charAt(0)
                            && charCmp(fileContents, index, COMMAND_END)) {
                        inside = false;
                        index += COMMAND_END.length();
                        String strCmd = parseCmd(command);
                        if (debug > 0) {
                            ssiExternalResolver.log(
                                    "SSIProcessor.process -- processing command: "
                                            + strCmd, null);
                        }
                        String[] paramNames = parseParamNames(command, strCmd
                                .length());
                        String[] paramValues = parseParamValues(command,
                                strCmd.length(), paramNames.length);
                        //We need to fetch this value each time, since it may
                        // change
                        // during the loop
                        String configErrMsg = ssiMediator.getConfigErrMsg();
                        SSICommand ssiCommand = (SSICommand)commands
                                .get(strCmd.toLowerCase());
                        String errorMessage = null;
                        if (ssiCommand == null) {
                            errorMessage = "Unknown command: " + strCmd;
                        } else if (paramValues == null) {
                            errorMessage = "Error parsing directive parameters.";
                        } else if (paramNames.length != paramValues.length) {
                            errorMessage = "Parameter names count does not match parameter values count on command: "
                                    + strCmd;
                        } else {
                            // 如果只处理有条件的命令，并且命令不是有条件的，就不要处理命令
                            if (!ssiMediator.getConditionalState().processConditionalCommandsOnly
                                    || ssiCommand instanceof SSIConditional) {
                                long lmd = ssiCommand.process(ssiMediator, strCmd,
                                               paramNames, paramValues, writer);
                                if (lmd > lastModifiedDate) {
                                    lastModifiedDate = lmd;
                                }                                    
                            }
                        }
                        if (errorMessage != null) {
                            ssiExternalResolver.log(errorMessage, null);
                            writer.write(configErrMsg);
                        }
                    } else {
                        command.append(c);
                        index++;
                    }
                }
            }
        } catch (SSIStopProcessingException e) {
            //如果在这里，那么已经停止了处理，所以一切都很好
        }
        return lastModifiedDate;
    }


    /**
     * 解析一个StringBuffer 取出参数类型标记.
     * 从<code>requestHandler</code>调用
     * 
     * @param cmd a value of type 'StringBuffer'
     * @return a value of type 'String[]'
     */
    protected String[] parseParamNames(StringBuffer cmd, int start) {
        int bIdx = start;
        int i = 0;
        int quotes = 0;
        boolean inside = false;
        StringBuffer retBuf = new StringBuffer();
        while (bIdx < cmd.length()) {
            if (!inside) {
                while (bIdx < cmd.length() && isSpace(cmd.charAt(bIdx)))
                    bIdx++;
                if (bIdx >= cmd.length()) break;
                inside = !inside;
            } else {
                while (bIdx < cmd.length() && cmd.charAt(bIdx) != '=') {
                    retBuf.append(cmd.charAt(bIdx));
                    bIdx++;
                }
                retBuf.append('=');
                inside = !inside;
                quotes = 0;
                boolean escaped = false;
                for (; bIdx < cmd.length() && quotes != 2; bIdx++) {
                    char c = cmd.charAt(bIdx);
                    // Need to skip escaped characters
                    if (c == '\\' && !escaped) {
                        escaped = true;
                        bIdx++;
                        continue;
                    }
                    escaped = false;
                    if (c == '"') quotes++;
                }
            }
        }
        StringTokenizer str = new StringTokenizer(retBuf.toString(), "=");
        String[] retString = new String[str.countTokens()];
        while (str.hasMoreTokens()) {
            retString[i++] = str.nextToken().trim();
        }
        return retString;
    }


    /**
     * 解析一个StringBuffer 取出参数类型标记.
     * 从<code>requestHandler</code>获取
     * 
     * @param cmd
     *            a value of type 'StringBuffer'
     * @return a value of type 'String[]'
     */
    protected String[] parseParamValues(StringBuffer cmd, int start, int count) {
        int valIndex = 0;
        boolean inside = false;
        String[] vals = new String[count];
        StringBuffer sb = new StringBuffer();
        for (int bIdx = start; bIdx < cmd.length(); bIdx++) {
            if (!inside) {
                while (bIdx < cmd.length() && cmd.charAt(bIdx) != '"')
                    bIdx++;
                if (bIdx >= cmd.length()) break;
                inside = !inside;
            } else {
                boolean escaped = false;
                for (; bIdx < cmd.length(); bIdx++) {
                    char c = cmd.charAt(bIdx);
                    if (c == '\\' && !escaped) {
                        escaped = true;
                        continue;
                    }
                    // 如果到达另一个地方 " 然后停止
                    if (c == '"' && !escaped) break;
                    // 由于属性的解析和var替换是在不同的地方完成的,
                    // we need to leave escape in the string
                    if (c == '$' && escaped) sb.append('\\');
                    escaped = false;
                    sb.append(c);
                }
                // If we hit the end without seeing a quote
                // the signal an error
                if (bIdx == cmd.length()) return null;
                vals[valIndex++] = sb.toString();
                sb.delete(0, sb.length()); // clear the buffer
                inside = !inside;
            }
        }
        return vals;
    }


    /**
     * 解析一个StringBuffer 取出参数类型标记.
     * 从<code>requestHandler</code>获取
     * 
     * @param cmd a value of type 'StringBuffer'
     * @return a value of type 'String', or null if there is none
     */
    private String parseCmd(StringBuffer cmd) {
        int firstLetter = -1;
        int lastLetter = -1;
        for (int i = 0; i < cmd.length(); i++) {
            char c = cmd.charAt(i);
            if (Character.isLetter(c)) {
                if (firstLetter == -1) {
                    firstLetter = i;
                }
                lastLetter = i;
            } else if (isSpace(c)) {
                if (lastLetter > -1) {
                    break;
                }
            } else {
                break;
            }
        }
        String command = null;
        if (firstLetter != -1) {
            command = cmd.substring(firstLetter, lastLetter + 1);
        }
        return command;
    }


    protected boolean charCmp(String buf, int index, String command) {
        return buf.regionMatches(index, command, 0, command.length());
    }


    protected boolean isSpace(char c) {
        return c == ' ' || c == '\n' || c == '\t' || c == '\r';
    }
}