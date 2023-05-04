package org.demo.annotation;

import java.lang.annotation.*;

/**
 * 不允许单独在方法处定义，直接在class级别启动所有public方法的事务
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface Transactional {

    String value() default "platformTransactionManager";
}
