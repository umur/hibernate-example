package com.cinetrack.series;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Series}.
 */
@Repository
public interface SeriesRepository extends JpaRepository<Series, UUID> {

    java.util.List<Series> findByTitle(String title);
}
