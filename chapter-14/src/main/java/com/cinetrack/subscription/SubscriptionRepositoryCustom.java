package com.cinetrack.subscription;

import java.util.Optional;

/**
 * Custom repository fragment for {@link Subscription} that owns lock-acquisition
 * methods which need precise control over the JPA lock-timeout hint.
 *
 * <p>The fragment is needed because Spring Data JPA's combination of
 * {@code @Lock} + {@code @QueryHints} on a {@code @Query} method does not
 * propagate the {@code jakarta.persistence.lock.timeout} hint to the actual
 * {@code SELECT ... FOR UPDATE} statement under Hibernate 7's PostgreSQL
 * dialect.  In that path the dialect issues
 * {@code SET LOCAL lock_timeout = 3000} and immediately reverts it to
 * {@code 0} (wait forever) before executing the lock query.</p>
 *
 * <p>By going through {@code EntityManager.find(..., lockMode, hints)} we use
 * Hibernate's standard entity-loader path, where the timeout is honoured by
 * {@code PostgreSQLLockingSupport} for the duration of the lock query.</p>
 */
public interface SubscriptionRepositoryCustom {

    /**
     * Loads a {@link Subscription} with {@code PESSIMISTIC_WRITE} and a
     * 3-second lock timeout.  Throws {@code PessimisticLockException} when the
     * row is held by another transaction beyond the timeout.
     */
    Optional<Subscription> findByIdForUpdate(Long id);
}
