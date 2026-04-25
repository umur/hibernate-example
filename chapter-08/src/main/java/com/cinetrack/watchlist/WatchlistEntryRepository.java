package com.cinetrack.watchlist;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistEntryRepository extends JpaRepository<WatchlistEntry, WatchlistEntryId> {
}
