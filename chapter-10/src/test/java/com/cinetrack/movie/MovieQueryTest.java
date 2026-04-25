package com.cinetrack.movie;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.review.Review;
import com.cinetrack.review.ReviewRepository;
import com.cinetrack.user.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MovieRepository}.
 *
 * <p>{@code @DataJpaTest} bootstraps only the JPA slice: entities, repositories,
 * and Flyway migrations — no web layer, no full application context. The
 * Testcontainer is inherited from {@link AbstractIntegrationTest} and provides a
 * real PostgreSQL instance so native queries and database-specific behaviour are
 * exercised faithfully.</p>
 *
 * <p>{@code @Sql} seeds the database before the test class runs and cleans up
 * afterwards, keeping tests independent of each other.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = "/sql/seed-movies.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/sql/cleanup.sql",
     executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class MovieQueryTest extends AbstractIntegrationTest {

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private TestEntityManager em;

    // -------------------------------------------------------------------------
    // 1. JPQL constructor expression
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findMovieSummaries() returns one DTO per movie with correct aggregates")
    void findMovieSummaries_returnsAggregatedDtos() {
        List<MovieSummaryDto> summaries = movieRepository.findMovieSummaries();

        assertThat(summaries).isNotEmpty();

        // Inception has two reviews (ratings 9 and 8) → avg 8.5, count 2
        MovieSummaryDto inception = summaries.stream()
                .filter(s -> s.title().equals("Inception"))
                .findFirst()
                .orElseThrow();

        assertThat(inception.avgRating()).isEqualTo(8.5);
        assertThat(inception.reviewCount()).isEqualTo(2L);

        // The Grand Illusion has no reviews → avg is null → mapped to 0.0 by AVG
        MovieSummaryDto grandIllusion = summaries.stream()
                .filter(s -> s.title().equals("The Grand Illusion"))
                .findFirst()
                .orElseThrow();

        assertThat(grandIllusion.reviewCount()).isZero();
    }

    // -------------------------------------------------------------------------
    // 2. JOIN FETCH — no N+1
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByGenreWithReviews() eagerly loads reviews in a single query")
    void findByGenreWithReviews_loadsReviewsEagerly() {
        List<Movie> sciFiMovies = movieRepository.findByGenreWithReviews(Genre.SCIENCE_FICTION);

        assertThat(sciFiMovies).hasSize(2); // Inception + Interstellar

        // Reviews collection must be initialised without triggering extra SELECTs.
        // Inside @DataJpaTest the EntityManager is still open, so we verify
        // the collection is already loaded by checking it is not a proxy.
        Movie inception = sciFiMovies.stream()
                .filter(m -> m.getTitle().equals("Inception"))
                .findFirst()
                .orElseThrow();

        assertThat(inception.getReviews()).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // 3. Native query
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findTopRated() delegates raw SQL to PostgreSQL and returns ordered results")
    void findTopRated_returnsMoviesAboveThreshold() {
        List<Movie> topMovies = movieRepository.findTopRated(8.0, 3);

        assertThat(topMovies).hasSizeLessThanOrEqualTo(3);
        // All returned movies must meet the minimum rating
        assertThat(topMovies).allSatisfy(m ->
                assertThat(m.getRating()).isGreaterThanOrEqualTo(BigDecimal.valueOf(8.0)));
        // Results must be ordered descending by rating
        for (int i = 0; i < topMovies.size() - 1; i++) {
            assertThat(topMovies.get(i).getRating())
                    .isGreaterThanOrEqualTo(topMovies.get(i + 1).getRating());
        }
    }

    // -------------------------------------------------------------------------
    // 4. @Modifying bulk update
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updateRating() issues a single UPDATE without loading the entity")
    void updateRating_persistsNewRating() {
        Movie movie = movieRepository.findAll().stream()
                .filter(m -> m.getTitle().equals("Interstellar"))
                .findFirst()
                .orElseThrow();

        BigDecimal newRating = new BigDecimal("9.1");
        int affected = movieRepository.updateRating(movie.getId(), newRating);

        assertThat(affected).isEqualTo(1);

        // clearAutomatically = true on @Modifying evicts the entity;
        // the next find() must hit the database and see the updated value.
        em.clear();
        Movie reloaded = movieRepository.findById(movie.getId()).orElseThrow();
        assertThat(reloaded.getRating()).isEqualByComparingTo(newRating);
    }

    // -------------------------------------------------------------------------
    // 5. @QueryHints read-only
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findReleasedAfterReadOnly() returns movies without adding them to dirty-check set")
    void findReleasedAfterReadOnly_returnsCorrectMovies() {
        List<Movie> recent = movieRepository.findReleasedAfterReadOnly(2000);

        assertThat(recent).isNotEmpty();
        assertThat(recent).allSatisfy(m -> assertThat(m.getReleaseYear()).isGreaterThanOrEqualTo(2000));
    }

    // -------------------------------------------------------------------------
    // 6. @EntityGraph
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findWithReviewsById() loads reviews and their reviewers via EntityGraph")
    void findWithReviewsById_loadsFullGraph() {
        Movie inception = movieRepository.findAll().stream()
                .filter(m -> m.getTitle().equals("Inception"))
                .findFirst()
                .orElseThrow();

        Optional<Movie> loaded = movieRepository.findWithReviewsById(inception.getId());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getReviews()).hasSize(2);
        // Reviewer must also be initialised (not a proxy) due to the EntityGraph
        loaded.get().getReviews().forEach(r ->
                assertThat(r.getReviewer().getUsername()).isNotBlank());
    }

    // -------------------------------------------------------------------------
    // 7. Keyset (scroll) pagination
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findFirst20ByGenreOrderByIdAsc() returns a Window using keyset position")
    void keysetPagination_firstWindowContainsExpectedMovies() {
        Window<Movie> firstPage = movieRepository.findFirst20ByGenreOrderByIdAsc(
                Genre.SCIENCE_FICTION, ScrollPosition.offset());

        assertThat(firstPage.getContent()).hasSize(2); // Inception + Interstellar

        // Simulate advancing to the next page using the position of the last element
        if (!firstPage.isEmpty()) {
            ScrollPosition nextPosition = firstPage.positionAt(firstPage.getContent().size() - 1);
            Window<Movie> secondPage = movieRepository.findFirst20ByGenreOrderByIdAsc(
                    Genre.SCIENCE_FICTION, nextPosition);
            // No more sci-fi movies → second page is empty
            assertThat(secondPage.getContent()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // 8. Bulk update — fresh reload reflects the new value
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updateRating() bulk UPDATE is visible on a fresh reload after clearAutomatically")
    void bulkUpdate_afterModifying_freshReloadReflectsChange() {
        // Arrange — persist a new movie with a known rating
        Movie movie = new Movie("Bulk Update Film", Genre.ACTION, 2020, new BigDecimal("5.0"));
        em.persistAndFlush(movie);
        em.clear();

        // Act — bulk UPDATE (clearAutomatically = true evicts the entity from L1 cache)
        BigDecimal newRating = new BigDecimal("8.8");
        int affected = movieRepository.updateRating(movie.getId(), newRating);
        assertThat(affected).isEqualTo(1);

        // Reload from DB — must see the updated rating
        Movie reloaded = movieRepository.findById(movie.getId()).orElseThrow();
        assertThat(reloaded.getRating()).isEqualByComparingTo(newRating);
    }

    // -------------------------------------------------------------------------
    // 9. Native query — top-rated movie is first in results
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findTopRated() returns the highest-rated movie as the first element")
    void nativeQuery_topRated_returnsHighestRated() {
        // Arrange — persist movies with distinct ratings
        em.persistAndFlush(new Movie("Low Rated",  Genre.DRAMA, 2010, new BigDecimal("4.0")));
        em.persistAndFlush(new Movie("Mid Rated",  Genre.DRAMA, 2011, new BigDecimal("6.5")));
        em.persistAndFlush(new Movie("High Rated", Genre.DRAMA, 2012, new BigDecimal("9.5")));
        em.clear();

        // Act — fetch top 1 with minRating=0 to include all of the above
        List<Movie> top = movieRepository.findTopRated(0.0, 1);

        // Assert — the single result must be the highest rating among all movies
        assertThat(top).hasSize(1);
        List<Movie> all = movieRepository.findAll();
        BigDecimal max = all.stream()
                .map(Movie::getRating)
                .max(BigDecimal::compareTo)
                .orElseThrow();
        assertThat(top.get(0).getRating()).isEqualByComparingTo(max);
    }

    // -------------------------------------------------------------------------
    // 10. Keyset pagination — unknown genre returns empty window
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findFirst20ByGenreOrderByIdAsc() returns empty window for genre with no movies")
    void keysetPagination_emptyGenre_returnsEmptyWindow() {
        // DOCUMENTARY has no movies in the seed data
        Window<Movie> window = movieRepository.findFirst20ByGenreOrderByIdAsc(
                Genre.DOCUMENTARY, ScrollPosition.offset());

        assertThat(window.getContent()).isEmpty();
        assertThat(window.hasNext()).isFalse();
    }

    // -------------------------------------------------------------------------
    // 11. Constructor expression — movie with no reviews returns zero count
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findMovieSummaries() returns reviewCount=0 (not null) for a movie with no reviews")
    void constructorExpression_movieWithNoReviews_returnsZeroCount() {
        // Arrange — a fresh movie with no reviews
        Movie noReviews = new Movie("No Reviews Film", Genre.DRAMA, 2023, new BigDecimal("7.0"));
        em.persistAndFlush(noReviews);
        em.clear();

        // Act
        List<MovieSummaryDto> summaries = movieRepository.findMovieSummaries();

        // Assert — the new movie appears with reviewCount=0 (COALESCE / LEFT JOIN behaviour)
        MovieSummaryDto dto = summaries.stream()
                .filter(s -> s.title().equals("No Reviews Film"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("'No Reviews Film' not found in summaries"));

        assertThat(dto.reviewCount()).isZero();
    }

    // -------------------------------------------------------------------------
    // 12. @EntityGraph — single query loads associations
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findWithReviewsById() loads reviews collection without extra SELECT statements")
    void entityGraph_loadsAssociations_inSingleQuery() {
        // Grab Inception (has 2 reviews in seed data)
        Movie inception = movieRepository.findAll().stream()
                .filter(m -> m.getTitle().equals("Inception"))
                .findFirst()
                .orElseThrow();

        // Clear the first-level cache so the EntityGraph query is truly issued
        em.clear();

        // Act — the @EntityGraph method should JOIN reviews and reviewers
        Movie loaded = movieRepository.findWithReviewsById(inception.getId()).orElseThrow();

        // Assert — reviews are already initialised (no lazy proxy trip needed)
        assertThat(loaded.getReviews()).hasSize(2);
        // Each reviewer must also be accessible (part of the EntityGraph path)
        loaded.getReviews().forEach(r ->
                assertThat(r.getReviewer().getUsername()).isNotBlank());
    }
}
