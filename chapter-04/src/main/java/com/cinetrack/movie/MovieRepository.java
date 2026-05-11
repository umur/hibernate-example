package com.cinetrack.movie;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Movie}.
 *
 * <p>Spring Data translates these interface method signatures into SQM (Semantic Query
 * Model) trees at application startup. At runtime each query traverses the SQM pipeline:
 * <ol>
 *   <li>SQM tree is built from the HQL string or derived method name.</li>
 *   <li>SQM is validated against the Hibernate metamodel.</li>
 *   <li>SQM is translated to a SQL AST.</li>
 *   <li>The SQL AST is rendered to a dialect-specific SQL string and cached.</li>
 * </ol>
 */
@Repository
public interface MovieRepository extends JpaRepository<Movie, UUID> {

    /**
     * Derived query: Spring Data infers the SQM from the method name.
     * Equivalent HQL: {@code SELECT m FROM Movie m WHERE m.genre = :genre}
     */
    List<Movie> findByGenre(Genre genre);

    /**
     * Named-parameter HQL query processed by the SQM pipeline.
     * Note the use of {@code :genre} and {@code :minRating}: SQM validates that
     * these parameter names exist on the {@code Movie} metamodel at startup.
     */
    @Query("SELECT m FROM Movie m WHERE m.genre = :genre AND m.rating >= :minRating ORDER BY m.rating DESC")
    List<Movie> findByGenreAndMinRating(@Param("genre") Genre genre,
                                        @Param("minRating") BigDecimal minRating);
}
