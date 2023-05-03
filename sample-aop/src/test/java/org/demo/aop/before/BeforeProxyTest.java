package org.demo.aop.before;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;

import org.demo.context.AnnotationConfigApplicationContext;
import org.demo.io.PropertyResolver;
import org.junit.jupiter.api.Test;



public class BeforeProxyTest {

    @Test
    public void testBeforeProxy() {
        try (var ctx = new AnnotationConfigApplicationContext(BeforeApplication.class, createPropertyResolver())) {
            BusinessBean proxy = ctx.getBean(BusinessBean.class);
            // should print log:
            assertEquals("Hello, Bob.", proxy.hello("Bob"));
            assertEquals("Morning, Alice.", proxy.morning("Alice"));
        }
    }

    PropertyResolver createPropertyResolver() {
        var ps = new Properties();
        var pr = new PropertyResolver(ps);
        return pr;
    }
}
