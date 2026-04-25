package com.cinetrack.audit;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionMapping;

/**
 * Custom Envers revision entity.
 *
 * <p>Envers records one row in {@code revinfo} per transaction that modifies
 * an {@code @Audited} entity. By extending {@link RevisionMapping} and
 * annotating with {@link RevisionEntity}, we add two extra fields:
 *
 * <ul>
 *   <li>{@code username} — the Spring Security principal name at commit time,
 *       populated by {@link CineTrackRevisionListener}.</li>
 *   <li>{@code ipAddress} — the remote IP of the HTTP request (optional;
 *       empty in non-web contexts such as batch jobs).</li>
 * </ul>
 *
 * <p>The {@code revinfo} table is created by {@code V2__create_envers_tables.sql}.
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity(CineTrackRevisionListener.class)
@AttributeOverrides({
        @AttributeOverride(name = "id", column = @Column(name = "rev")),
        @AttributeOverride(name = "timestamp", column = @Column(name = "revtstmp"))
})
@Getter
@Setter
public class CineTrackRevision extends RevisionMapping {

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;
}
