package com.cinetrack.movie;

import com.cinetrack.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MovieQuerydslService} covering both
 * {@code findMoviesReleasedBetween} and {@code findByGenreAndMinRating}.
 */
@SpringBootTest
@Transactional
class MovieQuerydslServiceTest extends AbstractIntegrationTest {

    @Autowired
    private MovieQuerydslService querydslService;

    @Autowired
    private MovieRepository movieRepository;

    @BeforeEach
    void setUp() {
        movieRepository.deleteAllInBatch();

        movieRepository.save(new Movie("Inception",         Genre.SCIENCE_FICTION, 2010, new BigDecimal("8.8")));
        movieRepository.save(new Movie("Interstellar",      Genre.SCIENCE_FICTION, 2014, new BigDecimal("8.6")));
        movieRepository.save(new Movie("The Dark Knight",   Genre.ACTION,          2008, new BigDecimal("9.0")));
        movieRepository.save(new Movie("Mad Max Fury Road", Genre.ACTION,          2015, new BigDecimal("8.1")));
        movieRepository.save(new Movie("Amélie",            Genre.ROMANCE,         2001, new BigDecimal("8.3")));
        movieRepository.flush();
    }

    // -------------------------------------------------------------------------
    // findMoviesReleasedBetween
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findMoviesReleasedBetween returns only movies within the inclusive year range")
    void findMoviesReleasedBetween_inclusiveRange() {
        List<Movie> result = querydslService.findMoviesReleasedBetween(2010, 2014);

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(m -> {
            assertThat(m.getReleaseYear()).isGreaterThanOrEqualTo(2010);
            assertThat(m.getReleaseYear()).isLessThanOrEqualTo(2014);
        });
        assertThat(result).extracting(Movie::getTitle)
                .containsExactlyInAnyOrder("Inception", "Interstellar");
    }

    @Test
    @DisplayName("findMoviesReleasedBetween returns empty list when no movies in range")
    void findMoviesReleasedBetween_noMatch_returnsEmpty() {
        List<Movie> result = querydslService.findMoviesReleasedBetween(1900, 1999);

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findByGenreAndMinRating: null params are treated as no-filter
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByGenreAndMinRating with genre only returns all movies of that genre")
    void findByGenreAndMinRating_genreOnly_returnsAllOfGenre() {
        List<Movie> result = querydslService.findByGenreAndMinRating(Genre.ACTION, null);

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(m -> assertThat(m.getGenre()).isEqualTo(Genre.ACTION));
    }

    @Test
    @DisplayName("findByGenreAndMinRating with minRating only filters by rating across all genres")
    void findByGenreAndMinRating_ratingOnly_filtersAllGenres() {
        List<Movie> result = querydslService.findByGenreAndMinRating(null, 8.7);

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(m ->
                assertThat(m.getRating().doubleValue()).isGreaterThanOrEqualTo(8.7));
    }

    @Test
    @DisplayName("findByGenreAndMinRating with both null returns all movies")
    void findByGenreAndMinRating_bothNull_returnsAll() {
        List<Movie> result = querydslService.findByGenreAndMinRating(null, null);

        assertThat(result).hasSize(5);
    }

    @Test
    @DisplayName("findByGenreAndMinRating with genre and minRating narrows results correctly")
    void findByGenreAndMinRating_genreAndRating_narrowsResults() {
        List<Movie> result = querydslService.findByGenreAndMinRating(Genre.SCIENCE_FICTION, 8.7);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Inception");
    }
}
