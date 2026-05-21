package com.cinetrack;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all integration tests in chapter-22.
 *
 * <p>Starts a single shared {@link PostgreSQLContainer} (Testcontainers'
 * {@code @Container} on a {@code static} field reuses the container across
 * all test classes in the JVM) and wires its JDBC URL / credentials into
 * Spring's datasource via {@link DynamicPropertySource}.</p>
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

        static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("cinetrack_test")
                    .withUsername("test")
                    .withPassword("test");


    static {
        POSTGRES.start();
    }
    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
