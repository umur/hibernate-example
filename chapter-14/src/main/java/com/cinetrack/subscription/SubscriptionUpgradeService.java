package com.cinetrack.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstrates pessimistic locking for subscription tier upgrades.
 *
 * <h2>Why pessimistic locking here?</h2>
 * A subscription upgrade typically involves an external payment step.  If we
 * used optimistic locking, a concurrent upgrade attempt could succeed at the
 * database layer while the payment for the "losing" transaction is still
 * in-flight — leaving us with a paid-but-not-upgraded or double-upgraded state.
 * Holding a {@code SELECT ... FOR UPDATE} lock for the duration of the
 * transaction prevents any concurrent modification, making the payment +
 * status-change atomic from the database's perspective.
 *
 * <h2>Deadlock prevention via consistent lock ordering</h2>
 * When a single business operation needs to upgrade two subscriptions (e.g. a
 * family plan), always acquire locks in ascending ID order.  If thread-A locks
 * sub-1 then sub-2, and thread-B locks sub-2 then sub-1, you get a deadlock.
 * Consistent ordering eliminates the cycle.  See {@link #upgradeBoth}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionUpgradeService {

    private final SubscriptionRepository subscriptionRepository;

    /**
     * Upgrades a single subscription to the given tier.
     *
     * The pessimistic write lock is held from the SELECT until the transaction
     * commits, preventing any concurrent read-modify-write on the same row.
     *
     * @throws jakarta.persistence.PessimisticLockException when the lock cannot
     *         be acquired within the configured 3-second timeout
     */
    @Transactional
    public Subscription upgrade(Long subscriptionId, SubscriptionTier newTier) {
        log.debug("Acquiring pessimistic lock on subscription {}", subscriptionId);

        Subscription subscription = subscriptionRepository
                .findByIdForUpdate(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Subscription not found: " + subscriptionId));

        if (subscription.getTier().ordinal() >= newTier.ordinal()) {
            throw new IllegalStateException(
                    "Cannot downgrade from " + subscription.getTier() + " to " + newTier);
        }

        subscription.setTier(newTier);
        log.info("Upgraded subscription {} to tier {}", subscriptionId, newTier);
        return subscriptionRepository.save(subscription);
    }

    /**
     * Upgrades two subscriptions in a single transaction using consistent
     * lock ordering to prevent deadlocks.
     *
     * <p>Always lock the lower ID first, then the higher ID.  Any concurrent
     * call that needs both rows will follow the same order, so neither can
     * hold the second lock while waiting for the first.</p>
     */
    @Transactional
    public void upgradeBoth(Long idA, SubscriptionTier tierA,
                            Long idB, SubscriptionTier tierB) {
        // Enforce ascending lock order regardless of call-site argument order
        long firstId  = Math.min(idA, idB);
        long secondId = Math.max(idA, idB);

        log.debug("Acquiring locks in order: {} then {}", firstId, secondId);

        Subscription first = subscriptionRepository
                .findByIdForUpdate(firstId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + firstId));

        Subscription second = subscriptionRepository
                .findByIdForUpdate(secondId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + secondId));

        // Assign tiers — map back from sorted IDs to original arguments
        if (firstId == idA) {
            first.setTier(tierA);
            second.setTier(tierB);
        } else {
            first.setTier(tierB);
            second.setTier(tierA);
        }

        subscriptionRepository.save(first);
        subscriptionRepository.save(second);
    }
}
