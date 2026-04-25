package com.cinetrack.watchlog;

import com.cinetrack.movie.Movie;
import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * Watch log entry — an append-only audit record that is never updated.
 *
 * <h2>{@code @Immutable}</h2>
 * Declares this entity as read-only from Hibernate's perspective:
 * <ul>
 *   <li>Hibernate skips dirty-checking for {@code WatchLog} instances — no
 *       {@code UPDATE} SQL is ever generated, even if fields are mutated in
 *       Java (the changes are silently discarded at flush time).</li>
 *   <li>The entity can still be deleted; {@code @Immutable} only suppresses
 *       updates.</li>
 *   <li>New instances can be persisted normally via {@code persist()} or
 *       {@code save()}.</li>
 * </ul>
 *
 * <p>This is the right tool for event/audit tables where the business rule is
 * "write once, never change" — it communicates intent clearly in the code and
 * eliminates unnecessary work during Hibernate's flush cycle.
 */
@Entity
@Table(name = "watch_logs")
@Immutable
@Getter
@NoArgsConstructor
public class WatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    /**
     * When the user watched the movie. Defaults to {@code now()} in the DB;
     * the application may also supply an explicit timestamp.
     */
    @Column(name = "watched_at", nullable = false)
    private Instant watchedAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    public WatchLog(AppUser user, Movie movie, Instant watchedAt, int durationMinutes) {
        this.user = user;
        this.movie = movie;
        this.watchedAt = watchedAt;
        this.durationMinutes = durationMinutes;
    }
}
