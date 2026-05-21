package com.cinetrack.review;

import com.cinetrack.common.SoftDeletableRepository;

public interface ReviewRepository extends SoftDeletableRepository<Review, Long> {

    long countByMovieId(Long movieId);
}
