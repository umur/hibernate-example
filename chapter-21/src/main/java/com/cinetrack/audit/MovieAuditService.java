package com.cinetrack.audit;

import com.cinetrack.movie.Movie;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.exception.RevisionDoesNotExistException;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service exposing Envers audit history queries for {@link Movie}.
 *
 * <p>Envers stores one row per (entity, revision) pair in {@code movies_aud}.
 * The {@link AuditReader} API lets us reconstruct the state of any entity at
 * any point in its history without touching the live {@code movies} table.
 *
 * <p>Both methods run inside a read-only transaction so the
 * {@link EntityManager} is available for {@link AuditReaderFactory#get}.
 */
@Service
@Transactional(readOnly = true)
public class MovieAuditService {

    private final EntityManager entityManager;

    public MovieAuditService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Returns the full revision history for a movie in ascending revision order.
     *
     * @param movieId the primary key of the movie
     * @return list of {@link Movie} snapshots, one per recorded revision
     */
    @SuppressWarnings("unchecked")
    public List<Movie> getMovieHistory(Long movieId) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        return (List<Movie>) reader.createQuery()
                .forRevisionsOfEntity(Movie.class, true, true)
                .add(AuditEntity.id().eq(movieId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();
    }

    /**
     * Returns the state of a movie as it existed at a specific revision number.
     *
     * @param movieId        the primary key of the movie
     * @param revisionNumber the Envers revision number (from {@code revinfo.rev})
     * @return a {@link Movie} snapshot at that revision, or {@code null} if
     *         the entity did not exist at that revision
     */
    public Movie getMovieAtRevision(Long movieId, int revisionNumber) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        try {
            // Verify the revision number actually corresponds to a recorded
            // revision; otherwise Envers' find() would silently return the
            // entity state at the latest <= revision, which is misleading.
            reader.getRevisionDate(revisionNumber);
        } catch (RevisionDoesNotExistException e) {
            return null;
        }
        return reader.find(Movie.class, movieId, revisionNumber);
    }

    /**
     * Returns the revision numbers at which a given movie was modified.
     *
     * @param movieId the primary key of the movie
     * @return sorted list of revision numbers
     */
    public List<Number> getRevisionNumbers(Long movieId) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        return reader.getRevisions(Movie.class, movieId);
    }
}
