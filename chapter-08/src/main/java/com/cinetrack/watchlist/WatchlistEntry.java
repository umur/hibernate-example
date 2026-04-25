package com.cinetrack.watchlist;

import com.cinetrack.movie.Movie;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Junction entity between Watchlist and Movie. The composite primary key
 * (watchlist_id, movie_id) is modelled with @EmbeddedId, which keeps the
 * key object as a first-class value type. This approach is preferred over
 * @IdClass when you want to pass the key around as a single object.
 */
@Entity
@Table(name = "watchlist_entries")
@Getter
@Setter
@NoArgsConstructor
public class WatchlistEntry {

    @EmbeddedId
    private WatchlistEntryId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("watchlistId")
    @JoinColumn(name = "watchlist_id")
    private Watchlist watchlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("movieId")
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    public WatchlistEntry(Watchlist watchlist, Movie movie) {
        this.watchlist = watchlist;
        this.movie = movie;
        this.id = new WatchlistEntryId(watchlist.getId(), movie.getId());
    }
}
