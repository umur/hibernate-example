package com.cinetrack.cache;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.CacheRegionStatistics;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Service;

/**
 * Reads Hibernate {@link Statistics} and logs second-level cache metrics.
 *
 * <p>Hibernate's statistics subsystem tracks every cache interaction at the
 * region level.  The key counters are:
 * <ul>
 *   <li><b>hitCount</b>: the entry was found in the cache; no database query
 *       was issued.</li>
 *   <li><b>missCount</b>: the entry was not in the cache; Hibernate fell
 *       through to the database and then put the loaded data into the cache.</li>
 *   <li><b>putCount</b>: a new entry was written to the cache (on first load
 *       or after an update).</li>
 * </ul>
 *
 * <p>The hit ratio is {@code hitCount / (hitCount + missCount)}.  A high ratio
 * (> 0.9) indicates the cache is working effectively.  A ratio close to zero
 * usually means the cache is too small, the TTL is too short, or the access
 * pattern is too random to benefit from caching.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CacheStatsService {

    private final EntityManagerFactory entityManagerFactory;

    /**
     * Returns the Hibernate {@link Statistics} object for the current
     * {@link SessionFactory}.
     *
     * <p>Statistics must be enabled via
     * {@code hibernate.generate_statistics=true} (set in
     * {@code application.yml}) otherwise all counters will be zero.
     */
    public Statistics getStatistics() {
        return entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
    }

    /**
     * Logs hit/miss/put counts for a specific L2C region and returns the
     * {@link CacheRegionStatistics} so callers (e.g. tests) can assert on
     * individual counters.
     *
     * @param regionName fully-qualified cache region name, e.g.
     *                   {@code "com.cinetrack.movie.Movie"}
     * @return the region's statistics snapshot
     */
    public CacheRegionStatistics logRegionStats(String regionName) {
        Statistics stats = getStatistics();
        CacheRegionStatistics region = stats.getDomainDataRegionStatistics(regionName);

        log.info("L2C region '{}': hits: {}, misses: {}, puts: {}",
                regionName,
                region.getHitCount(),
                region.getMissCount(),
                region.getPutCount());

        return region;
    }

    /**
     * Logs overall second-level cache statistics across all regions and also
     * returns the global hit/miss/put totals as a simple summary record.
     */
    public L2cSummary logGlobalStats() {
        Statistics stats = getStatistics();

        long hits   = stats.getSecondLevelCacheHitCount();
        long misses = stats.getSecondLevelCacheMissCount();
        long puts   = stats.getSecondLevelCachePutCount();

        double hitRatio = (hits + misses) == 0 ? 0.0
                : (double) hits / (hits + misses);

        log.info("Global L2C: hits: {}, misses: {}, puts: {}, hit-ratio: {}%",
                hits, misses, puts, String.format("%.1f", hitRatio * 100));

        return new L2cSummary(hits, misses, puts, hitRatio);
    }

    /** Immutable snapshot of global L2C counters. */
    public record L2cSummary(long hits, long misses, long puts, double hitRatio) {}
}
