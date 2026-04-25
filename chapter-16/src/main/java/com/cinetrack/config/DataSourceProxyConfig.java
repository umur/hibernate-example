package com.cinetrack.config;

import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

/**
 * Wraps the real DataSource with datasource-proxy so that:
 * <ul>
 *   <li>Every query is counted — useful for asserting exact query counts in tests
 *       via {@code QueryCountHolder}.</li>
 *   <li>Slow queries (> 100 ms) are logged at WARN level via SLF4J.</li>
 * </ul>
 *
 * <p>The {@code @Primary} annotation is essential: Spring Boot's auto-configured
 * JPA infrastructure will inject THIS bean (the proxy) wherever a DataSource is
 * needed, so Hibernate goes through the proxy on every statement.
 */
@Configuration
public class DataSourceProxyConfig {

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties props) {
        DataSource ds = props.initializeDataSourceBuilder().build();

        return ProxyDataSourceBuilder.create(ds)
                .name("CineTrackDS")
                // Log any query that takes longer than 100 ms
                .logSlowQueryBySlf4j(100, TimeUnit.MILLISECONDS, SLF4JLogLevel.WARN, "SlowQueryLogger")
                // Enable per-thread query counting (used by QueryCountHolder in tests)
                .countQuery()
                // Also log all queries at DEBUG level — helpful during development
                .logQueryBySlf4j(SLF4JLogLevel.DEBUG, "QueryLogger")
                .build();
    }
}
