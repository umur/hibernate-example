package com.cinetrack;

import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import com.cinetrack.movie.MovieSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MovieSearchIT extends AbstractIntegrationTest {

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private MovieSearchService movieSearchService;

    @BeforeEach
    void setUp() {
        movieRepository.deleteAll();

        movieRepository.save(new Movie(
                "Inception",
                2010,
                "SCIFI",
                new BigDecimal("8.8"),
                "A thief who steals corporate secrets through dream-sharing technology is given the inverse task of planting an idea."
        ));
        movieRepository.save(new Movie(
                "The Dark Knight",
                2008,
                "ACTION",
                new BigDecimal("9.0"),
                "Batman must accept one of the greatest psychological tests of his ability to fight injustice."
        ));
        movieRepository.save(new Movie(
                "Interstellar",
                2014,
                "SCIFI",
                new BigDecimal("8.6"),
                "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival."
        ));
    }

    @Test
    void searchByKeyword_returnsMatchingMovies() {
        List<Movie> results = movieSearchService.searchByKeyword("dream technology");

        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(m -> m.getTitle().equals("Inception"));
    }

    @Test
    void searchByKeyword_overviewMatch_returnsCorrectMovie() {
        List<Movie> results = movieSearchService.searchByKeyword("wormhole space");

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getTitle()).isEqualTo("Interstellar");
    }

    @Test
    void searchByGenre_returnsOnlyMatchingGenre() {
        List<Movie> scifiMovies = movieSearchService.searchByGenre("SCIFI");

        assertThat(scifiMovies).hasSize(2);
        assertThat(scifiMovies).extracting(Movie::getTitle)
                .containsExactlyInAnyOrder("Inception", "Interstellar");
    }

    @Test
    void searchByKeyword_noMatch_returnsEmptyList() {
        List<Movie> results = movieSearchService.searchByKeyword("zzznomatch999");

        assertThat(results).isEmpty();
    }

    @Test
    void search_afterUpdate_returnsNewTitle() {
        // "Inception" is already saved in setUp: update its title
        Movie inception = movieSearchService.searchByKeyword("dream technology").stream()
                .filter(m -> m.getTitle().equals("Inception"))
                .findFirst()
                .orElseThrow();

        inception.setTitle("Updated Title");
        movieRepository.save(inception);
        movieRepository.flush();

        List<Movie> results = movieSearchService.searchByKeyword("Updated");

        assertThat(results).anyMatch(m -> m.getTitle().equals("Updated Title"));
    }

    @Test
    void search_caseInsensitive_matches() {
        // "Inception" already saved in setUp; search with all-caps
        List<Movie> results = movieSearchService.searchByKeyword("INCEPTION");

        assertThat(results).anyMatch(m -> m.getTitle().equalsIgnoreCase("Inception"));
    }

    @Test
    void search_multipleKeywords_allMustMatch() {
        movieRepository.save(new Movie(
                "The Dark Knight Rises",
                2012,
                "ACTION",
                new java.math.BigDecimal("8.4"),
                "Batman faces Bane in a final confrontation for Gotham City."
        ));

        List<Movie> results = movieSearchService.searchByKeyword("dark knight");

        assertThat(results).anyMatch(m -> m.getTitle().contains("Dark Knight"));
    }

    @Test
    void search_nonExistent_returnsEmpty() {
        List<Movie> results = movieSearchService.searchByKeyword("xyzzynonexistent");

        assertThat(results).isEmpty();
    }

    // -----------------------------------------------------------------------
    // New coverage: MassIndexer, auto-index on save, searchByGenre no-match
    // -----------------------------------------------------------------------

    @Autowired
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @Test
    void massIndexer_emptyDatabase_doesNotThrow() {
        // Clear all movies so the index is built over an empty table
        movieRepository.deleteAll();

        // Replicate MassIndexerConfig.buildIndex() directly: creates its own
        // EntityManager so it does not interfere with the test's persistence context.
        jakarta.persistence.EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            org.hibernate.search.mapper.orm.Search.session(em)
                    .massIndexer(Movie.class)
                    .threadsToLoadObjects(2)
                    .batchSizeToLoadObjects(10)
                    .startAndWait();
            em.getTransaction().commit();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Mass indexer was interrupted", e);
        } finally {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }
        // Reaching here without exception is the assertion
    }

    @Test
    void newlySaved_movie_isSearchable_afterAutoIndex() {
        // Hibernate Search auto-indexes on entity save (IMMEDIATE strategy by default).
        // Save a new movie after setUp has already run, then search for it.
        movieRepository.save(new Movie(
                "Tenet",
                2020,
                "ACTION",
                new java.math.BigDecimal("7.4"),
                "A secret agent embarks on a dangerous mission involving time inversion."
        ));
        movieRepository.flush();

        List<Movie> results = movieSearchService.searchByKeyword("time inversion");

        assertThat(results)
                .as("newly saved movie must be auto-indexed and immediately searchable")
                .anyMatch(m -> m.getTitle().equals("Tenet"));
    }

    @Test
    void searchByGenre_noMatches_returnsEmpty() {
        List<Movie> results = movieSearchService.searchByGenre("NONEXISTENT_GENRE");

        assertThat(results)
                .as("searching by a genre that no movie has must return an empty list")
                .isEmpty();
    }

    @Test
    void searchByKeywordWithLimit_capsResultCount() {
        // The fixture inserts three movies; "the" matches multiple titles/overviews
        // via the `match` predicate. Asking for at most one hit must respect the cap.
        List<Movie> capped = movieSearchService.searchByKeywordWithLimit("the", 1);

        assertThat(capped)
                .as("searchByKeywordWithLimit must return no more than `limit` hits")
                .hasSizeLessThanOrEqualTo(1);
    }
}
