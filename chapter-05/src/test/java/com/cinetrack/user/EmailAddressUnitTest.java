package com.cinetrack.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Pure unit tests for the {@link EmailAddress} value object.
 * No Spring context, no database: just the domain rules.
 */
@DisplayName("EmailAddress: value object unit tests")
class EmailAddressUnitTest {

    @Test
    @DisplayName("Uppercase input is normalised to lowercase")
    void upperCaseInputNormalisedToLowercase() {
        EmailAddress email = new EmailAddress("ALICE@EXAMPLE.COM");
        assertThat(email.getValue()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("Mixed-case input is normalised to lowercase")
    void mixedCaseNormalisedToLowercase() {
        EmailAddress email = new EmailAddress("Bob@Example.COM");
        assertThat(email.getValue()).isEqualTo("bob@example.com");
    }

    @Test
    @DisplayName("Invalid format throws IllegalArgumentException")
    void invalidFormatThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailAddress("not-an-email"))
                .withMessageContaining("Invalid e-mail address format");
    }

    @Test
    @DisplayName("Missing @ throws IllegalArgumentException")
    void missingAtSignThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailAddress("userwithoutatsign.com"))
                .withMessageContaining("Invalid e-mail address format");
    }

    @Test
    @DisplayName("Blank input throws IllegalArgumentException")
    void blankInputThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailAddress("   "))
                .withMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("Null input throws IllegalArgumentException")
    void nullInputThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailAddress(null))
                .withMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("Two EmailAddress objects with the same value are equal")
    void equalityByValue() {
        EmailAddress a = new EmailAddress("carol@example.com");
        EmailAddress b = new EmailAddress("carol@example.com");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("Two EmailAddress objects with different values are not equal")
    void inequalityOnDifferentValue() {
        EmailAddress a = new EmailAddress("alice@example.com");
        EmailAddress b = new EmailAddress("bob@example.com");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("EmailAddress with same value but different case are equal after normalisation")
    void equalityAfterCaseNormalisation() {
        EmailAddress lower = new EmailAddress("dave@example.com");
        EmailAddress upper = new EmailAddress("DAVE@EXAMPLE.COM");
        assertThat(lower).isEqualTo(upper);
    }
}
