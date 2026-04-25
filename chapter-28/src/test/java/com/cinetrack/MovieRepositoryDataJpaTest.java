package com.cinetrack;

import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MovieRepositoryDataJpaTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("cinetrack")
            .withUsername("cinetrack")
            .withPassword("cinetrack");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.url", postgres::getJdbcUrl);
        r.add("spring.flyway.user", postgres::getUsername);
        r.add("spring.flyway.password", postgres::getPassword);
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.flyway.locations", () -> "classpath:db/migration");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private MovieRepository movieRepository;

    @BeforeEach
    void setUp() {
        movieRepository.deleteAll();

        movieRepository.save(new Movie("Inception", 2010, "SCIFI", new BigDecimal("8.8"),
                "A thief who steals corporate secrets through dream-sharing technology."));
        movieRepository.save(new Movie("Interstellar", 2014, "SCIFI", new BigDecimal("8.6"),
                "A team of explorers travel through a wormhole in space."));
        movieRepository.save(new Movie("The Dark Knight", 2008, "ACTION", new BigDecimal("9.0"),
                "Batman faces the Joker, a criminal mastermind."));
        movieRepository.save(new Movie("Parasite", 2019, "DRAMA", new BigDecimal("8.5"),
                "Greed and class discrimination threaten the symbiotic relationship between two families."));
        movieRepository.save(new Movie("Mad Max: Fury Road", 2015, "ACTION", new BigDecimal("8.1"),
                "In a post-apocalyptic wasteland, a woman rebels against a tyrannical ruler."));
    }

    @Test
    void findByGenre_returnsOnlyMatchingMovies() {
        List<Movie> scifiMovies = movieRepository.findByGenre("SCIFI");

        assertThat(scifiMovies).hasSize(2);
        assertThat(scifiMovies).extracting(Movie::getTitle)
                .containsExactlyInAnyOrder("Inception", "Interstellar");
    }

    @Test
    void findByMinRating_returnsMoviesAboveThreshold() {
        List<Movie> highRated = movieRepository.findByMinRating(new BigDecimal("8.6"));

        assertThat(highRated).hasSize(3);
        assertThat(highRated).extracting(Movie::getTitle)
                .containsExactlyInAnyOrder("Inception", "Interstellar", "The Dark Knight");
    }

    @Test
    void findTopRatedWithReviewCount_returnsLimitedResults() {
        List<Movie> top2 = movieRepository.findTopRatedWithReviewCount(2);

        assertThat(top2).hasSize(2);
        // Highest rated should be first (The Dark Knight at 9.0)
        assertThat(top2.get(0).getRating()).isGreaterThanOrEqualTo(top2.get(1).getRating());
    }

    @Test
    void findByGenre_noMatch_returnsEmptyList() {
        List<Movie> horror = movieRepository.findByGenre("HORROR");

        assertThat(horror).isEmpty();
    }

    @Test
    void findByMinRating_exactBoundary_isIncluded() {
        movieRepository.save(new Movie("Boundary Film", 2020, "DRAMA",
                new BigDecimal("7.0"), "Exactly at the threshold."));

        List<Movie> results = movieRepository.findByMinRating(new BigDecimal("7.0"));

        assertThat(results).extracting(Movie::getTitle)
                .contains("Boundary Film");
    }

    @Test
    void findByMinRating_justBelow_isExcluded() {
        movieRepository.save(new Movie("Below Threshold Film", 2020, "DRAMA",
                new BigDecimal("6.9"), "Just below the threshold."));

        List<Movie> results = movieRepository.findByMinRating(new BigDecimal("7.0"));

        assertThat(results).extracting(Movie::getTitle)
                .doesNotContain("Below Threshold Film");
    }

    @Test
    void findTopRated_limit_honored() {
        // setUp already saved 5 movies; add 15 more to reach 20 total
        for (int i = 1; i <= 15; i++) {
            movieRepository.save(new Movie(
                    "Extra Movie " + i, 2000 + i, "DRAMA",
                    new BigDecimal("5." + (i % 10)), "Overview " + i + "."));
        }

        List<Movie> top5 = movieRepository.findTopRatedWithReviewCount(5);

        assertThat(top5).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void findByGenre_nonExistentGenre_returnsEmpty() {
        List<Movie> results = movieRepository.findByGenre("NONEXISTENT");

        assertThat(results).isEmpty();
    }

    // -----------------------------------------------------------------------
    // New coverage: limit=0, case-sensitivity
    // -----------------------------------------------------------------------

    @Test
    void findTopRated_limit0_returnsEmpty() {
        // CustomMovieRepositoryImpl uses setMaxResults(limit); passing 0 means
        // JPA returns no rows (JPQL LIMIT 0).
        List<Movie> results = movieRepository.findTopRatedWithReviewCount(0);

        assertThat(results)
                .as("limit=0 must return an empty list")
                .isEmpty();
    }

    @Test
    void findByGenre_caseSensitive_doesNotMatchWrongCase() {
        // Genre is stored as-is ("SCIFI"). Querying lowercase "scifi" must return nothing
        // because PostgreSQL text comparison is case-sensitive by default and Spring Data
        // generates a literal equality predicate (no LOWER()).
        List<Movie> results = movieRepository.findByGenre("scifi");

        assertThat(results)
                .as("genre match must be case-sensitive: 'scifi' should not match 'SCIFI'")
                .isEmpty();
    }
}
