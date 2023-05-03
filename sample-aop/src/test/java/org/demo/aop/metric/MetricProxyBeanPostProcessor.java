package org.demo.aop.metric;


import org.demo.annotation.Component;
import org.demo.aop.AnnotationProxyBeanPostProcessor;

@Component
public class MetricProxyBeanPostProcessor extends AnnotationProxyBeanPostProcessor<Metric> {

}
