package com.cinetrack.migration;

import com.cinetrack.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that all Flyway migrations apply cleanly against a real PostgreSQL 16
 * instance and that Hibernate's {@code ddl-auto=validate} accepts the resulting
 * schema without errors.
 *
 * <p>Key assertions:
 * <ul>
 *   <li>Spring context loads successfully — this alone proves migrations pass
 *       and validation succeeds.</li>
 *   <li>The repeatable migration {@code R__movie_search_function.sql} created
 *       the {@code search_movies} function.</li>
 *   <li>V4 added {@code subscription_tier} with the correct column default.</li>
 * </ul>
 */
class MigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Context loads: all migrations applied and schema validation passed")
    void contextLoads() {
        // If this test method body executes, the Spring context started successfully.
        // That means Flyway ran V1–V4 + R migrations without errors, and
        // Hibernate's ddl-auto=validate found no mismatches.
        assertThat(jdbc).isNotNull();
    }

    @Test
    @DisplayName("R__movie_search_function: search_movies() exists in pg_proc")
    void searchMoviesFunctionExists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_proc WHERE proname = 'search_movies'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("R__movie_search_function: search_movies() can be called via JDBC")
    void searchMoviesFunctionIsCallable() {
        // Insert a known row so the function has data to match
        jdbc.update("INSERT INTO movies (title, genre) VALUES (?, ?)",
                "The Dark Knight", "THRILLER");

        List<Map<String, Object>> results = jdbc.queryForList(
                "SELECT * FROM search_movies(?)", "Knight");

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().get("title")).isEqualTo("The Dark Knight");
    }

    @Test
    @DisplayName("V4: subscription_tier column exists with correct default 'FREE'")
    void subscriptionTierColumnHasCorrectDefault() {
        // Query pg_attribute + pg_attrdef to retrieve the column default
        String columnDefault = jdbc.queryForObject(
                """
                SELECT pg_get_expr(d.adbin, d.adrelid)
                FROM   pg_attribute a
                JOIN   pg_class     c ON c.oid = a.attrelid
                JOIN   pg_attrdef   d ON d.adrelid = a.attrelid
                                     AND d.adnum   = a.attnum
                WHERE  c.relname  = 'app_users'
                AND    a.attname  = 'subscription_tier'
                """,
                String.class);

        // PostgreSQL stores the default as a cast expression
        assertThat(columnDefault).containsIgnoringCase("FREE");
    }

    @Test
    @DisplayName("V4: subscription_tier column is NOT NULL")
    void subscriptionTierColumnIsNotNull() {
        Boolean notNull = jdbc.queryForObject(
                """
                SELECT a.attnotnull
                FROM   pg_attribute a
                JOIN   pg_class     c ON c.oid = a.attrelid
                WHERE  c.relname = 'app_users'
                AND    a.attname = 'subscription_tier'
                """,
                Boolean.class);

        assertThat(notNull).isTrue();
    }

    @Test
    @DisplayName("V2: idx_movies_genre index was created by migration")
    void genreIndexExists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'idx_movies_genre'",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    // -----------------------------------------------------------------------
    // New tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("V1: movies table exists in information_schema")
    void v1_moviesTable_exists() {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                AND   table_name   = 'movies'
                """,
                Integer.class);

        assertThat(count)
                .as("The 'movies' table must exist after V1/V2 migrations")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("V1: app_users table exists in information_schema")
    void v1_appUsersTable_exists() {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                AND   table_name   = 'app_users'
                """,
                Integer.class);

        assertThat(count)
                .as("The 'app_users' table must exist after V1 migration")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("V2: idx_movies_genre index exists on movies table")
    void v2_indexExists_onMoviesGenre() {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE tablename = 'movies'
                AND   indexname = 'idx_movies_genre'
                """,
                Integer.class);

        assertThat(count)
                .as("Index idx_movies_genre must exist on the movies table after V2 migration")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("V4: inserting NULL subscription_tier raises a DataAccessException")
    void v4_subscriptionTier_notNull_constraintEnforced() {
        assertThatThrownBy(() ->
                jdbc.update(
                        """
                        INSERT INTO app_users (username, email, subscription_tier)
                        VALUES (?, ?, NULL)
                        """,
                        "nulluser", "nulluser@example.com")
        )
                .as("Inserting NULL into subscription_tier must violate the NOT NULL constraint")
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    @DisplayName("flyway_schema_history contains V1–V4 and R entries, all successful")
    void flyway_schemaHistory_hasAllVersions() {
        // Retrieve all rows from the Flyway history table
        java.util.List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                "SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank");

        // Collect the version strings present (null for repeatable migrations)
        java.util.Set<String> versions = new java.util.HashSet<>();
        boolean hasRepeatable = false;
        boolean allSuccessful = true;

        for (java.util.Map<String, Object> row : rows) {
            Object version = row.get("version");
            Boolean success = (Boolean) row.get("success");

            if (success == null || !success) {
                allSuccessful = false;
            }

            if (version != null) {
                versions.add(version.toString());
            } else {
                // A null version means a repeatable migration (R__ prefix)
                hasRepeatable = true;
            }
        }

        assertThat(versions)
                .as("flyway_schema_history must contain entries for V1, V2, V3, and V4")
                .contains("1", "2", "3", "4");

        assertThat(hasRepeatable)
                .as("flyway_schema_history must contain at least one repeatable migration (R__)")
                .isTrue();

        assertThat(allSuccessful)
                .as("Every migration in flyway_schema_history must have success = true")
                .isTrue();
    }

    @Test
    @DisplayName("R__movie_search_function: search_movies() returns results without error")
    void repeatable_migration_function_callable() {
        // Call with an empty / non-matching query — function must not throw
        java.util.List<java.util.Map<String, Object>> results = jdbc.queryForList(
                "SELECT * FROM search_movies(?)", "nonexistentxyz");

        // The function must execute without error; result set may be empty
        assertThat(results)
                .as("search_movies() must be callable and return a (possibly empty) result set")
                .isNotNull();
    }

    // -----------------------------------------------------------------------
    // New tests — Chapter 15 spec
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("V1: reviews table exists in information_schema")
    void v1_reviewsTable_exists() {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                AND   table_name   = 'reviews'
                """,
                Integer.class);

        assertThat(count)
                .as("The 'reviews' table must exist after V1 migration")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("V1: reviews table has a foreign key referencing movies(id)")
    void v1_foreignKey_moviesId_exists() {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.key_column_usage kcu
                JOIN information_schema.referential_constraints rc
                     ON rc.constraint_name = kcu.constraint_name
                    AND rc.constraint_schema = kcu.constraint_schema
                JOIN information_schema.key_column_usage ref
                     ON ref.constraint_name = rc.unique_constraint_name
                    AND ref.ordinal_position = kcu.position_in_unique_constraint
                WHERE kcu.table_name   = 'reviews'
                  AND ref.table_name   = 'movies'
                  AND ref.column_name  = 'id'
                """,
                Integer.class);

        assertThat(count)
                .as("reviews must have a foreign key column referencing movies(id)")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("movies.rating column has a numeric data type")
    void movies_ratingColumn_isNumeric() {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name   = 'movies'
                  AND column_name  = 'rating'
                  AND data_type IN ('numeric', 'decimal', 'real', 'double precision', 'integer', 'bigint')
                """,
                Integer.class);

        assertThat(count)
                .as("movies.rating must have a numeric data type")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("app_users.email has a unique constraint — duplicate insert raises DataAccessException")
    void appUser_emailColumn_isUnique() {
        // Insert the first user
        jdbc.update(
                "INSERT INTO app_users (username, email) VALUES (?, ?)",
                "uniqueuser1", "unique@example.com");

        // Inserting a second user with the same email must violate the unique constraint
        assertThatThrownBy(() ->
                jdbc.update(
                        "INSERT INTO app_users (username, email) VALUES (?, ?)",
                        "uniqueuser2", "unique@example.com")
        )
                .as("Inserting a duplicate email into app_users must raise a constraint violation")
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
