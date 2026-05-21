package com.cinetrack.review;

import com.cinetrack.movie.Movie;
import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Review entity with optimistic locking.
 *
 * <p>Reviews are mutable: a user may update their rating or content after
 * posting.  {@link Version} provides optimistic concurrency control: if two
 * sessions load the same review and both attempt to flush changes, the second
 * flush throws {@code OptimisticLockException} because the version column no
 * longer matches.
 *
 * <p>{@code createdAt} is DB-managed ({@code insertable=false, updatable=false}).
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private AppUser reviewer;

    @Column(columnDefinition = "text")
    private String content;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    public Review(Movie movie, AppUser reviewer, String content, BigDecimal rating) {
        this.movie = movie;
        this.reviewer = reviewer;
        this.content = content;
        this.rating = rating;
    }
}
