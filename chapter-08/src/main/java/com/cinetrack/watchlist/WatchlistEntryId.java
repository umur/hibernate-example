package com.cinetrack.watchlist;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite primary key for WatchlistEntry.
 *
 * Rules for @Embeddable composite keys:
 * <ul>
 *   <li>Must implement {@link Serializable}.</li>
 *   <li>Must override equals() and hashCode() based on all key fields : 
 *       Lombok's @EqualsAndHashCode handles this correctly.</li>
 *   <li>Must have a no-arg constructor.</li>
 * </ul>
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class WatchlistEntryId implements Serializable {

    @Column(name = "watchlist_id")
    private Long watchlistId;

    @Column(name = "movie_id")
    private Long movieId;
}
