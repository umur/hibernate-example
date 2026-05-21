package com.cinetrack.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Initializes the schema structure for each known tenant on startup.
 *
 * <p>Because Flyway is disabled (multi-tenant migrations cannot use Flyway's
 * single-schema model), this component iterates over a hard-coded tenant list,
 * creates each PostgreSQL schema if it does not exist, then applies the DDL
 * script from {@code db/migration/tenant/V1__create_tenant_schema.sql}.
 *
 * <p>In production, the tenant list would be read from a control-plane table
 * in the {@code public} schema rather than hard-coded here.
 */
@Component
public class TenantSchemaInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaInitializer.class);

    /** Tenants whose schemas will be created on startup. */
    static final List<String> KNOWN_TENANTS = List.of("tenant_a", "tenant_b");

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public TenantSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void afterPropertiesSet() {
        for (String tenant : KNOWN_TENANTS) {
            provisionTenant(tenant);
        }
    }

    private void provisionTenant(String tenant) {
        log.info("Provisioning schema for tenant: {}", tenant);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + tenant);

        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("SET search_path = " + tenant);
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                    new ClassPathResource("db/migration/tenant/V1__create_tenant_schema.sql"));
            populator.populate(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to provision schema for tenant: " + tenant, e);
        }
        log.info("Schema provisioned for tenant: {}", tenant);
    }
}
