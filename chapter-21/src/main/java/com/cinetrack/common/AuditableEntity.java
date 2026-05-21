package com.cinetrack.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base class for entities that require Spring Data JPA auditing.
 *
 * <p>Spring Data populates the four fields automatically on every
 * INSERT and UPDATE:
 * <ul>
 *   <li>{@code createdAt} / {@code updatedAt}: set from the system clock.</li>
 *   <li>{@code createdBy} / {@code updatedBy}: resolved from the
 *       {@link org.springframework.data.domain.AuditorAware} bean wired in
 *       {@link com.cinetrack.config.JpaAuditingConfig}, which reads the
 *       authenticated principal name from the Spring Security context.</li>
 * </ul>
 *
 * <p>{@code createdAt} and {@code createdBy} are marked {@code updatable=false}
 * so Hibernate never overwrites them on subsequent UPDATEs.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ")
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
