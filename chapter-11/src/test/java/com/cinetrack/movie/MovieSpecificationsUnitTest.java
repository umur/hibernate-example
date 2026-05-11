package com.cinetrack.movie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link MovieSpecifications} factory methods.
 *
 * <p>No Spring context, no database. Each test verifies only that the factory
 * returns a non-null {@link Specification} regardless of the input value: the
 * null-safety contract documented on the class.</p>
 */
class MovieSpecificationsUnitTest {

    // -------------------------------------------------------------------------
    // Null inputs: must return a non-null conjunction (no-op predicate)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("hasGenre(null) returns a non-null Specification")
    void hasGenre_null_isNotNull() {
        Specification<Movie> spec = MovieSpecifications.hasGenre(null);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("releasedAfter(null) returns a non-null Specification")
    void releasedAfter_null_isNotNull() {
        Specification<Movie> spec = MovieSpecifications.releasedAfter(null);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("ratingAtLeast(null) returns a non-null Specification")
    void ratingAtLeast_null_isNotNull() {
        Specification<Movie> spec = MovieSpecifications.ratingAtLeast(null);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("titleContains(null) returns a non-null Specification")
    void titleContains_null_isNotNull() {
        Specification<Movie> spec = MovieSpecifications.titleContains(null);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("titleContains(blank string) returns a non-null Specification")
    void titleContains_blank_isNotNull() {
        Specification<Movie> spec = MovieSpecifications.titleContains("   ");
        assertThat(spec).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Non-null inputs: factory must still return a non-null Specification
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("hasGenre(ACTION) returns a non-null Specification")
    void hasGenre_realValue_isNotNull() {
        Specification<Movie> spec = MovieSpecifications.hasGenre(Genre.ACTION);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("releasedAfter(2000) returns a non-null Specification")
    void releasedAfter_realValue_isNotNull() {
        Specification<Movie> spec = MovieSpecifications.releasedAfter(2000);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("ratingAtLeast(7.5) returns a non-null Specification")
    void ratingAtLeast_realValue_isNotNull() {
        Specification<Movie> spec = MovieSpecifications.ratingAtLeast(7.5);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("titleContains(\"dark\") returns a non-null Specification")
    void titleContains_realValue_isNotNull() {
        Specification<Movie> spec = MovieSpecifications.titleContains("dark");
        assertThat(spec).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Composition: chaining two non-null specs must not throw
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Composed spec (hasGenre AND ratingAtLeast) is non-null")
    void composed_twoSpecs_isNotNull() {
        Specification<Movie> spec = MovieSpecifications.hasGenre(Genre.DRAMA)
                .and(MovieSpecifications.ratingAtLeast(6.0));
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("Composed spec (null genre AND null rating) is non-null")
    void composed_twoNullSpecs_isNotNull() {
        Specification<Movie> spec = MovieSpecifications.hasGenre(null)
                .and(MovieSpecifications.ratingAtLeast(null));
        assertThat(spec).isNotNull();
    }
}
