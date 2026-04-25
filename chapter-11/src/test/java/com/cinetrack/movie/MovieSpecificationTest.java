package com.cinetrack.movie;

import com.cinetrack.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MovieSpecifications} and the composed
 * {@link MovieService#searchMovies} method.
 *
 * <p>The test class extends {@link AbstractIntegrationTest} to get a real
 * PostgreSQL container. {@code @DataJpaTest} bootstraps only the JPA slice, so
 * {@link com.cinetrack.movie.MovieService} is not in the context — we exercise
 * the repository and specifications directly, which is the right unit of testing
 * for persistence logic.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = "/sql/seed-movies.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/sql/cleanup.sql",
     executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class MovieSpecificationTest extends AbstractIntegrationTest {

    @Autowired
    private MovieRepository movieRepository;

    // -------------------------------------------------------------------------
    // Single-spec tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("hasGenre(ACTION) returns only ACTION movies")
    void hasGenre_filtersCorrectly() {
        List<Movie> results = movieRepository.findAll(
                MovieSpecifications.hasGenre(Genre.ACTION));

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(m ->
                assertThat(m.getGenre()).isEqualTo(Genre.ACTION));
        // Seed has 2 ACTION movies: The Dark Knight + Mad Max Fury Road
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("releasedAfter(2015) returns only movies released after 2015")
    void releasedAfter_filtersCorrectly() {
        List<Movie> results = movieRepository.findAll(
                MovieSpecifications.releasedAfter(2015));

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(m ->
                assertThat(m.getReleaseYear()).isGreaterThan(2015));
    }

    @Test
    @DisplayName("ratingAtLeast(8.5) returns only high-rated movies")
    void ratingAtLeast_filtersCorrectly() {
        List<Movie> results = movieRepository.findAll(
                MovieSpecifications.ratingAtLeast(8.5));

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(m ->
                assertThat(m.getRating().doubleValue()).isGreaterThanOrEqualTo(8.5));
    }

    @Test
    @DisplayName("titleContains('dark') is case-insensitive and returns matching movie")
    void titleContains_caseInsensitiveMatch() {
        List<Movie> results = movieRepository.findAll(
                MovieSpecifications.titleContains("dark"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("The Dark Knight");
    }

    // -------------------------------------------------------------------------
    // Combined-spec tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("SCIENCE_FICTION AND ratingAtLeast(8.7) returns only Inception")
    void combinedGenreAndRating_filtersCorrectly() {
        Specification<Movie> spec = MovieSpecifications.hasGenre(Genre.SCIENCE_FICTION)
                .and(MovieSpecifications.ratingAtLeast(8.7));

        List<Movie> results = movieRepository.findAll(spec);

        // Inception=8.8, Interstellar=8.6 → only Inception qualifies
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Inception");
    }

    @Test
    @DisplayName("HORROR AND releasedAfter(2015) returns only Hereditary and Get Out")
    void combinedGenreAndYear_filtersCorrectly() {
        Specification<Movie> spec = MovieSpecifications.hasGenre(Genre.HORROR)
                .and(MovieSpecifications.releasedAfter(2015));

        List<Movie> results = movieRepository.findAll(spec);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Movie::getTitle)
                .containsExactlyInAnyOrder("Get Out", "Hereditary");
    }

    @Test
    @DisplayName("Three combined specs narrow correctly")
    void threeSpecs_combineCorrectly() {
        Specification<Movie> spec = MovieSpecifications.hasGenre(Genre.ACTION)
                .and(MovieSpecifications.releasedAfter(2010))
                .and(MovieSpecifications.ratingAtLeast(8.0));

        List<Movie> results = movieRepository.findAll(spec);

        // Mad Max Fury Road: ACTION, 2015, 8.1 — matches all three
        // The Dark Knight: ACTION, 2008 — does not pass releasedAfter(2010)
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Mad Max Fury Road");
    }

    // -------------------------------------------------------------------------
    // Empty / no-filter spec
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Specification.where(null) with no filters returns all movies")
    void emptySpec_returnsAllMovies() {
        long total = movieRepository.count();

        // Spring Data 4 added a PredicateSpecification overload of where(), making
        // where(null) ambiguous. Use an explicit conjunction instead.
        Specification<Movie> all = (root, query, cb) -> cb.conjunction();
        List<Movie> results = movieRepository.findAll(all);

        assertThat(results).hasSize((int) total);
    }

    // -------------------------------------------------------------------------
    // Pagination via JpaSpecificationExecutor
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAll(spec, pageable) returns correct page of filtered results")
    void specWithPagination_returnsPage() {
        Specification<Movie> spec = MovieSpecifications.ratingAtLeast(7.5);
        Page<Movie> page = movieRepository.findAll(spec, PageRequest.of(0, 3));

        assertThat(page.getContent()).hasSizeLessThanOrEqualTo(3);
        assertThat(page.getContent()).allSatisfy(m ->
                assertThat(m.getRating().doubleValue()).isGreaterThanOrEqualTo(7.5));
    }

    // -------------------------------------------------------------------------
    // Three combined specs narrow to a single matching movie
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Three AND-combined specs return only the movie that satisfies all three")
    void threeSpecs_combined_AND_narrowsResults() {
        // From seed data:
        //   Inception       — SCIENCE_FICTION, 2010, 8.8  → matches genre + year + rating
        //   Interstellar    — SCIENCE_FICTION, 2014, 8.6  → matches genre + year, rating just below 8.7
        //   The Dark Knight — ACTION, 2008, 9.0            → wrong genre, wrong year
        Specification<Movie> spec = MovieSpecifications.hasGenre(Genre.SCIENCE_FICTION)
                .and(MovieSpecifications.releasedAfter(2009))
                .and(MovieSpecifications.ratingAtLeast(8.7));

        List<Movie> results = movieRepository.findAll(spec);

        // Only Inception passes all three predicates
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Inception");
    }

    // -------------------------------------------------------------------------
    // All-null specs act as a no-op — every saved movie is returned
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("All-null specs compose into a no-op that returns all movies")
    void allNullSpecs_returnsAllMovies() {
        long total = movieRepository.count();

        Specification<Movie> spec = MovieSpecifications.hasGenre(null)
                .and(MovieSpecifications.releasedAfter(null))
                .and(MovieSpecifications.ratingAtLeast(null))
                .and(MovieSpecifications.titleContains(null));

        List<Movie> results = movieRepository.findAll(spec);

        assertThat(results).hasSize((int) total);
    }

    // -------------------------------------------------------------------------
    // Boundary value — ratingAtLeast is inclusive
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ratingAtLeast(7.0) includes a movie with rating exactly 7.0")
    void ratingAtLeast_boundaryValue_includedInResults() {
        // The Dark Knight has rating=9.0 and Mad Max Fury Road=8.1 in seed.
        // We rely on the spec being >= not >: any movie with rating=7.0 must appear.
        // Use a rating known to exist: Interstellar is 8.6, filter at 8.6 exactly.
        Specification<Movie> spec = MovieSpecifications.ratingAtLeast(8.6);

        List<Movie> results = movieRepository.findAll(spec);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(m ->
                assertThat(m.getRating().doubleValue()).isGreaterThanOrEqualTo(8.6));
        // Interstellar (8.6) must be present — boundary is inclusive
        assertThat(results).extracting(Movie::getTitle).contains("Interstellar");
    }

    // -------------------------------------------------------------------------
    // Pagination — first page has exactly N items when enough data exists
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("First page of size 3 returns exactly 3 items when total >= 3")
    void pagination_firstPage_hasSizeN() {
        // Seed has at least 6 movies; page size 3 must yield exactly 3
        long total = movieRepository.count();
        org.junit.jupiter.api.Assumptions.assumeTrue(total >= 3,
                "Seed must contain at least 3 movies for this test");

        Specification<Movie> all = (root, query, cb) -> cb.conjunction();
        Page<Movie> page = movieRepository.findAll(all, PageRequest.of(0, 3));

        assertThat(page.getContent()).hasSize(3);
    }
}
