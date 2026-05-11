package com.cinetrack.movie;

import com.querydsl.core.types.dsl.BooleanExpression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Movie} that participates in three different
 * query-building strategies simultaneously:
 *
 * <ol>
 *   <li>{@link JpaRepository}: standard CRUD and derived query methods.</li>
 *   <li>{@link JpaSpecificationExecutor}: accepts {@code Specification<Movie>}
 *       predicates composed from {@link MovieSpecifications}.</li>
 *   <li>{@link QuerydslPredicateExecutor}: accepts QueryDSL
 *       {@link BooleanExpression} predicates built with the generated
 *       {@code QMovie} class.</li>
 * </ol>
 *
 * <p>All three interfaces share the same underlying {@code EntityManager} and
 * produce standard JPQL/SQL: there is no extra infrastructure required beyond
 * the dependency declarations in {@code pom.xml}.</p>
 */
@Repository
public interface MovieRepository extends
        JpaRepository<Movie, Long>,
        JpaSpecificationExecutor<Movie>,
        QuerydslPredicateExecutor<Movie> {
}
