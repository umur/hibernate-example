package com.cinetrack.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /**
     * Application-level Compare-And-Swap (CAS):
     * The WHERE clause acts as the guard — the UPDATE only applies when the
     * subscription is still ACTIVE.  The return value tells the caller whether
     * the state transition happened (1) or was already cancelled/expired (0).
     * No @Version bump is involved because this is a bulk UPDATE, not an
     * entity merge.
     */
    @Modifying
    @Query("UPDATE Subscription s SET s.status = 'CANCELLED' WHERE s.id = :id AND s.status = 'ACTIVE'")
    int cancelIfActive(@Param("id") Long id);
}
