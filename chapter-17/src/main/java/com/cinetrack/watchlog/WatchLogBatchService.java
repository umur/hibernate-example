package com.cinetrack.watchlog;

import com.cinetrack.movie.Movie;
import com.cinetrack.user.AppUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Demonstrates three patterns for high-throughput write and read operations
 * against the {@code watch_logs} table.
 *
 * <h2>Pattern 1 — JPA batch insert ({@link #importBatch})</h2>
 * <p>Uses the standard JPA EntityManager with periodic {@code flush()} +
 * {@code clear()} calls. Hibernate accumulates INSERT statements in a JDBC
 * batch buffer (size = {@code hibernate.jdbc.batch_size}) and sends them to
 * the database in one round-trip per batch.  The flush/clear cycle prevents
 * the first-level cache from growing unbounded and avoids
 * {@code OutOfMemoryError} on large imports.
 *
 * <h2>Pattern 2 — StatelessSession ({@link #importStateless})</h2>
 * <p>Bypasses the persistence context entirely: no first-level cache, no dirty
 * checking, no lifecycle callbacks, no lazy loading.  Every {@code insert()}
 * call is translated directly to a JDBC statement (still batched by the
 * underlying JDBC layer).  This is the fastest option for pure bulk-write
 * workloads where you only need to persist data and do not need managed
 * entities afterwards.
 *
 * <h2>Pattern 3 — Server-side streaming ({@link #streamAll})</h2>
 * <p>Uses a Spring Data {@link Stream} with a JDBC fetch size hint so that
 * rows are fetched from the database in chunks rather than all at once.  This
 * keeps heap usage constant regardless of table size, making it safe to
 * process millions of rows inside a single transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WatchLogBatchService {

    private static final int BATCH_SIZE = 50;

    private final WatchLogRepository watchLogRepository;
    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;

    // -------------------------------------------------------------------------
    // Pattern 1: Standard JPA batch insert
    // -------------------------------------------------------------------------

    /**
     * Inserts {@code logs} using the JPA EntityManager with explicit
     * flush-and-clear every {@value #BATCH_SIZE} records.
     *
     * <p>Prerequisites for JDBC batching to actually fire:
     * <ul>
     *   <li>{@code hibernate.jdbc.batch_size} must be > 1 (set to 50 in
     *       {@code application.yml}).</li>
     *   <li>The entity must NOT use {@code IDENTITY} generation — this
     *       project uses a sequence so Hibernate can assign IDs before the
     *       INSERT, enabling true batching.</li>
     *   <li>{@code hibernate.order_inserts=true} groups INSERTs by entity
     *       type so mixed-entity loops do not break batches.</li>
     * </ul>
     *
     * @param logs already-constructed {@link WatchLog} entities to persist
     */
    @Transactional
    public void importBatch(List<WatchLog> logs) {
        log.info("Starting JPA batch import of {} records", logs.size());
        for (int i = 0; i < logs.size(); i++) {
            entityManager.persist(logs.get(i));

            if ((i + 1) % BATCH_SIZE == 0) {
                // Flush queued INSERTs to the database as a JDBC batch
                entityManager.flush();
                // Clear the L1 cache to free memory — entities are detached
                entityManager.clear();
                log.debug("Flushed batch up to record {}", i + 1);
            }
        }
        // Flush any remaining records that did not fill a full batch
        entityManager.flush();
        entityManager.clear();
        log.info("JPA batch import complete");
    }

    // -------------------------------------------------------------------------
    // Pattern 2: StatelessSession bulk import
    // -------------------------------------------------------------------------

    /**
     * Inserts records using a Hibernate {@link StatelessSession}.
     *
     * <p>A StatelessSession is obtained from the underlying Hibernate
     * {@code SessionFactory} via the JPA EntityManager's unwrap mechanism.
     * It has no first-level cache, performs no dirty checking, fires no
     * {@code @PrePersist} / {@code @PostPersist} callbacks, and does not
     * participate in the Spring-managed transaction (it manages its own
     * JDBC connection).  For raw INSERT throughput these trade-offs are
     * acceptable.
     *
     * <p>The method resolves {@link AppUser} and {@link Movie} references
     * via {@link StatelessSession#get} so that foreign-key columns are
     * populated correctly without loading full entity graphs.
     *
     * @param commands lightweight command objects describing each row to insert
     */
    public void importStateless(List<CreateWatchLogCommand> commands) {
        log.info("Starting StatelessSession import of {} records", commands.size());

        if (commands.isEmpty()) {
            log.info("StatelessSession import skipped — empty command list");
            return;
        }

        // Unwrap the Hibernate SessionFactory from the EntityManagerFactory.
        // We deliberately avoid unwrapping the shared (transactional) EntityManager
        // here because StatelessSession opens its own JDBC connection and runs
        // outside any Spring-managed transaction — calling unwrap() on the
        // shared EM would require an active transaction we are not in.
        SessionFactory sessionFactory =
                entityManagerFactory.unwrap(SessionFactory.class);

        try (StatelessSession session = sessionFactory.openStatelessSession()) {
            session.beginTransaction();
            try {
                // Resolve the shared AppUser and Movie references once — all
                // commands in this batch share the same userId/movieId so we
                // avoid repeating the same SELECT on every iteration.
                // For workloads with many distinct user/movie combinations,
                // build a local Map<Long, AppUser> / Map<Long, Movie> cache here.
                Long firstUserId  = commands.get(0).userId();
                Long firstMovieId = commands.get(0).movieId();
                AppUser sharedUser  = session.get(AppUser.class, firstUserId);
                Movie   sharedMovie = session.get(Movie.class,   firstMovieId);

                for (CreateWatchLogCommand cmd : commands) {
                    // Re-fetch only when the IDs differ from the cached references
                    AppUser user  = cmd.userId().equals(firstUserId)
                            ? sharedUser  : session.get(AppUser.class, cmd.userId());
                    Movie   movie = cmd.movieId().equals(firstMovieId)
                            ? sharedMovie : session.get(Movie.class,   cmd.movieId());

                    WatchLog wl = new WatchLog(user, movie, cmd.watchedAt(), cmd.durationSeconds());
                    session.insert(wl);
                }
                session.getTransaction().commit();
                WatchLogBatchService.log.info("StatelessSession import complete");
            } catch (Exception ex) {
                session.getTransaction().rollback();
                throw ex;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pattern 3: Server-side streaming
    // -------------------------------------------------------------------------

    /**
     * Processes all watch-log rows without loading the full result set into
     * memory.
     *
     * <p>The underlying query carries a {@code org.hibernate.fetchSize=500}
     * hint (defined on {@link WatchLogRepository#streamAll()}) that instructs
     * the PostgreSQL JDBC driver to use a server-side cursor, returning rows in
     * chunks of 500.  This keeps heap usage O(fetch_size) rather than O(table
     * size).
     *
     * <p>The stream MUST be consumed inside a transaction and closed
     * afterwards.  {@code @Transactional} guarantees the session stays open
     * for the duration of streaming.
     *
     * @param processor callback invoked once per {@link WatchLog} row
     */
    @Transactional(readOnly = true)
    public void streamAll(Consumer<WatchLog> processor) {
        log.info("Starting streaming scan of watch_logs");
        try (Stream<WatchLog> stream = watchLogRepository.streamAll()) {
            stream.forEach(processor);
        }
        log.info("Streaming scan complete");
    }
}
