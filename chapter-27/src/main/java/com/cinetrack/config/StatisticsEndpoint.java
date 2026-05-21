package com.cinetrack.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatisticsEndpoint {

    private final EntityManagerFactory entityManagerFactory;

    @GetMapping
    public Map<String, Object> getStatistics() {
        SessionFactory sf = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryExecutionCount", stats.getQueryExecutionCount());
        result.put("queryExecutionMaxTime", stats.getQueryExecutionMaxTime());
        result.put("queryExecutionMaxTimeQueryString", stats.getQueryExecutionMaxTimeQueryString());
        result.put("entityLoadCount", stats.getEntityLoadCount());
        result.put("entityFetchCount", stats.getEntityFetchCount());
        result.put("entityInsertCount", stats.getEntityInsertCount());
        result.put("entityUpdateCount", stats.getEntityUpdateCount());
        result.put("entityDeleteCount", stats.getEntityDeleteCount());
        result.put("collectionLoadCount", stats.getCollectionLoadCount());
        result.put("secondLevelCacheHitCount", stats.getSecondLevelCacheHitCount());
        result.put("secondLevelCacheMissCount", stats.getSecondLevelCacheMissCount());
        result.put("secondLevelCachePutCount", stats.getSecondLevelCachePutCount());
        result.put("transactionCount", stats.getTransactionCount());
        result.put("successfulTransactionCount", stats.getSuccessfulTransactionCount());
        result.put("sessionOpenCount", stats.getSessionOpenCount());
        result.put("sessionCloseCount", stats.getSessionCloseCount());
        return result;
    }
}
