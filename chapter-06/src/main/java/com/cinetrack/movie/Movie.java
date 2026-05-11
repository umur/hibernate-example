package com.cinetrack.movie;

import com.cinetrack.review.Review;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Movie entity demonstrating two types of collection mappings side by side.
 *
 * <h2>{@code @ElementCollection}: tags</h2>
 * {@code tags} is a {@code Set<String>} with no entity lifecycle of its own.
 * Hibernate stores each element as a row in the {@code movie_genres} collection
 * table and manages insertions/deletions automatically when the set is mutated.
 * Using a {@code Set} (rather than a {@code List}/bag) prevents duplicate rows
 * and avoids the "delete all, re-insert all" behaviour that bags trigger on any
 * collection mutation.
 *
 * <h2>Bidirectional {@code @OneToMany}: reviews</h2>
 * The inverse (non-owning) side of the bidirectional association with {@link Review}.
 * The owning side is {@code Review.movie} ({@code @ManyToOne}).
 *
 * <ul>
 *   <li>{@code cascade = CascadeType.ALL}: persisting or removing a {@code Movie}
 *       cascades to all its {@code Review} children.</li>
 *   <li>{@code orphanRemoval = true}: removing a {@code Review} from this list
 *       automatically DELETEs the orphaned review row, even without explicit
 *       {@code em.remove(review)}.</li>
 *   <li>{@code @BatchSize(size = 25)}: when Hibernate initialises this
 *       collection for multiple {@code Movie} proxies in the same session it
 *       uses a batched IN-clause query rather than N individual SELECTs.</li>
 * </ul>
 *
 * <h2>Bag vs Set semantics</h2>
 * Reviews are stored in a {@code List} (a Hibernate "bag"): order is not
 * guaranteed by the database but duplicates are permitted at the Java level.
 * Bags produce efficient SQL for collection loading but trigger a full
 * DELETE + re-INSERT when an element is added/removed from the middle.
 * For ordered collections consider {@code @OrderColumn}; for uniqueness use
 * a {@code Set}.
 */
@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
public class Movie {

    /**
     * Sequence generator with {@code allocationSize=50}: Hibernate fetches
     * 50 IDs from the sequence in a single round-trip and allocates them in
     * memory, reducing sequence round-trips by 50x at the cost of potential
     * gaps on restart.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator = "movie_seq")
    @SequenceGenerator(name = "movie_seq",
                       sequenceName = "movies_id_seq",
                       allocationSize = 50)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Genre genre;

    /**
     * Free-form tags stored in the {@code movie_genres} collection table.
     *
     * <p>{@link CollectionTable} names the table and the FK column.
     * {@link Column} names the element column within that table.
     * Hibernate handles all INSERTs and DELETEs when the set is modified : 
     * no repository method needed.
     */
    @ElementCollection
    @CollectionTable(
            name = "movie_genres",
            joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "tags")
    private Set<String> tags = new HashSet<>();

    /**
     * Inverse side of the bidirectional Movie ↔ Review association.
     *
     * <p>{@code mappedBy = "movie"} tells Hibernate that {@code Review.movie}
     * owns the FK column: this side never writes to the database.
     * Always use the helper methods {@link #addReview} and {@link #removeReview}
     * to keep both sides of the association in sync within the same session.
     */
    @OneToMany(mappedBy = "movie",
               cascade = CascadeType.ALL,
               orphanRemoval = true)
    @BatchSize(size = 25)
    private List<Review> reviews = new ArrayList<>();

    public Movie(String title, Genre genre) {
        this.title = title;
        this.genre = genre;
    }

    // ── Bidirectional sync helpers ────────────────────────────────────────────

    /**
     * Adds a review and keeps the owning side ({@code review.movie}) in sync.
     * Always use this method instead of mutating {@code reviews} directly.
     */
    public void addReview(Review review) {
        reviews.add(review);
        review.setMovie(this);
    }

    /**
     * Removes a review. Because {@code orphanRemoval=true}, Hibernate will
     * DELETE the review row at the next flush without any explicit
     * {@code em.remove()} call.
     */
    public void removeReview(Review review) {
        reviews.remove(review);
        review.setMovie(null);
    }
}
