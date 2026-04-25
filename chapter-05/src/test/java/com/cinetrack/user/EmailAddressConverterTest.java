package com.cinetrack.user;

import com.cinetrack.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.*;

// Note: unit-style converter tests that don't need Spring are included below.

/**
 * Integration tests for {@link EmailAddressConverter} and {@link EmailAddress}.
 *
 * <p>These tests verify the full round-trip through the JPA AttributeConverter
 * pipeline: Java object → JDBC VARCHAR → Java object. The container database
 * ensures we exercise the real PostgreSQL JDBC driver, not an H2 stub.
 */
@DisplayName("EmailAddress — AttributeConverter round-trip")
class EmailAddressConverterTest extends AbstractIntegrationTest {

    @Autowired
    private AppUserRepository userRepository;

    @Test
    @DisplayName("EmailAddress is stored as a plain string and reloaded as an EmailAddress")
    void emailAddressRoundTrip() {
        // GIVEN
        EmailAddress email = new EmailAddress("alice@example.com");
        AppUser user = new AppUser("alice", email, "hashed_pw");
        userRepository.save(user);
        userRepository.flush();

        // WHEN — reload from DB (first-level cache already holds the instance,
        // but @DataJpaTest wraps in a transaction so a second findById still
        // returns the same managed instance — field values are unchanged)
        AppUser loaded = userRepository.findById(user.getId()).orElseThrow();

        // THEN
        assertThat(loaded.getEmail())
                .isNotNull()
                .isEqualTo(new EmailAddress("alice@example.com"));
        assertThat(loaded.getEmail().getValue()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("Constructing an EmailAddress with an invalid format throws IllegalArgumentException")
    void invalidEmailFormatThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailAddress("not-an-email"))
                .withMessageContaining("Invalid e-mail address format");
    }

    @Test
    @DisplayName("Constructing an EmailAddress with a blank value throws IllegalArgumentException")
    void blankEmailThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EmailAddress("  "))
                .withMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("EmailAddress normalises input to lowercase")
    void emailNormalisedToLowercase() {
        EmailAddress email = new EmailAddress("Bob@Example.COM");
        assertThat(email.getValue()).isEqualTo("bob@example.com");
    }

    // ── Unit-style converter tests (no Spring context, direct instantiation) ──

    @Test
    @DisplayName("convertToDatabaseColumn(null) returns null")
    void convertToDatabaseColumn_null_returnsNull() {
        EmailAddressConverter converter = new EmailAddressConverter();
        String result = converter.convertToDatabaseColumn(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("convertToDatabaseColumn returns the normalised email string")
    void convertToDatabaseColumn_validAddress_returnsString() {
        EmailAddressConverter converter = new EmailAddressConverter();
        String result = converter.convertToDatabaseColumn(new EmailAddress("Eve@Example.COM"));
        assertThat(result).isEqualTo("eve@example.com");
    }

    @Test
    @DisplayName("convertToEntityAttribute(null) returns null")
    void convertToEntityAttribute_null_returnsNull() {
        EmailAddressConverter converter = new EmailAddressConverter();
        EmailAddress result = converter.convertToEntityAttribute(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("convertToEntityAttribute reconstructs EmailAddress from stored string")
    void convertToEntityAttribute_validString_reconstructsEmailAddress() {
        EmailAddressConverter converter = new EmailAddressConverter();
        EmailAddress result = converter.convertToEntityAttribute("frank@example.com");
        assertThat(result).isNotNull();
        assertThat(result.getValue()).isEqualTo("frank@example.com");
        assertThat(result).isEqualTo(new EmailAddress("frank@example.com"));
    }
}
