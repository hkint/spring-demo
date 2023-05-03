package org.demo.aop;

import org.demo.context.ApplicationContextUtils;
import org.demo.context.BeanDefinition;
import org.demo.context.BeanPostProcessor;
import org.demo.context.ConfigurableApplicationContext;
import org.demo.exception.AopConfigException;
import org.demo.exception.BeansException;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public abstract class AnnotationProxyBeanPostProcessor<A extends Annotation> implements BeanPostProcessor {
    // 保存原始 bean 实例的映射表
    Map<String, Object> originBeans = new HashMap<>();
    // 参数化类型 A 的 Class 对象
    Class<A> annotationClass;
    public AnnotationProxyBeanPostProcessor() {
        // 获取参数化类型 A 的 Class 对象
        this.annotationClass = getParameterizedType();
    }
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // 获取 bean 的 Class 对象
        Class<?> beanClass = bean.getClass();
        // 判断类上是否使用了指定类型的注解
        A anno = beanClass.getAnnotation(annotationClass);
        if (anno != null) {
            // 获取注解中指定的处理器名称
            String handlerName;
            try {
                handlerName = (String) anno.annotationType().getMethod("value").invoke(anno);
            } catch (ReflectiveOperationException e) {
                throw new AopConfigException(String.format("@%s 必须返回 String 类型的 value() 方法.", this.annotationClass.getSimpleName()), e);
            }
            // 创建代理对象
            Object proxy = createProxy(beanClass, bean, handlerName);
            // 保存原始 bean 实例，并返回代理对象
            originBeans.put(beanName, bean);
            return proxy;
        } else {
            return bean;
        }
    }

    /**
     * 创建指定类型的代理对象
     * @param beanClass bean 的 Class 对象
     * @param bean 原始 bean 实例
     * @param handlerName 代理处理器名称
     * @return 代理对象
     */
    Object createProxy(Class<?> beanClass, Object bean, String handlerName) {
        // 获取 Spring 上下文环境
        ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) ApplicationContextUtils.getRequiredApplicationContext();
        // 根据处理器名称查找 BeanDefinition 对象
        BeanDefinition def = ctx.findBeanDefinition(handlerName);
        if (def == null) {
            throw new AopConfigException(String.format("@%s 代理处理器 '%s' 不存在.", this.annotationClass.getSimpleName(), handlerName));
        }
        // 根据 BeanDefinition 对象实例化处理器对象
        Object handlerBean = def.getInstance();
        if (handlerBean == null) {
            handlerBean = ctx.createBeanAsEarlySingleton(def);
        }
        // 判断处理器是否实现了 InvocationHandler 接口
        if (handlerBean instanceof InvocationHandler handler) {
            // 创建代理对象
            return ProxyResolver.getInstance().createProxy(bean, handler);
        } else {
            throw new AopConfigException(String.format("@%s 代理处理器 '%s' 没有实现 %s 接口.", this.annotationClass.getSimpleName(), handlerName,
                    InvocationHandler.class.getName()));
        }
    }

    /**
     * Bean 属性设置方法
     * @param bean bean 实例
     * @param beanName bean 名称
     * @return 原始 bean 实例或 bean 实例本身
     */
    @Override
    public Object postProcessOnSetProperty(Object bean, String beanName) {
        // 获取原始 bean 实例
        Object origin = this.originBeans.get(beanName);
        // 如果存在，则返回原始实例；否则返回 bean 实例本身
        return origin != null ? origin : bean;
    }

    /**
     * 获取参数化类型 A 的 Class 对象
     * @return 参数化类型 A 的 Class 对象
     */
    @SuppressWarnings("unchecked")
    private Class<A> getParameterizedType() {
        // 获取当前类对象的 GenericSuperclass 类型
        Type type = getClass().getGenericSuperclass();
        if (!(type instanceof ParameterizedType)) {
            throw new IllegalArgumentException("Class " + getClass().getName() + " 没有参数化类型.");
        }
        // 获取 ParameterizedType 对象
        ParameterizedType pt = (ParameterizedType) type;
        Type[] types = pt.getActualTypeArguments();
        if (types.length != 1) {
            throw new IllegalArgumentException("Class " + getClass().getName() + " 参数化类型不唯一.");
        }
        Type r = types[0];
        if (!(r instanceof Class<?>)) {
            throw new IllegalArgumentException("Class " + getClass().getName() + " 参数化类型不是 Class 类型.");
        }
        return (Class<A>) r;
    }
}
