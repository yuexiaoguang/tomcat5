package org.apache.jasper.xmlparser;

/**
 * 这个类是一个符号表实现，它保证用作标识符的字符串是唯一引用. 多次调用<code>addSymbol</code>将始终返回相同的字符串引用.
 * <p>
 * 符号表执行相同的任务<code>String.intern()</code>具有以下差异:
 * <ul>
 *  <li>
 *   不需要创建一个新字符串对象，以检索唯一引用. 可以通过使用字符数组中的一系列字符来添加符号.
 *  </li>
 *  <li>
 *   符号表的用户可以提供自己的符号哈希实现. 例如, 一个简单的字符串的哈希算法可能无法产生一种是唯一的符号哈希码均衡集.
 *   具有类似前导字符的字符串特别容易出现这种不好的哈希行为.
 *  </li>
 * </ul>
 */
public class SymbolTable {


    /** 表的默认大小. */
    protected static final int TABLE_SIZE = 101;


    /** 桶. */
    protected Entry[] fBuckets = null;

    // 实际表大小
    protected int fTableSize;

    /** 构造带有默认数量桶的符号表. */
    public SymbolTable() {
        this(TABLE_SIZE);
    }

    /** 构造具有指定数量桶的符号表. */
    public SymbolTable(int tableSize) {
        fTableSize = tableSize;
        fBuckets = new Entry[fTableSize];
    }

    /**
     * 将指定的符号添加到符号表中，并返回对唯一符号的引用. 如果符号已经存在,
     * 返回以前的符号引用, 为了保证符号引用的唯一性.
     *
     * @param symbol 新的符号.
     */
    public String addSymbol(String symbol) {

        // 寻找同一符号
        int bucket = hash(symbol) % fTableSize;
        int length = symbol.length();
        OUTER: for (Entry entry = fBuckets[bucket]; entry != null; entry = entry.next) {
            if (length == entry.characters.length) {
                for (int i = 0; i < length; i++) {
                    if (symbol.charAt(i) != entry.characters[i]) {
                        continue OUTER;
                    }
                }
                return entry.symbol;
            }
        }

        // 创建新条目
        Entry entry = new Entry(symbol, fBuckets[bucket]);
        fBuckets[bucket] = entry;
        return entry.symbol;

    }

    /**
     * 将指定的符号添加到符号表中，并返回对唯一符号的引用. 如果符号已经存在,
     * 返回以前的符号引用, 为了保证符号引用的唯一性.
     *
     * @param buffer 包含新符号的缓冲区.
     * @param offset 对新符号缓冲区的偏移量.
     * @param length 缓冲区中新符号的长度.
     */
    public String addSymbol(char[] buffer, int offset, int length) {

        // 寻找同一符号
        int bucket = hash(buffer, offset, length) % fTableSize;
        OUTER: for (Entry entry = fBuckets[bucket]; entry != null; entry = entry.next) {
            if (length == entry.characters.length) {
                for (int i = 0; i < length; i++) {
                    if (buffer[offset + i] != entry.characters[i]) {
                        continue OUTER;
                    }
                }
                return entry.symbol;
            }
        }

        // 添加新条目
        Entry entry = new Entry(buffer, offset, length, fBuckets[bucket]);
        fBuckets[bucket] = entry;
        return entry.symbol;
    }

    /**
     * 返回指定符号的hashCode值. 此方法返回的值必须和<code>hash(char[],int,int)</code>方法返回的值相同, 当使用包含符号字符串的字符数组调用时.
     *
     * @param symbol 散列符号.
     */
    public int hash(String symbol) {

        int code = 0;
        int length = symbol.length();
        for (int i = 0; i < length; i++) {
            code = code * 37 + symbol.charAt(i);
        }
        return code & 0x7FFFFFF;
    }

    /**
     * 返回指定的符号信息的hashCode值.
     * 此方法返回的值必须和<code>hash(String)</code>方法返回的值相同, 当使用包含符号字符串的字符数组调用时.
     *
     * @param buffer 包含符号的字符缓冲区.
     * @param offset 字符缓冲区中的开始的偏移量.
     * @param length 符号长度.
     */
    public int hash(char[] buffer, int offset, int length) {

        int code = 0;
        for (int i = 0; i < length; i++) {
            code = code * 37 + buffer[offset + i];
        }
        return code & 0x7FFFFFF;
    }

    /**
     * 如果符号表已经包含指定的符号，则返回true.
     *
     * @param symbol 寻找的符号.
     */
    public boolean containsSymbol(String symbol) {

        // 寻找同一符号
        int bucket = hash(symbol) % fTableSize;
        int length = symbol.length();
        OUTER: for (Entry entry = fBuckets[bucket]; entry != null; entry = entry.next) {
            if (length == entry.characters.length) {
                for (int i = 0; i < length; i++) {
                    if (symbol.charAt(i) != entry.characters[i]) {
                        continue OUTER;
                    }
                }
                return true;
            }
        }

        return false;
    }

    /**
     * 返回true, 如果符号表已经包含指定的符号.
     *
     * @param buffer 包含要查找的符号的缓冲区.
     * @param offset 缓冲区的偏移量.
     * @param length 缓冲区中符号的长度.
     */
    public boolean containsSymbol(char[] buffer, int offset, int length) {

        // 寻找同一符号
        int bucket = hash(buffer, offset, length) % fTableSize;
        OUTER: for (Entry entry = fBuckets[bucket]; entry != null; entry = entry.next) {
            if (length == entry.characters.length) {
                for (int i = 0; i < length; i++) {
                    if (buffer[offset + i] != entry.characters[i]) {
                        continue OUTER;
                    }
                }
                return true;
            }
        }

        return false;
    }

    /**
     * 这个类是一个符号表条目。每个条目充当链表中的一个节点.
     */
    protected static final class Entry {

        /** 符号. */
        public String symbol;

        /**
         * 符号字符. 复制此信息以比较性能.
         */
        public char[] characters;

        /** 下一个条目. */
        public Entry next;

        /**
         * 从指定符号和下一个条目引用构造新条目.
         */
        public Entry(String symbol, Entry next) {
            this.symbol = symbol.intern();
            characters = new char[symbol.length()];
            symbol.getChars(0, characters.length, characters, 0);
            this.next = next;
        }

        /**
         * 从指定的符号信息和下一个条目引用构造新条目.
         */
        public Entry(char[] ch, int offset, int length, Entry next) {
            characters = new char[length];
            System.arraycopy(ch, offset, characters, 0, length);
            symbol = new String(characters).intern();
            this.next = next;
        }
    }
}
