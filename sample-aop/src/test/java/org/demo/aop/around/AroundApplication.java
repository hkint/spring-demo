package org.demo.aop.around;


import org.demo.annotation.Bean;
import org.demo.annotation.ComponentScan;
import org.demo.annotation.Configuration;
import org.demo.aop.AroundProxyBeanPostProcessor;

@Configuration
@ComponentScan
public class AroundApplication {

    @Bean
    AroundProxyBeanPostProcessor createAroundProxyBeanPostProcessor() {
        return new AroundProxyBeanPostProcessor();
    }
}
