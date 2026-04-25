package com.cinetrack.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * Listens for {@link UserCreatedEvent} and simulates sending a welcome email.
 *
 * <h2>Why AFTER_COMMIT?</h2>
 * <p>{@link TransactionPhase#AFTER_COMMIT} guarantees that this listener is
 * invoked only after the transaction that published the event has successfully
 * committed. This means the {@code app_users} row is durably written to disk
 * and visible to all other sessions before we attempt any side-effect.
 *
 * <p>If the transaction rolls back (e.g., due to a unique-constraint violation
 * on the username), this method is <em>never</em> called — so we will never
 * send a welcome email to a user who doesn't actually exist in the database.
 *
 * <h2>Execution context</h2>
 * <p>By default, {@code @TransactionalEventListener} runs in the same thread
 * as the original transaction, but <em>after</em> commit. The originating
 * transaction is already closed at this point, so the listener does not
 * participate in it. If the listener itself needs a transaction (e.g., to
 * persist an outbox record), it must be annotated with
 * {@code @Transactional(propagation = REQUIRES_NEW)}.
 */
@Slf4j
@Component
public class WelcomeEmailListener {

    /**
     * Fires after the user creation transaction commits.
     *
     * <p>In production this would call an email service (SendGrid, SES, etc.).
     * Here we log the intent to keep the example self-contained.
     *
     * @param event the {@link UserCreatedEvent} published by {@link UserService}
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {
        log.info("[WelcomeEmailListener] AFTER_COMMIT fired — would send welcome email to: {}",
                event.email());
        log.info("[WelcomeEmailListener] User details: id={}, username={}",
                event.userId(), event.username());
        // In production: emailService.sendWelcome(event.email(), event.username());
    }
}
