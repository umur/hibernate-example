package com.cinetrack.types;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.service.ServiceRegistry;

/**
 * Registers application-specific Hibernate types globally so entity fields
 * of type {@link Money} do not require a per-field {@code @Type} annotation.
 *
 * <h3>Mechanism</h3>
 * <p>Hibernate 6+ discovers implementations of {@link TypeContributor} via
 * Java's {@link java.util.ServiceLoader}.  The fully-qualified class name must
 * appear in
 * {@code META-INF/services/org.hibernate.boot.model.TypeContributor}
 * on the classpath.  Spring Boot's fat-jar build preserves this file.</p>
 *
 * <p>Once registered, Hibernate resolves {@code Money} fields to
 * {@link MoneyType} at bootstrap time: before the first session is opened : 
 * so there is zero per-request overhead.</p>
 *
 * <h3>Alternative: {@code @Type} annotation</h3>
 * <p>If global registration is undesirable (e.g. the same JVM hosts multiple
 * persistence units), annotate individual fields instead:
 * <pre>{@code
 * @Type(MoneyType.class)
 * private Money price;
 * }</pre>
 * Both approaches produce identical SQL; the contributor just eliminates
 * repetition.</p>
 */
public class CineTrackTypeContributor implements TypeContributor {

    @Override
    public void contribute(TypeContributions typeContributions,
                           ServiceRegistry serviceRegistry) {
        typeContributions.contributeType(new MoneyType());
    }
}
