package org.apache.jasper.xmlparser;

/**
 * XMLString 是用于传递字符数组的结构.
 * 然而, XMLStringBuffer 是可以追加字符的缓冲区 并继承XMLString, 因此它可以传递给希望一个XMLString 对象的方法.
 * 这是一个安全的操作，因为它假定任何调用者不会修改的XMLString结构内容.
 * <p> 
 * 字符串的内容由字符串缓冲区管理. 字符被追加,字符串缓冲区将根据需要增长.
 * <p>
 * <strong>Note:</strong> 永远不要直接设置<code>ch</code>,  <code>offset</code>, <code>length</code>字段.
 * 这些字段由字符串缓冲区管理. 为了重置缓冲区, 调用<code>clear()</code>.
 */
public class XMLStringBuffer extends XMLString {


    /** 默认缓冲区大小(32). */
    public static final int DEFAULT_SIZE = 32;


    public XMLStringBuffer() {
        this(DEFAULT_SIZE);
    }

    /**
     * @param size 
     */
    public XMLStringBuffer(int size) {
        ch = new char[size];
    }

    public XMLStringBuffer(char c) {
        this(1);
        append(c);
    }

    public XMLStringBuffer(String s) {
        this(s.length());
        append(s);
    }

    public XMLStringBuffer(char[] ch, int offset, int length) {
        this(length);
        append(ch, offset, length);
    }

    public XMLStringBuffer(XMLString s) {
        this(s.length);
        append(s);
    }


    /** 清除字符串缓冲区. */
    public void clear() {
        offset = 0;
        length = 0;
    }

    /**
     * append
     * 
     * @param c 
     */
    public void append(char c) {
        if (this.length + 1 > this.ch.length) {
                    int newLength = this.ch.length*2;
                    if (newLength < this.ch.length + DEFAULT_SIZE)
                        newLength = this.ch.length + DEFAULT_SIZE;
                    char[] newch = new char[newLength];
                    System.arraycopy(this.ch, 0, newch, 0, this.length);
                    this.ch = newch;
        }
        this.ch[this.length] = c;
        this.length++;
    }

    /**
     * append
     * 
     * @param s 
     */
    public void append(String s) {
        int length = s.length();
        if (this.length + length > this.ch.length) {
            int newLength = this.ch.length*2;
            if (newLength < this.length + length + DEFAULT_SIZE)
                newLength = this.ch.length + length + DEFAULT_SIZE;
            char[] newch = new char[newLength];            
            System.arraycopy(this.ch, 0, newch, 0, this.length);
            this.ch = newch;
        }
        s.getChars(0, length, this.ch, this.length);
        this.length += length;
    }

    /**
     * append
     * 
     * @param ch 
     * @param offset 
     * @param length 
     */
    public void append(char[] ch, int offset, int length) {
        if (this.length + length > this.ch.length) {
            char[] newch = new char[this.ch.length + length + DEFAULT_SIZE];
            System.arraycopy(this.ch, 0, newch, 0, this.length);
            this.ch = newch;
        }
        System.arraycopy(ch, offset, this.ch, this.length, length);
        this.length += length;
    }

    /**
     * append
     * 
     * @param s 
     */
    public void append(XMLString s) {
        append(s.ch, s.offset, s.length);
    }
}
