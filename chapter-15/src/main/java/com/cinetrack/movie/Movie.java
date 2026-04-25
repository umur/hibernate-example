package com.cinetrack.movie;

import com.cinetrack.review.Review;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * Demonstrates two complementary fetch-optimisation strategies:
 *
 * <h2>1. @BatchSize on a @OneToMany collection</h2>
 * When Hibernate needs to initialise the {@code reviews} collection for N
 * movies, instead of issuing N individual {@code SELECT}s (the classic N+1
 * problem) it groups them into batches of up to 25 IDs:
 * {@code WHERE movie_id IN (?, ?, … up to 25 ?s)}.
 * For 100 movies that is ⌈100/25⌉ = 4 queries instead of 100.
 *
 * <h2>2. @NamedEntityGraph for ad-hoc JOIN FETCH via Spring Data</h2>
 * The named graph {@code "Movie.withReviews"} is referenced by
 * {@link MovieRepository#findByRatingGreaterThan}, which tells Spring Data to
 * annotate its query with {@code @EntityGraph}.  Hibernate translates this
 * into a single {@code LEFT JOIN FETCH} — one query for any number of movies,
 * regardless of batch size.
 *
 * <p>Both strategies solve N+1 but suit different call sites:
 * <ul>
 *   <li>JOIN FETCH / EntityGraph — when you <em>always</em> need the reviews</li>
 *   <li>@BatchSize — when you <em>sometimes</em> traverse the collection and want
 *       lazy loading to remain the default</li>
 * </ul>
 */
@NamedEntityGraph(
        name = "Movie.withReviews",
        attributeNodes = @NamedAttributeNode("reviews")
)
@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(STRING)
    private Genre genre;

    private BigDecimal rating;

    /**
     * LAZY is the correct default for collections — never use EAGER here.
     * @BatchSize limits the N+1 cost to ⌈N/25⌉ + 1 queries when the
     * collection is accessed outside a JOIN FETCH context.
     */
    @OneToMany(mappedBy = "movie", fetch = LAZY)
    @BatchSize(size = 25)
    private List<Review> reviews = new ArrayList<>();

    public Movie(String title, Genre genre, BigDecimal rating) {
        this.title = title;
        this.genre = genre;
        this.rating = rating;
    }
}
