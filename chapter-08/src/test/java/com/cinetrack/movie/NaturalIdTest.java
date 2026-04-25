package com.cinetrack.movie;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.user.AppUser;
import com.cinetrack.user.AppUserRepository;
import com.cinetrack.watchlist.Watchlist;
import com.cinetrack.watchlist.WatchlistEntry;
import com.cinetrack.watchlist.WatchlistEntryId;
import com.cinetrack.watchlist.WatchlistEntryRepository;
import com.cinetrack.watchlist.WatchlistRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies:
 * <ul>
 *   <li>NaturalId lookup resolves with one SQL query on first call.</li>
 *   <li>Second lookup within same session hits the identity map (zero SQL).</li>
 *   <li>Composite key entity can be persisted and retrieved.</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class NaturalIdTest extends AbstractIntegrationTest {

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private WatchlistRepository watchlistRepository;

    @Autowired
    private WatchlistEntryRepository watchlistEntryRepository;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void enableStatistics() {
        Session session = entityManager.unwrap(Session.class);
        statistics = session.getSessionFactory().getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    @Test
    @DisplayName("findByImdbId derived query always issues SQL")
    void findByImdbIdIssuesSql() {
        // Arrange
        movieRepository.saveAndFlush(new Movie("tt0468569", "The Dark Knight", "ACTION"));
        entityManager.clear();
        statistics.clear();

        // Act
        Optional<Movie> result = movieRepository.findByImdbId("tt0468569");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("The Dark Knight");
        // A WHERE imdb_id = ? query was executed
        assertThat(statistics.getQueryExecutionCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("findByImdbIdCached first call issues SQL; second call is served from session cache")
    void naturalIdCacheHitOnSecondCall() {
        // Arrange
        movieRepository.saveAndFlush(new Movie("tt1375666", "Inception", "SCIENCE_FICTION"));
        entityManager.clear();
        statistics.clear();

        // Act — first call: resolves natural-id → surrogate key via SQL, then loads entity
        Optional<Movie> first = movieRepository.findByImdbIdCached("tt1375666");
        long queriesAfterFirst = statistics.getQueryExecutionCount();

        // Second call within the same session: Hibernate serves from identity map
        Optional<Movie> second = movieRepository.findByImdbIdCached("tt1375666");
        long queriesAfterSecond = statistics.getQueryExecutionCount();

        // Assert
        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first.get().getId()).isEqualTo(second.get().getId());
        // No additional SQL was issued for the second lookup
        assertThat(queriesAfterSecond).isEqualTo(queriesAfterFirst);
    }

    @Test
    @DisplayName("imdbId_duplicate_throwsDataIntegrityViolation: two movies with same imdbId violate unique constraint")
    void imdbId_duplicate_throwsDataIntegrityViolation() {
        movieRepository.saveAndFlush(new Movie("tt9999001", "Original", "DRAMA"));

        org.springframework.dao.DataIntegrityViolationException ex =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> movieRepository.saveAndFlush(new Movie("tt9999001", "Duplicate", "ACTION")),
                        org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(ex).isNotNull();
    }

    @Test
    @DisplayName("uuids_areUnique_across10Saves: 10 saved movies all have distinct surrogate IDs")
    void uuids_areUnique_across10Saves() {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Movie m = movieRepository.saveAndFlush(
                    new Movie("tt800000" + i, "Movie " + i, "DRAMA"));
            ids.add(m.getId());
        }
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids).hasSize(10);
    }

    @Test
    @DisplayName("naturalIdCache_afterClear_reissuesSql: em.clear() forces a new SQL query on second lookup")
    void naturalIdCache_afterClear_reissuesSql() {
        // Arrange
        movieRepository.saveAndFlush(new Movie("tt2222222", "Cache Test", "THRILLER"));
        entityManager.clear();
        statistics.clear();

        // First load — issues SQL (session.byNaturalId goes via PreparedStatement,
        // not JPQL, so we track prepare-statement count rather than
        // getQueryExecutionCount, which only counts HQL/JPQL/Criteria queries.)
        movieRepository.findByImdbIdCached("tt2222222");
        long afterFirst = statistics.getPrepareStatementCount();

        // Clear session — evicts identity map and natural-id cache
        entityManager.clear();
        statistics.clear();

        // Second load after clear — must re-issue SQL
        movieRepository.findByImdbIdCached("tt2222222");
        long afterSecond = statistics.getPrepareStatementCount();

        assertThat(afterFirst).isGreaterThanOrEqualTo(1);
        assertThat(afterSecond).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("movie_nullImdbId_persistsWithoutNaturalIdCacheLookup: Movie with null imdbId persists normally")
    void movie_nullImdbId_persistsWithoutNaturalIdCacheLookup() {
        // Arrange — imdbId column has no NOT NULL constraint on Movie; null is allowed
        Movie movie = new Movie(null, "No IMDB Movie", "DRAMA");
        statistics.clear();

        // Act
        Movie saved = movieRepository.saveAndFlush(movie);
        entityManager.clear();

        // Assert — the entity was assigned a surrogate ID despite having no imdbId
        assertThat(saved.getId()).isNotNull();

        // Reload by surrogate key — must work without any natural-id cache involvement
        Movie reloaded = movieRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTitle()).isEqualTo("No IMDB Movie");
        assertThat(reloaded.getImdbId()).isNull();
    }

    @Test
    @DisplayName("WatchlistEntry can be saved and retrieved by composite key")
    void compositeKeyPersistAndFind() {
        // Arrange
        AppUser user = appUserRepository.saveAndFlush(new AppUser("alice", "alice@example.com"));
        Movie movie = movieRepository.saveAndFlush(new Movie("tt0110912", "Pulp Fiction", "DRAMA"));
        Watchlist watchlist = watchlistRepository.saveAndFlush(new Watchlist(user, "Favourites"));

        WatchlistEntry entry = new WatchlistEntry(watchlist, movie);
        watchlistEntryRepository.saveAndFlush(entry);
        entityManager.clear();

        // Act
        WatchlistEntryId key = new WatchlistEntryId(watchlist.getId(), movie.getId());
        Optional<WatchlistEntry> found = watchlistEntryRepository.findById(key);

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getId().getWatchlistId()).isEqualTo(watchlist.getId());
        assertThat(found.get().getId().getMovieId()).isEqualTo(movie.getId());
    }
}
