package com.cinetrack.movie;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import java.util.List;

public class CustomMovieRepositoryImpl implements CustomMovieRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Movie> findTopRatedWithReviewCount(int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Movie> cq = cb.createQuery(Movie.class);
        Root<Movie> movie = cq.from(Movie.class);

        cq.select(movie)
                .where(cb.isNotNull(movie.get("rating")))
                .orderBy(cb.desc(movie.get("rating")));

        return entityManager.createQuery(cq)
                .setMaxResults(limit)
                .getResultList();
    }
}
