package com.cinetrack.movie;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieSearchController {

    private final MovieSearchService movieSearchService;

    @GetMapping("/search")
    public ResponseEntity<List<Movie>> search(@RequestParam("q") String query) {
        List<Movie> results = movieSearchService.searchByKeyword(query);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/search/genre")
    public ResponseEntity<List<Movie>> searchByGenre(@RequestParam("genre") String genre) {
        List<Movie> results = movieSearchService.searchByGenre(genre);
        return ResponseEntity.ok(results);
    }
}
