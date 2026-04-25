package com.cinetrack;

import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FilterTest extends AbstractIntegrationTest {

    @Autowired
    private MovieRepository movieRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        movieRepository.deleteAll();
    }

    @Test
    @Transactional
    void activeMoviesFilter_excludesDeletedMovies() {
        Movie active1 = movieRepository.save(
                new Movie("Inception", 2010, "SCIFI", new BigDecimal("8.8"), "Dream heist."));
        Movie active2 = movieRepository.save(
                new Movie("Interstellar", 2014, "SCIFI", new BigDecimal("8.6"), "Space survival."));
        Movie deleted = new Movie("Deleted Film", 2000, "DRAMA", new BigDecimal("5.0"), "Should be hidden.");
        deleted.setDeleted(true);
        movieRepository.save(deleted);
        movieRepository.flush();

        // Enable the Hibernate filter on the current session
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("activeMovies").setParameter("deleted", false);

        List<Movie> results = entityManager
                .createQuery("SELECT m FROM Movie m", Movie.class)
                .getResultList();

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Movie::getTitle)
                .containsExactlyInAnyOrder("Inception", "Interstellar");
        assertThat(results).noneMatch(Movie::isDeleted);
    }

    @Test
    @Transactional
    void withoutFilter_allMoviesAreReturned() {
        movieRepository.save(
                new Movie("Inception", 2010, "SCIFI", new BigDecimal("8.8"), "Dream heist."));
        Movie deleted = new Movie("Old Film", 1999, "DRAMA", new BigDecimal("4.5"), "Archived.");
        deleted.setDeleted(true);
        movieRepository.save(deleted);
        movieRepository.flush();

        // No filter enabled — all records visible
        List<Movie> results = entityManager
                .createQuery("SELECT m FROM Movie m", Movie.class)
                .getResultList();

        assertThat(results).hasSize(2);
    }

    @Test
    @Transactional
    void withoutFilter_deletedMoviesAreVisible() {
        Movie active = movieRepository.save(
                new Movie("Visible Film", 2015, "ACTION", new BigDecimal("7.5"), "Active movie."));
        Movie softDeleted = new Movie("Soft Deleted Film", 2010, "DRAMA",
                new BigDecimal("6.0"), "This one is deleted.");
        softDeleted.setDeleted(true);
        movieRepository.save(softDeleted);
        movieRepository.flush();

        // No filter enabled — deleted movie IS visible in raw JPA query
        List<Movie> results = entityManager
                .createQuery("SELECT m FROM Movie m", Movie.class)
                .getResultList();

        assertThat(results).extracting(Movie::getTitle)
                .contains("Soft Deleted Film");
    }

    @Test
    @Transactional
    void withFilter_enabled_excludesDeleted() {
        movieRepository.save(
                new Movie("Active Movie", 2018, "SCIFI", new BigDecimal("8.2"), "Active."));
        Movie softDeleted = new Movie("Deleted Movie", 2005, "COMEDY",
                new BigDecimal("5.5"), "Should be hidden.");
        softDeleted.setDeleted(true);
        movieRepository.save(softDeleted);
        movieRepository.flush();

        // Enable filter — only non-deleted movies should appear
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("activeMovies").setParameter("deleted", false);

        List<Movie> results = entityManager
                .createQuery("SELECT m FROM Movie m", Movie.class)
                .getResultList();

        assertThat(results).extracting(Movie::getTitle)
                .doesNotContain("Deleted Movie");
        assertThat(results).extracting(Movie::getTitle)
                .contains("Active Movie");
    }

    // -----------------------------------------------------------------------
    // New coverage: filter with deleted=true parameter, cross-transaction leak
    // -----------------------------------------------------------------------

    @Test
    @Transactional
    void filter_parameter_false_returnsDeleted() {
        // Save one active and one deleted movie
        movieRepository.save(
                new Movie("Live Film", 2022, "ACTION", new BigDecimal("7.0"), "Active film."));
        Movie deleted = new Movie("Dead Film", 2000, "HORROR", new BigDecimal("4.0"), "Deleted film.");
        deleted.setDeleted(true);
        movieRepository.save(deleted);
        movieRepository.flush();

        // Enable filter with deleted=true — only the deleted movie should match
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("activeMovies").setParameter("deleted", true);

        List<Movie> results = entityManager
                .createQuery("SELECT m FROM Movie m", Movie.class)
                .getResultList();

        assertThat(results)
                .as("filter with deleted=true must return only deleted movies")
                .extracting(Movie::getTitle)
                .contains("Dead Film")
                .doesNotContain("Live Film");

        assertThat(results).allMatch(Movie::isDeleted);
    }

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void filter_crossTransaction_filterDoesNotLeak() {
        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);

        // Setup: one active movie and one deleted movie
        tx.execute(status -> {
            movieRepository.save(
                    new Movie("Leak Active Film", 2019, "DRAMA", new BigDecimal("6.5"), "Active."));
            Movie del = new Movie("Leak Deleted Film", 2018, "HORROR", new BigDecimal("4.0"), "Deleted.");
            del.setDeleted(true);
            movieRepository.save(del);
            return null;
        });

        // Transaction 1: enable filter with deleted=false → only active movie visible (count=1)
        long filteredCount = tx.execute(status -> {
            Session s = entityManager.unwrap(Session.class);
            s.enableFilter("activeMovies").setParameter("deleted", false);
            return (long) entityManager
                    .createQuery("SELECT m FROM Movie m", Movie.class)
                    .getResultList()
                    .size();
        });

        // Transaction 2: new session — filter must NOT be carried over.
        // Without filter, both movies are visible → count must be 2.
        long unfilteredCount = tx.execute(status ->
                (long) entityManager
                        .createQuery("SELECT m FROM Movie m", Movie.class)
                        .getResultList()
                        .size()
        );

        // Key assertions:
        // tx1 (filter on) sees only active movies
        assertThat(filteredCount)
                .as("tx1 with filter(deleted=false) must see only the active movie")
                .isEqualTo(1L);

        // tx2 (no filter) must see both — proves the filter did not leak
        assertThat(unfilteredCount)
                .as("tx2 without filter must see all movies (filter must not leak across sessions)")
                .isEqualTo(2L);

        assertThat(unfilteredCount)
                .as("unfiltered count must be strictly greater than filtered count")
                .isGreaterThan(filteredCount);
    }
}
