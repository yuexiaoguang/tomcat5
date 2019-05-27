package org.apache.jasper.compiler;

import java.util.List;
import java.util.ArrayList;

/**
 * 表示JSR-045 "stratum"关联的行和文件映射.
 */
public class SmapStratum {

    //*********************************************************************
    // 保存LineInfo 数据的类

    /**
     * 表示SMAP中的单个 LineSection, 与一个特定的层级关联.
     */
    public static class LineInfo {
        private int inputStartLine = -1;
        private int outputStartLine = -1;
        private int lineFileID = 0;
        private int inputLineCount = 1;
        private int outputLineIncrement = 1;
        private boolean lineFileIDSet = false;

        /** 设置InputStartLine. */
        public void setInputStartLine(int inputStartLine) {
            if (inputStartLine < 0)
                throw new IllegalArgumentException("" + inputStartLine);
            this.inputStartLine = inputStartLine;
        }

        /** 设置OutputStartLine. */
        public void setOutputStartLine(int outputStartLine) {
            if (outputStartLine < 0)
                throw new IllegalArgumentException("" + outputStartLine);
            this.outputStartLine = outputStartLine;
        }

        /**
         * 设置lineFileID. 只有当和之前的LineInfo对象不同或0的时候才被调用, 如果当期 LineInfo 没有(逻辑)前任.
         * <tt>LineInfo</tt>不管怎样，都会打印这个文件号.
         */
        public void setLineFileID(int lineFileID) {
            if (lineFileID < 0)
                throw new IllegalArgumentException("" + lineFileID);
            this.lineFileID = lineFileID;
            this.lineFileIDSet = true;
        }

        /** 设置InputLineCount. */
        public void setInputLineCount(int inputLineCount) {
            if (inputLineCount < 0)
                throw new IllegalArgumentException("" + inputLineCount);
            this.inputLineCount = inputLineCount;
        }

        /** 设置OutputLineIncrement. */
        public void setOutputLineIncrement(int outputLineIncrement) {
            if (outputLineIncrement < 0)
                throw new IllegalArgumentException("" + outputLineIncrement);
            this.outputLineIncrement = outputLineIncrement;
        }

        /**
         * 检索当期LineInfo 作为一个 String, 在适当的时候只打印所有的值
         * (但是 LineInfoID 当且仅当它被指定, 因为它的必要性是对上下文敏感的).
         */
        public String getString() {
            if (inputStartLine == -1 || outputStartLine == -1)
                throw new IllegalStateException();
            StringBuffer out = new StringBuffer();
            out.append(inputStartLine);
            if (lineFileIDSet)
                out.append("#" + lineFileID);
            if (inputLineCount != 1)
                out.append("," + inputLineCount);
            out.append(":" + outputStartLine);
            if (outputLineIncrement != 1)
                out.append("," + outputLineIncrement);
            out.append('\n');
            return out.toString();
        }

        public String toString() {
            return getString();
        }
    }

    //*********************************************************************
    // Private state

    private String stratumName;
    private List fileNameList;
    private List filePathList;
    private List lineData;
    private int lastFileID;

    //*********************************************************************
    // Constructor

    /**
     * @param stratumName 层级的名称(e.g., JSP)
     */
    public SmapStratum(String stratumName) {
        this.stratumName = stratumName;
        fileNameList = new ArrayList();
        filePathList = new ArrayList();
        lineData = new ArrayList();
        lastFileID = 0;
    }

    //*********************************************************************
    // Methods to add mapping information

    /**
     * 添加一个新文件的记录, 通过文件名.
     *
     * @param filename 要添加的文件名, 不限制路径.
     */
    public void addFile(String filename) {
        addFile(filename, filename);
    }

    /**
     * 添加一个新文件的记录, 通过文件名和路径. 路径可能相对于源编译路径.
     *
     * @param filename 要添加的文件名, 不限制路径.
     * @param filePath 文件名的路径, 可能相对于源编译路径
     */
    public void addFile(String filename, String filePath) {
        int pathIndex = filePathList.indexOf(filePath);
        if (pathIndex == -1) {
            fileNameList.add(filename);
            filePathList.add(filePath);
        }
    }

    /**
     * 尽可能结合连续LineInfo
     */
    public void optimizeLineSection() {

/* Some debugging code
        for (int i = 0; i < lineData.size(); i++) {
            LineInfo li = (LineInfo)lineData.get(i);
            System.out.print(li.toString());
        }
*/
        //将每个LineInfo 合并到之前的 LineInfo的outputLineIncrement
        int i = 0;
        while (i < lineData.size() - 1) {
            LineInfo li = (LineInfo)lineData.get(i);
            LineInfo liNext = (LineInfo)lineData.get(i + 1);
            if (!liNext.lineFileIDSet
                && liNext.inputStartLine == li.inputStartLine
                && liNext.inputLineCount == 1
                && li.inputLineCount == 1
                && liNext.outputStartLine
                    == li.outputStartLine
                        + li.inputLineCount * li.outputLineIncrement) {
                li.setOutputLineIncrement(
                    liNext.outputStartLine
                        - li.outputStartLine
                        + liNext.outputLineIncrement);
                lineData.remove(i + 1);
            } else {
                i++;
            }
        }

        //将每个LineInfo 合并到之前的 LineInfo的outputLineIncrement
        i = 0;
        while (i < lineData.size() - 1) {
            LineInfo li = (LineInfo)lineData.get(i);
            LineInfo liNext = (LineInfo)lineData.get(i + 1);
            if (!liNext.lineFileIDSet
                && liNext.inputStartLine == li.inputStartLine + li.inputLineCount
                && liNext.outputLineIncrement == li.outputLineIncrement
                && liNext.outputStartLine
                    == li.outputStartLine
                        + li.inputLineCount * li.outputLineIncrement) {
                li.setInputLineCount(li.inputLineCount + liNext.inputLineCount);
                lineData.remove(i + 1);
            } else {
                i++;
            }
        }
    }

    /**
     * 添加有关简单行映射的完整信息. 指定此方法中的所有字段; 后端只负责打印在final SMAP中是必要的.
     * (我的观点是，字段是可选的，主要用于空间效率, 不是为了程序员的方便. 可以稍后再添加实用工具方法.)
     *
     * @param inputStartLine 源文件中的起始行(SMAP <tt>InputStartLine</tt>)
     * @param inputFileName 输入流来源文件的路径（或名称） (yields SMAP <tt>LineFileID</tt>) 谨慎使用不合格的名称, 只有当它们唯一地标识一个文件时.
     * @param inputLineCount 输入到映射中的行数(SMAP <tt>LineFileCount</tt>)
     * @param outputStartLine输出文件中的起始行(SMAP <tt>OutputStartLine</tt>)
     * @param outputLineIncrement 映射到每个输入行的输出行数(SMAP <tt>OutputLineIncrement</tt>).
     * 			<i>鉴于名称以"output"开头, 将这个字段称为<tt>OutputLineExcrement</tt>.</i>
     */
    public void addLineData(
        int inputStartLine,
        String inputFileName,
        int inputLineCount,
        int outputStartLine,
        int outputLineIncrement) {
        // 检查输入
        int fileIndex = filePathList.indexOf(inputFileName);
        if (fileIndex == -1) // still
            throw new IllegalArgumentException(
                "inputFileName: " + inputFileName);

        // Jasper 不正确的 SMAP一定 Nodes, outputStartLine变成 0. 这在optimizeLineSection中可能会导致致命的错误, Jasper不可能编译 JSP.
        // 直到我们能修复下面的SMAPping 问题, 我们会忽略有缺陷的SMAP的条目.
        if (outputStartLine == 0)
            return;

        // 建立 LineInfo
        LineInfo li = new LineInfo();
        li.setInputStartLine(inputStartLine);
        li.setInputLineCount(inputLineCount);
        li.setOutputStartLine(outputStartLine);
        li.setOutputLineIncrement(outputLineIncrement);
        if (fileIndex != lastFileID)
            li.setLineFileID(fileIndex);
        lastFileID = fileIndex;

        // save it
        lineData.add(li);
    }

    //*********************************************************************
    // Methods to retrieve information

    /**
     * 返回层级的名称.
     */
    public String getStratumName() {
        return stratumName;
    }

    /**
     * 返回给定层级为一个String:  a StratumSection, 其次是至少有一个FileSection 和 LineSection.
     */
    public String getString() {
        // 检查状态和初始化缓冲区
        if (fileNameList.size() == 0 || lineData.size() == 0)
            return null;

        StringBuffer out = new StringBuffer();

        // 打印StratumSection
        out.append("*S " + stratumName + "\n");

        // 打印FileSection
        out.append("*F\n");
        int bound = fileNameList.size();
        for (int i = 0; i < bound; i++) {
            if (filePathList.get(i) != null) {
                out.append("+ " + i + " " + fileNameList.get(i) + "\n");
                // 源路径必须是相对的, 不是绝对的, 因此删除开头的 "/"
                String filePath = (String)filePathList.get(i);
                if (filePath.startsWith("/")) {
                    filePath = filePath.substring(1);
                }
                out.append(filePath + "\n");
            } else {
                out.append(i + " " + fileNameList.get(i) + "\n");
            }
        }
        // 打印LineSection
        out.append("*L\n");
        bound = lineData.size();
        for (int i = 0; i < bound; i++) {
            LineInfo li = (LineInfo)lineData.get(i);
            out.append(li.getString());
        }
        return out.toString();
    }

    public String toString() {
        return getString();
    }
}
