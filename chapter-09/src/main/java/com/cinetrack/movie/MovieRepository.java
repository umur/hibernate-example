package com.cinetrack.movie;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE;

/**
 * MovieRepository demonstrates four advanced Spring Data JPA capabilities:
 *
 * <ol>
 *   <li><b>Interface projections</b> — findByGenre returns lightweight proxies,
 *       not full Movie entities.</li>
 *   <li><b>Streaming with @QueryHints</b> — streamAllByGenre uses a server-side
 *       cursor (fetch size 50) to process large result sets without loading
 *       everything into the heap.</li>
 *   <li><b>Pessimistic locking</b> — findByIdForUpdate issues SELECT … FOR UPDATE,
 *       preventing concurrent readers from acquiring a conflicting lock.</li>
 *   <li><b>Scrolling API</b> — findFirst20By uses keyset or offset pagination
 *       introduced in Spring Data 3, avoiding the COUNT(*) overhead of
 *       traditional Page<T> queries.</li>
 * </ol>
 */
public interface MovieRepository
        extends JpaRepository<Movie, Long>, MovieRepositoryCustom, JpaSpecificationExecutor<Movie> {

    /**
     * Interface projection: Spring Data JPA generates a proxy that exposes only
     * title and rating. The underlying SQL selects only those two columns.
     */
    List<MovieSummary> findByGenre(Genre genre);

    /**
     * Server-side streaming. The HINT_FETCH_SIZE tells the JDBC driver to fetch
     * rows in batches of 50 rather than buffering the entire result set.
     * The caller MUST consume the Stream inside a transaction and close it
     * (try-with-resources) to release the underlying cursor.
     */
    @QueryHints(@QueryHint(name = HINT_FETCH_SIZE, value = "50"))
    Stream<Movie> streamAllByGenre(Genre genre);

    /**
     * Acquires a pessimistic write lock (SELECT … FOR UPDATE). Use this when
     * the application must prevent lost updates on a critical entity without
     * relying on @Version optimistic locking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Movie m WHERE m.id = :id")
    Optional<Movie> findByIdForUpdate(@Param("id") Long id);

    /**
     * Scrolling API (Spring Data 3+). Returns a Window of at most 20 movies
     * starting from the given ScrollPosition. Keyset-based scrolling avoids
     * the OFFSET performance cliff of traditional pagination.
     */
    Window<Movie> findFirst20By(ScrollPosition position, Sort sort);
}
