package com.cinetrack.movie;

import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.List;

/**
 * Repository for {@link Movie}.
 *
 * <h2>Query cache</h2>
 * <p>{@code findByGenre} is annotated with the Hibernate-specific
 * {@code org.hibernate.cacheable} query hint.  When the query cache is enabled
 * ({@code hibernate.cache.use_query_cache=true}), Hibernate stores the result
 * set (as a list of entity PKs) in the {@code default-query-results-region}.
 * On the next execution with the same parameters Hibernate:
 * <ol>
 *   <li>Finds the PK list in the query cache.</li>
 *   <li>Resolves each PK against the entity second-level cache (or the
 *       database if the entity is not cached).</li>
 * </ol>
 * <p>This is only worthwhile for queries whose parameters and results change
 * infrequently.  Any write to the {@code movies} table invalidates the
 * query-cache entry via the {@code default-update-timestamps-region}.
 */
public interface MovieRepository extends JpaRepository<Movie, Long> {

    @QueryHints({
            @QueryHint(name = "org.hibernate.cacheable", value = "true")
    })
    List<Movie> findByGenre(String genre);
}
