package com.cinetrack.movie;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Maps to the {@code movies} table created by V2__create_movies.sql.
 * Column names and types must match exactly: ddl-auto=validate will reject
 * any mismatch at startup.
 */
@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 50)
    private String genre;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Movie(String title, String genre) {
        this.title = title;
        this.genre = genre;
    }
}
