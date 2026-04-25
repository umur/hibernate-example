package com.cinetrack.media;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single episode belonging to a Series. The series_id foreign key points
 * back to the same media_items table — a self-referential relationship
 * that works cleanly under SINGLE_TABLE inheritance.
 */
@Entity
@DiscriminatorValue("EPISODE")
@Getter
@Setter
@NoArgsConstructor
public class Episode extends MediaItem {

    @Column(name = "season_number")
    private int seasonNumber;

    @Column(name = "episode_number")
    private int episodeNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    private Series series;

    public Episode(String title, int releaseYear, Genre genre, int seasonNumber, int episodeNumber) {
        super(title, releaseYear, genre);
        this.seasonNumber = seasonNumber;
        this.episodeNumber = episodeNumber;
    }
}
