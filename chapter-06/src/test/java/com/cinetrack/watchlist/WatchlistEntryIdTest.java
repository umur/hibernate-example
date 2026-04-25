package com.cinetrack.watchlist;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the {@link WatchlistEntryId} composite-key record.
 *
 * <p>Verifies the {@link Serializable} equals/hashCode contract that JPA requires
 * of all composite-key classes. No Spring context, no database.
 *
 * <p>Because {@code WatchlistEntryId} is a Java {@code record}, the compiler
 * generates structural equals/hashCode automatically — these tests confirm the
 * contract is upheld and will catch any accidental refactoring to a plain class.
 */
@DisplayName("WatchlistEntryId — equals/hashCode contract")
class WatchlistEntryIdTest {

    @Test
    @DisplayName("equals — same watchlistId and movieId returns true")
    void equals_sameIds_isTrue() {
        WatchlistEntryId a = WatchlistEntryId.of(1L, 10L);
        WatchlistEntryId b = WatchlistEntryId.of(1L, 10L);
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("equals — different watchlistId returns false")
    void equals_differentWatchlistId_isFalse() {
        WatchlistEntryId a = WatchlistEntryId.of(1L, 10L);
        WatchlistEntryId b = WatchlistEntryId.of(2L, 10L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("equals — different movieId returns false")
    void equals_differentMovieId_isFalse() {
        WatchlistEntryId a = WatchlistEntryId.of(1L, 10L);
        WatchlistEntryId b = WatchlistEntryId.of(1L, 99L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("hashCode — equal objects have identical hash codes")
    void hashCode_consistentWithEquals() {
        WatchlistEntryId a = WatchlistEntryId.of(5L, 42L);
        WatchlistEntryId b = WatchlistEntryId.of(5L, 42L);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("hashCode — unequal objects typically have different hash codes")
    void hashCode_differentForDifferentIds() {
        WatchlistEntryId a = WatchlistEntryId.of(1L, 1L);
        WatchlistEntryId b = WatchlistEntryId.of(2L, 3L);
        // Not a strict requirement (collisions are legal), but for these small longs
        // the record-generated hash must differ.
        assertThat(a.hashCode()).isNotEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("null field components do not cause NullPointerException")
    void nullFields_doNotThrow() {
        WatchlistEntryId a = new WatchlistEntryId(null, null);
        WatchlistEntryId b = new WatchlistEntryId(null, null);
        // equals and hashCode must not throw even with null components
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("factory method of() produces the same result as direct constructor")
    void factoryMethod_matchesDirectConstructor() {
        WatchlistEntryId via_factory = WatchlistEntryId.of(7L, 13L);
        WatchlistEntryId via_ctor   = new WatchlistEntryId(7L, 13L);
        assertThat(via_factory).isEqualTo(via_ctor);
    }
}
