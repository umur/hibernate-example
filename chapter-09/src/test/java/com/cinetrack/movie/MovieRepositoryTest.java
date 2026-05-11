package com.cinetrack.movie;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.review.Review;
import com.cinetrack.review.ReviewRepository;
import com.cinetrack.user.AppUser;
import com.cinetrack.user.AppUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the advanced repository capabilities introduced in Chapter 9:
 * <ul>
 *   <li>Interface projections return lightweight proxies, not full entities.</li>
 *   <li>Custom fragment findMoviesWithMinReviews filters by aggregated review count.</li>
 *   <li>Scrolling API pages through records without COUNT(*) overhead.</li>
 *   <li>Spring Data JPA auditing populates createdAt/updatedAt on save.</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Import({
        com.cinetrack.config.JpaAuditingConfig.class,
        com.cinetrack.config.RepositoryConfig.class
})
class MovieRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    // -------------------------------------------------------------------------
    // Interface Projection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Interface projection returns only title and rating: not the full entity")
    void interfaceProjectionReturnsOnlyDeclaredFields() {
        // Arrange
        movieRepository.saveAndFlush(new Movie("Amélie", Genre.ROMANCE, new BigDecimal("8.3")));
        movieRepository.saveAndFlush(new Movie("Mad Max", Genre.ACTION, new BigDecimal("7.9")));

        // Act
        List<MovieSummary> summaries = movieRepository.findByGenre(Genre.ROMANCE);

        // Assert
        assertThat(summaries).hasSize(1);
        MovieSummary s = summaries.getFirst();
        assertThat(s.getTitle()).isEqualTo("Amélie");
        assertThat(s.getRating()).isEqualTo(8.3);
        // The proxy is NOT a Movie instance: only the projection interface is exposed
        assertThat(s).isNotInstanceOf(Movie.class);
    }

    // -------------------------------------------------------------------------
    // Custom Fragment
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findMoviesWithMinReviews returns only movies meeting the review threshold")
    void customFragmentFiltersOnReviewCount() {
        // Arrange
        AppUser user = appUserRepository.saveAndFlush(new AppUser("bob", "bob@example.com"));

        Movie popular = movieRepository.saveAndFlush(
                new Movie("Interstellar", Genre.SCIENCE_FICTION, new BigDecimal("8.6")));
        Movie obscure = movieRepository.saveAndFlush(
                new Movie("The Room", Genre.DRAMA, new BigDecimal("3.7")));

        // popular gets 2 reviews, obscure gets 0
        reviewRepository.saveAndFlush(new Review(popular, user, "Mind-bending!", 9));
        reviewRepository.saveAndFlush(new Review(popular, user, "Stunning visuals.", 8));

        // Act
        List<Movie> result = movieRepository.findMoviesWithMinReviews(2);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTitle()).isEqualTo("Interstellar");
    }

    // -------------------------------------------------------------------------
    // Scrolling API
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Scrolling API returns the first window and correctly signals more data")
    void scrollingApiPagesThroughMovies() {
        // Arrange: insert 25 movies
        for (int i = 1; i <= 25; i++) {
            movieRepository.save(
                    new Movie("Movie %02d".formatted(i), Genre.ACTION, new BigDecimal("7.0")));
        }
        movieRepository.flush();

        Sort sort = Sort.by("title").ascending();

        // Act: first window (up to 20 items)
        Window<Movie> firstWindow = movieRepository.findFirst20By(
                ScrollPosition.offset(), sort);

        // Assert
        assertThat(firstWindow.getContent()).hasSize(20);
        assertThat(firstWindow.hasNext()).isTrue();

        // Scroll to next window
        Window<Movie> secondWindow = movieRepository.findFirst20By(
                firstWindow.positionAt(firstWindow.getContent().size() - 1), sort);

        assertThat(secondWindow.getContent()).isNotEmpty();
        // Total across both windows covers all 25 movies
        int total = firstWindow.getContent().size() + secondWindow.getContent().size();
        assertThat(total).isGreaterThanOrEqualTo(25);
    }

    // -------------------------------------------------------------------------
    // Auditing
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createdAt and updatedAt are populated automatically by Spring Data auditing")
    void auditingPopulatesTimestamps() {
        // Act
        Movie movie = movieRepository.saveAndFlush(
                new Movie("Parasite", Genre.DRAMA, new BigDecimal("8.5")));

        // Assert
        assertThat(movie.getCreatedAt()).isNotNull();
        assertThat(movie.getUpdatedAt()).isNotNull();
        assertThat(movie.getCreatedBy()).isEqualTo("system");
    }

    // -------------------------------------------------------------------------
    // Soft Delete
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deleteById sets deletedAt instead of issuing a DELETE statement")
    void softDeleteSetsDeletedAt() {
        // Arrange
        Movie movie = movieRepository.saveAndFlush(
                new Movie("Oldboy", Genre.THRILLER, new BigDecimal("8.4")));
        Long id = movie.getId();

        // Act
        movieRepository.deleteById(id);
        movieRepository.flush();

        // Assert: entity still exists in the database
        Movie found = movieRepository.findById(id).orElseThrow();
        assertThat(found.getDeletedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Scrolling API: second window shares no IDs with first window
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Second scrolling window contains no IDs that appeared in the first window")
    void scrollingApi_secondWindow_doesNotRepeatFirstPageResults() {
        // Arrange: 40 movies guarantees a full first window of 20 and a second window
        for (int i = 1; i <= 40; i++) {
            movieRepository.save(
                    new Movie("Scroll Movie %02d".formatted(i), Genre.ACTION, new BigDecimal("6.0")));
        }
        movieRepository.flush();

        Sort sort = Sort.by("title").ascending();

        // Act
        Window<Movie> firstWindow = movieRepository.findFirst20By(ScrollPosition.offset(), sort);
        assertThat(firstWindow.getContent()).hasSize(20);

        Window<Movie> secondWindow = movieRepository.findFirst20By(
                firstWindow.positionAt(firstWindow.getContent().size() - 1), sort);
        assertThat(secondWindow.getContent()).isNotEmpty();

        // Assert: the two windows share no IDs
        List<Long> firstIds  = firstWindow.getContent().stream().map(Movie::getId).toList();
        List<Long> secondIds = secondWindow.getContent().stream().map(Movie::getId).toList();
        assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);
    }

    // -------------------------------------------------------------------------
    // Soft Delete: raw DB verification via JdbcTemplate
    // -------------------------------------------------------------------------

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("After soft-delete, deleted_at column is NOT NULL in the raw table row")
    void softDelete_deletedAt_isNotNull_afterDelete() {
        // Arrange
        Movie movie = movieRepository.saveAndFlush(
                new Movie("Deleted Film", Genre.DRAMA, new BigDecimal("5.0")));
        Long id = movie.getId();

        // Act
        movieRepository.deleteById(id);
        movieRepository.flush();

        // Assert via raw JDBC: the row must still exist with a non-null deleted_at
        java.time.Instant deletedAt = jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM movies WHERE id = ?",
                java.time.Instant.class,
                id);
        assertThat(deletedAt).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Interface projection: closed interface returns proxies, not entities
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Closed interface projection returns proxies with correct field values")
    void projection_closedInterface_returnsOnlyDeclaredFields() {
        // Arrange
        String title  = "Proxy Test Movie";
        double rating = 7.7;
        movieRepository.saveAndFlush(new Movie(title, Genre.THRILLER, new BigDecimal("7.7")));

        // Act
        List<MovieSummary> summaries = movieRepository.findByGenre(Genre.THRILLER);

        // Assert: at least one result present
        assertThat(summaries).isNotEmpty();
        MovieSummary first = summaries.stream()
                .filter(s -> s.getTitle().equals(title))
                .findFirst()
                .orElseThrow();

        // Proxy check: must not be a plain Movie instance
        assertThat(first).isNotInstanceOf(Movie.class);

        // Field values must match what was saved
        assertThat(first.getTitle()).isEqualTo(title);
        assertThat(first.getRating()).isEqualTo(rating);
    }

    // -------------------------------------------------------------------------
    // Auditing: updatedAt advances on second save
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updatedAt is greater than or equal to the original value after a second save")
    void audit_updatedAt_changesOnSecondSave() throws InterruptedException {
        // Arrange: first save
        Movie movie = movieRepository.saveAndFlush(
                new Movie("Audit Movie", Genre.ROMANCE, new BigDecimal("6.5")));
        java.time.Instant originalUpdatedAt = movie.getUpdatedAt();
        assertThat(originalUpdatedAt).isNotNull();

        // Small gap to ensure clock can advance
        Thread.sleep(1);

        // Act: second save with a change
        movie.setRating(new BigDecimal("7.0"));
        Movie updated = movieRepository.saveAndFlush(movie);

        // Assert
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(originalUpdatedAt);
    }

    // -------------------------------------------------------------------------
    // Soft Delete: fallback to hard delete for entity without deletedAt
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("softDelete_entityWithoutDeletedAt_fallsBackToHardDelete: AppUser has no deletedAt, so deleteById performs a real DELETE")
    void softDelete_entityWithoutDeletedAt_fallsBackToHardDelete() {
        // Arrange: AppUser has no setDeletedAt method; SoftDeletableRepositoryImpl falls back to hard delete
        com.cinetrack.user.AppUser user = appUserRepository.saveAndFlush(
                new com.cinetrack.user.AppUser("hard_delete_user", "harddelete@example.com"));
        Long userId = user.getId();

        // Act
        appUserRepository.deleteById(userId);
        appUserRepository.flush();

        // Assert: the row is physically gone (hard delete)
        assertThat(appUserRepository.findById(userId)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Soft Delete: multiple entities each get a deletedAt timestamp
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("softDelete_multipleEntities_eachGetsTimestamp: 3 soft-deleted movies each have a non-null deleted_at via JdbcTemplate")
    void softDelete_multipleEntities_eachGetsTimestamp() {
        // Arrange
        Movie m1 = movieRepository.saveAndFlush(new Movie("Film A", Genre.ACTION, new BigDecimal("7.0")));
        Movie m2 = movieRepository.saveAndFlush(new Movie("Film B", Genre.DRAMA, new BigDecimal("6.5")));
        Movie m3 = movieRepository.saveAndFlush(new Movie("Film C", Genre.THRILLER, new BigDecimal("8.0")));

        // Act: soft-delete all three
        movieRepository.deleteById(m1.getId());
        movieRepository.deleteById(m2.getId());
        movieRepository.deleteById(m3.getId());
        movieRepository.flush();

        // Assert via JdbcTemplate: each row must have a non-null deleted_at
        for (Long id : java.util.List.of(m1.getId(), m2.getId(), m3.getId())) {
            java.time.Instant ts = jdbcTemplate.queryForObject(
                    "SELECT deleted_at FROM movies WHERE id = ?",
                    java.time.Instant.class, id);
            assertThat(ts).as("deleted_at for movie id=%d", id).isNotNull();
        }
    }

    // -------------------------------------------------------------------------
    // Soft Delete: findAll still includes soft-deleted rows (no @Where on entity)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("repository_findAll_afterSoftDelete_dependsOnWhereClause: Movie has no @Where filter, so findAll still returns soft-deleted rows")
    void repository_findAll_afterSoftDelete_dependsOnWhereClause() {
        // Arrange
        Movie movie = movieRepository.saveAndFlush(
                new Movie("Visible After Delete", Genre.ROMANCE, new BigDecimal("5.5")));
        Long id = movie.getId();

        // Act
        movieRepository.deleteById(id);
        movieRepository.flush();

        // Assert: Movie entity has no @Where(clause="deleted_at IS NULL"),
        // so findAll returns the soft-deleted row (deletedAt is non-null but row still present)
        java.util.List<Movie> all = movieRepository.findAll();
        assertThat(all).extracting(Movie::getId).contains(id);

        Movie found = movieRepository.findById(id).orElseThrow();
        assertThat(found.getDeletedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Custom fragment: exact boundary is included
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findMoviesWithMinReviews includes a movie with exactly the minimum review count")
    void findMoviesWithMinReviews_exactBoundary_isIncluded() {
        // Arrange
        AppUser user = appUserRepository.saveAndFlush(
                new AppUser("boundary_user", "boundary@example.com"));

        Movie movie = movieRepository.saveAndFlush(
                new Movie("Boundary Film", Genre.SCIENCE_FICTION, new BigDecimal("8.0")));

        // Exactly 3 reviews
        int minReviews = 3;
        for (int i = 0; i < minReviews; i++) {
            reviewRepository.saveAndFlush(
                    new Review(movie, user, "Review " + i, 7 + i));
        }

        // Act
        List<Movie> result = movieRepository.findMoviesWithMinReviews(minReviews);

        // Assert: the movie with exactly minReviews reviews must appear
        assertThat(result).extracting(Movie::getId).contains(movie.getId());
    }
}
