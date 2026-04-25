package com.cinetrack.batch;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.movie.Genre;
import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MovieImportService}.
 *
 * <p>These tests verify the per-item transaction behaviour: each movie is
 * committed in its own transaction, so a validation failure for one item
 * must not affect the others.
 */
@SpringBootTest
@ActiveProfiles("test")
class MovieImportServiceTest extends AbstractIntegrationTest {

    @Autowired private MovieImportService movieImportService;
    @Autowired private MovieRepository    movieRepository;
    @Autowired private TransactionTemplate txTemplate;

    @BeforeEach
    void clean() {
        txTemplate.execute(st -> {
            movieRepository.deleteAll();
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Test 1: all valid items are committed
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("importMovies() commits every valid item in its own transaction")
    void importMovies_allValid_allCommitted() {
        List<Movie> movies = List.of(
                validMovie("The Godfather",  Genre.DRAMA,   1972),
                validMovie("Goodfellas",     Genre.THRILLER, 1990),
                validMovie("Casino",         Genre.DRAMA,   1995)
        );

        MovieImportService.ImportResult result = movieImportService.importMovies(movies);

        assertThat(result.importedTitles()).containsExactlyInAnyOrder(
                "The Godfather", "Goodfellas", "Casino");
        assertThat(result.skippedTitles()).isEmpty();
        assertThat(movieRepository.count()).isEqualTo(3);
    }

    // -------------------------------------------------------------------------
    // Test 2: invalid items are skipped; valid ones still commit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("importMovies() skips invalid items and commits the remaining valid ones")
    void importMovies_mixedValidity_onlyValidOnesCommitted() {
        Movie blankTitle    = Movie.builder().title("").genre(Genre.ACTION).releaseYear(2000).build();
        Movie nullGenre     = Movie.builder().title("No Genre").genre(null).releaseYear(2001).build();
        Movie badYear       = Movie.builder().title("Future Film").genre(Genre.SCI_FI).releaseYear(3000).build();
        Movie validOne      = validMovie("Inception", Genre.SCI_FI, 2010);
        Movie anotherValid  = validMovie("Parasite",  Genre.THRILLER, 2019);

        MovieImportService.ImportResult result = movieImportService.importMovies(
                List.of(blankTitle, validOne, nullGenre, badYear, anotherValid));

        assertThat(result.importedTitles()).containsExactlyInAnyOrder("Inception", "Parasite");
        assertThat(result.skippedTitles()).hasSize(3);
        assertThat(movieRepository.count()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // Test 3: single invalid item — nothing is committed
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("importMovies() with one invalid item commits nothing")
    void importMovies_singleInvalidItem_nothingCommitted() {
        Movie blank = Movie.builder().title("   ").genre(Genre.DRAMA).releaseYear(2005).build();

        MovieImportService.ImportResult result = movieImportService.importMovies(List.of(blank));

        assertThat(result.importedTitles()).isEmpty();
        assertThat(result.skippedTitles()).hasSize(1);
        assertThat(movieRepository.count()).isZero();
    }

    // -------------------------------------------------------------------------
    // Test 4: empty list produces an empty result
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("importMovies() with an empty list returns an empty ImportResult")
    void importMovies_emptyList_returnsEmptyResult() {
        MovieImportService.ImportResult result = movieImportService.importMovies(List.of());

        assertThat(result.importedTitles()).isEmpty();
        assertThat(result.skippedTitles()).isEmpty();
        assertThat(movieRepository.count()).isZero();
    }

    // -------------------------------------------------------------------------
    // Test 5: release-year boundary — exactly 1888 and 2100 are valid
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("importMovies() accepts release years at the inclusive boundaries 1888 and 2100")
    void importMovies_boundaryYears_accepted() {
        List<Movie> movies = List.of(
                validMovieWithYear("Roundhay Garden Scene", Genre.DOCUMENTARY, 1888),
                validMovieWithYear("Future Classic",        Genre.SCI_FI,      2100)
        );

        MovieImportService.ImportResult result = movieImportService.importMovies(movies);

        assertThat(result.importedTitles()).hasSize(2);
        assertThat(result.skippedTitles()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 6: release year just outside the valid range is rejected
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("importMovies() rejects a release year outside [1888, 2100]")
    void importMovies_outOfRangeYear_skipped() {
        Movie tooOld  = validMovieWithYear("Too Old",  Genre.DOCUMENTARY, 1887);
        Movie tooNew  = validMovieWithYear("Too New",  Genre.SCI_FI,      2101);
        Movie perfect = validMovie("Perfect",          Genre.DRAMA,       2000);

        MovieImportService.ImportResult result = movieImportService.importMovies(
                List.of(tooOld, perfect, tooNew));

        assertThat(result.importedTitles()).containsExactly("Perfect");
        assertThat(result.skippedTitles()).hasSize(2);
        assertThat(movieRepository.count()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Test 7: whitespace-only title is rejected
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("importMovies() skips a movie whose title is whitespace only")
    void importMovies_whitespaceTitle_skipped() {
        Movie whitespace = Movie.builder()
                .title("   ")
                .genre(Genre.DRAMA)
                .releaseYear(2010)
                .build();

        MovieImportService.ImportResult result = movieImportService.importMovies(List.of(whitespace));

        assertThat(result.importedTitles()).isEmpty();
        assertThat(result.skippedTitles()).hasSize(1);
        assertThat(movieRepository.count()).isZero();
    }

    // -------------------------------------------------------------------------
    // Test 8: year exactly 1888 is accepted (inclusive lower boundary)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("importMovies() accepts release year exactly 1888 (inclusive minimum)")
    void importMovies_yearExactly1888_accepted() {
        Movie movie = validMovieWithYear("Roundhay Garden Scene", Genre.DOCUMENTARY, 1888);

        MovieImportService.ImportResult result = movieImportService.importMovies(List.of(movie));

        assertThat(result.importedTitles()).containsExactly("Roundhay Garden Scene");
        assertThat(result.skippedTitles()).isEmpty();
        assertThat(movieRepository.count()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Test 9: year exactly 2100 is accepted (inclusive upper boundary)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("importMovies() accepts release year exactly 2100 (inclusive maximum)")
    void importMovies_yearExactly2100_accepted() {
        Movie movie = validMovieWithYear("Far Future Film", Genre.SCI_FI, 2100);

        MovieImportService.ImportResult result = movieImportService.importMovies(List.of(movie));

        assertThat(result.importedTitles()).containsExactly("Far Future Film");
        assertThat(result.skippedTitles()).isEmpty();
        assertThat(movieRepository.count()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Movie validMovie(String title, Genre genre, int year) {
        return Movie.builder()
                .title(title)
                .genre(genre)
                .releaseYear(year)
                .rating(new BigDecimal("7.5"))
                .build();
    }

    private Movie validMovieWithYear(String title, Genre genre, int year) {
        return Movie.builder()
                .title(title)
                .genre(genre)
                .releaseYear(year)
                .build();
    }
}
