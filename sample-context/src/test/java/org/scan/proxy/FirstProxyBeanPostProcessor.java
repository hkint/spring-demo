package org.scan.proxy;

import org.demo.annotation.Component;
import org.demo.annotation.Order;
import org.demo.context.BeanPostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Order(100)
@Component
public class FirstProxyBeanPostProcessor implements BeanPostProcessor {

    final Logger logger = LoggerFactory.getLogger(getClass());

    // 保存原始Bean:
    Map<String, Object> originBeans = new HashMap<>();

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (OriginBean.class.isAssignableFrom(bean.getClass())) {
            logger.debug("create first proxy for bean '{}': {}", beanName, bean);
            // 检测到OriginBean,创建FirstProxyBean:
            var proxy = new FirstProxyBean((OriginBean) bean);
            // 保存原始Bean:
            originBeans.put(beanName, bean);
            // 返回Proxy:
            return proxy;
        }
        return bean;
    }

    @Override
    public Object postProcessOnSetProperty(Object bean, String beanName) {
        Object origin = originBeans.get(beanName);
        if (origin != null) {
            logger.debug("auto set property for {} from first proxy {} to origin bean: {}", beanName, bean, origin);
            // 存在原始Bean时,返回原始Bean:
            return origin;
        }
        return bean;
    }
}
