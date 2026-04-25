package com.cinetrack.movie;

import org.springframework.data.jpa.domain.Specification;

/**
 * Factory class for reusable {@link Specification} predicates targeting {@link Movie}.
 *
 * <h2>Composition pattern</h2>
 * <p>Each static method returns a single-concern {@code Specification}. Callers
 * combine them with {@link Specification#and(Specification)} and
 * {@link Specification#or(Specification)} to build arbitrarily complex dynamic
 * queries without string concatenation or conditional JPQL fragments.</p>
 *
 * <pre>{@code
 * Specification<Movie> spec = MovieSpecifications.hasGenre(Genre.ACTION)
 *         .and(MovieSpecifications.ratingAtLeast(7.5))
 *         .and(MovieSpecifications.releasedAfter(2000));
 *
 * List<Movie> results = movieRepository.findAll(spec, PageRequest.of(0, 20));
 * }</pre>
 *
 * <h2>Null safety</h2>
 * <p>Every method checks its argument for {@code null} and returns
 * {@link Specification#where(Specification)} (a no-op predicate) when the caller
 * provides no value, making it safe to always pass all specs into the chain.</p>
 */
public final class MovieSpecifications {

    private MovieSpecifications() {}

    /**
     * Matches movies whose {@code genre} equals the supplied value.
     *
     * <p>Generates: {@code WHERE m.genre = ?}</p>
     */
    public static Specification<Movie> hasGenre(Genre genre) {
        return (root, query, cb) -> {
            if (genre == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get(Movie_.genre), genre);
        };
    }

    /**
     * Matches movies released strictly after {@code year} (exclusive lower bound).
     *
     * <p>Generates: {@code WHERE m.release_year > ?}</p>
     */
    public static Specification<Movie> releasedAfter(Integer year) {
        return (root, query, cb) -> {
            if (year == null) {
                return cb.conjunction();
            }
            return cb.greaterThan(root.get(Movie_.releaseYear), year);
        };
    }

    /**
     * Matches movies whose rating is greater than or equal to {@code minRating}.
     *
     * <p>Generates: {@code WHERE m.rating >= ?}</p>
     */
    public static Specification<Movie> ratingAtLeast(Double minRating) {
        return (root, query, cb) -> {
            if (minRating == null) {
                return cb.conjunction();
            }
            return cb.greaterThanOrEqualTo(
                    root.get(Movie_.rating).as(Double.class),
                    minRating);
        };
    }

    /**
     * Case-insensitive substring match on {@code title}.
     *
     * <p>Generates: {@code WHERE LOWER(m.title) LIKE LOWER('%keyword%')}</p>
     */
    public static Specification<Movie> titleContains(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + keyword.toLowerCase() + "%";
            return cb.like(cb.lower(root.get(Movie_.title)), pattern);
        };
    }
}
