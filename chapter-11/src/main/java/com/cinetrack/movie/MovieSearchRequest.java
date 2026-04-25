package com.cinetrack.movie;

/**
 * Incoming search parameters from the API layer.
 *
 * <p>All fields are nullable — a {@code null} value means "no filter on this
 * dimension". {@link MovieService#searchMovies(MovieSearchRequest)} converts each
 * non-null field into a {@link org.springframework.data.jpa.domain.Specification}
 * and composes them with {@code and()}.</p>
 */
public record MovieSearchRequest(
        Genre genre,
        Integer releaseAfter,
        Double minRating,
        String titleKeyword
) {}
