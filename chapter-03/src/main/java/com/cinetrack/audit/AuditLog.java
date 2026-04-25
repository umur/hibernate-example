package com.cinetrack.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Immutable audit record.
 *
 * <p>Audit logs are written in a {@code REQUIRES_NEW} transaction (see
 * {@link AuditService}), which means they commit independently of the
 * outer business transaction. Even if the outer transaction rolls back,
 * the audit entry survives — providing a durable, tamper-evident trail.
 *
 * <p>The entity intentionally has no {@code @Version} and no update path:
 * audit records are append-only.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private Instant performedAt;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @PrePersist
    void onPersist() {
        if (performedAt == null) {
            performedAt = Instant.now();
        }
    }
}
