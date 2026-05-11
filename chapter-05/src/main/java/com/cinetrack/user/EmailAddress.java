package com.cinetrack.user;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object representing a validated e-mail address.
 *
 * <p>This is a plain Java class: not a JPA entity and not an embeddable.
 * It is mapped to/from the {@code email} VARCHAR column by
 * {@link EmailAddressConverter}, which is registered with
 * {@code @Converter(autoApply=true)}, so JPA applies it automatically
 * wherever an {@code EmailAddress} field appears.
 *
 * <h2>Why a value object?</h2>
 * Wrapping a string in a type makes the domain model self-documenting
 * ({@code EmailAddress} vs {@code String}), centralises validation, and
 * prevents passing arbitrary strings where an e-mail is expected.
 */
public final class EmailAddress {

    // RFC 5322-ish simplified pattern: good enough for illustration.
    private static final Pattern PATTERN =
            Pattern.compile("^[\\w.+\\-]+@[\\w\\-]+(\\.[\\w\\-]+)+$");

    private final String value;

    /**
     * Constructs a validated {@code EmailAddress}.
     *
     * @param value the raw e-mail string
     * @throws IllegalArgumentException if {@code value} is null, blank,
     *                                  or does not match the expected format
     */
    public EmailAddress(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("E-mail address must not be blank");
        }
        if (!PATTERN.matcher(value.trim()).matches()) {
            throw new IllegalArgumentException(
                    "Invalid e-mail address format: '" + value + "'");
        }
        this.value = value.trim().toLowerCase();
    }

    /** Returns the normalised (lowercase, trimmed) e-mail string. */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmailAddress other)) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
