package com.cinetrack.config;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registers PostgreSQL's {@code similarity(text, text)} function from the
 * {@code pg_trgm} extension into Hibernate's function registry so it can be
 * called from HQL / JPQL without resorting to native queries.
 *
 * <h3>How Hibernate discovers this class</h3>
 * <p>Hibernate 6+ uses Java's {@link java.util.ServiceLoader} mechanism.
 * The fully-qualified class name of this contributor must appear in
 * {@code META-INF/services/org.hibernate.boot.model.FunctionContributor}
 * on the classpath.  Spring Boot's fat-jar packaging preserves
 * {@code META-INF/services} entries automatically.</p>
 *
 * <h3>Pattern syntax</h3>
 * <p>{@code registerPattern} accepts a printf-style template where
 * {@code ?1}, {@code ?2} … are positional argument placeholders.
 * The rendered SQL is {@code similarity(<arg1>, <arg2>)}, which
 * PostgreSQL delegates to pg_trgm and returns a {@code float4}.</p>
 */
public class SimilarityFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        contributions.getFunctionRegistry()
                .registerPattern(
                        "similarity",
                        "similarity(?1, ?2)",
                        contributions.getTypeConfiguration()
                                .getBasicTypeRegistry()
                                .resolve(StandardBasicTypes.DOUBLE));
    }
}
