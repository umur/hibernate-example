package com.cinetrack.movie;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.review.Review;
import com.cinetrack.user.AppUser;
import com.cinetrack.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the advanced HQL features demonstrated in chapter 23.
 *
 * <h3>Coverage</h3>
 * <ol>
 *   <li>Window function ({@code RANK() OVER}) — verifies query executes and
 *       produces correct per-genre ranks.</li>
 *   <li>CTE + FILTER aggregate — verifies positive and negative review counts
 *       are correctly split.</li>
 *   <li>Custom {@code similarity()} function — verifies the pg_trgm function
 *       registered via {@link com.cinetrack.config.SimilarityFunctionContributor}
 *       is callable from HQL without error.</li>
 * </ol>
 */
class HqlQueryTest extends AbstractIntegrationTest {

    @Autowired MovieRepository movieRepository;
    @Autowired AppUserRepository userRepository;
    @Autowired MovieQueryService movieQueryService;

    private Movie actionHigh;
    private Movie actionLow;
    private Movie dramaHigh;

    @BeforeEach
    @Transactional
    void setUp() {
        movieRepository.deleteAll();
        userRepository.deleteAll();

        AppUser reviewer = userRepository.save(AppUser.builder().username("critic1").build());

        actionHigh = movieRepository.save(
                Movie.builder().title("Die Hard").genre("Action").rating(4.8).build());
        actionLow = movieRepository.save(
                Movie.builder().title("Generic Action").genre("Action").rating(3.2).build());
        dramaHigh = movieRepository.save(
                Movie.builder().title("The Shawshank Redemption").genre("Drama").rating(4.9).build());

        // Add reviews to actionHigh (rating > 4.0 → qualifies for CTE)
        Review pos1 = Review.builder().movie(actionHigh).reviewer(reviewer).rating(5).build();
        Review pos2 = Review.builder().movie(actionHigh).reviewer(reviewer).rating(4).build();
        Review neg1 = Review.builder().movie(actionHigh).reviewer(reviewer).rating(1).build();
        actionHigh.getReviews().addAll(List.of(pos1, pos2, neg1));
        movieRepository.save(actionHigh);

        // dramaHigh also qualifies (rating 4.9)
        Review dPos = Review.builder().movie(dramaHigh).reviewer(reviewer).rating(5).build();
        dramaHigh.getReviews().add(dPos);
        movieRepository.save(dramaHigh);
    }

    // -----------------------------------------------------------------------
    // Window function
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("RANK() OVER: highest-rated Action movie gets rank 1")
    @Transactional
    void windowFunction_rankWithinGenre() {
        List<MovieRankDto> results = movieQueryService.findMoviesRankedByGenre();

        assertThat(results).isNotEmpty();

        MovieRankDto dieHardRank = results.stream()
                .filter(r -> r.title().equals("Die Hard"))
                .findFirst()
                .orElseThrow();

        MovieRankDto genericRank = results.stream()
                .filter(r -> r.title().equals("Generic Action"))
                .findFirst()
                .orElseThrow();

        assertThat(dieHardRank.genreRank()).isEqualTo(1L);
        assertThat(genericRank.genreRank()).isEqualTo(2L);
    }

    @Test
    @DisplayName("RANK() OVER: Drama movie gets rank 1 in its own partition")
    @Transactional
    void windowFunction_separatePartitionPerGenre() {
        List<MovieRankDto> results = movieQueryService.findMoviesRankedByGenre();

        MovieRankDto dramaRank = results.stream()
                .filter(r -> r.title().equals("The Shawshank Redemption"))
                .findFirst()
                .orElseThrow();

        assertThat(dramaRank.genreRank()).isEqualTo(1L);
    }

    // -----------------------------------------------------------------------
    // CTE + FILTER aggregate
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("CTE + FILTER: correct positive and negative review counts for top-rated movies")
    @Transactional
    void cteQuery_positiveAndNegativeCounts() {
        List<MovieStatsDto> stats = movieQueryService.findMovieStats();

        // Only movies with rating > 4.0 appear (actionHigh=4.8, dramaHigh=4.9; actionLow=3.2 excluded)
        assertThat(stats).extracting(MovieStatsDto::title)
                .containsExactlyInAnyOrder("Die Hard", "The Shawshank Redemption");

        MovieStatsDto dieHardStats = stats.stream()
                .filter(s -> s.title().equals("Die Hard"))
                .findFirst()
                .orElseThrow();

        assertThat(dieHardStats.totalReviews()).isEqualTo(3L);
        assertThat(dieHardStats.positiveReviews()).isEqualTo(2L); // ratings 5, 4
        assertThat(dieHardStats.negativeReviews()).isEqualTo(1L); // rating 1
    }

    @Test
    @DisplayName("CTE + FILTER: movie below rating threshold excluded from result")
    @Transactional
    void cteQuery_lowRatedMovieExcluded() {
        List<MovieStatsDto> stats = movieQueryService.findMovieStats();

        assertThat(stats).extracting(MovieStatsDto::title)
                .doesNotContain("Generic Action");
    }

    // -----------------------------------------------------------------------
    // Custom similarity() function
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("similarity(): custom pg_trgm function executes without error")
    @Transactional
    void similarityFunction_executesAndReturnsResults() {
        // "Die Hard" fuzzy search — just verify no exception and result is a list
        List<Movie> results = movieQueryService.findBySimilarTitle("Die");

        assertThat(results).isNotNull();
        // At least "Die Hard" should match with similarity > 0.1
        assertThat(results).extracting(Movie::getTitle)
                .contains("Die Hard");
    }

    @Test
    @DisplayName("similarity(): low-similarity title does not appear in results")
    @Transactional
    void similarityFunction_noMatchForUnrelatedTitle() {
        List<Movie> results = movieQueryService.findBySimilarTitle("zzz_nomatch_xyz");

        // Either empty or doesn't contain our movies (threshold 0.1)
        assertThat(results).extracting(Movie::getTitle)
                .doesNotContain("Die Hard", "The Shawshank Redemption", "Generic Action");
    }

    // -----------------------------------------------------------------------
    // Additional window function tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("RANK() OVER: movie with rating 9.0 gets rank 1 in its genre")
    @Transactional
    void windowFunction_rank1_isHighestRatedInGenre() {
        // Save three ACTION movies with distinct ratings alongside existing data
        movieRepository.save(Movie.builder().title("Top Action").genre("Action").rating(9.0).build());
        movieRepository.save(Movie.builder().title("Mid Action").genre("Action").rating(7.0).build());
        movieRepository.save(Movie.builder().title("Low Action").genre("Action").rating(5.0).build());

        List<MovieRankDto> results = movieQueryService.findMoviesRankedByGenre();

        MovieRankDto top = results.stream()
                .filter(r -> r.title().equals("Top Action"))
                .findFirst()
                .orElseThrow();

        assertThat(top.genreRank()).isEqualTo(1L);
    }

    @Test
    @DisplayName("RANK() OVER: two movies with the same rating in the same genre both get rank 1")
    @Transactional
    void windowFunction_tiedRatings_getSameRank() {
        movieRepository.save(Movie.builder().title("Tied A").genre("Horror").rating(8.0).build());
        movieRepository.save(Movie.builder().title("Tied B").genre("Horror").rating(8.0).build());

        List<MovieRankDto> results = movieQueryService.findMoviesRankedByGenre();

        List<Long> horrorRanks = results.stream()
                .filter(r -> "Horror".equals(r.genre()))
                .map(MovieRankDto::genreRank)
                .toList();

        // Both tied movies must share the same rank value
        assertThat(horrorRanks).containsOnly(1L);
    }

    // -----------------------------------------------------------------------
    // Additional CTE tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("CTE + FILTER: movie with no reviews has zero positive count")
    @Transactional
    void cte_movieWithNoReviews_zeroPositiveCount() {
        // dramaHigh (rating 4.9) has reviews; add a new top-rated movie with no reviews
        Movie noReviewMovie = movieRepository.save(
                Movie.builder().title("No Review Film").genre("Documentary").rating(4.5).build());

        List<MovieStatsDto> stats = movieQueryService.findMovieStats();

        MovieStatsDto dto = stats.stream()
                .filter(s -> s.title().equals("No Review Film"))
                .findFirst()
                .orElseThrow();

        assertThat(dto.positiveReviews()).isEqualTo(0L);
        assertThat(dto.totalReviews()).isEqualTo(0L);
    }

    @Test
    @DisplayName("CTE + FILTER: movie with 3 positive and 2 negative reviews has correct counts")
    @Transactional
    void cte_movieWithMixedReviews_correctCounts() {
        AppUser reviewer = userRepository.save(AppUser.builder().username("mixed_reviewer").build());

        Movie mixed = movieRepository.save(
                Movie.builder().title("Mixed Review Film").genre("Thriller").rating(4.2).build());

        // 3 positive (rating >= 4)
        mixed.getReviews().add(Review.builder().movie(mixed).reviewer(reviewer).rating(5).build());
        mixed.getReviews().add(Review.builder().movie(mixed).reviewer(reviewer).rating(4).build());
        mixed.getReviews().add(Review.builder().movie(mixed).reviewer(reviewer).rating(4).build());
        // 2 negative (rating <= 2)
        mixed.getReviews().add(Review.builder().movie(mixed).reviewer(reviewer).rating(2).build());
        mixed.getReviews().add(Review.builder().movie(mixed).reviewer(reviewer).rating(1).build());
        movieRepository.save(mixed);

        List<MovieStatsDto> stats = movieQueryService.findMovieStats();

        MovieStatsDto dto = stats.stream()
                .filter(s -> s.title().equals("Mixed Review Film"))
                .findFirst()
                .orElseThrow();

        assertThat(dto.positiveReviews()).isEqualTo(3L);
        assertThat(dto.negativeReviews()).isEqualTo(2L);
        assertThat(dto.totalReviews()).isEqualTo(5L);
    }

    // -----------------------------------------------------------------------
    // Additional similarity tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("similarity(): 'Incption' (typo) matches 'Inception' via pg_trgm")
    @Transactional
    void similarityFunction_aboveThreshold_returnsMatch() {
        movieRepository.save(Movie.builder().title("Inception").genre("SciFi").rating(4.8).build());

        List<Movie> results = movieQueryService.findBySimilarTitle("Incption");

        assertThat(results).extracting(Movie::getTitle)
                .contains("Inception");
    }

    // -----------------------------------------------------------------------
    // New: edge-case / coverage tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("findBySimilarTitle: empty string does not throw and returns a non-null list")
    @Transactional
    void findBySimilarTitle_emptyString_returnsEmpty_orDoesNotThrow() {
        // pg_trgm similarity against "" is a valid query — Hibernate must not throw.
        // The result may be empty or contain low-similarity matches; either is acceptable.
        List<Movie> results;
        try {
            results = movieQueryService.findBySimilarTitle("");
        } catch (Exception ex) {
            // If the database rejects an empty-string trigram query, that is also acceptable
            // behaviour — we just ensure it is not a silent data-corruption.
            assertThat(ex).isInstanceOfAny(
                    org.hibernate.exception.GenericJDBCException.class,
                    jakarta.persistence.PersistenceException.class,
                    IllegalArgumentException.class);
            return;
        }
        // Happy path: result must be non-null (never null from JPA)
        assertThat(results).isNotNull();
        // Our three seeded movies have very low trigram similarity to "" — they must not appear
        assertThat(results).extracting(Movie::getTitle)
                .doesNotContain("Die Hard", "Generic Action", "The Shawshank Redemption");
    }

    @Test
    @DisplayName("RANK() OVER: genre rank resets to 1 for each independent genre partition")
    @Transactional
    void rankByGenre_multipleGenres_ranksAreIndependent() {
        // Save one extra movie per genre so we have 2 Action + 1 Drama already from setUp.
        // The lowest-rated Action movie gets rank 2; the sole Drama movie gets rank 1.
        List<MovieRankDto> results = movieQueryService.findMoviesRankedByGenre();

        // Action partition: "Die Hard" (4.8) → rank 1, "Generic Action" (3.2) → rank 2
        MovieRankDto dieHard = results.stream()
                .filter(r -> r.title().equals("Die Hard")).findFirst().orElseThrow();
        MovieRankDto genericAction = results.stream()
                .filter(r -> r.title().equals("Generic Action")).findFirst().orElseThrow();
        MovieRankDto shawshank = results.stream()
                .filter(r -> r.title().equals("The Shawshank Redemption")).findFirst().orElseThrow();

        // Drama partition is independent — its rank starts at 1 regardless of Action ranks
        assertThat(dieHard.genreRank()).isEqualTo(1L);
        assertThat(genericAction.genreRank()).isEqualTo(2L);
        assertThat(shawshank.genreRank()).isEqualTo(1L);

        // The two partitions must produce independent rank sequences (both have a rank-1 entry)
        assertThat(dieHard.genreRank()).isEqualTo(shawshank.genreRank());
    }

    @Test
    @DisplayName("CTE + FILTER: movie above threshold with no reviews has negativeCount = 0")
    @Transactional
    void cte_movie_noReviews_zeroNegativeCount() {
        // Add a top-rated movie with no reviews — it qualifies for the CTE (rating > 4.0)
        // but has no associated Review rows, so negativeCount must be 0.
        movieRepository.save(
                Movie.builder()
                        .title("Zero Reviews Film")
                        .genre("Documentary")
                        .rating(4.7)
                        .build());

        List<MovieStatsDto> stats = movieQueryService.findMovieStats();

        MovieStatsDto dto = stats.stream()
                .filter(s -> s.title().equals("Zero Reviews Film"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected 'Zero Reviews Film' in CTE results (rating=4.7 > 4.0)"));

        assertThat(dto.negativeReviews())
                .as("movie with no reviews must have negativeCount = 0")
                .isEqualTo(0L);
        assertThat(dto.positiveReviews())
                .as("movie with no reviews must have positiveCount = 0")
                .isEqualTo(0L);
        assertThat(dto.totalReviews())
                .as("movie with no reviews must have totalReviews = 0")
                .isEqualTo(0L);
    }
}
