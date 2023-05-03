package org.demo.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * 客户端提供的InvocationHandler只需继承自BeforeInvocationHandlerAdapter，自然就需要覆写before()方法，实现了Before拦截
 */
public abstract class BeforeInvocationHandlerAdapter implements InvocationHandler {

    public abstract void before(Object proxy, Method method, Object[] args);

    @Override
    public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        before(proxy, method, args);
        return method.invoke(proxy, args);
    }
}
