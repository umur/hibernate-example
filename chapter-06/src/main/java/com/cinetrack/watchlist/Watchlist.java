package com.cinetrack.watchlist;

import com.cinetrack.movie.Movie;
import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A named list of movies that a user wants to watch.
 *
 * <h2>Association summary</h2>
 * <ul>
 *   <li>{@code @ManyToOne owner}: the user who owns this watchlist.</li>
 *   <li>{@code @OneToMany entries}: the intermediate entities connecting
 *       movies to this watchlist.  {@code cascade = ALL} and
 *       {@code orphanRemoval = true} give the watchlist full lifecycle
 *       control over its entries.</li>
 * </ul>
 *
 * <h2>Helper methods</h2>
 * {@link #addEntry} and {@link #removeEntry} keep the in-memory collection and
 * the owning-side reference in sync, which is essential when the Watchlist and
 * its entries are all managed in the same persistence context.
 */
@Entity
@Table(name = "watchlists")
@Getter
@Setter
@NoArgsConstructor
public class Watchlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String name;

    /**
     * Inverse side of the Watchlist ↔ WatchlistEntry relationship.
     *
     * <p>{@code orphanRemoval = true}: removing a {@link WatchlistEntry} from
     * this list causes Hibernate to DELETE the corresponding row without any
     * explicit {@code em.remove()} call.  Combined with {@code cascade = ALL},
     * deleting the watchlist removes all entries automatically.
     */
    @OneToMany(mappedBy = "watchlist",
               cascade = CascadeType.ALL,
               orphanRemoval = true)
    private List<WatchlistEntry> entries = new ArrayList<>();

    public Watchlist(AppUser owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    // ── Bidirectional sync helpers ────────────────────────────────────────────

    /**
     * Adds a movie to this watchlist with an optional note.
     *
     * <p>Both sides of the association are kept in sync: the new
     * {@link WatchlistEntry} is added to the {@code entries} list and its
     * {@code watchlist} reference is set to {@code this}.
     *
     * <p><strong>Pre-condition:</strong> both {@code this.id} and
     * {@code movie.id} must be non-null (i.e. both entities must have been
     * persisted before calling this method), because {@link WatchlistEntryId}
     * is constructed from the two IDs.
     *
     * @param movie the movie to add
     * @param notes optional user note; may be null
     * @return the newly created entry
     */
    public WatchlistEntry addEntry(Movie movie, String notes) {
        WatchlistEntry entry = new WatchlistEntry(this, movie, notes);
        entries.add(entry);
        return entry;
    }

    /**
     * Convenience overload that adds a movie with no note.
     */
    public WatchlistEntry addEntry(Movie movie) {
        return addEntry(movie, null);
    }

    /**
     * Removes an entry from the watchlist.
     * Because {@code orphanRemoval = true}, Hibernate DELETEs the row at the
     * next flush: no explicit repository call needed.
     *
     * @param entry the entry to remove; must belong to this watchlist
     */
    public void removeEntry(WatchlistEntry entry) {
        entries.remove(entry);
        entry.setWatchlist(null);
    }
}
