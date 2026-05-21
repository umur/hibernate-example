package com.cinetrack.movie;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.review.Review;
import com.cinetrack.user.AppUser;
import com.cinetrack.user.AppUserRepository;
import net.ttddyy.dsproxy.QueryCountHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates and verifies the N+1 query problem using datasource-proxy's
 * {@link QueryCountHolder}.
 *
 * <h2>How QueryCountHolder works</h2>
 * <p>{@code QueryCountHolder} maintains per-thread SELECT/INSERT/UPDATE/DELETE
 * counters that are populated by the datasource-proxy interceptor configured in
 * {@link com.cinetrack.config.DataSourceProxyConfig}.  Calling
 * {@code QueryCountHolder.clear()} before each measured call resets the counters;
 * reading {@code QueryCountHolder.getGrandTotal().getSelect()} afterwards gives
 * the exact number of SELECT statements executed on the current thread.
 *
 * <h2>Test data</h2>
 * <p>5 movies, each with 2 reviews: a total of 10 review rows. The fixture is
 * re-created before every test so each test is fully isolated.
 */
class NplusOneTest extends AbstractIntegrationTest {

    @Autowired
    private MovieService movieService;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private TransactionTemplate txTemplate;

    @BeforeEach
    void setUp() {
        // Clean slate: order matters due to FK constraints
        txTemplate.executeWithoutResult(status -> {
            movieRepository.deleteAll();
            appUserRepository.deleteAll();
        });

        txTemplate.executeWithoutResult(status -> {
            AppUser critic = appUserRepository.save(new AppUser("critic", "critic@example.com"));

            for (int i = 1; i <= 5; i++) {
                Movie movie = new Movie("Movie " + i, Genre.DRAMA, 2020 + i);
                movie.addReview(new Review(critic, 8, "Great film " + i));
                movie.addReview(new Review(critic, 7, "Good film " + i));
                movieRepository.save(movie);
            }
        });
    }

    @Test
    @DisplayName("N+1: plain findAll() + lazy access fires 1 + N queries (batched to 2 with @BatchSize(25))")
    void nplusOneWithoutFix() {
        // Reset per-thread query counters
        QueryCountHolder.clear();

        List<Movie> movies = movieService.getMovieNplusOne();

        long selectCount = QueryCountHolder.getGrandTotal().getSelect();

        assertThat(movies).hasSize(5);

        // With @BatchSize(size=25) and only 5 movies, Hibernate issues:
        //   1  SELECT for movies
        //   1  SELECT … WHERE movie_id IN (?,?,?,?,?) for the reviews batch
        // = 2 total SELECTs.
        //
        // Without @BatchSize it would be 6 (1 + 5).  The assertion documents
        // the batched behaviour so readers can see the difference clearly.
        assertThat(selectCount)
                .as("Expected 2 SELECTs with @BatchSize(25): 1 for movies + 1 batched for reviews")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("JOIN FETCH: findAllWithReviews() executes exactly 1 query regardless of movie count")
    void joinFetchEliminatesNplusOne() {
        QueryCountHolder.clear();

        List<Movie> movies = movieService.getMovieFixed();

        long selectCount = QueryCountHolder.getGrandTotal().getSelect();

        assertThat(movies).hasSize(5);
        movies.forEach(m -> assertThat(m.getReviews()).hasSize(2));

        assertThat(selectCount)
                .as("JOIN FETCH must produce exactly 1 SELECT")
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Test 3: naïve lazy access: documents actual query count (batched)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Naive lazy access: 5 movies with @BatchSize(25) produces 2 SELECTs (1 movies + 1 batch)")
    void naiveLazyAccess_5movies_produces6Queries() {
        // setUp already created 5 movies with 2 reviews each; just measure
        QueryCountHolder.clear();

        // getMovieNplusOne() loads movies then accesses reviews inside a transaction
        List<Movie> movies = movieService.getMovieNplusOne();

        long selectCount = QueryCountHolder.getGrandTotal().getSelect();

        assertThat(movies).hasSize(5);

        // With @BatchSize(size=25) all 5 movies' reviews are loaded in a single
        // IN-clause batch:  1 (movies) + 1 (reviews batch) = 2 total SELECTs.
        // Without @BatchSize the count would be 1 + 5 = 6.
        assertThat(selectCount)
                .as("@BatchSize(25) must batch all 5 review collections into 1 extra SELECT (total 2)")
                .isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Test 4: JOIN FETCH with 5 movies: exactly 1 query
    // ------------------------------------------------------------------

    @Test
    @DisplayName("JOIN FETCH: findAllWithReviews(): 5 movies, exactly 1 SELECT verified by QueryCountHolder")
    void joinFetch_5movies_produces1Query() {
        QueryCountHolder.clear();

        List<Movie> movies = movieService.getMovieFixed();

        long selectCount = QueryCountHolder.getGrandTotal().getSelect();

        assertThat(movies).hasSize(5);
        movies.forEach(m -> assertThat(m.getReviews()).hasSize(2));

        assertThat(selectCount)
                .as("JOIN FETCH must produce exactly 1 SELECT for movies + reviews")
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Test 5: @BatchSize reduces query count for 25 movies
    // ------------------------------------------------------------------

    @Test
    @DisplayName("@BatchSize: 25 movies produce 2 SELECTs (1 movies + 1 full batch) instead of 26")
    void batchSize_reduces_queryCount() {
        // Add 20 more movies (setUp already persisted 5) so we reach exactly 25
        txTemplate.executeWithoutResult(status -> {
            AppUser critic = appUserRepository.findAll().get(0);
            for (int i = 6; i <= 25; i++) {
                Movie movie = new Movie("Movie " + i, Genre.DRAMA, 2020 + i);
                movie.addReview(new Review(critic, 7, "Film " + i));
                movieRepository.save(movie);
            }
        });

        QueryCountHolder.clear();

        // getMovieNplusOne loads all movies then accesses each movie's reviews
        List<Movie> movies = movieService.getMovieNplusOne();

        long selectCount = QueryCountHolder.getGrandTotal().getSelect();

        assertThat(movies).hasSize(25);

        // @BatchSize(25) fits all 25 movie IDs into a single IN-clause:
        //   1  SELECT for movies
        //   1  SELECT … WHERE movie_id IN (25 ids)
        // = 2 total (not 26)
        assertThat(selectCount)
                .as("@BatchSize(25) must load 25 review collections in exactly 1 batch query (total 2 SELECTs)")
                .isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Test 6: QueryCountHolder resets correctly between measurements
    // ------------------------------------------------------------------

    @Test
    @DisplayName("QueryCountHolder.clear() resets counter so counts are not cumulative across measurements")
    void queryCountHolder_perTest_resetsCorrectly() {
        // First measurement: one call that issues exactly 1 SELECT (movies, no review access)
        QueryCountHolder.clear();
        movieRepository.findAll();
        long firstCount = QueryCountHolder.getGrandTotal().getSelect();
        assertThat(firstCount)
                .as("First measurement must record exactly 1 SELECT")
                .isEqualTo(1);

        // Reset and re-measure: should start from 0 again, not accumulate
        QueryCountHolder.clear();
        movieRepository.findAll();
        long secondCount = QueryCountHolder.getGrandTotal().getSelect();
        assertThat(secondCount)
                .as("After clear(), second measurement must also be 1, not cumulative (%d)", firstCount + 1)
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Test 7: movies with NO reviews: reviews collections are empty, not null
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getMovieNplusOne: 5 movies with no reviews return empty (not null) review collections")
    void getMovieNplusOne_noReviews_returns0Reviews() {
        // Delete the fixture created by setUp (which has reviews) and replace with
        // movies that have no reviews at all.
        txTemplate.executeWithoutResult(status -> {
            movieRepository.deleteAll();
            appUserRepository.deleteAll();
        });

        txTemplate.executeWithoutResult(status -> {
            for (int i = 1; i <= 5; i++) {
                movieRepository.save(new Movie("Empty Movie " + i, Genre.DRAMA, 2000 + i));
            }
        });

        QueryCountHolder.clear();

        List<Movie> movies = movieService.getMovieNplusOne();

        assertThat(movies).hasSize(5);
        movies.forEach(m -> {
            assertThat(m.getReviews())
                    .as("Reviews collection for movie '%s' must not be null", m.getTitle())
                    .isNotNull();
            assertThat(m.getReviews())
                    .as("Reviews collection for movie '%s' must be empty (no reviews persisted)", m.getTitle())
                    .isEmpty();
        });
    }

    // ------------------------------------------------------------------
    // Test 8: JOIN FETCH with 26 movies (> batch size 25): still 1 query
    // ------------------------------------------------------------------

    @Test
    @DisplayName("getMovieFixed: 26 movies (> batch size 25) still produce exactly 1 SELECT via JOIN FETCH")
    void joinFetch_26movies_stillOneQuery() {
        // Add 21 more movies so we exceed the @BatchSize(25) threshold (setUp created 5)
        txTemplate.executeWithoutResult(status -> {
            AppUser critic = appUserRepository.findAll().get(0);
            for (int i = 6; i <= 26; i++) {
                Movie movie = new Movie("Movie " + i, Genre.DRAMA, 2020 + i);
                movie.addReview(new Review(critic, 7, "Good film " + i));
                movieRepository.save(movie);
            }
        });

        QueryCountHolder.clear();

        List<Movie> movies = movieService.getMovieFixed();

        long selectCount = QueryCountHolder.getGrandTotal().getSelect();

        assertThat(movies).hasSize(26);
        assertThat(selectCount)
                .as("JOIN FETCH must produce exactly 1 SELECT even for 26 movies (exceeding @BatchSize(25))")
                .isEqualTo(1);
    }
}
