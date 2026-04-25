package com.cinetrack.watchlist;

import com.cinetrack.movie.Movie;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;

@Entity
@Table(name = "watchlist_entries")
@Getter
@Setter
@NoArgsConstructor
public class WatchlistEntry {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "watchlist_id")
    private Watchlist watchlist;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @Column(name = "added_at")
    private Instant addedAt;

    public WatchlistEntry(Movie movie) {
        this.movie = movie;
        this.addedAt = Instant.now();
    }
}
