package com.cinetrack.movie;

import com.cinetrack.review.Review;
import com.cinetrack.review.ReviewSummaryDto;
import com.cinetrack.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Demonstrates the JPA Criteria API with {@link Tuple} queries.
 *
 * <h2>Why Tuple queries?</h2>
 * <p>A {@code CriteriaQuery<Tuple>} lets you select arbitrary expressions
 * (scalar values, aggregates, columns from multiple entities) and name each
 * one so the result can be accessed by alias rather than column position.
 * This avoids the fragile {@code Object[]} index arithmetic of native queries
 * while remaining fully type-checked by the compiler.</p>
 *
 * <h2>Comparison with other strategies</h2>
 * <table border="1">
 *   <tr>
 *     <th>Strategy</th>
 *     <th>Type safety</th>
 *     <th>Supports aggregation</th>
 *     <th>Works with native SQL</th>
 *   </tr>
 *   <tr>
 *     <td>Interface projection</td><td>Proxy: no</td><td>No</td><td>Limited</td>
 *   </tr>
 *   <tr>
 *     <td>Constructor expression</td><td>Yes</td><td>Yes</td><td>Via @SqlResultSetMapping</td>
 *   </tr>
 *   <tr>
 *     <td>Tuple (Criteria)</td><td>Yes (by alias)</td><td>Yes</td><td>No</td>
 *   </tr>
 *   <tr>
 *     <td>Object[] (native)</td><td>No</td><td>Yes</td><td>Yes</td>
 *   </tr>
 * </table>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieQueryService {

    @PersistenceContext
    private final EntityManager em;

    /**
     * Returns a flat list of review summaries for the given movie, assembled
     * from three tables (reviews, movies, app_users) using a Tuple query.
     *
     * <p>The equivalent JPQL would be:</p>
     * <pre>{@code
     * SELECT r.id, m.title, u.username, r.rating
     * FROM Review r
     * JOIN r.movie m
     * JOIN r.reviewer u
     * WHERE m.id = :movieId
     * ORDER BY r.rating DESC
     * }</pre>
     *
     * <p>Using the Criteria API makes each selection alias explicit and keeps
     * the query refactor-safe: renaming {@code Review.rating} to
     * {@code Review.score} in the entity will cause a compile error here rather
     * than a runtime exception.</p>
     *
     * @param movieId PK of the movie whose reviews to summarise
     * @return ordered list of review summaries, highest rating first
     */
    public List<ReviewSummaryDto> findReviewSummaries(Long movieId) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> q = cb.createTupleQuery();

        // FROM review r
        Root<Review> review = q.from(Review.class);

        // JOIN review.movie m  /  JOIN review.reviewer u
        Join<Review, Movie>   movie    = review.join("movie",    JoinType.INNER);
        Join<Review, AppUser> reviewer = review.join("reviewer", JoinType.INNER);

        // SELECT r.id AS reviewId, m.title AS movieTitle,
        //        u.username AS reviewerName, r.rating AS rating
        q.multiselect(
                review.get("id")           .alias("reviewId"),
                movie.get("title")         .alias("movieTitle"),
                reviewer.get("username")   .alias("reviewerName"),
                review.get("rating")       .alias("rating")
        );

        // WHERE m.id = :movieId
        q.where(cb.equal(movie.get("id"), movieId));

        // ORDER BY r.rating DESC
        q.orderBy(cb.desc(review.get("rating")));

        // Execute and map each Tuple to a ReviewSummaryDto
        return em.createQuery(q)
                 .getResultList()
                 .stream()
                 .map(tuple -> new ReviewSummaryDto(
                         tuple.get("reviewId",      Long.class),
                         tuple.get("movieTitle",    String.class),
                         tuple.get("reviewerName",  String.class),
                         tuple.get("rating",        Integer.class)
                 ))
                 .toList();
    }
}
