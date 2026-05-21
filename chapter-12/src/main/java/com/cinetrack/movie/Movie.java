package com.cinetrack.movie;

import com.cinetrack.review.Review;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Core movie entity for Chapter 12.
 *
 * <p>All projection strategies in this chapter target this entity:
 * interface projections read a subset of its columns, DTO constructor expressions
 * aggregate its joined {@code reviews}, and Tuple queries combine it with user data.</p>
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

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Review> reviews = new ArrayList<>();

    public Movie(String title, Genre genre, int releaseYear, BigDecimal rating) {
        this.title = title;
        this.genre = genre;
        this.releaseYear = releaseYear;
        this.rating = rating;
    }

    public void addReview(Review review) {
        reviews.add(review);
        review.setMovie(this);
    }
}
