package com.cinetrack.movie;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    /**
     * Bulk UPDATE that bypasses Hibernate's dirty-checking and the @Version
     * mechanism entirely — {@code view_count} is also annotated with
     * {@code @OptimisticLock(excluded = true)} on the entity, so even when
     * Hibernate does process the entity, this field never triggers a version
     * bump.  This query is the fast path for high-frequency increments.
     */
    @Modifying
    @Query("UPDATE Movie m SET m.viewCount = m.viewCount + 1 WHERE m.id = :id")
    int incrementViewCount(@Param("id") Long id);
}
