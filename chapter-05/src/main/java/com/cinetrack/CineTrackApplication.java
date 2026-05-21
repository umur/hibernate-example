package com.cinetrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Chapter 5: Entity Mapping Deep Dive.
 *
 * <p>This module demonstrates:
 * <ul>
 *   <li>{@code AttributeConverter}: mapping a value-object ({@code EmailAddress})
 *       to/from a plain VARCHAR column with {@code @Converter(autoApply=true)}</li>
 *   <li>{@code @Embeddable} / {@code @Embedded}: the {@code Money} record composed
 *       inline into the {@code Subscription} table</li>
 *   <li>{@code @DynamicUpdate} / {@code @DynamicInsert}: Hibernate generates SQL
 *       containing only the columns that actually changed</li>
 *   <li>{@code @Formula}: virtual read-only properties backed by sub-SELECT SQL</li>
 *   <li>{@code @Immutable}: marks {@code WatchLog} as read-only; Hibernate skips
 *       dirty-checking and never issues UPDATE statements for it</li>
 * </ul>
 */
@SpringBootApplication
public class CineTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(CineTrackApplication.class, args);
    }
}
