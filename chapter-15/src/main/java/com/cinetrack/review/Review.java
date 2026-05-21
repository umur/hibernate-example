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
 * Both associations are LAZY: the correct default for @ManyToOne in Hibernate.
 *
 * EAGER on a @ManyToOne causes Hibernate to LEFT OUTER JOIN to the associated
 * table on every query, even when you only need the Review's own fields.
 * With LAZY the proxy is created cheaply and the JOIN only fires when you call
 * {@code getMovie()} or {@code getReviewer()} inside an active Session.
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
