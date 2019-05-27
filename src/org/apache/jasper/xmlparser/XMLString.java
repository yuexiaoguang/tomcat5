package org.apache.jasper.xmlparser;

/**
 * 这个类用作一种结构，用于传递扫描器的底层字符缓冲区中包含的文本. offset 和 length 字段允许在不创建新字符数组的情况下重新使用缓冲区.
 * <p>
 * <strong>Note:</strong> 被传递一个XMLString 结构的方法应该考虑以只读的方式读取内容，而不能对缓冲区的内容进行任何修改.
 * 如果该结构（或该结构的值）传递到另一个方法，则接收该结构的方法也不应修改偏移量和长度.
 * <p>
 * <strong>Note:</strong> 被传递一个XMLString 结构的方法需要将信息从缓冲区中复制出来，如果保存操作超出该方法的范围.
 * 一旦传递该结构的方法返回，结构的内容是不稳定的，字符缓冲区的内容也不能保证. 因此, 传递此结构的方法不应保存对结构中包含的结构或字符数组的任何引用.
 */
public class XMLString {


    /** 字符数组. */
    public char[] ch;

    /** 字符数组中的偏移量. */
    public int offset;

    /** 从偏移量中提取字符的长度. */
    public int length;


    public XMLString() {
    }

    /**
     * @param ch     字符数组.
     * @param offset 字符数组中的偏移量.
     * @param length 从偏移量中提取字符的长度.
     */
    public XMLString(char[] ch, int offset, int length) {
        setValues(ch, offset, length);
    }

    /**
     * <strong>Note:</strong> 这不会复制字符数组; 只有对数组的引用被复制.
     *
     * @param string 要复制的XMLString.
     */
    public XMLString(XMLString string) {
        setValues(string);
    }

    /**
     * 使用指定值初始化XMLString 结构的内容.
     * 
     * @param ch     字符数组.
     * @param offset 字符数组中的偏移量.
     * @param length 从偏移量中提取字符的长度.
     */
    public void setValues(char[] ch, int offset, int length) {
        this.ch = ch;
        this.offset = offset;
        this.length = length;
    }

    /**
     * 复制指定字符串结构初始化XMLString 结构的内容.
     * <p>
     * <strong>Note:</strong>这不会复制字符数组; 只有对数组的引用被复制.
     * 
     * @param s
     */
    public void setValues(XMLString s) {
        setValues(s.ch, s.offset, s.length);
    }

    /** 将所有值重置为默认值. */
    public void clear() {
        this.ch = null;
        this.offset = 0;
        this.length = -1;
    }

    /**
     * 返回true 如果XMLString 结构的内容和指定数组相等.
     * 
     * @param ch     字符数组.
     * @param offset 字符数组中的偏移量.
     * @param length 从偏移量中提取字符的长度.
     */
    public boolean equals(char[] ch, int offset, int length) {
        if (ch == null) {
            return false;
        }
        if (this.length != length) {
            return false;
        }

        for (int i=0; i<length; i++) {
            if (this.ch[this.offset+i] != ch[offset+i] ) {
                return false;
            }
        }
        return true;
    }

    /**
     * 返回true 如果XMLString 结构的内容和指定的字符串相等.
     * 
     * @param s 要比较的字符串.
     */
    public boolean equals(String s) {
        if (s == null) {
            return false;
        }
        if ( length != s.length() ) {
            return false;
        }

        // 首先这个比调用 s.toCharArray更快, 直接比较两个数组, 可能涉及创建一个新的char数组对象.
        for (int i=0; i<length; i++) {
            if (ch[offset+i] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }


    public String toString() {
        return length > 0 ? new String(ch, offset, length) : "";
    }
}
