package com.cinetrack.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the QueryDSL {@link JPAQueryFactory} as a Spring bean so that
 * services such as {@code MovieQuerydslService} can inject it via
 * constructor injection. QueryDSL does not provide Spring Boot auto-config,
 * so a single {@code @Configuration} class is all that is needed.
 */
@Configuration
public class QuerydslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
