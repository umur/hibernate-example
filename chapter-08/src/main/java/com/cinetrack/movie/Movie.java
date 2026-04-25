package com.cinetrack.movie;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.NaturalId;

/**
 * Movie uses a database sequence generator with an allocationSize of 50,
 * meaning Hibernate fetches one sequence value and then hands out IDs
 * 1..50 in memory before hitting the DB again — 50x fewer round-trips
 * than IDENTITY generation.
 *
 * The imdbId is declared as a @NaturalId: a business identifier that is
 * stable and unique. Hibernate caches natural-ID lookups in the
 * second-level cache so repeated findByImdbId calls within the same
 * session issue zero SQL after the first hit.
 */
@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "movie_seq")
    @SequenceGenerator(name = "movie_seq", sequenceName = "movie_seq", allocationSize = 50)
    private Long id;

    @NaturalId
    @Column(name = "imdb_id", unique = true)
    private String imdbId;

    @Column(nullable = false)
    private String title;

    @Column(name = "genre")
    private String genre;

    public Movie(String imdbId, String title, String genre) {
        this.imdbId = imdbId;
        this.title = title;
        this.genre = genre;
    }
}
