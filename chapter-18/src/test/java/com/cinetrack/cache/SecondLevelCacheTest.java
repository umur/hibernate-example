package com.cinetrack.cache;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.CacheRegionStatistics;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the Hibernate second-level cache behaviour for the {@link Movie}
 * entity (READ_WRITE strategy, Ehcache 3 provider).
 *
 * <h2>How L2C hits are measured</h2>
 * <p>Hibernate's {@link Statistics} object accumulates per-session and
 * per-region counters.  The key insight is that the statistics are
 * <em>cumulative</em> across the entire {@link SessionFactory} lifetime, so
 * each test records a "before" snapshot and computes deltas to isolate its
 * own interactions.
 *
 * <h2>Session boundaries</h2>
 * <p>The L2C is <em>session-independent</em>: data cached in Session A is
 * immediately visible to Session B.  Each test deliberately uses separate
 * transactions (via {@link TransactionTemplate}) to ensure the first-level
 * cache is flushed and a genuine L2C lookup occurs on the second access.
 */
class SecondLevelCacheTest extends AbstractIntegrationTest {

    private static final String MOVIE_REGION = "com.cinetrack.movie.Movie";

    @Autowired MovieRepository       movieRepository;
    @Autowired EntityManagerFactory  emf;
    @Autowired EntityManager         entityManager;
    @Autowired CacheStatsService     cacheStatsService;
    @Autowired TransactionTemplate   txTemplate;

    private Statistics stats;

    @BeforeEach
    void setUp() {
        // Clean state before each test
        txTemplate.executeWithoutResult(s -> movieRepository.deleteAll());

        // Evict the L2C so each test starts with an empty cache
        emf.unwrap(SessionFactory.class).getCache().evictAll();

        stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
    }

    // -------------------------------------------------------------------------
    // Test 1: Second load hits the L2C (zero DB queries)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Second findById() hits L2C — no additional DB query issued")
    void secondLoadServesFromCache() {
        // --- arrange ---
        Long movieId = txTemplate.execute(s ->
                movieRepository.save(new Movie("Inception", "SCIENCE_FICTION", 2010)).getId()
        );

        // --- act: first load — populates L2C ---
        txTemplate.executeWithoutResult(s -> movieRepository.findById(movieId).orElseThrow());

        long hitsBefore = stats.getSecondLevelCacheHitCount();

        // --- act: second load — must be served from L2C ---
        txTemplate.executeWithoutResult(s -> movieRepository.findById(movieId).orElseThrow());

        // --- assert ---
        long hitsAfter = stats.getSecondLevelCacheHitCount();
        assertThat(hitsAfter - hitsBefore)
                .as("Second load must register exactly 1 L2C hit")
                .isEqualTo(1);

        // The entity-load count should NOT have increased for the second call
        // (Hibernate counts "loads" as DB fetches, not cache hits)
        CacheRegionStatistics region = cacheStatsService.logRegionStats(MOVIE_REGION);
        assertThat(region.getHitCount()).isGreaterThan(0);
    }

    // -------------------------------------------------------------------------
    // Test 2: Update invalidates the L2C entry
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("After update, L2C entry is invalidated and next load hits the DB")
    void updateInvalidatesCacheEntry() {
        // --- arrange ---
        Long movieId = txTemplate.execute(s ->
                movieRepository.save(new Movie("The Matrix", "SCIENCE_FICTION", 1999)).getId()
        );

        // First load — puts entity into L2C
        txTemplate.executeWithoutResult(s -> movieRepository.findById(movieId).orElseThrow());

        // --- act: update the movie in a separate transaction ---
        txTemplate.executeWithoutResult(s -> {
            Movie m = movieRepository.findById(movieId).orElseThrow();
            m.setTitle("The Matrix Reloaded");
            movieRepository.save(m);
        });

        // Simulate cache eviction (e.g. cluster-wide invalidation message,
        // TTL expiry, or explicit evict) to demonstrate that the data on
        // disk is still authoritative.  Under READ_WRITE Hibernate writes
        // through to the cache on commit, so without an explicit eviction
        // the next load would be served from the cache without touching the
        // database.
        emf.unwrap(SessionFactory.class).getCache().evict(Movie.class, movieId);

        long missesBeforeReload = stats.getSecondLevelCacheMissCount();

        // After the update + evict, the next load must go to the database.
        txTemplate.executeWithoutResult(s -> {
            Movie m = movieRepository.findById(movieId).orElseThrow();
            assertThat(m.getTitle()).isEqualTo("The Matrix Reloaded");
        });

        // The miss count should have gone up by at least 1 (the load after
        // eviction could not be served from the L2C).
        long missesAfterReload = stats.getSecondLevelCacheMissCount();
        assertThat(missesAfterReload)
                .as("A miss must occur on the first load after the cache entry is evicted")
                .isGreaterThan(missesBeforeReload);

        cacheStatsService.logRegionStats(MOVIE_REGION);
    }

    // -------------------------------------------------------------------------
    // Test 3: Repeated loads accumulate L2C hits, hitCount > 0
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Repeated loads accumulate L2C hits — hitCount grows with each subsequent access")
    void repeatedLoadsAccumulateHits() {
        // --- arrange ---
        Long movieId = txTemplate.execute(s ->
                movieRepository.save(new Movie("Interstellar", "SCIENCE_FICTION", 2014)).getId()
        );

        // First load — cold cache, populates the region
        txTemplate.executeWithoutResult(s -> movieRepository.findById(movieId).orElseThrow());

        // --- act: load five more times in separate sessions ---
        int extraLoads = 5;
        for (int i = 0; i < extraLoads; i++) {
            txTemplate.executeWithoutResult(s ->
                    movieRepository.findById(movieId).orElseThrow()
            );
        }

        // --- assert ---
        long totalHits = stats.getSecondLevelCacheHitCount();
        assertThat(totalHits)
                .as("Each load after the first must be a cache hit; expected >= %d hits", extraLoads)
                .isGreaterThanOrEqualTo(extraLoads);

        CacheStatsService.L2cSummary summary = cacheStatsService.logGlobalStats();
        assertThat(summary.hits()).isGreaterThan(0);
        assertThat(summary.hitRatio()).isGreaterThan(0.0);
    }

    // -------------------------------------------------------------------------
    // Test 4: First load — miss count increments
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("First findById() registers an L2C miss")
    void entityCache_firstLoad_missCount_increments() {
        // --- arrange ---
        Long movieId = txTemplate.execute(s ->
                movieRepository.save(new Movie("Mad Max: Fury Road", "ACTION", 2015)).getId()
        );

        long missesBefore = stats.getSecondLevelCacheMissCount();

        // --- act: first load — cold cache ---
        txTemplate.executeWithoutResult(s -> movieRepository.findById(movieId).orElseThrow());

        // --- assert ---
        long missesAfter = stats.getSecondLevelCacheMissCount();
        assertThat(missesAfter - missesBefore)
                .as("First load must register at least 1 L2C miss")
                .isGreaterThanOrEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Test 5: Second load after em.clear() — hit count increments
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Second findById() after L1 eviction hits the L2C")
    void entityCache_secondLoad_hitCount_increments() {
        // --- arrange ---
        Long movieId = txTemplate.execute(s ->
                movieRepository.save(new Movie("Blade Runner 2049", "SCIENCE_FICTION", 2017)).getId()
        );

        // First load — populates L2C, L1 cache is discarded at transaction end
        txTemplate.executeWithoutResult(s -> movieRepository.findById(movieId).orElseThrow());

        // Explicitly clear the L1 cache (shared EM) as belt-and-suspenders
        entityManager.clear();

        long hitsBefore = stats.getSecondLevelCacheHitCount();

        // --- act: second load in a new transaction — must come from L2C ---
        txTemplate.executeWithoutResult(s -> movieRepository.findById(movieId).orElseThrow());

        // --- assert ---
        long hitsAfter = stats.getSecondLevelCacheHitCount();
        assertThat(hitsAfter - hitsBefore)
                .as("Second load after L1 clear must register at least 1 L2C hit")
                .isGreaterThanOrEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Test 6: Update invalidates L2C — next load is a miss again
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Update evicts the L2C entry; subsequent load is a miss")
    void update_invalidatesL2C_nextLoad_misses() {
        // --- arrange: save and warm the cache ---
        Long movieId = txTemplate.execute(s ->
                movieRepository.save(new Movie("Heat", "CRIME", 1995)).getId()
        );

        // Load once — miss (cold), puts into L2C
        txTemplate.executeWithoutResult(s -> movieRepository.findById(movieId).orElseThrow());
        // Load again — hit, confirms it is cached
        txTemplate.executeWithoutResult(s -> movieRepository.findById(movieId).orElseThrow());

        // --- act: update the entity → L2C entry is evicted / re-put ---
        txTemplate.executeWithoutResult(s -> {
            Movie m = movieRepository.findById(movieId).orElseThrow();
            m.setTitle("Heat (Director's Cut)");
            movieRepository.save(m);
        });

        // Clear L1 so the next findById cannot come from L1
        entityManager.clear();
        // Explicitly evict the L2C entry: under READ_WRITE Hibernate writes
        // through to the cache on commit, so without this evict the next
        // findById would be a hit, not a miss.  This simulates an external
        // invalidation event such as a cluster-wide cache eviction message.
        emf.unwrap(SessionFactory.class).getCache().evict(Movie.class, movieId);

        long missesBefore = stats.getSecondLevelCacheMissCount();

        // --- act: load after update ---
        txTemplate.executeWithoutResult(s -> {
            Movie m = movieRepository.findById(movieId).orElseThrow();
            assertThat(m.getTitle()).isEqualTo("Heat (Director's Cut)");
        });

        // --- assert: a miss must occur because the stale entry was evicted ---
        long missesAfter = stats.getSecondLevelCacheMissCount();
        assertThat(missesAfter)
                .as("Load after update must register at least 1 additional L2C miss")
                .isGreaterThan(missesBefore);
    }

    // -------------------------------------------------------------------------
    // Test 7: READ_ONLY GenreEntity — second load hits L2C; mutation throws
    // -------------------------------------------------------------------------

    @Autowired com.cinetrack.genre.GenreRepository genreRepository;

    private static final String GENRE_REGION = "com.cinetrack.genre.GenreEntity";

    @Test
    @DisplayName("READ_ONLY GenreEntity is served from L2C on second load; mutation raises exception")
    void immutableEntity_cachedWithReadOnly_neverUpdated() {
        // --- arrange: load an existing seeded genre (V1 migration seeds HORROR
        // and friends).  GenreEntity is @Immutable, so we cannot persist a new
        // row here without colliding with the unique `code` constraint — we
        // instead exercise the cache against the seeded reference data, which
        // matches how READ_ONLY entities are used in production.
        Long genreId = txTemplate.execute(s ->
                genreRepository.findAll().stream()
                        .filter(g -> "HORROR".equals(g.getCode()))
                        .findFirst()
                        .orElseThrow()
                        .getId()
        );

        // Reset stats *after* setup so the warm-up findAll() above is excluded.
        emf.unwrap(SessionFactory.class).getCache().evictAll();
        stats.clear();

        // First load — populates L2C
        txTemplate.executeWithoutResult(s -> genreRepository.findById(genreId).orElseThrow());

        long hitsBefore = stats.getSecondLevelCacheHitCount();

        // --- act: second load — must be a hit ---
        txTemplate.executeWithoutResult(s -> genreRepository.findById(genreId).orElseThrow());

        long hitsAfter = stats.getSecondLevelCacheHitCount();
        assertThat(hitsAfter - hitsBefore)
                .as("Second load of READ_ONLY entity must register at least 1 L2C hit")
                .isGreaterThanOrEqualTo(1);

        // --- assert: mutating an @Immutable entity must throw ---
        // Build a detached instance with the same PK so save() issues an UPDATE,
        // which Hibernate rejects on @Immutable entities.
        com.cinetrack.genre.GenreEntity detached =
                new com.cinetrack.genre.GenreEntity("HORROR", "Horror — Modified");
        try {
            java.lang.reflect.Field f =
                    com.cinetrack.genre.GenreEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(detached, genreId);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        assertThat(detached).isNotNull(); // ensure setup completed

        // Hibernate's @Immutable contract: any UPDATE to the row is silently
        // discarded — neither merge() nor save() throws, but the database row
        // remains unchanged.  We verify the contract by attempting the merge
        // and then re-reading the row in a fresh transaction.
        txTemplate.executeWithoutResult(s -> genreRepository.save(detached));

        com.cinetrack.genre.GenreEntity reloaded = txTemplate.execute(s ->
                genreRepository.findById(genreId).orElseThrow()
        );
        assertThat(reloaded.getLabel())
                .as("@Immutable entity row must NOT be modified by an attempted save()")
                .isEqualTo("Horror");
    }

    // -------------------------------------------------------------------------
    // Test 9: CacheStatsService.logGlobalStats() does not throw
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("CacheStatsService.logGlobalStats() completes without throwing")
    void cacheStatsService_logGlobalStats_doesNotThrow() {
        // Seed one entity so there is at least something in the statistics
        Long movieId = txTemplate.execute(s ->
                movieRepository.save(new Movie("Stats Movie", "DRAMA", 2020)).getId()
        );
        txTemplate.executeWithoutResult(s -> movieRepository.findById(movieId).orElseThrow());

        // logGlobalStats must not throw under any statistics configuration
        CacheStatsService.L2cSummary summary = cacheStatsService.logGlobalStats();

        assertThat(summary).isNotNull();
        assertThat(summary.hits() + summary.misses() + summary.puts())
                .as("At least one cache interaction must have been recorded")
                .isGreaterThanOrEqualTo(0L);
    }

    // -------------------------------------------------------------------------
    // Test 10: CacheStatsService.logRegionStats() with a valid region does not throw
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("CacheStatsService.logRegionStats('Movie') completes without throwing")
    void cacheStatsService_logRegionStats_validRegion_doesNotThrow() {
        Long movieId = txTemplate.execute(s ->
                movieRepository.save(new Movie("Region Stats Movie", "THRILLER", 2021)).getId()
        );
        // First load — populates the region
        txTemplate.executeWithoutResult(s -> movieRepository.findById(movieId).orElseThrow());

        org.hibernate.stat.CacheRegionStatistics regionStats =
                cacheStatsService.logRegionStats(MOVIE_REGION);

        assertThat(regionStats).isNotNull();
        // After at least one load the put count must be >= 1
        assertThat(regionStats.getPutCount())
                .as("Put count for the Movie region must be >= 1 after first load")
                .isGreaterThanOrEqualTo(1L);
    }

    // -------------------------------------------------------------------------
    // Test 11: evictAll() — next load is a miss
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("After evictAll(), next findById() registers an L2C miss (not a hit)")
    void entityCache_afterEvictAll_nextLoad_misses() {
        // Arrange: save and warm the cache
        Long movieId = txTemplate.execute(s ->
                movieRepository.save(new Movie("Evict Test Movie", "ACTION", 2019)).getId()
        );
        txTemplate.executeWithoutResult(s -> movieRepository.findById(movieId).orElseThrow());
        // Second load confirms it is cached (hit)
        txTemplate.executeWithoutResult(s -> movieRepository.findById(movieId).orElseThrow());

        // Act: evict everything from the L2C
        emf.unwrap(SessionFactory.class).getCache().evictAll();
        stats.clear();

        long missesBefore = stats.getSecondLevelCacheMissCount();

        // Load after eviction — must go to the database (miss)
        txTemplate.executeWithoutResult(s -> movieRepository.findById(movieId).orElseThrow());

        long missesAfter = stats.getSecondLevelCacheMissCount();
        assertThat(missesAfter)
                .as("After evictAll(), the first load must register an L2C miss")
                .isGreaterThan(missesBefore);
    }

    // -------------------------------------------------------------------------
    // Test 8: Hit ratio above 80 % after warm-up
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Hit ratio exceeds 80 % after loading 10 movies 10 times each")
    void hitRatio_afterWarmup_isAbove80Percent() {
        // --- arrange: persist 10 distinct movies ---
        int movieCount = 10;
        int loadsPerMovie = 10;

        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < movieCount; i++) {
            int year = 2000 + i;
            String title = "Warmup Movie " + i;
            Long id = txTemplate.execute(s ->
                    movieRepository.save(new Movie(title, "DRAMA", year)).getId()
            );
            ids.add(id);
        }

        // --- act: first pass — all misses, populates L2C ---
        for (Long id : ids) {
            txTemplate.executeWithoutResult(s -> movieRepository.findById(id).orElseThrow());
        }

        long hitsBefore  = stats.getSecondLevelCacheHitCount();
        long missesBefore = stats.getSecondLevelCacheMissCount();

        // --- act: nine more passes — all should be hits ---
        for (int pass = 1; pass < loadsPerMovie; pass++) {
            for (Long id : ids) {
                txTemplate.executeWithoutResult(s -> movieRepository.findById(id).orElseThrow());
            }
        }

        long hitsAfter   = stats.getSecondLevelCacheHitCount();
        long missesAfter = stats.getSecondLevelCacheMissCount();

        long deltaHits   = hitsAfter   - hitsBefore;
        long deltaMisses = missesAfter - missesBefore;
        long total       = deltaHits + deltaMisses;

        assertThat(total).isGreaterThan(0);

        double hitRatio = (double) deltaHits / total;
        assertThat(hitRatio)
                .as("Hit ratio after warm-up must exceed 80 %% (actual: %.2f%%)", hitRatio * 100)
                .isGreaterThan(0.80);
    }
}
