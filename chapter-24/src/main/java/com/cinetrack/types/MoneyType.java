package com.cinetrack.types;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.SqlTypes;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

/**
 * Hibernate {@link UserType} that persists a {@link Money} record as a
 * single JSONB column.
 *
 * <h3>Design decisions</h3>
 * <ul>
 *   <li><strong>Single-column JSONB</strong>: {@code {"cents":1999,"currency":"USD"}}.
 *       This avoids the two-column complexity of splitting into
 *       {@code amount_cents INT} + {@code currency VARCHAR} while keeping the
 *       value human-readable in the database.</li>
 *   <li><strong>Immutability</strong>: {@code isMutable()} returns {@code false}
 *       and {@code deepCopy()} returns the same instance. Hibernate's dirty-checking
 *       uses {@code equals()} to detect changes; because {@code Money} is a record,
 *       equality is structural and correct without extra ceremony.</li>
 *   <li><strong>Registration</strong>: this class is registered globally by
 *       {@link CineTrackTypeContributor} via the {@code TypeContributor} SPI,
 *       so entity fields of type {@link Money} need no {@code @Type} annotation.</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * <p>The shared {@link ObjectMapper} instance is safe for concurrent use
 * (Jackson's default configuration is immutable after construction).</p>
 */
public class MoneyType implements UserType<Money> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------
    // SQL type descriptor
    // ------------------------------------------------------------------

    @Override
    public int getSqlType() {
        return SqlTypes.JSON;
    }

    // ------------------------------------------------------------------
    // Java type
    // ------------------------------------------------------------------

    @Override
    public Class<Money> returnedClass() {
        return Money.class;
    }

    // ------------------------------------------------------------------
    // Read / write
    // ------------------------------------------------------------------

    @Override
    public Money nullSafeGet(ResultSet rs,
                             int position,
                             SharedSessionContractImplementor session,
                             Object owner) throws SQLException {
        String json = rs.getString(position);
        if (rs.wasNull() || json == null) {
            return null;
        }
        try {
            MoneyJson dto = MAPPER.readValue(json, MoneyJson.class);
            return new Money(dto.cents(), dto.currency());
        } catch (JsonProcessingException e) {
            throw new HibernateException("Cannot deserialize Money from JSON: " + json, e);
        }
    }

    @Override
    public void nullSafeSet(PreparedStatement st,
                            Money value,
                            int index,
                            SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
            return;
        }
        try {
            MoneyJson dto = new MoneyJson(value.amountCents(), value.currency());
            String json = MAPPER.writeValueAsString(dto);
            // PostgreSQL JDBC driver accepts String for JSONB when type is OTHER
            st.setObject(index, json, Types.OTHER);
        } catch (JsonProcessingException e) {
            throw new HibernateException("Cannot serialize Money to JSON", e);
        }
    }

    // ------------------------------------------------------------------
    // Mutability & identity
    // ------------------------------------------------------------------

    /**
     * Money is an immutable record: return the same instance.
     * Hibernate will use {@link #equals} for dirty checking.
     */
    @Override
    public Money deepCopy(Money value) {
        return value; // records are immutable; no copy needed
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public boolean equals(Money x, Money y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(Money x) {
        return Objects.hashCode(x);
    }

    @Override
    public Serializable disassemble(Money value) {
        // For second-level cache: store as "cents:currency" string
        return value == null ? null : value.amountCents() + ":" + value.currency();
    }

    @Override
    public Money assemble(Serializable cached, Object owner) {
        if (cached == null) return null;
        String[] parts = ((String) cached).split(":");
        return new Money(Integer.parseInt(parts[0]), parts[1]);
    }

    // ------------------------------------------------------------------
    // Internal DTO for Jackson round-trip
    // ------------------------------------------------------------------

    /** Jackson-friendly projection of {@link Money}. */
    private record MoneyJson(int cents, String currency) {}
}
