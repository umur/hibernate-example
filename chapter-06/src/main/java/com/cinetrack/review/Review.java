package com.cinetrack.review;

import com.cinetrack.movie.Movie;
import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Review entity: the owning side of the bidirectional Movie ↔ Review association.
 *
 * <h2>Owning vs inverse side</h2>
 * In a bidirectional {@code @OneToMany} / {@code @ManyToOne} pair, exactly one
 * side owns the foreign-key column.  The owning side is always the {@code @ManyToOne}
 * side: here, {@code Review.movie} with its {@code @JoinColumn(name="movie_id")}.
 *
 * <p>Hibernate only looks at the owning side when deciding what SQL to emit.
 * If you add a {@code Review} to {@code Movie.reviews} but forget to set
 * {@code review.setMovie(movie)}, no FK will be written to the database.
 * Always use the {@link Movie#addReview} helper which sets both sides.
 *
 * <h2>{@code @Version}: optimistic locking</h2>
 * Appends {@code AND version = ?} to every UPDATE and increments the column on
 * success.  Concurrent edits to the same review (e.g. two users updating rating
 * simultaneously) are detected at flush time and raise
 * {@code OptimisticLockException} rather than silently overwriting each other.
 *
 * <h2>Rating constraint</h2>
 * A DB CHECK constraint ({@code rating BETWEEN 1 AND 5}) enforces the domain
 * rule at the storage layer, independent of any application validation.
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owning side of the bidirectional association.
     * {@code fetch = LAZY} is the default for {@code @ManyToOne} in the spec
     * but Hibernate respects it explicitly here for clarity.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private AppUser reviewer;

    @Column(columnDefinition = "text")
    private String content;

    /** Rating from 1 to 5, validated by a DB CHECK constraint. */
    @Column(nullable = false)
    private int rating;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    public Review(AppUser reviewer, String content, int rating) {
        this.reviewer = reviewer;
        this.content = content;
        this.rating = rating;
    }
}
