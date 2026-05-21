package com.cinetrack;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for Chapter 15 integration tests.
 *
 * Uses {@code @SpringBootTest} backed by a real PostgreSQL 16 container so that
 * all Hibernate fetch-strategy behaviour: including {@code @BatchSize}, SUBSELECT
 * fetch, and JOIN FETCH: is exercised against a real database engine, not an
 * in-memory stub.
 *
 * Hibernate statistics are enabled via {@code DynamicPropertySource} so that
 * subclasses can inject {@link org.hibernate.stat.Statistics} and assert exact
 * SQL query counts.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

        static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("cinetrack_test")
                    .withUsername("cinetrack")
                    .withPassword("cinetrack")


    static {
        POSTGRES.start();
    }
    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Enable Hibernate statistics for query-count assertions
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }
}
