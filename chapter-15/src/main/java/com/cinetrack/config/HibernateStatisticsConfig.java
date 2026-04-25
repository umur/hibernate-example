package com.cinetrack.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Exposes Hibernate's internal {@link Statistics} object as a Spring bean and
 * registers key metrics with Micrometer.
 *
 * <h2>Why track Hibernate statistics?</h2>
 * Hibernate's statistics subsystem counts every query, collection load, entity
 * load, cache hit/miss, and more.  In tests (see {@code FetchStrategyTest}) we
 * use {@link Statistics#getQueryExecutionCount()} to assert that a JOIN FETCH
 * query fires exactly one SQL statement regardless of result-set size.
 *
 * <h2>Enabling statistics</h2>
 * Statistics are off by default (they add a small overhead).  Enable them via:
 * <pre>
 * spring.jpa.properties.hibernate.generate_statistics=true
 * </pre>
 * which is set in {@code application.yml}.  The {@link Statistics} bean can
 * then be injected into tests via {@code @Autowired}.
 *
 * <h2>Micrometer integration</h2>
 * We register Hibernate's counters as Micrometer {@code Gauge}s so they appear
 * under {@code /actuator/metrics}.  We wire them manually against the
 * {@link Statistics} bean rather than using {@code HibernateMetrics} from
 * micrometer-core, which depends on the legacy {@code javax.persistence} API
 * not present on a Hibernate 7 / Jakarta EE 10 classpath.
 */
@Slf4j
@Configuration
public class HibernateStatisticsConfig {

    /**
     * Exposes the raw Hibernate {@link Statistics} object as a bean so tests
     * can inject it and assert query counts directly.
     */
    @Bean
    public Statistics hibernateStatistics(EntityManagerFactory emf) {
        SessionFactory sf = emf.unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
        log.info("Hibernate statistics enabled");
        return stats;
    }

    /**
     * Registers key Hibernate statistics as Micrometer gauges.
     *
     * Metrics registered:
     * <ul>
     *   <li>{@code hibernate.query.execution.count} — cumulative SQL query count</li>
     *   <li>{@code hibernate.entity.load.count} — entity loads from DB</li>
     *   <li>{@code hibernate.collection.load.count} — collection initialisations</li>
     *   <li>{@code hibernate.second.level.cache.hit.count} — L2 cache hits</li>
     *   <li>{@code hibernate.second.level.cache.miss.count} — L2 cache misses</li>
     * </ul>
     *
     * These appear under {@code /actuator/metrics/hibernate.query.execution.count}
     * etc. when spring-boot-starter-actuator is on the classpath.
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    public Object hibernateMetrics(Statistics stats, MeterRegistry registry) {
        List<Tag> tags = List.of(Tag.of("entityManagerFactory", "cinetrack"));

        registry.gauge("hibernate.query.execution.count", tags, stats,
                s -> (double) s.getQueryExecutionCount());

        registry.gauge("hibernate.entity.load.count", tags, stats,
                s -> (double) s.getEntityLoadCount());

        registry.gauge("hibernate.collection.load.count", tags, stats,
                s -> (double) s.getCollectionLoadCount());

        registry.gauge("hibernate.second.level.cache.hit.count", tags, stats,
                s -> (double) s.getSecondLevelCacheHitCount());

        registry.gauge("hibernate.second.level.cache.miss.count", tags, stats,
                s -> (double) s.getSecondLevelCacheMissCount());

        log.info("Hibernate statistics gauges registered with Micrometer");
        return stats; // return something non-null so Spring registers the bean
    }
}
