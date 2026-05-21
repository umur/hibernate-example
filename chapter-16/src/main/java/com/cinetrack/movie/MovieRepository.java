package com.cinetrack.movie;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Spring Data repository for {@link Movie}.
 *
 * <p>Two finder methods are provided to directly compare the N+1 problem and
 * its JOIN FETCH solution side-by-side in {@link MovieService} and in the
 * integration test {@code NplusOneTest}.
 */
public interface MovieRepository extends JpaRepository<Movie, Long> {

    /**
     * Plain {@code findAll()} inherited from {@link JpaRepository}.
     *
     * <p>Issue: every call to {@code movie.getReviews()} on the returned list
     * triggers a separate SELECT against the {@code reviews} table.  With 5
     * movies that means 1 query for the movies + 5 queries for reviews = 6
     * total: the textbook N+1 pattern.
     *
     * <p>Note: {@code @BatchSize(size=25)} on the collection reduces this to
     * 1 + ceil(N/25) queries, but does not eliminate the extra round-trips.
     * The method is intentionally left as-is so the test can demonstrate the
     * unmitigated case before @BatchSize kicks in for larger datasets.
     */
    // findAll() is inherited: no override needed

    /**
     * Loads movies together with their reviews in a single SQL query using a
     * LEFT JOIN FETCH.
     *
     * <p>{@code DISTINCT} prevents Hibernate from returning duplicate {@link Movie}
     * references in the result list when a movie has multiple reviews (a natural
     * consequence of the SQL join producing one row per review).
     *
     * <p>This is the recommended fix for collections that are always needed
     * together with their owner entity.
     */
    @Query("SELECT DISTINCT m FROM Movie m LEFT JOIN FETCH m.reviews")
    List<Movie> findAllWithReviews();
}
