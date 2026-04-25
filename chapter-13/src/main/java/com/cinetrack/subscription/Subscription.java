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
 * Used in the chapter to demonstrate application-level Compare-And-Swap (CAS)
 * via {@link SubscriptionRepository#cancelIfActive(Long)}.
 *
 * The {@code @Version} field participates in normal optimistic locking for
 * all other field mutations.
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
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(STRING)
    @Column(nullable = false)
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Version
    private long version;

    public Subscription(AppUser user) {
        this.user = user;
        this.status = SubscriptionStatus.ACTIVE;
    }
}
