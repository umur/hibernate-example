package com.cinetrack.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates users and publishes a {@link UserCreatedEvent} within the transaction.
 *
 * <p>The event is published <em>inside</em> the open transaction. Spring's
 * event infrastructure holds the event in a list of "pending events" until the
 * transaction reaches its commit phase. At that point:
 * <ul>
 *   <li>Listeners annotated with {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 *       are invoked <em>after</em> the commit succeeds.</li>
 *   <li>If the transaction rolls back, those listeners are never called — the
 *       user row doesn't exist, so sending a welcome email would be wrong.</li>
 * </ul>
 *
 * <p>This is the canonical pattern for triggering side-effects (emails, push
 * notifications, async jobs) that must not run on a rolled-back transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Persists a new user and publishes a {@link UserCreatedEvent}.
     *
     * <p>The event is dispatched via {@link ApplicationEventPublisher#publishEvent},
     * which in a transactional context enqueues it for delivery after commit.
     * Listeners using plain {@code @EventListener} receive it synchronously
     * within this transaction; listeners using {@code @TransactionalEventListener}
     * receive it after the transaction phase they declare.
     *
     * @param username     the desired username (must be unique)
     * @param email        the user's email address
     * @param passwordHash a pre-hashed password (bcrypt, argon2, etc.)
     * @return the persisted {@link AppUser}
     */
    @Transactional
    public AppUser createUser(String username, String email, String passwordHash) {
        log.info("Creating user '{}'", username);

        AppUser user = AppUser.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordHash)
                .build();

        AppUser saved = userRepository.save(user);
        log.info("User persisted with id={}; publishing UserCreatedEvent", saved.getId());

        // Publish inside the transaction. @TransactionalEventListener(AFTER_COMMIT)
        // listeners will receive this only after the INSERT commits successfully.
        eventPublisher.publishEvent(new UserCreatedEvent(saved.getId(), saved.getUsername(), saved.getEmail()));

        return saved;
    }
}
