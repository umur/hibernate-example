package com.cinetrack.movie;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieSearchService {

    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public List<Movie> searchByKeyword(String keyword) {
        SearchSession searchSession = Search.session(entityManager);

        return searchSession.search(Movie.class)
                .where(f -> f.simpleQueryString()
                        .fields("title", "overview")
                        .matching(keyword))
                .fetchAllHits();
    }

    @Transactional(readOnly = true)
    public List<Movie> searchByKeywordWithLimit(String keyword, int limit) {
        SearchSession searchSession = Search.session(entityManager);

        return searchSession.search(Movie.class)
                .where(f -> f.match()
                        .fields("title", "overview")
                        .matching(keyword))
                .fetchHits(limit);
    }

    @Transactional(readOnly = true)
    public List<Movie> searchByGenre(String genre) {
        SearchSession searchSession = Search.session(entityManager);

        return searchSession.search(Movie.class)
                .where(f -> f.match()
                        .field("genre")
                        .matching(genre))
                .fetchAllHits();
    }
}
