package org.demo.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.demo.annotation.Autowired;
import org.demo.annotation.Bean;
import org.demo.annotation.Configuration;
import org.demo.annotation.Value;
import org.demo.jdbc.tx.DataSourceTransactionManager;
import org.demo.jdbc.tx.PlatformTransactionManager;
import org.demo.jdbc.tx.TransactionalBeanPostProcessor;

import javax.sql.DataSource;

@Configuration
public class JdbcConfiguration {

    @Bean(destroyMethod = "close")
    DataSource dataSource(
            // properties:
            @Value("${demo.datasource.url}") String url,
            @Value("${demo.datasource.username}") String username,
            @Value("${demo.datasource.password}") String password,
            @Value("${demo.datasource.driver-class-name:}") String driver,
            @Value("${demo.datasource.maximum-pool-size:20}") int maximumPoolSize,
            @Value("${demo.datasource.minimum-pool-size:1}") int minimumPoolSize,
            @Value("${demo.datasource.connection-timeout:30000}") int connTimeout
    ) {
        var config = new HikariConfig();
        config.setAutoCommit(false);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        if (driver != null) {
            config.setDriverClassName(driver);
        }
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumPoolSize);
        config.setConnectionTimeout(connTimeout);
        return new HikariDataSource(config);
    }

    @Bean
    JdbcTemplate jdbcTemplate(@Autowired DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    TransactionalBeanPostProcessor transactionalBeanPostProcessor() {
        return new TransactionalBeanPostProcessor();
    }

    @Bean
    PlatformTransactionManager platformTransactionManager(@Autowired DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}