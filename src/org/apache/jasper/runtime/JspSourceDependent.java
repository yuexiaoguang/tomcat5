package org.apache.jasper.runtime;

/**
 * 跟踪源文件依赖关系的接口, 为了编译过时的页面. 用于
 * 1) 由页面指令包含的文件
 * 2) jsp:config中的include-prelude和include-coda包含的文件
 * 3) 标签文件和引用文件
 * 4) TLD引用
 */
public interface JspSourceDependent {

   /**
    * 返回当前页面具有源依赖项的文件名列表.
    */
    // FIXME: Type used is Object due to very weird behavior 
    // with Eclipse JDT 3.1 in Java 5 mode
    public Object getDependants();

}
