package com.cinetrack.types;

/**
 * Immutable value object representing a monetary amount.
 *
 * <p>Amounts are stored as integer cents to avoid floating-point rounding
 * errors. {@code Money(1999, "USD")} represents USD 19.99.</p>
 *
 * <p>Being a {@code record}, Money inherits compiler-generated
 * {@code equals}, {@code hashCode}, and {@code toString} — which
 * {@link MoneyType} delegates to directly.</p>
 *
 * <p>The JSONB column layout on disk is:
 * <pre>{@code {"cents": 1999, "currency": "USD"}}</pre>
 * Serialisation is handled exclusively by {@link MoneyType}; application
 * code never touches raw JSON.</p>
 *
 * @param amountCents monetary value in the smallest currency unit (cents)
 * @param currency    ISO 4217 currency code, e.g. "USD", "EUR"
 */
public record Money(int amountCents, String currency) {

    /** Convenience factory — {@code Money.of(1999, "USD")}. */
    public static Money of(int amountCents, String currency) {
        return new Money(amountCents, currency);
    }
}
