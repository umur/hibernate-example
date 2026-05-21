package com.cinetrack.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that extracts the tenant identifier from the
 * {@code X-Tenant-ID} HTTP header and stores it in {@link TenantContext}.
 *
 * <p>The filter runs exactly once per request ({@link OncePerRequestFilter})
 * and always clears the tenant in the {@code finally} block, ensuring the
 * ThreadLocal is cleaned up even when the downstream handler throws.
 *
 * <p>Requests without an {@code X-Tenant-ID} header are served under the
 * {@code public} schema (the fallback defined in
 * {@link TenantIdentifierResolver}).
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String tenantId = request.getHeader(TENANT_HEADER);
        TenantContext.set(tenantId != null ? tenantId : "public");
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
