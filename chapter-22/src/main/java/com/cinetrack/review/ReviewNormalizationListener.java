package com.cinetrack.review;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;

/**
 * JPA entity listener that normalises {@link Review} text fields before
 * they are written to the database.
 *
 * <p>Registered on {@link Review} via {@code @EntityListeners}. Hibernate
 * calls {@link #normalize(Review)} both on initial insert ({@code @PrePersist})
 * and on every dirty-checked update ({@code @PreUpdate}), so the rule is
 * enforced uniformly without any service-layer ceremony.</p>
 */
@Slf4j
public class ReviewNormalizationListener {

    @PrePersist
    @PreUpdate
    public void normalize(Review review) {
        if (review.getContent() != null) {
            review.setContent(review.getContent().trim());
        }
        log.debug("Normalized review id={}: content trimmed", review.getId());
    }
}
