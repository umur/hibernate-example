package com.cinetrack.movie;

import com.cinetrack.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MovieService#searchMovies} covering OR/null-safe
 * Specification composition and all parameter combinations.
 */
@SpringBootTest
@Transactional
class MovieServiceTest extends AbstractIntegrationTest {

    @Autowired
    private MovieService movieService;

    @Autowired
    private MovieRepository movieRepository;

    @BeforeEach
    void setUp() {
        movieRepository.deleteAllInBatch();

        movieRepository.save(new Movie("Inception",        Genre.SCIENCE_FICTION, 2010, new BigDecimal("8.8")));
        movieRepository.save(new Movie("Interstellar",     Genre.SCIENCE_FICTION, 2014, new BigDecimal("8.6")));
        movieRepository.save(new Movie("The Dark Knight",  Genre.ACTION,          2008, new BigDecimal("9.0")));
        movieRepository.save(new Movie("Mad Max Fury Road",Genre.ACTION,          2015, new BigDecimal("8.1")));
        movieRepository.save(new Movie("Amélie",           Genre.ROMANCE,         2001, new BigDecimal("8.3")));
        movieRepository.flush();
    }

    // -------------------------------------------------------------------------
    // Genre-only filter
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("searchMovies_withGenreOnly_returnsMatchingGenre")
    void searchMovies_withGenreOnly_returnsMatchingGenre() {
        MovieSearchRequest req = new MovieSearchRequest(Genre.ACTION, null, null, null);

        Page<Movie> page = movieService.searchMovies(req, PageRequest.of(0, 20));

        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent()).allSatisfy(m ->
                assertThat(m.getGenre()).isEqualTo(Genre.ACTION));
        assertThat(page.getContent()).extracting(Movie::getTitle)
                .containsExactlyInAnyOrder("The Dark Knight", "Mad Max Fury Road");
    }

    // -------------------------------------------------------------------------
    // Title-only filter
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("searchMovies_withTitleOnly_returnsMatchingTitle")
    void searchMovies_withTitleOnly_returnsMatchingTitle() {
        MovieSearchRequest req = new MovieSearchRequest(null, null, null, "inception");

        Page<Movie> page = movieService.searchMovies(req, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTitle()).isEqualTo("Inception");
    }

    // -------------------------------------------------------------------------
    // All-null params → returns all movies
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("searchMovies_allNullParams_returnsAll")
    void searchMovies_allNullParams_returnsAll() {
        MovieSearchRequest req = new MovieSearchRequest(null, null, null, null);

        Page<Movie> page = movieService.searchMovies(req, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    // -------------------------------------------------------------------------
    // No-match case
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("searchMovies_noMatches_returnsEmpty")
    void searchMovies_noMatches_returnsEmpty() {
        // Rating threshold higher than any movie in the DB
        MovieSearchRequest req = new MovieSearchRequest(null, null, 9.9, null);

        Page<Movie> page = movieService.searchMovies(req, PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
    }
}
