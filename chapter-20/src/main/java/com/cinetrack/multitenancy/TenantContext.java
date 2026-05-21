package com.cinetrack.multitenancy;

/**
 * Thread-local holder for the current tenant identifier.
 *
 * <p>Set by {@link TenantFilter} at the start of every HTTP request and
 * cleared in the {@code finally} block to prevent tenant ID leakage across
 * requests when threads are recycled from the servlet container pool.
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * TenantContext.set("tenant_a");
 * try {
 *     // all JPA calls in this thread use schema tenant_a
 * } finally {
 *     TenantContext.clear();
 * }
 * }</pre>
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static String get() {
        return CURRENT.get();
    }

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
