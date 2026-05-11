package com.cinetrack.batch;

import com.cinetrack.movie.Movie;
import com.cinetrack.movie.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates programmatic transaction management with {@link TransactionTemplate}.
 *
 * <h2>Why TransactionTemplate instead of @Transactional?</h2>
 * <p>{@code @Transactional} wraps the entire method in a single transaction.
 * For bulk import that means one enormous transaction: all inserts or none.
 * When one item is invalid, the whole batch rolls back and you lose everything.
 *
 * <p>{@link TransactionTemplate} lets you commit each item individually, so
 * a bad row only loses that row. This is the "per-item commit" pattern: a
 * pragmatic alternative to full chunk-based frameworks like Spring Batch when
 * the import is simple enough.
 *
 * <h2>Error handling strategy</h2>
 * <p>Inside the lambda, calling {@code status.setRollbackOnly()} marks the
 * current (per-item) transaction for rollback without throwing an exception.
 * This is preferable to letting an unchecked exception escape, which would
 * also roll back but would require the caller to catch and suppress it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieImportService {

    private final MovieRepository movieRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * Imports a list of movies, committing each one in its own transaction.
     *
     * <p>For each movie:
     * <ul>
     *   <li>A new transaction is opened via {@link TransactionTemplate#execute}.</li>
     *   <li>Basic validation runs. If invalid, {@code status.setRollbackOnly()}
     *       is called and the item is skipped: no INSERT reaches the database.</li>
     *   <li>If valid, the movie is saved and the transaction commits when the
     *       lambda returns.</li>
     * </ul>
     *
     * @param movies list of (possibly invalid) movies to import
     * @return an {@link ImportResult} summarising successes and skipped titles
     */
    public ImportResult importMovies(List<Movie> movies) {
        List<String> imported = new ArrayList<>();
        List<String> skipped  = new ArrayList<>();

        for (Movie movie : movies) {
            String title = movie.getTitle();

            transactionTemplate.execute(status -> {
                try {
                    validate(movie);
                    movieRepository.save(movie);
                    log.info("Imported: '{}'", title);
                    imported.add(title);
                } catch (IllegalArgumentException validationEx) {
                    // Mark this per-item transaction for rollback and skip the row.
                    // The outer loop continues with the next movie.
                    log.warn("Skipping '{}': {}", title, validationEx.getMessage());
                    status.setRollbackOnly();
                    skipped.add(title + " (" + validationEx.getMessage() + ")");
                }
                return null;
            });
        }

        log.info("Import complete: {} imported, {} skipped", imported.size(), skipped.size());
        return new ImportResult(imported, skipped);
    }

    private void validate(Movie movie) {
        if (movie.getTitle() == null || movie.getTitle().isBlank()) {
            throw new IllegalArgumentException("title is blank");
        }
        if (movie.getReleaseYear() < 1888 || movie.getReleaseYear() > 2100) {
            throw new IllegalArgumentException("release year out of range: " + movie.getReleaseYear());
        }
        if (movie.getGenre() == null) {
            throw new IllegalArgumentException("genre is required");
        }
    }

    /**
     * Value object summarising the outcome of a bulk import operation.
     *
     * @param importedTitles titles that were successfully committed
     * @param skippedTitles  titles that failed validation (with reason)
     */
    public record ImportResult(List<String> importedTitles, List<String> skippedTitles) {
    }
}
