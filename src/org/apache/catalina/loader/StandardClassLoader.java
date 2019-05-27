package org.apache.catalina.loader;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * <b>java.net.URLClassLoader</b>子类实现类，它知道如何从磁盘目录加载类, 以及本地和远程JAR文件. 
 * 它也实现了<code>Reloader</code>接口, 为相关的加载程序提供自动重新加载支持.
 * <p>
 * 在所有的情况下, URLs必须符合<code>URLClassLoader</code>指定的合同- 任何以"/"结尾的URL字符被假定为表示目录;
 * 所有其他URL都被假定为JAR文件的地址.
 * <p>
 * <strong>实现注意</strong> - 本地库是按照初始构造函数添加的顺序和<code>addRepository()</code>的调用进行搜索的.
 * <p>
 * <strong>实现注意</strong> - 目前，这个类没有依赖任何其他Catalina类, 这样可以独立使用
 */
public class StandardClassLoader extends URLClassLoader implements StandardClassLoaderMBean {

	public StandardClassLoader(URL repositories[]) {
        super(repositories);
    }

    public StandardClassLoader(URL repositories[], ClassLoader parent) {
        super(repositories, parent);
    }
}

