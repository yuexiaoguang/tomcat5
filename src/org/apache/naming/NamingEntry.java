package org.apache.naming;


/**
 * 代表一个NamingContext绑定.
 */
public class NamingEntry {


    // -------------------------------------------------------------- Constants


    public static final int ENTRY = 0;
    public static final int LINK_REF = 1;
    public static final int REFERENCE = 2;
    
    public static final int CONTEXT = 10;


    // ----------------------------------------------------------- Constructors


    public NamingEntry(String name, Object value, int type) {
        this.name = name;
        this.value = value;
        this.type = type;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * 用于在搜索时避免使用 RTTI.
     */
    public int type;
    public String name;
    public Object value;


    // --------------------------------------------------------- Object Methods


    public boolean equals(Object obj) {
        if ((obj != null) && (obj instanceof NamingEntry)) {
            return name.equals(((NamingEntry) obj).name);
        } else {
            return false;
        }
    }


    public int hashCode() {
        return name.hashCode();
    }
}
