package com.cinetrack.movie;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that converts a {@link MovieSearchRequest} into a composed
 * {@link Specification} and delegates to the repository.
 *
 * <h2>How composition works</h2>
 * <p>Each filter dimension is only added to the predicate chain when the
 * corresponding field is non-null. Because every {@link MovieSpecifications}
 * factory method is null-safe (returning a no-op conjunction for {@code null}
 * input), you can also write the composition unconditionally and get the same
 * result: but the explicit null-check makes the intent clearer for readers.</p>
 *
 * <pre>{@code
 * // Equivalent: pass all specs unconditionally: nulls become conjunctions
 * Specification<Movie> spec =
 *     hasGenre(req.genre())
 *         .and(releasedAfter(req.releaseAfter()))
 *         .and(ratingAtLeast(req.minRating()))
 *         .and(titleContains(req.titleKeyword()));
 * }</pre>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movieRepository;

    /**
     * Returns a paginated list of movies matching all supplied criteria.
     *
     * @param req      search parameters (any field may be {@code null} to skip that filter)
     * @param pageable pagination and sort instructions
     * @return page of matching movies
     */
    public Page<Movie> searchMovies(MovieSearchRequest req, Pageable pageable) {
        // Spring Data 4 added a PredicateSpecification overload of where(), which makes
        // where((Specification<Movie>) null) ambiguous. Starting from an always-true
        // conjunction avoids the overload collision entirely.
        Specification<Movie> spec = (root, query, cb) -> cb.conjunction();

        if (req.genre() != null) {
            spec = spec.and(MovieSpecifications.hasGenre(req.genre()));
        }
        if (req.releaseAfter() != null) {
            spec = spec.and(MovieSpecifications.releasedAfter(req.releaseAfter()));
        }
        if (req.minRating() != null) {
            spec = spec.and(MovieSpecifications.ratingAtLeast(req.minRating()));
        }
        if (req.titleKeyword() != null && !req.titleKeyword().isBlank()) {
            spec = spec.and(MovieSpecifications.titleContains(req.titleKeyword()));
        }

        return movieRepository.findAll(spec, pageable);
    }
}
