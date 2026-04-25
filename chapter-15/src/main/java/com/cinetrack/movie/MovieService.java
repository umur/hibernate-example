package com.cinetrack.movie;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer demonstrating correct fetch-strategy patterns and the pitfalls
 * they prevent.
 *
 * <h2>LazyInitializationException — the most common Hibernate mistake</h2>
 * If you load a {@link Movie} in one transaction and then try to access
 * {@code movie.getReviews()} after the transaction (and therefore the Session)
 * has closed, Hibernate throws {@code LazyInitializationException} because the
 * proxy can no longer talk to the database.
 *
 * <p>The fix is always the same: ensure the collection is initialised while the
 * Session is still open.  The two canonical approaches are:
 * <ol>
 *   <li>JOIN FETCH / @EntityGraph — fetch the collection in the initial query</li>
 *   <li>Keep the caller inside a {@code @Transactional} boundary long enough to
 *       initialise what it needs</li>
 * </ol>
 * Never use {@code FetchType.EAGER} on a collection as a band-aid — it causes
 * Cartesian products for multi-level associations and loads data you may never use.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;

    /**
     * Loads a single movie with its reviews initialised in ONE query.
     *
     * Uses {@link MovieRepository#findByGenreWithReviews} (JOIN FETCH) scoped to
     * a single ID via the genre query, or for a direct ID lookup we issue a
     * plain find and accept that reviews will be batch-loaded when first accessed
     * (still within this transaction).
     *
     * <p>Calling {@code movie.getReviews().size()} here forces Hibernate to
     * initialise the collection while the Session is open.  The caller receives
     * a fully-loaded object that is safe to use after the transaction closes.</p>
     */
    @Transactional(readOnly = true)
    public Movie getMovieWithReviews(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Movie not found: " + id));

        // Trigger collection initialisation while Session is open.
        // Hibernate uses @BatchSize, so if other movies were loaded in the same
        // context their collections are batch-fetched here too.
        int reviewCount = movie.getReviews().size();
        log.debug("Loaded movie '{}' with {} reviews", movie.getTitle(), reviewCount);

        return movie;
    }

    /**
     * Returns all movies WITHOUT loading their reviews.
     *
     * This is the correct pattern for summary/list views.  Because reviews are
     * LAZY, Hibernate does not issue any secondary SELECT.  The returned movie
     * objects must not have their {@code reviews} collection accessed after this
     * method returns (Session is closed) — doing so would throw
     * {@code LazyInitializationException}.
     *
     * <p>If the caller needs reviews too, use {@link #getMovieWithReviews} or
     * {@link MovieRepository#findByGenreWithReviews}.</p>
     */
    @Transactional(readOnly = true)
    public List<Movie> getMovieSummaries() {
        List<Movie> movies = movieRepository.findAll();
        log.debug("Loaded {} movie summaries (reviews not initialised)", movies.size());
        return movies;
    }
}
