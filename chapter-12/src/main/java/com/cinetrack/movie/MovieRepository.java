package com.cinetrack.movie;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository demonstrating all four Spring Data JPA result-mapping strategies
 * covered in Chapter 12.
 *
 * <ol>
 *   <li><strong>Closed interface projection</strong>: {@link #findByGenre(Genre)} returns
 *       a JDK proxy per movie; only the projected columns are included in the SQL
 *       {@code SELECT} list.</li>
 *   <li><strong>Open interface projection</strong>: {@link #findProjectedByGenre(Genre)}
 *       returns a proxy whose {@code getGenreName()} is evaluated via SpEL
 *       against the full entity in memory.</li>
 *   <li><strong>Class-based DTO with constructor expression</strong> : 
 *       {@link #findMovieStats()} uses {@code SELECT new …} JPQL to aggregate
 *       review data directly in the database.</li>
 *   <li><strong>Native query returning raw {@code Object[]}</strong> : 
 *       {@link #findRawReviewData(Long)} shows the lowest-level escape hatch;
 *       the caller maps column positions manually (or via
 *       {@code @SqlResultSetMapping}).</li>
 * </ol>
 */
@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    // -------------------------------------------------------------------------
    // 1. Closed interface projection
    //    Spring Data generates a SELECT title, genre FROM movies WHERE genre = ?
    //    Because the interface is closed (no @Value SpEL), the provider can
    //    optimise the column list.
    // -------------------------------------------------------------------------

    List<MovieTitleProjection> findByGenre(Genre genre);

    // -------------------------------------------------------------------------
    // 2. Open interface projection
    //    The full entity must be loaded because getGenreName() is evaluated
    //    in Java via SpEL. Returned as a proxy, not a plain object.
    // -------------------------------------------------------------------------

    List<MovieWithReviewerCount> findProjectedByGenre(Genre genre);

    // -------------------------------------------------------------------------
    // 3. Class-based DTO: constructor expression
    //    AVG and COUNT are computed in one pass on the database side.
    //    Movies with no reviews get avgRating=0.0 and reviewCount=0 from
    //    the COALESCE handled implicitly by the AVG/COUNT over an empty set.
    // -------------------------------------------------------------------------

    @Query("""
            SELECT new com.cinetrack.movie.MovieStats(
                m.id, m.title, COALESCE(AVG(r.rating), 0.0), COUNT(r)
            )
            FROM Movie m
            LEFT JOIN m.reviews r
            GROUP BY m.id, m.title
            ORDER BY m.title
            """)
    List<MovieStats> findMovieStats();

    // -------------------------------------------------------------------------
    // 4. Native query: raw Object[] result set
    //    Column order: [0]=review.id, [1]=movie.title, [2]=user.username,
    //    [3]=review.rating. The caller (or a @SqlResultSetMapping) is
    //    responsible for casting each element.
    // -------------------------------------------------------------------------

    @Query(
        value = """
                SELECT r.id,
                       m.title,
                       u.username,
                       r.rating
                FROM reviews r
                JOIN movies    m ON r.movie_id    = m.id
                JOIN app_users u ON r.reviewer_id = u.id
                WHERE m.id = :movieId
                ORDER BY r.rating DESC
                """,
        nativeQuery = true
    )
    List<Object[]> findRawReviewData(@Param("movieId") Long movieId);
}
