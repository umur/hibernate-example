package com.cinetrack.movie;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.time.Instant;

/**
 * Movie entity with Hibernate second-level cache (READ_WRITE strategy).
 *
 * <h2>READ_WRITE cache concurrency strategy</h2>
 * <p>{@code CacheConcurrencyStrategy.READ_WRITE} is the correct choice for
 * entities that are occasionally updated.  Hibernate uses a "soft lock"
 * protocol to coordinate the cache and the database:
 * <ol>
 *   <li>Before the transaction commits, Hibernate places a soft lock on the
 *       cache entry so concurrent readers know the data may be stale.</li>
 *   <li>After the commit, the entry is updated in the cache with fresh data
 *       and the lock is released.</li>
 * </ol>
 * <p>This prevents dirty reads from the cache at the cost of a brief
 * unavailability of the cached entry around each write.  READ_WRITE requires
 * that the database isolation level is at least READ_COMMITTED, which is the
 * PostgreSQL default.
 *
 * <p>The Ehcache region for this class is configured in {@code ehcache.xml}
 * with a 30-minute TTL and a maximum of 1 000 heap entries.
 */
@Entity
@Table(name = "movies")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(nullable = false)
    @ToString.Include
    private String title;

    @Column(length = 50)
    private String genre;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    public Movie(String title, String genre, int releaseYear) {
        this.title = title;
        this.genre = genre;
        this.releaseYear = releaseYear;
    }
}
