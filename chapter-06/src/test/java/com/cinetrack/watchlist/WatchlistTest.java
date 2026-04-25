package com.cinetrack.watchlist;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.movie.Genre;
import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import com.cinetrack.user.AppUser;
import com.cinetrack.user.AppUserRepository;
import com.cinetrack.user.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link Watchlist} associations.
 *
 * <p>Covers:
 * <ul>
 *   <li>addEntry — entry persists via cascade and is queryable</li>
 *   <li>removeEntry + orphanRemoval — entry is deleted from the DB</li>
 *   <li>Bidirectional sync helper consistency</li>
 * </ul>
 */
@DisplayName("Watchlist — association behaviour")
class WatchlistTest extends AbstractIntegrationTest {

    @Autowired private WatchlistRepository watchlistRepository;
    @Autowired private MovieRepository     movieRepository;
    @Autowired private AppUserRepository   userRepository;
    @Autowired private EntityManager   em;
    @Autowired private JdbcTemplate        jdbcTemplate;

    private AppUser user;
    private Movie   movie1;
    private Movie   movie2;

    @BeforeEach
    void setUp() {
        // AppUser requires a UserProfile (optional=false on the OneToOne)
        AppUser newUser = new AppUser("eve", "eve@example.com");
        UserProfile profile = new UserProfile("Movie buff", null);
        newUser.setProfile(profile);
        user = userRepository.saveAndFlush(newUser);

        movie1 = movieRepository.saveAndFlush(new Movie("Interstellar", Genre.SCIENCE_FICTION));
        movie2 = movieRepository.saveAndFlush(new Movie("1917", Genre.DRAMA));
    }

    // ── addEntry ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addEntry persists the entry via cascade and it appears in the DB")
    void addEntryPersistsViacascade() {
        // GIVEN
        Watchlist wl = new Watchlist(user, "Must Watch");
        watchlistRepository.saveAndFlush(wl);  // persist first so id is assigned

        // WHEN
        wl.addEntry(movie1, "Oscar winner");
        watchlistRepository.saveAndFlush(wl);

        // THEN — evict and reload to verify DB state
        em.detach(wl);
        Watchlist loaded = watchlistRepository.findByIdWithEntries(wl.getId()).orElseThrow();
        assertThat(loaded.getEntries()).hasSize(1);
        assertThat(loaded.getEntries().get(0).getMovie().getTitle()).isEqualTo("Interstellar");
        assertThat(loaded.getEntries().get(0).getNotes()).isEqualTo("Oscar winner");
    }

    @Test
    @DisplayName("addEntry for two movies produces two entries in the DB")
    void addTwoEntriesProducesTwoRows() {
        Watchlist wl = new Watchlist(user, "Weekend");
        watchlistRepository.saveAndFlush(wl);

        wl.addEntry(movie1);
        wl.addEntry(movie2);
        watchlistRepository.saveAndFlush(wl);

        em.detach(wl);
        Watchlist loaded = watchlistRepository.findByIdWithEntries(wl.getId()).orElseThrow();
        assertThat(loaded.getEntries()).hasSize(2);
    }

    // ── removeEntry + orphanRemoval ───────────────────────────────────────────

    @Test
    @DisplayName("removeEntry via orphanRemoval deletes the entry row from the DB")
    void removeEntryDeletesFromDb() {
        // GIVEN — watchlist with two entries
        Watchlist wl = new Watchlist(user, "Favourites");
        watchlistRepository.saveAndFlush(wl);
        wl.addEntry(movie1);
        wl.addEntry(movie2);
        watchlistRepository.saveAndFlush(wl);

        // WHEN — remove one entry
        WatchlistEntry toRemove = wl.getEntries().stream()
                .filter(e -> e.getMovie().getId().equals(movie1.getId()))
                .findFirst().orElseThrow();
        wl.removeEntry(toRemove);
        watchlistRepository.saveAndFlush(wl);

        // THEN — only movie2 entry survives
        em.detach(wl);
        Watchlist loaded = watchlistRepository.findByIdWithEntries(wl.getId()).orElseThrow();
        assertThat(loaded.getEntries()).hasSize(1);
        assertThat(loaded.getEntries().get(0).getMovie().getId()).isEqualTo(movie2.getId());
    }

    // ── orphanRemoval — clear all ─────────────────────────────────────────────

    @Test
    @DisplayName("removeAllEntries_viaOrphanRemoval_deletesAllFromDb: clearing entries removes all DB rows")
    void removeAllEntries_viaOrphanRemoval_deletesAllFromDb() {
        // GIVEN — watchlist with three entries
        Movie movie3 = movieRepository.saveAndFlush(new Movie("Dunkirk", Genre.DRAMA));
        Watchlist wl = new Watchlist(user, "Clear Test");
        watchlistRepository.saveAndFlush(wl);
        wl.addEntry(movie1);
        wl.addEntry(movie2);
        wl.addEntry(movie3);
        watchlistRepository.saveAndFlush(wl);

        // WHEN — clear the entire collection
        wl.getEntries().clear();
        watchlistRepository.saveAndFlush(wl);

        // THEN — no entry rows survive in the DB
        em.detach(wl);
        Watchlist reloaded = watchlistRepository.findByIdWithEntries(wl.getId()).orElseThrow();
        assertThat(reloaded.getEntries()).isEmpty();
    }

    @Test
    @DisplayName("cascade_persist_savesEntries: entries persisted via cascade without explicit save")
    void cascade_persist_savesEntries() {
        // GIVEN — create watchlist and add entries before the first saveAndFlush
        Watchlist wl = new Watchlist(user, "Cascade Test");
        watchlistRepository.saveAndFlush(wl);  // must persist watchlist first so id is assigned
        wl.addEntry(movie1, "via cascade");
        wl.addEntry(movie2);

        // WHEN — save only the watchlist; entries cascade automatically
        watchlistRepository.saveAndFlush(wl);

        // THEN — both entries are in the DB without any explicit entry repository call
        em.detach(wl);
        Watchlist loaded = watchlistRepository.findByIdWithEntries(wl.getId()).orElseThrow();
        assertThat(loaded.getEntries()).hasSize(2);
    }

    @Test
    @DisplayName("entries_afterReload_preserveMovieReference: reloaded entry has accessible movie title")
    void entries_afterReload_preserveMovieReference() {
        // GIVEN
        Watchlist wl = new Watchlist(user, "Movie Ref Test");
        watchlistRepository.saveAndFlush(wl);
        wl.addEntry(movie1, "check title");
        watchlistRepository.saveAndFlush(wl);

        // WHEN — evict and reload with entries + movie fetch
        em.detach(wl);
        Watchlist loaded = watchlistRepository.findByIdWithEntries(wl.getId()).orElseThrow();

        // THEN — movie title is accessible without LazyInitializationException
        assertThat(loaded.getEntries()).hasSize(1);
        assertThat(loaded.getEntries().get(0).getMovie().getTitle()).isEqualTo("Interstellar");
    }

    // ── Bidirectional sync ────────────────────────────────────────────────────

    @Test
    @DisplayName("addEntry keeps both sides consistent: entry.watchlist == watchlist")
    void bidirectionalSyncHelperConsistency() {
        Watchlist wl = new Watchlist(user, "Sync Test");
        watchlistRepository.saveAndFlush(wl);

        WatchlistEntry entry = wl.addEntry(movie1, "test note");

        // Both sides must be set before the flush
        assertThat(entry.getWatchlist()).isSameAs(wl);
        assertThat(wl.getEntries()).contains(entry);

        // Composite PK is pre-populated
        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getId().watchlistId()).isEqualTo(wl.getId());
        assertThat(entry.getId().movieId()).isEqualTo(movie1.getId());
    }

    // ── Cascade delete ────────────────────────────────────────────────────────

    @Test
    @DisplayName("watchlist_delete_cascadesToEntries: deleting the watchlist removes its entries from the DB")
    void watchlist_delete_cascadesToEntries() {
        // GIVEN — watchlist with two entries
        Watchlist wl = new Watchlist(user, "To Delete");
        watchlistRepository.saveAndFlush(wl);
        wl.addEntry(movie1);
        wl.addEntry(movie2);
        watchlistRepository.saveAndFlush(wl);
        Long wlId = wl.getId();

        // Verify entries exist before delete
        Integer entryCountBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM watchlist_entries WHERE watchlist_id = ?",
                Integer.class, wlId);
        assertThat(entryCountBefore).isEqualTo(2);

        // WHEN — delete the watchlist; CascadeType.ALL propagates the DELETE
        watchlistRepository.delete(wl);
        watchlistRepository.flush();

        // THEN — all entries must be gone from the DB
        Integer entryCountAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM watchlist_entries WHERE watchlist_id = ?",
                Integer.class, wlId);
        assertThat(entryCountAfter).isZero();
    }

    // ── Composite key uniqueness ──────────────────────────────────────────────

    @Test
    @DisplayName("watchlistEntry_duplicateKey_throwsException: adding the same movie twice violates the composite PK")
    void watchlistEntry_duplicateKey_throwsException() {
        // GIVEN — a watchlist with movie1 already added
        Watchlist wl = new Watchlist(user, "Duplicate Test");
        watchlistRepository.saveAndFlush(wl);
        wl.addEntry(movie1);
        watchlistRepository.saveAndFlush(wl);

        // WHEN / THEN — inserting a duplicate (watchlist_id, movie_id) must fail
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO watchlist_entries (watchlist_id, movie_id) VALUES (?, ?)",
                        wl.getId(), movie1.getId())
        ).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
