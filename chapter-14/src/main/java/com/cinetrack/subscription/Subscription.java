package com.cinetrack.subscription;

import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * Entity used to demonstrate pessimistic locking in Chapter 14.
 *
 * The key technique is {@link SubscriptionRepository#findByIdForUpdate}, which
 * issues a {@code SELECT ... FOR UPDATE} statement via
 * {@code LockModeType.PESSIMISTIC_WRITE}.  This prevents any other transaction
 * from reading or modifying the row until the lock holder commits or rolls back.
 *
 * <p>The {@code @Version} field co-exists with pessimistic locking.  In practice
 * you only need one of the two strategies; the version column is kept here to
 * match the schema and to show that they are not mutually exclusive.</p>
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(STRING)
    @Column(nullable = false)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Enumerated(STRING)
    @Column(nullable = false)
    private SubscriptionTier tier = SubscriptionTier.FREE;

    @Version
    private long version;

    public Subscription(AppUser user, SubscriptionTier tier) {
        this.user = user;
        this.tier = tier;
        this.status = SubscriptionStatus.ACTIVE;
    }
}
