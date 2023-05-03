package org.scan.destroy;

import jakarta.annotation.PreDestroy;
import org.demo.annotation.Component;
import org.demo.annotation.Value;

@Component
public class AnnotationDestroyBean {

    @Value("${app.title}")
    public String appTitle;

    @PreDestroy
    void destroy() {
        this.appTitle = null;
    }
}
