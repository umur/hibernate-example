package com.cinetrack.movie;

/**
 * MPAA-style content rating for movies.
 * Ordinal ordering is intentional: G < PG < PG_13 < R < NC_17.
 */
public enum ContentRating {
    G,
    PG,
    PG_13,
    R,
    NC_17
}
