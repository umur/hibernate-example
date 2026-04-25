package com.cinetrack.movie;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OptimisticLock;

import java.math.BigDecimal;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * Demonstrates two key optimistic locking patterns:
 *
 * 1. {@code @Version} — Hibernate increments this column on every UPDATE and
 *    checks it before flushing, throwing {@code OptimisticLockException} when
 *    a stale value is detected.
 *
 * 2. {@code @OptimisticLock(excluded = true)} on {@code viewCount} — tells
 *    Hibernate NOT to bump the version when only this field changes.  High-
 *    frequency view increments can therefore be applied with a plain
 *    {@code UPDATE ... SET view_count = view_count + 1} without causing spurious
 *    lock failures on concurrent rating updates.
 */
@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(STRING)
    private Genre genre;

    private BigDecimal rating;

    /**
     * Excluded from optimistic lock version calculation.
     * Concurrent view-count increments will NOT cause
     * OptimisticLockException on reviewers updating content.
     */
    @OptimisticLock(excluded = true)
    @Column(name = "view_count")
    private long viewCount;

    @Version
    private long version;

    public Movie(String title, Genre genre) {
        this.title = title;
        this.genre = genre;
    }
}
