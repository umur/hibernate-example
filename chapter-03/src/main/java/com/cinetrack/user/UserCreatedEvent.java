package com.cinetrack.user;

/**
 * Domain event published after a new {@link AppUser} is successfully persisted.
 *
 * <p>Using a Java record keeps event objects immutable and removes boilerplate.
 * The event is published via {@link org.springframework.context.ApplicationEventPublisher}
 * inside {@link UserService#createUser} <em>within</em> the active transaction,
 * so listeners registered with
 * {@link org.springframework.transaction.event.TransactionalEventListener}
 * can choose when to react:
 * <ul>
 *   <li>{@code AFTER_COMMIT}: safest; fires only if the user row actually
 *       committed. Use this for side-effects like sending welcome emails.</li>
 *   <li>{@code AFTER_ROLLBACK}: useful for compensating actions.</li>
 *   <li>{@code BEFORE_COMMIT}: fires inside the transaction; can still
 *       participate in rollback.</li>
 * </ul>
 *
 * @param userId   the generated surrogate key of the new user
 * @param username the chosen username
 * @param email    the user's email address (used by the welcome email listener)
 */
public record UserCreatedEvent(Long userId, String username, String email) {
}
