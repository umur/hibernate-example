package com.cinetrack.movie;

/**
 * Projection carrying aggregate review statistics for a movie.
 *
 * <p>Populated by the CTE-based HQL query in
 * {@link MovieQueryService#findMovieStats()}. The {@code FILTER} clause on
 * {@code COUNT} is a Hibernate 7 / SQL:2003 extension that lets us compute
 * conditional aggregates in a single pass without sub-queries or CASE
 * expressions.</p>
 *
 * @param id               surrogate PK
 * @param title            movie title
 * @param totalReviews     total number of reviews (including neutral)
 * @param positiveReviews  reviews with rating &gt;= 4
 * @param negativeReviews  reviews with rating &lt;= 2
 */
public record MovieStatsDto(
        Long id,
        String title,
        Long totalReviews,
        Long positiveReviews,
        Long negativeReviews) {
}
