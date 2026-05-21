package com.cinetrack.movie;

import com.cinetrack.common.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;

/**
 * A movie whose full change history is tracked by Hibernate Envers.
 *
 * <p>{@link Audited} instructs Envers to record a snapshot of this entity in
 * {@code movies_aud} on every INSERT, UPDATE, and DELETE. Each snapshot links
 * to a row in {@code revinfo} that carries the revision timestamp, the
 * authenticated username, and the remote IP address.
 *
 * <p>Auditing fields ({@code created_at}, {@code created_by}, etc.) are
 * inherited from {@link AuditableEntity} and are also captured in the audit
 * table, giving a complete picture of who changed what and when.
 */
@Entity
@Table(name = "movies")
@Audited
@Getter
@Setter
@NoArgsConstructor
public class Movie extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 50)
    private String genre;

    @Column(precision = 3, scale = 1)
    private BigDecimal rating;

    public Movie(String title, String genre) {
        this.title = title;
        this.genre = genre;
    }
}
