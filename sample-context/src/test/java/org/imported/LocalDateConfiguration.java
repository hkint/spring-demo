package org.imported;

import org.demo.annotation.Bean;
import org.demo.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Configuration
public class LocalDateConfiguration {

    @Bean
    LocalDate startLocalDate() {
        return LocalDate.now();
    }

    @Bean
    LocalDateTime startLocalDateTime() {
        return LocalDateTime.now();
    }
}
