package com.cinetrack.movie;

/**
 * Enumeration of broad movie genres stored as a VARCHAR string column
 * via {@code @Enumerated(EnumType.STRING)}.
 *
 * <p>Storing the name rather than the ordinal means adding new values
 * never invalidates existing rows: a best practice with Hibernate.
 */
public enum Genre {
    ACTION,
    COMEDY,
    DRAMA,
    HORROR,
    SCIENCE_FICTION,
    THRILLER,
    DOCUMENTARY,
    ANIMATION,
    ROMANCE,
    FANTASY
}
