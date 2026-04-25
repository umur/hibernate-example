package com.cinetrack.config;

import com.cinetrack.common.SoftDeletableRepositoryImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Registers SoftDeletableRepositoryImpl as the base class for all JPA
 * repositories in the application. This replaces SimpleJpaRepository globally,
 * so every repository inherits the soft-delete behaviour without any per-repo
 * configuration.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.cinetrack",
        repositoryBaseClass = SoftDeletableRepositoryImpl.class
)
public class RepositoryConfig {
}
