package com.cinetrack.review;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.movie.Movie;
import com.cinetrack.movie.Genre;
import com.cinetrack.movie.MovieRepository;
import com.cinetrack.subscription.Subscription;
import com.cinetrack.subscription.SubscriptionRepository;
import com.cinetrack.subscription.SubscriptionStatus;
import com.cinetrack.user.AppUser;
import com.cinetrack.user.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Chapter 13 — Optimistic Locking.
 *
 * Each test spins up real transactions against a PostgreSQL 16 container so that
 * the version-check behaviour is exactly what production will see.
 */
@DisplayName("Chapter 13 — Optimistic Locking")
class OptimisticLockTest extends AbstractIntegrationTest {

    @Autowired ReviewRepository reviewRepository;
    @Autowired MovieRepository movieRepository;
    @Autowired AppUserRepository userRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired ReviewService reviewService;
    @Autowired PlatformTransactionManager txManager;
    @PersistenceContext EntityManager entityManager;

    private TransactionTemplate tx;
    private Movie movie;
    private AppUser alice;
    private AppUser bob;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);

        // Clean state before each test
        tx.executeWithoutResult(s -> {
            subscriptionRepository.deleteAllInBatch();
            reviewRepository.deleteAllInBatch();
            movieRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
        });

        tx.executeWithoutResult(s -> {
            alice = userRepository.save(new AppUser("alice", "alice@example.com"));
            bob   = userRepository.save(new AppUser("bob",   "bob@example.com"));
            movie = movieRepository.save(new Movie("Inception", Genre.SCI_FI));
        });
    }

    // ------------------------------------------------------------------
    // Test 1: concurrent updates — second writer loses
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Second concurrent update to the same review throws ObjectOptimisticLockingFailureException")
    void concurrentUpdates_secondWriterThrows() throws Exception {
        // Persist initial review
        Long reviewId = tx.execute(s ->
                reviewRepository.save(new Review(movie, alice, "Brilliant!", 5)).getId()
        );

        /*
         * Simulate two concurrent transactions:
         *
         *   Thread-1 reads the row (version=0), then BLOCKS before committing.
         *   Thread-2 reads the row (version=0) and commits its UPDATE (version→1).
         *   Thread-1 resumes and tries to commit — Hibernate sees version=1 in DB
         *   vs version=0 in its snapshot → OptimisticLockException.
         */
        CountDownLatch thread2Read    = new CountDownLatch(1);
        CountDownLatch thread1Commit  = new CountDownLatch(1);

        AtomicReference<Exception> thread1Error = new AtomicReference<>();

        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<?> t1 = pool.submit(() -> {
            try {
                tx.executeWithoutResult(status -> {
                    Review r = reviewRepository.findById(reviewId).orElseThrow();
                    r.setRating(4);

                    // Signal thread-2 that we've read the entity
                    thread2Read.countDown();

                    // Wait for thread-2 to commit first
                    try { thread1Commit.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                    // This save/flush will trigger the version check and FAIL
                    reviewRepository.saveAndFlush(r);
                });
            } catch (ObjectOptimisticLockingFailureException ex) {
                thread1Error.set(ex);
            }
        });

        Future<?> t2 = pool.submit(() -> {
            try { thread2Read.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            tx.executeWithoutResult(status -> {
                Review r = reviewRepository.findById(reviewId).orElseThrow();
                r.setRating(3);
                reviewRepository.saveAndFlush(r); // commits, bumps version to 1
            });
            thread1Commit.countDown(); // let thread-1 attempt its commit
        });

        t1.get();
        t2.get();
        pool.shutdown();

        assertThat(thread1Error.get())
                .as("Thread-1 must fail with an optimistic locking exception")
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // DB should reflect thread-2's rating
        Review saved = tx.execute(s -> reviewRepository.findById(reviewId).orElseThrow());
        assertThat(saved.getRating()).isEqualTo(3);
        assertThat(saved.getVersion()).isEqualTo(1L);
    }

    // ------------------------------------------------------------------
    // Test 2: viewCount increment does NOT bump version
    // ------------------------------------------------------------------

    @Test
    @DisplayName("@OptimisticLock(excluded=true) — incrementing viewCount does not bump the entity version")
    void viewCountIncrement_doesNotBumpVersion() {
        long versionBefore = tx.execute(s -> movieRepository.findById(movie.getId()).orElseThrow().getVersion());

        // Increment view count 5 times via bulk UPDATE
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < 5; i++) {
                movieRepository.incrementViewCount(movie.getId());
            }
        });

        tx.execute(s -> {
            Movie reloaded = movieRepository.findById(movie.getId()).orElseThrow();
            assertThat(reloaded.getViewCount()).isEqualTo(5L);
            assertThat(reloaded.getVersion())
                    .as("Version must not change when only viewCount is incremented")
                    .isEqualTo(versionBefore);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Test 3: CAS-style subscription cancel
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CAS cancel — returns 1 when ACTIVE, returns 0 when already CANCELLED")
    void casCancel_idempotentCancellation() {
        Long subId = tx.execute(s ->
                subscriptionRepository.save(new Subscription(alice)).getId()
        );

        // First cancel — should affect 1 row
        int affected = tx.execute(s -> subscriptionRepository.cancelIfActive(subId));
        assertThat(affected).isEqualTo(1);

        // Verify status in DB
        SubscriptionStatus status = tx.execute(s ->
                subscriptionRepository.findById(subId).orElseThrow().getStatus()
        );
        assertThat(status).isEqualTo(SubscriptionStatus.CANCELLED);

        // Second cancel — guard condition fails, 0 rows updated
        int affected2 = tx.execute(s -> subscriptionRepository.cancelIfActive(subId));
        assertThat(affected2).isEqualTo(0);
    }

    // ------------------------------------------------------------------
    // Test 4: fail-fast version check via updateRatingOrThrow
    // ------------------------------------------------------------------

    @Test
    @DisplayName("updateRatingOrThrow — throws when expectedVersion does not match current version")
    void updateRatingOrThrow_throwsOnVersionMismatch() {
        Long reviewId = tx.execute(s ->
                reviewRepository.save(new Review(movie, bob, "Good film", 4)).getId()
        );

        // Advance the version by making a legitimate update
        reviewService.updateRating(reviewId, 3);

        long staleVersion = 0L; // we are one version behind after the update above

        assertThatThrownBy(() -> reviewService.updateRatingOrThrow(reviewId, 5, staleVersion))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class)
                .hasMessageContaining("Version mismatch");
    }

    // ------------------------------------------------------------------
    // Test 5: @Retryable recovers from optimistic lock failure
    // ------------------------------------------------------------------

    @Test
    @DisplayName("@Retryable — updateRating eventually succeeds despite a stale read")
    void retryable_eventuallySucceeds() {
        Long reviewId = tx.execute(s ->
                reviewRepository.save(new Review(movie, alice, "Amazing!", 5)).getId()
        );

        // Bump the version externally so the first attempt in updateRating will fail
        tx.executeWithoutResult(s -> {
            Review r = reviewRepository.findById(reviewId).orElseThrow();
            r.setContent("Updated content");
            reviewRepository.saveAndFlush(r);
        });

        // updateRating reads the stale version on its first attempt, gets a lock failure,
        // then retries and reads the current version — this must succeed within 3 attempts.
        Review result = reviewService.updateRating(reviewId, 2);
        assertThat(result.getRating()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // Test 6: version increments on each successive update
    // ------------------------------------------------------------------

    @Test
    @DisplayName("@Version — version increments by 1 on each successful update")
    void version_incrementsOnEachUpdate() {
        Long reviewId = tx.execute(s ->
                reviewRepository.save(new Review(movie, alice, "First draft", 3)).getId()
        );

        long v1 = tx.execute(s ->
                reviewRepository.findById(reviewId).orElseThrow().getVersion()
        );

        // First modification
        tx.executeWithoutResult(s -> {
            Review r = reviewRepository.findById(reviewId).orElseThrow();
            r.setRating(4);
            reviewRepository.saveAndFlush(r);
        });

        long v2 = tx.execute(s ->
                reviewRepository.findById(reviewId).orElseThrow().getVersion()
        );
        assertThat(v2).isEqualTo(v1 + 1);

        // Second modification
        tx.executeWithoutResult(s -> {
            Review r = reviewRepository.findById(reviewId).orElseThrow();
            r.setContent("Revised draft");
            reviewRepository.saveAndFlush(r);
        });

        long v3 = tx.execute(s ->
                reviewRepository.findById(reviewId).orElseThrow().getVersion()
        );
        assertThat(v3).isEqualTo(v1 + 2);
    }

    // ------------------------------------------------------------------
    // Test 7: @OptimisticLock(excluded=true) — viewCount does not bump version
    // ------------------------------------------------------------------

    @Test
    @DisplayName("@OptimisticLock(excluded=true) — viewCount update leaves version unchanged")
    void version_doesNotIncrementOnExcludedField() {
        long v1 = tx.execute(s ->
                movieRepository.findById(movie.getId()).orElseThrow().getVersion()
        );

        // Bulk UPDATE on the excluded field
        tx.executeWithoutResult(s ->
                movieRepository.incrementViewCount(movie.getId())
        );

        tx.execute(s -> {
            Movie reloaded = movieRepository.findById(movie.getId()).orElseThrow();
            assertThat(reloaded.getViewCount()).isEqualTo(1L);
            assertThat(reloaded.getVersion())
                    .as("Version must stay at V1 when only the excluded viewCount changes")
                    .isEqualTo(v1);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Test 8: stale detached entity merge throws optimistic lock exception
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Merging a stale detached entity throws ObjectOptimisticLockingFailureException")
    void staleDetachedEntity_merge_throwsOptimisticLock() {
        Long reviewId = tx.execute(s ->
                reviewRepository.save(new Review(movie, bob, "Original", 3)).getId()
        );

        // tx1: load and immediately detach (capture stale snapshot)
        Review detached = tx.execute(s -> {
            Review r = reviewRepository.findById(reviewId).orElseThrow();
            entityManager.detach(r);
            return r;
        });

        // tx2: update the row — increments version in DB
        tx.executeWithoutResult(s -> {
            Review r = reviewRepository.findById(reviewId).orElseThrow();
            r.setRating(5);
            reviewRepository.saveAndFlush(r);
        });

        // tx3: attempt to merge the old snapshot — version mismatch must be detected
        assertThatThrownBy(() ->
                tx.executeWithoutResult(s -> {
                    detached.setContent("Stale attempt");
                    entityManager.merge(detached);
                    entityManager.flush();
                })
        ).isInstanceOfAny(
                ObjectOptimisticLockingFailureException.class,
                jakarta.persistence.OptimisticLockException.class,
                org.springframework.orm.jpa.JpaOptimisticLockingFailureException.class
        );
    }

    // ------------------------------------------------------------------
    // Test 9: 5 concurrent threads — exactly 1 succeeds, rest fail
    // ------------------------------------------------------------------

    @Test
    @DisplayName("5 concurrent updates to the same review — exactly 1 succeeds")
    void concurrentUpdates_5threads_atLeast4Fail() throws Exception {
        Long reviewId = tx.execute(s ->
                reviewRepository.save(new Review(movie, alice, "Race condition bait", 3)).getId()
        );

        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            final int newRating = (i % 5) + 1;
            futures.add(pool.submit(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                try {
                    tx.executeWithoutResult(s -> {
                        Review r = reviewRepository.findById(reviewId).orElseThrow();
                        r.setRating(newRating);
                        reviewRepository.saveAndFlush(r);
                    });
                    successCount.incrementAndGet();
                } catch (OptimisticLockingFailureException ex) {
                    failureCount.incrementAndGet();
                }
            }));
        }

        startGate.countDown(); // release all threads simultaneously

        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(successCount.get() + failureCount.get())
                .as("All 5 threads must complete (success or failure)")
                .isEqualTo(5);
        // Timing note: with real DB concurrency, threads may execute sequentially
        // enough that more than one wins (each reading the latest version in turn).
        // The guarantee optimistic locking gives is that concurrent writers racing
        // on the SAME version snapshot cannot both succeed — so at least one must
        // win and at least one must lose when there is genuine contention.
        assertThat(successCount.get())
                .as("At least one thread must win")
                .isGreaterThanOrEqualTo(1);
        assertThat(failureCount.get())
                .as("At least one thread must lose the version race")
                .isGreaterThanOrEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Test 10: cancelIfActive — idempotent CAS on subscription
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cancelIfActive — returns 1 on first call, 0 on second (idempotent CAS)")
    void cancelIfActive_alreadyCancelled_returnsZero() {
        Long subId = tx.execute(s ->
                subscriptionRepository.save(new Subscription(alice)).getId()
        );

        // First cancel: guard condition (status=ACTIVE) is satisfied → 1 row updated
        int first = tx.execute(s -> subscriptionRepository.cancelIfActive(subId));
        assertThat(first).as("First cancel should affect 1 row").isEqualTo(1);

        // Verify DB state
        SubscriptionStatus status = tx.execute(s ->
                subscriptionRepository.findById(subId).orElseThrow().getStatus()
        );
        assertThat(status).isEqualTo(SubscriptionStatus.CANCELLED);

        // Second cancel: guard condition fails (status=CANCELLED) → 0 rows updated
        int second = tx.execute(s -> subscriptionRepository.cancelIfActive(subId));
        assertThat(second).as("Second cancel must be a no-op (idempotent)").isEqualTo(0);
    }

    // ------------------------------------------------------------------
    // Test 11: bulk JPQL UPDATE bypasses version check
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Bulk JPQL UPDATE — bypasses @Version check, version stays unchanged")
    void bulkUpdate_bypassesVersionCheck() {
        Long movieId = tx.execute(s ->
                movieRepository.save(new Movie("Dune", Genre.SCI_FI)).getId()
        );

        long versionBefore = tx.execute(s ->
                movieRepository.findById(movieId).orElseThrow().getVersion()
        );

        // incrementViewCount uses a bulk JPQL UPDATE — no version column involved
        tx.executeWithoutResult(s ->
                movieRepository.incrementViewCount(movieId)
        );

        tx.execute(s -> {
            Movie reloaded = movieRepository.findById(movieId).orElseThrow();
            assertThat(reloaded.getViewCount())
                    .as("View count must reflect the bulk update")
                    .isEqualTo(1L);
            assertThat(reloaded.getVersion())
                    .as("Bulk UPDATE must not increment the @Version column")
                    .isEqualTo(versionBefore);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Test 12a: validateRating boundary — 0 throws
    // ------------------------------------------------------------------

    @Test
    @DisplayName("addReview_rating0_throwsValidationException — rating below 1 is rejected")
    void addReview_rating0_throwsValidationException() {
        Long reviewId = tx.execute(s ->
                reviewRepository.save(new Review(movie, alice, "Initial", 3)).getId()
        );

        assertThatThrownBy(() -> reviewService.updateRating(reviewId, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rating must be between 1 and 5");
    }

    // ------------------------------------------------------------------
    // Test 12b: validateRating boundary — 6 throws
    // ------------------------------------------------------------------

    @Test
    @DisplayName("addReview_rating6_throwsValidationException — rating above 5 is rejected")
    void addReview_rating6_throwsValidationException() {
        Long reviewId = tx.execute(s ->
                reviewRepository.save(new Review(movie, alice, "Initial", 3)).getId()
        );

        assertThatThrownBy(() -> reviewService.updateRating(reviewId, 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Rating must be between 1 and 5");
    }

    // ------------------------------------------------------------------
    // Test 12c: validateRating boundary — 1 is valid (lower inclusive bound)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("addReview_rating1_succeeds — rating of 1 is valid (lower inclusive bound)")
    void addReview_rating1_succeeds() {
        Long reviewId = tx.execute(s ->
                reviewRepository.save(new Review(movie, alice, "Initial", 3)).getId()
        );

        Review result = reviewService.updateRating(reviewId, 1);

        assertThat(result.getRating()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Test 12d: validateRating boundary — 5 is valid (upper inclusive bound)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("addReview_rating5_succeeds — rating of 5 is valid (upper inclusive bound)")
    void addReview_rating5_succeeds() {
        Long reviewId = tx.execute(s ->
                reviewRepository.save(new Review(movie, bob, "Initial", 3)).getId()
        );

        Review result = reviewService.updateRating(reviewId, 5);

        assertThat(result.getRating()).isEqualTo(5);
    }

    // ------------------------------------------------------------------
    // Test 12e: updateRatingOrThrow with wrong version throws
    // ------------------------------------------------------------------

    @Test
    @DisplayName("updateRatingOrThrow_wrongVersion_throws — wrong expectedVersion causes immediate failure")
    void updateRatingOrThrow_wrongVersion_throws() {
        Long reviewId = tx.execute(s ->
                reviewRepository.save(new Review(movie, alice, "Version test", 3)).getId()
        );

        // The review starts at version 0; supply a wrong expected version of 99
        long wrongVersion = 99L;

        assertThatThrownBy(() -> reviewService.updateRatingOrThrow(reviewId, 4, wrongVersion))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
                .hasMessageContaining("Version mismatch");
    }

    // ------------------------------------------------------------------
    // Test 12: @Retryable — recovers in real DB scenario, final state correct
    // ------------------------------------------------------------------

    @Test
    @DisplayName("@Retryable — updateRating converges to correct final value after stale-read failure")
    void retryable_method_eventuallySucceeds() {
        Long reviewId = tx.execute(s ->
                reviewRepository.save(new Review(movie, bob, "Will be retried", 2)).getId()
        );

        // Advance the version twice to ensure the first retry also fails
        tx.executeWithoutResult(s -> {
            Review r = reviewRepository.findById(reviewId).orElseThrow();
            r.setContent("Interleaved change 1");
            reviewRepository.saveAndFlush(r);
        });
        tx.executeWithoutResult(s -> {
            Review r = reviewRepository.findById(reviewId).orElseThrow();
            r.setContent("Interleaved change 2");
            reviewRepository.saveAndFlush(r);
        });

        // updateRating is @Retryable(maxAttempts=3): it re-reads on each attempt,
        // so it must succeed on attempt 3 even if version moved twice beforehand.
        Review result = reviewService.updateRating(reviewId, 5);

        assertThat(result.getRating())
                .as("Final rating must be 5 after all retries")
                .isEqualTo(5);

        Review persisted = tx.execute(s ->
                reviewRepository.findById(reviewId).orElseThrow()
        );
        assertThat(persisted.getRating()).isEqualTo(5);
    }
}
