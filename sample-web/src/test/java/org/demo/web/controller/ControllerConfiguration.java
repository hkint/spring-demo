package org.demo.web.controller;

import org.demo.annotation.Configuration;
import org.demo.annotation.Import;
import org.demo.web.WebMvcConfiguration;

@Configuration
@Import(WebMvcConfiguration.class)
public class ControllerConfiguration {

}
