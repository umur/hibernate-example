package com.cinetrack.movie;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Movie> create(@RequestBody Movie movie) {
        return movieService.save(movie);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Movie>> findById(@PathVariable Long id) {
        return movieService.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping
    public Flux<Movie> findAll() {
        return movieService.findAll();
    }

    @GetMapping("/genre/{genre}")
    public Flux<Movie> findByGenre(@PathVariable String genre) {
        return movieService.findByGenre(genre);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Long id) {
        return movieService.deleteById(id);
    }
}
