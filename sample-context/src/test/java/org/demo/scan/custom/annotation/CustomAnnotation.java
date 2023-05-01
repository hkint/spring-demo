package org.demo.scan.custom.annotation;

import org.demo.annotation.Component;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface CustomAnnotation {

    String value() default "";

}
