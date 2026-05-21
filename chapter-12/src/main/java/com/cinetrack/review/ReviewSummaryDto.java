package com.cinetrack.review;

/**
 * Flat DTO combining data from the {@code reviews}, {@code movies}, and
 * {@code app_users} tables.
 *
 * <p>This record is populated by the Tuple query in
 * {@link com.cinetrack.movie.MovieQueryService#findReviewSummaries(Long)}, which
 * uses the JPA Criteria API to build a type-safe cross-entity query without
 * relying on JPQL string literals.</p>
 *
 * <p>Because it is a plain record it is also usable as a response DTO in the
 * REST layer: no Jackson annotations needed; the record component names become
 * JSON field names automatically.</p>
 */
public record ReviewSummaryDto(
        Long reviewId,
        String movieTitle,
        String reviewerName,
        int rating
) {}
