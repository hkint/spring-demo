package org.demo.web;

import jakarta.servlet.ServletContext;
import org.demo.annotation.Autowired;
import org.demo.annotation.Bean;
import org.demo.annotation.Configuration;
import org.demo.annotation.Value;

import java.util.Objects;

@Configuration
public class WebMvcConfiguration {

    private static ServletContext servletContext = null;

    /**
     * Set by web listener.
     */
    static void setServletContext(ServletContext ctx) {
        servletContext = ctx;
    }

    @Bean(initMethod = "init")
    ViewResolver viewResolver( //
                               @Autowired ServletContext servletContext, //
                               @Value("${demo.web.freemarker.template-path:/WEB-INF/templates}") String templatePath, //
                               @Value("${demo.web.freemarker.template-encoding:UTF-8}") String templateEncoding) {
        return new FreeMarkerViewResolver(servletContext, templatePath, templateEncoding);
    }

    @Bean
    ServletContext servletContext() {
        return Objects.requireNonNull(servletContext, "ServletContext is not set.");
    }
}
