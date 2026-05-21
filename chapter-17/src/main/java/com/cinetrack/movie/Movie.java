package com.cinetrack.movie;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(nullable = false)
    @ToString.Include
    private String title;

    @Column(length = 50)
    private String genre;

    public Movie(String title, String genre) {
        this.title = title;
        this.genre = genre;
    }
}
