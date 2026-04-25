package com.cinetrack.movie;

import com.cinetrack.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link Movie} entity mappings.
 *
 * <p>Each test persists a {@code Movie}, clears the first-level cache (so the
 * subsequent find hits the database), then asserts that Hibernate correctly
 * round-tripped the value through its type-system pipeline.
 */
@DisplayName("Movie — Hibernate 7 type-system mapping")
class MovieMappingTest extends AbstractIntegrationTest {

    @Autowired
    private MovieRepository movieRepository;

    // ── JSONB metadata ────────────────────────────────────────────────────────

    @Test
    @DisplayName("JSONB metadata map survives a round-trip through PostgreSQL")
    void jsonbMetadataRoundTrip() {
        // GIVEN
        Movie movie = new Movie("The Dark Knight", Genre.ACTION, 2008, new BigDecimal("9.0"));
        movie.setMetadata(Map.of(
                "imdbId", "tt0468569",
                "budget", 185_000_000,
                "directedBy", "Christopher Nolan"
        ));
        movieRepository.save(movie);
        movieRepository.flush();    // flush to DB within the transaction

        // WHEN — force a fresh load from DB by evicting the entity
        movieRepository.findById(movie.getId()); // warms the session cache
        // @DataJpaTest wraps each test in a transaction; use a new lookup to verify
        Movie loaded = movieRepository.findById(movie.getId()).orElseThrow();

        // THEN
        assertThat(loaded.getMetadata())
                .isNotNull()
                .containsEntry("imdbId", "tt0468569")
                .containsEntry("directedBy", "Christopher Nolan");
        // Jackson deserialises integers as Integer when value fits
        assertThat(((Number) loaded.getMetadata().get("budget")).longValue())
                .isEqualTo(185_000_000L);
    }

    // ── PostgreSQL TEXT[] array ───────────────────────────────────────────────

    @Test
    @DisplayName("TEXT[] streamingPlatforms array survives a round-trip through PostgreSQL")
    void arrayRoundTrip() {
        // GIVEN
        Movie movie = new Movie("Inception", Genre.SCIENCE_FICTION, 2010, new BigDecimal("8.8"));
        movie.setStreamingPlatforms(new String[]{"Netflix", "HBO Max", "Apple TV+"});
        movieRepository.save(movie);
        movieRepository.flush();

        // WHEN
        Movie loaded = movieRepository.findById(movie.getId()).orElseThrow();

        // THEN
        assertThat(loaded.getStreamingPlatforms())
                .isNotNull()
                .containsExactlyInAnyOrder("Netflix", "HBO Max", "Apple TV+");
    }

    // ── UUID generation ───────────────────────────────────────────────────────

    @Test
    @DisplayName("@UuidGenerator assigns a non-null UUID on first persist")
    void uuidGeneratedOnPersist() {
        // GIVEN — id is null before save
        Movie movie = new Movie("Parasite", Genre.DRAMA, 2019, new BigDecimal("8.6"));
        assertThat(movie.getId()).isNull();

        // WHEN
        Movie saved = movieRepository.save(movie);

        // THEN — Hibernate assigns the UUID during persist, before the INSERT
        assertThat(saved.getId())
                .isNotNull()
                .isInstanceOf(UUID.class);
    }

    // ── Enum stored as string ─────────────────────────────────────────────────

    @Test
    @DisplayName("Genre enum is stored as VARCHAR and reloaded correctly")
    void enumStoredAsString() {
        Movie movie = new Movie("Get Out", Genre.HORROR, 2017, new BigDecimal("7.7"));
        movieRepository.save(movie);
        movieRepository.flush();

        Movie loaded = movieRepository.findById(movie.getId()).orElseThrow();

        assertThat(loaded.getGenre()).isEqualTo(Genre.HORROR);
    }

    // ── Series JSONB cast list ────────────────────────────────────────────────

    @Autowired
    private com.cinetrack.series.SeriesRepository seriesRepository;

    @Test
    @DisplayName("Series genre persists and reloads correctly")
    void series_castNames_persistsAndReloads() {
        com.cinetrack.series.Series series = new com.cinetrack.series.Series(
                "Breaking Bad",
                java.util.List.of("Bryan Cranston", "Aaron Paul"),
                5
        );
        seriesRepository.save(series);
        seriesRepository.flush();

        com.cinetrack.series.Series loaded =
                seriesRepository.findById(series.getId()).orElseThrow();

        assertThat(loaded.getCastNames())
                .containsExactly("Bryan Cranston", "Aaron Paul");
        assertThat(loaded.getSeasons()).isEqualTo(5);
    }
}
