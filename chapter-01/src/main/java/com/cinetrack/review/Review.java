package com.cinetrack.review;

import com.cinetrack.movie.Movie;
import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Review entity. Both associations are {@code LAZY}: the default for
 * {@code @ManyToOne} in JPA spec is EAGER, but that is almost always the
 * wrong choice. Explicit {@code fetch = FetchType.LAZY} makes the intent
 * visible and prevents accidental N+1 issues at the entity level.
 *
 * <p>{@code @Version} enables optimistic locking: Hibernate appends
 * {@code WHERE version = ?} to every UPDATE and throws
 * {@link jakarta.persistence.OptimisticLockException} on a conflict.
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"movie", "reviewer"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private AppUser reviewer;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Optimistic lock version. Hibernate reads this value on load, includes
     * it in the UPDATE WHERE clause, and increments it on each successful
     * write. No database-level lock is held between reads and writes.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PrePersist
    void onPersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
