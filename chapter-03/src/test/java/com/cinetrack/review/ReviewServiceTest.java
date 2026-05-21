package com.cinetrack.review;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.audit.AuditLog;
import com.cinetrack.audit.AuditLogRepository;
import com.cinetrack.movie.Genre;
import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import com.cinetrack.user.AppUser;
import com.cinetrack.user.AppUserRepository;
import com.cinetrack.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Chapter 3: Transactions and the Unit of Work.
 *
 * <p>These tests exercise real transaction boundaries against a live PostgreSQL
 * database. Using {@code @SpringBootTest} (rather than a slice) ensures that
 * Spring's full transaction infrastructure: AOP proxies, event multicaster,
 * {@code TransactionSynchronizationManager}: is active.
 *
 * <h2>Test isolation</h2>
 * <p>Each test method relies on {@code @BeforeEach} to insert the prerequisite
 * data in its own transaction. Because the tests exercise commit/rollback, we
 * cannot use {@code @Transactional} on the test method itself (that would
 * prevent us from observing cross-transaction effects).
 */
@SpringBootTest
@ActiveProfiles("test")
class ReviewServiceTest extends AbstractIntegrationTest {

    @Autowired private ReviewService reviewService;
    @Autowired private UserService userService;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private MovieRepository movieRepository;
    @Autowired private AppUserRepository userRepository;
    @Autowired private TransactionTemplate txTemplate;

    // Injected so tests can query the captured event state.
    @Autowired private TestEventCapture testEventCapture;

    private Movie movie;
    private AppUser reviewer;

    @BeforeEach
    void setUp() {
        // Each test starts with a clean slate for reviews and audit logs.
        txTemplate.execute(status -> {
            reviewRepository.deleteAll();
            auditLogRepository.deleteAll();
            return null;
        });

        // Persist prerequisite entities in their own transaction.
        movie = txTemplate.execute(status ->
                movieRepository.save(Movie.builder()
                        .title("Parasite")
                        .genre(Genre.THRILLER)
                        .releaseYear(2019)
                        .rating(new BigDecimal("8.6"))
                        .build())
        );

        reviewer = txTemplate.execute(status ->
                userRepository.save(AppUser.builder()
                        .username("bong_fan_" + System.nanoTime())
                        .email("bong" + System.nanoTime() + "@example.com")
                        .passwordHash("$2a$12$placeholder")
                        .build())
        );

        testEventCapture.reset();
    }

    // -------------------------------------------------------------------------
    // Test 1: REQUIRES_NEW audit commits even when outer transaction rolls back
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("REQUIRES_NEW audit log commits even when the outer review transaction rolls back")
    void requiresNew_auditCommitsEvenOnRollback() {
        // addReviewWithAudit(rollbackAfterAudit=true) throws after recording the audit,
        // causing the outer transaction to roll back. The review INSERT is undone.
        // The audit entry is already committed in its own REQUIRES_NEW transaction.
        assertThatThrownBy(() ->
                reviewService.addReviewWithAudit(movie, reviewer, "Outstanding film", 10, true)
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("outer transaction rolls back");

        // Review must NOT exist: the outer transaction rolled back.
        List<Review> reviews = reviewRepository.findAll();
        assertThat(reviews).isEmpty();

        // Audit log MUST exist: it committed in REQUIRES_NEW before the rollback.
        List<AuditLog> auditLogs = auditLogRepository
                .findByEntityTypeAndEntityId("Review", anyReviewId());
        // We can't know the review id (it rolled back), so query all audit logs.
        List<AuditLog> allLogs = auditLogRepository.findAll();
        assertThat(allLogs)
                .isNotEmpty()
                .anySatisfy(log -> {
                    assertThat(log.getEntityType()).isEqualTo("Review");
                    assertThat(log.getAction()).isEqualTo("CREATE");
                    assertThat(log.getPerformedBy()).isEqualTo(reviewer.getUsername());
                });
    }

    // -------------------------------------------------------------------------
    // Test 2: @TransactionalEventListener fires after commit
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("@TransactionalEventListener(AFTER_COMMIT) fires after user creation commits")
    void transactionalEventListener_firesAfterCommit() {
        assertThat(testEventCapture.eventReceived()).isFalse();

        String uniqueEmail = "listener_test_" + System.nanoTime() + "@example.com";
        AppUser created = userService.createUser(
                "listener_user_" + System.nanoTime(),
                uniqueEmail,
                "$2a$12$placeholder"
        );

        // The UserCreatedEvent is published inside the transaction and delivered
        // AFTER_COMMIT. By the time createUser() returns (transaction committed),
        // the AFTER_COMMIT listener has already run synchronously.
        assertThat(testEventCapture.eventReceived()).isTrue();
        assertThat(testEventCapture.capturedEmail()).isEqualTo(uniqueEmail);
        assertThat(testEventCapture.capturedUserId()).isEqualTo(created.getId());
    }

    // -------------------------------------------------------------------------
    // Test 3: Normal addReview with REQUIRED propagation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("addReview() with REQUIRED propagation persists the review")
    void addReview_persistsSuccessfully() {
        Review saved = reviewService.addReview(movie, reviewer, "Masterpiece", 10);

        assertThat(saved.getId()).isNotNull();

        Review reloaded = reviewRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getContent()).isEqualTo("Masterpiece");
        assertThat(reloaded.getRating()).isEqualTo(10);
    }

    // -------------------------------------------------------------------------
    // Test 4: addReviewWithAudit: success path (rollbackAfterAudit = false)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("addReviewWithAudit(rollback=false) commits both the review and the audit log")
    void addReviewWithAudit_success_bothReviewAndAuditPersist() {
        Review review = reviewService.addReviewWithAudit(
                movie, reviewer, "Brilliant!", 9, false);

        assertThat(review).isNotNull();
        assertThat(review.getId()).isNotNull();

        // Review must be in the DB
        assertThat(reviewRepository.findById(review.getId())).isPresent();

        // Audit log must be in the DB
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs)
                .isNotEmpty()
                .anySatisfy(log -> {
                    assertThat(log.getEntityType()).isEqualTo("Review");
                    assertThat(log.getEntityId()).isEqualTo(review.getId());
                    assertThat(log.getAction()).isEqualTo("CREATE");
                    assertThat(log.getPerformedBy()).isEqualTo(reviewer.getUsername());
                });
    }

    // -------------------------------------------------------------------------
    // Test 5: AuditService.recordEvent() saves an AuditLog directly
    // -------------------------------------------------------------------------

    @Autowired
    private com.cinetrack.audit.AuditService auditService;

    @Test
    @DisplayName("AuditService.recordEvent() persists an AuditLog entry")
    void auditService_recordEvent_savesLog() {
        long before = auditLogRepository.count();

        com.cinetrack.audit.AuditLog saved =
                auditService.recordEvent("Movie", 42L, "VIEW", "test_user");

        assertThat(auditLogRepository.count()).isEqualTo(before + 1);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEntityType()).isEqualTo("Movie");
        assertThat(saved.getEntityId()).isEqualTo(42L);
        assertThat(saved.getAction()).isEqualTo("VIEW");
        assertThat(saved.getPerformedBy()).isEqualTo("test_user");
        assertThat(saved.getPerformedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Long anyReviewId() {
        return 0L; // used as a fallback; the test queries all logs instead
    }

}
