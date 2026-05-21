package com.cinetrack.review;

import com.cinetrack.movie.Movie;
import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * A user review for a movie. The @Version field enables optimistic locking:
 * if two transactions try to update the same review simultaneously, the second
 * commit will throw an OptimisticLockException rather than silently overwriting
 * the first change.
 */
@Entity
@Table(name = "reviews")
@EntityListeners(AuditingEntityListener.class)
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
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private int score;

    /**
     * Hibernate increments this value on every UPDATE. If the row's version
     * in the database differs from the version held in memory, an
     * OptimisticLockException is thrown before the UPDATE executes.
     */
    @Version
    private long version;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Review(Movie movie, AppUser user, String body, int score) {
        this.movie = movie;
        this.user = user;
        this.body = body;
        this.score = score;
    }
}
