package com.cinetrack.movie;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.review.Review;
import com.cinetrack.review.ReviewRepository;
import com.cinetrack.user.AppUser;
import com.cinetrack.user.AppUserRepository;
import com.cinetrack.watchlist.Watchlist;
import com.cinetrack.watchlist.WatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link MovieService} error paths and
 * {@link WatchlistRepository} basic save/reload.
 */
@SpringBootTest
@DisplayName("Chapter 15 — MovieService & WatchlistRepository")
class MovieServiceIT extends AbstractIntegrationTest {

    @Autowired MovieService         movieService;
    @Autowired MovieRepository      movieRepository;
    @Autowired ReviewRepository     reviewRepository;
    @Autowired AppUserRepository    userRepository;
    @Autowired WatchlistRepository  watchlistRepository;
    @Autowired PlatformTransactionManager txManager;

    private TransactionTemplate tx;
    private AppUser alice;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        // Delete watchlists (and cascade-delete entries) in a separate transaction
        // so the DELETEs are flushed to the database before the bulk JPQL
        // operations on app_users run — otherwise the FK from watchlists to
        // app_users still references rows we are about to delete.
        tx.executeWithoutResult(s -> {
            reviewRepository.deleteAllInBatch();
            watchlistRepository.deleteAll();          // entries are cascade-deleted
        });
        tx.executeWithoutResult(s -> {
            movieRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
        });
        tx.executeWithoutResult(s ->
            alice = userRepository.save(new AppUser("alice", "alice@example.com"))
        );
    }

    // -----------------------------------------------------------------------
    // getMovieWithReviews — not found
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getMovieWithReviews: non-existent ID throws IllegalArgumentException")
    void getMovieWithReviews_notFound_throwsException() {
        assertThatThrownBy(() -> movieService.getMovieWithReviews(999_999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999999");
    }

    // -----------------------------------------------------------------------
    // getMovieWithReviews — found with reviews
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getMovieWithReviews: existing movie returns with initialised reviews")
    void getMovieWithReviews_found_returnsWithReviews() {
        Long movieId = tx.execute(s -> {
            Movie movie = movieRepository.save(
                    new Movie("Inception", Genre.SCI_FI, BigDecimal.valueOf(8.8)));
            reviewRepository.save(new Review(movie, alice, "Mindbending", 5));
            reviewRepository.save(new Review(movie, alice, "Great visuals", 4));
            return movie.getId();
        });

        Movie result = tx.execute(s -> movieService.getMovieWithReviews(movieId));

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(movieId);
        assertThat(result.getReviews()).hasSize(2);
    }

    // -----------------------------------------------------------------------
    // getMovieSummaries — returns all movies
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getMovieSummaries: persisted movies are all returned")
    void getMovieSummaries_returnsAllMovies() {
        tx.executeWithoutResult(s -> {
            movieRepository.save(new Movie("Movie One",   Genre.ACTION, BigDecimal.valueOf(7.0)));
            movieRepository.save(new Movie("Movie Two",   Genre.DRAMA,  BigDecimal.valueOf(6.5)));
            movieRepository.save(new Movie("Movie Three", Genre.COMEDY, BigDecimal.valueOf(8.0)));
        });

        List<Movie> summaries = movieService.getMovieSummaries();

        assertThat(summaries).hasSize(3);
        assertThat(summaries).extracting(Movie::getTitle)
                .containsExactlyInAnyOrder("Movie One", "Movie Two", "Movie Three");
    }

    // -----------------------------------------------------------------------
    // WatchlistRepository — save and reload
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("WatchlistRepository: saved watchlist is reloaded with correct owner and name")
    void watchlist_save_andReload_success() {
        Long watchlistId = tx.execute(s -> {
            AppUser owner = userRepository.findById(alice.getId()).orElseThrow();
            Watchlist wl = new Watchlist(owner, "My Favourites");
            return watchlistRepository.save(wl).getId();
        });

        Watchlist reloaded = tx.execute(s ->
                watchlistRepository.findById(watchlistId).orElseThrow());

        assertThat(reloaded.getId()).isEqualTo(watchlistId);
        assertThat(reloaded.getName()).isEqualTo("My Favourites");
        assertThat(reloaded.getOwner()).isNotNull();
    }
}
