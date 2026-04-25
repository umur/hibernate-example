package com.cinetrack.watchlog;

import com.cinetrack.movie.Movie;
import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Records that a user watched a movie, and for how long.
 */
@Entity
@Table(name = "watch_logs")
@Getter
@Setter
@NoArgsConstructor
public class WatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "watch_logs_seq")
    @SequenceGenerator(name = "watch_logs_seq", sequenceName = "watch_logs_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "watched_at", nullable = false)
    private Instant watchedAt = Instant.now();

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    public WatchLog(Movie movie, AppUser user, int durationSeconds) {
        this.movie = movie;
        this.user = user;
        this.durationSeconds = durationSeconds;
    }
}
