package com.cinetrack.subscription;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import java.util.Optional;

/**
 * Implementation of {@link SubscriptionRepositoryCustom}.
 *
 * <h2>Why we issue {@code SET LOCAL lock_timeout} ourselves</h2>
 * <p>Hibernate 7's {@code PostgreSQLLockingSupport} attempts to honour the
 * {@code jakarta.persistence.lock.timeout} hint by issuing
 * {@code SET LOCAL lock_timeout = N} before the {@code SELECT ... FOR UPDATE}
 * statement and then resetting it to {@code 0} (wait forever) immediately after.
 * In several Hibernate 7 / PostgreSQL dialect combinations the reset is emitted
 * before the lock acquisition actually returns, with the practical effect that
 * a waiting acquirer never times out — the second thread blocks indefinitely.</p>
 *
 * <p>To produce a deterministic, dialect-independent timeout we step around the
 * dialect: first issue {@code SET LOCAL lock_timeout = 3000} on the active
 * connection (this binds the timeout to the current transaction only, per
 * PostgreSQL semantics), then run the {@code SELECT ... FOR UPDATE} via a
 * standard JPQL query with {@link LockModeType#PESSIMISTIC_WRITE} but
 * <strong>without</strong> a lock-timeout hint, so Hibernate has no opportunity
 * to override or reset the value we just set.  Because the timeout is
 * {@code SET LOCAL}, PostgreSQL clears it automatically when the transaction
 * commits or rolls back; no manual cleanup is needed.</p>
 */
public class SubscriptionRepositoryImpl implements SubscriptionRepositoryCustom {

    static final long LOCK_TIMEOUT_MS = 3000L;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<Subscription> findByIdForUpdate(Long id) {
        // Bind a 3-second lock timeout to the *current transaction* only.
        // PostgreSQL will clear this automatically at commit/rollback.
        entityManager
                .createNativeQuery("SET LOCAL lock_timeout = " + LOCK_TIMEOUT_MS)
                .executeUpdate();

        // Acquire the row lock without passing a Hibernate lock-timeout hint,
        // which would otherwise cause the dialect to overwrite the value above.
        Query query = entityManager.createQuery(
                "SELECT s FROM Subscription s WHERE s.id = :id", Subscription.class);
        query.setParameter("id", id);
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);

        return query.getResultList().stream()
                .findFirst()
                .map(Subscription.class::cast);
    }
}
