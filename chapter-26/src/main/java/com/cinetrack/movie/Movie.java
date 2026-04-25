package com.cinetrack.movie;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Table("movies")
@Getter
@Setter
@NoArgsConstructor
public class Movie {

    @Id
    private Long id;

    @Column("title")
    private String title;

    @Column("release_year")
    private Integer year;

    @Column("genre")
    private String genre;

    @Column("rating")
    private BigDecimal rating;

    @Column("overview")
    private String overview;

    public Movie(String title, Integer year, String genre, BigDecimal rating, String overview) {
        this.title = title;
        this.year = year;
        this.genre = genre;
        this.rating = rating;
        this.overview = overview;
    }
}
