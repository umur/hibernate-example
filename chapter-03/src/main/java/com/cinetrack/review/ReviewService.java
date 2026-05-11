package com.cinetrack.review;

import com.cinetrack.audit.AuditService;
import com.cinetrack.movie.Movie;
import com.cinetrack.user.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstrates {@code @Transactional} propagation and the self-invocation trap.
 *
 * <h2>Propagation recap</h2>
 * <ul>
 *   <li>{@code REQUIRED} (default): join an existing transaction or start one.
 *       All work inside shares a single commit/rollback boundary.</li>
 *   <li>{@code REQUIRES_NEW}: always suspend the current transaction and open
 *       a fresh one. The new transaction commits or rolls back independently.</li>
 * </ul>
 *
 * <h2>The self-invocation trap</h2>
 * Spring's {@code @Transactional} works via a CGLIB or JDK proxy that wraps
 * the bean. When external code calls {@code reviewService.someMethod()}, it
 * goes through the proxy and Spring can intercept it to start/join a
 * transaction. But when <em>this class</em> calls {@code this.someMethod()}
 * internally, the call bypasses the proxy entirely: no transaction demarcation
 * occurs. See {@link #selfInvocationTrap()} for a concrete example.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final AuditService auditService;

    // -------------------------------------------------------------------------
    // addReview: REQUIRED propagation (default)
    // -------------------------------------------------------------------------

    /**
     * Saves a review using the default {@code REQUIRED} propagation.
     *
     * <p>If a transaction is already active (e.g., the caller opened one),
     * this method joins it. If not, a new transaction is started. Either way,
     * the review INSERT is part of a single atomic unit of work.
     *
     * @param movie      the movie being reviewed (must be a managed or detached entity)
     * @param reviewer   the user submitting the review
     * @param content    review text
     * @param rating     score 1–10
     * @return the persisted {@link Review} with its generated id
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public Review addReview(Movie movie, AppUser reviewer, String content, int rating) {
        log.info("ReviewService.addReview(): participating in current transaction (REQUIRED)");

        Review review = Review.builder()
                .movie(movie)
                .reviewer(reviewer)
                .content(content)
                .rating(rating)
                .build();

        Review saved = reviewRepository.save(review);
        log.info("Review saved: id={}", saved.getId());
        return saved;
    }

    // -------------------------------------------------------------------------
    // addReviewWithAudit: shows REQUIRES_NEW audit surviving outer rollback
    // -------------------------------------------------------------------------

    /**
     * Saves a review and records an audit entry, then deliberately throws to
     * roll back the review INSERT: while the audit row survives.
     *
     * <p>Flow:
     * <ol>
     *   <li>Outer {@code REQUIRED} transaction starts.</li>
     *   <li>{@code addReview()} joins the outer transaction: review is staged.</li>
     *   <li>{@code auditService.recordEvent()} opens a {@code REQUIRES_NEW} transaction,
     *       inserts the audit row, and commits it immediately.</li>
     *   <li>If {@code rollbackAfterAudit} is {@code true}, a {@link RuntimeException}
     *       is thrown, rolling back the outer transaction: the review INSERT is lost.</li>
     *   <li>The audit row is unaffected because it committed in its own transaction.</li>
     * </ol>
     *
     * @param movie             the movie being reviewed
     * @param reviewer          the user submitting the review
     * @param content           review text
     * @param rating            score 1–10
     * @param rollbackAfterAudit if true, throws after audit to simulate outer rollback
     * @return the persisted review (only if {@code rollbackAfterAudit} is false)
     */
    @Transactional
    public Review addReviewWithAudit(Movie movie, AppUser reviewer,
                                     String content, int rating,
                                     boolean rollbackAfterAudit) {
        log.info("ReviewService.addReviewWithAudit(): outer REQUIRED transaction");

        Review review = addReview(movie, reviewer, content, rating);

        // auditService.recordEvent() runs in REQUIRES_NEW: commits independently.
        auditService.recordEvent("Review", review.getId(), "CREATE", reviewer.getUsername());

        if (rollbackAfterAudit) {
            log.warn("Simulating failure after audit: outer transaction will roll back");
            throw new RuntimeException("Simulated failure: outer transaction rolls back, audit survives");
        }

        return review;
    }

    // -------------------------------------------------------------------------
    // selfInvocationTrap: documented BAD example
    // -------------------------------------------------------------------------

    /**
     * <strong>BAD EXAMPLE: self-invocation bypasses the Spring proxy.</strong>
     *
     * <p>This non-transactional method calls {@code this.transactionalInnerMethod()}
     * directly. Because the call goes through {@code this} (the raw bean object)
     * rather than the Spring proxy, the {@code @Transactional} annotation on
     * {@code transactionalInnerMethod()} is completely ignored: no transaction
     * is started.
     *
     * <p>The fix: inject {@code ReviewService} into itself (via
     * {@code @Autowired} on a setter, or via {@code ApplicationContext.getBean()}),
     * or extract the inner method to a separate Spring bean so the call always
     * goes through the proxy.
     *
     * <p>This method is intentionally left untested to avoid masking the bug.
     * The log statement documents what a developer would observe at runtime:
     * no transaction, no rollback on failure.
     */
    public void selfInvocationTrap() {
        log.warn("=== SELF-INVOCATION TRAP ===");
        log.warn("Calling this.transactionalInnerMethod() directly: the proxy is bypassed.");
        log.warn("@Transactional on transactionalInnerMethod() will have NO effect.");

        // BAD: calling via `this` bypasses the proxy: no transaction demarcation.
        this.transactionalInnerMethod();

        log.warn("If transactionalInnerMethod() threw, there would be NO rollback.");
        log.warn("Fix: call through the Spring proxy (inject self or extract to another bean).");
    }

    /**
     * Intended to run inside a transaction, but only works correctly when called
     * through the Spring proxy: i.e., from external code, not from within
     * {@code ReviewService} itself.
     */
    @Transactional
    public void transactionalInnerMethod() {
        log.info("transactionalInnerMethod(): transaction active? Only if called via proxy.");
        // In a real scenario this would do DB work. The point is that if reached
        // via selfInvocationTrap(), it runs WITHOUT a transaction.
    }
}
