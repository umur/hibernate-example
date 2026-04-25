package com.cinetrack.multitenancy;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that schema-based multi-tenancy provides complete data isolation
 * between tenants and that {@link TenantContext} never leaks between tests.
 *
 * <p>Each test uses {@link TransactionTemplate} to drive transactions
 * programmatically, which ensures the transaction commits (and schema routing
 * takes effect) before assertions run.
 */
class MultiTenancyTest extends AbstractIntegrationTest {

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private TransactionTemplate txTemplate;

    @AfterEach
    void cleanUp() {
        // Belt-and-suspenders: ensure no tenant leaks across tests
        TenantContext.clear();

        // Remove test data from both schemas
        for (String tenant : List.of("tenant_a", "tenant_b")) {
            TenantContext.set(tenant);
            try {
                txTemplate.executeWithoutResult(s -> movieRepository.deleteAll());
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Test
    @DisplayName("Movie saved in tenant_a is NOT visible in tenant_b")
    void movieSavedForTenantAIsIsolatedFromTenantB() {
        // Save a movie under tenant_a
        TenantContext.set("tenant_a");
        try {
            txTemplate.executeWithoutResult(s ->
                    movieRepository.save(new Movie("Inception", "SCI_FI")));
        } finally {
            TenantContext.clear();
        }

        // Switch to tenant_b and assert the movie is absent
        TenantContext.set("tenant_b");
        try {
            List<Movie> tenantBMovies = txTemplate.execute(s -> movieRepository.findAll());
            assertThat(tenantBMovies).isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Movie saved in tenant_b is NOT visible in tenant_a")
    void movieSavedForTenantBIsIsolatedFromTenantA() {
        TenantContext.set("tenant_b");
        try {
            txTemplate.executeWithoutResult(s ->
                    movieRepository.save(new Movie("Interstellar", "SCI_FI")));
        } finally {
            TenantContext.clear();
        }

        TenantContext.set("tenant_a");
        try {
            List<Movie> tenantAMovies = txTemplate.execute(s -> movieRepository.findAll());
            assertThat(tenantAMovies).isEmpty();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Each tenant sees only its own movies when both have data")
    void tenantsHaveIndependentMovieLists() {
        TenantContext.set("tenant_a");
        try {
            txTemplate.executeWithoutResult(s ->
                    movieRepository.save(new Movie("The Matrix", "SCI_FI")));
        } finally {
            TenantContext.clear();
        }

        TenantContext.set("tenant_b");
        try {
            txTemplate.executeWithoutResult(s ->
                    movieRepository.save(new Movie("John Wick", "ACTION")));
        } finally {
            TenantContext.clear();
        }

        // tenant_a sees only The Matrix
        TenantContext.set("tenant_a");
        try {
            List<Movie> tenantAMovies = txTemplate.execute(s -> movieRepository.findAll());
            assertThat(tenantAMovies).hasSize(1);
            assertThat(tenantAMovies.getFirst().getTitle()).isEqualTo("The Matrix");
        } finally {
            TenantContext.clear();
        }

        // tenant_b sees only John Wick
        TenantContext.set("tenant_b");
        try {
            List<Movie> tenantBMovies = txTemplate.execute(s -> movieRepository.findAll());
            assertThat(tenantBMovies).hasSize(1);
            assertThat(tenantBMovies.getFirst().getTitle()).isEqualTo("John Wick");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("TenantContext.clear() in finally block prevents ThreadLocal leak")
    void tenantContextClearedAfterRequest() {
        TenantContext.set("tenant_a");
        try {
            // Simulate request processing
            txTemplate.executeWithoutResult(s ->
                    movieRepository.save(new Movie("Tenet", "SCI_FI")));
        } finally {
            TenantContext.clear();
        }

        // After the finally block, the ThreadLocal must be null
        assertThat(TenantContext.get())
                .as("TenantContext must be null after clear() in finally block")
                .isNull();
    }

    // -----------------------------------------------------------------------
    // New tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("tenant_a sees only its own 3 movies; tenant_b sees 0")
    void tenantA_sees_onlyOwnMovies() {
        // Save 3 movies under tenant_a
        TenantContext.set("tenant_a");
        try {
            txTemplate.executeWithoutResult(s -> {
                movieRepository.save(new Movie("Movie A1", "DRAMA"));
                movieRepository.save(new Movie("Movie A2", "DRAMA"));
                movieRepository.save(new Movie("Movie A3", "DRAMA"));
            });
        } finally {
            TenantContext.clear();
        }

        // tenant_b must see none of those movies
        TenantContext.set("tenant_b");
        try {
            List<Movie> tenantBMovies = txTemplate.execute(s -> movieRepository.findAll());
            assertThat(tenantBMovies)
                    .as("tenant_b must not see any movies saved under tenant_a")
                    .isEmpty();
        } finally {
            TenantContext.clear();
        }

        // Confirm tenant_a itself sees exactly 3
        TenantContext.set("tenant_a");
        try {
            List<Movie> tenantAMovies = txTemplate.execute(s -> movieRepository.findAll());
            assertThat(tenantAMovies)
                    .as("tenant_a must see exactly the 3 movies it saved")
                    .hasSize(3);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("TenantContext.clear() sets the tenant to null")
    void tenantContext_cleared_doesNotLeak() {
        TenantContext.set("tenant_a");
        assertThat(TenantContext.get())
                .as("TenantContext.get() must return 'tenant_a' after set()")
                .isEqualTo("tenant_a");

        TenantContext.clear();

        assertThat(TenantContext.get())
                .as("TenantContext.get() must be null after clear()")
                .isNull();
    }

    @Test
    @DisplayName("Both tenants have independent movie counts: 3 vs 2")
    void bothTenants_totalMovieCount_correct() {
        // Insert 3 movies in tenant_a
        TenantContext.set("tenant_a");
        try {
            txTemplate.executeWithoutResult(s -> {
                movieRepository.save(new Movie("Count A1", "THRILLER"));
                movieRepository.save(new Movie("Count A2", "THRILLER"));
                movieRepository.save(new Movie("Count A3", "THRILLER"));
            });
        } finally {
            TenantContext.clear();
        }

        // Insert 2 movies in tenant_b
        TenantContext.set("tenant_b");
        try {
            txTemplate.executeWithoutResult(s -> {
                movieRepository.save(new Movie("Count B1", "ACTION"));
                movieRepository.save(new Movie("Count B2", "ACTION"));
            });
        } finally {
            TenantContext.clear();
        }

        // Verify tenant_a count
        TenantContext.set("tenant_a");
        try {
            long countA = txTemplate.execute(s -> movieRepository.count());
            assertThat(countA)
                    .as("tenant_a must have exactly 3 movies")
                    .isEqualTo(3L);
        } finally {
            TenantContext.clear();
        }

        // Verify tenant_b count
        TenantContext.set("tenant_b");
        try {
            long countB = txTemplate.execute(s -> movieRepository.count());
            assertThat(countB)
                    .as("tenant_b must have exactly 2 movies")
                    .isEqualTo(2L);
        } finally {
            TenantContext.clear();
        }
    }

    // -----------------------------------------------------------------------
    // New tests — spec additions
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("TenantContext: set then overwrite — get() returns the last value")
    void tenantContext_set_overwrite_usesLastValue() {
        TenantContext.set("tenant_a");
        // Overwrite without clearing first
        TenantContext.set("tenant_b");

        try {
            assertThat(TenantContext.get())
                    .as("After overwriting 'tenant_a' with 'tenant_b', get() must return 'tenant_b'")
                    .isEqualTo("tenant_b");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("TenantContext: never set on a fresh thread — get() returns null")
    void tenantContext_neverSet_getReturnsNull() {
        // Run in a fresh thread so the ThreadLocal has never been set in this thread's lifetime
        String[] result = new String[1];
        Thread thread = new Thread(() -> result[0] = TenantContext.get());
        thread.start();
        try {
            thread.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(result[0])
                .as("TenantContext.get() must be null on a thread that never called set()")
                .isNull();
    }

    @Test
    @DisplayName("tenant_a: 5 separate transactions each inserting 1 movie accumulate to count = 5")
    void tenantA_multipleInserts_countAccumulates() {
        for (int i = 1; i <= 5; i++) {
            final int idx = i;
            TenantContext.set("tenant_a");
            try {
                txTemplate.executeWithoutResult(s ->
                        movieRepository.save(new Movie("Accumulate Movie " + idx, "DRAMA")));
            } finally {
                TenantContext.clear();
            }
        }

        TenantContext.set("tenant_a");
        try {
            long count = txTemplate.execute(s -> movieRepository.count());
            assertThat(count)
                    .as("5 separate transactions must accumulate to exactly 5 movies in tenant_a")
                    .isEqualTo(5L);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Same title in different tenants causes no conflict; each tenant sees exactly 1")
    void sameMovieTitle_differentTenants_noConflict() {
        // Save "Inception" in tenant_a
        TenantContext.set("tenant_a");
        try {
            txTemplate.executeWithoutResult(s ->
                    movieRepository.save(new Movie("Inception", "SCI_FI")));
        } finally {
            TenantContext.clear();
        }

        // Save "Inception" in tenant_b
        TenantContext.set("tenant_b");
        try {
            txTemplate.executeWithoutResult(s ->
                    movieRepository.save(new Movie("Inception", "SCI_FI")));
        } finally {
            TenantContext.clear();
        }

        // tenant_a sees exactly 1 movie with that title
        TenantContext.set("tenant_a");
        try {
            List<Movie> moviesA = txTemplate.execute(s -> movieRepository.findAll());
            assertThat(moviesA)
                    .as("tenant_a must see exactly 1 'Inception' movie")
                    .hasSize(1);
            assertThat(moviesA.getFirst().getTitle()).isEqualTo("Inception");
        } finally {
            TenantContext.clear();
        }

        // tenant_b sees exactly 1 movie with that title
        TenantContext.set("tenant_b");
        try {
            List<Movie> moviesB = txTemplate.execute(s -> movieRepository.findAll());
            assertThat(moviesB)
                    .as("tenant_b must see exactly 1 'Inception' movie")
                    .hasSize(1);
            assertThat(moviesB.getFirst().getTitle()).isEqualTo("Inception");
        } finally {
            TenantContext.clear();
        }
    }
}
