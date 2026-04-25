package com.cinetrack.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class HibernateStatisticsConfig {

    /**
     * Registers the SlowQueryLogger as Hibernate's StatementInspector via
     * HibernatePropertiesCustomizer so it is applied to the auto-configured
     * SessionFactory without replacing the full EntityManagerFactory bean.
     */
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return hibernateProperties -> hibernateProperties.put(
                "hibernate.session_factory.statement_inspector",
                new SlowQueryLogger()
        );
    }

    /**
     * Exposes Hibernate Statistics counters as Micrometer gauges.
     */
    @Bean
    public MeterBinder hibernateStatisticsBinder(EntityManagerFactory emf) {
        return (MeterRegistry registry) -> {
            SessionFactory sf = emf.unwrap(SessionFactory.class);
            Statistics stats = sf.getStatistics();

            io.micrometer.core.instrument.Gauge
                    .builder("hibernate.query.execution.count",
                            stats, Statistics::getQueryExecutionCount)
                    .description("Total number of executed queries")
                    .register(registry);

            io.micrometer.core.instrument.Gauge
                    .builder("hibernate.second.level.cache.hit.count",
                            stats, Statistics::getSecondLevelCacheHitCount)
                    .description("Second-level cache hit count")
                    .register(registry);

            io.micrometer.core.instrument.Gauge
                    .builder("hibernate.second.level.cache.miss.count",
                            stats, Statistics::getSecondLevelCacheMissCount)
                    .description("Second-level cache miss count")
                    .register(registry);

            io.micrometer.core.instrument.Gauge
                    .builder("hibernate.entity.load.count",
                            stats, Statistics::getEntityLoadCount)
                    .description("Total number of entity loads")
                    .register(registry);
        };
    }
}
