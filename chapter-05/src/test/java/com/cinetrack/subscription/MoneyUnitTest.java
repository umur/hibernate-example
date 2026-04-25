package com.cinetrack.subscription;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Pure unit tests for the {@link Money} value object.
 * No Spring context, no database — just the domain rules.
 */
@DisplayName("Money — value object unit tests")
class MoneyUnitTest {

    @Test
    @DisplayName("Money.of(0, USD) is valid — zero amount is allowed")
    void zeroAmountIsAllowed() {
        Money money = Money.of(0, "USD");
        assertThat(money.amountCents()).isZero();
        assertThat(money.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Money.equals — same amount and currency are equal")
    void equalsSameAmountAndCurrency() {
        Money a = Money.of(500, "EUR");
        Money b = Money.of(500, "EUR");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("Money.equals — different currency means not equal")
    void equalsDifferentCurrencyNotEqual() {
        Money usd = Money.of(500, "USD");
        Money eur = Money.of(500, "EUR");
        assertThat(usd).isNotEqualTo(eur);
    }

    @Test
    @DisplayName("Money.equals — different amount means not equal")
    void equalsDifferentAmountNotEqual() {
        Money a = Money.of(100, "USD");
        Money b = Money.of(200, "USD");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Money.of(-1, USD) throws IllegalArgumentException")
    void negativeAmountThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of(-1, "USD"))
                .withMessageContaining("must not be negative");
    }

    @Test
    @DisplayName("Money.of(100, null) throws IllegalArgumentException")
    void nullCurrencyThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of(100, null))
                .withMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("Money.of(100, \"\") throws IllegalArgumentException")
    void blankCurrencyThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of(100, ""))
                .withMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("Money.of normalises currency code to uppercase")
    void currencyNormalisedToUppercase() {
        Money money = Money.of(999, "usd");
        assertThat(money.currency()).isEqualTo("USD");
    }

    // ── format() coverage ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Money.format() renders 100 cents as '1.00' with currency prefix")
    void money_format_100cents_displaysAsOneDollar() {
        Money money = Money.of(100, "USD");
        String formatted = money.format();
        assertThat(formatted).isEqualTo("USD 1.00");
    }

    @Test
    @DisplayName("Money.format() renders 99 cents correctly")
    void money_format_99cents_displaysCorrectly() {
        Money money = Money.of(99, "EUR");
        String formatted = money.format();
        assertThat(formatted).isEqualTo("EUR 0.99");
    }

    @Test
    @DisplayName("Money.toString() contains the amount and currency")
    void money_toString_isReadable() {
        Money money = Money.of(1999, "GBP");
        // Record toString format: Money[amountCents=1999, currency=GBP]
        String str = money.toString();
        assertThat(str).contains("1999").contains("GBP");
    }
}
