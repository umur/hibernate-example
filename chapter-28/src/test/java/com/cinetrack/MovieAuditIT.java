package com.cinetrack;

import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MovieAuditIT extends AbstractIntegrationTest {

    @Autowired
    private MovieRepository movieRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate txTemplate;

    @BeforeEach
    void setUp() {
        // deleteAll() runs in its own transaction so envers writes its DEL
        // revisions to revinfo before the test body starts.
        txTemplate.executeWithoutResult(s -> movieRepository.deleteAll());
    }

    // Each mutation runs in its own committed transaction so Envers writes
    // the audit row before we query AuditReader. A single @Transactional test
    // method would leave revinfo empty until the method returns.

    @Test
    void auditHistory_recordsTwoRevisionsAfterCreateAndUpdate() {
        Long movieId = txTemplate.execute(s -> movieRepository.save(
                new Movie("Dune", 2021, "SCIFI", new BigDecimal("8.0"),
                        "A noble family becomes embroiled in a war for a desert planet.")
        ).getId());

        txTemplate.executeWithoutResult(s -> {
            Movie m = movieRepository.findById(movieId).orElseThrow();
            m.setTitle("Dune: Part One");
            m.setRating(new BigDecimal("8.1"));
            movieRepository.save(m);
        });

        List<Number> revisions = txTemplate.execute(s ->
                AuditReaderFactory.get(entityManager).getRevisions(Movie.class, movieId));

        assertThat(revisions).hasSize(2);
    }

    @Test
    void auditHistory_firstRevisionMatchesOriginalTitle() {
        Long movieId = txTemplate.execute(s -> movieRepository.save(
                new Movie("Arrival", 2016, "SCIFI", new BigDecimal("7.9"),
                        "A linguist works with the military to communicate with alien lifeforms.")
        ).getId());

        txTemplate.executeWithoutResult(s -> {
            Movie m = movieRepository.findById(movieId).orElseThrow();
            m.setTitle("Arrival (Director's Cut)");
            movieRepository.save(m);
        });

        txTemplate.executeWithoutResult(s -> {
            AuditReader auditReader = AuditReaderFactory.get(entityManager);
            List<Number> revisions = auditReader.getRevisions(Movie.class, movieId);

            assertThat(revisions).hasSizeGreaterThanOrEqualTo(2);

            Movie firstRevision = auditReader.find(Movie.class, movieId, revisions.get(0));
            assertThat(firstRevision.getTitle()).isEqualTo("Arrival");

            Movie secondRevision = auditReader.find(Movie.class, movieId, revisions.get(1));
            assertThat(secondRevision.getTitle()).isEqualTo("Arrival (Director's Cut)");
        });
    }

    @Test
    void threeUpdates_produceThreeRevisions() {
        Long movieId = txTemplate.execute(s -> movieRepository.save(
                new Movie("Trilogy Film", 2020, "ACTION", new BigDecimal("7.5"),
                        "First version of the overview.")
        ).getId());

        txTemplate.executeWithoutResult(s -> {
            Movie m = movieRepository.findById(movieId).orElseThrow();
            m.setTitle("Trilogy Film: Chapter Two");
            movieRepository.save(m);
        });

        txTemplate.executeWithoutResult(s -> {
            Movie m = movieRepository.findById(movieId).orElseThrow();
            m.setTitle("Trilogy Film: Chapter Three");
            m.setRating(new BigDecimal("8.0"));
            movieRepository.save(m);
        });

        List<Number> revisions = txTemplate.execute(s ->
                AuditReaderFactory.get(entityManager).getRevisions(Movie.class, movieId));

        assertThat(revisions).hasSize(3);
    }

    // -----------------------------------------------------------------------
    // New coverage: deleted entity retrievable at pre-delete revision
    // -----------------------------------------------------------------------

    @Test
    void audit_deletedEntity_canBeRetrievedByRevision() {
        // Revision 1: create the movie
        String originalTitle = "Ephemeral Film";
        Long movieId = txTemplate.execute(s -> movieRepository.save(
                new Movie(originalTitle, 2022, "THRILLER", new BigDecimal("7.3"),
                        "A film that will be deleted.")
        ).getId());

        // Capture the revision number right after creation
        Number createRevision = txTemplate.execute(s -> {
            List<Number> revs = AuditReaderFactory.get(entityManager)
                    .getRevisions(Movie.class, movieId);
            assertThat(revs).isNotEmpty();
            return revs.get(0);
        });

        // Revision 2: delete the movie
        txTemplate.executeWithoutResult(s ->
                movieRepository.deleteById(movieId));

        txTemplate.executeWithoutResult(s -> {
            AuditReader auditReader = AuditReaderFactory.get(entityManager);

            List<Number> allRevisions = auditReader.getRevisions(Movie.class, movieId);
            assertThat(allRevisions)
                    .as("Envers must record at least the create and delete revisions")
                    .hasSizeGreaterThanOrEqualTo(2);

            Movie stateAtCreate = auditReader.find(Movie.class, movieId, createRevision);
            assertThat(stateAtCreate)
                    .as("pre-delete revision must be retrievable from the audit table")
                    .isNotNull();
            assertThat(stateAtCreate.getTitle())
                    .as("pre-delete state must preserve the original title")
                    .isEqualTo(originalTitle);
        });
    }
}
