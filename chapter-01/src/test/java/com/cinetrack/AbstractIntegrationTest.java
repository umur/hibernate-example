package com.cinetrack;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all integration tests in Chapter 1.
 *
 * <p>Testcontainers starts a real PostgreSQL 16 container once per test run
 * (the {@code static} field means the container is shared across all subclasses
 * in the same JVM). {@link DynamicPropertySource} wires the container's
 * dynamically-assigned port into Spring's datasource properties before the
 * application context is created.
 *
 * <p>This approach gives you:
 * <ul>
 *   <li>A real database: no H2 dialect quirks</li>
 *   <li>Flyway migrations running against exactly the same engine as production</li>
 *   <li>Isolation: the container is thrown away after the test run</li>
 * </ul>
 */
public abstract class AbstractIntegrationTest {

        static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("cinetrack_test")
                    .withUsername("cinetrack")
                    .withPassword("cinetrack")


    static {
        POSTGRES.start();
    }
    /**
     * Overrides the datasource URL, username, and password with the values
     * provided by the running Testcontainers instance. Spring resolves these
     * properties before constructing any beans, so the datasource and Flyway
     * both pick up the correct JDBC URL automatically.
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
