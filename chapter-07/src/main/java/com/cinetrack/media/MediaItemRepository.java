package com.cinetrack.media;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Polymorphic repository: findAll() returns Movie, Series, and Episode
 * instances from the single media_items table. Hibernate uses the dtype
 * discriminator to instantiate the correct subclass for each row.
 */
public interface MediaItemRepository extends JpaRepository<MediaItem, Long> {
}
