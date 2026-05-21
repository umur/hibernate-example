package com.cinetrack.movie;

import java.util.Optional;

/**
 * Fragment interface for natural-ID lookups backed by Hibernate's
 * first-level cache. Implementations use Session.byNaturalId() so that
 * a second lookup within the same persistence context issues no SQL.
 */
public interface MovieRepositoryCustom {

    /**
     * Find a movie by its IMDb ID, exploiting Hibernate's natural-ID cache.
     * The first call resolves the surrogate key via SQL; subsequent calls
     * within the same session are served from the identity map.
     */
    Optional<Movie> findByImdbIdCached(String imdbId);
}
