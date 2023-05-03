package org.demo.aop.before;

import java.lang.reflect.Method;

import org.demo.annotation.Component;
import org.demo.aop.BeforeInvocationHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Component
public class LogInvocationHandler extends BeforeInvocationHandlerAdapter {

    final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void before(Object proxy, Method method, Object[] args) {
        logger.info("[Before] {}()", method.getName());
    }
}
