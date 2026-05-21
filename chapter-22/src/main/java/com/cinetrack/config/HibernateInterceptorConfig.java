package com.cinetrack.config;

import com.cinetrack.audit.AuditInterceptor;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link AuditInterceptor} as a <em>factory-scoped</em> Hibernate
 * interceptor.
 *
 * <h3>Factory-scoped vs session-scoped</h3>
 * <p>A <em>session-scoped</em> interceptor is supplied per
 * {@code Session.withOptions().interceptor(…).openSession()} call and is
 * therefore stateful per unit-of-work. A <em>factory-scoped</em> interceptor
 * is a singleton registered once at bootstrap via the
 * {@code hibernate.session_factory.interceptor} property and is shared across
 * all sessions: so it must be thread-safe (stateless logging is fine).</p>
 *
 * <p>Spring Boot exposes {@link HibernatePropertiesCustomizer} as the clean
 * way to inject arbitrary Hibernate properties without touching
 * {@code application.properties}.</p>
 */
@Configuration
public class HibernateInterceptorConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernateInterceptorCustomizer() {
        return hibernateProperties ->
                hibernateProperties.put(
                        "hibernate.session_factory.interceptor",
                        new AuditInterceptor());
    }
}
