package com.cinetrack.movie;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Formula;

import java.time.Instant;

/**
 * Movie entity demonstrating {@link DynamicUpdate} and {@link Formula}.
 *
 * <h2>{@code @DynamicUpdate}</h2>
 * By default Hibernate generates a single UPDATE template at bootstrap that
 * sets every non-ID column, regardless of what actually changed.  With
 * {@code @DynamicUpdate}, Hibernate inspects the dirty fields at flush time
 * and emits an UPDATE that touches only those columns.  This reduces network
 * payload and avoids clobbering concurrent writes to unrelated columns.
 *
 * <h2>{@code @Formula}</h2>
 * A formula property is a read-only virtual column backed by an arbitrary SQL
 * sub-SELECT.  Hibernate appends the SQL fragment as a subquery in the SELECT
 * list whenever the entity is loaded — no stored column, no trigger, no view
 * required.  Formulas participate in HQL WHERE clauses too.
 *
 * <p><strong>Caveat:</strong> formulas use native SQL, not HQL, so they are
 * dialect-specific.  Keep them simple or abstract behind a database view for
 * portability.
 */
@Entity
@Table(name = "movies")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Genre genre;

    @Column(name = "release_year", nullable = false)
    private int releaseYear;

    /**
     * Count of reviews for this movie — computed in-database, never stored.
     *
     * <p>The SQL fragment references {@code id} (unqualified) which Hibernate
     * resolves to the owning table's primary-key column in the generated SELECT.
     */
    @Formula("(SELECT COUNT(r.id) FROM reviews r WHERE r.movie_id = id)")
    private long reviewCount;

    /**
     * Average reviewer rating — {@code COALESCE} returns 0.0 when there are no
     * reviews, avoiding a NULL in the Java field.
     */
    @Formula("(SELECT COALESCE(AVG(r.rating), 0) FROM reviews r WHERE r.movie_id = id)")
    private double averageRating;

    /**
     * Timestamp set by the database DEFAULT expression; Hibernate never writes it.
     * {@code insertable=false} prevents it from appearing in the INSERT column list;
     * {@code updatable=false} prevents it from appearing in UPDATE statements.
     */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public Movie(String title, Genre genre, int releaseYear) {
        this.title = title;
        this.genre = genre;
        this.releaseYear = releaseYear;
    }
}
