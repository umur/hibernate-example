package com.cinetrack.multitenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which tenant (PostgreSQL schema) to use for the current
 * thread.
 *
 * <p>Hibernate calls {@link #resolveCurrentTenantIdentifier()} before opening
 * every {@link org.hibernate.Session}. The value is read from
 * {@link TenantContext}, which is populated by {@link TenantFilter} from the
 * incoming {@code X-Tenant-ID} HTTP header.
 *
 * <p>Falls back to the {@code public} schema when no tenant is set — useful
 * during startup health-checks and actuator requests that do not carry a
 * tenant header.
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    private static final String DEFAULT_TENANT = "public";

    @Override
    public String resolveCurrentTenantIdentifier() {
        String tenantId = TenantContext.get();
        return tenantId != null ? tenantId : DEFAULT_TENANT;
    }

    /**
     * Returning {@code false} tells Hibernate it is safe to reuse an existing
     * {@link org.hibernate.Session} even when the tenant identifier changes
     * mid-thread (rare in practice; true would force a new Session every time).
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
