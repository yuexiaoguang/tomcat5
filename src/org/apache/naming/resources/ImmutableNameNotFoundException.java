package org.apache.naming.resources;

import javax.naming.Name;
import javax.naming.NameNotFoundException;

/**
 * 不可变异常，以避免代理上下文创建无用的对象.
 * 这只能在代理上下文中使用. 实际上下文应该返回适当填充的异常.
 */
public class ImmutableNameNotFoundException
    extends NameNotFoundException {

    public void appendRemainingComponent(String name) {}
    public void appendRemainingName(Name name) {}
    public void setRemainingName(Name name) {}
    public void setResolverName(Name name) {}
    public void setRootCause(Throwable e) {}

}
