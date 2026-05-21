package com.cinetrack.movie;

import com.cinetrack.review.Review;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a film in the CineTrack catalogue.
 *
 * <p>The {@code reviews} collection is intentionally lazy (the default) so that
 * queries in {@link MovieRepository} can demonstrate explicit JOIN FETCH and
 * {@code @EntityGraph} loading strategies.</p>
 */
@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "movies_seq")
    @SequenceGenerator(name = "movies_seq", sequenceName = "movies_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Genre genre;

    @Column(name = "release_year", nullable = false)
    private int releaseYear;

    @Column(precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Review> reviews = new ArrayList<>();

    public Movie(String title, Genre genre, int releaseYear, BigDecimal rating) {
        this.title = title;
        this.genre = genre;
        this.releaseYear = releaseYear;
        this.rating = rating;
    }

    /** Convenience bidirectional-link helper. */
    public void addReview(Review review) {
        reviews.add(review);
        review.setMovie(this);
    }
}
