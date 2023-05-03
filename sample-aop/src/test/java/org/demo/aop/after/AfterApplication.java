package org.demo.aop.after;


import org.demo.annotation.Bean;
import org.demo.annotation.ComponentScan;
import org.demo.annotation.Configuration;
import org.demo.aop.AroundProxyBeanPostProcessor;

@Configuration
@ComponentScan
public class AfterApplication {

    @Bean
    AroundProxyBeanPostProcessor createAroundProxyBeanPostProcessor() {
        return new AroundProxyBeanPostProcessor();
    }
}
