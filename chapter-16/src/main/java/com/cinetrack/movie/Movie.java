package com.cinetrack.movie;

import com.cinetrack.review.Review;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Movie entity.
 *
 * <p>The {@code reviews} collection is LAZY by default. Without any fetch
 * strategy adjustment, iterating over a list of movies and accessing their
 * reviews triggers one extra SELECT per movie: the classic N+1 problem.
 *
 * <p>Two complementary mitigations are shown in this chapter:
 * <ol>
 *   <li>{@code @BatchSize(size=25)}: Hibernate issues SELECT … WHERE movie_id IN (?,?,…)
 *       batches instead of individual per-row queries. This is a low-effort fix
 *       that keeps lazy loading but reduces round-trips from N to N/25.</li>
 *   <li>JOIN FETCH in {@link MovieRepository#findAllWithReviews()}: a single
 *       query loads both sides at once, eliminating extra queries entirely.</li>
 * </ol>
 */
@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(nullable = false)
    @ToString.Include
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Genre genre;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    /**
     * LAZY collection: the source of N+1 when accessed naively.
     *
     * <p>{@code @BatchSize(size=25)} reduces the number of extra queries from N
     * down to ceil(N/25) when the collection IS accessed outside a JOIN FETCH.
     */
    @OneToMany(mappedBy = "movie", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 25)
    private List<Review> reviews = new ArrayList<>();

    public Movie(String title, Genre genre, int releaseYear) {
        this.title = title;
        this.genre = genre;
        this.releaseYear = releaseYear;
    }

    public void addReview(Review review) {
        reviews.add(review);
        review.setMovie(this);
    }
}
