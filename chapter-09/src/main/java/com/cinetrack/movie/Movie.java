package com.cinetrack.movie;

import com.cinetrack.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Movie entity extended with a soft-delete marker (deletedAt) and an
 * optimistic-locking version field on the Review side. This entity is the
 * central subject of all Chapter 9 repository demonstrations.
 */
@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
public class Movie extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Genre genre;

    @Column(precision = 3, scale = 1)
    private BigDecimal rating;

    // Soft-delete support: null means active, non-null means deleted
    @Column(name = "deleted_at")
    private java.time.Instant deletedAt;

    public Movie(String title, Genre genre, BigDecimal rating) {
        this.title = title;
        this.genre = genre;
        this.rating = rating;
    }
}
