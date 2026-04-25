package com.cinetrack.config;

import com.cinetrack.multitenancy.MultiTenantConnectionProviderImpl;
import com.cinetrack.multitenancy.TenantIdentifierResolver;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Wires the multi-tenancy SPI implementations into Hibernate's configuration.
 *
 * <p>Spring Boot exposes {@link HibernatePropertiesCustomizer} as a hook for
 * adding arbitrary Hibernate properties after auto-configuration runs. We use
 * it here to inject the two SPI beans that power schema-based multi-tenancy:
 *
 * <ul>
 *   <li>{@link org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider}
 *       — supplies tenant-scoped JDBC connections.</li>
 *   <li>{@link org.hibernate.context.spi.CurrentTenantIdentifierResolver}
 *       — resolves the tenant for the current thread.</li>
 * </ul>
 *
 * <p>The {@code hibernate.multiTenancy=SCHEMA} property is set in
 * {@code application.yml}; this class only supplies the two companion beans.
 */
@Configuration
public class HibernateMultiTenancyConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernateMultiTenancyCustomizer(
            MultiTenantConnectionProviderImpl connectionProvider,
            TenantIdentifierResolver tenantResolver) {

        return (properties) -> properties.putAll(Map.of(
                AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider,
                AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver
        ));
    }
}
