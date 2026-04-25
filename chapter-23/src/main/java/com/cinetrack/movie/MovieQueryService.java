package com.cinetrack.movie;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Demonstrates Hibernate 7's extended HQL dialect:
 *
 * <ul>
 *   <li><strong>Window functions</strong> — {@code RANK() OVER (PARTITION BY … ORDER BY …)}
 *       computed inside HQL without dropping to native SQL.</li>
 *   <li><strong>CTEs</strong> — {@code WITH topMovies AS (…)} allows named
 *       sub-queries that the main query can reference, keeping complex logic
 *       readable and database-portable.</li>
 *   <li><strong>FILTER on aggregates</strong> — {@code COUNT(r) FILTER (WHERE …)}
 *       is the SQL:2003 standard for conditional counting; Hibernate 7 renders
 *       it natively on PostgreSQL instead of falling back to CASE expressions.</li>
 *   <li><strong>Custom functions</strong> — {@code similarity()} from pg_trgm
 *       is registered via {@link SimilarityFunctionContributor} and callable
 *       from HQL like any built-in function.</li>
 * </ul>
 */
@Slf4j
@Service
public class MovieQueryService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Ranks every movie within its genre by rating (descending) using the
     * SQL {@code RANK()} window function.
     *
     * <p>The query selects scalar columns rather than a managed entity so that
     * Hibernate does not attempt to load associations — the result set is
     * intended purely for reporting.</p>
     *
     * @return list of {@link MovieRankDto}, one row per movie
     */
    @Transactional(readOnly = true)
    public List<MovieRankDto> findMoviesRankedByGenre() {
        List<Object[]> rows = entityManager.createQuery(
                "SELECT m.id, m.title, m.genre, m.rating, " +
                "RANK() OVER (PARTITION BY m.genre ORDER BY m.rating DESC) " +
                "FROM Movie m " +
                "WHERE m.genre IS NOT NULL AND m.rating IS NOT NULL",
                Object[].class)
                .getResultList();

        return rows.stream()
                .map(r -> new MovieRankDto(
                        (Long)   r[0],
                        (String) r[1],
                        (String) r[2],
                        r[3] instanceof Number n ? n.doubleValue() : (Double) r[3],
                        (Long)   r[4]))
                .toList();
    }

    /**
     * Uses a CTE to pre-filter movies with an average rating above 4.0, then
     * joins their reviews and computes positive / negative counts in one query
     * using {@code FILTER}.
     *
     * @return list of {@link MovieStatsDto} for top-rated movies only
     */
    @Transactional(readOnly = true)
    public List<MovieStatsDto> findMovieStats() {
        // The CTE selects scalar IDs of top-rated movies; the outer query then
        // joins those IDs against Movie and its reviews. We avoid embedding the
        // entity inside the CTE and dereferencing associations through a tuple
        // element, which Hibernate 7's SqmToSqlAst converter does not yet
        // support for CTE roots.
        return entityManager.createQuery(
                "WITH topMovieIds AS (" +
                "  SELECT m.id AS id FROM Movie m WHERE m.rating > 4.0" +
                ") " +
                "SELECT new com.cinetrack.movie.MovieStatsDto(" +
                "  m.id, m.title, " +
                "  COUNT(r), " +
                "  COUNT(r) FILTER (WHERE r.rating >= 4), " +
                "  COUNT(r) FILTER (WHERE r.rating <= 2)" +
                ") " +
                "FROM topMovieIds t " +
                "JOIN Movie m ON m.id = t.id " +
                "LEFT JOIN m.reviews r " +
                "GROUP BY m.id, m.title",
                MovieStatsDto.class)
                .getResultList();
    }

    /**
     * Demonstrates the registered {@code similarity()} function from pg_trgm.
     *
     * <p>Returns movies whose title is trigram-similar to the given query
     * string (similarity score &gt; 0.1). The function is registered in
     * {@link SimilarityFunctionContributor} and resolved by Hibernate's
     * function registry at parse time — no native query needed.</p>
     *
     * @param titleQuery fuzzy title string to match against
     * @return movies with similarity score above threshold, ordered by score
     */
    @Transactional(readOnly = true)
    public List<Movie> findBySimilarTitle(String titleQuery) {
        return entityManager.createQuery(
                "SELECT m FROM Movie m " +
                "WHERE similarity(m.title, :q) > 0.1 " +
                "ORDER BY similarity(m.title, :q) DESC",
                Movie.class)
                .setParameter("q", titleQuery)
                .getResultList();
    }
}
