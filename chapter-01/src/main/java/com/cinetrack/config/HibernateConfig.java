package com.cinetrack.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

/**
 * Demonstrates the relationship between JPA's {@link EntityManagerFactory}
 * and Hibernate's {@link SessionFactory}.
 *
 * <p>Spring Boot's {@code HibernateJpaAutoConfiguration} creates a
 * {@link org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean}
 * that produces a Hibernate-backed {@link EntityManagerFactory}.
 * Because Hibernate implements the JPA contract, you can always unwrap
 * to the native API with {@code emf.unwrap(SessionFactory.class)}.
 *
 * <p>This class also logs Hibernate statistics when the Spring context
 * closes, which is useful during development to verify connection counts,
 * cache hit ratios, and query totals.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class HibernateConfig {

    /**
     * Spring Boot registers the {@link EntityManagerFactory} as a bean named
     * {@code entityManagerFactory}. We inject it here to demonstrate unwrapping.
     */
    private final EntityManagerFactory entityManagerFactory;

    /**
     * Returns the underlying Hibernate {@link SessionFactory}.
     *
     * <p>You rarely need this in application code: Spring Data JPA and
     * {@code @Transactional} handle session lifecycle for you. The unwrap
     * is shown here purely for educational purposes: to illustrate that
     * JPA's EntityManagerFactory and Hibernate's SessionFactory are the
     * same object under the hood.
     */
    public SessionFactory sessionFactory() {
        return entityManagerFactory.unwrap(SessionFactory.class);
    }

    /**
     * On application shutdown, log Hibernate statistics so readers can see
     * how many queries were executed, cache hits, and connection acquisitions
     * during the application's lifetime.
     *
     * <p>Statistics are enabled by setting {@code hibernate.generate_statistics=true}
     * in {@code application.yml}.
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        Statistics stats = sessionFactory().getStatistics();
        if (!stats.isStatisticsEnabled()) {
            log.info("Hibernate statistics are disabled. Set hibernate.generate_statistics=true to enable.");
            return;
        }
        log.info("=== Hibernate Session Statistics ===");
        log.info("  Sessions opened          : {}", stats.getSessionOpenCount());
        log.info("  Sessions closed          : {}", stats.getSessionCloseCount());
        log.info("  Transactions             : {}", stats.getTransactionCount());
        log.info("  Successful transactions  : {}", stats.getSuccessfulTransactionCount());
        log.info("  Queries executed         : {}", stats.getQueryExecutionCount());
        log.info("  Slowest query (ms)       : {}", stats.getQueryExecutionMaxTime());
        log.info("  Slowest query string     : {}", stats.getQueryExecutionMaxTimeQueryString());
        log.info("  Entity load count        : {}", stats.getEntityLoadCount());
        log.info("  Entity insert count      : {}", stats.getEntityInsertCount());
        log.info("  Entity update count      : {}", stats.getEntityUpdateCount());
        log.info("  Entity delete count      : {}", stats.getEntityDeleteCount());
        log.info("  Collection load count    : {}", stats.getCollectionLoadCount());
        log.info("  2nd-level cache hits     : {}", stats.getSecondLevelCacheHitCount());
        log.info("  2nd-level cache misses   : {}", stats.getSecondLevelCacheMissCount());
        log.info("====================================");
    }
}
