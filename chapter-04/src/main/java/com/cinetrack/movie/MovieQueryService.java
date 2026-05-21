package com.cinetrack.movie;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service showcasing two distinct paths through Hibernate 7's SQM query pipeline.
 *
 * <h2>Path 1: HQL with named parameters ({@link #findByGenreAndMinRating})</h2>
 * The HQL string is parsed into an SQM tree once at first execution (then cached).
 * Named parameters ({@code :genre}, {@code :minRating}) are resolved against the
 * SQM parameter nodes: Hibernate knows their Java types from the entity mapping and
 * selects the correct {@code JdbcType} binder automatically.
 *
 * <h2>Path 2: Array function query ({@link #findMoviesByStreamingPlatform})</h2>
 * Hibernate 7 registers {@code array_contains(array, element)} as an SQM function.
 * It translates to PostgreSQL's {@code array_contains(streaming_platforms, ?)},
 * keeping the query portable across dialects that support arrays.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieQueryService {

    private final EntityManager em;

    /**
     * Finds movies matching the given genre with a rating at or above {@code minRating}.
     *
     * <p>The HQL uses named parameters bound by name, not position: this is the
     * recommended style in Hibernate 7 because it survives query refactoring.
     *
     * @param genre     the genre to filter by
     * @param minRating the minimum acceptable rating (inclusive)
     * @return list of matching movies ordered by rating descending
     */
    public List<Movie> findByGenreAndMinRating(Genre genre, BigDecimal minRating) {
        TypedQuery<Movie> query = em.createQuery(
                """
                SELECT m
                FROM   Movie m
                WHERE  m.genre     = :genre
                  AND  m.rating   >= :minRating
                ORDER BY m.rating DESC
                """,
                Movie.class);

        query.setParameter("genre", genre);
        query.setParameter("minRating", minRating);

        return query.getResultList();
    }

    /**
     * Finds movies available on the specified streaming platform.
     *
     * <p>Hibernate 7 translates the HQL function call {@code array_contains()}
     * into the PostgreSQL-native form. The function is registered by
     * {@code PostgreSQLDialect} and participates fully in SQM type resolution : 
     * the second argument is bound as {@code VARCHAR} matching the array's element type.
     *
     * <p>Equivalent SQL:
     * <pre>{@code
     * SELECT * FROM movies WHERE array_contains(streaming_platforms, ?)
     * }</pre>
     *
     * @param platform the streaming platform name, e.g. {@code "Netflix"}
     * @return movies available on that platform
     */
    public List<Movie> findMoviesByStreamingPlatform(String platform) {
        TypedQuery<Movie> query = em.createQuery(
                """
                SELECT m
                FROM   Movie m
                WHERE  array_contains(m.streamingPlatforms, :platform) = true
                """,
                Movie.class);

        query.setParameter("platform", platform);

        return query.getResultList();
    }
}
