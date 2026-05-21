package com.cinetrack.subscription;

import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

/**
 * Subscription entity demonstrating {@link DynamicInsert}, {@link DynamicUpdate},
 * {@link Embedded} with a record embeddable, and optimistic locking via {@link Version}.
 *
 * <h2>{@code @DynamicInsert}</h2>
 * Causes Hibernate to omit NULL columns from the INSERT statement.  Combined with
 * database DEFAULT expressions, this lets the DB supply sensible defaults (e.g.
 * {@code DEFAULT 0} for version) without the application having to set every field.
 *
 * <h2>{@code @DynamicUpdate}</h2>
 * Only dirty fields appear in UPDATE SQL.  For a subscription record that frequently
 * has only {@code end_date} or {@code tier} changed, this avoids touching
 * {@code amount_cents} and {@code currency} unnecessarily.
 *
 * <h2>{@code @Embedded Money price}</h2>
 * The {@link Money} record's two fields ({@code amount_cents}, {@code currency}) are
 * mapped as regular columns on the {@code subscriptions} table: no join needed.
 *
 * <h2>Optimistic locking</h2>
 * {@link Version} on a {@code long} field tells Hibernate to append
 * {@code AND version = ?} to every UPDATE and increment the column on success.
 * A concurrent modification throws {@code OptimisticLockException}.
 */
@Entity
@Table(name = "subscriptions")
@DynamicInsert
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SubscriptionTier tier;

    /**
     * Monetary price: composed inline via {@code @Embedded}.
     * Hibernate maps {@code Money.amountCents} → {@code amount_cents}
     * and {@code Money.currency} → {@code currency} on the same row.
     */
    @Embedded
    private Money price;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    /**
     * Optimistic lock version.  Hibernate reads and writes this column
     * automatically; application code should never modify it directly.
     */
    @Version
    private long version;

    public Subscription(AppUser user, SubscriptionTier tier, Money price, Instant startDate) {
        this.user = user;
        this.tier = tier;
        this.price = price;
        this.startDate = startDate;
    }
}
