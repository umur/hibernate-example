package com.cinetrack.genre;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

/**
 * Reference-data entity representing a film genre (e.g. ACTION, DRAMA).
 *
 * <h2>Second-level cache — READ_ONLY strategy</h2>
 * <p>{@code CacheConcurrencyStrategy.READ_ONLY} is the most efficient L2C
 * strategy: Hibernate never acquires a lock on cache entries and never
 * invalidates them on update — because updates are not expected.  Choosing
 * READ_ONLY on a mutable entity would lead to stale reads, so this strategy
 * is only appropriate for truly immutable reference data.
 *
 * <p>{@code @Immutable} reinforces this at the Hibernate level: any attempt to
 * modify a managed {@code GenreEntity} instance within a transaction throws an
 * exception, preventing accidental mutations.
 *
 * <p>The corresponding Ehcache region is configured in {@code ehcache.xml} with
 * a 24-hour TTL — genre codes are effectively never changed at runtime.
 */
@Entity
@Table(name = "genres")
@Immutable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Getter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class GenreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    @ToString.Include
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    public GenreEntity(String code, String label) {
        this.code = code;
        this.label = label;
    }
}
