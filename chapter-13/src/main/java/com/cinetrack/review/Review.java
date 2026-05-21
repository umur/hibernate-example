package com.cinetrack.review;

import com.cinetrack.movie.Movie;
import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * The {@code @Version} field on this entity is the primary demonstration
 * vehicle for Chapter 13.  When two transactions read the same Review row
 * and both try to flush a change, the second flush will detect a stale
 * version and throw {@code OptimisticLockException}: which Spring Data
 * wraps as {@code ObjectOptimisticLockingFailureException}.
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "reviewer_id")
    private AppUser reviewer;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "rating")
    private int rating;

    /**
     * Hibernate uses this column to detect concurrent modifications.
     * Every successful UPDATE increments the value by 1.
     */
    @Version
    private long version;

    @Column(name = "created_at")
    private Instant createdAt;

    public Review(Movie movie, AppUser reviewer, String content, int rating) {
        this.movie = movie;
        this.reviewer = reviewer;
        this.content = content;
        this.rating = rating;
        this.createdAt = Instant.now();
    }
}
