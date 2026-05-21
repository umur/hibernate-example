package com.cinetrack.multitenancy;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Provides JDBC connections scoped to a specific PostgreSQL schema.
 *
 * <p>When Hibernate needs a connection for tenant {@code tenantA}, this
 * provider borrows a connection from the shared {@link DataSource} and issues
 * {@code SET search_path = tenantA}. PostgreSQL then resolves all unqualified
 * table names against that schema for the lifetime of the connection.
 *
 * <p><strong>Security note:</strong> In production, validate
 * {@code tenantIdentifier} against a whitelist of known tenants before
 * interpolating it into SQL to prevent schema injection.
 */
@Component
public class MultiTenantConnectionProviderImpl
        implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    public MultiTenantConnectionProviderImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        try (Statement stmt = connection.createStatement()) {
            // Switch the schema for this connection
            stmt.execute("SET search_path = " + tenantIdentifier);
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection)
            throws SQLException {
        // Reset to public before returning to pool so the next borrower is safe
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path = public");
        }
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        // Connection-per-transaction mode: safe to release early
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return MultiTenantConnectionProvider.class.isAssignableFrom(unwrapType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> unwrapType) {
        if (isUnwrappableAs(unwrapType)) {
            return (T) this;
        }
        throw new IllegalArgumentException("Cannot unwrap as " + unwrapType.getName());
    }
}
