package com.cinetrack.watchlist;

import com.cinetrack.user.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

import static jakarta.persistence.FetchType.LAZY;
import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * Watchlist entity demonstrating nested @BatchSize on {@code entries}.
 *
 * When Hibernate loads watchlists for a set of users (already optimised by
 * {@code @Fetch(SUBSELECT)} on {@link AppUser#getWatchlists()}), accessing the
 * {@code entries} collection for multiple watchlists triggers another potential
 * N+1.  {@code @BatchSize(size = 50)} on this collection reduces that to
 * ⌈watchlistCount / 50⌉ + 1 queries.
 */
@Entity
@Table(name = "watchlists")
@Getter
@Setter
@NoArgsConstructor
public class Watchlist {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "watchlist", fetch = LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<WatchlistEntry> entries = new ArrayList<>();

    public Watchlist(AppUser owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    public void addEntry(WatchlistEntry entry) {
        entries.add(entry);
        entry.setWatchlist(this);
    }
}
