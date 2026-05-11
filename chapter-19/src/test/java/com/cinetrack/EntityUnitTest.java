package com.cinetrack;

import com.cinetrack.movie.Movie;
import com.cinetrack.review.Review;
import com.cinetrack.user.AppUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests for the JPA entities. They verify that Lombok-generated
 * accessors and convenience constructors round-trip values correctly without
 * starting a Spring context: keeping coverage on entity classes high while
 * adding negligible test runtime.
 */
class EntityUnitTest {

    // -----------------------------------------------------------------------
    // Movie
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Movie: convenience constructor populates title and genre")
    void movie_convenienceConstructor_setsTitleAndGenre() {
        Movie movie = new Movie("Inception", "SCI_FI");

        assertThat(movie.getTitle()).isEqualTo("Inception");
        assertThat(movie.getGenre()).isEqualTo("SCI_FI");
        assertThat(movie.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Movie: setters mutate every field")
    void movie_setters_mutateFields() {
        Movie movie = new Movie();

        movie.setId(42L);
        movie.setTitle("The Matrix");
        movie.setGenre("ACTION");
        movie.setReleaseYear(1999);
        movie.setRating(new BigDecimal("8.7"));
        OffsetDateTime now = OffsetDateTime.now();
        movie.setCreatedAt(now);

        assertThat(movie.getId()).isEqualTo(42L);
        assertThat(movie.getTitle()).isEqualTo("The Matrix");
        assertThat(movie.getGenre()).isEqualTo("ACTION");
        assertThat(movie.getReleaseYear()).isEqualTo(1999);
        assertThat(movie.getRating()).isEqualByComparingTo("8.7");
        assertThat(movie.getCreatedAt()).isEqualTo(now);
    }

    // -----------------------------------------------------------------------
    // AppUser
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AppUser: convenience constructor populates username and email; tier defaults to FREE")
    void appUser_convenienceConstructor_setsUsernameEmail_defaultsTierFree() {
        AppUser user = new AppUser("alice", "alice@example.com");

        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getSubscriptionTier()).isEqualTo("FREE");
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("AppUser: setters mutate every field")
    void appUser_setters_mutateFields() {
        AppUser user = new AppUser();

        user.setId(7L);
        user.setUsername("bob");
        user.setEmail("bob@example.com");
        user.setSubscriptionTier("PREMIUM");
        OffsetDateTime now = OffsetDateTime.now();
        user.setCreatedAt(now);

        assertThat(user.getId()).isEqualTo(7L);
        assertThat(user.getUsername()).isEqualTo("bob");
        assertThat(user.getEmail()).isEqualTo("bob@example.com");
        assertThat(user.getSubscriptionTier()).isEqualTo("PREMIUM");
        assertThat(user.getCreatedAt()).isEqualTo(now);
    }

    // -----------------------------------------------------------------------
    // Review
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Review: convenience constructor wires associations and content")
    void review_convenienceConstructor_setsAssociationsAndContent() {
        Movie movie = new Movie("Tenet", "SCI_FI");
        AppUser reviewer = new AppUser("carol", "carol@example.com");

        Review review = new Review(movie, reviewer, "Great film", 5);

        assertThat(review.getMovie()).isSameAs(movie);
        assertThat(review.getReviewer()).isSameAs(reviewer);
        assertThat(review.getContent()).isEqualTo("Great film");
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Review: setters mutate every field including version and id")
    void review_setters_mutateFields() {
        Review review = new Review();

        Movie movie = new Movie();
        AppUser reviewer = new AppUser();
        OffsetDateTime now = OffsetDateTime.now();

        review.setId(99L);
        review.setMovie(movie);
        review.setReviewer(reviewer);
        review.setContent("Terrible.");
        review.setRating(1);
        review.setVersion(3L);
        review.setCreatedAt(now);

        assertThat(review.getId()).isEqualTo(99L);
        assertThat(review.getMovie()).isSameAs(movie);
        assertThat(review.getReviewer()).isSameAs(reviewer);
        assertThat(review.getContent()).isEqualTo("Terrible.");
        assertThat(review.getRating()).isEqualTo(1);
        assertThat(review.getVersion()).isEqualTo(3L);
        assertThat(review.getCreatedAt()).isEqualTo(now);
    }
}
