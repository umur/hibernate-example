package com.cinetrack.watchlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    List<Watchlist> findByOwnerId(Long ownerId);

    /** Fetches watchlist with all entries and their associated movies eagerly. */
    @Query("""
            SELECT DISTINCT w FROM Watchlist w
            LEFT JOIN FETCH w.entries e
            LEFT JOIN FETCH e.movie
            WHERE w.id = :id
            """)
    Optional<Watchlist> findByIdWithEntries(Long id);
}
