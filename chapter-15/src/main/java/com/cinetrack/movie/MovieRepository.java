package com.cinetrack.movie;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    /**
     * Explicit JOIN FETCH in JPQL — the gold standard for loading a collection
     * alongside its parent in a single query.
     *
     * DISTINCT in JPQL tells Hibernate to de-duplicate the Movie objects in the
     * result list (the SQL result set has one row per movie+review pair, so
     * without DISTINCT you get one Movie instance per review).  Note that
     * "DISTINCT" here is a Hibernate instruction, not necessarily a SQL DISTINCT
     * clause — Hibernate uses a pass-through ResultTransformer instead.
     *
     * When to prefer this over @EntityGraph:
     * - You need a custom WHERE / ORDER BY that references the joined table.
     * - The JPQL makes the intent explicit in the repository (no annotation magic).
     */
    @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN FETCH m.reviews WHERE m.genre = :genre")
    List<Movie> findByGenreWithReviews(@Param("genre") Genre genre);

    /**
     * @EntityGraph delegates to the named graph defined on the entity.
     * Spring Data rewrites the derived query into a JOIN FETCH behind the scenes.
     *
     * When to prefer this over an explicit JPQL JOIN FETCH:
     * - The base query is a simple derived finder (no custom JPQL needed).
     * - You want to keep the fetch strategy separated from the query predicate.
     */
    @EntityGraph(value = "Movie.withReviews")
    List<Movie> findByRatingGreaterThan(BigDecimal rating);

    /**
     * Plain finder — intentionally loads movies WITHOUT reviews.
     * Used in {@link com.cinetrack.movie.MovieService#getMovieSummaries()} to
     * demonstrate that lazy loading means "pay only for what you use".
     */
    Optional<Movie> findById(Long id);
}
