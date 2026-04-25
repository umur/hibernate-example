package com.cinetrack.movie;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.series.Series;
import com.cinetrack.series.SeriesRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Hibernate 7 type-system mappings in Chapter 4.
 *
 * <p>Extends {@link AbstractIntegrationTest} which supplies a real PostgreSQL 16
 * container via Testcontainers and configures the slice with {@code @DataJpaTest}.
 * {@link JdbcTemplate} is injected to verify raw column values independently of
 * the Hibernate type pipeline — this is the only reliable way to confirm that the
 * database actually stores what we expect (e.g., enum name vs. ordinal).
 */
@Import(MovieQueryService.class)
@DisplayName("Hibernate 7 type-system mapping — extended coverage")
class HibernateTypeMappingTest extends AbstractIntegrationTest {

    @Autowired private MovieRepository    movieRepository;
    @Autowired private SeriesRepository   seriesRepository;
    @Autowired private TestEntityManager  entityManager;
    @Autowired private JdbcTemplate       jdbcTemplate;
    @Autowired private MovieQueryService  movieQueryService;

    // ── Scenario 1: JSONB null metadata ──────────────────────────────────────

    @Test
    @DisplayName("Saving a Movie with null metadata reloads as null — not an empty map")
    void jsonbNullMetadata_roundTrips_asNull() {
        Movie movie = new Movie("Null Metadata Film", Genre.DRAMA, 2020, new BigDecimal("6.5"));
        movie.setMetadata(null);
        movieRepository.save(movie);
        entityManager.flush();
        entityManager.clear();

        Movie loaded = movieRepository.findById(movie.getId()).orElseThrow();
        assertThat(loaded.getMetadata()).isNull();
    }

    // ── Scenario 2: Series castNames (List<String>) via JSONB ────────────────

    @Test
    @DisplayName("Series castNames List<String> survives a JSONB round-trip through PostgreSQL")
    void series_castNames_jsonbRoundTrip() {
        Series series = new Series(
                "Breaking Bad",
                List.of("Bryan Cranston", "Aaron Paul", "Anna Gunn"),
                5
        );
        seriesRepository.save(series);
        entityManager.flush();
        entityManager.clear();

        Series loaded = seriesRepository.findById(series.getId()).orElseThrow();
        assertThat(loaded.getCastNames())
                .isNotNull()
                .containsExactly("Bryan Cranston", "Aaron Paul", "Anna Gunn");
    }

    @Test
    @DisplayName("Series with an empty castNames list round-trips correctly")
    void series_emptyCastNames_roundTrips() {
        Series series = new Series("Documentary Special", List.of(), 1);
        seriesRepository.save(series);
        entityManager.flush();
        entityManager.clear();

        Series loaded = seriesRepository.findById(series.getId()).orElseThrow();
        assertThat(loaded.getCastNames()).isNotNull().isEmpty();
    }

    // ── Scenario 3: two saves produce different UUIDs ─────────────────────────

    @Test
    @DisplayName("Two separate save() calls produce distinct non-null UUIDs")
    void twoSaves_produceDifferentUuids() {
        Movie first  = new Movie("Movie Alpha", Genre.ACTION,  2021, new BigDecimal("7.0"));
        Movie second = new Movie("Movie Beta",  Genre.COMEDY,  2022, new BigDecimal("6.0"));

        movieRepository.save(first);
        movieRepository.save(second);
        entityManager.flush();

        assertThat(first.getId()).isNotNull();
        assertThat(second.getId()).isNotNull();
        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    // ── Scenario 4: Genre stored as VARCHAR string, not ordinal ──────────────

    @Test
    @DisplayName("Genre is stored as its name string (e.g. 'ACTION'), not as its ordinal (e.g. '0')")
    void genre_storedAsVarcharString_notOrdinal() {
        Movie movie = new Movie("Raw Genre Check", Genre.ACTION, 2019, new BigDecimal("8.0"));
        movieRepository.save(movie);
        entityManager.flush();

        UUID id = movie.getId();
        String rawGenre = jdbcTemplate.queryForObject(
                "SELECT genre FROM movies WHERE id = ?::uuid",
                String.class,
                id.toString()
        );

        assertThat(rawGenre).isEqualTo("ACTION");
        // Ordinal of ACTION is 0; verifying it is NOT "0" makes the intent explicit.
        assertThat(rawGenre).isNotEqualTo("0");
    }

    @Test
    @DisplayName("Genre SCIENCE_FICTION is stored as 'SCIENCE_FICTION', not as its ordinal")
    void genre_scienceFiction_storedAsString() {
        Movie movie = new Movie("Sci-Fi Film", Genre.SCIENCE_FICTION, 2023, new BigDecimal("7.5"));
        movieRepository.save(movie);
        entityManager.flush();

        String rawGenre = jdbcTemplate.queryForObject(
                "SELECT genre FROM movies WHERE id = ?::uuid",
                String.class,
                movie.getId().toString()
        );

        assertThat(rawGenre).isEqualTo("SCIENCE_FICTION");
    }

    // ── Scenario 5: array_contains via MovieQueryService ─────────────────────

    @Test
    @DisplayName("findMoviesByStreamingPlatform() returns only movies available on the given platform")
    void findMoviesByStreamingPlatform_returnsFilteredResults() {
        Movie onNetflix = new Movie("Netflix Only", Genre.DRAMA, 2018, new BigDecimal("7.0"));
        onNetflix.setStreamingPlatforms(new String[]{"Netflix", "Amazon Prime"});

        Movie onHbo = new Movie("HBO Only", Genre.THRILLER, 2019, new BigDecimal("8.0"));
        onHbo.setStreamingPlatforms(new String[]{"HBO Max"});

        Movie noStreaming = new Movie("No Streaming", Genre.ACTION, 2020, new BigDecimal("6.5"));
        noStreaming.setStreamingPlatforms(new String[]{});

        movieRepository.save(onNetflix);
        movieRepository.save(onHbo);
        movieRepository.save(noStreaming);
        entityManager.flush();
        entityManager.clear();

        List<Movie> netflixMovies = movieQueryService.findMoviesByStreamingPlatform("Netflix");

        assertThat(netflixMovies)
                .extracting(Movie::getTitle)
                .containsExactlyInAnyOrder("Netflix Only");
    }

    @Test
    @DisplayName("findByGenreAndMinRating() returns movies matching genre and minimum rating")
    void findByGenreAndMinRating_returnsCorrectMovies() {
        Movie highRated  = new Movie("High Rated Action", Genre.ACTION, 2015, new BigDecimal("8.5"));
        Movie lowRated   = new Movie("Low Rated Action",  Genre.ACTION, 2016, new BigDecimal("5.0"));
        Movie wrongGenre = new Movie("High Drama",        Genre.DRAMA,  2017, new BigDecimal("9.0"));

        movieRepository.save(highRated);
        movieRepository.save(lowRated);
        movieRepository.save(wrongGenre);
        entityManager.flush();
        entityManager.clear();

        List<Movie> results = movieQueryService.findByGenreAndMinRating(
                Genre.ACTION, new BigDecimal("7.0"));

        assertThat(results)
                .extracting(Movie::getTitle)
                .containsExactlyInAnyOrder("High Rated Action")
                .doesNotContain("Low Rated Action", "High Drama");
    }

    // ── Scenario 6: JSONB metadata with nested structure ─────────────────────

    @Test
    @DisplayName("JSONB metadata with nested map survives a round-trip without data loss")
    void jsonbMetadata_nestedStructure_roundTrips() {
        Movie movie = new Movie("Nested Meta Film", Genre.THRILLER, 2021, new BigDecimal("7.8"));
        movie.setMetadata(java.util.Map.of(
                "imdbId", "tt9999999",
                "awards", java.util.Map.of("oscars", 2, "baftas", 1)
        ));
        movieRepository.save(movie);
        entityManager.flush();
        entityManager.clear();

        Movie loaded = movieRepository.findById(movie.getId()).orElseThrow();
        assertThat(loaded.getMetadata()).containsKey("imdbId");
        assertThat(loaded.getMetadata()).containsKey("awards");
        assertThat(loaded.getMetadata().get("awards")).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> awards =
                (java.util.Map<String, Object>) loaded.getMetadata().get("awards");
        assertThat(((Number) awards.get("oscars")).intValue()).isEqualTo(2);
    }
}
