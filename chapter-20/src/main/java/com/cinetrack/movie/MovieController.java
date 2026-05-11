package com.cinetrack.movie;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for movie operations.
 *
 * <p>The active tenant is resolved transparently from the {@code X-Tenant-ID}
 * header by {@link com.cinetrack.multitenancy.TenantFilter}. No tenant-aware
 * code is needed here: Hibernate routes all repository calls to the correct
 * schema automatically.
 *
 * <pre>
 * # Fetch movies for tenant_a
 * curl -H "X-Tenant-ID: tenant_a" http://localhost:8080/movies
 *
 * # Fetch movies for tenant_b (completely isolated data set)
 * curl -H "X-Tenant-ID: tenant_b" http://localhost:8080/movies
 * </pre>
 */
@RestController
@RequestMapping("/movies")
public class MovieController {

    private final MovieRepository movieRepository;

    public MovieController(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    @GetMapping
    public ResponseEntity<List<Movie>> listMovies() {
        return ResponseEntity.ok(movieRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Movie> createMovie(@RequestBody Movie movie) {
        return ResponseEntity.ok(movieRepository.save(movie));
    }
}
