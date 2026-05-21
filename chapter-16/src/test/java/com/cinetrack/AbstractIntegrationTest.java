package com.cinetrack;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all integration tests.
 *
 * <p>Starts a single shared PostgreSQL container (Testcontainers reuse is
 * enabled via the static field) and wires its JDBC URL, username, and password
 * into the Spring application context through {@link DynamicPropertySource}.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

        static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("cinetrack_test")
                    .withUsername("cinetrack")
                    .withPassword("secret");


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
