package com.cinetrack.movie;

import org.springframework.beans.factory.annotation.Value;

/**
 * <strong>Open interface projection</strong> using a SpEL {@code @Value} expression.
 *
 * <h2>What makes it "open"?</h2>
 * <p>The {@code getGenreName()} getter is annotated with {@code @Value} and
 * contains a Spring Expression Language expression. Spring Data must load the
 * full entity (or at minimum the referenced field) into memory so that the SpEL
 * can be evaluated in the Java process. This means open projections do <em>not</em>
 * reduce the SQL {@code SELECT} list the way closed projections do.</p>
 *
 * <h2>SpEL target variable</h2>
 * <p>{@code target} refers to the underlying entity instance. Calling
 * {@code target.genre.name()} invokes the {@link Genre#name()} method at
 * runtime and returns the enum constant's declared name as a {@code String}.</p>
 *
 * <h2>When to prefer closed projections</h2>
 * <p>Use open projections only when the derived value cannot be expressed as a
 * plain JPQL or SQL expression. For simple column aliasing or format conversions,
 * prefer a DTO record with a constructor expression instead: it is explicit,
 * testable without proxies, and works equally well with native queries.</p>
 */
public interface MovieWithReviewerCount {

    /** Plain mapping: part of the closed subset. */
    String getTitle();

    /**
     * Derived via SpEL: calls {@link Genre#name()} on the loaded {@code genre}
     * field to convert the enum to its declared string constant.
     *
     * <p>Example: for a movie with genre {@code SCIENCE_FICTION} this returns
     * {@code "SCIENCE_FICTION"}.</p>
     */
    @Value("#{target.genre.name()}")
    String getGenreName();
}
