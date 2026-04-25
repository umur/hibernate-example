package com.cinetrack.movie;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.review.Review;
import com.cinetrack.review.ReviewRepository;
import com.cinetrack.user.AppUser;
import com.cinetrack.user.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.LazyInitializationException;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Chapter 15 — Fetch Strategies.
 *
 * Runs as a full {@code @SpringBootTest} against PostgreSQL 16 (via Testcontainers).
 * Hibernate statistics are enabled so we can assert exact SQL query counts,
 * making fetch-strategy behaviour observable and verifiable.
 *
 * <p>Test inventory:
 * <ol>
 *   <li>JOIN FETCH — single SQL query for movies + reviews</li>
 *   <li>@BatchSize — limits N+1 to ⌈N/batchSize⌉ + 1 queries</li>
 *   <li>LazyInitializationException — accessing lazy collection on detached entity</li>
 *   <li>@EntityGraph — findByRatingGreaterThan loads reviews in one query</li>
 *   <li>getMovieSummaries — no collection load, zero secondary queries</li>
 * </ol>
 */
@SpringBootTest
@DisplayName("Chapter 15 — Fetch Strategies")
class FetchStrategyTest extends AbstractIntegrationTest {

    @Autowired MovieRepository movieRepository;
    @Autowired ReviewRepository reviewRepository;
    @Autowired AppUserRepository userRepository;
    @Autowired Statistics hibernateStatistics;
    @Autowired PlatformTransactionManager txManager;

    @PersistenceContext
    EntityManager em;

    private TransactionTemplate tx;
    private AppUser alice;
    private AppUser bob;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);

        // Clean state
        tx.executeWithoutResult(s -> {
            reviewRepository.deleteAllInBatch();
            movieRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
        });

        tx.executeWithoutResult(s -> {
            alice = userRepository.save(new AppUser("alice", "alice@example.com"));
            bob   = userRepository.save(new AppUser("bob",   "bob@example.com"));
        });

        // Reset statistics counters after setup queries
        hibernateStatistics.clear();
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private List<Movie> persistMoviesWithReviews(int movieCount, int reviewsEach,
                                                  Genre genre, BigDecimal rating) {
        return tx.execute(s -> {
            List<Movie> movies = new ArrayList<>();
            for (int m = 0; m < movieCount; m++) {
                Movie movie = movieRepository.save(new Movie("Movie " + m, genre, rating));
                for (int r = 0; r < reviewsEach; r++) {
                    AppUser reviewer = (r % 2 == 0) ? alice : bob;
                    reviewRepository.save(new Review(movie, reviewer, "Review " + r, (r % 5) + 1));
                }
                movies.add(movie);
            }
            return movies;
        });
    }

    // ------------------------------------------------------------------
    // Test 1: JOIN FETCH — single SQL query
    // ------------------------------------------------------------------

    @Test
    @DisplayName("JOIN FETCH — loads movies with reviews in exactly 1 SQL query")
    void joinFetch_singleQuery() {
        persistMoviesWithReviews(5, 3, Genre.ACTION, BigDecimal.valueOf(7.5));

        hibernateStatistics.clear();

        List<Movie> movies = tx.execute(s ->
                movieRepository.findByGenreWithReviews(Genre.ACTION)
        );

        long queryCount = hibernateStatistics.getQueryExecutionCount();

        assertThat(movies).hasSize(5);
        movies.forEach(m -> assertThat(m.getReviews()).hasSize(3));

        assertThat(queryCount)
                .as("JOIN FETCH must load movies + reviews in a single SQL statement")
                .isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // Test 2: @BatchSize — limits N+1 queries
    // ------------------------------------------------------------------

    @Test
    @DisplayName("@BatchSize(25) — accessing reviews for 30 movies issues ⌈30/25⌉ + 1 = 3 queries")
    void batchSize_limitsNPlusOne() {
        persistMoviesWithReviews(30, 2, Genre.DRAMA, BigDecimal.valueOf(6.0));

        hibernateStatistics.clear();

        tx.executeWithoutResult(s -> {
            // 1 query: SELECT * FROM movies
            List<Movie> movies = movieRepository.findAll();
            assertThat(movies).hasSize(30);

            long afterLoad = hibernateStatistics.getQueryExecutionCount();
            assertThat(afterLoad).isEqualTo(1L);

            // Access reviews — triggers batch initialisation
            movies.forEach(m -> m.getReviews().size());

            long afterCollectionAccess = hibernateStatistics.getQueryExecutionCount();

            // 1 (initial) + ⌈30/25⌉ = 1 + 2 = 3 total
            int expectedBatches = (int) Math.ceil(30.0 / 25);
            assertThat(afterCollectionAccess)
                    .as("@BatchSize(25) should issue at most %d batch queries for 30 movies",
                            expectedBatches)
                    .isLessThanOrEqualTo(1L + expectedBatches);
        });
    }

    // ------------------------------------------------------------------
    // Test 3: LazyInitializationException outside session
    // ------------------------------------------------------------------

    @Test
    @DisplayName("LazyInitializationException — accessing lazy collection on detached entity throws")
    void lazyCollection_detachedEntity_throwsLazyInitializationException() {
        Long movieId = tx.execute(s -> {
            Movie movie = movieRepository.save(
                    new Movie("Detached Movie", Genre.THRILLER, BigDecimal.valueOf(8.0)));
            reviewRepository.save(new Review(movie, alice, "Great thriller", 5));
            return movie.getId();
        });

        // Load outside a transaction — entity returned is detached (no open Session)
        Movie detached = movieRepository.findById(movieId).orElseThrow();

        // Accessing the lazy proxy on a detached entity throws LazyInitializationException
        assertThatThrownBy(() -> detached.getReviews().size())
                .isInstanceOf(LazyInitializationException.class)
                .hasMessageContaining("reviews");
    }

    // ------------------------------------------------------------------
    // Test 4: @EntityGraph via derived finder
    // ------------------------------------------------------------------

    @Test
    @DisplayName("@EntityGraph — findByRatingGreaterThan loads reviews in a single SQL query")
    void entityGraph_singleQuery() {
        persistMoviesWithReviews(4, 2, Genre.COMEDY, BigDecimal.valueOf(8.0));
        tx.executeWithoutResult(s ->
                movieRepository.save(new Movie("Low rated", Genre.COMEDY, BigDecimal.valueOf(4.0)))
        );

        hibernateStatistics.clear();

        List<Movie> movies = tx.execute(s ->
                movieRepository.findByRatingGreaterThan(BigDecimal.valueOf(7.0))
        );

        long queryCount = hibernateStatistics.getQueryExecutionCount();

        assertThat(movies).hasSize(4);
        movies.forEach(m -> assertThat(m.getReviews()).hasSize(2));

        assertThat(queryCount)
                .as("@EntityGraph should produce a single JOIN FETCH query")
                .isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // Test 6: lazy proxy — not initialized before first access
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Lazy proxy — reviews collection is not initialized until first access within session")
    void lazyProxy_isNotInitializedBeforeAccess() {
        Long movieId = tx.execute(s -> {
            Movie movie = movieRepository.save(new Movie("Lazy Proxy Movie", Genre.ACTION, BigDecimal.valueOf(7.0)));
            reviewRepository.save(new Review(movie, alice, "Good", 4));
            return movie.getId();
        });

        tx.executeWithoutResult(s -> {
            Movie movie = movieRepository.findById(movieId).orElseThrow();

            // Collection proxy exists but has not been touched — must NOT be initialized
            assertThat(org.hibernate.Hibernate.isInitialized(movie.getReviews()))
                    .as("reviews collection must not be initialized before first access")
                    .isFalse();

            // Force initialization by accessing the collection
            int size = movie.getReviews().size();
            assertThat(size).isGreaterThan(0);

            // Now the proxy must be initialized
            assertThat(org.hibernate.Hibernate.isInitialized(movie.getReviews()))
                    .as("reviews collection must be initialized after size() access")
                    .isTrue();
        });
    }

    // ------------------------------------------------------------------
    // Test 7: @BatchSize with 30 movies — fewer than 30 queries
    // ------------------------------------------------------------------

    @Test
    @DisplayName("@BatchSize(25) — 30 movies produce fewer than 30 collection-load queries")
    void batchSize_30movies_producesFewer30Queries() {
        persistMoviesWithReviews(30, 1, Genre.HORROR, BigDecimal.valueOf(5.5));

        hibernateStatistics.clear();

        tx.executeWithoutResult(s -> {
            List<Movie> movies = movieRepository.findAll();
            assertThat(movies).hasSize(30);

            // Access every movie's reviews collection — without batching this would fire 30 queries
            movies.forEach(m -> m.getReviews().size());
        });

        long queryCount = hibernateStatistics.getQueryExecutionCount();

        assertThat(queryCount)
                .as("@BatchSize(25) must produce fewer than 30 queries for 30 movies (got %d)", queryCount)
                .isLessThan(30);
    }

    // ------------------------------------------------------------------
    // Test 8: @EntityGraph — loads reviews but not reviewer sub-associations
    // ------------------------------------------------------------------

    @Test
    @DisplayName("@EntityGraph(Movie.withReviews) — reviews initialized, reviewer proxy is NOT initialized")
    void entityGraph_loadsSpecifiedAssociations_notOthers() {
        tx.executeWithoutResult(s -> {
            Movie movie = movieRepository.save(new Movie("EntityGraph Movie", Genre.DRAMA, BigDecimal.valueOf(8.5)));
            reviewRepository.save(new Review(movie, alice, "Excellent", 5));
        });

        hibernateStatistics.clear();

        // findByRatingGreaterThan uses @EntityGraph("Movie.withReviews") — fetches reviews only
        List<Movie> movies = tx.execute(s ->
                movieRepository.findByRatingGreaterThan(BigDecimal.valueOf(8.0))
        );

        assertThat(movies).isNotEmpty();
        Movie movie = movies.get(0);

        // reviews must be initialized because the entity graph includes them
        assertThat(org.hibernate.Hibernate.isInitialized(movie.getReviews()))
                .as("reviews must be initialized by @EntityGraph")
                .isTrue();

        // The graph only specifies 'reviews', NOT 'reviews.reviewer', so each
        // Review's reviewer association should remain an uninitialized proxy
        assertThat(movie.getReviews()).isNotEmpty();
        com.cinetrack.review.Review firstReview = movie.getReviews().get(0);
        assertThat(org.hibernate.Hibernate.isInitialized(firstReview.getReviewer()))
                .as("reviewer proxy must NOT be initialized — it is outside the entity graph")
                .isFalse();
    }

    // ------------------------------------------------------------------
    // Test 9: LazyInitializationException outside session
    // ------------------------------------------------------------------

    @Test
    @DisplayName("LazyInitializationException — accessing reviews on detached movie outside session throws")
    void lazyInitializationException_outsideSession_throws() {
        Long movieId = tx.execute(s -> {
            Movie movie = movieRepository.save(new Movie("LIE Movie", Genre.THRILLER, BigDecimal.valueOf(6.5)));
            reviewRepository.save(new Review(movie, alice, "Decent", 3));
            return movie.getId();
        });

        // Load outside of any transaction — the returned entity is detached (session is closed)
        Movie detached = movieRepository.findById(movieId).orElseThrow();

        // Accessing the lazy collection with no active session must throw
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> detached.getReviews().size())
                .isInstanceOf(org.hibernate.LazyInitializationException.class)
                .hasMessageContaining("reviews");
    }

    // ------------------------------------------------------------------
    // Test 10: JOIN FETCH — single query verified by Hibernate statistics
    // ------------------------------------------------------------------

    @Test
    @DisplayName("JOIN FETCH — findByGenreWithReviews fires exactly 1 SQL query (statistics verified)")
    void joinFetch_singleQuery_verifiedByStatistics() {
        persistMoviesWithReviews(4, 3, Genre.SCI_FI, BigDecimal.valueOf(8.0));

        hibernateStatistics.clear();

        List<Movie> movies = tx.execute(s ->
                movieRepository.findByGenreWithReviews(Genre.SCI_FI)
        );

        long queryCount = hibernateStatistics.getQueryExecutionCount();

        assertThat(movies).hasSize(4);
        movies.forEach(m -> assertThat(m.getReviews()).hasSize(3));
        assertThat(queryCount)
                .as("JOIN FETCH must produce exactly 1 SQL query regardless of collection size")
                .isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // Test 5: plain findAll — no collection queries fired
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Plain findAll — loads movies without touching the reviews collection")
    void findAll_noCollectionLoad() {
        persistMoviesWithReviews(3, 4, Genre.SCI_FI, BigDecimal.valueOf(7.0));

        hibernateStatistics.clear();

        // findAll() outside transaction — reviews are lazy proxies, never initialised
        List<Movie> summaries = movieRepository.findAll();

        long queryCount = hibernateStatistics.getQueryExecutionCount();
        long collectionLoads = hibernateStatistics.getCollectionLoadCount();

        assertThat(summaries).hasSize(3);
        assertThat(queryCount)
                .as("Plain findAll must not trigger any collection initialisation")
                .isEqualTo(1L);
        assertThat(collectionLoads)
                .as("No collection should have been loaded")
                .isZero();
    }
}
