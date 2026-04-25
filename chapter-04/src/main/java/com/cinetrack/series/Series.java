package com.cinetrack.series;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/**
 * Television series entity — demonstrates storing a {@code List<String>} cast list
 * as a PostgreSQL {@code jsonb} column.
 *
 * <h2>Why JSONB for a string list?</h2>
 * PostgreSQL arrays ({@code TEXT[]}) work well for simple flat lists, but JSONB gives
 * you richer query operators ({@code @>}, {@code jsonb_array_elements}, GIN indexes)
 * and is schema-free — the cast list can later evolve to objects without a migration.
 *
 * <h2>Hibernate 7 type resolution</h2>
 * {@code @JdbcTypeCode(SqlTypes.JSON)} tells Hibernate to use {@code JsonJdbcType}.
 * The {@code JavaType} for {@code List<String>} is inferred from the field's generic
 * signature via reflection at bootstrap time, so deserialisation produces the correct
 * parameterised type without any additional hints.
 */
@Entity
@Table(name = "series")
@Getter
@Setter
@NoArgsConstructor
public class Series {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false)
    private String title;

    /**
     * Cast member names stored as a JSON array in a {@code jsonb} column.
     *
     * <p>Hibernate's {@code JsonJdbcType} serialises this as:
     * {@code ["Bryan Cranston","Aaron Paul","Anna Gunn"]}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cast_info", columnDefinition = "jsonb")
    private List<String> castNames;

    @Column(name = "seasons", nullable = false)
    private int seasons;

    public Series(String title, List<String> castNames, int seasons) {
        this.title = title;
        this.castNames = castNames;
        this.seasons = seasons;
    }
}
