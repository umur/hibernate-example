package com.cinetrack.notification;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.subscription.Subscription;
import com.cinetrack.subscription.SubscriptionRepository;
import com.cinetrack.subscription.SubscriptionTier;
import com.cinetrack.subscription.SubscriptionUpgradeService;
import com.cinetrack.user.AppUser;
import com.cinetrack.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Chapter 14 — Pessimistic Locking.
 *
 * Covers:
 * <ul>
 *   <li>SKIP LOCKED: multiple workers claim disjoint notification sets</li>
 *   <li>Lock timeout: PessimisticLockException when wait exceeds 3 s</li>
 *   <li>Consistent lock ordering: upgradeBoth avoids deadlock</li>
 * </ul>
 */
@DisplayName("Chapter 14 — Pessimistic Locking")
class SkipLockedTest extends AbstractIntegrationTest {

    @Autowired NotificationRepository notificationRepository;
    @Autowired NotificationWorker notificationWorker;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired SubscriptionUpgradeService upgradeService;
    @Autowired AppUserRepository userRepository;
    @Autowired PlatformTransactionManager txManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> {
            subscriptionRepository.deleteAllInBatch();
            notificationRepository.deleteAllInBatch();
            userRepository.deleteAllInBatch();
        });
    }

    // ------------------------------------------------------------------
    // Test 1: SKIP LOCKED — disjoint claim sets
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SKIP LOCKED — two concurrent workers claim disjoint notification batches")
    void skipLocked_workersClaimDisjointSets() throws Exception {
        // Insert 20 PENDING notifications
        tx.executeWithoutResult(s -> {
            List<PendingNotification> notifications = IntStream.rangeClosed(1, 20)
                    .mapToObj(i -> new PendingNotification((long) i, "Message " + i))
                    .toList();
            notificationRepository.saveAll(notifications);
        });

        CountDownLatch bothStarted = new CountDownLatch(2);
        List<Long> worker1Ids = Collections.synchronizedList(new ArrayList<>());
        List<Long> worker2Ids = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<?> f1 = pool.submit(() -> {
            tx.executeWithoutResult(s -> {
                List<PendingNotification> batch = notificationRepository.claimPendingBatch(10);
                batch.forEach(n -> {
                    worker1Ids.add(n.getId());
                    n.setStatus(NotificationStatus.PROCESSING);
                });
                notificationRepository.saveAll(batch);
                bothStarted.countDown();
                // Hold the transaction open briefly so worker-2 runs concurrently
                try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        });

        Future<?> f2 = pool.submit(() -> {
            // Give worker-1 a moment to acquire its locks first
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            tx.executeWithoutResult(s -> {
                List<PendingNotification> batch = notificationRepository.claimPendingBatch(10);
                batch.forEach(n -> {
                    worker2Ids.add(n.getId());
                    n.setStatus(NotificationStatus.PROCESSING);
                });
                notificationRepository.saveAll(batch);
                bothStarted.countDown();
            });
        });

        f1.get();
        f2.get();
        pool.shutdown();

        assertThat(worker1Ids).hasSize(10);
        assertThat(worker2Ids).hasSize(10);

        // Verify the two sets are completely disjoint
        assertThat(worker1Ids).doesNotContainAnyElementsOf(worker2Ids);

        // Together they cover all 20 notifications
        List<Long> allClaimed = new ArrayList<>(worker1Ids);
        allClaimed.addAll(worker2Ids);
        assertThat(allClaimed).hasSize(20);
    }

    // ------------------------------------------------------------------
    // Test 2: lock timeout throws when another TX holds the row lock
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Lock timeout — PessimisticLockException when waiting exceeds configured timeout")
    void lockTimeout_throwsWhenRowIsLocked() throws Exception {
        AppUser user = tx.execute(s -> userRepository.save(new AppUser("charlie", "charlie@example.com")));
        Long subId = tx.execute(s ->
                subscriptionRepository.save(new Subscription(user, SubscriptionTier.FREE)).getId()
        );

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock  = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);

        // Thread-1: holds the pessimistic lock for 5 seconds (longer than the 3 s timeout)
        Future<?> holder = pool.submit(() ->
            tx.executeWithoutResult(s -> {
                subscriptionRepository.findByIdForUpdate(subId).orElseThrow();
                lockAcquired.countDown();
                try { releaseLock.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            })
        );

        lockAcquired.await(); // wait until thread-1 actually holds the lock

        // Thread-2: tries to acquire the same lock — should time out
        assertThatThrownBy(() ->
            tx.executeWithoutResult(s ->
                subscriptionRepository.findByIdForUpdate(subId).orElseThrow()
            )
        ).satisfies(ex -> assertThat(ex)
                .isInstanceOfAny(
                        jakarta.persistence.PessimisticLockException.class,
                        org.springframework.dao.PessimisticLockingFailureException.class
                )
        );

        releaseLock.countDown(); // release thread-1
        holder.get();
        pool.shutdown();
    }

    // ------------------------------------------------------------------
    // Test 3: consistent lock ordering prevents deadlock
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Consistent lock ordering — upgradeBoth completes without deadlock")
    void upgradeBoth_noDeadlock() throws Exception {
        AppUser user = tx.execute(s -> userRepository.save(new AppUser("dana", "dana@example.com")));

        Long subAId = tx.execute(s ->
                subscriptionRepository.save(new Subscription(user, SubscriptionTier.FREE)).getId()
        );
        Long subBId = tx.execute(s ->
                subscriptionRepository.save(new Subscription(user, SubscriptionTier.FREE)).getId()
        );

        ExecutorService pool = Executors.newFixedThreadPool(2);

        // Both threads upgrade A→STANDARD and B→PREMIUM in opposite argument order.
        // Consistent lock ordering (ascending ID) inside upgradeBoth prevents deadlock.
        Future<?> f1 = pool.submit(() ->
                upgradeService.upgradeBoth(subAId, SubscriptionTier.STANDARD,
                                           subBId, SubscriptionTier.PREMIUM)
        );
        Future<?> f2 = pool.submit(() ->
                upgradeService.upgradeBoth(subBId, SubscriptionTier.STANDARD,
                                           subAId, SubscriptionTier.PREMIUM)
        );

        // Both futures must complete (one will win the lock, the other will wait then proceed)
        f1.get();
        f2.get();
        pool.shutdown();

        // Final state: both subscriptions upgraded (last writer wins, no deadlock)
        tx.execute(s -> {
            Subscription a = subscriptionRepository.findById(subAId).orElseThrow();
            Subscription b = subscriptionRepository.findById(subBId).orElseThrow();
            assertThat(a.getTier()).isNotEqualTo(SubscriptionTier.FREE);
            assertThat(b.getTier()).isNotEqualTo(SubscriptionTier.FREE);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Test 4: processBatch marks notifications DONE
    // ------------------------------------------------------------------

    @Test
    @DisplayName("processBatch — transitions notifications from PENDING to DONE")
    void processBatch_marksNotificationsDone() {
        tx.executeWithoutResult(s -> {
            List<PendingNotification> notifications = IntStream.rangeClosed(1, 5)
                    .mapToObj(i -> new PendingNotification((long) i, "Batch message " + i))
                    .toList();
            notificationRepository.saveAll(notifications);
        });

        int processed = notificationWorker.processBatch(5);
        assertThat(processed).isEqualTo(5);

        tx.execute(s -> {
            List<PendingNotification> all = notificationRepository.findAll();
            assertThat(all).allMatch(n -> n.getStatus() == NotificationStatus.DONE);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Test 5: SKIP LOCKED with no available work returns empty list
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SKIP LOCKED — returns empty list when no PENDING notifications exist")
    void skipLocked_withNoAvailableWork_returnsEmpty() {
        // Table is already empty from @BeforeEach teardown
        List<PendingNotification> result = tx.execute(s ->
                notificationRepository.claimPendingBatch(10)
        );

        assertThat(result)
                .as("Must return an empty list when no PENDING notifications exist")
                .isEmpty();
    }

    // ------------------------------------------------------------------
    // Test 6: processBatch with empty table returns 0, no exception
    // ------------------------------------------------------------------

    @Test
    @DisplayName("processBatch — empty table returns 0 and throws no exception")
    void processBatch_emptyInput_noException() {
        // No notifications inserted — worker must handle empty batch gracefully
        int processed = notificationWorker.processBatch(10);

        assertThat(processed)
                .as("processBatch must return 0 when there are no PENDING notifications")
                .isEqualTo(0);

        // Verify DB is still empty
        tx.execute(s -> {
            assertThat(notificationRepository.findAll()).isEmpty();
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Test 7: full PENDING → PROCESSING → DONE lifecycle
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Notification lifecycle — PENDING to PROCESSING to DONE for 3 notifications")
    void notificationStatus_transitions_PENDING_to_PROCESSING_to_DONE() {
        tx.executeWithoutResult(s -> {
            List<PendingNotification> notifications = IntStream.rangeClosed(1, 3)
                    .mapToObj(i -> new PendingNotification((long) i, "Lifecycle message " + i))
                    .toList();
            notificationRepository.saveAll(notifications);
        });

        // Verify initial state
        tx.execute(s -> {
            assertThat(notificationRepository.findAll())
                    .allMatch(n -> n.getStatus() == NotificationStatus.PENDING);
            return null;
        });

        // processBatch drives PENDING → PROCESSING → DONE within one transaction
        int processed = notificationWorker.processBatch(3);
        assertThat(processed).isEqualTo(3);

        // Verify final state
        tx.execute(s -> {
            List<PendingNotification> all = notificationRepository.findAll();
            assertThat(all).hasSize(3);
            assertThat(all)
                    .as("All 3 notifications must be DONE after processing")
                    .allMatch(n -> n.getStatus() == NotificationStatus.DONE);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Test 8: 3 concurrent workers claim 10 notifications without duplicates
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SKIP LOCKED — 3 concurrent workers claim 10 notifications with no duplicates")
    void concurrentWorkers_10notifications_noDoubleClaim() throws Exception {
        tx.executeWithoutResult(s -> {
            List<PendingNotification> notifications = IntStream.rangeClosed(1, 10)
                    .mapToObj(i -> new PendingNotification((long) i, "Concurrent message " + i))
                    .toList();
            notificationRepository.saveAll(notifications);
        });

        CountDownLatch startGate = new CountDownLatch(1);
        List<Long> allClaimedIds = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<?>> futures = new ArrayList<>();

        for (int w = 0; w < 3; w++) {
            futures.add(pool.submit(() -> {
                try { startGate.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                tx.executeWithoutResult(s -> {
                    List<PendingNotification> batch = notificationRepository.claimPendingBatch(10);
                    batch.forEach(n -> {
                        allClaimedIds.add(n.getId());
                        n.setStatus(NotificationStatus.PROCESSING);
                    });
                    notificationRepository.saveAll(batch);
                });
            }));
        }

        startGate.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        // All claimed IDs across all workers must be unique (SKIP LOCKED prevents double-claim)
        Set<Long> uniqueIds = new HashSet<>(allClaimedIds);
        assertThat(uniqueIds)
                .as("No notification should be claimed by more than one worker")
                .hasSameSizeAs(allClaimedIds);

        assertThat(allClaimedIds)
                .as("All 10 notifications must be claimed in total")
                .hasSize(10);
    }

    // ------------------------------------------------------------------
    // Test 9: lock timeout — short timeout throws PessimisticLockingFailureException
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Lock timeout — second acquirer throws when lock is held beyond timeout")
    void lockTimeout_exceeded_throwsException() throws Exception {
        AppUser user = tx.execute(s ->
                userRepository.save(new AppUser("eve", "eve@example.com"))
        );
        Long subId = tx.execute(s ->
                subscriptionRepository.save(new Subscription(user, SubscriptionTier.FREE)).getId()
        );

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock  = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);

        // Thread 1: holds PESSIMISTIC_WRITE lock until signalled
        Future<?> holder = pool.submit(() ->
                tx.executeWithoutResult(s -> {
                    subscriptionRepository.findByIdForUpdate(subId).orElseThrow();
                    lockAcquired.countDown();
                    try { releaseLock.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                })
        );

        lockAcquired.await();

        // Thread 2: tries to acquire the same lock — the repository's 3000 ms timeout
        // will expire because thread 1 holds the lock indefinitely until we release it.
        assertThatThrownBy(() ->
                tx.executeWithoutResult(s ->
                        subscriptionRepository.findByIdForUpdate(subId).orElseThrow()
                )
        ).satisfies(ex ->
                assertThat(ex).isInstanceOfAny(
                        jakarta.persistence.PessimisticLockException.class,
                        PessimisticLockingFailureException.class
                )
        );

        releaseLock.countDown();
        holder.get();
        pool.shutdown();
    }

    // ------------------------------------------------------------------
    // Test 10a: upgrade_singleSubscription_tierChanges
    // ------------------------------------------------------------------

    @Test
    @DisplayName("upgrade_singleSubscription_tierChanges — calling upgrade() changes the tier of a single subscription")
    void upgrade_singleSubscription_tierChanges() {
        // Arrange
        AppUser user = tx.execute(s ->
                userRepository.save(new AppUser("grace", "grace@example.com"))
        );
        Long subId = tx.execute(s ->
                subscriptionRepository.save(new Subscription(user, SubscriptionTier.FREE)).getId()
        );

        // Act
        upgradeService.upgrade(subId, SubscriptionTier.STANDARD);

        // Assert — reload and verify tier changed
        tx.execute(s -> {
            Subscription reloaded = subscriptionRepository.findById(subId).orElseThrow();
            assertThat(reloaded.getTier())
                    .as("Tier must be updated to STANDARD after upgrade()")
                    .isEqualTo(SubscriptionTier.STANDARD);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Test 10b: processBatch_zero_batchSize_returnsZero
    // ------------------------------------------------------------------

    @Test
    @DisplayName("processBatch_zero_batchSize_returnsZero — requesting 0 items claims nothing")
    void processBatch_zero_batchSize_returnsZero() {
        // Arrange — insert 3 PENDING notifications to make sure the table is non-empty
        tx.executeWithoutResult(s -> {
            IntStream.rangeClosed(1, 3)
                    .forEach(i -> notificationRepository.save(
                            new PendingNotification((long) i, "Zero-batch message " + i)));
        });

        // Act — request a batch of 0
        int processed = notificationWorker.processBatch(0);

        // Assert — nothing claimed
        assertThat(processed)
                .as("processBatch(0) must claim and process zero notifications")
                .isEqualTo(0);

        // The 3 notifications remain PENDING
        tx.execute(s -> {
            assertThat(notificationRepository.findAll())
                    .allMatch(n -> n.getStatus() == NotificationStatus.PENDING);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Test 10c: processBatch_largerThanAvailable_claimsAll
    // ------------------------------------------------------------------

    @Test
    @DisplayName("processBatch_largerThanAvailable_claimsAll — batch larger than queue size claims all available")
    void processBatch_largerThanAvailable_claimsAll() {
        // Arrange — insert exactly 5 PENDING notifications
        tx.executeWithoutResult(s -> {
            IntStream.rangeClosed(1, 5)
                    .forEach(i -> notificationRepository.save(
                            new PendingNotification((long) i, "Large-batch message " + i)));
        });

        // Act — request 100 (far more than available)
        int processed = notificationWorker.processBatch(100);

        // Assert — all 5 were claimed and processed
        assertThat(processed)
                .as("processBatch(100) must claim all 5 available notifications")
                .isEqualTo(5);

        tx.execute(s -> {
            assertThat(notificationRepository.findAll())
                    .as("All notifications must be DONE after over-sized batch")
                    .allMatch(n -> n.getStatus() == NotificationStatus.DONE);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Test 10: upgradeBoth is idempotent when called twice sequentially
    // ------------------------------------------------------------------

    @Test
    @DisplayName("upgradeBoth — sequential calls with same tiers produce correct final state")
    void upgradeBoth_idempotent_onSameSubscriptionPair() throws Exception {
        AppUser user = tx.execute(s ->
                userRepository.save(new AppUser("frank", "frank@example.com"))
        );
        Long subAId = tx.execute(s ->
                subscriptionRepository.save(new Subscription(user, SubscriptionTier.FREE)).getId()
        );
        Long subBId = tx.execute(s ->
                subscriptionRepository.save(new Subscription(user, SubscriptionTier.FREE)).getId()
        );

        // First call: FREE → STANDARD for both
        upgradeService.upgradeBoth(subAId, SubscriptionTier.STANDARD,
                                   subBId, SubscriptionTier.STANDARD);

        tx.execute(s -> {
            assertThat(subscriptionRepository.findById(subAId).orElseThrow().getTier())
                    .isEqualTo(SubscriptionTier.STANDARD);
            assertThat(subscriptionRepository.findById(subBId).orElseThrow().getTier())
                    .isEqualTo(SubscriptionTier.STANDARD);
            return null;
        });

        // Second call: STANDARD → PREMIUM for both (upgradeBoth does not guard downgrades)
        upgradeService.upgradeBoth(subAId, SubscriptionTier.PREMIUM,
                                   subBId, SubscriptionTier.PREMIUM);

        tx.execute(s -> {
            assertThat(subscriptionRepository.findById(subAId).orElseThrow().getTier())
                    .as("subA must be PREMIUM after second upgrade")
                    .isEqualTo(SubscriptionTier.PREMIUM);
            assertThat(subscriptionRepository.findById(subBId).orElseThrow().getTier())
                    .as("subB must be PREMIUM after second upgrade")
                    .isEqualTo(SubscriptionTier.PREMIUM);
            return null;
        });
    }
}
