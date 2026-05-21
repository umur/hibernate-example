package com.cinetrack.media;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A standalone film. The discriminator value "MOVIE" is written into the
 * dtype column so Hibernate knows which Java type to instantiate on load.
 */
@Entity
@DiscriminatorValue("MOVIE")
@Getter
@Setter
@NoArgsConstructor
public class Movie extends MediaItem {

    @Column(name = "runtime_minutes")
    private int runtimeMinutes;

    public Movie(String title, int releaseYear, Genre genre, int runtimeMinutes) {
        super(title, releaseYear, genre);
        this.runtimeMinutes = runtimeMinutes;
    }
}
