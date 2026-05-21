package com.cinetrack.movie;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

import java.math.BigDecimal;

@Entity
@Table(name = "movies")
@Indexed
@Getter
@Setter
@NoArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "movie_seq")
    @SequenceGenerator(name = "movie_seq", sequenceName = "movie_seq", allocationSize = 50)
    private Long id;

    @FullTextField
    @Column(nullable = false)
    private String title;

    @Column(name = "release_year")
    private Integer year;

    @KeywordField
    @Column(nullable = false)
    private String genre;

    @Column(precision = 3, scale = 1)
    private BigDecimal rating;

    @FullTextField
    @Column(columnDefinition = "text")
    private String overview;

    public Movie(String title, Integer year, String genre, BigDecimal rating, String overview) {
        this.title = title;
        this.year = year;
        this.genre = genre;
        this.rating = rating;
        this.overview = overview;
    }
}
