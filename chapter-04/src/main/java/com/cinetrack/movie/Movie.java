package com.cinetrack.movie;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Core movie entity demonstrating Hibernate 7's new type-system features.
 *
 * <h2>Key Hibernate 7 annotations used here</h2>
 * <ul>
 *   <li>{@link UuidGenerator} with {@code style = Style.TIME} — produces a time-ordered
 *       (version-7) UUID so that inserts are naturally clustered in the index.</li>
 *   <li>{@link JdbcTypeCode} with {@link SqlTypes#JSON} — instructs Hibernate to use
 *       the {@code JsonJdbcType} descriptor, serialising {@code Map<String,Object>}
 *       to/from PostgreSQL's native {@code jsonb} column without any custom converter.</li>
 *   <li>{@link Array} — maps a {@code TEXT[]} PostgreSQL array column directly to a
 *       Java {@code String[]} field through Hibernate's {@code ArrayJdbcType}.</li>
 *   <li>{@link BatchSize} on the class — when multiple {@code Movie} proxies are
 *       initialised in the same session, Hibernate batches the SELECT statements
 *       into groups of 25 (the "IN-clause batch" strategy).</li>
 * </ul>
 *
 * <h2>Bytecode enhancement</h2>
 * The Hibernate Maven plugin (configured in pom.xml) instruments this class at
 * build time to enable:
 * <ul>
 *   <li><em>Dirty tracking</em> — only changed fields are included in UPDATE statements.</li>
 *   <li><em>Lazy initialisation</em> — basic fields can be fetched lazily when annotated
 *       with {@code @Basic(fetch = FetchType.LAZY)}.</li>
 *   <li><em>Association management</em> — bidirectional associations are kept in sync
 *       automatically on the bytecode level.</li>
 * </ul>
 */
@Entity
@Table(name = "movies")
@BatchSize(size = 25)
@Getter
@Setter
@NoArgsConstructor
public class Movie {

    /**
     * Time-ordered UUID v7 primary key.
     *
     * <p>{@link UuidGenerator.Style#TIME} generates a UUID where the most-significant
     * bits encode the current timestamp, ensuring that new rows sort after older ones
     * in the B-tree index — far better for insert performance than random UUID v4.
     */
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false)
    private String title;

    /**
     * Enum stored as its name string.
     * Adding new enum constants never invalidates existing DB rows.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "genre", nullable = false, length = 50)
    private Genre genre;

    @Column(name = "release_year", nullable = false)
    private int releaseYear;

    /**
     * Rating stored as NUMERIC(3,1) — e.g. 8.4.
     * {@code precision=3} means three significant digits total; {@code scale=1} means
     * one digit after the decimal point.
     */
    @Column(name = "rating", precision = 3, scale = 1)
    private BigDecimal rating;

    /**
     * Arbitrary key/value metadata stored as PostgreSQL {@code jsonb}.
     *
     * <p>Hibernate 7's type system resolves {@link SqlTypes#JSON} to
     * {@code JsonJdbcType}, which delegates serialisation to Jackson (or whichever
     * JSON library is on the classpath).  No custom {@code AttributeConverter} needed.
     *
     * <p>Example stored value:
     * <pre>{@code {"imdbId": "tt0111161", "budget": 25000000, "awards": ["Oscar"]}}</pre>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /**
     * Native PostgreSQL {@code TEXT[]} array mapped via {@link Array}.
     *
     * <p>Hibernate 7 introduced the {@code @Array} annotation together with a
     * dedicated {@code ArrayJdbcType} that communicates with PostgreSQL's array
     * protocol directly — no serialisation to/from a delimited string.
     *
     * <p>Example: {@code {"Netflix","HBO Max","Apple TV+"}}
     */
    @Array(length = 10)
    @Column(name = "streaming_platforms", columnDefinition = "text[]")
    private String[] streamingPlatforms;

    // ── Convenience constructor used in tests ─────────────────────────────────

    public Movie(String title, Genre genre, int releaseYear, BigDecimal rating) {
        this.title = title;
        this.genre = genre;
        this.releaseYear = releaseYear;
        this.rating = rating;
    }
}
