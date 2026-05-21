package com.cinetrack;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all Chapter 4 integration tests.
 *
 * <p>Spins up a PostgreSQL 16 container via Testcontainers once for the entire
 * test suite (static field → single container per JVM). Spring's
 * {@link DynamicPropertySource} wires the container's JDBC URL, username,
 * and password into the application context before the context is created,
 * so Flyway runs migrations against the real database engine.
 *
 * <p>{@code replace = AutoConfigureTestDatabase.Replace.NONE} is essential here:
 * it tells {@code @DataJpaTest} NOT to swap out the real DataSource with an
 * in-memory H2, because we want PostgreSQL-specific features (JSONB, arrays,
 * UUIDs) to actually work.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
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
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
