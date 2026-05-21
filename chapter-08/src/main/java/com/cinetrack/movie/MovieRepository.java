package com.cinetrack.movie;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Standard Spring Data repository extended with the natural-ID fragment.
 * Spring Data JPA detects MovieRepositoryCustomImpl at startup and composes
 * it into the proxy, so callers see a single unified interface.
 */
public interface MovieRepository extends JpaRepository<Movie, Long>, MovieRepositoryCustom {

    /**
     * Derived query: translates to WHERE imdb_id = ?1.
     * Unlike findByImdbIdCached, this always issues SQL even if the entity
     * is already in the session cache. Useful as a baseline for comparison.
     */
    Optional<Movie> findByImdbId(String imdbId);
}
