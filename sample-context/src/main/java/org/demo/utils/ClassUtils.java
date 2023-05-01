package org.demo.utils;

import jakarta.annotation.Nullable;
import org.demo.annotation.Bean;
import org.demo.annotation.Component;
import org.demo.exception.BeanDefinitionException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class ClassUtils {

    /**
     * 递归查找Annotation
     * <p>
     * 示例：Annotation A可以直接标注在Class定义:
     *
     * <code>
     *
     * @A public class Hello {}
     * </code>
     * <p>
     * 或者Annotation B标注了A，Class标注了B:
     *
     * <code>
     * &#64;A
     * public @interface B {}
     * @B public class Hello {}
     * </code>
     */
    public static <A extends Annotation> A findAnnotation(Class<?> target, Class<A> annoClass) {
        // 获取目标注解
        A a = target.getAnnotation(annoClass);
        // 遍历类上的注解
        for (Annotation anno : target.getAnnotations()) {
            // 获取注解类型
            Class<? extends Annotation> annoType = anno.annotationType();
            // 判断注解类型是否为 java.lang.annotation 包中的注解
            if (!annoType.getPackageName().equals("java.lang.annotation")) {
                // 如果不是，则递归调用该方法，对其上的注解进行查找
                A found = findAnnotation(annoType, annoClass);
                if (found != null) {
                    // 如果找到了目标注解，则进行处理
                    if (a != null) {
                        // 如果已经找到了目标注解，但是又找到了一个，则抛出异常
                        throw new BeanDefinitionException("在类 " + target.getSimpleName() + " 中找到两个 @"+ annoClass.getSimpleName() +" 注解");
                    }
                    a = found;
                }
            }
        }
        // 返回目标注解
        return a;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A getAnnotation(Annotation[] annos, Class<A> annoClass) {
        for (Annotation anno : annos) {
            if (annoClass.isInstance(anno)) {
                return (A) anno;
            }
        }
        return null;
    }

    /**
     * Get bean name by:
     *
     * <code>
     *
     * @Bean Hello createHello() {}
     * </code>
     */
    public static String getBeanName(Method method) {
        Bean bean = method.getAnnotation(Bean.class);
        String name = bean.value();
        if (name.isEmpty()) {
            name = method.getName();
        }
        return name;
    }

    /**
     * Get bean name by:
     *
     * <code>
     *
     * @Component public class Hello {}
     * </code>
     */
    public static String getBeanName(Class<?> clazz) {
        String name = "";
        // 查找@Component注解:
        Component component = clazz.getAnnotation(Component.class);
        if (component != null) {
            // @Component注解存在:
            name = component.value();
        } else {
            // 如果未找到@Component注解，则在其他注解中查找@Component注解：
            for (Annotation anno : clazz.getAnnotations()) {
                if (findAnnotation(anno.annotationType(), Component.class) != null) {
                    try {
                        // 反射获取@Component注解的值：
                        name = (String) anno.annotationType().getMethod("value").invoke(anno);
                    } catch (ReflectiveOperationException e) {
                        throw new BeanDefinitionException("无法获取注解值: ", e);
                    }
                }
            }
        }
        if (name.isEmpty()) {
            // 默认名字为"HelloWorld" => "helloWorld"：
            name = clazz.getSimpleName();
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return name;
    }

    /**
     * Get non-arg method by @PostConstruct or @PreDestroy. Not search in super
     * class.
     *
     * <code>
     *
     * @PostConstruct void init() {}
     * </code>
     */
    @Nullable
    public static Method findAnnotationMethod(Class<?> clazz, Class<? extends Annotation> annoClass) {
        // 尝试获取已声明的方法：
        List<Method> ms = Arrays.stream(clazz.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(annoClass)).peek(m -> {
            if (m.getParameterCount() != 0) {
                // 如果方法有参数，抛出异常：
                throw new BeanDefinitionException(
                        String.format("方法'%s'使用了@%s注解，不应该有参数：%s", m.getName(), annoClass.getSimpleName(), clazz.getName()));
            }
        }).toList();
        if (ms.isEmpty()) {
            return null;
        }
        if (ms.size() == 1) {
            // 只有一个满足，返回该方法：
            return ms.get(0);
        }
        // 多个方法满足注解，抛出异常：
        throw new BeanDefinitionException(String.format("在类%s中找到多个使用了@%s注解的方法", annoClass.getSimpleName(), clazz.getName()));
    }

    /**
     * Get non-arg method by method name. Not search in super class.
     */
    public static Method getNamedMethod(Class<?> clazz, String methodName) {
        try {
            return clazz.getDeclaredMethod(methodName);
        } catch (ReflectiveOperationException e) {
            throw new BeanDefinitionException(String.format("Method '%s' not found in class: %s", methodName, clazz.getName()));
        }
    }
}
