package com.cinetrack.watchlog;

import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.stream.Stream;

public interface WatchLogRepository extends JpaRepository<WatchLog, Long> {

    /**
     * Bulk DELETE that issues a single {@code DELETE FROM watch_logs WHERE
     * watched_at < ?} statement without loading entities into memory.
     *
     * <p>{@code @Modifying} marks this as a write operation so Spring Data
     * clears the persistence context after execution, preventing stale reads.
     */
    @Modifying
    @Query("DELETE FROM WatchLog w WHERE w.watchedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);

    /**
     * Returns all rows as a lazy {@link Stream}.
     *
     * <p>The {@code org.hibernate.fetchSize} hint instructs the JDBC driver to
     * fetch rows in chunks of 500 instead of loading the entire result set into
     * memory at once — essential for processing millions of rows without
     * exhausting the heap.
     *
     * <p>The caller MUST consume the stream inside an active transaction and
     * close it when done (try-with-resources).
     */
    @QueryHints(value = {
            @QueryHint(name = "org.hibernate.fetchSize", value = "500")
    })
    @Query("SELECT w FROM WatchLog w")
    Stream<WatchLog> streamAll();
}
