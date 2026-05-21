package com.cinetrack.movie;

import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

import java.math.BigDecimal;

/**
 * JPA static metamodel for {@link Movie}.
 *
 * <p>In a normal Maven build the APT plugin generates this class automatically
 * into {@code target/generated-sources/java}. It is checked in here so that the
 * project compiles without running the APT phase first (e.g. when opening in an
 * IDE before the first {@code mvn compile}).</p>
 *
 * <p>If the generated version appears on the classpath it will shadow this one;
 * both are semantically identical.</p>
 */
@StaticMetamodel(Movie.class)
public class Movie_ {

    public static volatile SingularAttribute<Movie, Long>    id;
    public static volatile SingularAttribute<Movie, String>  title;
    public static volatile SingularAttribute<Movie, Genre>   genre;
    public static volatile SingularAttribute<Movie, Integer> releaseYear;
    public static volatile SingularAttribute<Movie, BigDecimal> rating;
    public static volatile ListAttribute<Movie, com.cinetrack.review.Review> reviews;
}
