package com.cinetrack.movie;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Movie}.
 *
 * <p>Spring Boot's auto-configuration detects this interface and wires it
 * into the application context via {@code JpaRepositoriesAutoConfiguration}.
 * At runtime the proxy is backed by {@code SimpleJpaRepository}, which
 * delegates to the shared {@code EntityManager} (and therefore the shared
 * Hibernate {@code SessionFactory}).
 *
 * <p>Chapter 1 keeps this minimal: the focus is on understanding what
 * Spring Boot sets up for you, not on query derivation or custom JPQL.
 */
@Repository
public interface MovieRepository extends JpaRepository<Movie, UUID> {
}
