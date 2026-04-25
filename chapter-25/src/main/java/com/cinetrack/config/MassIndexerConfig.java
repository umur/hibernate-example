package com.cinetrack.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.massindexing.MassIndexer;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cinetrack.movie.Movie;

@Slf4j
@Component
@RequiredArgsConstructor
public class MassIndexerConfig {

    private final EntityManager entityManager;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void buildIndex() {
        log.info("Starting Hibernate Search mass indexing...");
        try {
            SearchSession searchSession = Search.session(entityManager);
            MassIndexer indexer = searchSession.massIndexer(Movie.class)
                    .threadsToLoadObjects(4)
                    .batchSizeToLoadObjects(25);
            indexer.startAndWait();
            log.info("Mass indexing completed successfully.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Mass indexing was interrupted", e);
        } catch (Exception e) {
            log.error("Mass indexing failed", e);
        }
    }
}
