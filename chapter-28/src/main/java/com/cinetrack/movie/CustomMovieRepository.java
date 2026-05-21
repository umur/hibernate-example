package com.cinetrack.movie;

import java.util.List;

public interface CustomMovieRepository {

    List<Movie> findTopRatedWithReviewCount(int limit);
}
