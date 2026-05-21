package com.cinetrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Chapter 6: Associations: Every Variant.
 *
 * <p>This module demonstrates:
 * <ul>
 *   <li>{@code @OneToOne} with shared primary key ({@code @MapsId}) : 
 *       {@code UserProfile} shares its PK with {@code AppUser}</li>
 *   <li>Bidirectional {@code @OneToMany} / {@code @ManyToOne} with
 *       {@code orphanRemoval=true}: {@code Movie} ↔ {@code Review}</li>
 *   <li>Bag vs Set semantics: reviews use {@code List} (bag); watchlist
 *       entries use a composite-key intermediate entity</li>
 *   <li>{@code @ManyToMany} modelled as an intermediate entity
 *       ({@code WatchlistEntry}) to carry extra columns ({@code addedAt},
 *       {@code notes}) and avoid the pitfalls of a direct join table</li>
 *   <li>{@code @ElementCollection}: {@code Movie.tags} stored in
 *       a separate {@code movie_genres} collection table</li>
 *   <li>Cascade types and {@code orphanRemoval} explained in context</li>
 * </ul>
 */
@SpringBootApplication
public class CineTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(CineTrackApplication.class, args);
    }
}
