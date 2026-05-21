package com.cinetrack.multitenancy;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Pure unit tests for the small, branch-free pieces of the multi-tenancy
 * infrastructure that integration tests miss: the {@code validateExistingCurrentSessions}
 * flag on the resolver, and the unwrap/isUnwrappableAs contract on the
 * connection provider.
 */
class TenantInfraUnitTest {

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    @Test
    void resolver_validateExistingCurrentSessions_returnsFalse() {
        TenantIdentifierResolver resolver = new TenantIdentifierResolver();
        assertThat(resolver.validateExistingCurrentSessions())
                .as("Hibernate may safely reuse a Session across tenant changes; this must be false")
                .isFalse();
    }

    @Test
    void resolver_resolveCurrentTenant_fallsBackToPublic() {
        TenantIdentifierResolver resolver = new TenantIdentifierResolver();
        TenantContext.clear();

        assertThat(resolver.resolveCurrentTenantIdentifier())
                .as("when no tenant is set on the thread, fallback must be 'public'")
                .isEqualTo("public");
    }

    @Test
    void connectionProvider_isUnwrappableAs_trueForMultiTenantSpi() {
        MultiTenantConnectionProviderImpl provider =
                new MultiTenantConnectionProviderImpl(mock(DataSource.class));

        assertThat(provider.isUnwrappableAs(MultiTenantConnectionProvider.class)).isTrue();
        assertThat(provider.isUnwrappableAs(String.class)).isFalse();
    }

    @Test
    void connectionProvider_unwrap_returnsSelfWhenAssignable() {
        MultiTenantConnectionProviderImpl provider =
                new MultiTenantConnectionProviderImpl(mock(DataSource.class));

        MultiTenantConnectionProvider<?> unwrapped =
                provider.unwrap(MultiTenantConnectionProvider.class);

        assertThat(unwrapped).isSameAs(provider);
    }

    @Test
    void connectionProvider_unwrap_throwsForUnsupportedType() {
        MultiTenantConnectionProviderImpl provider =
                new MultiTenantConnectionProviderImpl(mock(DataSource.class));

        assertThatThrownBy(() -> provider.unwrap(String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot unwrap as");
    }

    @Test
    void connectionProvider_supportsAggressiveRelease_isFalse() {
        MultiTenantConnectionProviderImpl provider =
                new MultiTenantConnectionProviderImpl(mock(DataSource.class));

        assertThat(provider.supportsAggressiveRelease())
                .as("connection-per-transaction mode does not support aggressive release")
                .isFalse();
    }
}
