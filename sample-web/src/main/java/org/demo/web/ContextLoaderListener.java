package org.demo.web;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.demo.context.AnnotationConfigApplicationContext;
import org.demo.context.ApplicationContext;
import org.demo.exception.NestedRuntimeException;
import org.demo.io.PropertyResolver;
import org.demo.web.utils.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContextLoaderListener implements ServletContextListener {

    final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Servlet容器启动时自动调用
     *
     * @param sce
     */
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        logger.info("init {}.", getClass().getName());
        // Servlet 容器上下文
        var servletContext = sce.getServletContext();
        var propertyResolver = WebUtils.createPropertyResolver();
        // 设置 UTF-8 编码
        String encoding = propertyResolver.getProperty("${demo.web.character-encoding:UTF-8}");
        servletContext.setRequestCharacterEncoding(encoding);
        servletContext.setResponseCharacterEncoding(encoding);
        // 创建 IoC 容器, 从 web.xml 中获取配置类
        var applicationContext = createApplicationContext(servletContext.getInitParameter("configuration"), propertyResolver);
        // 注册 DispatcherServlet
        WebUtils.registerDispatcherServlet(servletContext, propertyResolver);
        // 设置 applicationContext 属性，便于停止时获取并关闭
        servletContext.setAttribute("applicationContext", applicationContext);
    }

    /**
     * Servlet 容器停止（销毁）时调用
     *
     * @param sce
     */
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (sce.getServletContext().getAttribute("applicationContext") instanceof ApplicationContext applicationContext) {
            applicationContext.close();
        }
    }

    /**
     * 创建 IoC 容器
     *
     * @param configClassName 配置类名称
     * @param propertyResolver 属性解析
     * @return applicationContext
     */
    ApplicationContext createApplicationContext(String configClassName, PropertyResolver propertyResolver) {
        logger.info("init ApplicationContext by configuration: {}", configClassName);
        if (configClassName == null || configClassName.isEmpty()) {
            throw new NestedRuntimeException("Cannot init ApplicationContext for missing init param name: configuration");
        }
        Class<?> configClass;
        try {
            configClass = Class.forName(configClassName);
        } catch (ClassNotFoundException e) {
            throw new NestedRuntimeException("Could not load class from init param 'configuration': " + configClassName);
        }
        return new AnnotationConfigApplicationContext(configClass, propertyResolver);
    }
}