package com.cinetrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Chapter 4: Hibernate 7 Internals.
 *
 * <p>This module demonstrates:
 * <ul>
 *   <li>The SQM (Semantic Query Model) pipeline replacing the legacy HQL/Criteria AST</li>
 *   <li>Hibernate 7's new type system: {@code JavaType} / {@code JdbcType} descriptors</li>
 *   <li>{@code @JdbcTypeCode(SqlTypes.JSON)} for transparent JSONB mapping</li>
 *   <li>{@code @Array} for native PostgreSQL {@code TEXT[]} columns</li>
 *   <li>{@code @UuidGenerator(style = Style.TIME)} for time-ordered UUIDs</li>
 *   <li>Bytecode enhancement via the Hibernate Maven plugin (dirty-tracking, lazy init)</li>
 * </ul>
 */
@SpringBootApplication
public class CineTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(CineTrackApplication.class, args);
    }
}
