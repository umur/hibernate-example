package com.cinetrack.movie;

import jakarta.persistence.QueryHint;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository demonstrating the full breadth of Spring Data JPA query techniques:
 * <ul>
 *   <li>JPQL constructor expressions ({@code SELECT new …})</li>
 *   <li>JOIN FETCH to prevent N+1 selects</li>
 *   <li>Native SQL pass-through</li>
 *   <li>Bulk UPDATE with {@code @Modifying}</li>
 *   <li>{@code @QueryHints} for read-only optimisation</li>
 *   <li>{@code @EntityGraph} for ad-hoc eager loading</li>
 *   <li>Keyset (scroll) pagination via the Spring Data 3 {@link Window} API</li>
 * </ul>
 */
@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    // -------------------------------------------------------------------------
    // 1. JPQL constructor expression — projects into a DTO without loading the
    //    full entity graph. AVG and COUNT are aggregate functions over the joined
    //    reviews relation.
    // -------------------------------------------------------------------------

    @Query("""
            SELECT new com.cinetrack.movie.MovieSummaryDto(
                m.id, m.title, COALESCE(AVG(r.rating), 0.0), COUNT(r)
            )
            FROM Movie m
            LEFT JOIN m.reviews r
            GROUP BY m.id, m.title
            ORDER BY m.title
            """)
    List<MovieSummaryDto> findMovieSummaries();

    // -------------------------------------------------------------------------
    // 2. JOIN FETCH — loads Movie + its Review collection in a single SQL JOIN,
    //    avoiding the classic N+1 problem. DISTINCT removes duplicate Movie rows
    //    that the SQL JOIN would otherwise produce.
    // -------------------------------------------------------------------------

    @Query("""
            SELECT DISTINCT m
            FROM Movie m
            LEFT JOIN FETCH m.reviews
            WHERE m.genre = :genre
            """)
    List<Movie> findByGenreWithReviews(@Param("genre") Genre genre);

    // -------------------------------------------------------------------------
    // 3. Native query — escapes to raw SQL when JPQL cannot express what you
    //    need (e.g. database-specific functions, LIMIT/OFFSET syntax, CTEs).
    //    Spring Data maps results to the entity via column-name matching.
    // -------------------------------------------------------------------------

    @Query(
        value = """
                SELECT *
                FROM movies
                WHERE rating >= :minRating
                ORDER BY rating DESC
                LIMIT :limit
                """,
        nativeQuery = true
    )
    List<Movie> findTopRated(@Param("minRating") double minRating, @Param("limit") int limit);

    // -------------------------------------------------------------------------
    // 4. @Modifying — required for any DML statement (UPDATE / DELETE).
    //    clearAutomatically = true evicts the affected entities from the
    //    first-level cache so subsequent reads see the updated values.
    // -------------------------------------------------------------------------

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Movie m SET m.rating = :rating WHERE m.id = :id")
    int updateRating(@Param("id") Long id, @Param("rating") BigDecimal rating);

    // -------------------------------------------------------------------------
    // 5. @QueryHints — passes JDBC/Hibernate hints alongside the query.
    //    HINT_READONLY tells Hibernate to skip dirty-checking for the returned
    //    entities, reducing first-level cache overhead for read-only operations.
    // -------------------------------------------------------------------------

    @QueryHints(
        @QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_READ_ONLY, value = "true")
    )
    @Query("SELECT m FROM Movie m WHERE m.releaseYear >= :year ORDER BY m.rating DESC")
    List<Movie> findReleasedAfterReadOnly(@Param("year") int year);

    // -------------------------------------------------------------------------
    // 6. @EntityGraph — overrides fetch plan at the query site without touching
    //    the entity mapping. Loads Movie + reviews + reviews.reviewer eagerly
    //    in two LEFT OUTER JOINs (no N+1, no LazyInitializationException).
    // -------------------------------------------------------------------------

    @EntityGraph(attributePaths = {"reviews", "reviews.reviewer"})
    Optional<Movie> findWithReviewsById(Long id);

    // -------------------------------------------------------------------------
    // 7. Keyset (scroll) pagination — Spring Data 3 Window API.
    //    Unlike offset pagination, keyset pagination uses the last seen id as
    //    a "cursor", making it stable and O(1) regardless of page depth.
    //    Callers pass ScrollPosition.keyset() or ScrollPosition.offset(0).
    // -------------------------------------------------------------------------

    Window<Movie> findFirst20ByGenreOrderByIdAsc(Genre genre, ScrollPosition position);
}
