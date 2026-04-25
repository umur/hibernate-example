package com.cinetrack.subscription;

/**
 * Subscription tier stored as a VARCHAR string via {@code @Enumerated(EnumType.STRING)}.
 */
public enum SubscriptionTier {
    FREE,
    BASIC,
    PREMIUM
}
