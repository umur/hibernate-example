package com.cinetrack.watchlog;

import java.time.Instant;

/**
 * Immutable command record carrying the data needed to create a single
 * {@link WatchLog} row.
 *
 * <p>Used by {@link WatchLogBatchService#importStateless} so that callers do
 * not need to resolve entity references before calling the service: the
 * service handles the lookup internally, keeping the ID fetches inside the
 * StatelessSession for maximum throughput.
 *
 * @param userId          PK of the watching user (must already exist)
 * @param movieId         PK of the movie (must already exist)
 * @param watchedAt       timestamp of the viewing event
 * @param durationSeconds how many seconds the user watched
 */
public record CreateWatchLogCommand(
        Long userId,
        Long movieId,
        Instant watchedAt,
        int durationSeconds
) {}
