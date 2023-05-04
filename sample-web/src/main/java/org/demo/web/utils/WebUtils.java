package org.demo.web.utils;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContext;
import org.demo.context.ApplicationContextUtils;
import org.demo.io.PropertyResolver;
import org.demo.utils.ClassPathUtils;
import org.demo.utils.YamlUtils;
import org.demo.web.DispatcherServlet;
import org.demo.web.FilterRegistrationBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.UncheckedIOException;
import java.util.*;

public class WebUtils {

    public static final String DEFAULT_PARAM_VALUE = "\0\t\0\t\0";

    static final Logger logger = LoggerFactory.getLogger(WebUtils.class);

    static final String CONFIG_APP_YAML = "/application.yml";
    static final String CONFIG_APP_PROP = "/application.properties";

    public static void registerDispatcherServlet(ServletContext servletContext, PropertyResolver properyResolver) {
        var dispatcherServlet = new DispatcherServlet(ApplicationContextUtils.getRequiredApplicationContext(), properyResolver);
        logger.info("register servlet {} for URL '/'", dispatcherServlet.getClass().getName());
        var dispatcherReg = servletContext.addServlet("dispatcherServlet", dispatcherServlet);
        dispatcherReg.addMapping("/");
        dispatcherReg.setLoadOnStartup(0);
    }

    /**
     * 在ServletContext中注册Filter。
     *
     * @param servletContext ServletContext对象，用于注册Filter
     */
    public static void registerFilters(ServletContext servletContext) {
        // 获取ApplicationContext对象
        var applicationContext = ApplicationContextUtils.getRequiredApplicationContext();
        // 遍历所有FilterRegistrationBean类型的bean
        for (var filterRegBean : applicationContext.getBeans(FilterRegistrationBean.class)) {
            // 获取Filter的URL模式
            List<String> urlPatterns = filterRegBean.getUrlPatterns();
            // 如果URL模式为空，则抛出异常
            if (urlPatterns == null || urlPatterns.isEmpty()) {
                throw new IllegalArgumentException("没有URL模式：" + filterRegBean.getClass().getName());
            }
            // 获取Filter对象
            var filter = Objects.requireNonNull(filterRegBean.getFilter(), "FilterRegistrationBean.getFilter()返回的值不能为空");
            // 打印日志
            logger.info("为URL {} 注册过滤器 '{}'，类型为 {}", String.join(", ", urlPatterns), filterRegBean.getName(), filter.getClass().getName());
            // 在ServletContext中添加Filter并映射到URL模式
            var filterReg = servletContext.addFilter(filterRegBean.getName(), filter);
            filterReg.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, urlPatterns.toArray(String[]::new));
        }
    }


    /**
     * Try load property resolver from /application.yml or /application.properties.
     */
    public static PropertyResolver createPropertyResolver() {
        final Properties props = new Properties();
        // try load application.yml:
        try {
            Map<String, Object> ymlMap = YamlUtils.loadYamlAsPlainMap(CONFIG_APP_YAML);
            logger.info("load config: {}", CONFIG_APP_YAML);
            for (String key : ymlMap.keySet()) {
                Object value = ymlMap.get(key);
                if (value instanceof String strValue) {
                    props.put(key, strValue);
                }
            }
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                // try load application.properties:
                ClassPathUtils.readInputStream(CONFIG_APP_PROP, (input) -> {
                    logger.info("load config: {}", CONFIG_APP_PROP);
                    props.load(input);
                    return true;
                });
            }
        }
        return new PropertyResolver(props);
    }
}
