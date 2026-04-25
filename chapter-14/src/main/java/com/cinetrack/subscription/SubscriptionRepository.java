package com.cinetrack.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Subscription}.
 *
 * <p>The pessimistic-lock query lives in the custom fragment
 * {@link SubscriptionRepositoryCustom} because Spring Data's combination of
 * {@code @Lock} + {@code @QueryHints} on a JPQL {@code @Query} does not
 * propagate the {@code jakarta.persistence.lock.timeout} hint to the actual
 * lock statement under Hibernate 7's PostgreSQL dialect.  Going through
 * {@link jakarta.persistence.EntityManager#find} with explicit hints is the
 * supported, dialect-friendly path.</p>
 */
@Repository
public interface SubscriptionRepository
        extends JpaRepository<Subscription, Long>, SubscriptionRepositoryCustom {
}
