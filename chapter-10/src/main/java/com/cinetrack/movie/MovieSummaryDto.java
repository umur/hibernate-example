package com.cinetrack.movie;

/**
 * Lightweight DTO returned by JPQL constructor expressions.
 *
 * <p>Using a {@code record} keeps the class immutable and gives us
 * {@code equals}/{@code hashCode}/{@code toString} for free — handy in tests.</p>
 *
 * <p>The fully-qualified class name must match exactly what is written in the
 * {@code SELECT new …} clause of the JPQL query.</p>
 */
public record MovieSummaryDto(
        Long id,
        String title,
        double avgRating,
        long reviewCount
) {}
