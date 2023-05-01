package org.demo.scan.init;

import jakarta.annotation.PostConstruct;
import org.demo.annotation.Component;
import org.demo.annotation.Value;

@Component
public class AnnotationInitBean {

    @Value("${app.title}")
    String appTitle;

    @Value("${app.version}")
    String appVersion;

    public String appName;

    @PostConstruct
    void init() {
        this.appName = this.appTitle + " / " + this.appVersion;
    }
}