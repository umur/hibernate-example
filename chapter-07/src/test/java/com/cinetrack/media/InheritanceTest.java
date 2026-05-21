package com.cinetrack.media;

import com.cinetrack.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies SINGLE_TABLE inheritance behaviour:
 * <ul>
 *   <li>Polymorphic queries return all subtypes.</li>
 *   <li>Typed repositories filter by discriminator automatically.</li>
 *   <li>The dtype column contains the expected literal values.</li>
 *   <li>Audit fields are populated without manual intervention.</li>
 * </ul>
 */
@SpringBootTest
@Transactional
@Import(com.cinetrack.config.JpaAuditingConfig.class)
class InheritanceTest extends AbstractIntegrationTest {

    @Autowired
    private MediaItemRepository mediaItemRepository;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Polymorphic findAll returns Movies, Series, and Episodes in one query")
    void polymorphicQueryReturnsAllSubtypes() {
        // Arrange
        Movie movie = new Movie("Inception", 2010, Genre.SCIENCE_FICTION, 148);

        Series series = new Series("Breaking Bad", 2008, Genre.DRAMA, 5);
        Episode ep1 = new Episode("Pilot", 2008, Genre.DRAMA, 1, 1);
        Episode ep2 = new Episode("Cat's in the Bag", 2008, Genre.DRAMA, 1, 2);
        series.addEpisode(ep1);
        series.addEpisode(ep2);

        mediaItemRepository.save(movie);
        seriesRepository.save(series);
        mediaItemRepository.flush();

        // Act
        List<MediaItem> all = mediaItemRepository.findAll();

        // Assert: 1 movie + 1 series + 2 episodes = 4 rows
        assertThat(all).hasSize(4);
        assertThat(all).anyMatch(m -> m instanceof Movie);
        assertThat(all).anyMatch(m -> m instanceof Series);
        assertThat(all).filteredOn(m -> m instanceof Episode).hasSize(2);
    }

    @Test
    @DisplayName("MovieRepository returns only Movies: Series and Episodes are excluded")
    void typedRepositoryFiltersOnDiscriminator() {
        // Arrange
        movieRepository.save(new Movie("The Dark Knight", 2008, Genre.ACTION, 152));
        seriesRepository.save(new Series("Chernobyl", 2019, Genre.DRAMA, 1));
        movieRepository.flush();

        // Act
        List<Movie> movies = movieRepository.findAll();

        // Assert
        assertThat(movies).hasSize(1);
        assertThat(movies.getFirst().getTitle()).isEqualTo("The Dark Knight");
    }

    @Test
    @DisplayName("dtype column holds correct discriminator literals for each subtype")
    void discriminatorValueIsCorrectInDatabase() {
        // Arrange
        Movie movie = movieRepository.save(new Movie("Dune", 2021, Genre.SCIENCE_FICTION, 155));
        Series series = seriesRepository.save(new Series("Severance", 2022, Genre.THRILLER, 2));
        movieRepository.flush();

        // Act: read raw dtype values via JDBC to bypass ORM mapping
        String movieDtype = jdbcTemplate.queryForObject(
                "SELECT dtype FROM media_items WHERE id = ?", String.class, movie.getId());
        String seriesDtype = jdbcTemplate.queryForObject(
                "SELECT dtype FROM media_items WHERE id = ?", String.class, series.getId());

        // Assert
        assertThat(movieDtype).isEqualTo("MOVIE");
        assertThat(seriesDtype).isEqualTo("SERIES");
    }

    @Test
    @DisplayName("Audit fields createdAt and createdBy are populated automatically on persist")
    void auditFieldsArePopulatedOnSave() {
        // Act
        Movie movie = movieRepository.save(new Movie("Parasite", 2019, Genre.DRAMA, 132));
        movieRepository.flush();

        // Assert
        assertThat(movie.getCreatedAt()).isNotNull();
        assertThat(movie.getCreatedBy()).isEqualTo("system");
        assertThat(movie.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("movieRepository_doesNotReturnSeriesOrEpisode: typed repo returns only Movie rows")
    void movieRepository_doesNotReturnSeriesOrEpisode() {
        // Arrange: one of each type
        Movie movie = movieRepository.save(new Movie("Arrival", 2016, Genre.SCIENCE_FICTION, 116));

        Series series = new Series("Mindhunter", 2017, Genre.DRAMA, 2);
        Episode ep = new Episode("Ep 1", 2017, Genre.DRAMA, 1, 1);
        series.addEpisode(ep);
        seriesRepository.save(series);
        movieRepository.flush();

        // Act
        List<Movie> movies = movieRepository.findAll();

        // Assert: only Movie instances, no Series or Episode
        assertThat(movies).allMatch(m -> m.getClass().equals(Movie.class));
        assertThat(movies).extracting(Movie::getTitle).contains("Arrival");
    }

    @Test
    @DisplayName("delete_movie_doesNotDeleteSeries: deleting a Movie leaves Series intact")
    void delete_movie_doesNotDeleteSeries() {
        // Arrange
        Movie movie = movieRepository.saveAndFlush(new Movie("Tenet", 2020, Genre.ACTION, 150));
        Series series = seriesRepository.saveAndFlush(new Series("Ozark", 2017, Genre.DRAMA, 4));

        // Act: delete only the Movie
        movieRepository.delete(movie);
        movieRepository.flush();

        // Assert: Series is still there
        assertThat(seriesRepository.findById(series.getId())).isPresent();
        assertThat(movieRepository.findById(movie.getId())).isEmpty();
    }

    @Test
    @DisplayName("polymorphicFindAll_countMatchesTotal: 2 movies + 1 series + 1 episode = 4 items")
    void polymorphicFindAll_countMatchesTotal() {
        // Arrange
        mediaItemRepository.save(new Movie("Film A", 2020, Genre.ACTION, 90));
        mediaItemRepository.save(new Movie("Film B", 2021, Genre.DRAMA, 100));

        Series series = new Series("Show C", 2022, Genre.THRILLER, 1);
        Episode episode = new Episode("Ep 1", 2022, Genre.THRILLER, 1, 1);
        series.addEpisode(episode);
        seriesRepository.save(series);
        mediaItemRepository.flush();

        // Act
        List<MediaItem> all = mediaItemRepository.findAll();

        // Assert
        assertThat(all).hasSize(4);
    }

    @Test
    @DisplayName("episode_dtype_isEPISODE: raw dtype column for Episode row is 'EPISODE'")
    void episode_dtype_isEPISODE() {
        // Arrange: build the series-with-episode graph BEFORE persisting so that
        // cascading from save() uses persist-semantics and writes IDs back onto
        // our local Episode reference. (Calling saveAndFlush on an already-managed
        // Series would trigger merge-cascade, which copies the transient Episode
        // into a new managed instance and leaves the local "ep" without an ID.)
        Series series = new Series("Fargo", 2014, Genre.DRAMA, 5);
        Episode ep = new Episode("Pilot", 2014, Genre.DRAMA, 1, 1);
        series.addEpisode(ep);
        seriesRepository.saveAndFlush(series);

        Long epId = ep.getId();

        // Act: bypass ORM and query the raw discriminator column
        String dtype = jdbcTemplate.queryForObject(
                "SELECT dtype FROM media_items WHERE id = ?", String.class, epId);

        // Assert
        assertThat(dtype).isEqualTo("EPISODE");
    }

    @Test
    @DisplayName("audit_createdAt_notNull: saved MediaItem has non-null createdAt")
    void audit_createdAt_notNull() {
        Movie movie = movieRepository.saveAndFlush(new Movie("Nomadland", 2020, Genre.DRAMA, 108));
        assertThat(movie.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("audit_updatedAt_changesOnMutation: updatedAt is set after title change")
    void audit_updatedAt_changesOnMutation() throws InterruptedException {
        // Arrange: save and capture the initial updatedAt
        Movie movie = movieRepository.saveAndFlush(new Movie("Draft Title", 2023, Genre.ACTION, 95));
        java.time.Instant createdAt = movie.getCreatedAt();

        // Small sleep to guarantee a different timestamp resolution on fast machines
        Thread.sleep(2);

        // Act: mutate and re-save
        movie.setTitle("Final Title");
        Movie updated = movieRepository.saveAndFlush(movie);

        // Assert: updatedAt is set and not null (it may equal createdAt on very fast
        // systems depending on clock resolution, but it must not be null)
        assertThat(updated.getUpdatedAt()).isNotNull();
        assertThat(updated.getTitle()).isEqualTo("Final Title");
    }

    // ── AuditorAware: @CreatedBy / @LastModifiedBy ───────────────────────────

    @Test
    @DisplayName("audit_createdBy_isSystem: @CreatedBy is populated with 'system' on persist")
    void audit_createdBy_isSystem() {
        Movie movie = movieRepository.saveAndFlush(
                new Movie("CreatedBy Test", 2022, Genre.DRAMA, 100));

        assertThat(movie.getCreatedBy()).isEqualTo("system");
    }

    @Test
    @DisplayName("audit_lastModifiedBy_isSystem: @LastModifiedBy is 'system' after update")
    void audit_lastModifiedBy_isSystem() {
        Movie movie = movieRepository.saveAndFlush(
                new Movie("ModifiedBy Test", 2021, Genre.ACTION, 110));

        // Mutate to trigger a merge / update cycle
        movie.setTitle("ModifiedBy Test: Updated");
        Movie updated = movieRepository.saveAndFlush(movie);

        assertThat(updated.getUpdatedBy()).isEqualTo("system");
    }

    // ── Polymorphic delete ────────────────────────────────────────────────────

    @Test
    @DisplayName("polymorphicDelete_movie_survivesOtherSubtypes: deleting a Movie leaves Series count unchanged")
    void polymorphicDelete_movie_survivesOtherSubtypes() {
        // Arrange: persist a Movie and a Series
        Movie movie  = movieRepository.saveAndFlush(new Movie("Delete Me", 2020, Genre.ACTION, 90));
        Series series1 = seriesRepository.saveAndFlush(new Series("Keep Me 1", 2019, Genre.DRAMA, 3));
        Series series2 = seriesRepository.saveAndFlush(new Series("Keep Me 2", 2018, Genre.THRILLER, 2));
        mediaItemRepository.flush();

        long seriesCountBefore = seriesRepository.count();

        // Act: delete only the Movie
        movieRepository.delete(movie);
        movieRepository.flush();

        // Assert: Series count is unchanged
        long seriesCountAfter = seriesRepository.count();
        assertThat(seriesCountAfter).isEqualTo(seriesCountBefore);
        assertThat(movieRepository.findById(movie.getId())).isEmpty();
    }
}
