package com.cinetrack.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.io.Serializable;

/**
 * Marker interface for repositories whose entities support soft deletion.
 * Repositories extending this interface get the overridden deleteById()
 * from SoftDeletableRepositoryImpl, which sets deletedAt instead of
 * issuing a DELETE statement.
 *
 * @NoRepositoryBean prevents Spring Data from trying to instantiate this
 * interface directly as a repository.
 */
@NoRepositoryBean
public interface SoftDeletableRepository<T, ID extends Serializable>
        extends JpaRepository<T, ID> {
}
