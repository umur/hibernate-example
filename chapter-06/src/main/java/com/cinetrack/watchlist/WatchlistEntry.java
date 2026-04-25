package com.cinetrack.watchlist;

import com.cinetrack.movie.Movie;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Intermediate entity representing a movie in a watchlist.
 *
 * <h2>Why an intermediate entity instead of a plain {@code @ManyToMany}?</h2>
 * A direct {@code @ManyToMany} produces a bare join table with only the two FK
 * columns.  As soon as the business needs extra data on the relationship —
 * here {@code addedAt} and {@code notes} — you must promote the join table to
 * a full entity.  Doing so from the start avoids a painful migration later.
 *
 * <h2>{@code @EmbeddedId} with {@code @MapsId}</h2>
 * The composite PK ({@code watchlistId}, {@code movieId}) is embedded via
 * {@link WatchlistEntryId}.  The {@code @MapsId} annotation on each
 * {@code @ManyToOne} association tells Hibernate which component of the
 * embedded ID to populate from the FK value:
 * <ul>
 *   <li>{@code @MapsId("watchlistId")} — copies {@code watchlist.id}
 *       into {@code id.watchlistId}</li>
 *   <li>{@code @MapsId("movieId")} — copies {@code movie.id}
 *       into {@code id.movieId}</li>
 * </ul>
 * This means the application never needs to construct or set the
 * {@link WatchlistEntryId} manually — Hibernate derives it from the associated
 * entities before the INSERT.
 */
@Entity
@Table(name = "watchlist_entries")
@Getter
@Setter
@NoArgsConstructor
public class WatchlistEntry {

    @EmbeddedId
    private WatchlistEntryId id;

    /**
     * Owning side FK that also drives the {@code watchlistId} PK component.
     * {@code insertable=false, updatable=false} is required when a column is
     * managed both by the embedded ID and by a {@code @ManyToOne} — without
     * these flags Hibernate would attempt to write the column twice and throw.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("watchlistId")
    @JoinColumn(name = "watchlist_id", nullable = false,
                insertable = false, updatable = false)
    private Watchlist watchlist;

    /**
     * FK that drives the {@code movieId} PK component.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("movieId")
    @JoinColumn(name = "movie_id", nullable = false,
                insertable = false, updatable = false)
    private Movie movie;

    /** When the movie was added to the watchlist. Set by DB DEFAULT. */
    @Column(name = "added_at", nullable = false,
            insertable = false, updatable = false)
    private Instant addedAt;

    /** Optional user note, e.g. "watch with family". */
    @Column(length = 500)
    private String notes;

    /**
     * Creates a new entry. The {@link WatchlistEntryId} is constructed here
     * from the IDs of the two associated entities so that Hibernate can
     * immediately determine the PK value before the INSERT.
     */
    public WatchlistEntry(Watchlist watchlist, Movie movie, String notes) {
        this.watchlist = watchlist;
        this.movie = movie;
        this.notes = notes;
        this.id = WatchlistEntryId.of(watchlist.getId(), movie.getId());
    }
}
