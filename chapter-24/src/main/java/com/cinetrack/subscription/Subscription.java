package com.cinetrack.subscription;

import com.cinetrack.types.Money;
import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.Map;

/**
 * Subscription entity demonstrating two custom type strategies side-by-side:
 *
 * <ol>
 *   <li><strong>{@link Money} via {@code UserType}</strong>: the {@code price}
 *       field uses {@link com.cinetrack.types.MoneyType}, registered globally by
 *       {@link com.cinetrack.types.CineTrackTypeContributor}.  Hibernate maps the
 *       field to a single JSONB column ({@code price}) without any annotation on
 *       the field itself.</li>
 *   <li><strong>{@code Map<String,Object>} via {@code @JdbcTypeCode(SqlTypes.JSON)}</strong>
 *      : the {@code metadata} field uses Hibernate's built-in JSON support backed
 *       by Jackson.  The annotation tells Hibernate's type resolution to use the
 *       JSON JDBC type descriptor, which serialises/deserialises the map
 *       transparently.</li>
 * </ol>
 */
@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SubscriptionTier tier;

    /**
     * Monetary price of this subscription.
     *
     * <p>Resolved to {@link com.cinetrack.types.MoneyType} by the global
     * {@link com.cinetrack.types.CineTrackTypeContributor}: no {@code @Type}
     * annotation needed on this field.</p>
     *
     * <p>Column DDL: {@code price JSONB NOT NULL}</p>
     */
    @Column(name = "price", columnDefinition = "jsonb", nullable = false)
    private Money price;

    /**
     * Arbitrary key-value metadata stored as PostgreSQL JSONB.
     *
     * <p>{@code @JdbcTypeCode(SqlTypes.JSON)} instructs Hibernate to use its
     * built-in Jackson-backed JSON type descriptor for this field.  Any
     * {@code Map<String, Object>} round-trips without additional configuration.</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;
}
