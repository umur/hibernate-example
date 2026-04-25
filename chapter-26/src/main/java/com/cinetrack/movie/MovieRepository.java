package com.cinetrack.movie;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface MovieRepository extends ReactiveCrudRepository<Movie, Long> {

    Flux<Movie> findByGenre(String genre);
}
