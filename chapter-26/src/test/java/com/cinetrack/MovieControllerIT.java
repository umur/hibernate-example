package com.cinetrack;

import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;

/**
 * Integration tests for {@link com.cinetrack.movie.MovieController}.
 *
 * <p>Runs the full Spring context with a random HTTP port so that the reactive
 * web layer (WebFlux + R2DBC) is exercised end-to-end against a real
 * PostgreSQL instance provided by Testcontainers.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class MovieControllerIT {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("cinetrack")
            .withUsername("cinetrack")
            .withPassword("cinetrack");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.r2dbc.url", () -> postgres.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        r.add("spring.r2dbc.username", postgres::getUsername);
        r.add("spring.r2dbc.password", postgres::getPassword);
        r.add("spring.flyway.url", postgres::getJdbcUrl);
        r.add("spring.flyway.user", postgres::getUsername);
        r.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MovieRepository movieRepository;

    @BeforeEach
    void setUp() {
        movieRepository.deleteAll()
                .thenMany(movieRepository.saveAll(Flux.just(
                        new Movie("Inception",      2010, "DRAMA",  new BigDecimal("8.8"), "Dream heist."),
                        new Movie("Interstellar",   2014, "DRAMA",  new BigDecimal("8.6"), "Space survival."),
                        new Movie("The Dark Knight", 2008, "ACTION", new BigDecimal("9.0"), "Batman vs Joker.")
                )))
                .blockLast();
    }

    @Test
    void getMovies_returnsFlux() {
        webTestClient.get()
                .uri("/movies")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Movie.class)
                .hasSize(3);
    }

    @Test
    void getMoviesByGenre_returnsFiltered() {
        webTestClient.get()
                .uri("/movies/genre/DRAMA")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Movie.class)
                .hasSize(2)
                .value(movies -> movies.forEach(m ->
                        org.assertj.core.api.Assertions.assertThat(m.getGenre()).isEqualTo("DRAMA")));
    }

    @Test
    void getMovieById_existingId_returns200() {
        Movie saved = movieRepository.findAll().blockFirst();
        assert saved != null;

        webTestClient.get()
                .uri("/movies/{id}", saved.getId())
                .exchange()
                .expectStatus().isOk()
                .expectBody(Movie.class)
                .value(m -> org.assertj.core.api.Assertions
                        .assertThat(m.getId()).isEqualTo(saved.getId()));
    }

    @Test
    void getMovieById_nonExistentId_returns404() {
        webTestClient.get()
                .uri("/movies/{id}", Long.MAX_VALUE)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void postMovie_returns201_andPersistsRow() {
        Movie payload = new Movie(
                "Tenet", 2020, "ACTION", new BigDecimal("7.5"), "Inverted-time spy thriller.");

        webTestClient.post()
                .uri("/movies")
                .bodyValue(payload)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Movie.class)
                .value(m -> org.assertj.core.api.Assertions.assertThat(m.getId()).isNotNull());
    }

    @Test
    void deleteMovie_returns204_andRemovesRow() {
        Movie saved = movieRepository.findAll().blockFirst();
        assert saved != null;

        webTestClient.delete()
                .uri("/movies/{id}", saved.getId())
                .exchange()
                .expectStatus().isNoContent();

        org.assertj.core.api.Assertions
                .assertThat(movieRepository.findById(saved.getId()).blockOptional())
                .isEmpty();
    }
}
