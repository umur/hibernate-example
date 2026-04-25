package com.cinetrack.filter;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.movie.ContentRating;
import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import com.cinetrack.review.Review;
import com.cinetrack.review.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for:
 * <ol>
 *   <li>Content-rating {@code @Filter} — unfiltered vs child-safe vs adult session</li>
 *   <li>Soft-delete via {@code @SQLDelete} + {@code @Where} — entity invisible after
 *       {@code delete()}, but underlying row still present</li>
 * </ol>
 */
class ContentRatingFilterTest extends AbstractIntegrationTest {

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ContentRatingService contentRatingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @Transactional
    void setUp() {
        movieRepository.deleteAll();
        movieRepository.saveAll(List.of(
                Movie.builder().title("Family Fun").genre("Comedy").contentRating(ContentRating.G).build(),
                Movie.builder().title("Teen Adventure").genre("Action").contentRating(ContentRating.PG_13).build(),
                Movie.builder().title("Thriller Night").genre("Thriller").contentRating(ContentRating.R).build(),
                Movie.builder().title("Adult Drama").genre("Drama").contentRating(ContentRating.NC_17).build()
        ));
    }

    // -----------------------------------------------------------------------
    // Filter tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Without filter: all non-deleted movies are returned regardless of rating")
    @Transactional
    void withoutFilter_allMoviesReturned() {
        List<Movie> movies = contentRatingService.findAllUnfiltered();

        assertThat(movies).hasSize(4);
        assertThat(movies).extracting(Movie::getContentRating)
                .containsExactlyInAnyOrder(
                        ContentRating.G,
                        ContentRating.PG_13,
                        ContentRating.R,
                        ContentRating.NC_17);
    }

    @Test
    @DisplayName("With filter (unverified user): only G/PG/PG-13 movies returned")
    @Transactional
    void withFilter_unverifiedUser_onlyFamilyRatings() {
        List<Movie> movies = contentRatingService.findForUser(false);

        assertThat(movies).hasSize(2)
                .extracting(Movie::getContentRating)
                .containsExactlyInAnyOrder(ContentRating.G, ContentRating.PG_13);
    }

    @Test
    @DisplayName("With filter (adult-verified user): all ratings up to NC-17 returned")
    @Transactional
    void withFilter_adultVerifiedUser_allRatingsReturned() {
        List<Movie> movies = contentRatingService.findForUser(true);

        assertThat(movies).hasSize(4);
    }

    // -----------------------------------------------------------------------
    // Soft-delete tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Soft delete: movie absent from JPA query but row still present in DB")
    @Transactional
    void softDelete_movieInvisibleViaJpa_butRowExistsInDb() {
        Movie movie = movieRepository.findAll().stream()
                .filter(m -> m.getTitle().equals("Family Fun"))
                .findFirst()
                .orElseThrow();
        Long movieId = movie.getId();

        movieRepository.delete(movie);
        movieRepository.flush();

        // JPA query honours @Where(clause = "deleted = false") — movie gone
        assertThat(movieRepository.findById(movieId)).isEmpty();
        assertThat(movieRepository.findAll())
                .extracting(Movie::getTitle)
                .doesNotContain("Family Fun");

        // Raw JDBC bypasses @Where — row is still there, just flagged
        Boolean deletedFlag = jdbcTemplate.queryForObject(
                "SELECT deleted FROM movies WHERE id = ?", Boolean.class, movieId);
        assertThat(deletedFlag).isTrue();
    }

    @Test
    @DisplayName("Soft delete: deleted_at timestamp is populated")
    @Transactional
    void softDelete_deletedAtIsSet() {
        Movie movie = movieRepository.findAll().getFirst();
        Long movieId = movie.getId();

        movieRepository.delete(movie);
        movieRepository.flush();

        Object deletedAt = jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM movies WHERE id = ?", Object.class, movieId);
        assertThat(deletedAt).isNotNull();
    }

    // -----------------------------------------------------------------------
    // Additional filter tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Without filter enabled: all ratings (G, PG-13, R) are returned")
    @Transactional
    void filter_notEnabled_returnsAllRatings() {
        // @BeforeEach already saved G, PG_13, R, NC_17 movies
        // findAllUnfiltered() does not enable the filter
        List<Movie> movies = contentRatingService.findAllUnfiltered();

        assertThat(movies).extracting(Movie::getContentRating)
                .containsExactlyInAnyOrder(
                        ContentRating.G,
                        ContentRating.PG_13,
                        ContentRating.R,
                        ContentRating.NC_17);
    }

    @Test
    @DisplayName("Soft delete: underlying row still exists in DB after JPA delete")
    @Transactional
    void softDelete_rowStillExistsInDb_afterJpaDelete() {
        Movie movie = movieRepository.findAll().stream()
                .filter(m -> m.getTitle().equals("Teen Adventure"))
                .findFirst()
                .orElseThrow();
        Long movieId = movie.getId();

        movieRepository.delete(movie);
        movieRepository.flush();

        // JPA is blind to the row via @Where
        assertThat(movieRepository.findById(movieId)).isEmpty();
        assertThat(movieRepository.findAll())
                .extracting(Movie::getTitle)
                .doesNotContain("Teen Adventure");

        // Raw JDBC: row must still be present
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movies WHERE id = ?", Integer.class, movieId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Soft delete: deleted_at column is non-null after delete")
    @Transactional
    void softDelete_deletedAt_notNull() {
        Movie movie = movieRepository.findAll().stream()
                .filter(m -> m.getTitle().equals("Adult Drama"))
                .findFirst()
                .orElseThrow();
        Long movieId = movie.getId();

        movieRepository.delete(movie);
        movieRepository.flush();

        Object deletedAt = jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM movies WHERE id = ?", Object.class, movieId);
        assertThat(deletedAt).isNotNull();
    }

    @Test
    @DisplayName("Filter with PG_13 parameter returns only G and PG_13; switching to R also includes R")
    @Transactional
    void filter_withDifferentParameter_returnsCorrectSubset() {
        // Child-safe: only G and PG_13
        List<Movie> childSafe = contentRatingService.findForUser(false);
        assertThat(childSafe).extracting(Movie::getContentRating)
                .containsExactlyInAnyOrder(ContentRating.G, ContentRating.PG_13);

        // Adult-verified: all four ratings
        List<Movie> adult = contentRatingService.findForUser(true);
        assertThat(adult).extracting(Movie::getContentRating)
                .contains(ContentRating.R, ContentRating.NC_17);
    }

    // -----------------------------------------------------------------------
    // ReviewNormalizationListener
    // -----------------------------------------------------------------------

    @Autowired
    private ReviewRepository reviewRepository;

    @Test
    @DisplayName("ReviewNormalizationListener: content with leading/trailing whitespace is trimmed on persist")
    @Transactional
    void reviewNormalizationListener_whitespaceContent_trimmed() {
        Movie movie = movieRepository.findAll().getFirst();

        // reviewer is nullable in the schema — no FK constraint violation
        Review review = Review.builder()
                .movie(movie)
                .content("   Great film!   ")
                .build();
        Review saved = reviewRepository.save(review);
        reviewRepository.flush();

        Review reloaded = reviewRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getContent()).isEqualTo("Great film!");
    }

    // -----------------------------------------------------------------------
    // AuditInterceptor tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AuditInterceptor.onSave: saving a Movie does not throw — interceptor fires on insert")
    @Transactional
    void auditInterceptor_onInsert_doesNotThrow() {
        // The AuditInterceptor is registered as a factory-scoped interceptor.
        // Simply saving a Movie causes onSave() to be called; we assert no exception escapes.
        org.assertj.core.api.ThrowableAssert.ThrowingCallable save = () -> {
            Movie movie = Movie.builder()
                    .title("Audit Insert Test")
                    .genre("Drama")
                    .contentRating(ContentRating.PG)
                    .build();
            movieRepository.save(movie);
            movieRepository.flush();
        };

        org.assertj.core.api.Assertions.assertThatCode(save).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AuditInterceptor.onFlushDirty: updating a Movie does not throw — interceptor fires on update")
    @Transactional
    void auditInterceptor_onUpdate_doesNotThrow() {
        Movie movie = Movie.builder()
                .title("Audit Update Before")
                .genre("Action")
                .contentRating(ContentRating.G)
                .build();
        movie = movieRepository.save(movie);
        movieRepository.flush();

        // Mutate the managed entity — dirty check will trigger onFlushDirty
        final Long movieId = movie.getId();
        org.assertj.core.api.ThrowableAssert.ThrowingCallable update = () -> {
            Movie managed = movieRepository.findById(movieId).orElseThrow();
            managed.setTitle("Audit Update After");
            movieRepository.flush();
        };

        org.assertj.core.api.Assertions.assertThatCode(update).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ReviewNormalizationListener.prePersist: content with surrounding whitespace is trimmed on reload")
    @Transactional
    void reviewNormalizationListener_prePersist_normalizesContent() {
        Movie movie = movieRepository.findAll().getFirst();

        Review review = Review.builder()
                .movie(movie)
                .content(" GREAT film! ")
                .build();
        Review saved = reviewRepository.save(review);
        reviewRepository.flush();

        Review reloaded = reviewRepository.findById(saved.getId()).orElseThrow();
        // Listener must have trimmed both leading and trailing whitespace
        assertThat(reloaded.getContent()).isEqualTo("GREAT film!");
        assertThat(reloaded.getContent()).doesNotStartWith(" ");
        assertThat(reloaded.getContent()).doesNotEndWith(" ");
    }

    @Test
    @DisplayName("Filter enabled then disabled: calling filtered service then unfiltered service returns more records")
    void filter_disabled_afterBeingEnabled_returnsAllRecords() {
        // findForUser(false) enables the contentRatingFilter inside its own transaction/session.
        // Each service call opens its own session, so the second call starts with a fresh,
        // filter-free session — equivalent to "disabling" the filter for the new call.
        //
        // setUp() saved: G, PG_13, R, NC_17 — 4 movies total.
        // findForUser(false) with maxRating=PG_13 returns only G and PG_13 → 2 movies.
        // findAllUnfiltered() returns all 4.

        List<Movie> filteredResults = contentRatingService.findForUser(false);
        int filteredCount = filteredResults.size();

        List<Movie> allResults = contentRatingService.findAllUnfiltered();
        int allCount = allResults.size();

        // Unfiltered count must be strictly greater — proves the filter was only active
        // in the first session, not in the second (no leak between sessions).
        assertThat(allCount).isGreaterThan(filteredCount);
        assertThat(filteredCount).isEqualTo(2);
        assertThat(allCount).isEqualTo(4);
    }
}
