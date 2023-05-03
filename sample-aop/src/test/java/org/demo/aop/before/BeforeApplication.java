package org.demo.aop.before;


import org.demo.annotation.Bean;
import org.demo.annotation.ComponentScan;
import org.demo.annotation.Configuration;
import org.demo.aop.AroundProxyBeanPostProcessor;

@Configuration
@ComponentScan
public class BeforeApplication {

    @Bean
    AroundProxyBeanPostProcessor createAroundProxyBeanPostProcessor() {
        return new AroundProxyBeanPostProcessor();
    }
}
