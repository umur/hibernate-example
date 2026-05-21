package com.cinetrack.movie;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "movies")
@BatchSize(size = 25)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"metadata", "streamingPlatforms"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Movie {

    @Id
    @UuidGenerator
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "genre", nullable = false, length = 50)
    private Genre genre;

    @Column(name = "release_year", nullable = false)
    private int releaseYear;

    @Column(name = "rating", precision = 3, scale = 1)
    private BigDecimal rating;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Array(length = 20)
    @Column(name = "streaming_platforms", columnDefinition = "text[]")
    private String[] streamingPlatforms;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
