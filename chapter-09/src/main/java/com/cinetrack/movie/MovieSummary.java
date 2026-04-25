package com.cinetrack.movie;

/**
 * Interface projection for Movie. Spring Data JPA generates a proxy at
 * runtime that implements this interface and maps each getter to the
 * corresponding entity attribute. Only the declared columns are fetched
 * from the database — no SELECT * is issued.
 */
public interface MovieSummary {

    String getTitle();

    double getRating();
}
