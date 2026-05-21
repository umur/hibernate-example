package com.cinetrack;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all integration tests in Chapter 19.
 *
 * Spins up a single PostgreSQL 16 container shared across the entire test suite
 * via {@code @Testcontainers} static lifecycle. Flyway migrations run
 * automatically on context startup; {@code ddl-auto=validate} then confirms
 * every entity mapping matches the migrated schema.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

        static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("cinetrack_ch19")
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
    }
}
