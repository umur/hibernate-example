package com.cinetrack.review;

import com.cinetrack.user.UserCreatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-scoped Spring bean that captures {@link UserCreatedEvent} deliveries.
 *
 * <p>Extracted to a top-level class so that {@code @SpringBootTest}'s component
 * scan (rooted at {@code com.cinetrack}) picks it up reliably in Spring Boot 4.
 * A static nested {@code @Component} inside a test class is not guaranteed to
 * be scanned by the production {@code @SpringBootApplication}.
 */
@Component
public class TestEventCapture {

    private final AtomicBoolean received = new AtomicBoolean(false);
    private final AtomicLong    userId   = new AtomicLong(-1L);
    private volatile String     email    = null;

    @EventListener
    public void onUserCreated(UserCreatedEvent event) {
        received.set(true);
        userId.set(event.userId());
        email = event.email();
    }

    public void reset() {
        received.set(false);
        userId.set(-1L);
        email = null;
    }

    public boolean eventReceived()  { return received.get(); }
    public long    capturedUserId() { return userId.get(); }
    public String  capturedEmail()  { return email; }
}
