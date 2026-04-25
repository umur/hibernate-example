package com.cinetrack.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Value object representing a monetary amount.
 *
 * <p>Declared as {@link Embeddable} so that Hibernate can inline its fields
 * ({@code amount_cents}, {@code currency}) directly into the owning entity's
 * table — no foreign key, no join, no extra table.
 *
 * <h2>Why store cents as an integer?</h2>
 * Floating-point arithmetic is unsuitable for money because IEEE 754 cannot
 * represent every decimal fraction exactly.  Storing the value in the smallest
 * currency unit (cents for USD/EUR, pence for GBP) keeps everything as integer
 * arithmetic and eliminates rounding surprises.
 *
 * <h2>Record semantics</h2>
 * Java records are inherently immutable and provide {@code equals}, {@code hashCode},
 * and {@code toString} for free — ideal for value objects.  JPA/Hibernate supports
 * records as embeddables since Hibernate 6.2.
 */
@Embeddable
public record Money(

        @Column(name = "amount_cents", nullable = false)
        int amountCents,

        @Column(name = "currency", nullable = false, length = 3)
        String currency

) {
    /**
     * Factory method for readability at call sites:
     * {@code Money.of(1999, "USD")} reads better than {@code new Money(1999, "USD")}.
     */
    public static Money of(int cents, String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency code must not be blank");
        }
        if (cents < 0) {
            throw new IllegalArgumentException("Amount in cents must not be negative, got: " + cents);
        }
        return new Money(cents, currency.toUpperCase());
    }

    /** Convenience: format as a human-readable string, e.g. {@code "USD 19.99"}. */
    public String format() {
        return "%s %.2f".formatted(currency, amountCents / 100.0);
    }
}
