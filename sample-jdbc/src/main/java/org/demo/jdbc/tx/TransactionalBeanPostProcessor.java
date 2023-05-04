package org.demo.jdbc.tx;

import org.demo.annotation.Transactional;
import org.demo.aop.AnnotationProxyBeanPostProcessor;

public class TransactionalBeanPostProcessor extends AnnotationProxyBeanPostProcessor<Transactional> {

}
