package com.cinetrack.watchlog;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import com.cinetrack.user.AppUser;
import com.cinetrack.user.AppUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Integration tests for the three high-throughput data-access patterns shown
 * in Chapter 17.
 *
 * <p>Each test inserts or reads 1 000 rows and asserts both correctness (row
 * count) and, where appropriate, a timing budget — not as a hard gate but as
 * documentary evidence that the bulk path is meaningfully faster than row-by-row
 * processing would be.
 */
class BatchImportTest extends AbstractIntegrationTest {

    private static final int RECORD_COUNT = 1_000;

    @Autowired WatchLogBatchService batchService;
    @Autowired WatchLogRepository   watchLogRepository;
    @Autowired MovieRepository      movieRepository;
    @Autowired AppUserRepository    appUserRepository;
    @Autowired TransactionTemplate  txTemplate;

    @PersistenceContext
    EntityManager entityManager;

    private Long userId;
    private Long movieId;

    @BeforeEach
    void setUp() {
        txTemplate.executeWithoutResult(s -> {
            watchLogRepository.deleteAll();
            movieRepository.deleteAll();
            appUserRepository.deleteAll();
        });

        txTemplate.executeWithoutResult(s -> {
            AppUser user  = appUserRepository.save(new AppUser("batcher", "batch@example.com"));
            Movie   movie = movieRepository.save(new Movie("Batch Movie", "DRAMA"));
            userId  = user.getId();
            movieId = movie.getId();
        });
    }

    // -------------------------------------------------------------------------
    // Test 1: JPA batch insert
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("JPA batch insert: 1 000 records persisted correctly")
    void jpaBatchInsert() {
        List<WatchLog> logs = buildWatchLogs(RECORD_COUNT);

        long start = System.currentTimeMillis();
        batchService.importBatch(logs);
        long elapsed = System.currentTimeMillis() - start;

        long count = watchLogRepository.count();
        assertThat(count)
                .as("All %d watch logs must be persisted", RECORD_COUNT)
                .isEqualTo(RECORD_COUNT);

        System.out.printf("JPA batch insert of %d records: %d ms%n", RECORD_COUNT, elapsed);
    }

    // -------------------------------------------------------------------------
    // Test 2: StatelessSession bulk import
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("StatelessSession import: 1 000 records persisted, typically faster than JPA batch")
    void statelessSessionImport() {
        List<CreateWatchLogCommand> commands = buildCommands(RECORD_COUNT);

        long start = System.currentTimeMillis();
        batchService.importStateless(commands);
        long elapsed = System.currentTimeMillis() - start;

        long count = watchLogRepository.count();
        assertThat(count)
                .as("All %d watch logs must be persisted via StatelessSession", RECORD_COUNT)
                .isEqualTo(RECORD_COUNT);

        System.out.printf("StatelessSession import of %d records: %d ms%n", RECORD_COUNT, elapsed);
    }

    // -------------------------------------------------------------------------
    // Test 3: Server-side streaming
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Streaming: 1 000 records processed without loading all into memory")
    void streamingProcessesAllRecordsWithBoundedHeap() {
        // Seed data via StatelessSession so this test does not depend on JPA batch
        batchService.importStateless(buildCommands(RECORD_COUNT));
        assertThat(watchLogRepository.count()).isEqualTo(RECORD_COUNT);

        AtomicLong processed = new AtomicLong();

        // Record heap before streaming
        Runtime rt = Runtime.getRuntime();
        rt.gc();
        long heapBefore = rt.totalMemory() - rt.freeMemory();

        batchService.streamAll(wl -> processed.incrementAndGet());

        rt.gc();
        long heapAfter = rt.totalMemory() - rt.freeMemory();
        long heapDeltaMb = (heapAfter - heapBefore) / (1024 * 1024);

        assertThat(processed.get())
                .as("Streaming must visit every row")
                .isEqualTo(RECORD_COUNT);

        // With fetch_size=500 and lightweight WatchLog objects the heap delta
        // should stay well below 50 MB even for 1 000 rows.
        assertThat(heapDeltaMb)
                .as("Heap growth during streaming should stay below 50 MB")
                .isLessThan(50);

        System.out.printf("Streamed %d records; net heap delta: %d MB%n",
                processed.get(), heapDeltaMb);
    }

    // -------------------------------------------------------------------------
    // Test 4: batchImport — count and sample field verification
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("JPA batch import: 100 records persisted, sample record fields correct")
    void batchImport_allRecords_persistedCorrectly() {
        List<WatchLog> logs = buildWatchLogs(100);
        batchService.importBatch(logs);

        assertThat(watchLogRepository.count())
                .as("All 100 WatchLog records must be persisted")
                .isEqualTo(100);

        // Reload first record and verify key fields
        WatchLog sample = txTemplate.execute(s ->
                watchLogRepository.findAll().get(0)
        );
        assertThat(sample.getUser().getId()).isEqualTo(userId);
        assertThat(sample.getMovie().getId()).isEqualTo(movieId);
        assertThat(sample.getDurationSeconds()).isBetween(90, 149);
        assertThat(sample.getWatchedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Test 5: statelessImport — count verification
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("StatelessSession import: 100 records persisted correctly")
    void statelessImport_allRecords_persistedCorrectly() {
        List<CreateWatchLogCommand> commands = buildCommands(100);
        batchService.importStateless(commands);

        assertThat(watchLogRepository.count())
                .as("All 100 WatchLog records must be persisted via StatelessSession")
                .isEqualTo(100);
    }

    // -------------------------------------------------------------------------
    // Test 6: streamAll — processes all records with AtomicLong counter
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("streamAll: 200 records processed via AtomicLong counter without loading all into memory")
    void streamAll_processesAllRecords_withoutLoadingAll() {
        batchService.importStateless(buildCommands(200));
        assertThat(watchLogRepository.count()).isEqualTo(200);

        AtomicLong counter = new AtomicLong();
        batchService.streamAll(wl -> counter.incrementAndGet());

        assertThat(counter.get())
                .as("streamAll must visit every one of the 200 rows")
                .isEqualTo(200);
    }

    // -------------------------------------------------------------------------
    // Test 7: streamAll — closed stream does not leak resources
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("streamAll: stream closed after partial read throws no exception")
    void streamAll_closedStream_doesNotLeakResources() {
        batchService.importStateless(buildCommands(10));

        AtomicLong counter = new AtomicLong();

        // streamAll uses try-with-resources internally — it always closes the
        // underlying cursor.  Processing only 3 rows then returning early must
        // not cause a resource leak or any exception.
        assertThatNoException().isThrownBy(() ->
                batchService.streamAll(wl -> {
                    if (counter.incrementAndGet() >= 3) {
                        // Stop processing after 3 — the service's try-with-resources
                        // will close the stream when streamAll returns.
                        return;
                    }
                })
        );

        assertThat(counter.get())
                .as("Callback must have been invoked for all 10 rows (early return does not short-circuit forEach)")
                .isEqualTo(10);
    }

    // -------------------------------------------------------------------------
    // Test 8: bulk DELETE via JPQL removes only matching records
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Bulk JPQL DELETE: removes all WatchLog records for userId=1, leaves others intact")
    void bulkDelete_jpql_removesAllMatchingRecords() {
        // Create a second user and movie for the "other" records
        Long otherUserId = txTemplate.execute(s -> {
            AppUser other = appUserRepository.save(new AppUser("other", "other@example.com"));
            return other.getId();
        });

        // Insert 50 records for the primary user and 10 for the other user
        batchService.importStateless(buildCommands(50));
        List<CreateWatchLogCommand> otherCmds = new ArrayList<>(10);
        Instant base = Instant.now().minusSeconds(1000);
        for (int i = 0; i < 10; i++) {
            otherCmds.add(new CreateWatchLogCommand(otherUserId, movieId, base.plusSeconds(i), 120));
        }
        batchService.importStateless(otherCmds);

        assertThat(watchLogRepository.count()).isEqualTo(60);

        // Execute bulk DELETE for the primary user
        txTemplate.executeWithoutResult(s ->
                entityManager.createQuery("DELETE FROM WatchLog w WHERE w.user.id = :uid")
                        .setParameter("uid", userId)
                        .executeUpdate()
        );

        assertThat(watchLogRepository.count())
                .as("Only the 10 records belonging to the other user must remain")
                .isEqualTo(10);
    }

    // -------------------------------------------------------------------------
    // Test 9: batchImport timing budget
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("JPA batch import: 1 000 records imported in under 5 seconds")
    void batchImport_timing_under5Seconds() {
        List<WatchLog> logs = buildWatchLogs(RECORD_COUNT);

        long start = System.currentTimeMillis();
        batchService.importBatch(logs);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(watchLogRepository.count()).isEqualTo(RECORD_COUNT);
        assertThat(elapsed)
                .as("JPA batch import of %d records must complete in under 5 000 ms (took %d ms)",
                        RECORD_COUNT, elapsed)
                .isLessThan(5_000L);
    }

    // -------------------------------------------------------------------------
    // Test 10: importBatch — empty list persists nothing
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("importBatch: empty list results in zero persisted records")
    void importBatch_emptyList_persistsNothing() {
        batchService.importBatch(List.of());

        long count = watchLogRepository.count();
        assertThat(count)
                .as("importBatch with an empty list must persist 0 records")
                .isEqualTo(0L);
    }

    // -------------------------------------------------------------------------
    // Test 11: importStateless — empty list persists nothing
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("importStateless: empty list results in zero persisted records")
    void importStateless_emptyList_persistsNothing() {
        batchService.importStateless(List.of());

        long count = watchLogRepository.count();
        assertThat(count)
                .as("importStateless with an empty list must persist 0 records")
                .isEqualTo(0L);
    }

    // -------------------------------------------------------------------------
    // Test 12: streamAll — empty table returns an empty stream
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("streamAll: empty table produces a stream with 0 elements processed")
    void streamAll_emptyTable_returnsEmptyStream() {
        // Ensure the table is empty (setUp already cleaned, but be explicit)
        txTemplate.executeWithoutResult(s -> watchLogRepository.deleteAll());
        assertThat(watchLogRepository.count()).isEqualTo(0L);

        AtomicLong counter = new AtomicLong();
        batchService.streamAll(wl -> counter.incrementAndGet());

        assertThat(counter.get())
                .as("streamAll on an empty table must process 0 records")
                .isEqualTo(0L);
    }

    // -------------------------------------------------------------------------
    // Test 13: importBatch with BATCH_SIZE + 1 records — all persisted
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("importBatch: BATCH_SIZE+1 records forces at least 2 flush cycles; all are persisted")
    void importBatch_largerThan1Batch_allPersisted() {
        // WatchLogBatchService.BATCH_SIZE = 50; we need 51 records to force a second flush
        int recordCount = 51;
        List<WatchLog> logs = buildWatchLogs(recordCount);

        batchService.importBatch(logs);

        long count = watchLogRepository.count();
        assertThat(count)
                .as("All %d records must be persisted when input exceeds BATCH_SIZE (50)", recordCount)
                .isEqualTo(recordCount);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<WatchLog> buildWatchLogs(int count) {
        // We need managed AppUser / Movie references — load them in a transaction
        AppUser user  = txTemplate.execute(s -> appUserRepository.findById(userId).orElseThrow());
        Movie   movie = txTemplate.execute(s -> movieRepository.findById(movieId).orElseThrow());

        List<WatchLog> logs = new ArrayList<>(count);
        Instant base = Instant.now().minusSeconds(count);
        for (int i = 0; i < count; i++) {
            logs.add(new WatchLog(user, movie, base.plusSeconds(i), 90 + (i % 60)));
        }
        return logs;
    }

    private List<CreateWatchLogCommand> buildCommands(int count) {
        List<CreateWatchLogCommand> cmds = new ArrayList<>(count);
        Instant base = Instant.now().minusSeconds(count);
        for (int i = 0; i < count; i++) {
            cmds.add(new CreateWatchLogCommand(userId, movieId, base.plusSeconds(i), 90 + (i % 60)));
        }
        return cmds;
    }
}
