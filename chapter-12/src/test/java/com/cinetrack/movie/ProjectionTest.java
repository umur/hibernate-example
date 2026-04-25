package com.cinetrack.movie;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.review.Review;
import com.cinetrack.review.ReviewRepository;
import com.cinetrack.user.AppUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for all projection and DTO strategies in Chapter 12.
 *
 * <h2>Test structure</h2>
 * <ul>
 *   <li>Closed interface projection — verifies only the projected columns are
 *       accessible and the proxy correctly delegates to entity data.</li>
 *   <li>Open interface projection (SpEL) — verifies the derived expression is
 *       evaluated correctly at runtime.</li>
 *   <li>Class-based DTO (constructor expression) — verifies aggregates are
 *       computed correctly and the record fields are populated.</li>
 * </ul>
 *
 * <p>The {@link com.cinetrack.movie.MovieQueryService} Tuple-query test is
 * omitted from the {@code @DataJpaTest} slice because {@code MovieQueryService}
 * is a {@code @Service} bean, not a repository. A separate
 * {@code @SpringBootTest} test would be needed to include it; it is left as an
 * exercise in the chapter's "Going Further" section.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("test")
@Sql(scripts = "/sql/seed-movies.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/sql/cleanup.sql",
     executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class ProjectionTest extends AbstractIntegrationTest {

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private TestEntityManager em;

    // -------------------------------------------------------------------------
    // 1. Closed interface projection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByGenre() returns a closed interface proxy with only title and genre")
    void closedProjection_returnsOnlyProjectedColumns() {
        List<MovieTitleProjection> projections =
                movieRepository.findByGenre(Genre.SCIENCE_FICTION);

        assertThat(projections).hasSize(2); // Inception + Interstellar

        projections.forEach(p -> {
            // Both getters must be non-null and correct
            assertThat(p.getTitle()).isNotBlank();
            assertThat(p.getGenre()).isEqualTo("SCIENCE_FICTION");
        });

        // The returned object must be a JDK proxy, not a Movie entity
        projections.forEach(p ->
                assertThat(p).isNotInstanceOf(Movie.class));
    }

    @Test
    @DisplayName("Closed projection titles match expected movie titles for SCIENCE_FICTION genre")
    void closedProjection_titleValuesAreCorrect() {
        List<MovieTitleProjection> projections =
                movieRepository.findByGenre(Genre.SCIENCE_FICTION);

        assertThat(projections)
                .extracting(MovieTitleProjection::getTitle)
                .containsExactlyInAnyOrder("Inception", "Interstellar");
    }

    // -------------------------------------------------------------------------
    // 2. Open interface projection (SpEL)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Open projection getGenreName() returns the enum constant name via SpEL")
    void openProjection_spelExpressionEvaluatesCorrectly() {
        List<MovieWithReviewerCount> projections =
                movieRepository.findProjectedByGenre(Genre.ACTION);

        assertThat(projections).hasSize(1); // The Dark Knight
        MovieWithReviewerCount p = projections.get(0);

        assertThat(p.getTitle()).isEqualTo("The Dark Knight");
        // @Value("#{target.genre.name()}") must yield the enum name string
        assertThat(p.getGenreName()).isEqualTo("ACTION");
    }

    // -------------------------------------------------------------------------
    // 3. Class-based DTO — constructor expression with aggregation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findMovieStats() populates all record fields from JPQL aggregation")
    void dtoProjection_constructorExpressionPopulatesAllFields() {
        List<MovieStats> stats = movieRepository.findMovieStats();

        assertThat(stats).isNotEmpty();

        // Every record must have a non-null title and non-negative counts
        stats.forEach(s -> {
            assertThat(s.movieId()).isPositive();
            assertThat(s.title()).isNotBlank();
            assertThat(s.reviewCount()).isGreaterThanOrEqualTo(0L);
            assertThat(s.avgRating()).isGreaterThanOrEqualTo(0.0);
        });
    }

    @Test
    @DisplayName("findMovieStats() computes correct avg and count for Inception")
    void dtoProjection_inceptionAggregatesAreCorrect() {
        List<MovieStats> stats = movieRepository.findMovieStats();

        MovieStats inception = stats.stream()
                .filter(s -> s.title().equals("Inception"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Inception not found in stats"));

        // alice=9, bob=8 → avg 8.5, count 2
        assertThat(inception.reviewCount()).isEqualTo(2L);
        assertThat(inception.avgRating()).isEqualTo(8.5);
    }

    @Test
    @DisplayName("findMovieStats() returns zero reviewCount for movies with no reviews")
    void dtoProjection_movieWithNoReviewsHasZeroCount() {
        List<MovieStats> stats = movieRepository.findMovieStats();

        // Interstellar, Get Out, and The Grand Illusion have no reviews in seed data
        List<MovieStats> unreviewed = stats.stream()
                .filter(s -> s.reviewCount() == 0)
                .toList();

        assertThat(unreviewed).hasSize(3);
        assertThat(unreviewed)
                .extracting(MovieStats::title)
                .containsExactlyInAnyOrder("Interstellar", "Get Out", "The Grand Illusion");
    }

    @Test
    @DisplayName("findMovieStats() returns correct aggregates for The Dark Knight")
    void dtoProjection_darkKnightAggregatesAreCorrect() {
        List<MovieStats> stats = movieRepository.findMovieStats();

        MovieStats darkKnight = stats.stream()
                .filter(s -> s.title().equals("The Dark Knight"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("The Dark Knight not found in stats"));

        // charlie=10 → avg 10.0, count 1
        assertThat(darkKnight.reviewCount()).isEqualTo(1L);
        assertThat(darkKnight.avgRating()).isEqualTo(10.0);
    }

    // -------------------------------------------------------------------------
    // 5. Closed projection result is a proxy, not a Movie entity
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByGenre() result is a JDK proxy and not a Movie instance")
    void closedProjection_isProxyNotEntity() {
        List<MovieTitleProjection> projections =
                movieRepository.findByGenre(Genre.SCIENCE_FICTION);

        assertThat(projections).isNotEmpty();
        projections.forEach(p -> assertThat(p).isNotInstanceOf(Movie.class));
    }

    // -------------------------------------------------------------------------
    // 6. MovieStats for a movie with no reviews has count=0 and avgRating=0.0
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findMovieStats() returns count=0 and avgRating=0.0 for a movie with no reviews")
    void movieStats_noReviews_avgRatingIsZeroOrNull() {
        // Arrange — persist a brand-new movie with no reviews
        Movie fresh = new Movie("No Reviews At All", Genre.ANIMATION, 2024, new BigDecimal("7.5"));
        em.persistAndFlush(fresh);
        em.clear();

        // Act
        List<MovieStats> stats = movieRepository.findMovieStats();

        MovieStats result = stats.stream()
                .filter(s -> s.title().equals("No Reviews At All"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("'No Reviews At All' not found in stats"));

        // Assert — COALESCE(AVG(r.rating), 0.0) and COUNT over empty set = 0
        assertThat(result.reviewCount()).isZero();
        assertThat(result.avgRating()).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // 7. Three movies each have their own distinct stats entry
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findMovieStats() returns distinct stats for each of three newly saved movies")
    void movieStats_threeMovies_eachHasOwnStats() {
        // Arrange — three movies with 1, 2, and 3 reviews respectively
        AppUser reviewer = em.persistAndFlush(new AppUser("stats_user", "statsuser@example.com"));

        Movie m1 = em.persistAndFlush(new Movie("Stats Movie A", Genre.COMEDY, 2020, new BigDecimal("6.0")));
        Movie m2 = em.persistAndFlush(new Movie("Stats Movie B", Genre.COMEDY, 2021, new BigDecimal("7.0")));
        Movie m3 = em.persistAndFlush(new Movie("Stats Movie C", Genre.COMEDY, 2022, new BigDecimal("8.0")));

        reviewRepository.saveAndFlush(new Review(m1, reviewer, "Good",      6));

        reviewRepository.saveAndFlush(new Review(m2, reviewer, "Better",    7));
        AppUser reviewer2 = em.persistAndFlush(new AppUser("stats_user2", "statsuser2@example.com"));
        reviewRepository.saveAndFlush(new Review(m2, reviewer2, "Also good", 8));

        AppUser reviewer3 = em.persistAndFlush(new AppUser("stats_user3", "statsuser3@example.com"));
        AppUser reviewer4 = em.persistAndFlush(new AppUser("stats_user4", "statsuser4@example.com"));
        AppUser reviewer5 = em.persistAndFlush(new AppUser("stats_user5", "statsuser5@example.com"));
        reviewRepository.saveAndFlush(new Review(m3, reviewer3, "r1", 7));
        reviewRepository.saveAndFlush(new Review(m3, reviewer4, "r2", 8));
        reviewRepository.saveAndFlush(new Review(m3, reviewer5, "r3", 9));

        em.clear();

        // Act
        List<MovieStats> stats = movieRepository.findMovieStats();

        // Assert — each movie has its own entry with the correct count
        MovieStats s1 = stats.stream().filter(s -> s.title().equals("Stats Movie A")).findFirst().orElseThrow();
        MovieStats s2 = stats.stream().filter(s -> s.title().equals("Stats Movie B")).findFirst().orElseThrow();
        MovieStats s3 = stats.stream().filter(s -> s.title().equals("Stats Movie C")).findFirst().orElseThrow();

        assertThat(s1.reviewCount()).isEqualTo(1L);
        assertThat(s2.reviewCount()).isEqualTo(2L);
        assertThat(s3.reviewCount()).isEqualTo(3L);
    }

    // -------------------------------------------------------------------------
    // 8. Open projection SpEL — getGenreName() returns the enum constant name
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Open projection getGenreName() returns the genre enum name via SpEL")
    void openProjection_spelExpression_returnsCorrectString() {
        // Arrange — save a movie with a known genre
        Movie movie = new Movie("SpEL Test Film", Genre.THRILLER, 2021, new BigDecimal("7.2"));
        em.persistAndFlush(movie);
        em.clear();

        // Act — findProjectedByGenre uses the open projection (MovieWithReviewerCount)
        List<MovieWithReviewerCount> projections =
                movieRepository.findProjectedByGenre(Genre.THRILLER);

        // Assert — must contain our movie and the SpEL expression must yield "THRILLER"
        assertThat(projections).isNotEmpty();
        MovieWithReviewerCount result = projections.stream()
                .filter(p -> p.getTitle().equals("SpEL Test Film"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("'SpEL Test Film' not found in projections"));

        assertThat(result.getGenreName()).isEqualTo("THRILLER");
    }
}
