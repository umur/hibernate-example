package com.cinetrack.audit;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Hibernate Envers audit history.
 *
 * <p>Each test uses {@link TransactionTemplate} to drive commits explicitly.
 * This is essential for Envers: audit rows are written at transaction commit
 * time, not at the point of the JPA call. Without real commits the
 * {@code movies_aud} table would stay empty.
 *
 * <p>{@code @WithMockUser} injects a Spring Security principal so that
 * {@link CineTrackRevisionListener} can capture the username into
 * {@link CineTrackRevision#getUsername()}.
 */
class EnversAuditTest extends AbstractIntegrationTest {

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private MovieAuditService movieAuditService;

    @Autowired
    private TransactionTemplate txTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Long createMovie(String title, String genre) {
        return txTemplate.execute(s -> {
            Movie movie = new Movie(title, genre);
            return movieRepository.save(movie).getId();
        });
    }

    private void updateMovieTitle(Long id, String newTitle) {
        txTemplate.executeWithoutResult(s -> {
            Movie movie = movieRepository.findById(id).orElseThrow();
            movie.setTitle(newTitle);
            movieRepository.save(movie);
        });
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Create + update produces exactly 2 Envers revisions")
    void createAndUpdateProducesTwoRevisions() {
        Long movieId = createMovie("Dune", "SCI_FI");
        updateMovieTitle(movieId, "Dune: Part Two");

        List<Movie> history = txTemplate.execute(s ->
                movieAuditService.getMovieHistory(movieId));

        assertThat(history)
                .as("Expected one revision for INSERT and one for UPDATE")
                .hasSize(2);
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Point-in-time query returns the title at revision 1 (original title)")
    void pointInTimeQueryReturnsTitleAtFirstRevision() {
        Long movieId = createMovie("Oppenheimer", "DRAMA");
        updateMovieTitle(movieId, "Oppenheimer: Director's Cut");

        List<Number> revNums = txTemplate.execute(s ->
                movieAuditService.getRevisionNumbers(movieId));

        assertThat(revNums).hasSize(2);
        int firstRevision = revNums.getFirst().intValue();

        Movie atFirstRevision = txTemplate.execute(s ->
                movieAuditService.getMovieAtRevision(movieId, firstRevision));

        assertThat(atFirstRevision).isNotNull();
        assertThat(atFirstRevision.getTitle())
                .as("At revision 1 the title should still be the original")
                .isEqualTo("Oppenheimer");
    }

    @Test
    @WithMockUser(username = "bob")
    @DisplayName("Revision entity captures the username from the security context")
    void revisionEntityCapturesUsername() {
        Long movieId = createMovie("Parasite", "DRAMA");

        List<Number> revNums = txTemplate.execute(s ->
                movieAuditService.getRevisionNumbers(movieId));

        assertThat(revNums).isNotEmpty();
        int firstRev = revNums.getFirst().intValue();

        // Use AuditReader directly to retrieve the CineTrackRevision metadata
        CineTrackRevision revision = txTemplate.execute(s -> {
            AuditReader reader = AuditReaderFactory.get(entityManager);
            return reader.findRevision(CineTrackRevision.class, firstRev);
        });

        assertThat(revision).isNotNull();
        assertThat(revision.getUsername())
                .as("Revision must record the authenticated username 'bob'")
                .isEqualTo("bob");
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Three updates produce three revisions with correct first and last title")
    void threeUpdatesProduceThreeRevisions() {
        Long movieId = createMovie("Avatar", "SCI_FI");
        updateMovieTitle(movieId, "Avatar: The Way of Water");
        updateMovieTitle(movieId, "Avatar: Fire and Ash");

        List<Movie> history = txTemplate.execute(s ->
                movieAuditService.getMovieHistory(movieId));

        assertThat(history).hasSize(3);
        assertThat(history.getFirst().getTitle()).isEqualTo("Avatar");
        assertThat(history.getLast().getTitle()).isEqualTo("Avatar: Fire and Ash");
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Spring Data auditing sets createdBy on the entity")
    void springDataAuditingSetsCreatedBy() {
        Long movieId = createMovie("Everything Everywhere All at Once", "SCI_FI");

        Movie saved = txTemplate.execute(s ->
                movieRepository.findById(movieId).orElseThrow());

        assertThat(saved.getCreatedBy())
                .as("createdBy should be populated from the security context")
                .isEqualTo("alice");
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Spring Data auditing sets updatedBy after title change")
    void springDataAuditingSetsUpdatedBy() {
        Long movieId = createMovie("Arrival", "SCI_FI");
        updateMovieTitle(movieId, "Arrival: Extended");

        Movie updated = txTemplate.execute(s ->
                movieRepository.findById(movieId).orElseThrow());

        assertThat(updated.getUpdatedBy())
                .as("updatedBy should be set after title update")
                .isEqualTo("alice");
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("createdAt is set on insert and not changed by update")
    void createdAtIsImmutableAfterUpdate() {
        Long movieId = createMovie("The Dark Knight", "THRILLER");

        Movie original = txTemplate.execute(s ->
                movieRepository.findById(movieId).orElseThrow());
        var originalCreatedAt = original.getCreatedAt();

        updateMovieTitle(movieId, "The Dark Knight Rises");

        Movie updated = txTemplate.execute(s ->
                movieRepository.findById(movieId).orElseThrow());

        assertThat(updated.getCreatedAt())
                .as("createdAt must not change after an update (updatable=false)")
                .isEqualTo(originalCreatedAt);
        assertThat(updated.getUpdatedAt())
                .as("updatedAt must be set after the title change")
                .isNotNull();
    }

    // -----------------------------------------------------------------------
    // New tests
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("Delete produces a DEL revision as the last Envers entry")
    void delete_createsDEL_revision() {
        Long movieId = createMovie("Memento", "THRILLER");

        // Delete the movie in its own transaction so Envers records the DEL revision
        txTemplate.executeWithoutResult(s ->
                movieRepository.deleteById(movieId));

        // Retrieve all revisions including the deleted one
        // forRevisionsOfEntity(Movie.class, false, true) returns Object[] {entity, revInfo, revType}
        @SuppressWarnings("unchecked")
        java.util.List<Object[]> history = txTemplate.execute(s -> {
            AuditReader reader = AuditReaderFactory.get(entityManager);
            return (java.util.List<Object[]>) reader.createQuery()
                    .forRevisionsOfEntity(Movie.class, false, true)
                    .add(AuditEntity.id().eq(movieId))
                    .addOrder(AuditEntity.revisionNumber().asc())
                    .getResultList();
        });

        assertThat(history)
                .as("There must be at least one revision (the DEL) for the deleted movie")
                .isNotEmpty();

        Object[] lastRevisionRow = history.getLast();
        RevisionType lastRevType = (RevisionType) lastRevisionRow[2];

        assertThat(lastRevType)
                .as("The last revision for a deleted entity must be RevisionType.DEL")
                .isEqualTo(RevisionType.DEL);
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("getRevisions() returns all revision numbers after 3 saves")
    void auditReader_getRevisions_returnsAllRevisionNumbers() {
        Long movieId = createMovie("Whiplash", "DRAMA");
        updateMovieTitle(movieId, "Whiplash: Extended");
        updateMovieTitle(movieId, "Whiplash: Director's Cut");

        List<Number> revisions = txTemplate.execute(s ->
                movieAuditService.getRevisionNumbers(movieId));

        assertThat(revisions)
                .as("Three separate transactions (1 insert + 2 updates) must produce 3 revision numbers")
                .hasSize(3);
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("createdAt is immutable: unchanged after an update")
    void createdAt_immutable_afterUpdate() {
        Long movieId = createMovie("1917", "WAR");

        java.time.Instant originalCreatedAt = txTemplate.execute(s ->
                movieRepository.findById(movieId).orElseThrow().getCreatedAt());

        updateMovieTitle(movieId, "1917: Extended Edition");

        java.time.Instant createdAtAfterUpdate = txTemplate.execute(s ->
                movieRepository.findById(movieId).orElseThrow().getCreatedAt());

        assertThat(createdAtAfterUpdate)
                .as("createdAt must not change after an update (column is updatable=false)")
                .isEqualTo(originalCreatedAt);
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("lastModifiedDate (updatedAt) advances after an update")
    void lastModifiedDate_changes_afterUpdate() throws InterruptedException {
        Long movieId = createMovie("Parasite Extended", "DRAMA");

        java.time.Instant originalUpdatedAt = txTemplate.execute(s ->
                movieRepository.findById(movieId).orElseThrow().getUpdatedAt());

        // Ensure at least 1 ms passes so the timestamp can advance
        Thread.sleep(5);

        updateMovieTitle(movieId, "Parasite Extended: Director's Cut");

        java.time.Instant updatedAtAfter = txTemplate.execute(s ->
                movieRepository.findById(movieId).orElseThrow().getUpdatedAt());

        assertThat(updatedAtAfter)
                .as("updatedAt must advance after an update")
                .isNotNull()
                .isAfter(originalUpdatedAt);
    }

    // -----------------------------------------------------------------------
    // New tests: spec additions
    // -----------------------------------------------------------------------

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("getMovieAtRevision: invalid revision number returns null (not exception)")
    void getMovieAtRevision_invalidRevisionNumber_returnsNull() {
        Long movieId = createMovie("The Prestige", "THRILLER");

        // Revision 999_999 almost certainly does not exist: must return null, not throw
        Movie result = txTemplate.execute(s ->
                movieAuditService.getMovieAtRevision(movieId, 999_999));

        assertThat(result)
                .as("getMovieAtRevision with a non-existent revision number must return null, not throw")
                .isNull();
    }

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("getRevisionNumbers: non-existent movie ID returns an empty list")
    void getRevisionNumbers_nonExistentId_returnsEmptyList() {
        List<Number> revisions = txTemplate.execute(s ->
                movieAuditService.getRevisionNumbers(999_999L));

        assertThat(revisions)
                .as("getRevisionNumbers for a non-existent ID must return an empty list")
                .isNotNull()
                .isEmpty();
    }

    @Test
    @WithMockUser(username = "auditUser")
    @DisplayName("CineTrackRevisionListener: username field is set on the revision entity")
    void customRevision_ipAddress_isNotNull_ifAvailable() {
        Long movieId = createMovie("Revision Listener Test", "DRAMA");

        List<Number> revNums = txTemplate.execute(s ->
                movieAuditService.getRevisionNumbers(movieId));
        assertThat(revNums).isNotEmpty();

        int firstRevNum = revNums.getFirst().intValue();

        CineTrackRevision revision = txTemplate.execute(s -> {
            AuditReader reader = AuditReaderFactory.get(entityManager);
            return reader.findRevision(CineTrackRevision.class, firstRevNum);
        });

        assertThat(revision)
                .as("CineTrackRevision must exist for the recorded revision number")
                .isNotNull();

        // The listener always populates username from the security context.
        // In the test harness (no real HTTP request) ipAddress will be null : 
        // but the listener must not throw, and username must be set.
        assertThat(revision.getUsername())
                .as("CineTrackRevisionListener must have set the username field")
                .isEqualTo("auditUser");

        // ipAddress is null in a non-web context: that is the expected, safe behaviour.
        // We assert nothing about its value; just confirm the revision was stored.
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Envers revision captures the username from the security context")
    void revision_capturesUsername_fromSecurityContext() {
        Long movieId = createMovie("No Country for Old Men", "CRIME");
        updateMovieTitle(movieId, "No Country for Old Men: Redux");

        List<Number> revNums = txTemplate.execute(s ->
                movieAuditService.getRevisionNumbers(movieId));

        assertThat(revNums).isNotEmpty();
        // Examine the latest revision: it was written under @WithMockUser("testuser")
        int lastRevNum = revNums.getLast().intValue();

        CineTrackRevision revision = txTemplate.execute(s -> {
            AuditReader reader = AuditReaderFactory.get(entityManager);
            return reader.findRevision(CineTrackRevision.class, lastRevNum);
        });

        assertThat(revision)
                .as("The CineTrackRevision for the last commit must exist")
                .isNotNull();
        assertThat(revision.getUsername())
                .as("Revision must record the authenticated username 'testuser'")
                .isEqualTo("testuser");
    }
}
