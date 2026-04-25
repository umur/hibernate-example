package com.cinetrack.subscription;

import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Subscription entity.
 *
 * <p>{@code @Version} provides optimistic locking — critical for subscription
 * state transitions (e.g., concurrent CANCEL and RENEW requests on the same row).
 * Hibernate appends {@code AND version = ?} to every UPDATE; if another
 * transaction already incremented the version, an {@link jakarta.persistence.OptimisticLockException}
 * is thrown and the caller must retry.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "user")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    private SubscriptionTier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /**
     * Optimistic lock token. Never set this field manually.
     * Hibernate manages it automatically on every UPDATE.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
