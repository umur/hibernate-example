package com.cinetrack.media;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Typed repository for Movie only. Spring Data JPA automatically appends
 * a WHERE dtype = 'MOVIE' predicate to every query, so no Series or Episode
 * rows are ever returned: even though they share the same table.
 */
public interface MovieRepository extends JpaRepository<Movie, Long> {
}
