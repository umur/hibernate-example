package com.cinetrack.movie;

import com.cinetrack.AbstractIntegrationTest;
import com.cinetrack.review.ReviewSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MovieQueryService#findReviewSummaries(Long)}.
 *
 * Uses {@code @SpringBootTest} because {@code MovieQueryService} is a {@code @Service}
 * bean not visible in the {@code @DataJpaTest} JPA slice.  Data is managed via
 * {@link TransactionTemplate} and raw JDBC inserts so each test starts with a
 * known, isolated state.
 */
@SpringBootTest
class MovieQueryServiceTest extends AbstractIntegrationTest {

    @Autowired
    private MovieQueryService movieQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager txManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> {
            jdbcTemplate.execute("DELETE FROM reviews");
            jdbcTemplate.execute("DELETE FROM watch_logs");
            jdbcTemplate.execute("DELETE FROM movies");
            jdbcTemplate.execute("DELETE FROM app_users");
        });
    }

    // -------------------------------------------------------------------------
    // Helper — insert a movie and return its generated id
    // -------------------------------------------------------------------------

    private Long insertMovie(String title, String genre, int releaseYear, double rating) {
        jdbcTemplate.update(
                "INSERT INTO movies (title, genre, release_year, rating) VALUES (?, ?, ?, ?)",
                title, genre, releaseYear, rating);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM movies WHERE title = ?", Long.class, title);
    }

    private Long insertUser(String username, String email) {
        jdbcTemplate.update(
                "INSERT INTO app_users (username, email) VALUES (?, ?)",
                username, email);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM app_users WHERE username = ?", Long.class, username);
    }

    private void insertReview(Long movieId, Long userId, String content, int rating) {
        jdbcTemplate.update(
                "INSERT INTO reviews (movie_id, reviewer_id, content, rating) VALUES (?, ?, ?, ?)",
                movieId, userId, content, rating);
    }

    // -------------------------------------------------------------------------
    // findReviewSummaries — with data returns populated DTOs
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findReviewSummaries_withData_returnsTuples")
    void findReviewSummaries_withData_returnsTuples() {
        // Arrange
        Long movieId = tx.execute(s -> insertMovie("Tuple Film", "DRAMA", 2020, 7.5));
        Long userId  = tx.execute(s -> insertUser("tuple_alice", "tuple_alice@example.com"));
        tx.executeWithoutResult(s -> insertReview(movieId, userId, "Great film!", 8));

        // Act
        List<ReviewSummaryDto> summaries = tx.execute(s ->
                movieQueryService.findReviewSummaries(movieId));

        // Assert
        assertThat(summaries).hasSize(1);
        ReviewSummaryDto dto = summaries.get(0);
        assertThat(dto.movieTitle()).isEqualTo("Tuple Film");
        assertThat(dto.reviewerName()).isEqualTo("tuple_alice");
        assertThat(dto.rating()).isEqualTo(8);
        assertThat(dto.reviewId()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // findReviewSummaries — no reviews returns empty list
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findReviewSummaries_noData_returnsEmpty")
    void findReviewSummaries_noData_returnsEmpty() {
        // Arrange — a movie with no reviews
        Long movieId = tx.execute(s -> insertMovie("Empty Film", "COMEDY", 2021, 6.0));

        // Act
        List<ReviewSummaryDto> summaries = tx.execute(s ->
                movieQueryService.findReviewSummaries(movieId));

        // Assert
        assertThat(summaries).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findReviewSummaries — tuple fields all accessible by alias, ordered DESC
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("tupleResult_columns_accessibleByAlias")
    void tupleResult_columns_accessibleByAlias() {
        // Arrange — two reviews on the same movie
        Long movieId = tx.execute(s -> insertMovie("Multi-Review Film", "THRILLER", 2019, 8.0));
        Long bobId   = tx.execute(s -> insertUser("alias_bob",   "alias_bob@example.com"));
        Long carolId = tx.execute(s -> insertUser("alias_carol", "alias_carol@example.com"));
        tx.executeWithoutResult(s -> {
            insertReview(movieId, bobId,   "Solid thriller.", 7);
            insertReview(movieId, carolId, "Loved the twist.", 9);
        });

        // Act
        List<ReviewSummaryDto> summaries = tx.execute(s ->
                movieQueryService.findReviewSummaries(movieId));

        // Assert — two DTOs, all fields non-null, ordered by rating DESC
        assertThat(summaries).hasSize(2);
        summaries.forEach(dto -> {
            assertThat(dto.reviewId()).isNotNull();
            assertThat(dto.movieTitle()).isEqualTo("Multi-Review Film");
            assertThat(dto.reviewerName()).isNotBlank();
            assertThat(dto.rating()).isBetween(1, 10);
        });

        // ORDER BY r.rating DESC: carol(9) first, bob(7) second
        assertThat(summaries.get(0).rating()).isEqualTo(9);
        assertThat(summaries.get(1).rating()).isEqualTo(7);
    }
}
