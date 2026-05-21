package com.cinetrack.movie;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * Movie entity demonstrating two additional custom-type patterns:
 *
 * <ol>
 *   <li><strong>PostgreSQL text array via {@code @Array}</strong>: the {@code tags}
 *       field maps to a {@code TEXT[]} column.  Hibernate's {@code @Array} annotation
 *       (introduced in Hibernate 6.2) generates a proper array binding rather than
 *       serialising to a comma-separated string.</li>
 *   <li><strong>JSONB {@code Map} via {@code @JdbcTypeCode}</strong>: same pattern
 *       as {@link com.cinetrack.subscription.Subscription#metadata}: arbitrary
 *       key-value pairs stored in a JSONB column and deserialised to
 *       {@code Map<String,Object>} by Jackson.</li>
 * </ol>
 */
@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    /**
     * Genre tags stored as a native PostgreSQL {@code TEXT[]} array.
     *
     * <p>{@code @Array} tells Hibernate to bind this field using the JDBC
     * array API ({@code java.sql.Array}) rather than a scalar type, producing
     * {@code text[]} on PostgreSQL.  The column DDL is {@code tags TEXT[]}.</p>
     */
    @Array(length = 50)
    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;

    /**
     * Arbitrary metadata stored as JSONB.
     *
     * @see com.cinetrack.subscription.Subscription#metadata
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
