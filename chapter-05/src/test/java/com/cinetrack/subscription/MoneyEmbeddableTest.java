package com.cinetrack.subscription;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.user.AppUser;
import com.cinetrack.user.AppUserRepository;
import com.cinetrack.user.EmailAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the {@link Money} {@code @Embeddable} record.
 *
 * <p>These tests confirm that Hibernate correctly inlines the two fields of
 * {@code Money} ({@code amount_cents}, {@code currency}) as plain columns on the
 * {@code subscriptions} table — no intermediate join table is created.
 */
@DisplayName("Money — @Embeddable round-trip")
class MoneyEmbeddableTest extends AbstractIntegrationTest {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ── helper ────────────────────────────────────────────────────────────────

    private AppUser savedUser(String name) {
        return userRepository.saveAndFlush(
                new AppUser(name, new EmailAddress(name + "@example.com"), "pw"));
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Money(1999, USD) survives a round-trip: amountCents=1999, currency=USD")
    void moneyRoundTrip() {
        // GIVEN
        AppUser user = savedUser("carol");
        Money price = Money.of(1999, "USD");
        Subscription sub = new Subscription(user, SubscriptionTier.BASIC, price, Instant.now());
        subscriptionRepository.saveAndFlush(sub);

        // Evict from first-level cache to force a real SELECT
        em.detach(sub);

        // WHEN
        Subscription loaded = subscriptionRepository.findById(sub.getId()).orElseThrow();

        // THEN
        assertThat(loaded.getPrice()).isNotNull();
        assertThat(loaded.getPrice().amountCents()).isEqualTo(1999);
        assertThat(loaded.getPrice().currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Money embedded fields map to separate columns, not a joined table")
    void moneyMapsToSeparateColumns() {
        // Verify via native SQL that amount_cents and currency exist directly
        // on the subscriptions table — i.e. no extra join table was created.
        AppUser user = savedUser("dave");
        Money price = Money.of(999, "EUR");
        Subscription sub = new Subscription(user, SubscriptionTier.PREMIUM, price, Instant.now());
        subscriptionRepository.saveAndFlush(sub);

        // Query the raw columns using the EntityManager's underlying EntityManager
        Object[] row = (Object[]) em
                .createNativeQuery(
                        "SELECT amount_cents, currency FROM subscriptions WHERE id = :id")
                .setParameter("id", sub.getId())
                .getSingleResult();

        assertThat(((Number) row[0]).intValue()).isEqualTo(999);
        assertThat(row[1]).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Money.of rejects negative cents")
    void moneyRejectsNegativeCents() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of(-1, "USD"))
                .withMessageContaining("must not be negative");
    }

    @Test
    @DisplayName("Money.of rejects blank currency")
    void moneyRejectsBlankCurrency() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of(100, ""))
                .withMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("Two subscriptions with different Money values are stored as separate rows with distinct amounts")
    void twoSubscriptionsWithDifferentMoneyStoredSeparately() {
        AppUser user1 = savedUser("edgar");
        AppUser user2 = savedUser("fiona");

        Subscription sub1 = new Subscription(user1, SubscriptionTier.BASIC, Money.of(999, "USD"), Instant.now());
        Subscription sub2 = new Subscription(user2, SubscriptionTier.PREMIUM, Money.of(1999, "USD"), Instant.now());
        subscriptionRepository.saveAndFlush(sub1);
        subscriptionRepository.saveAndFlush(sub2);

        em.detach(sub1);
        em.detach(sub2);

        Subscription loaded1 = subscriptionRepository.findById(sub1.getId()).orElseThrow();
        Subscription loaded2 = subscriptionRepository.findById(sub2.getId()).orElseThrow();

        assertThat(loaded1.getPrice().amountCents()).isEqualTo(999);
        assertThat(loaded2.getPrice().amountCents()).isEqualTo(1999);
        assertThat(loaded1.getPrice()).isNotEqualTo(loaded2.getPrice());
    }

    @Test
    @DisplayName("Two subscriptions with same price but different currency are stored as distinct rows")
    void samePriceDifferentCurrencyAreDistinct() {
        AppUser user1 = savedUser("george");
        AppUser user2 = savedUser("helen");

        Subscription sub1 = new Subscription(user1, SubscriptionTier.BASIC, Money.of(1000, "USD"), Instant.now());
        Subscription sub2 = new Subscription(user2, SubscriptionTier.BASIC, Money.of(1000, "EUR"), Instant.now());
        subscriptionRepository.saveAndFlush(sub1);
        subscriptionRepository.saveAndFlush(sub2);

        String currency1 = jdbcTemplate.queryForObject(
                "SELECT currency FROM subscriptions WHERE id = ?", String.class, sub1.getId());
        String currency2 = jdbcTemplate.queryForObject(
                "SELECT currency FROM subscriptions WHERE id = ?", String.class, sub2.getId());

        assertThat(currency1).isEqualTo("USD");
        assertThat(currency2).isEqualTo("EUR");
        assertThat(currency1).isNotEqualTo(currency2);
    }

    @Test
    @DisplayName("Updating Money amount persists the new value after reload")
    void updatingMoneyAmountPersists() {
        AppUser user = savedUser("ivan");
        Subscription sub = new Subscription(user, SubscriptionTier.BASIC, Money.of(500, "GBP"), Instant.now());
        subscriptionRepository.saveAndFlush(sub);

        // Update the price
        sub.setPrice(Money.of(750, "GBP"));
        subscriptionRepository.saveAndFlush(sub);

        em.detach(sub);

        Subscription reloaded = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertThat(reloaded.getPrice().amountCents()).isEqualTo(750);
        assertThat(reloaded.getPrice().currency()).isEqualTo("GBP");
    }
}
