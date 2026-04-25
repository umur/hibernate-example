package com.cinetrack.watchlog;

import com.cinetrack.movie.Movie;
import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Records a single viewing event — which user watched which movie, when, and
 * for how long.
 *
 * <h2>ID generation strategy</h2>
 * <p>The entity uses a database sequence ({@code watch_log_seq}) with an
 * allocation size of 50, matching both the sequence INCREMENT defined in the
 * migration and {@code hibernate.jdbc.batch_size}.  This is crucial for
 * efficient batching: Hibernate pre-allocates a block of 50 IDs from the
 * sequence in a single round-trip, then assigns them in memory without
 * contacting the database per row — eliminating the "sequence per insert"
 * anti-pattern that would break JDBC batching.
 *
 * <p>With {@code IDENTITY} generation (auto-increment), the database returns
 * the generated ID only after the INSERT, forcing Hibernate to flush each row
 * individually and making JDBC batching impossible.  The sequence strategy
 * avoids this entirely.
 */
@Entity
@Table(name = "watch_logs")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class WatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "watch_log_gen")
    @SequenceGenerator(
            name = "watch_log_gen",
            sequenceName = "watch_log_seq",
            allocationSize = 50  // must match the sequence INCREMENT in the migration
    )
    @ToString.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "watched_at", nullable = false)
    private Instant watchedAt = Instant.now();

    @Column(name = "duration_seconds")
    private int durationSeconds;

    public WatchLog(AppUser user, Movie movie, Instant watchedAt, int durationSeconds) {
        this.user = user;
        this.movie = movie;
        this.watchedAt = watchedAt;
        this.durationSeconds = durationSeconds;
    }
}
