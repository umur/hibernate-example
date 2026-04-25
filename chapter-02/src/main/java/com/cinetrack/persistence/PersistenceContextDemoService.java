package com.cinetrack.persistence;

import com.cinetrack.movie.Movie;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Demonstrates the four core persistence-context behaviours covered in Chapter 2.
 *
 * <p>The {@link EntityManager} is injected via {@code @PersistenceContext}, which
 * gives a thread-safe, transaction-scoped proxy. Each public method is annotated
 * with {@code @Transactional} so that Spring opens a Hibernate Session (i.e., a
 * persistence context) at the start of the call and closes it — flushing any
 * pending changes — at the end.
 */
@Slf4j
@Service
public class PersistenceContextDemoService {

    /**
     * Spring injects a transaction-scoped {@link EntityManager} proxy here.
     * The proxy delegates every call to the real EM bound to the current
     * transaction. This is safe for singleton beans because the proxy itself
     * holds no mutable state.
     */
    @PersistenceContext
    private EntityManager em;

    // -------------------------------------------------------------------------
    // 1. Identity Map
    // -------------------------------------------------------------------------

    /**
     * Demonstrates that within a single persistence context (transaction),
     * {@code em.find()} always returns the <em>same Java object</em> for the
     * same primary key — the identity map guarantee.
     *
     * <p>The first call issues a SELECT. The second call is served from the
     * first-level cache (identity map) with zero SQL. Because the cache returns
     * the identical object, {@code first == second} is {@code true}.
     *
     * @param id the UUID of an existing Movie
     * @return {@code true} if both lookups returned the same Java reference
     */
    @Transactional(readOnly = true)
    public boolean demonstrateIdentityMap(UUID id) {
        log.info("--- Identity Map Demo ---");

        // First find: issues SELECT … WHERE id = ?
        Movie first = em.find(Movie.class, id);
        log.info("First  find() → object@{}", Integer.toHexString(System.identityHashCode(first)));

        // Second find: NO SQL — returned from the identity map
        Movie second = em.find(Movie.class, id);
        log.info("Second find() → object@{}", Integer.toHexString(System.identityHashCode(second)));

        boolean sameReference = (first == second);
        log.info("first == second? {}", sameReference);
        return sameReference;
    }

    // -------------------------------------------------------------------------
    // 2. Dirty Checking
    // -------------------------------------------------------------------------

    /**
     * Demonstrates automatic dirty checking (write-behind).
     *
     * <p>After {@code em.find()}, the entity is <em>managed</em>. Hibernate
     * takes a snapshot of its state at load time. When the transaction commits,
     * Hibernate compares the current state against the snapshot and issues an
     * UPDATE for every field that changed — without any explicit {@code save()}
     * or {@code merge()} call.
     *
     * <p>This is the "Unit of Work" pattern: you work with plain Java objects,
     * and the persistence context tracks what needs writing.
     *
     * @param id       the UUID of an existing Movie
     * @param newTitle the title to set (will be flushed automatically on commit)
     */
    @Transactional
    public void demonstrateDirtyChecking(UUID id, String newTitle) {
        log.info("--- Dirty Checking Demo ---");

        Movie movie = em.find(Movie.class, id);
        log.info("Loaded: '{}' (managed = {})", movie.getTitle(), em.contains(movie));

        // No repository.save(), no em.merge() — just a plain setter.
        movie.setTitle(newTitle);
        log.info("Title changed to '{}' in memory. No explicit save called.", newTitle);

        // When this method returns, Spring commits the transaction.
        // Hibernate's flush compares the current state against the snapshot
        // taken at load time, detects the title change, and issues:
        //   UPDATE movies SET title = ?, … WHERE id = ? AND version = ?
        log.info("Transaction will commit → Hibernate will flush the dirty title automatically.");
    }

    // -------------------------------------------------------------------------
    // 3. Detach and Reattach
    // -------------------------------------------------------------------------

    /**
     * Demonstrates the detached state and {@code merge()}.
     *
     * <p>After {@code em.detach()}, the entity leaves the persistence context.
     * Hibernate stops tracking it: changes made to a detached entity are
     * invisible to the current session and will NOT be flushed. To bring it
     * back under management, call {@code em.merge()}, which returns a new
     * managed copy and schedules an UPDATE if any fields differ from the
     * database state.
     *
     * @param movieId UUID of an existing Movie
     * @return the managed copy returned by {@code merge()}
     */
    @Transactional
    public Movie demonstrateDetachReattach(UUID movieId) {
        log.info("--- Detach / Reattach Demo ---");

        Movie movie = em.find(Movie.class, movieId);
        log.info("After find()  → managed: {}", em.contains(movie));

        em.detach(movie);
        log.info("After detach() → managed: {}", em.contains(movie));

        // Mutations on a detached entity are not tracked.
        movie.setTitle("[DETACHED] " + movie.getTitle());
        log.info("Title mutated while detached — Hibernate is unaware.");

        // merge() copies the detached state into a new managed instance.
        // The returned reference is the managed copy; the passed-in object
        // remains detached.
        Movie managed = em.merge(movie);
        log.info("After merge()  → managed: {}", em.contains(managed));
        log.info("Original detached ref still detached: {}", !em.contains(movie));

        return managed;
    }

    // -------------------------------------------------------------------------
    // 4. FlushMode
    // -------------------------------------------------------------------------

    /**
     * Demonstrates the difference between {@link FlushModeType#AUTO} and
     * {@link FlushModeType#COMMIT}.
     *
     * <p>With {@code AUTO} (the default), Hibernate flushes before executing a
     * JPQL/criteria query that might be affected by pending changes — so that
     * the query sees up-to-date data. With {@code COMMIT}, flushing is deferred
     * entirely until commit time, meaning in-flight queries may read stale data
     * from the session but you avoid the extra flush overhead in read-heavy
     * paths.
     *
     * @param id the UUID of an existing Movie
     */
    @Transactional
    public void demonstrateFlushModes(UUID id) {
        log.info("--- FlushMode Demo ---");

        Movie movie = em.find(Movie.class, id);
        movie.setTitle("Flush Mode Test Title");

        // AUTO: Hibernate will flush before the next query if the query
        // could be affected by the pending change to 'movies'.
        em.setFlushMode(FlushModeType.AUTO);
        log.info("FlushMode set to AUTO");
        long countAuto = em.createQuery("SELECT COUNT(m) FROM Movie m", Long.class)
                .getSingleResult();
        log.info("Query result under AUTO (flush happened before query): count={}", countAuto);

        // Reset the title so the COMMIT demo starts clean.
        movie.setTitle("Flush Mode Test Title — COMMIT");

        // COMMIT: Hibernate defers all flushes until commit. The query below
        // runs against the database without flushing pending changes first.
        // Use with care — the query may return stale results.
        em.setFlushMode(FlushModeType.COMMIT);
        log.info("FlushMode set to COMMIT");
        long countCommit = em.createQuery("SELECT COUNT(m) FROM Movie m", Long.class)
                .getSingleResult();
        log.info("Query result under COMMIT (no flush before query): count={}", countCommit);

        // Restore the default before the transaction commits.
        em.setFlushMode(FlushModeType.AUTO);
    }
}
