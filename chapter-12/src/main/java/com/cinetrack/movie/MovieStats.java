package com.cinetrack.movie;

/**
 * <strong>Class-based (DTO) projection</strong> populated via a JPQL constructor expression.
 *
 * <h2>Constructor expression syntax</h2>
 * <pre>{@code
 * SELECT new com.cinetrack.movie.MovieStats(
 *     m.id, m.title, AVG(r.rating), COUNT(r)
 * )
 * FROM Movie m LEFT JOIN m.reviews r
 * GROUP BY m.id, m.title
 * }</pre>
 *
 * <p>Hibernate matches the constructor by the exact sequence and types of the
 * arguments. Unlike interface projections, no proxy is involved — {@code MovieStats}
 * is a plain Java record and can be used in pure unit tests without a
 * Spring context.</p>
 *
 * <h2>Trade-offs vs interface projections</h2>
 * <ul>
 *   <li>Supports aggregation functions ({@code AVG}, {@code COUNT}, etc.).</li>
 *   <li>Works with native queries if you use {@code @SqlResultSetMapping}.</li>
 *   <li>No lazy-loading surprises — all fields are populated at query time.</li>
 *   <li>Requires an exact constructor match; adding a field is a coordinated change.</li>
 * </ul>
 */
public record MovieStats(
        Long movieId,
        String title,
        double avgRating,
        long reviewCount
) {}
