package com.cinetrack.common;

import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.time.Instant;

/**
 * Custom base repository that overrides deleteById() to perform a soft delete.
 *
 * <p>Spring Data JPA allows replacing SimpleJpaRepository as the base class by
 * annotating a @Configuration class with
 * {@code @EnableJpaRepositories(repositoryBaseClass = SoftDeletableRepositoryImpl.class)}.
 * Every repository in the application then inherits this behaviour automatically.</p>
 *
 * <p>The implementation uses reflection to call a {@code setDeletedAt(Instant)}
 * method if the entity exposes one — making the base class generic without
 * requiring a common entity supertype beyond AuditableEntity.</p>
 */
public class SoftDeletableRepositoryImpl<T, ID extends Serializable>
        extends SimpleJpaRepository<T, ID>
        implements SoftDeletableRepository<T, ID> {

    private final EntityManager entityManager;

    public SoftDeletableRepositoryImpl(
            JpaEntityInformation<T, ?> entityInformation,
            EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    /**
     * Performs a soft delete by setting deletedAt to the current instant.
     * Falls back to a hard delete if the entity does not have a setDeletedAt method.
     */
    @Override
    public void deleteById(ID id) {
        findById(id).ifPresent(entity -> {
            try {
                Method setter = entity.getClass().getMethod("setDeletedAt", Instant.class);
                setter.invoke(entity, Instant.now());
                entityManager.merge(entity);
            } catch (NoSuchMethodException e) {
                // Entity does not support soft delete — fall back to hard delete
                entityManager.remove(entityManager.contains(entity)
                        ? entity
                        : entityManager.merge(entity));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Soft delete failed for entity: " + entity, e);
            }
        });
    }
}
