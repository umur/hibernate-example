package com.cinetrack.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * Represents a work item in a durable notification queue.
 *
 * The {@link NotificationRepository#claimPendingBatch} query uses
 * {@code FOR UPDATE SKIP LOCKED} so that multiple worker threads (or pods)
 * can each claim their own disjoint batch of rows without blocking each other.
 * This is the PostgreSQL-native alternative to message brokers for moderate
 * throughput scenarios.
 *
 * Lifecycle: PENDING → PROCESSING → DONE
 */
@Entity
@Table(name = "notification_queue")
@Getter
@Setter
@NoArgsConstructor
public class PendingNotification {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "created_at")
    private Instant createdAt;

    public PendingNotification(Long userId, String message) {
        this.userId = userId;
        this.message = message;
        this.status = NotificationStatus.PENDING;
        this.createdAt = Instant.now();
    }
}
