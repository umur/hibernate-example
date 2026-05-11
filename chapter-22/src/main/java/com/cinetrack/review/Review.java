package com.cinetrack.review;

import com.cinetrack.movie.Movie;
import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Review entity demonstrating:
 * <ul>
 *   <li>{@code @EntityListeners}: delegates pre-persist/pre-update normalisation
 *       to {@link ReviewNormalizationListener}</li>
 *   <li>{@code @SQLDelete} + {@code @Where}: soft-delete pattern identical to
 *       {@code Movie}, ensuring deleted reviews are invisible to JPQL queries</li>
 * </ul>
 */
@Entity
@Table(name = "reviews")
@EntityListeners(ReviewNormalizationListener.class)
@SQLDelete(sql = "UPDATE reviews SET deleted = true WHERE id = ?")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private AppUser reviewer;

    @Column(columnDefinition = "TEXT")
    private String content;

    private Integer rating;

    @Column(nullable = false)
    private boolean deleted = false;
}
