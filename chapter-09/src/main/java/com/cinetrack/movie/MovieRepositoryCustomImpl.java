package com.cinetrack.movie;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Concrete implementation of the MovieRepositoryCustom fragment.
 *
 * Using EntityManager directly gives full control over the JPQL or Criteria
 * query — useful for complex aggregations that derived queries cannot express.
 */
@RequiredArgsConstructor
public class MovieRepositoryCustomImpl implements MovieRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    @SuppressWarnings("unchecked")
    public List<Movie> findMoviesWithMinReviews(int minReviews) {
        return entityManager.createQuery("""
                SELECT m
                FROM Movie m
                WHERE (SELECT COUNT(r) FROM Review r WHERE r.movie = m) >= :minReviews
                ORDER BY m.title
                """, Movie.class)
                .setParameter("minReviews", (long) minReviews)
                .getResultList();
    }
}
