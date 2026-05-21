package com.cinetrack.media;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A multi-episode television series. Episodes are owned by the series via a
 * bidirectional one-to-many. CascadeType.ALL means persisting a Series
 * automatically persists its episodes.
 */
@Entity
@DiscriminatorValue("SERIES")
@Getter
@Setter
@NoArgsConstructor
public class Series extends MediaItem {

    @Column(name = "total_seasons")
    private int totalSeasons;

    @OneToMany(mappedBy = "series", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Episode> episodes = new ArrayList<>();

    public Series(String title, int releaseYear, Genre genre, int totalSeasons) {
        super(title, releaseYear, genre);
        this.totalSeasons = totalSeasons;
    }

    public void addEpisode(Episode episode) {
        episodes.add(episode);
        episode.setSeries(this);
    }
}
