package com.cinetrack.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Maps to the {@code app_users} table.
 * Includes {@code subscriptionTier} added by V4__add_subscription_tier.sql.
 * The expand-contract migration guarantees the column exists with a NOT NULL
 * constraint and a DEFAULT of 'FREE' before the application starts.
 */
@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    /**
     * Added via the V4 expand-contract migration.
     * New rows default to 'FREE'; existing rows were backfilled before the
     * NOT NULL constraint was applied: zero downtime for running instances.
     */
    @Column(name = "subscription_tier", nullable = false, length = 20)
    private String subscriptionTier = "FREE";

    public AppUser(String username, String email) {
        this.username = username;
        this.email = email;
    }
}
