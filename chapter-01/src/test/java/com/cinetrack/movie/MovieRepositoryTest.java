package com.cinetrack.movie;

import com.cinetrack.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MovieRepository}.
 *
 * <p>Uses {@code @SpringBootTest} with the {@code test} profile which sets
 * {@code ddl-auto: create-drop} and disables Flyway so the JPA schema export
 * is used. Each test method is {@code @Transactional} so that saves and entity
 * manager operations share a single session and roll back after each test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MovieRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private MovieRepository movieRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("save() assigns a UUID to a new Movie without hitting a DB sequence")
    void save_assignsUuid() {
        Movie movie = Movie.builder()
                .title("Interstellar")
                .genre(Genre.SCI_FI)
                .releaseYear(2014)
                .rating(new BigDecimal("9.3"))
                .build();

        Movie saved = movieRepository.save(movie);

        // Hibernate 7's @UuidGenerator assigns the UUID before the INSERT,
        // so the entity already has its identity after persist().
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isInstanceOf(UUID.class);
    }

    @Test
    @DisplayName("JSONB metadata round-trips through PostgreSQL without data loss")
    void save_metadataJsonRoundTrips() {
        Map<String, Object> metadata = Map.of(
                "director", "Christopher Nolan",
                "budget_usd", 165_000_000,
                "awards", Map.of("oscars_won", 5, "nominated", true)
        );

        Movie movie = Movie.builder()
                .title("Interstellar")
                .genre(Genre.SCI_FI)
                .releaseYear(2014)
                .rating(new BigDecimal("9.3"))
                .metadata(metadata)
                .build();

        UUID savedId = movieRepository.save(movie).getId();

        // Force a flush and clear so the next find() hits the database.
        entityManager.flush();
        entityManager.clear();

        Movie reloaded = movieRepository.findById(savedId).orElseThrow();

        assertThat(reloaded.getMetadata())
                .containsEntry("director", "Christopher Nolan")
                .containsKey("awards");

        // Nested map is deserialised by Hibernate's JSON type handler.
        @SuppressWarnings("unchecked")
        Map<String, Object> awards = (Map<String, Object>) reloaded.getMetadata().get("awards");
        assertThat(awards).containsEntry("oscars_won", 5);
    }

    @Test
    @DisplayName("TEXT[] streaming platforms round-trip through PostgreSQL")
    void save_streamingPlatformsArrayRoundTrips() {
        String[] platforms = {"Netflix", "Apple TV+", "Amazon Prime"};

        Movie movie = Movie.builder()
                .title("Inception")
                .genre(Genre.SCI_FI)
                .releaseYear(2010)
                .rating(new BigDecimal("8.8"))
                .streamingPlatforms(platforms)
                .build();

        UUID savedId = movieRepository.save(movie).getId();

        entityManager.flush();
        entityManager.clear();

        Movie reloaded = movieRepository.findById(savedId).orElseThrow();

        assertThat(reloaded.getStreamingPlatforms())
                .containsExactly("Netflix", "Apple TV+", "Amazon Prime");
    }

    @Test
    @DisplayName("findById() returns empty Optional when UUID does not exist")
    void findById_returnsEmptyForUnknownId() {
        assertThat(movieRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("save() then findById() after L1-cache eviction returns the persisted entity")
    void save_thenFindById_returnsEntity() {
        Movie movie = Movie.builder()
                .title("Blade Runner")
                .genre(Genre.SCI_FI)
                .releaseYear(1982)
                .build();
        Movie saved = movieRepository.save(movie);
        entityManager.flush();
        entityManager.clear(); // evict L1 cache

        assertThat(movieRepository.findById(saved.getId()))
                .isPresent()
                .get()
                .extracting(Movie::getTitle)
                .isEqualTo("Blade Runner");
    }

    @Test
    @DisplayName("JSONB metadata with nested map round-trips correctly")
    void jsonbMetadata_withNestedMap_roundTrips() {
        Map<String, Object> meta = Map.of(
                "director", "Kubrick",
                "awards", Map.of("oscar", true, "count", 3)
        );
        Movie movie = Movie.builder()
                .title("2001: A Space Odyssey")
                .genre(Genre.SCI_FI)
                .releaseYear(1968)
                .metadata(meta)
                .build();
        UUID savedId = movieRepository.save(movie).getId();
        entityManager.flush();
        entityManager.clear();

        Movie loaded = movieRepository.findById(savedId).orElseThrow();
        assertThat(loaded.getMetadata()).containsKey("director");
        assertThat(loaded.getMetadata().get("awards")).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("TEXT[] streamingPlatforms empty array round-trips correctly")
    void textArray_empty_roundTrips() {
        Movie movie = Movie.builder()
                .title("Empty Platforms Test")
                .genre(Genre.DRAMA)
                .releaseYear(2000)
                .streamingPlatforms(new String[]{})
                .build();
        UUID savedId = movieRepository.save(movie).getId();
        entityManager.flush();
        entityManager.clear();

        Movie loaded = movieRepository.findById(savedId).orElseThrow();
        assertThat(loaded.getStreamingPlatforms()).isEmpty();
    }

    @Test
    @DisplayName("TEXT[] streamingPlatforms single-element array round-trips correctly")
    void textArray_singleElement_roundTrips() {
        Movie movie = Movie.builder()
                .title("Single Platform Test")
                .genre(Genre.DRAMA)
                .releaseYear(2001)
                .streamingPlatforms(new String[]{"Netflix"})
                .build();
        UUID savedId = movieRepository.save(movie).getId();
        entityManager.flush();
        entityManager.clear();

        Movie loaded = movieRepository.findById(savedId).orElseThrow();
        assertThat(loaded.getStreamingPlatforms()).containsExactly("Netflix");
    }

    @Test
    @DisplayName("findAll() returns all persisted movies")
    void multipleMovies_findAll_returnsAll() {
        for (int i = 0; i < 5; i++) {
            movieRepository.save(Movie.builder()
                    .title("Bulk Movie " + i)
                    .genre(Genre.ACTION)
                    .releaseYear(2000 + i)
                    .build());
        }
        entityManager.flush();

        assertThat(movieRepository.findAll()).hasSizeGreaterThanOrEqualTo(5);
    }

    // ── Chapter 1 lifecycle callback coverage ────────────────────────────────

    @Test
    @DisplayName("@PrePersist on AppUser sets createdAt automatically")
    void appUser_prePersist_setsCreatedAt() {
        com.cinetrack.user.AppUser user = com.cinetrack.user.AppUser.builder()
                .username("lifecycle_user_" + System.nanoTime())
                .email("lifecycle_" + System.nanoTime() + "@example.com")
                .passwordHash("$2a$12$placeholder")
                .build();

        // createdAt must be null before persist
        assertThat(user.getCreatedAt()).isNull();

        entityManager.persist(user);
        entityManager.flush();

        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("@PrePersist on Review sets createdAt automatically")
    void review_prePersist_setsTimestamp() {
        // Persist prerequisite entities first
        com.cinetrack.user.AppUser user = com.cinetrack.user.AppUser.builder()
                .username("reviewer_" + System.nanoTime())
                .email("rev_" + System.nanoTime() + "@example.com")
                .passwordHash("$2a$12$placeholder")
                .build();
        entityManager.persist(user);

        Movie movie = Movie.builder()
                .title("Review Target")
                .genre(Genre.DRAMA)
                .releaseYear(2020)
                .build();
        entityManager.persist(movie);
        entityManager.flush();

        com.cinetrack.review.Review review = com.cinetrack.review.Review.builder()
                .movie(movie)
                .reviewer(user)
                .content("Great film")
                .rating(8)
                .build();

        assertThat(review.getCreatedAt()).isNull();

        entityManager.persist(review);
        entityManager.flush();

        assertThat(review.getCreatedAt()).isNotNull();
    }
}
