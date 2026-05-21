package com.cinetrack.movie;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;

@Entity
@Table(name = "movies")
@Audited
@FilterDef(name = "activeMovies", parameters = @ParamDef(name = "deleted", type = Boolean.class))
@Filter(name = "activeMovies", condition = "deleted = :deleted")
@Getter
@Setter
@NoArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "movie_seq")
    @SequenceGenerator(name = "movie_seq", sequenceName = "movie_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "release_year")
    private Integer year;

    @Column(nullable = false)
    private String genre;

    @Column(precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(columnDefinition = "text")
    private String overview;

    @Column(nullable = false)
    private boolean deleted = false;

    public Movie(String title, Integer year, String genre, BigDecimal rating, String overview) {
        this.title = title;
        this.year = year;
        this.genre = genre;
        this.rating = rating;
        this.overview = overview;
        this.deleted = false;
    }
}
