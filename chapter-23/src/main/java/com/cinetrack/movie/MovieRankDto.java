package com.cinetrack.movie;

/**
 * Projection carrying a movie's identity, genre, rating, and its
 * {@code RANK()} position within its genre partition.
 *
 * <p>Populated directly from the HQL window-function query in
 * {@link MovieQueryService#findMoviesRankedByGenre()}. Using a record keeps
 * the DTO immutable and gives us a free {@code equals}/{@code hashCode}
 * useful in assertions.</p>
 *
 * @param id        surrogate PK
 * @param title     movie title
 * @param genre     genre bucket used for the PARTITION BY clause
 * @param rating    average rating (1–5 scale)
 * @param genreRank 1-based rank within the genre, ordered by rating DESC
 */
public record MovieRankDto(Long id, String title, String genre, Double rating, Long genreRank) {
}
