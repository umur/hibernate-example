package com.cinetrack.config;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.resource.jdbc.spi.StatementInspector;

@Slf4j
public class SlowQueryLogger implements StatementInspector {

    private static final long SLOW_QUERY_THRESHOLD_MS = 100L;

    @Override
    public String inspect(String sql) {
        long start = System.currentTimeMillis();
        // The actual timing wraps execution elsewhere; here we log the SQL for tracing.
        // In production, pair this with a ProxyDataSource listener for accurate timing.
        if (log.isDebugEnabled()) {
            log.debug("Inspecting SQL: {}", sql);
        }
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed > SLOW_QUERY_THRESHOLD_MS) {
            log.warn("SLOW QUERY detected ({}ms): {}", elapsed, sql);
        }
        return sql;
    }
}
