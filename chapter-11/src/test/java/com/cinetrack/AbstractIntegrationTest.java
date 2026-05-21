package com.cinetrack;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers base class.
 *
 * <p>The static {@link PostgreSQLContainer} is started once per JVM process and
 * reused across all test classes. {@code @ServiceConnection} wires the container
 * URL, username, and password into the Spring context automatically.</p>
 */
public abstract class AbstractIntegrationTest {

        @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");
}
