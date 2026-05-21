package com.cinetrack.movie;

import java.util.List;

/**
 * Custom repository fragment for operations that cannot be expressed cleanly
 * via derived queries or @Query. Spring Data JPA discovers the implementation
 * class (MovieRepositoryCustomImpl) by naming convention and weaves it into
 * the MovieRepository proxy at startup.
 */
public interface MovieRepositoryCustom {

    /**
     * Returns movies that have at least {@code minReviews} reviews.
     * Implemented with a JPQL GROUP BY / HAVING query in the fragment.
     */
    List<Movie> findMoviesWithMinReviews(int minReviews);
}
