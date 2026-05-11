package com.cinetrack.movie;

/**
 * <strong>Closed interface projection</strong>: the simplest form of projection in
 * Spring Data JPA.
 *
 * <h2>How it works</h2>
 * <p>Spring Data generates a JDK proxy at runtime that implements this interface.
 * Each getter maps to an entity attribute by convention: {@code getTitle()} reads
 * {@code Movie.title}, {@code getGenre()} reads {@code Movie.genre}.</p>
 *
 * <h2>Why "closed"?</h2>
 * <p>A projection is <em>closed</em> when every getter maps directly to an entity
 * field with no computation. Spring Data can then optimise the SQL {@code SELECT}
 * list to include only the projected columns, reducing data transfer from the
 * database.</p>
 *
 * <h2>Contrast with open projections</h2>
 * <p>See {@link MovieWithReviewerCount} for an <em>open</em> projection that uses
 * a SpEL {@code @Value} expression: open projections must load the full entity
 * because the SpEL is evaluated in Java, not pushed into SQL.</p>
 */
public interface MovieTitleProjection {

    /** Maps to {@code Movie.title}. */
    String getTitle();

    /**
     * Maps to {@code Movie.genre}.
     *
     * <p>Spring Data calls {@code Genre.toString()} when serialising the result,
     * so callers receive the enum name (e.g. {@code "ACTION"}) as a String.</p>
     */
    String getGenre();
}
