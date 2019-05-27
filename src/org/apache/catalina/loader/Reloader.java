package org.apache.catalina.loader;

/**
 * 内部接口,<code>ClassLoader</code>实现类可以实现支持与上下文相关的类装载器的自动更新功能.
 */
public interface Reloader {


    /**
     * 添加一个新的存储库, 这个ClassLoader可以查找要加载的类
     *
     * @param repository 要加载的类资源的名称, 如一个目录的路径名, 一个JAR文件路径名, 或一个ZIP文件路径名
     *
     * @exception IllegalArgumentException 如果指定的存储库无效或不存在
     */
    public void addRepository(String repository);


    /**
     * 为这个类装入器返回当前存储库的字符串数组. 如果没有, 返回零长度数组
     */
    public String[] findRepositories();


    /**
     * 修改了一个或多个类或资源，以便重新加载?
     */
    public boolean modified();


}
