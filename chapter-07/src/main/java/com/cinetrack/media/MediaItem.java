package com.cinetrack.media;

import com.cinetrack.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Root of the media hierarchy. Uses SINGLE_TABLE inheritance so all subtypes
 * share one table ("media_items"), differentiated by the "dtype" discriminator
 * column. This trades some nullable columns for zero joins on polymorphic queries.
 */
@Entity
@Table(name = "media_items")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
@Getter
@Setter
@NoArgsConstructor
public abstract class MediaItem extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "media_item_seq")
    @SequenceGenerator(
            name = "media_item_seq",
            sequenceName = "media_items_id_seq",
            allocationSize = 50
    )
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "release_year")
    private int releaseYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "genre", length = 50)
    private Genre genre;

    protected MediaItem(String title, int releaseYear, Genre genre) {
        this.title = title;
        this.releaseYear = releaseYear;
        this.genre = genre;
    }
}
