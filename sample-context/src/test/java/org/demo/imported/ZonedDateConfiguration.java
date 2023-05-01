package org.demo.imported;

import org.demo.annotation.Bean;
import org.demo.annotation.Configuration;

import java.time.ZonedDateTime;


@Configuration
public class ZonedDateConfiguration {

    @Bean
    ZonedDateTime startZonedDateTime() {
        return ZonedDateTime.now();
    }
}
