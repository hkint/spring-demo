package org.demo.jdbc.with.tx;

import org.demo.annotation.ComponentScan;
import org.demo.annotation.Configuration;
import org.demo.annotation.Import;
import org.demo.jdbc.JdbcConfiguration;

@ComponentScan
@Configuration
@Import(JdbcConfiguration.class)
public class JdbcWithTxApplication {

}
