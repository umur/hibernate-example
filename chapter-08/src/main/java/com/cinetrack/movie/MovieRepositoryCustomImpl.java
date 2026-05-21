package com.cinetrack.movie;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;

import java.util.Optional;

/**
 * Concrete fragment that Spring Data JPA weaves into the MovieRepository proxy.
 * The class name must follow the convention: repository interface name + "Impl"
 * (or fragment interface name + "Impl", as used here).
 *
 * Session.byNaturalId() is Hibernate-specific and not available via the JPA
 * EntityManager API, so we unwrap to the underlying Hibernate Session.
 */
@RequiredArgsConstructor
public class MovieRepositoryCustomImpl implements MovieRepositoryCustom {

    private final EntityManager entityManager;

    @Override
    public Optional<Movie> findByImdbIdCached(String imdbId) {
        Session session = entityManager.unwrap(Session.class);
        Movie movie = session.byNaturalId(Movie.class)
                .using("imdbId", imdbId)
                .load();
        return Optional.ofNullable(movie);
    }
}
