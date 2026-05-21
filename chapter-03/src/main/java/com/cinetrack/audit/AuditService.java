package com.cinetrack.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Audit service that always commits its write in a <em>separate</em> transaction.
 *
 * <p><strong>Propagation.REQUIRES_NEW</strong> is the key here. When Spring's
 * transaction interceptor encounters this annotation it:
 * <ol>
 *   <li>Suspends the caller's active transaction (if any).</li>
 *   <li>Opens a brand-new transaction and Connection from the pool.</li>
 *   <li>Runs {@code recordEvent()} inside that new transaction.</li>
 *   <li>Commits (or rolls back) the new transaction independently.</li>
 *   <li>Resumes the suspended caller transaction.</li>
 * </ol>
 *
 * <p>Consequence: even if the outer transaction rolls back after calling this
 * method, the {@link AuditLog} row is already committed and visible to other
 * sessions. This is exactly the behaviour you want for regulatory audit trails.
 *
 * <p>Trade-off: {@code REQUIRES_NEW} always acquires a second connection from
 * the pool. Under high concurrency this can cause pool exhaustion. Size your
 * pool accordingly (HikariCP default is 10; consider a dedicated small pool
 * for audit writes).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Persists an audit entry in its own transaction.
     *
     * <p>This method is deliberately annotated with {@code REQUIRES_NEW} rather
     * than {@code REQUIRED}. If it used {@code REQUIRED}, a rollback in the
     * outer transaction would also roll back the audit entry: defeating the
     * entire purpose.
     *
     * @param entityType the JPA entity class simple name (e.g., "Review")
     * @param entityId   the surrogate key of the entity that was acted upon
     * @param action     a short verb describing the operation (e.g., "CREATE")
     * @param performedBy the username or system identifier of the actor
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog recordEvent(String entityType, Long entityId,
                                String action, String performedBy) {
        log.info("AuditService.recordEvent(): running in its own REQUIRES_NEW transaction");

        AuditLog entry = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .performedAt(Instant.now())
                .performedBy(performedBy)
                .build();

        AuditLog saved = auditLogRepository.save(entry);
        log.info("Audit log committed: id={}, entity={}/{}, action={}",
                saved.getId(), entityType, entityId, action);
        return saved;
    }
}
