package com.cinetrack.movie;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;

    public Mono<Movie> save(Movie movie) {
        return movieRepository.save(movie);
    }

    public Mono<Movie> findById(Long id) {
        return movieRepository.findById(id);
    }

    public Flux<Movie> findByGenre(String genre) {
        return movieRepository.findByGenre(genre);
    }

    public Flux<Movie> findAll() {
        return movieRepository.findAll();
    }

    public Mono<Void> deleteById(Long id) {
        return movieRepository.deleteById(id);
    }
}
