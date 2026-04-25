package com.cinetrack.persistence;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.movie.Genre;
import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Chapter 2: The Persistence Context.
 *
 * <p>{@code @SpringBootTest} loads the full application context so we can
 * exercise {@link PersistenceContextDemoService} with real Spring transaction
 * proxies and a real PostgreSQL database (via Testcontainers).
 *
 * <p>Tests that need to cross transaction boundaries use {@link TransactionTemplate}
 * instead of {@code @Transactional} on the test method — because if the test
 * method itself is transactional, all nested calls share the same transaction
 * and we cannot observe commit/rollback effects between them.
 */
@SpringBootTest
@ActiveProfiles("test")
class PersistenceContextTest extends AbstractIntegrationTest {

    @Autowired
    private PersistenceContextDemoService demoService;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private TransactionTemplate txTemplate;

    @PersistenceContext
    private EntityManager em;

    private UUID savedMovieId;

    @BeforeEach
    void setUp() {
        // Persist a Movie in its own transaction so subsequent tests can find it.
        savedMovieId = txTemplate.execute(status -> {
            Movie movie = Movie.builder()
                    .title("The Dark Knight")
                    .genre(Genre.ACTION)
                    .releaseYear(2008)
                    .rating(new BigDecimal("9.0"))
                    .build();
            return movieRepository.save(movie).getId();
        });
    }

    // -------------------------------------------------------------------------
    // Test 1: Identity Map
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("em.find() called twice with the same id returns the identical Java reference")
    void identityMap_returnsSameInstance() {
        // demonstrateIdentityMap runs both finds inside a single transaction,
        // so the identity map guarantee applies and the method returns true.
        boolean sameReference = demoService.demonstrateIdentityMap(savedMovieId);
        assertThat(sameReference).isTrue();
    }

    // -------------------------------------------------------------------------
    // Test 2: Dirty Checking
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Mutating a managed entity without calling save() still persists the change on commit")
    void dirtyChecking_updatesWithoutExplicitSave() {
        String newTitle = "The Dark Knight — Director's Cut";

        // Transaction 1: load the movie, change its title without calling save().
        // Hibernate's dirty checker detects the mutation and issues an UPDATE on commit.
        demoService.demonstrateDirtyChecking(savedMovieId, newTitle);

        // Transaction 2: reload in a fresh persistence context to confirm the UPDATE hit the DB.
        String persistedTitle = txTemplate.execute(status -> {
            Movie reloaded = movieRepository.findById(savedMovieId).orElseThrow();
            return reloaded.getTitle();
        });

        assertThat(persistedTitle).isEqualTo(newTitle);
    }

    // -------------------------------------------------------------------------
    // Test 3: Detach prevents tracking
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Modifying a detached entity without merging leaves the database unchanged")
    void detach_preventsTracking() {
        String originalTitle = txTemplate.execute(status ->
                movieRepository.findById(savedMovieId).orElseThrow().getTitle()
        );

        // Load, detach, mutate — but do NOT call merge.
        // The detached entity's mutation must not reach the database.
        txTemplate.execute(status -> {
            Movie movie = em.find(Movie.class, savedMovieId);
            em.detach(movie);

            // Mutate the detached entity — Hibernate is blind to this change.
            movie.setTitle("[SHOULD NOT PERSIST] " + movie.getTitle());

            // No merge(), no save() — the transaction commits with no dirty entity.
            return null;
        });

        // Verify the database title is unchanged.
        String titleAfter = txTemplate.execute(status ->
                movieRepository.findById(savedMovieId).orElseThrow().getTitle()
        );

        assertThat(titleAfter).isEqualTo(originalTitle);
    }

    // -------------------------------------------------------------------------
    // Test 4: Detach + Merge reattaches and persists
    // -------------------------------------------------------------------------

    @Test
    @Transactional
    @DisplayName("demonstrateDetachReattach() returns a managed entity after merge()")
    void detachReattach_mergedEntityIsManaged() {
        // Persist a brand-new movie inside THIS transaction so its @Version is
        // always 0 and no other test can have incremented it.
        Movie fresh = Movie.builder()
                .title("Detach Reattach Target")
                .genre(Genre.DRAMA)
                .releaseYear(2005)
                .build();
        movieRepository.save(fresh);
        em.flush(); // ensure the INSERT is issued before demonstrateDetachReattach loads it

        // demonstrateDetachReattach is @Transactional — it joins this test's
        // transaction, so 'managed' is tracked by the same persistence context.
        Movie managed = demoService.demonstrateDetachReattach(fresh.getId());

        assertThat(em.contains(managed)).isTrue();
        assertThat(managed.getTitle()).startsWith("[DETACHED]");
    }

    // -------------------------------------------------------------------------
    // Test 5: refresh() overwrites in-memory changes with the DB state
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("em.refresh() discards in-memory mutations and reloads from the database")
    void refresh_overwritesInMemoryChanges() {
        // Transaction 1: persist the movie with a known title.
        UUID movieId = txTemplate.execute(st -> {
            Movie m = Movie.builder()
                    .title("Original Title")
                    .genre(Genre.DRAMA)
                    .releaseYear(2010)
                    .build();
            return movieRepository.save(m).getId();
        });

        // Transaction 2: mutate in memory then refresh — should reset to DB value.
        txTemplate.execute(st -> {
            Movie m = movieRepository.findById(movieId).orElseThrow();
            m.setTitle("Changed In Memory");
            em.refresh(m); // reloads from DB, discarding the in-memory change
            assertThat(m.getTitle()).isEqualTo("Original Title");
            st.setRollbackOnly();
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Test 6: em.contains() returns false after em.detach()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("em.contains() returns false after em.detach()")
    void contains_afterDetach_returnsFalse() {
        txTemplate.execute(st -> {
            Movie m = Movie.builder()
                    .title("Detach Test")
                    .genre(Genre.ACTION)
                    .releaseYear(2015)
                    .build();
            Movie saved = movieRepository.save(m);
            assertThat(em.contains(saved)).isTrue();

            em.detach(saved);
            assertThat(em.contains(saved)).isFalse();

            st.setRollbackOnly();
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Test 7: em.contains() returns false after em.remove()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("em.contains() returns false after em.remove()")
    void contains_afterRemove_returnsFalse() {
        UUID movieId = txTemplate.execute(st -> {
            Movie m = Movie.builder()
                    .title("Remove Test")
                    .genre(Genre.DRAMA)
                    .releaseYear(2012)
                    .build();
            return movieRepository.save(m).getId();
        });

        txTemplate.execute(st -> {
            Movie m = movieRepository.findById(movieId).orElseThrow();
            em.remove(m);
            assertThat(em.contains(m)).isFalse();
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Test 8: em.merge() returns a new managed instance; original stays detached
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("em.merge() returns a new managed instance while the original remains detached")
    void merge_returnsNewManagedInstance_originalRemainsDetached() {
        UUID movieId = txTemplate.execute(st -> {
            Movie m = Movie.builder()
                    .title("Merge Test")
                    .genre(Genre.COMEDY)
                    .releaseYear(2018)
                    .build();
            return movieRepository.save(m).getId();
        });

        txTemplate.execute(st -> {
            Movie m = movieRepository.findById(movieId).orElseThrow();
            em.detach(m);
            m.setTitle("Updated Via Merge");

            Movie managed = em.merge(m);
            assertThat(managed).isNotSameAs(m);
            assertThat(em.contains(managed)).isTrue();
            assertThat(em.contains(m)).isFalse();
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Test 9: FlushMode.COMMIT — pending change not visible to JPQL in same tx
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("FlushMode.COMMIT — in-memory title change is NOT visible to a JPQL query within the same tx")
    void flushModeCommit_pendingChange_notVisibleToQueryInSameTx() {
        // The original title we know the movie has after setUp().
        String originalTitle = txTemplate.execute(st ->
                movieRepository.findById(savedMovieId).orElseThrow().getTitle()
        );

        txTemplate.execute(st -> {
            Movie movie = movieRepository.findById(savedMovieId).orElseThrow();
            String changedTitle = "[COMMIT-MODE-CHANGE] " + originalTitle;

            // Switch to COMMIT flush mode — Hibernate will NOT flush before queries.
            em.setFlushMode(jakarta.persistence.FlushModeType.COMMIT);

            // Mutate the managed entity — not yet flushed.
            movie.setTitle(changedTitle);

            // Run a JPQL query — with COMMIT mode, no flush happens first.
            // The query should return the OLD title from the DB, not the in-memory change.
            String queriedTitle = em
                    .createQuery("SELECT m.title FROM Movie m WHERE m.id = :id", String.class)
                    .setParameter("id", savedMovieId)
                    .getSingleResult();

            assertThat(queriedTitle).isEqualTo(originalTitle)
                    .as("COMMIT flush mode must NOT flush before a JPQL query");

            // Restore AUTO and roll back so we don't pollute other tests.
            em.setFlushMode(jakarta.persistence.FlushModeType.AUTO);
            st.setRollbackOnly();
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Test 10: LazyInitializationException after detach
    // -------------------------------------------------------------------------

    @Autowired
    private com.cinetrack.review.ReviewRepository reviewRepository;

    @Test
    @DisplayName("Accessing a lazy @ManyToOne on a detached Review throws LazyInitializationException")
    void lazyLoad_afterDetach_throwsLazyInitializationException() {
        // Persist a user (required by Review.reviewer FK)
        com.cinetrack.user.AppUser user = txTemplate.execute(st -> {
            com.cinetrack.user.AppUser u = com.cinetrack.user.AppUser.builder()
                    .username("lazy_user_" + System.nanoTime())
                    .email("lazy_" + System.nanoTime() + "@example.com")
                    .passwordHash("$2a$12$placeholder")
                    .build();
            em.persist(u);
            return u;
        });

        // Persist review linked to the already-saved movie
        Long reviewId = txTemplate.execute(st -> {
            // Reload managed instances inside this transaction
            Movie managedMovie = em.find(Movie.class, savedMovieId);
            com.cinetrack.user.AppUser managedUser =
                    em.find(com.cinetrack.user.AppUser.class, user.getId());

            com.cinetrack.review.Review review = com.cinetrack.review.Review.builder()
                    .movie(managedMovie)
                    .reviewer(managedUser)
                    .content("Lazy test review")
                    .rating(8)
                    .build();
            return reviewRepository.save(review).getId();
        });

        // Load and detach the Review inside a transaction, then access the lazy proxy
        // OUTSIDE — where there is no active session to back-fill the proxy.
        com.cinetrack.review.Review detachedReview = txTemplate.execute(st -> {
            com.cinetrack.review.Review review =
                    reviewRepository.findById(reviewId).orElseThrow();
            // Detach: the Review (and its uninitialized lazy Movie proxy) leaves the PC.
            em.detach(review);
            return review; // returned detached — proxy is NOT initialized
        });

        // Accessing the title on the uninitialized lazy proxy of a detached entity,
        // with NO active Session in scope, must trigger LazyInitializationException.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> detachedReview.getMovie().getTitle()
        ).isInstanceOf(org.hibernate.LazyInitializationException.class);
    }

    // -------------------------------------------------------------------------
    // Test 11: demonstrateFlushModes() completes without exception
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("demonstrateFlushModes() executes without throwing")
    void demonstrateFlushModes_doesNotThrow() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> demoService.demonstrateFlushModes(savedMovieId)
        );
    }
}
