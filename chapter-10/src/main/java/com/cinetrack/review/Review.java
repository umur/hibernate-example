package com.cinetrack.review;

import com.cinetrack.movie.Movie;
import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A user's review of a movie.
 *
 * <p>Uses optimistic locking via {@code @Version} to illustrate how Hibernate
 * detects concurrent modifications without holding database locks.</p>
 */
@Entity
@Table(
    name = "reviews",
    uniqueConstraints = @UniqueConstraint(columnNames = {"movie_id", "reviewer_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reviews_seq")
    @SequenceGenerator(name = "reviews_seq", sequenceName = "reviews_id_seq", allocationSize = 1)
    private Long id;

    /** Optimistic-lock column: Hibernate increments this on every UPDATE. */
    @Version
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private AppUser reviewer;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Review(Movie movie, AppUser reviewer, int rating, String content) {
        this.movie = movie;
        this.reviewer = reviewer;
        this.rating = rating;
        this.content = content;
    }
}
