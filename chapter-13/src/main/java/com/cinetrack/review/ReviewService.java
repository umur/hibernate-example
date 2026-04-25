package com.cinetrack.review;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstrates the two standard strategies for dealing with optimistic lock
 * collisions in a Spring application:
 *
 * <ol>
 *   <li><b>Automatic retry</b> — {@link #updateRating} uses {@code @Retryable}
 *       to transparently re-attempt the transaction when the version check
 *       fails.  Each retry re-reads the entity, so it automatically picks up
 *       the winner's changes.  Suitable when the operation is idempotent or
 *       "last writer wins" is acceptable.</li>
 *   <li><b>Fail-fast with client feedback</b> — {@link #updateRatingOrThrow}
 *       accepts the caller's expected version and throws immediately when there
 *       is a mismatch, letting the API layer return a 409 Conflict.  Suitable
 *       for collaborative editing where you want to tell the user "someone else
 *       changed this".</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;

    // ------------------------------------------------------------------
    // Strategy 1: automatic retry via @Retryable
    // ------------------------------------------------------------------

    /**
     * Updates the rating on a review.  If another transaction commits a change
     * to the same row between our read and our flush, Hibernate throws
     * {@code ObjectOptimisticLockingFailureException}.  Spring Retry catches
     * that exception, waits {@code delay * multiplier^attempt} ms, then calls
     * this method again from scratch — which re-reads the now-current row.
     *
     * <p>The transaction annotation ensures that the entire read-modify-write
     * cycle (including the version check at flush time) happens within a single
     * unit of work.</p>
     */
    @Transactional
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public Review updateRating(Long reviewId, int newRating) {
        log.debug("Attempting to update review {} to rating {}", reviewId, newRating);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found: " + reviewId));

        validateRating(newRating);
        review.setRating(newRating);

        // flush happens at transaction commit; version check occurs here
        return reviewRepository.save(review);
    }

    // ------------------------------------------------------------------
    // Strategy 2: CAS-style fail-fast — caller supplies expected version
    // ------------------------------------------------------------------

    /**
     * Updates the rating only when the caller's {@code expectedVersion} matches
     * the current database version.  If another writer has already bumped the
     * version, we throw {@code ObjectOptimisticLockingFailureException} without
     * retrying, letting the caller (e.g. a REST controller) return HTTP 409.
     *
     * <p>This pattern is preferred in collaborative UIs where the user should
     * be informed that the document changed beneath them.</p>
     *
     * @throws ObjectOptimisticLockingFailureException when the entity version
     *         in the database does not match {@code expectedVersion}
     */
    @Transactional
    public Review updateRatingOrThrow(Long reviewId, int newRating, long expectedVersion) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found: " + reviewId));

        if (review.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(
                    "Version mismatch: expected " + expectedVersion
                            + " but found " + review.getVersion()
                            + " on Review#" + reviewId,
                    reviewId
            );
        }

        validateRating(newRating);
        review.setRating(newRating);
        return reviewRepository.save(review);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void validateRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5, got: " + rating);
        }
    }
}
