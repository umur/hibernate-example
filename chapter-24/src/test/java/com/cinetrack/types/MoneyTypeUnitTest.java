package com.cinetrack.types;

import org.hibernate.HibernateException;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link MoneyType}: covers the rare paths that the
 * integration test does not exercise: null handling, SQL type metadata,
 * mutability flags, deepCopy/equals/hashCode, JSON deserialization errors,
 * and the disassemble/assemble round-trip used by the second-level cache.
 */
class MoneyTypeUnitTest {

    private final MoneyType type = new MoneyType();

    @Test
    void getSqlType_isJson() {
        assertThat(type.getSqlType()).isEqualTo(SqlTypes.JSON);
    }

    @Test
    void returnedClass_isMoney() {
        assertThat(type.returnedClass()).isEqualTo(Money.class);
    }

    @Test
    void isMutable_returnsFalse() {
        assertThat(type.isMutable())
                .as("Money is an immutable record")
                .isFalse();
    }

    @Test
    void deepCopy_returnsSameInstance() {
        Money money = new Money(1999, "USD");
        assertThat(type.deepCopy(money))
                .as("deepCopy returns the same reference for an immutable record")
                .isSameAs(money);
        assertThat(type.deepCopy(null)).isNull();
    }

    @Test
    void equals_isStructural() {
        Money a = new Money(100, "USD");
        Money b = new Money(100, "USD");
        Money c = new Money(200, "USD");

        assertThat(type.equals(a, b)).isTrue();
        assertThat(type.equals(a, c)).isFalse();
        assertThat(type.equals(null, null)).isTrue();
        assertThat(type.equals(a, null)).isFalse();
    }

    @Test
    void hashCode_matchesObjectsHashCode() {
        Money money = new Money(500, "EUR");
        assertThat(type.hashCode(money)).isEqualTo(money.hashCode());
        assertThat(type.hashCode(null)).isZero();
    }

    @Test
    void disassemble_serializesToString() {
        Money money = new Money(1999, "USD");
        Serializable cached = type.disassemble(money);

        assertThat(cached).isEqualTo("1999:USD");
        assertThat(type.disassemble(null)).isNull();
    }

    @Test
    void assemble_reconstructsFromString() {
        Money money = type.assemble("2500:GBP", null);

        assertThat(money).isEqualTo(new Money(2500, "GBP"));
        assertThat(type.assemble(null, null)).isNull();
    }

    @Test
    void disassembleThenAssemble_roundTrips() {
        Money original = new Money(1234, "JPY");
        Serializable cached = type.disassemble(original);
        Money roundTripped = type.assemble(cached, null);

        assertThat(roundTripped).isEqualTo(original);
    }

    @Test
    void nullSafeGet_whenColumnIsNull_returnsNull() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn(null);
        when(rs.wasNull()).thenReturn(true);

        Money result = type.nullSafeGet(rs, 1, null, null);

        assertThat(result).isNull();
    }

    @Test
    void nullSafeGet_whenJsonInvalid_throwsHibernateException() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("{not-valid-json}");
        when(rs.wasNull()).thenReturn(false);

        assertThatThrownBy(() -> type.nullSafeGet(rs, 1, null, null))
                .isInstanceOf(HibernateException.class)
                .hasMessageContaining("Cannot deserialize Money");
    }

    @Test
    void nullSafeSet_whenValueIsNull_setsSqlNull() throws SQLException {
        PreparedStatement st = mock(PreparedStatement.class);

        type.nullSafeSet(st, null, 3, null);

        verify(st, times(1)).setNull(eq(3), eq(Types.OTHER));
    }
}
