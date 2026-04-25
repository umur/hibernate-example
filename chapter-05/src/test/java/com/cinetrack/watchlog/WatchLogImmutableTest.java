package com.cinetrack.watchlog;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.movie.Genre;
import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import com.cinetrack.user.AppUser;
import com.cinetrack.user.AppUserRepository;
import com.cinetrack.user.EmailAddress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the {@code @Immutable} {@link WatchLog} entity.
 *
 * <p>Verifies that mutations to a managed {@code WatchLog} instance are silently
 * discarded at flush time — Hibernate never generates an UPDATE for immutable entities.
 */
@DisplayName("WatchLog — @Immutable entity behaviour")
class WatchLogImmutableTest extends AbstractIntegrationTest {

    @Autowired private WatchLogRepository watchLogRepository;
    @Autowired private MovieRepository     movieRepository;
    @Autowired private AppUserRepository   userRepository;
    @Autowired private EntityManager   em;
    @Autowired private JdbcTemplate        jdbcTemplate;

    private AppUser savedUser(String name) {
        return userRepository.saveAndFlush(
                new AppUser(name, new EmailAddress(name + "@example.com"), "pw"));
    }

    private Movie savedMovie(String title) {
        return movieRepository.saveAndFlush(new Movie(title, Genre.DRAMA, 2020));
    }

    @Test
    @DisplayName("Mutating a managed WatchLog and flushing does not update the DB row")
    void mutationWithinTransactionIsDiscarded() {
        // GIVEN — persist a WatchLog
        AppUser user  = savedUser("jack");
        Movie   movie = savedMovie("Oldboy");
        Instant originalTime = Instant.parse("2024-01-01T10:00:00Z");

        WatchLog log = new WatchLog(user, movie, originalTime, 120);
        watchLogRepository.saveAndFlush(log);
        Long logId = log.getId();

        // WHEN — mutate the managed instance and flush
        // @Immutable means Hibernate will silently skip the UPDATE
        setDuration(log, 999);  // attempt to change duration via reflection
        watchLogRepository.flush();

        // THEN — the DB row still has the original duration
        Integer durationInDb = jdbcTemplate.queryForObject(
                "SELECT duration_minutes FROM watch_logs WHERE id = ?",
                Integer.class, logId);

        assertThat(durationInDb).isEqualTo(120);
    }

    @Test
    @DisplayName("WatchLog can be persisted and reloaded — basic round-trip works")
    void watchLogPersistsAndReloads() {
        AppUser user  = savedUser("kate");
        Movie   movie = savedMovie("Moonlight");
        Instant watchedAt = Instant.parse("2024-03-15T20:00:00Z");

        WatchLog log = new WatchLog(user, movie, watchedAt, 111);
        watchLogRepository.saveAndFlush(log);

        em.detach(log);

        WatchLog loaded = watchLogRepository.findById(log.getId()).orElseThrow();
        assertThat(loaded.getDurationMinutes()).isEqualTo(111);
        assertThat(loaded.getWatchedAt()).isEqualTo(watchedAt);
    }

    @Test
    @DisplayName("WatchLog DB row retains original duration_minutes after in-memory mutation and flush")
    void jdbcVerifiesOriginalValueIsPreserved() {
        AppUser user  = savedUser("leo");
        Movie   movie = savedMovie("Parasite");
        Instant watchedAt = Instant.now();

        WatchLog log = new WatchLog(user, movie, watchedAt, 132);
        watchLogRepository.saveAndFlush(log);
        Long logId = log.getId();

        // Mutate in-memory and flush — @Immutable suppresses the UPDATE
        setDuration(log, 1);
        watchLogRepository.flush();

        // Verify via raw JDBC — the persisted value must still be 132
        Integer dbValue = jdbcTemplate.queryForObject(
                "SELECT duration_minutes FROM watch_logs WHERE id = ?",
                Integer.class, logId);

        assertThat(dbValue).isEqualTo(132);
        // The in-memory Java object holds 1, but the DB was never touched
        assertThat(log.getDurationMinutes()).isEqualTo(1);
    }

    private static void setDuration(WatchLog log, int minutes) {
        try {
            var field = WatchLog.class.getDeclaredField("durationMinutes");
            field.setAccessible(true);
            field.setInt(log, minutes);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
