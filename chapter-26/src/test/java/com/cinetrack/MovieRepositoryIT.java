package com.cinetrack;

import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

class MovieRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private MovieRepository movieRepository;

    @BeforeEach
    void setUp() {
        movieRepository.deleteAll().block();
    }

    @Test
    void save_persistsMovieAndAssignsId() {
        Movie movie = new Movie("Inception", 2010, "SCIFI", new BigDecimal("8.8"),
                "A thief who steals corporate secrets via dream-sharing technology.");

        StepVerifier.create(movieRepository.save(movie))
                .assertNext(saved -> {
                    assert saved.getId() != null;
                    assert "Inception".equals(saved.getTitle());
                    assert "SCIFI".equals(saved.getGenre());
                })
                .verifyComplete();
    }

    @Test
    void findByGenre_returnsOnlyMatchingMovies() {
        Flux<Movie> setup = movieRepository.saveAll(Flux.just(
                new Movie("Inception", 2010, "SCIFI", new BigDecimal("8.8"), "Dream heist film."),
                new Movie("Interstellar", 2014, "SCIFI", new BigDecimal("8.6"), "Space survival film."),
                new Movie("The Dark Knight", 2008, "ACTION", new BigDecimal("9.0"), "Batman vs Joker.")
        ));

        StepVerifier.create(setup.then(Mono.empty())
                        .thenMany(movieRepository.findByGenre("SCIFI")))
                .expectNextMatches(m -> "SCIFI".equals(m.getGenre()))
                .expectNextMatches(m -> "SCIFI".equals(m.getGenre()))
                .verifyComplete();
    }

    @Test
    void findByGenre_noMatch_returnsEmpty() {
        StepVerifier.create(movieRepository.findByGenre("HORROR"))
                .verifyComplete();
    }

    @Test
    void deleteById_removesMovie() {
        Mono<Void> workflow = movieRepository.save(
                        new Movie("Oppenheimer", 2023, "DRAMA", new BigDecimal("8.5"), "The atomic bomb story."))
                .flatMap(saved -> movieRepository.deleteById(saved.getId()))
                .then();

        StepVerifier.create(workflow)
                .verifyComplete();

        StepVerifier.create(movieRepository.findAll())
                .verifyComplete();
    }

    @Test
    void findAll_afterMultipleSaves_returnsAll() {
        Flux<Movie> saves = movieRepository.saveAll(Flux.just(
                new Movie("Film One",   2001, "DRAMA",  new BigDecimal("7.0"), "Overview one."),
                new Movie("Film Two",   2002, "ACTION", new BigDecimal("7.1"), "Overview two."),
                new Movie("Film Three", 2003, "SCIFI",  new BigDecimal("7.2"), "Overview three.")
        ));

        StepVerifier.create(saves.then(movieRepository.count()))
                .expectNextMatches(n -> n >= 3)
                .verifyComplete();
    }

    @Test
    void deleteById_thenFindById_returnsEmpty() {
        Mono<Void> workflow = movieRepository.save(
                        new Movie("Gone Movie", 2020, "HORROR", new BigDecimal("5.0"), "A scary film."))
                .flatMap(saved -> movieRepository.deleteById(saved.getId())
                        .then(movieRepository.findById(saved.getId()))
                        .doOnNext(m -> { throw new AssertionError("Expected empty but found: " + m.getTitle()); })
                        .then());

        StepVerifier.create(workflow)
                .verifyComplete();
    }

    @Test
    void count_afterInserts_isCorrect() {
        Mono<Long> countBefore = movieRepository.count();

        Flux<Movie> inserts = movieRepository.saveAll(Flux.just(
                new Movie("Count A", 2011, "DRAMA",  new BigDecimal("6.0"), "Ov A."),
                new Movie("Count B", 2012, "DRAMA",  new BigDecimal("6.1"), "Ov B."),
                new Movie("Count C", 2013, "ACTION", new BigDecimal("6.2"), "Ov C."),
                new Movie("Count D", 2014, "SCIFI",  new BigDecimal("6.3"), "Ov D."),
                new Movie("Count E", 2015, "SCIFI",  new BigDecimal("6.4"), "Ov E.")
        ));

        StepVerifier.create(
                countBefore.flatMap(before ->
                        inserts.then(movieRepository.count())
                                .map(after -> after - before)))
                .expectNext(5L)
                .verifyComplete();
    }

    // -----------------------------------------------------------------------
    // New coverage: null title, findByGenre after delete
    // -----------------------------------------------------------------------

    @Test
    void save_withNullTitle_throwsOrPersistsNull() {
        // The movies DDL declares title VARCHAR(255) NOT NULL.
        // R2DBC propagates the DB constraint as an error signal on the Mono.
        Movie movie = new Movie(null, 2023, "DRAMA", new BigDecimal("7.0"), "No title film.");

        StepVerifier.create(movieRepository.save(movie))
                .verifyError();
    }

    @Test
    void findByGenre_afterDelete_excludesDeleted() {
        // Save two MYSTERY movies, delete one, assert only one remains via findByGenre.
        Flux<Movie> setup = movieRepository.saveAll(Flux.just(
                new Movie("Mystery One", 2020, "MYSTERY", new BigDecimal("7.5"), "First mystery."),
                new Movie("Mystery Two", 2021, "MYSTERY", new BigDecimal("7.8"), "Second mystery.")
        ));

        Mono<Long> countAfterDelete = setup
                .collectList()
                .flatMap(saved -> {
                    Long idToDelete = saved.get(0).getId();
                    return movieRepository.deleteById(idToDelete);
                })
                .then(movieRepository.findByGenre("MYSTERY").count());

        StepVerifier.create(countAfterDelete)
                .expectNext(1L)
                .verifyComplete();
    }
}
