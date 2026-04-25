package com.cinetrack.review;

import com.cinetrack.common.AuditableEntity;
import com.cinetrack.movie.Movie;
import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

/**
 * A user review of a movie, tracked by Hibernate Envers.
 *
 * <p>{@link Audited} causes Envers to write a row to {@code reviews_aud} for
 * every state change. The {@link Version} field participates in optimistic
 * locking: Hibernate increments it on each UPDATE and checks it matches before
 * writing, preventing lost updates in concurrent scenarios.
 *
 * <p>Foreign-key associations ({@code movie_id}, {@code reviewer_id}) are also
 * recorded in the audit table so history queries can reconstruct the complete
 * review at any revision.
 */
@Entity
@Table(name = "reviews")
@Audited
@Getter
@Setter
@NoArgsConstructor
public class Review extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private AppUser reviewer;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column
    private Integer rating;

    @Version
    @Column(nullable = false)
    private long version;

    public Review(Movie movie, AppUser reviewer, String content, int rating) {
        this.movie = movie;
        this.reviewer = reviewer;
        this.content = content;
        this.rating = rating;
    }
}
