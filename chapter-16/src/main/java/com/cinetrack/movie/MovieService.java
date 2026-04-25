package com.cinetrack.movie;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Demonstrates the N+1 problem and its JOIN FETCH fix side by side.
 *
 * <p>Both methods are read-only transactions so Hibernate's dirty-checking
 * overhead is skipped and the session is closed immediately after the method
 * returns — making query counts easy to measure in tests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MovieService {

    private final MovieRepository movieRepository;

    /**
     * N+1 scenario: loads movies with a plain {@code findAll()}, then
     * iterates the lazy {@code reviews} collection on each movie.
     *
     * <p>Query count breakdown:
     * <ol>
     *   <li>1 SELECT to fetch all movies.</li>
     *   <li>N additional SELECTs — one per movie — to initialise the lazy
     *       {@code reviews} collection when it is first accessed.</li>
     * </ol>
     *
     * <p>With {@code @BatchSize(size=25)} Hibernate batches the collection
     * SELECTs into {@code WHERE movie_id IN (?,?,…)} chunks, so for small
     * datasets (N ≤ 25) the real count is 2 not N+1.  The test pins the exact
     * count so readers can observe the behaviour.
     *
     * @return list of movies with review count logged
     */
    @Transactional(readOnly = true)
    public List<Movie> getMovieNplusOne() {
        List<Movie> movies = movieRepository.findAll();

        // Deliberately trigger lazy loading inside the loop — this is what
        // causes N+1. Each getReviews() call that has not been batch-loaded
        // yet fires an individual SQL statement.
        movies.forEach(m ->
                log.debug("Movie '{}' has {} review(s)", m.getTitle(), m.getReviews().size())
        );

        return movies;
    }

    /**
     * Fixed scenario: a single JOIN FETCH query loads movies AND reviews
     * together.  No lazy initialisation happens inside the loop because the
     * collection is already populated.
     *
     * <p>Query count: exactly 1 regardless of how many movies exist.
     *
     * @return list of movies with their reviews already initialised
     */
    @Transactional(readOnly = true)
    public List<Movie> getMovieFixed() {
        List<Movie> movies = movieRepository.findAllWithReviews();

        // getReviews() here does NOT hit the database — the collection was
        // populated by the JOIN FETCH above.
        movies.forEach(m ->
                log.debug("Movie '{}' has {} review(s) [JOIN FETCH]", m.getTitle(), m.getReviews().size())
        );

        return movies;
    }
}
