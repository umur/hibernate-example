package com.cinetrack;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests.
 *
 * <p>A single {@link PostgreSQLContainer} is started once for the entire test
 * suite (static field + {@code @Testcontainers} reuse semantics). Spring Boot's
 * {@code @ServiceConnection} reads the container's JDBC URL, username, and
 * password and wires them into the application context automatically — no
 * manual {@code @DynamicPropertySource} required.</p>
 */
public abstract class AbstractIntegrationTest {

        @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");
}
