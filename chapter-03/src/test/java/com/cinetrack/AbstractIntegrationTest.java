package com.cinetrack;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all integration tests in Chapter 3.
 *
 * <p>A single PostgreSQL 16 container is shared across the entire test run
 * using the Testcontainers singleton pattern: the container is started once
 * in a static initialiser and stopped automatically by the JVM shutdown hook
 * (Testcontainers' Ryuk container or {@code stop()} via the JVM hook).
 *
 * <p>We intentionally do NOT use {@code @Testcontainers} + {@code @Container}
 * here, because that combination stops the container after every test <em>class</em>
 *: even for {@code static} fields: which tears down HikariCP connections and
 * causes {@code CannotCreateTransactionException} for subsequent test classes
 * that share the same Spring application context.
 *
 * <p>{@link DynamicPropertySource} wires the container's JDBC URL into the Spring
 * datasource before any application context is created, so Flyway, HikariCP,
 * and Hibernate all connect to the same container instance.
 */
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("cinetrack_test")
                .withUsername("cinetrack")
                .withPassword("cinetrack")
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
