package com.cinetrack.multitenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link TenantFilter} — no Spring context, no Postgres.
 *
 * <p>Covers the three behaviors that integration tests do not exercise:
 * the X-Tenant-ID header is propagated, the public-schema fallback is used
 * when the header is missing, and the ThreadLocal is always cleared, even
 * if the downstream chain throws.
 */
class TenantFilterUnitTest {

    private final TenantFilter filter = new TenantFilter();

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
    }

    @Test
    void doFilterInternal_withHeader_setsTenantThenClears() throws ServletException, IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getHeader(TenantFilter.TENANT_HEADER)).thenReturn("tenant_a");

        // Capture the tenant value at the moment the chain is invoked
        String[] tenantSeenInsideChain = new String[1];
        doAnswer(inv -> {
            tenantSeenInsideChain[0] = TenantContext.get();
            return null;
        }).when(chain).doFilter(req, res);

        filter.doFilter(req, res, chain);

        assertThat(tenantSeenInsideChain[0])
                .as("inside chain.doFilter, the tenant must be the one from the header")
                .isEqualTo("tenant_a");
        assertThat(TenantContext.get())
                .as("after the filter returns, the ThreadLocal must be cleared")
                .isNull();
        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    void doFilterInternal_withoutHeader_fallsBackToPublic() throws ServletException, IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getHeader(TenantFilter.TENANT_HEADER)).thenReturn(null);

        String[] tenantSeenInsideChain = new String[1];
        doAnswer(inv -> {
            tenantSeenInsideChain[0] = TenantContext.get();
            return null;
        }).when(chain).doFilter(req, res);

        filter.doFilter(req, res, chain);

        assertThat(tenantSeenInsideChain[0])
                .as("missing X-Tenant-ID header must fall back to the 'public' schema")
                .isEqualTo("public");
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void doFilterInternal_chainThrows_threadLocalStillCleared() throws ServletException, IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(req.getHeader(TenantFilter.TENANT_HEADER)).thenReturn("tenant_b");
        doAnswer(inv -> { throw new ServletException("downstream blew up"); })
                .when(chain).doFilter(req, res);

        try {
            filter.doFilter(req, res, chain);
        } catch (ServletException expected) {
            // expected — re-thrown by the filter
        }

        assertThat(TenantContext.get())
                .as("finally block must clear the ThreadLocal even when the chain throws")
                .isNull();
    }
}
