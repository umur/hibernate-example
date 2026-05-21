package com.cinetrack.filter;

import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service that demonstrates Hibernate's session-scoped {@code @Filter} API.
 *
 * <p>The {@code contentRatingFilter} is defined on {@link Movie} but is
 * <em>disabled by default</em>: Hibernate filters are opt-in per session.
 * Calling {@link Session#enableFilter(String)} before any query in the same
 * transaction activates the filter SQL fragment for the lifetime of that
 * session.</p>
 *
 * <p>This two-path approach lets the application serve different audiences
 * (child-safe vs adult-verified) without duplicating repository methods or
 * leaking business rules into JPQL.</p>
 */
@Service
@RequiredArgsConstructor
public class ContentRatingService {

    @PersistenceContext
    private final EntityManager entityManager;

    private final MovieRepository movieRepository;

    /**
     * Returns movies visible to the current user.
     *
     * <ul>
     *   <li>Unverified users see only G / PG / PG-13 content.</li>
     *   <li>Adult-verified users see everything up to NC-17.</li>
     * </ul>
     *
     * @param isAdultVerified whether the calling user has completed age verification
     * @return filtered (or unfiltered) movie list
     */
    @Transactional(readOnly = true)
    public List<Movie> findForUser(boolean isAdultVerified) {
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter("contentRatingFilter")
               .setParameter("maxRating", isAdultVerified ? "NC_17" : "PG_13");
        return movieRepository.findAll();
    }

    /**
     * Returns all non-deleted movies with no content-rating filter applied.
     * Useful for admin dashboards.
     */
    @Transactional(readOnly = true)
    public List<Movie> findAllUnfiltered() {
        return movieRepository.findAll();
    }
}
