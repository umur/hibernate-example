package com.cinetrack.movie;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;

import java.time.Instant;

/**
 * Movie entity demonstrating:
 * <ul>
 *   <li>{@code @FilterDef} / {@code @Filter}: session-scoped content-rating gate</li>
 *   <li>{@code @SQLDelete} + {@code @Where}: transparent soft-delete</li>
 * </ul>
 */
@Entity
@Table(name = "movies")
@FilterDef(
    name = "contentRatingFilter",
    parameters = @ParamDef(name = "maxRating", type = String.class)
)
@Filter(
    name = "contentRatingFilter",
    condition = "(CASE content_rating " +
                "WHEN 'G' THEN 0 " +
                "WHEN 'PG' THEN 1 " +
                "WHEN 'PG_13' THEN 2 " +
                "WHEN 'R' THEN 3 " +
                "WHEN 'NC_17' THEN 4 " +
                "ELSE 99 END) <= " +
                "(CASE :maxRating " +
                "WHEN 'G' THEN 0 " +
                "WHEN 'PG' THEN 1 " +
                "WHEN 'PG_13' THEN 2 " +
                "WHEN 'R' THEN 3 " +
                "WHEN 'NC_17' THEN 4 " +
                "ELSE 99 END)"
)
@SQLDelete(sql = "UPDATE movies SET deleted = true, deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 50)
    private String genre;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_rating", length = 10)
    private ContentRating contentRating;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
