package com.cinetrack.watchlist;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link WatchlistEntry}.
 *
 * <p>JPA requires that composite PKs implement {@link Serializable} and provide
 * correct {@link #equals} and {@link #hashCode} implementations based on all
 * key fields.  Incorrect equality semantics here will cause subtle bugs in
 * Hibernate's first-level cache and collection management.
 *
 * <p>Declared as a Java {@code record} for conciseness — records provide
 * structural {@code equals}/{@code hashCode}/{@code toString} automatically.
 * Hibernate 6.2+ fully supports records as {@code @Embeddable} types.
 */
@Embeddable
public record WatchlistEntryId(
        Long watchlistId,
        Long movieId
) implements Serializable {

    // Records provide equals/hashCode/toString automatically based on all components.
    // No manual implementation needed — the record compiler generates them correctly.

    /** Convenience factory used in {@link Watchlist#addEntry}. */
    public static WatchlistEntryId of(Long watchlistId, Long movieId) {
        return new WatchlistEntryId(watchlistId, movieId);
    }
}
