package org.apache.catalina.util;


/**
 * 字符串解析工具类，比简单分隔文本案例的StringParser拥有更高的性能.
 * 通过设置字符串来执行解析, 然后使用<code>findXxxx()</code>和<code>skipXxxx()</code>方法记住重要的偏移量.
 * 检索解析字符串的子串, 调用<code>extract()</code>方法使用适当的保存偏移值
 */
public final class StringParser {

    // ----------------------------------------------------------- Constructors

    public StringParser() {
        this(null);
    }


    /**
     * @param string 要解析的字符串
     */
    public StringParser(String string) {
        super();
        setString(string);
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 当前字符串的字符, 作为字符数组. 首次指定字符串时存储，以加快在分析期间对正在比较的字符的访问.
     */
    private char chars[] = null;


    /**
     * 当前点的零相对索引位于被解析的字符串中.
     * <strong>NOTE</strong>: 此索引的值可以大于字符串最后一个字符的索引(即等于字符串长度)如果解析字符串的结尾.
     * 这个值是有用的，包括提取包含字符串末尾的子字符串.
     */
    private int index = 0;


    /**
     * 当前正在分析的字符串的长度. 首次指定字符串时存储避免重复的重新计算.
     */
    private int length = 0;


    /**
     * 当前正在分析的字符串.
     */
    private String string = null;


    // ------------------------------------------------------------- Properties


    /**
     * 返回字符串中当前解析位置的零相对索引.
     */
    public int getIndex() {
        return (this.index);
    }


    /**
     * 返回我们正在分析的字符串的长度
     */
    public int getLength() {
        return (this.length);
    }


    /**
     * 返回当前正在解析的字符串
     */
    public String getString() {
        return (this.string);
    }


    /**
     * 设置当前正在分析的字符串. 解析器状态也被重置为在该字符串的开始处
     *
     * @param string 要解析的字符串
     */
    public void setString(String string) {
        this.string = string;
        if (string != null) {
            this.length = string.length();
            chars = this.string.toCharArray();
        } else {
            this.length = 0;
            chars = new char[0];
        }
        reset();
    }


    // --------------------------------------------------------- Public Methods


    /**
     * 将当前解析位置提前一个, 如果还没有超过字符串的结尾.
     */
    public void advance() {
        if (index < length)
            index++;
    }


    /**
     * 提取并返回从指定位置开始的子字符串, 直到正在解析的字符串的结尾.
     * 如果这是不可能的, 返回零长度字符串.
     *
     * @param start Starting index, zero relative, inclusive
     */
    public String extract(int start) {
        if ((start < 0) || (start >= length))
            return ("");
        else
            return (string.substring(start));
    }


    /**
     * 提取并返回从指定位置开始的子字符串, 并在指定位置结束.
     * 如果这是不可能的, 返回一个零长度的字符串.
     *
     * @param start Starting index, zero relative, inclusive
     * @param end Ending index, zero relative, exclusive
     */
    public String extract(int start, int end) {
        if ((start < 0) || (start >= end) || (end > length))
            return ("");
        else
            return (string.substring(start, end));
    }


    /**
     * 返回指定字符下一个出现的索引,
     * 或字符串最后一个位置之后字符的索引 ，如果找不到这个字符的更多出现.
     * 当前解析位置将更新为返回值.
     *
     * @param ch Character to be found
     */
    public int findChar(char ch) {
        while ((index < length) && (ch != chars[index]))
            index++;
        return (index);
    }


    /**
     * 返回一个非空格字符的下一个匹配项的索引,
     * 或字符串最后一个位置后字符的索引，如果没有发现更多的非空白字符.
     * 当前解析位置将更新为返回值.
     */
    public int findText() {
        while ((index < length) && isWhite(chars[index]))
            index++;
        return (index);
    }


    /**
     * 返回一个空格字符的下一个匹配项的索引,
     * 或字符串最后一个位置后字符的索引，如果没有发现更多的空格字符.
     * 当前解析位置将更新为返回值.
     */
    public int findWhite() {
        while ((index < length) && !isWhite(chars[index]))
            index++;
        return (index);
    }


    /**
     * 将解析器的当前状态重置为正在解析的当前字符串的开头.
     */
    public void reset() {
        index = 0;
    }


    /**
     * 在指向指定字符时推进当前解析位置, 或者直到它移动到字符串的结尾为止.
     * 返回最终值.
     *
     * @param ch Character to be skipped
     */
    public int skipChar(char ch) {
        while ((index < length) && (ch == chars[index]))
            index++;
        return (index);
    }


    /**
     * 在指向非空格字符时推进当前解析位置, 或者直到它移动到字符串的结尾为止.
     * 返回最终值.
     */
    public int skipText() {
        while ((index < length) && !isWhite(chars[index]))
            index++;
        return (index);
    }


    /**
     * 在指向空格字符时推进当前解析位置, 或者直到它移动到字符串的结尾为止.
     * 返回最终值.
     */
    public int skipWhite() {
        while ((index < length) && isWhite(chars[index]))
            index++;
        return (index);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * 指定的字符是空格?
     *
     * @param ch Character to be checked
     */
    protected boolean isWhite(char ch) {
        if ((ch == ' ') || (ch == '\t') || (ch == '\r') || (ch == '\n'))
            return (true);
        else
            return (false);
    }
}
